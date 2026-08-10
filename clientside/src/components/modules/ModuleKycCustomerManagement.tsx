import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Select, Spinner, Table, TextField, Toolbar } from '../../ui';
import type { Column } from '../../ui';
import { ApiError, request } from '../../shared/api/httpClient';

interface KycProfile {
  id?: number;
  entityId?: number;
  entityType?: string;
  profileType?: string;
  tier?: string;
  status?: string;
  riskRating?: string;
  verifiedBy?: string;
  decisionReason?: string;
}

interface ComplianceCase {
  id?: number;
  merchantId?: number;
  caseType?: string;
  status?: string;
  severity?: string;
  reason?: string;
  createdAt?: string;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function tone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const value = String(status ?? '').toUpperCase();
  if (['APPROVED', 'VERIFIED', 'CLOSED'].includes(value)) return 'success';
  if (['REJECTED', 'BLOCKED', 'FAILED'].includes(value)) return 'danger';
  if (['PENDING', 'REVIEW', 'OPEN', 'REQUIRES_INFORMATION'].includes(value)) return 'warning';
  return 'neutral';
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

export default function ModuleKycCustomerManagement({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [profiles, setProfiles] = useState<KycProfile[]>([]);
  const [cases, setCases] = useState<ComplianceCase[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [decisionActor, setDecisionActor] = useState('');
  const [decisionReason, setDecisionReason] = useState('');

  async function load() {
    setLoading(true);
    setError(null);
    loader?.('START');
    try {
      const suffix = statusFilter ? `?status=${encodeURIComponent(statusFilter)}` : '';
      const [profileRows, caseRows] = await Promise.all([
        request<KycProfile[]>(`/api/v2/admin/compliance/profiles${suffix}`),
        request<ComplianceCase[]>('/api/v2/admin/compliance/cases'),
      ]);
      setProfiles(Array.isArray(profileRows) ? profileRows : []);
      setCases(Array.isArray(caseRows) ? caseRows : []);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }

  useEffect(() => { void load(); }, [statusFilter, refreshSignal]);

  async function decideCase(id: number | undefined, decision: 'APPROVED' | 'REJECTED' | 'REVIEWED') {
    if (!id) return;
    setError(null);
    try {
      const query = new URLSearchParams({
        decision,
        actor: decisionActor.trim() || 'admin',
      });
      if (decisionReason.trim()) query.set('reason', decisionReason.trim());
      await request(`/api/v2/admin/compliance/cases/${id}/decision?${query.toString()}`, { method: 'POST' });
      await load();
    } catch (e) {
      setError(e);
    }
  }

  const profileColumns = useMemo<Column<KycProfile>[]>(() => [
    { key: 'entity', header: 'Customer', accessor: (r) => String(r.entityId ?? '') },
    { key: 'type', header: 'Profile', accessor: (r) => r.profileType ?? r.entityType ?? '' },
    { key: 'tier', header: 'Tier', accessor: (r) => r.tier ?? '' },
    { key: 'status', header: 'Status', render: (r) => <Badge tone={tone(r.status)}>{r.status ?? 'UNKNOWN'}</Badge> },
    { key: 'risk', header: 'Risk', accessor: (r) => r.riskRating ?? '' },
    { key: 'verifiedBy', header: 'Verified by', accessor: (r) => r.verifiedBy ?? '' },
    { key: 'reason', header: 'Decision reason', accessor: (r) => r.decisionReason ?? '' },
  ], []);

  const caseColumns = useMemo<Column<ComplianceCase>[]>(() => [
    { key: 'id', header: 'Case', accessor: (r) => String(r.id ?? '') },
    { key: 'merchant', header: 'Customer', accessor: (r) => String(r.merchantId ?? '') },
    { key: 'type', header: 'Type', accessor: (r) => r.caseType ?? '' },
    { key: 'severity', header: 'Severity', accessor: (r) => r.severity ?? '' },
    { key: 'status', header: 'Status', render: (r) => <Badge tone={tone(r.status)}>{r.status ?? 'OPEN'}</Badge> },
    { key: 'reason', header: 'Reason', accessor: (r) => r.reason ?? '' },
    {
      key: 'actions', header: 'Actions', render: (r) => (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button className="ios-btn--sm" variant="ghost" onClick={() => decideCase(r.id, 'REVIEWED')}>Review</Button>
          <Button className="ios-btn--sm" variant="primary" onClick={() => decideCase(r.id, 'APPROVED')}>Approve</Button>
          <Button className="ios-btn--sm" variant="danger" onClick={() => decideCase(r.id, 'REJECTED')}>Reject</Button>
        </div>
      ),
    },
  ], [decisionActor, decisionReason]);

  return (
    <div className="cpay-kyc-customer-management">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      <Card>
        <Toolbar>
          <strong>KYC & Customer Mgt</strong>
          <Toolbar.Spacer />
          <Button variant="ghost" onClick={() => void load()}>Refresh</Button>
        </Toolbar>
        <p>Review customer KYC/KYB profiles, risk status and open compliance cases from one workflow.</p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          <Select id="kyc-status" label="Profile status" value={statusFilter} onValueChange={setStatusFilter} options={[
            { value: '', label: 'All statuses' },
            { value: 'PENDING', label: 'Pending' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
          ]} />
          <TextField id="kyc-actor" label="Decision actor" value={decisionActor} onValueChange={setDecisionActor} placeholder="admin name" />
          <TextField id="kyc-reason" label="Decision reason" value={decisionReason} onValueChange={setDecisionReason} placeholder="optional reason" />
        </div>
      </Card>

      {loading ? <Spinner label="Loading KYC records" /> : null}
      <Card flush><div style={{ padding: 16 }}><strong>Customer KYC / compliance profiles</strong></div></Card>
      <Table columns={profileColumns} rows={profiles} rowKey={(r, i) => r.id ?? `${r.entityId ?? 'profile'}-${i}`} pageSize={20} emptyText="No KYC profiles found." />

      <Card flush><div style={{ padding: 16 }}><strong>Open compliance cases</strong></div></Card>
      <Table columns={caseColumns} rows={cases} rowKey={(r, i) => r.id ?? i} pageSize={20} emptyText="No open compliance cases." />
    </div>
  );
}
