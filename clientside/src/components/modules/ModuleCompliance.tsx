import React, { useEffect, useState } from 'react';
import {
  Section,
  Table,
  Badge,
  Alert,
  Spinner,
  Select,
  StatGrid,
  StatTile,
} from '../../ui';
import type { Column, SelectOption } from '../../ui';
import {
  useComplianceSummary,
  useOpenComplianceCases,
  useComplianceCaseDecisionMutation,
  useComplianceProfiles,
  useReviewComplianceEventMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type {
  ComplianceCaseRow,
  ComplianceProfileRow,
  ComplianceSummary,
} from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Admin compliance/AML surface (audit P7): summary counts, open compliance
 * cases with decision actions, screening events review, and compliance
 * profiles. Backend (ComplianceReportingController / ComplianceCaseService)
 * already existed; this is the missing human workflow screen.
 */

const DECISION_OPTIONS: SelectOption[] = [
  { value: 'REVIEWED', label: 'REVIEWED' },
  { value: 'REVIEW', label: 'REVIEW' },
  { value: 'ALLOW', label: 'ALLOW' },
  { value: 'BLOCK', label: 'BLOCK' },
];

const PROFILE_STATUS_OPTIONS: SelectOption[] = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'PENDING' },
  { value: 'IN_REVIEW', label: 'IN_REVIEW' },
  { value: 'APPROVED', label: 'APPROVED' },
  { value: 'REJECTED', label: 'REJECTED' },
];

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
  if (s === 'APPROVED' || s === 'ALLOW' || s === 'CLOSED' || s === 'REVIEWED') return 'success';
  if (s === 'BLOCK' || s === 'REJECTED' || s === 'CRITICAL' || s === 'HIGH') return 'danger';
  if (s === 'OPEN' || s === 'IN_REVIEW' || s === 'PENDING' || s === 'REVIEW') return 'warning';
  return 'neutral';
}

interface ModuleComplianceProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleCompliance({ loader, refreshSignal, sessionExpired }: ModuleComplianceProps): React.ReactElement {
  const [profileStatus, setProfileStatus] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const summaryQuery = useComplianceSummary();
  const casesQuery = useOpenComplianceCases();
  const profilesQuery = useComplianceProfiles(profileStatus);
  const decideMutation = useComplianceCaseDecisionMutation();
  const reviewEventMutation = useReviewComplianceEventMutation();

  useLoaderSync(
    loader,
    summaryQuery.isFetching || casesQuery.isFetching || profilesQuery.isFetching ||
      decideMutation.isPending || reviewEventMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [summaryQuery.refetch, casesQuery.refetch, profilesQuery.refetch]);

