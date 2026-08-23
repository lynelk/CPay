import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Section, Spinner, Table, TextField, Toolbar } from '../../ui';
import type { Column } from '../../ui';
import { apiFetch } from '../../shared/api/httpClient';
import { apiUrl } from '../../shared/config';

interface GoLiveRequest {
  id: number;
  merchant_id: number;
  merchant_name?: string;
  account_number?: string;
  certification_run_id?: number;
  request_status?: string;
  current_stage?: string;
  requested_by?: string;
  decision_by?: string;
  requested_at?: string;
  updated_at?: string;
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(apiUrl(path), {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init,
  });
  if (response.status === 401) throw new Error('SESSION_EXPIRED');
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload?.message || payload?.detail || `Request failed (${response.status})`);
  return payload as T;
}

function tone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const value = (status ?? '').toUpperCase();
  if (value === 'ACTIVATED' || value === 'APPROVED') return 'success';
  if (value === 'REJECTED') return 'danger';
  if (value === 'REQUESTED' || value === 'IN_REVIEW') return 'warning';
  return 'neutral';
}

export default function ModuleSandboxGoLive({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [rows, setRows] = useState<GoLiveRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [smokeMerchantId, setSmokeMerchantId] = useState('');
  const [smokeMerchantNumber, setSmokeMerchantNumber] = useState('');
  const [smokeReference, setSmokeReference] = useState('');

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    loader?.('START');
    try {
      setRows(await requestJson<GoLiveRequest[]>('/api/v2/admin/sandbox/go-live-requests'));
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to load go-live requests.';
      if (message === 'SESSION_EXPIRED') sessionExpired?.();
      else setError(message);
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }, [loader, sessionExpired]);

  useEffect(() => { void load(); }, [load, refreshSignal]);

  const mutate = useCallback(async (path: string, body?: unknown, message = 'Operation completed.') => {
    setBusy(true);
    setError(null);
    setNotice(null);
    loader?.('START');
    try {
      const result = await requestJson<Record<string, unknown>>(path, {
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const operationStatus = result.status ? String(result.status).toUpperCase() : '';
      if (operationStatus === 'FAILED') {
        throw new Error(`${message.replace(/[.!]\s*$/, '')} failed. Review the verification evidence before continuing.`);
      }
      setNotice(`${message}${result.status ? ` Status: ${String(result.status)}.` : ''}`);
      await load();
    } catch (err) {
      const text = err instanceof Error ? err.message : 'Operation failed.';
      if (text === 'SESSION_EXPIRED') sessionExpired?.();
      else setError(text);
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }, [load, loader, sessionExpired]);

  const columns = useMemo<Column<GoLiveRequest>[]>(() => [
    { key: 'merchant', header: 'Merchant', render: (row) => `${row.merchant_name ?? row.merchant_id} (${row.account_number ?? row.merchant_id})` },
    { key: 'cert', header: 'Certification', accessor: (row) => row.certification_run_id ? `#${row.certification_run_id}` : '' },
    { key: 'stage', header: 'Stage', accessor: (row) => row.current_stage ?? '' },
    { key: 'status', header: 'Status', render: (row) => <Badge tone={tone(row.request_status)}>{row.request_status ?? 'UNKNOWN'}</Badge> },
    { key: 'reviewer', header: 'Last reviewer', accessor: (row) => row.decision_by ?? '' },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => {
        const terminal = row.request_status === 'ACTIVATED' || row.request_status === 'REJECTED';
        return (
          <div style={{ display: 'flex', gap: 'var(--ios-space-2)', flexWrap: 'wrap' }}>
            {!terminal ? (
              <Button
                variant="primary"
                className="ios-btn--sm"
                disabled={busy}
                onClick={() => void mutate(`/api/v2/admin/sandbox/go-live-requests/${row.id}/decision`, { action: 'ADVANCE', notes: 'Approved in go-live workbench' }, row.request_status === 'APPROVED' ? 'Merchant activated.' : 'Review stage advanced.')}
              >{row.request_status === 'APPROVED' ? 'Activate' : 'Advance'}</Button>
            ) : null}
            {!terminal ? (
              <Button
                variant="danger"
                className="ios-btn--sm"
                disabled={busy}
                onClick={() => {
                  if (window.confirm('Reject this production-access request?')) {
                    void mutate(`/api/v2/admin/sandbox/go-live-requests/${row.id}/decision`, { action: 'REJECT', notes: 'Rejected in go-live workbench' }, 'Production-access request rejected.');
                  }
                }}
              >Reject</Button>
            ) : null}
            {row.request_status === 'APPROVED' ? (
              <Button
                variant="ghost"
                className="ios-btn--sm"
                disabled={busy}
                onClick={() => void mutate(`/api/v2/admin/sandbox/merchants/${row.merchant_id}/promote-configuration`, { goLiveRequestId: row.id }, 'Safe tenant configuration promoted/validated.')}
              >Promote config</Button>
            ) : null}
            {row.request_status === 'ACTIVATED' ? (
              <>
                <Button variant="ghost" className="ios-btn--sm" disabled={busy} onClick={() => void mutate(`/api/v2/admin/sandbox/merchants/${row.merchant_id}/rollout`, { stage: 'REFUNDS', dailyLimit: 25 }, 'Refund stage enabled.')}>Refunds</Button>
                <Button variant="ghost" className="ios-btn--sm" disabled={busy} onClick={() => void mutate(`/api/v2/admin/sandbox/merchants/${row.merchant_id}/rollout`, { stage: 'PAYOUTS_LOW_LIMIT', dailyLimit: 50 }, 'Low-limit payout stage enabled.')}>Payouts</Button>
                <Button variant="primary" className="ios-btn--sm" disabled={busy} onClick={() => void mutate(`/api/v2/admin/sandbox/merchants/${row.merchant_id}/rollout`, { stage: 'FULL' }, 'Full production stage enabled.')}>Full</Button>
              </>
            ) : null}
          </div>
        );
      },
    },
  ], [busy, mutate]);

  return (
    <div style={{ display: 'grid', gap: 'var(--ios-space-4)' }}>
      {error ? <Alert variant="error">{error}</Alert> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}
      {busy && rows.length === 0 ? <Spinner label="Loading sandbox go-live operations" /> : null}

      <Card>
        <Section
          title="Sandbox → production approvals"
          actions={<Button variant="ghost" className="ios-btn--sm" onClick={() => void load()} disabled={busy}>Refresh</Button>}
        >
          <p style={{ color: 'var(--ios-text-secondary)' }}>Technical, compliance, risk and operations reviews are explicit stages. Production activation starts at a low collections-only limit.</p>
        </Section>
        <Table columns={columns} rows={rows} rowKey={(row) => row.id} pageSize={50} emptyText="No production-access requests yet." />
      </Card>

      <Card>
        <Section title="Production smoke-test verification">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Verify an already-authorized low-value live transaction without creating a real transaction from the admin console.</p>
          <div className="ios-form" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            <TextField id="smoke-merchant-id" label="Merchant ID" type="number" value={smokeMerchantId} onValueChange={setSmokeMerchantId} />
            <TextField id="smoke-merchant-number" label="Merchant number" value={smokeMerchantNumber} onValueChange={setSmokeMerchantNumber} />
            <TextField id="smoke-reference" label="Transaction reference" value={smokeReference} onValueChange={setSmokeReference} />
          </div>
          <Button
            variant="primary"
            disabled={busy || !smokeMerchantId || !smokeMerchantNumber.trim() || !smokeReference.trim()}
            onClick={() => void mutate(`/api/v2/admin/sandbox/merchants/${Number(smokeMerchantId)}/live-smoke-test`, { merchantNumber: smokeMerchantNumber, transactionReference: smokeReference }, 'Production smoke test verified.')}
          >Verify live transaction</Button>
        </Section>
      </Card>

      <Card>
        <Section title="Isolation verification">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Re-check the database-level sandbox/production boundaries and production capability gates.</p>
          <Toolbar>
            <Button variant="primary" disabled={busy} onClick={() => void mutate('/api/v2/admin/sandbox/verify-isolation', undefined, 'Sandbox isolation checks completed.')}>Run isolation verification</Button>
          </Toolbar>
        </Section>
      </Card>
    </div>
  );
}
