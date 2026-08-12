import React, { useEffect, useState } from 'react';
import {
  Card,
  Section,
  Toolbar,
  Table,
  Badge,
  Alert,
  Spinner,
  TextField,
  Button,
} from '../../ui';
import type { Column } from '../../ui';
import {
  useKycMerchantSummary,
  useKycOwnerReviewMutation,
  useKycDocumentReviewMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { KybRecordRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Admin KYB review workbench (audit P7): load a merchant's beneficial owners
 * and KYC documents, then approve/reject each pending record. Backend
 * (KycController + new owner/document review routes) existed; this is the
 * missing review screen for compliance officers.
 */

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function formatDate(value?: string): string {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function statusTone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const s = (status ?? '').toUpperCase();
  if (s === 'APPROVED' || s === 'VERIFIED') return 'success';
  if (s === 'REJECTED' || s === 'FAILED') return 'danger';
  if (s === 'PENDING' || s === 'IN_REVIEW') return 'warning';
  return 'neutral';
}

interface ModuleKybReviewProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleKybReview({ loader, refreshSignal, sessionExpired }: ModuleKybReviewProps): React.ReactElement {
  const [merchantIdInput, setMerchantIdInput] = useState('');
  const [merchantId, setMerchantId] = useState<number | undefined>(undefined);
  const [actor, setActor] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const summaryQuery = useKycMerchantSummary(merchantId);
  const ownerReviewMutation = useKycOwnerReviewMutation();
  const documentReviewMutation = useKycDocumentReviewMutation();

  useLoaderSync(
    loader,
    summaryQuery.isFetching || ownerReviewMutation.isPending || documentReviewMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [summaryQuery.refetch]);

  useEffect(() => {
    if (summaryQuery.error instanceof ApiError && summaryQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summaryQuery.error]);

  const records = summaryQuery.data ?? [];

  function handleLoad() {
    const parsed = Number(merchantIdInput.trim());
    if (!Number.isFinite(parsed) || parsed <= 0) {
      setFeedback({ tone: 'error', message: 'Enter a valid merchant ID.' });
      return;
    }
    setFeedback(null);
    setMerchantId(parsed);
  }

  function handleReview(record: KybRecordRow, decision: string) {
    if (record.record_type === 'DOCUMENT') {
      documentReviewMutation.mutate(
        { id: record.id ?? 0, decision, reviewedBy: actor.trim() || undefined },
        {
          onSuccess: () => setFeedback({ tone: 'success', message: `Document #${record.id} → ${decision}` }),
          onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
        },
      );
    } else {
      ownerReviewMutation.mutate(
        { id: record.id ?? 0, decision, reviewedBy: actor.trim() || undefined },
        {
          onSuccess: () => setFeedback({ tone: 'success', message: `Owner #${record.id} → ${decision}` }),
          onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
        },
      );
    }
  }

  const columns: Column<KybRecordRow>[] = [
    { key: 'id', header: 'ID', accessor: (r) => String(r.id ?? '') },
    {
      key: 'record_type',
      header: 'Type',
      render: (r) => <Badge tone={r.record_type === 'DOCUMENT' ? 'neutral' : 'warning'}>{r.record_type ?? 'UNKNOWN'}</Badge>,
    },
    { key: 'label', header: 'Label', accessor: (r) => r.label ?? '' },
    {
      key: 'status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status ?? 'UNKNOWN'}</Badge>,
    },
    {
      key: 'created_at',
      header: 'Created',
      accessor: (r) => formatDate(r.created_at),
      sortable: true,
      sortValue: (r) => r.created_at || '',
    },
    {
      key: 'actions',
      header: 'Review',
      render: (r) => {
        const s = (r.status ?? '').toUpperCase();
        const isPending = s === 'PENDING' || s === 'IN_REVIEW';
        if (!isPending) return <em>Decided</em>;
        return (
          <div style={{ display: 'flex', gap: 'var(--ios-space-2)' }}>
            <Button variant="primary" className="ios-btn--sm" onClick={() => handleReview(r, 'APPROVED')}>
              Approve
            </Button>
            <Button variant="danger" className="ios-btn--sm" onClick={() => handleReview(r, 'REJECTED')}>
              Reject
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <div className="cpay-kyb-review">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>KYB review workbench</strong>
            <Toolbar.Spacer />
            <div style={{ display: 'flex', gap: 'var(--ios-space-2)', alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <TextField
                id="kyb-merchant-id"
                label="Merchant ID"
                value={merchantIdInput}
                onValueChange={setMerchantIdInput}
                placeholder="e.g. 7"
              />
              <TextField
                id="kyb-actor"
                label=""
                value={actor}
                onValueChange={setActor}
                placeholder="Reviewer (optional)"
              />
              <Button variant="secondary" onClick={handleLoad}>
                Load records
              </Button>
            </div>
          </Toolbar>
        </div>
      </Card>

      {merchantId ? (
        <Section title={`Beneficial owners & KYC documents for merchant ${merchantId}`}>
          {summaryQuery.isLoading ? <Spinner label="Loading KYB records" /> : null}
          {summaryQuery.error ? <Alert variant="error">{errorMessage(summaryQuery.error)}</Alert> : null}
          {!summaryQuery.isLoading && !summaryQuery.error ? (
            <Table
              columns={columns}
              rows={records}
              rowKey={(r) => r.id ?? 0}
              pageSize={20}
              emptyText="No owners or KYC documents recorded for this merchant."
            />
          ) : null}
        </Section>
      ) : (
        <p>Enter a merchant ID to load their beneficial owners and KYC documents for review.</p>
      )}
    </div>
  );
}

export default ModuleKybReview;
