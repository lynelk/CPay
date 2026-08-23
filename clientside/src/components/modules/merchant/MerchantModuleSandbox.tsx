import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Section, Spinner, StatGrid, StatTile, Table, TextField, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import { apiFetch } from '../../../shared/api/httpClient';
import { apiUrl } from '../../../shared/config';

interface SandboxCheck {
  id?: string;
  key?: string;
  label?: string;
  check_label?: string;
  status?: string;
  passed?: boolean | number;
  value?: number;
  action?: string;
  evidence?: string;
}

interface SandboxWallet {
  id?: number;
  channel_code?: string;
  currency?: string;
  available_balance?: string | number;
  updated_at?: string;
}

interface SandboxSnapshot {
  id: number;
  snapshot_name?: string;
  created_by?: string;
  created_at?: string;
}

interface SandboxPersona {
  persona_code?: string;
  persona_type?: string;
  display_name?: string;
  expected_status?: string;
  scenario?: string;
}

interface SandboxCertification {
  id?: number;
  run_status?: string;
  passed_checks?: number;
  total_checks?: number;
  checks?: SandboxCheck[];
}

interface GoLiveRequest {
  id?: number;
  request_status?: string;
  current_stage?: string;
  requested_at?: string;
}

interface RolloutState {
  stage_code?: string;
  production_daily_limit?: number;
  collections_enabled?: boolean | number;
  refunds_enabled?: boolean | number;
  payouts_enabled?: boolean | number;
}

interface SandboxSummary {
  environment?: { activeEnvironment?: string; sandboxSeparated?: boolean };
  readiness?: { checklist?: SandboxCheck[]; configuredChannels?: number; channelsWithApprovedCertification?: number };
  wallets?: SandboxWallet[];
  snapshots?: SandboxSnapshot[];
  personas?: SandboxPersona[];
  certification?: SandboxCertification;
  goLive?: GoLiveRequest;
  rollout?: RolloutState;
  environmentComparison?: Record<string, unknown>;
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

async function jsonRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(apiUrl(path), {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init,
  });
  if (response.status === 401) throw new Error('SESSION_EXPIRED');
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = payload?.message || payload?.detail || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return payload as T;
}

function ready(check: SandboxCheck): boolean {
  return check.status === 'READY' || check.passed === true || check.passed === 1;
}

function yes(value: boolean | number | undefined): string {
  return value === true || value === 1 ? 'Enabled' : 'Disabled';
}

