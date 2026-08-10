import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Select, Spinner, Table, Tabs, TextField, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import { ApiError, request } from '../../../shared/api/httpClient';

interface KycRow {
  id?: number;
  record_type?: string;
  label?: string;
  status?: string;
  created_at?: string;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function statusTone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const value = String(status ?? '').toUpperCase();
  if (['APPROVED', 'VERIFIED', 'CLEARED'].includes(value)) return 'success';
  if (['REJECTED', 'FAILED', 'BLOCKED'].includes(value)) return 'danger';
  if (['PENDING', 'REVIEW'].includes(value)) return 'warning';
  return 'neutral';
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

export default function MerchantModuleKyc({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [rows, setRows] = useState<KycRow[]>([]);
  const [tab, setTab] = useState('status');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState('');

  const [ownerName, setOwnerName] = useState('');
  const [ownerIdType, setOwnerIdType] = useState('NATIONAL_ID');
  const [ownerIdValue, setOwnerIdValue] = useState('');
  const [ownership, setOwnership] = useState('');

  const [documentType, setDocumentType] = useState('CERTIFICATE_OF_INCORPORATION');
  const [storageRef, setStorageRef] = useState('');
  const [documentHash, setDocumentHash] = useState('');

  async function load() {
    setLoading(true); setError(null); loader?.('START');
    try {
      const data = await request<KycRow[]>('/api/v2/merchant-self-service/kyc');
      setRows(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally { setLoading(false); loader?.('STOP'); }
  }

  useEffect(() => { void load(); }, [refreshSignal]);

  async function addOwner() {
    setError(null); setFeedback('');
    try {
      await request('/api/v2/merchant-self-service/kyc/beneficial-owners', {
        method: 'POST',
        body: JSON.stringify({ fullName: ownerName, idType: ownerIdType, idValue: ownerIdValue, ownershipPercent: ownership }),
      });
      setFeedback('Beneficial owner submitted for review.');
      setOwnerName(''); setOwnerIdValue(''); setOwnership('');
      await load();
    } catch (e) { setError(e); }
  }

  async function addDocument() {
    setError(null); setFeedback('');
    try {
      await request('/api/v2/merchant-self-service/kyc/documents', {
        method: 'POST',
        body: JSON.stringify({ documentType, storageRef, documentHash }),
      });
      setFeedback('KYC document reference submitted for verification.');
      setStorageRef(''); setDocumentHash('');
      await load();
    } catch (e) { setError(e); }
  }

  const columns = useMemo<Column<KycRow>[]>(() => [
    { key: 'type', header: 'Type', accessor: (r) => r.record_type ?? '' },
    { key: 'label', header: 'Record', accessor: (r) => r.label ?? '' },
    { key: 'status', header: 'Status', render: (r) => <Badge tone={statusTone(r.status)}>{r.status ?? 'PENDING'}</Badge> },
    { key: 'created', header: 'Submitted', accessor: (r) => r.created_at ?? '' },
  ], []);

  return (
    <div className="cpay-merchant-kyc">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}
      <Card>
        <Toolbar><strong>KYC & Customer Mgt</strong><Toolbar.Spacer /><Button variant="ghost" onClick={() => void load()}>Refresh</Button></Toolbar>
        <p>Track your business verification records and submit beneficial-owner or document references for review.</p>
        <Tabs items={[{ key: 'status', label: 'Verification Status' }, { key: 'owners', label: 'Beneficial Owners' }, { key: 'documents', label: 'Documents' }]} active={tab} onChange={setTab} />
      </Card>
      {loading ? <Spinner label="Loading KYC records" /> : null}
      {tab === 'status' ? <Table columns={columns} rows={rows} rowKey={(r, i) => r.id ?? i} pageSize={20} emptyText="No KYC records submitted yet." /> : null}
      {tab === 'owners' ? <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12 }}>
          <TextField id="owner-name" label="Full name" value={ownerName} onValueChange={setOwnerName} />
          <Select id="owner-id-type" label="ID type" value={ownerIdType} onValueChange={setOwnerIdType} options={[{ value: 'NATIONAL_ID', label: 'National ID' }, { value: 'PASSPORT', label: 'Passport' }, { value: 'OTHER', label: 'Other' }]} />
          <TextField id="owner-id" label="ID value" value={ownerIdValue} onValueChange={setOwnerIdValue} />
          <TextField id="owner-percent" label="Ownership %" value={ownership} onValueChange={setOwnership} inputMode="decimal" />
        </div>
        <div style={{ marginTop: 16 }}><Button onClick={() => void addOwner()}>Submit owner</Button></div>
      </Card> : null}
      {tab === 'documents' ? <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 12 }}>
          <Select id="doc-type" label="Document type" value={documentType} onValueChange={setDocumentType} options={[{ value: 'CERTIFICATE_OF_INCORPORATION', label: 'Certificate of Incorporation' }, { value: 'TRADING_LICENSE', label: 'Trading License' }, { value: 'TAX_CERTIFICATE', label: 'Tax Certificate' }, { value: 'OTHER', label: 'Other' }]} />
          <TextField id="doc-ref" label="Secure storage reference" value={storageRef} onValueChange={setStorageRef} placeholder="document store reference" />
          <TextField id="doc-hash" label="Document hash (optional)" value={documentHash} onValueChange={setDocumentHash} placeholder="SHA-256 or provider hash" />
        </div>
        <div style={{ marginTop: 16 }}><Button onClick={() => void addDocument()}>Submit document reference</Button></div>
      </Card> : null}
    </div>
  );
}
