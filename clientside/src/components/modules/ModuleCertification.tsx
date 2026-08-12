import React, { useEffect, useState } from 'react';
import { Section, Table, Badge, Alert, Spinner, Select, Button, Toolbar } from '../../ui';
import type { Column, SelectOption } from '../../ui';
import {
  useCertificationSummary,
  useCertificationEvidence,
  useApproveCertificationEvidenceMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { CertificationCoverageRow, CertificationEvidenceRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Admin provider-certification dashboard (audit P4): scenario coverage per
 * provider, evidence rows with status, and the approve action for CAPTURED
 * evidence. Backend (ProviderCertificationController / Service) already
 * existed; this is the missing operations screen.
 */

const PROVIDER_OPTIONS: SelectOption[] = [
  { value: '', label: 'All providers' },
  { value: 'MTN', label: 'MTN' },
  { value: 'AIRTEL', label: 'Airtel' },
  { value: 'AIRTEL_OPENAPI', label: 'Airtel OpenAPI' },
  { value: 'SAFARICOM', label: 'Safaricom' },
  { value: 'YO', label: 'Yo! Payments' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function formatDate(value?: string | null): string {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function statusTone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const s = (status ?? '').toUpperCase();
  if (s === 'APPROVED') return 'success';
  if (s === 'FAILED') return 'danger';
  if (s === 'CAPTURED' || s === 'PENDING') return 'warning';
  return 'neutral';
}

interface ModuleCertificationProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleCertification({ loader, refreshSignal, sessionExpired }: ModuleCertificationProps): React.ReactElement {
  const [provider, setProvider] = useState('');
  const [approvedBy, setApprovedBy] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const summaryQuery = useCertificationSummary();
  const evidenceQuery = useCertificationEvidence(provider);
  const approveMutation = useApproveCertificationEvidenceMutation();

  useLoaderSync(
    loader,
    summaryQuery.isFetching || evidenceQuery.isFetching || approveMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [summaryQuery.refetch, evidenceQuery.refetch]);

  useEffect(() => {
    if (summaryQuery.error instanceof ApiError && summaryQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summaryQuery.error]);

  const summary = summaryQuery.data;
  const coverage = summary?.coverage ?? [];
  const evidence = evidenceQuery.data ?? [];

  function handleApprove(evidenceRow: CertificationEvidenceRow) {
    setFeedback(null);
    approveMutation.mutate(
      { id: Number(evidenceRow.id), approvedBy: approvedBy.trim() || undefined },
      {
        onSuccess: () => setFeedback({ tone: 'success', message: `Evidence #${evidenceRow.id} approved.` }),
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  const coverageColumns: Column<CertificationCoverageRow>[] = [
    { key: 'provider_code', header: 'Provider', accessor: (r) => r.provider_code ?? '*' },
    { key: 'channel_code', header: 'Channel', accessor: (r) => r.channel_code ?? '*' },
    { key: 'scenario_name', header: 'Scenario', accessor: (r) => r.scenario_name ?? '' },
    {
      key: 'approved',
      header: 'Status',
      render: (r) => (r.approved ? <Badge tone="success">APPROVED</Badge> : <Badge tone="warning">PENDING EVIDENCE</Badge>),
    },
    {
      key: 'latest_evidence_at',
      header: 'Latest evidence',
      accessor: (r) => formatDate(r.latest_evidence_at),
      sortable: true,
      sortValue: (r) => r.latest_evidence_at || '',
    },
  ];

  const evidenceColumns: Column<CertificationEvidenceRow>[] = [
    { key: 'id', header: 'ID', accessor: (r) => String(r.id ?? '') },
    { key: 'provider_code', header: 'Provider', accessor: (r) => r.provider_code ?? '' },
    { key: 'channel_code', header: 'Channel', accessor: (r) => r.channel_code ?? '' },
    { key: 'scenario_name', header: 'Scenario', accessor: (r) => r.scenario_name ?? '' },
    { key: 'evidence_type', header: 'Type', accessor: (r) => r.evidence_type ?? '' },
    {
      key: 'evidence_status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.evidence_status)}>{r.evidence_status ?? 'UNKNOWN'}</Badge>,
    },
    {
      key: 'created_at',
      header: 'Captured',
      accessor: (r) => formatDate(r.created_at),
      sortable: true,
      sortValue: (r) => r.created_at || '',
    },
    {
      key: 'actions',
      header: 'Action',
      render: (r) =>
        (r.evidence_status ?? '').toUpperCase() === 'CAPTURED' ? (
          <Button variant="primary" className="ios-btn--sm" onClick={() => handleApprove(r)}>
            Approve
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="cpay-certification">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      {summaryQuery.isLoading ? <Spinner label="Loading certification summary" /> : null}
      {summaryQuery.error ? <Alert variant="error">{errorMessage(summaryQuery.error)}</Alert> : null}

      <Section title="Required scenario coverage">
        {!summaryQuery.isLoading && !summaryQuery.error ? (
          <Table
            columns={coverageColumns}
            rows={coverage}
            rowKey={(r) => `${r.provider_code ?? '*'}-${r.channel_code ?? '*'}-${r.scenario_name ?? ''}`}
            pageSize={20}
            emptyText="No certification requirements configured."
          />
        ) : null}
      </Section>

      <Section
        title="Certification evidence"
        actions={
          <Toolbar>
            <Select id="cert-provider" value={provider} options={PROVIDER_OPTIONS} onValueChange={setProvider} />
          </Toolbar>
        }
      >
        {evidenceQuery.isLoading ? <Spinner label="Loading evidence" /> : null}
        {evidenceQuery.error ? <Alert variant="error">{errorMessage(evidenceQuery.error)}</Alert> : null}
        {!evidenceQuery.isLoading && !evidenceQuery.error ? (
          <Table
            columns={evidenceColumns}
            rows={evidence}
            rowKey={(r) => r.id ?? 0}
            pageSize={20}
            emptyText="No certification evidence captured."
          />
        ) : null}
      </Section>

      <Section title="Approve captured evidence">
        <p style={{ marginBottom: 'var(--ios-space-3)' }}>
          Approving evidence marks it APPROVED with the reviewer stamped. Evidence must be CAPTURED
          before it can be approved.
        </p>
        <input
          aria-label="Approved by"
          className="ios-textfield"
          value={approvedBy}
          onChange={(e) => setApprovedBy(e.target.value)}
          placeholder="Approved by (optional)"
        />
      </Section>
    </div>
  );
}

export default ModuleCertification;
