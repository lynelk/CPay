import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge,
  Button,
  Select,
  Spinner,
  Table,
  Tabs,
} from '../../ui';
import type { Column, SelectOption, TabItem } from '../../ui';
import {
  useComplianceSummary,
  useOpenComplianceCases,
  useComplianceCaseDecisionMutation,
  useComplianceProfiles,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type {
  ComplianceCaseRow,
  ComplianceProfileRow,
  ComplianceSummary,
} from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

interface ModuleComplianceProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

type View = 'overview' | 'cases' | 'profiles';

const TABS: TabItem[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'cases', label: 'Cases' },
  { key: 'profiles', label: 'KYC / KYB profiles' },
];

const DECISION_OPTIONS: SelectOption[] = [
  { value: 'REVIEWED', label: 'Mark reviewed' },
  { value: 'REVIEW', label: 'Keep in review' },
  { value: 'ALLOW', label: 'Allow' },
  { value: 'BLOCK', label: 'Block' },
];

const PROFILE_STATUS_OPTIONS: SelectOption[] = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_REVIEW', label: 'In review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

const identityCapabilities = [
  'NIN / identity validation',
  'KYC & KYB',
  'CRB reports',
  'Credit scoring',
  '0–1000 normalized scores',
  'Raw provider evidence',
  'Bank-account verification',
  'TIN / business registry',
];

function rawError(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'The live request could not be completed.';
}

function friendlyError(error: unknown): string {
  const message = rawError(error);
  if (/internal application error|internal server error|something went wrong/i.test(message)) {
    return 'Cito could not load this live section. Other compliance functions remain available while the request is retried.';
  }
  return message;
}

function formatDate(value?: string): string {
  if (!value) return 'Not recorded';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function statusTone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  const s = (status ?? '').toUpperCase();
  if (['APPROVED', 'ALLOW', 'CLOSED', 'REVIEWED'].includes(s)) return 'success';
  if (['BLOCK', 'REJECTED', 'CRITICAL', 'HIGH'].includes(s)) return 'danger';
  if (['OPEN', 'IN_REVIEW', 'PENDING', 'REVIEW'].includes(s)) return 'warning';
  return 'neutral';
}

function HumanStatus({ value }: { value?: string }): React.ReactElement {
  const status = value || 'UNKNOWN';
  return <Badge tone={statusTone(status)}>{status.replaceAll('_', ' ')}</Badge>;
}

function InlineError({ title, error, retry }: { title: string; error: unknown; retry: () => void }): React.ReactElement {
  return (
    <div className="cito-inline-error" role="alert">
      <div>
        <strong>{title}</strong>
        <p>{friendlyError(error)} No substitute or fallback data is being shown.</p>
      </div>
      <Button variant="ghost" onClick={retry}>Retry</Button>
    </div>
  );
}

function PurposeEmpty({ title, copy }: { title: string; copy: string }): React.ReactElement {
  return <div className="cito-purpose-empty"><div><strong>{title}</strong><p>{copy}</p></div></div>;
}

