import React, { useState } from 'react';
import { Card, Toolbar, Alert, TextField, Button } from '../../ui';
import {
  useSettlementCloseSubmitMutation,
  useSettlementCloseApproveMutation,
  useSettlementCloseRejectMutation,
  useLoaderSync,
} from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit E8: settlement batch close maker-checker. Backend
 * (`SettlementOpsController` under `/api/v2/admin/reconciliation/settlements/**`)
 * opens batches, submits closes for approval, and requires a different actor
 * to approve; this is the missing admin screen for the close half of the
 * workflow.
 */

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface ModuleSettlementCloseProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleSettlementClose({ loader }: ModuleSettlementCloseProps): React.ReactElement {
  const [reference, setReference] = useState('');
  const [closedBy, setClosedBy] = useState('');
  const [approvedBy, setApprovedBy] = useState('');
  const [rejectedBy, setRejectedBy] = useState('');
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const submitMutation = useSettlementCloseSubmitMutation();
  const approveMutation = useSettlementCloseApproveMutation();
  const rejectMutation = useSettlementCloseRejectMutation();

  useLoaderSync(
    loader,
    submitMutation.isPending || approveMutation.isPending || rejectMutation.isPending,
  );

  function handleSubmit() {
    if (!reference.trim()) return;
    setFeedback(null);
    submitMutation.mutate(
      { reference: reference.trim(), closedBy: closedBy.trim() || 'system' },
      {
        onSuccess: (raw) => {
          setFeedback({
            tone: 'success',
            message: `Batch ${reference.trim()} closed for approval: ${raw}.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleApprove() {
    if (!reference.trim() || !approvedBy.trim()) return;
    setFeedback(null);
    approveMutation.mutate(
      { reference: reference.trim(), approvedBy: approvedBy.trim() },
      {
        onSuccess: (result) => {
          setFeedback({
            tone: 'success',
            message: `Batch ${reference.trim()} ${result.status ?? 'closed'}.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleReject() {
    if (!reference.trim() || !rejectedBy.trim()) return;
    setFeedback(null);
    rejectMutation.mutate(
      { reference: reference.trim(), rejectedBy: rejectedBy.trim(), reason: reason.trim() || undefined },
      {
        onSuccess: (result) => {
          setFeedback({
            tone: 'success',
            message: `Batch ${reference.trim()} ${result.status ?? 'rejected'} — maker can re-submit.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  return (
    <div className="cpay-settlement-close">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      <Card>
        <Toolbar>
          <strong>Settlement batch close — maker submits, different actor approves</strong>
        </Toolbar>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 'var(--ios-space-4)',
            marginTop: 'var(--ios-space-4)',
            alignItems: 'flex-end',
          }}
        >
          <div>
            <TextField
              id="sc-batch-reference"
              label="Batch reference"
              value={reference}
              onValueChange={setReference}
              placeholder="e.g. SET-2026-08-02-001"
            />
          </div>
          <div>
            <TextField
              id="sc-closed-by"
              label="Closed by (maker)"
              value={closedBy}
              onValueChange={setClosedBy}
              placeholder="e.g. finance-maker"
            />
            <Button variant="primary" onClick={handleSubmit} loading={submitMutation.isPending} loadingLabel="Submitting…">
              Submit close for approval
            </Button>
          </div>
          <div>
            <TextField
              id="sc-approved-by"
              label="Approved by (checker)"
              value={approvedBy}
              onValueChange={setApprovedBy}
              placeholder="different actor"
            />
            <Button variant="secondary" onClick={handleApprove} loading={approveMutation.isPending} loadingLabel="Approving…">
              Approve close
            </Button>
          </div>
          <div>
            <TextField
              id="sc-rejected-by"
              label="Rejected by (checker)"
              value={rejectedBy}
              onValueChange={setRejectedBy}
              placeholder="different actor"
            />
            <TextField id="sc-reject-reason" label="Reason" value={reason} onValueChange={setReason} placeholder="optional" />
            <Button variant="ghost" className="ios-btn--sm" onClick={handleReject} loading={rejectMutation.isPending} loadingLabel="Rejecting…">
              Reject close
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

export default ModuleSettlementClose;
