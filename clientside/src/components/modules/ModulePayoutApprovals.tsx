import React, { useEffect, useState } from 'react';
import {
  Card,
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
  usePendingPayoutApprovals,
  usePayoutApproveMutation,
  usePayoutRejectMutation,
  usePayoutCancelMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { PayoutApprovalRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit E7: the payout maker-checker approval queue. Backend
 * (`PayoutApprovalController` under `/api/v2/admin/payout-approvals/**`) parks
 * a payout when a configured limit/velocity control trips or first-beneficiary
 * approval is enabled; this is the missing admin screen - previously the queue
 * could only be actioned by calling the API directly. Approval re-executes the
 * stored payout through the normal orchestrator path; the same-actor rejection
 * is enforced server-side.
 */

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function triggerTone(reason?: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (reason === 'PER_TRANSACTION_LIMIT' || reason === 'DAILY_LIMIT' || reason === 'MONTHLY_LIMIT') {
    return 'warning';
  }
  if (reason === 'BENEFICIARY_VELOCITY_LIMIT') return 'danger';
  if (reason === 'FIRST_BENEFICIARY') return 'neutral';
  return 'neutral';
}

function formatDate(value?: string): string {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function formatMoney(amount: number | string | undefined, currency?: string): string {
  if (amount === undefined || amount === null || amount === '') return '';
  return `${currency ?? ''} ${amount}`.trim();
}

interface ModulePayoutApprovalsProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModulePayoutApprovals({ loader, refreshSignal, sessionExpired }: ModulePayoutApprovalsProps): React.ReactElement {
  const [selected, setSelected] = useState<PayoutApprovalRow | null>(null);
  const [approvedBy, setApprovedBy] = useState('');
  const [rejectedBy, setRejectedBy] = useState('');
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const queueQuery = usePendingPayoutApprovals(100);
  const approveMutation = usePayoutApproveMutation();
  const rejectMutation = usePayoutRejectMutation();
  const cancelMutation = usePayoutCancelMutation();

  useLoaderSync(
    loader,
    queueQuery.isFetching || approveMutation.isPending || rejectMutation.isPending || cancelMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [queueQuery.refetch]);

  useEffect(() => {
    if (queueQuery.error instanceof ApiError && queueQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queueQuery.error]);

  const rows = queueQuery.data ?? [];

  function handleApprove() {
    if (!selected?.id || !approvedBy.trim()) return;
    setFeedback(null);
    approveMutation.mutate(
      { queueId: selected.id, approvedBy: approvedBy.trim() },
      {
        onSuccess: (result) => {
          setFeedback({
            tone: 'success',
            message: `Payout ${selected.payout_reference ?? '#' + selected.id} approved and submitted (${result.status ?? 'accepted'}).`,
          });
          setSelected(null);
          setApprovedBy('');
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleReject() {
    if (!selected?.id || !rejectedBy.trim()) return;
    setFeedback(null);
    rejectMutation.mutate(
      { queueId: selected.id, rejectedBy: rejectedBy.trim(), reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          setFeedback({ tone: 'success', message: `Payout ${selected.payout_reference ?? '#' + selected.id} rejected.` });
          setSelected(null);
          setRejectedBy('');
          setReason('');
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleCancel() {
    if (!selected?.id || !rejectedBy.trim()) return;
    setFeedback(null);
    cancelMutation.mutate(
      { queueId: selected.id, cancelledBy: rejectedBy.trim() },
      {
        onSuccess: () => {
          setFeedback({ tone: 'success', message: `Payout ${selected.payout_reference ?? '#' + selected.id} cancelled.` });
          setSelected(null);
          setRejectedBy('');
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  const columns: Column<PayoutApprovalRow>[] = [
    { key: 'id', header: 'Queue ID', accessor: (r) => String(r.id ?? '') },
    { key: 'payout_reference', header: 'Reference', accessor: (r) => r.payout_reference ?? '' },
    { key: 'merchant_number', header: 'Merchant', accessor: (r) => r.merchant_number ?? '' },
    {
      key: 'amount',
      header: 'Amount',
      numeric: true,
      render: (r) => formatMoney(r.amount, r.currency),
      sortable: true,
      sortValue: (r) => Number(r.amount) || 0,
    },
    { key: 'channel_code', header: 'Channel', accessor: (r) => r.channel_code ?? '' },
    { key: 'beneficiary_reference', header: 'Beneficiary', accessor: (r) => r.beneficiary_reference ?? '' },
    {
      key: 'trigger_reason',
      header: 'Trigger',
      render: (r) => <Badge tone={triggerTone(r.trigger_reason)}>{r.trigger_reason ?? 'UNKNOWN'}</Badge>,
    },
    { key: 'requested_by', header: 'Requested by', accessor: (r) => r.requested_by ?? '' },
    {
      key: 'requested_at',
      header: 'Requested at',
      accessor: (r) => formatDate(r.requested_at),
      sortable: true,
      sortValue: (r) => r.requested_at || '',
    },
  ];

  return (
    <div className="cpay-payout-approvals">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Payouts awaiting maker-checker approval</strong>
            <Toolbar.Spacer />
            <Button variant="ghost" className="ios-btn--sm" onClick={() => queueQuery.refetch()}>
              Refresh
            </Button>
          </Toolbar>
        </div>
      </Card>

      {queueQuery.isLoading ? <Spinner label="Loading payout approvals" /> : null}
      {queueQuery.error ? <Alert variant="error">{errorMessage(queueQuery.error)}</Alert> : null}
      {!queueQuery.isLoading && !queueQuery.error ? (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id ?? 0}
          isRowSelected={(r) => r.id === selected?.id}
          onRowClick={setSelected}
          pageSize={20}
          emptyText="No payouts awaiting approval. All clear."
        />
      ) : null}

      <Card>
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'flex-end',
            gap: 'var(--ios-space-4)',
          }}
        >
          <div>
            <strong>Selected: </strong>
            {selected
              ? `${selected.payout_reference ?? '#' + selected.id} (${formatMoney(selected.amount, selected.currency)})`
              : 'None — click a row to approve/reject/cancel'}
          </div>
          <div style={{ flex: '1 1 200px' }}>
            <TextField
              id="pa-approver"
              label="Approved by (checker — must differ from the requester)"
              value={approvedBy}
              onValueChange={setApprovedBy}
              placeholder="e.g. finance-checker"
            />
            <Button
              variant="primary"
              disabled={!selected || !approvedBy.trim()}
              loading={approveMutation.isPending}
              loadingLabel="Approving…"
              onClick={handleApprove}
            >
              Approve & execute
            </Button>
          </div>
          <div style={{ flex: '1 1 200px' }}>
            <TextField
              id="pa-rejector"
              label="Rejected / cancelled by (checker)"
              value={rejectedBy}
              onValueChange={setRejectedBy}
              placeholder="different actor"
            />
            <TextField id="pa-reject-reason" label="Reason" value={reason} onValueChange={setReason} placeholder="optional" />
            <div style={{ display: 'flex', gap: 'var(--ios-space-2)' }}>
              <Button
                variant="secondary"
                disabled={!selected || !rejectedBy.trim()}
                loading={rejectMutation.isPending}
                loadingLabel="Rejecting…"
                onClick={handleReject}
              >
                Reject
              </Button>
              <Button
                variant="ghost"
                className="ios-btn--sm"
                disabled={!selected || !rejectedBy.trim()}
                loading={cancelMutation.isPending}
                loadingLabel="Cancelling…"
                onClick={handleCancel}
              >
                Cancel
              </Button>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

export default ModulePayoutApprovals;
