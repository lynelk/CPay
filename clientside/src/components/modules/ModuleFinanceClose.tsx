import React, { useEffect, useState } from 'react';
import {
  Card,
  Section,
  Toolbar,
  Table,
  Badge,
  Alert,
  Spinner,
  DateField,
  TextField,
  Select,
  Button,
} from '../../ui';
import type { Column } from '../../ui';
import {
  useFinanceCloseSummary,
  useFinanceCloseSubmitMutation,
  useFinanceCloseApproveMutation,
  useFinanceCloseRejectMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { FinanceCloseSummary } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit E6: the finance daily-close dashboard. Backend maker-checker workflow
 * (`ReconFinanceController` under `/api/v2/admin/recon-finance/**`) already
 * existed; this was the missing screen - previously finance could only submit
 * a close by calling the API directly, and the maker/checker split was
 * invisible.
 */

const CURRENCIES = [
  { value: 'UGX', label: 'UGX (Uganda)' },
  { value: 'KES', label: 'KES (Kenya)' },
  { value: 'TZS', label: 'TZS (Tanzania)' },
  { value: 'RWF', label: 'RWF (Rwanda)' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function statusTone(status?: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'CLOSED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'PENDING_APPROVAL' || status === 'PENDING') return 'warning';
  return 'neutral';
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

interface ModuleFinanceCloseProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleFinanceClose({ loader, refreshSignal, sessionExpired }: ModuleFinanceCloseProps): React.ReactElement {
  const [currency, setCurrency] = useState('UGX');
  const [closeDate, setCloseDate] = useState(today());
  const [submittedBy, setSubmittedBy] = useState('');
  const [approvedBy, setApprovedBy] = useState('');
  const [rejectedBy, setRejectedBy] = useState('');
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const summaryQuery = useFinanceCloseSummary(currency);
  const submitMutation = useFinanceCloseSubmitMutation();
  const approveMutation = useFinanceCloseApproveMutation();
  const rejectMutation = useFinanceCloseRejectMutation();

  useLoaderSync(
    loader,
    summaryQuery.isFetching || submitMutation.isPending || approveMutation.isPending || rejectMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [summaryQuery.refetch]);

  useEffect(() => {
    if (summaryQuery.error instanceof ApiError && summaryQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summaryQuery.error]);

  const summary: FinanceCloseSummary | undefined = summaryQuery.data;

  function handleSubmit() {
    if (!closeDate) return;
    setFeedback(null);
    submitMutation.mutate(
      { date: closeDate, currency, submittedBy: submittedBy.trim() || 'system' },
      {
        onSuccess: (closeId) => {
          setFeedback({
            tone: 'success',
            message: `Daily close submitted for ${closeDate} (${currency}) — row #${closeId} is awaiting a checker's approval.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleApprove() {
    if (!closeDate || !approvedBy.trim()) return;
    setFeedback(null);
    approveMutation.mutate(
      { date: closeDate, currency, approvedBy: approvedBy.trim() },
      {
        onSuccess: () => {
          setFeedback({ tone: 'success', message: `Daily close approved and closed for ${closeDate} (${currency}).` });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleReject() {
    if (!closeDate || !rejectedBy.trim()) return;
    setFeedback(null);
    rejectMutation.mutate(
      { date: closeDate, currency, rejectedBy: rejectedBy.trim(), reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          setFeedback({
            tone: 'success',
            message: `Daily close for ${closeDate} (${currency}) rejected — the maker can correct and re-submit.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  const pendingRows = summary?.pendingSubmissions ?? [];

  const pendingColumns: Column<{ closeDate?: string; currency?: string; submittedBy?: string; submittedAt?: string; status?: string }>[] = [
    { key: 'closeDate', header: 'Close date', accessor: (r) => r.closeDate ?? '' },
    { key: 'currency', header: 'Currency', accessor: (r) => r.currency ?? '' },
    { key: 'submittedBy', header: 'Submitted by', accessor: (r) => r.submittedBy ?? '' },
    { key: 'submittedAt', header: 'Submitted at', accessor: (r) => r.submittedAt ?? '' },
    {
      key: 'status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status ?? 'UNKNOWN'}</Badge>,
    },
  ];

  return (
    <div className="cpay-finance-close">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 'var(--ios-space-3)', marginBottom: 'var(--ios-space-4)' }}>
        <Card flush>
          <div style={{ padding: 'var(--ios-space-4)' }}>
            <strong>Statements received</strong>
            <div>{summaryQuery.isLoading ? '…' : (summary?.statementsReceived ?? 0)}</div>
          </div>
        </Card>
        <Card flush>
          <div style={{ padding: 'var(--ios-space-4)' }}>
            <strong>Unmatched records</strong>
            <div>{summaryQuery.isLoading ? '…' : (summary?.unmatchedRecords ?? 0)}</div>
          </div>
        </Card>
        <Card flush>
          <div style={{ padding: 'var(--ios-space-4)' }}>
            <strong>Parked callbacks</strong>
            <div>{summaryQuery.isLoading ? '…' : (summary?.parkedCallbacks ?? 0)}</div>
          </div>
        </Card>
        <Card flush>
          <div style={{ padding: 'var(--ios-space-4)' }}>
            <strong>Open controls</strong>
            <div>{summaryQuery.isLoading ? '…' : (summary?.openControls ?? 0)}</div>
          </div>
        </Card>
      </div>

      {summaryQuery.error ? <Alert variant="error">{errorMessage(summaryQuery.error)}</Alert> : null}
      {summaryQuery.isLoading ? <Spinner label="Loading finance summary" /> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Daily close actions (maker-checker). The maker submits; a different actor must approve.</strong>
          </Toolbar>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)' }}>
            <DateField id="fc-close-date" label="Close date" value={closeDate} onValueChange={setCloseDate} />
            <div>
              <label htmlFor="fc-currency">Currency</label>
              <Select id="fc-currency" value={currency} options={CURRENCIES} onValueChange={setCurrency} />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <div>
              <TextField id="fc-submitted-by" label="Submitted by (maker)" value={submittedBy} onValueChange={setSubmittedBy} placeholder="e.g. finance-maker" />
              <Button variant="primary" onClick={handleSubmit} loading={submitMutation.isPending} loadingLabel="Submitting…">
                Submit for approval
              </Button>
            </div>
            <div>
              <TextField id="fc-approved-by" label="Approved by (checker)" value={approvedBy} onValueChange={setApprovedBy} placeholder="different actor" />
              <Button variant="secondary" onClick={handleApprove} loading={approveMutation.isPending} loadingLabel="Approving…">
                Approve close
              </Button>
            </div>
            <div>
              <TextField id="fc-rejected-by" label="Rejected by (checker)" value={rejectedBy} onValueChange={setRejectedBy} placeholder="different actor" />
              <TextField id="fc-reject-reason" label="Reject reason" value={reason} onValueChange={setReason} placeholder="optional" />
              <Button variant="ghost" className="ios-btn--sm" onClick={handleReject} loading={rejectMutation.isPending} loadingLabel="Rejecting…">
                Reject close
              </Button>
            </div>
          </div>
        </div>
      </Card>

      <Section title="Pending close submissions">
        {pendingRows.length === 0 ? <p>No daily-close submissions awaiting approval for {currency}.</p> : null}
        <Table
          columns={pendingColumns}
          rows={pendingRows}
          rowKey={(r) => `${r.closeDate ?? ''}-${r.currency ?? ''}`}
          pageSize={20}
          emptyText="No pending submissions."
        />
      </Section>
    </div>
  );
}

export default ModuleFinanceClose;