export default function MerchantModuleSandbox({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [summary, setSummary] = useState<SandboxSummary | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [channelCode, setChannelCode] = useState('GENERAL');
  const [currency, setCurrency] = useState('UGX');
  const [amount, setAmount] = useState('1000000');
  const [snapshotName, setSnapshotName] = useState('Integration baseline');

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    loader?.('START');
    try {
      setSummary(await jsonRequest<SandboxSummary>('/api/v2/portal/sandbox/summary'));
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to load sandbox state.';
      if (message === 'SESSION_EXPIRED') sessionExpired?.();
      else setError(message);
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }, [loader, sessionExpired]);

  useEffect(() => { void load(); }, [load, refreshSignal]);

  const mutate = useCallback(async (path: string, body?: unknown, success = 'Sandbox updated.') => {
    setBusy(true);
    setError(null);
    setNotice(null);
    loader?.('START');
    try {
      await jsonRequest(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
      setNotice(success);
      await load();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Sandbox operation failed.';
      if (message === 'SESSION_EXPIRED') sessionExpired?.();
      else setError(message);
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }, [load, loader, sessionExpired]);

  const readiness = summary?.readiness?.checklist ?? [];
  const readinessPassed = readiness.filter(ready).length;
  const certification = summary?.certification ?? {};
  const certificationChecks = certification.checks ?? [];
  const goLive = summary?.goLive ?? {};
  const rollout = summary?.rollout ?? {};
  const wallets = summary?.wallets ?? [];
  const snapshots = summary?.snapshots ?? [];
  const personas = summary?.personas ?? [];

  const walletColumns = useMemo<Column<SandboxWallet>[]>(() => [
    { key: 'channel', header: 'Channel', accessor: (row) => row.channel_code ?? 'GENERAL' },
    { key: 'currency', header: 'Currency', accessor: (row) => row.currency ?? '' },
    { key: 'balance', header: 'Synthetic balance', numeric: true, accessor: (row) => row.available_balance ?? 0 },
    { key: 'updated', header: 'Updated', accessor: (row) => row.updated_at ?? '' },
  ], []);

  const snapshotColumns = useMemo<Column<SandboxSnapshot>[]>(() => [
    { key: 'name', header: 'Snapshot', accessor: (row) => row.snapshot_name ?? `Snapshot ${row.id}` },
    { key: 'created', header: 'Created', accessor: (row) => row.created_at ?? '' },
    { key: 'actor', header: 'Created by', accessor: (row) => row.created_by ?? '' },
    {
      key: 'restore', header: '', render: (row) => (
        <Button
          variant="ghost"
          className="ios-btn--sm"
          disabled={busy}
          onClick={() => {
            if (window.confirm('Restore this sandbox snapshot? Production data is never changed.')) {
              void mutate(`/api/v2/portal/sandbox/snapshots/${row.id}/restore`, undefined, 'Sandbox snapshot restored.');
            }
          }}
        >Restore</Button>
      ),
    },
  ], [busy, mutate]);

  const personaColumns = useMemo<Column<SandboxPersona>[]>(() => [
    { key: 'code', header: 'Persona', accessor: (row) => row.persona_code ?? '' },
    { key: 'type', header: 'Type', accessor: (row) => row.persona_type ?? '' },
    { key: 'name', header: 'Scenario', accessor: (row) => row.display_name ?? '' },
    { key: 'expected', header: 'Expected', accessor: (row) => row.expected_status ?? '' },
  ], []);

  const checkColumns = useMemo<Column<SandboxCheck>[]>(() => [
    {
      key: 'status', header: 'Status', width: 100, render: (row) => (
        <Badge tone={ready(row) ? 'success' : 'warning'}>{ready(row) ? 'Ready' : 'Action'}</Badge>
      ),
    },
    { key: 'check', header: 'Check', accessor: (row) => row.label ?? row.check_label ?? row.key ?? row.id ?? '' },
    { key: 'evidence', header: 'Evidence / next step', accessor: (row) => row.evidence ?? row.action ?? '' },
  ], []);

  return (
    <div style={{ display: 'grid', gap: 'var(--ios-space-4)' }}>
      {error ? <Alert variant="error">{error}</Alert> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}
      {busy && !summary ? <Spinner label="Loading sandbox workbench" /> : null}

      <Card>
        <Section title="Sandbox & production readiness">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Use synthetic money and identities to validate the whole integration before production activation.</p>
          <StatGrid>
            <StatTile label="Environment" value={summary?.environment?.activeEnvironment ?? 'SANDBOX'} />
            <StatTile label="Readiness" value={`${readinessPassed}/${readiness.length}`} />
            <StatTile label="Certification" value={certification.run_status ?? 'Not run'} />
            <StatTile label="Go-live" value={goLive.request_status ?? 'Not requested'} />
            <StatTile label="Rollout" value={rollout.stage_code ?? 'SANDBOX'} />
          </StatGrid>
          <Toolbar>
            <Button variant="primary" disabled={busy} onClick={() => void mutate('/api/v2/portal/sandbox/certification/run', undefined, 'Certification run completed.')}>
              Run certification
            </Button>
            <Button
              variant="ghost"
              disabled={busy || certification.run_status !== 'PASSED'}
              onClick={() => void mutate('/api/v2/portal/sandbox/production-access', undefined, 'Production-access request submitted.')}
            >
              Request production access
            </Button>
            <Toolbar.Spacer />
            <Button variant="ghost" disabled={busy} onClick={() => void load()}>Refresh</Button>
          </Toolbar>
        </Section>
        <Table columns={checkColumns} rows={readiness} rowKey={(row, index) => row.id ?? row.key ?? index} emptyText="No readiness checks are available yet." />
      </Card>

      {certificationChecks.length > 0 ? (
        <Card>
          <Section title="Certification evidence">
            <p style={{ color: 'var(--ios-text-secondary)' }}>{certification.passed_checks ?? 0}/{certification.total_checks ?? 0} automated checks passed.</p>
          </Section>
          <Table columns={checkColumns} rows={certificationChecks} rowKey={(row, index) => row.key ?? index} pageSize={50} />
        </Card>
      ) : null}

      <Card>
        <Section title="Synthetic wallets">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Top up test balances only. These values can never settle or move real money.</p>
          <div className="ios-form" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
            <TextField id="sandbox-channel" label="Channel" value={channelCode} onValueChange={setChannelCode} />
            <TextField id="sandbox-currency" label="Currency" value={currency} onValueChange={setCurrency} />
            <TextField id="sandbox-amount" label="Amount" type="number" min="1" value={amount} onValueChange={setAmount} />
          </div>
          <Toolbar>
            <Button
              variant="primary"
              disabled={busy || Number(amount) <= 0}
              onClick={() => void mutate('/api/v2/portal/sandbox/wallets/top-up', { channelCode, currency, amount: Number(amount) }, 'Synthetic balance topped up.')}
            >Top up synthetic balance</Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={() => {
                if (window.confirm('Reset sandbox wallets, snapshots, certification runs and sandbox provider-run logs? Production data is excluded.')) {
                  void mutate('/api/v2/portal/sandbox/reset', { scope: 'ALL' }, 'Sandbox reset completed.');
                }
              }}
            >Reset sandbox</Button>
          </Toolbar>
        </Section>
        <Table columns={walletColumns} rows={wallets} rowKey={(row, index) => row.id ?? index} emptyText="No synthetic balances yet." />
      </Card>

      <Card>
        <Section title="Snapshots">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Save and restore a known sandbox baseline without touching production data.</p>
          <div className="ios-form" style={{ maxWidth: 480 }}>
            <TextField id="sandbox-snapshot-name" label="Snapshot name" value={snapshotName} onValueChange={setSnapshotName} />
          </div>
          <Button variant="primary" disabled={busy || !snapshotName.trim()} onClick={() => void mutate('/api/v2/portal/sandbox/snapshots', { name: snapshotName }, 'Sandbox snapshot created.')}>
            Create snapshot
          </Button>
        </Section>
        <Table columns={snapshotColumns} rows={snapshots} rowKey={(row) => row.id} emptyText="No sandbox snapshots yet." />
      </Card>

      <Card>
        <Section title="Synthetic KYC/KYB personas">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Deterministic identities for success, mismatch, screening, document and biometric scenarios.</p>
        </Section>
        <Table columns={personaColumns} rows={personas} rowKey={(row, index) => row.persona_code ?? index} pageSize={20} />
      </Card>

      <Card>
        <Section title="Environment comparison">
          <p style={{ color: 'var(--ios-text-secondary)' }}>Production keeps its own credentials and real balances; shared tenant configuration is reused without copying secrets.</p>
          <StatGrid>
            <StatTile label="Sandbox channels" value={String(summary?.environmentComparison?.sandboxChannels ?? 0)} />
            <StatTile label="Production channels" value={String(summary?.environmentComparison?.productionChannels ?? 0)} />
            <StatTile label="Callbacks" value={String(summary?.environmentComparison?.webhookEndpoints ?? 0)} />
            <StatTile label="Production tx today" value={String(summary?.environmentComparison?.productionTransactionCountToday ?? 0)} />
          </StatGrid>
          <p style={{ color: 'var(--ios-text-secondary)' }}>
            Production rollout: collections {yes(rollout.collections_enabled)}, refunds {yes(rollout.refunds_enabled)}, payouts {yes(rollout.payouts_enabled)}. Daily launch limit: {rollout.production_daily_limit ?? 0}.
          </p>
          {goLive.id ? <Alert variant="warning">Go-live request #{goLive.id}: {goLive.request_status} / {goLive.current_stage}</Alert> : null}
        </Section>
      </Card>
    </div>
  );
}