export default function ModuleCompliance({ loader, refreshSignal, sessionExpired }: ModuleComplianceProps): React.ReactElement {
  const navigate = useNavigate();
  const [view, setView] = useState<View>('overview');
  const [profileStatus, setProfileStatus] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const summaryQuery = useComplianceSummary();
  const casesQuery = useOpenComplianceCases();
  const profilesQuery = useComplianceProfiles(profileStatus);
  const decideMutation = useComplianceCaseDecisionMutation();

  useLoaderSync(loader, summaryQuery.isFetching || casesQuery.isFetching || profilesQuery.isFetching || decideMutation.isPending);
  useRefreshSignal(refreshSignal, [summaryQuery.refetch, casesQuery.refetch, profilesQuery.refetch]);

  useEffect(() => {
    const errors = [summaryQuery.error, casesQuery.error, profilesQuery.error];
    if (errors.some((error) => error instanceof ApiError && error.status === 401)) sessionExpired?.();
  }, [summaryQuery.error, casesQuery.error, profilesQuery.error, sessionExpired]);

  const summary = summaryQuery.data ?? ({} as ComplianceSummary);
  const cases = casesQuery.data ?? [];
  const profiles = profilesQuery.data ?? [];

  const needsAttention = Number(summary.highSeverityComplianceCases ?? 0)
    + Number(summary.pendingComplianceProfiles ?? 0)
    + Number(summary.highSeverityControlEvents ?? 0)
    + Number(summary.parkedCallbacks ?? 0);

  const caseColumns = useMemo<Column<ComplianceCaseRow>[]>(() => [
    {
      key: 'case_reference',
      header: 'Case',
      render: (row) => <div><strong>{row.case_reference ?? String(row.id ?? '')}</strong><small style={{ display: 'block', color: 'var(--cito-muted)', marginTop: 3 }}>{row.case_type ?? 'Compliance review'}</small></div>,
    },
    { key: 'severity', header: 'Risk', render: (row) => <HumanStatus value={row.severity} /> },
    { key: 'case_status', header: 'Status', render: (row) => <HumanStatus value={row.case_status} /> },
    {
      key: 'entity_type',
      header: 'Subject',
      accessor: (row) => `${row.entity_type ?? 'Entity'} ${row.entity_id ?? ''}`.trim(),
    },
    { key: 'source_reference', header: 'Source', accessor: (row) => row.source_reference ?? 'Not recorded' },
    {
      key: 'created_at',
      header: 'Opened',
      accessor: (row) => formatDate(row.created_at),
      sortable: true,
      sortValue: (row) => row.created_at || '',
    },
    {
      key: 'actions',
      header: 'Next action',
      render: (row) => (
        <Select
          id={`case-${row.id}`}
          value=""
          options={DECISION_OPTIONS}
          onValueChange={(decision) => {
            if (!decision) return;
            setFeedback(null);
            decideMutation.mutate(
              { id: Number(row.id), decision, actor: 'admin' },
              {
                onSuccess: () => setFeedback({ tone: 'success', message: `Case ${row.case_reference ?? row.id} updated to ${decision.replaceAll('_', ' ')}.` }),
                onError: (error) => setFeedback({ tone: 'error', message: friendlyError(error) }),
              },
            );
          }}
          placeholder="Choose action…"
          style={{ minWidth: 150 }}
        />
      ),
    },
  ], [decideMutation]);

  const profileColumns = useMemo<Column<ComplianceProfileRow>[]>(() => [
    {
      key: 'entity_id',
      header: 'Entity',
      render: (row) => <div><strong>{String(row.entity_id ?? '')}</strong><small style={{ display: 'block', color: 'var(--cito-muted)', marginTop: 3 }}>{row.entity_type ?? 'Entity'}</small></div>,
    },
    { key: 'profile_type', header: 'Profile', accessor: (row) => row.profile_type?.replaceAll('_', ' ') ?? 'General' },
    { key: 'tier', header: 'Tier', accessor: (row) => row.tier ?? '—' },
    { key: 'status', header: 'Status', render: (row) => <HumanStatus value={row.status} /> },
    { key: 'risk_rating', header: 'Risk rating', render: (row) => <HumanStatus value={row.risk_rating} /> },
    { key: 'verified_by', header: 'Verified by', accessor: (row) => row.verified_by ?? 'Not yet verified' },
  ], []);

  function renderOverview(): React.ReactElement {
    return (
      <>
        <div className="cito-compliance-posture">
          <div className="cito-posture-primary">
            <span>Current posture</span>
            <strong>{needsAttention > 0 ? `${needsAttention} item${needsAttention === 1 ? '' : 's'} need attention` : 'No urgent compliance actions'}</strong>
            <span>{needsAttention > 0 ? 'Prioritise high-risk cases and incomplete identity profiles.' : 'Live queues are clear based on the data currently available.'}</span>
          </div>
          <div className="cito-posture-metric"><span>Open cases</span><strong>{summary.openComplianceCases ?? 0}</strong></div>
          <div className="cito-posture-metric"><span>High risk</span><strong>{summary.highSeverityComplianceCases ?? 0}</strong></div>
          <div className="cito-posture-metric"><span>Pending profiles</span><strong>{summary.pendingComplianceProfiles ?? 0}</strong></div>
        </div>

        {summaryQuery.error ? <InlineError title="Risk posture could not be refreshed" error={summaryQuery.error} retry={() => void summaryQuery.refetch()} /> : null}

        <section className="cito-compliance-panel">
          <div className="cito-section-heading">
            <div>
              <h3>Identity, credit & scoring services</h3>
              <p>Verification and decisioning should be visible as a service family, not hidden inside compliance plumbing.</p>
            </div>
            <Button variant="ghost" onClick={() => navigate('/bo/admin/platform')}>Service portfolio</Button>
          </div>
          <div className="cito-service-card__capabilities">
            {identityCapabilities.map((capability) => <span key={capability}>{capability}</span>)}
          </div>
          <p style={{ color: 'var(--cito-muted)', fontSize: 12.5, lineHeight: 1.55, margin: '8px 0 0' }}>
            Provider availability is explicit: NIN validation, CRB data, scores, bank checks and registry lookups only become operational when approved provider integrations are configured and entitled. Normalized scoring should retain the original provider score and evidence alongside the Cito 0–1000 representation.
          </p>
          <div style={{ display: 'flex', gap: 8, marginTop: 14, flexWrap: 'wrap' }}>
            <Button variant="primary" onClick={() => setView('cases')}>Review cases</Button>
            <Button variant="ghost" onClick={() => setView('profiles')}>Review KYC / KYB</Button>
            <Button variant="ghost" onClick={() => navigate('/bo/admin/providers-integrations')}>Configure providers</Button>
          </div>
        </section>

        <section className="cito-compliance-panel">
          <div className="cito-section-heading">
            <div><h3>Control watch</h3><p>Operational controls that can materially affect risk and customer outcomes.</p></div>
          </div>
          <div className="cito-compliance-posture" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
            <div className="cito-posture-metric"><span>Open control events</span><strong>{summary.openControlEvents ?? 0}</strong></div>
            <div className="cito-posture-metric"><span>High-severity controls</span><strong>{summary.highSeverityControlEvents ?? 0}</strong></div>
            <div className="cito-posture-metric"><span>Parked callbacks</span><strong>{summary.parkedCallbacks ?? 0}</strong></div>
          </div>
        </section>
      </>
    );
  }

  function renderCases(): React.ReactElement {
    return (
      <section className="cito-compliance-panel">
        <div className="cito-section-heading">
          <div><h3>Cases requiring review</h3><p>Work from the queue by risk and age instead of searching through generic compliance records.</p></div>
          <Badge tone={cases.length ? 'warning' : 'success'}>{cases.length ? `${cases.length} open` : 'Queue clear'}</Badge>
        </div>
        {casesQuery.isLoading ? <Spinner label="Loading compliance cases" /> : null}
        {casesQuery.error ? <InlineError title="Compliance cases could not be loaded" error={casesQuery.error} retry={() => void casesQuery.refetch()} /> : null}
        {!casesQuery.isLoading && !casesQuery.error && cases.length === 0 ? <PurposeEmpty title="No open compliance cases" copy="New screening, identity or control cases will appear here when they require human review." /> : null}
        {!casesQuery.isLoading && !casesQuery.error && cases.length > 0 ? <Table columns={caseColumns} rows={cases} rowKey={(row) => row.id ?? 0} pageSize={20} /> : null}
      </section>
    );
  }

  function renderProfiles(): React.ReactElement {
    return (
      <section className="cito-compliance-panel">
        <div className="cito-section-heading">
          <div><h3>KYC / KYB profiles</h3><p>Identity and business-verification profiles with status and risk context.</p></div>
          <div style={{ minWidth: 190 }}><Select id="profile-status" value={profileStatus} options={PROFILE_STATUS_OPTIONS} onValueChange={setProfileStatus} /></div>
        </div>
        {profilesQuery.isLoading ? <Spinner label="Loading compliance profiles" /> : null}
        {profilesQuery.error ? <InlineError title="KYC / KYB profiles could not be loaded" error={profilesQuery.error} retry={() => void profilesQuery.refetch()} /> : null}
        {!profilesQuery.isLoading && !profilesQuery.error && profiles.length === 0 ? <PurposeEmpty title={profileStatus ? `No ${profileStatus.replaceAll('_', ' ').toLowerCase()} profiles` : 'No compliance profiles found'} copy="Profiles will appear here as merchants, beneficial owners and other governed entities enter verification journeys." /> : null}
        {!profilesQuery.isLoading && !profilesQuery.error && profiles.length > 0 ? <Table columns={profileColumns} rows={profiles} rowKey={(row) => row.id ?? 0} pageSize={20} /> : null}
      </section>
    );
  }

  return (
    <div className="cito-compliance-workspace">
      <header className="cito-workspace-hero">
        <div>
          <p className="cito-workspace-hero__eyebrow">Risk, identity and decisioning</p>
          <h2>Protect the platform without hiding the work</h2>
          <p>Review the cases that need attention, govern KYC/KYB and bring identity, CRB and scoring services into the same operational context.</p>
        </div>
        <div className="cito-workspace-hero__actions">
          <Button variant="ghost" onClick={() => navigate('/bo/admin/providers-integrations')}>Provider readiness</Button>
          <Button variant="primary" onClick={() => setView('cases')}>Open review queue</Button>
        </div>
      </header>

      {feedback ? <div className={`cito-inline-error${feedback.tone === 'success' ? ' cito-inline-success' : ''}`} role="status"><div><strong>{feedback.tone === 'success' ? 'Case updated' : 'Action failed'}</strong><p>{feedback.message}</p></div></div> : null}

      <div className="cito-compliance-tabs"><Tabs items={TABS} active={view} onChange={(key) => setView(key as View)} /></div>
      {view === 'overview' ? renderOverview() : null}
      {view === 'cases' ? renderCases() : null}
      {view === 'profiles' ? renderProfiles() : null}
    </div>
  );
}