  useEffect(() => {
    if (summaryQuery.error instanceof ApiError && summaryQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summaryQuery.error]);

  const summary = summaryQuery.data ?? ({} as ComplianceSummary);
  const cases = casesQuery.data ?? [];
  const profiles = profilesQuery.data ?? [];

  function handleDecide(caseRow: ComplianceCaseRow, decision: string) {
    setFeedback(null);
    decideMutation.mutate(
      { id: Number(caseRow.id), decision, actor: 'admin' },
      {
        onSuccess: () => setFeedback({ tone: 'success', message: `Case ${caseRow.case_reference ?? caseRow.id} → ${decision}` }),
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  const caseColumns: Column<ComplianceCaseRow>[] = [
    { key: 'case_reference', header: 'Case', accessor: (r) => r.case_reference ?? String(r.id ?? '') },
    { key: 'case_type', header: 'Type', accessor: (r) => r.case_type ?? '' },
    {
      key: 'severity',
      header: 'Severity',
      render: (r) => <Badge tone={statusTone(r.severity)}>{r.severity ?? 'UNKNOWN'}</Badge>,
    },
    {
      key: 'case_status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.case_status)}>{r.case_status ?? 'UNKNOWN'}</Badge>,
    },
    { key: 'source_reference', header: 'Source ref', accessor: (r) => r.source_reference ?? '' },
    { key: 'entity_type', header: 'Entity', accessor: (r) => `${r.entity_type ?? ''} ${r.entity_id ?? ''}`.trim() },
    {
      key: 'created_at',
      header: 'Created',
      accessor: (r) => formatDate(r.created_at),
      sortable: true,
      sortValue: (r) => r.created_at || '',
    },
    {
      key: 'actions',
      header: 'Decision',
      render: (r) => (
        <div style={{ display: 'flex', gap: 'var(--ios-space-2)' }}>
          <Select
            id={`case-${r.id}`}
            value=""
            options={DECISION_OPTIONS}
            onValueChange={(v) => {
              if (v) handleDecide(r, v);
            }}
            placeholder="Decide…"
            style={{ minWidth: 120 }}
          />
        </div>
      ),
    },
  ];

  const profileColumns: Column<ComplianceProfileRow>[] = [
    { key: 'entity_id', header: 'Entity', accessor: (r) => String(r.entity_id ?? '') },
    { key: 'entity_type', header: 'Type', accessor: (r) => r.entity_type ?? '' },
    { key: 'profile_type', header: 'Profile', accessor: (r) => r.profile_type ?? '' },
    { key: 'tier', header: 'Tier', accessor: (r) => r.tier ?? '' },
    {
      key: 'status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status ?? 'UNKNOWN'}</Badge>,
    },
    { key: 'risk_rating', header: 'Risk rating', accessor: (r) => r.risk_rating ?? '' },
    { key: 'verified_by', header: 'Verified by', accessor: (r) => r.verified_by ?? '' },
  ];

  return (
    <div className="cpay-compliance">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      {summaryQuery.isLoading ? <Spinner label="Loading compliance summary" /> : null}
      {summaryQuery.error ? <Alert variant="error">{errorMessage(summaryQuery.error)}</Alert> : null}

      <StatGrid>
        <StatTile label="Open cases" value={summary.openComplianceCases ?? 0} />
        <StatTile label="High-severity cases" value={summary.highSeverityComplianceCases ?? 0} deltaDirection={summary.highSeverityComplianceCases ? 'down' : undefined} />
        <StatTile label="Pending profiles" value={summary.pendingComplianceProfiles ?? 0} />
        <StatTile label="Open control events" value={summary.openControlEvents ?? 0} />
        <StatTile label="High-severity controls" value={summary.highSeverityControlEvents ?? 0} />
        <StatTile label="Parked callbacks" value={summary.parkedCallbacks ?? 0} />
      </StatGrid>

      <Section title="Open compliance cases">
        {casesQuery.isLoading ? <Spinner label="Loading cases" /> : null}
        {casesQuery.error ? <Alert variant="error">{errorMessage(casesQuery.error)}</Alert> : null}
        {!casesQuery.isLoading && !casesQuery.error ? (
          <Table
            columns={caseColumns}
            rows={cases}
            rowKey={(r) => r.id ?? 0}
            pageSize={20}
            emptyText="No open compliance cases."
          />
        ) : null}
      </Section>

      <Section
        title="Compliance profiles"
        actions={
          <div style={{ minWidth: 180 }}>
            <Select id="profile-status" value={profileStatus} options={PROFILE_STATUS_OPTIONS} onValueChange={setProfileStatus} />
          </div>
        }
      >
        {profilesQuery.isLoading ? <Spinner label="Loading profiles" /> : null}
        {profilesQuery.error ? <Alert variant="error">{errorMessage(profilesQuery.error)}</Alert> : null}
        {!profilesQuery.isLoading && !profilesQuery.error ? (
          <Table
            columns={profileColumns}
            rows={profiles}
            rowKey={(r) => r.id ?? 0}
            pageSize={20}
            emptyText="No compliance profiles found."
          />
        ) : null}
      </Section>
    </div>
  );
}

export default ModuleCompliance;
