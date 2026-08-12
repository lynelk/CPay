import React, { useEffect, useState } from 'react';
import { Section, Table, Badge, Alert, Spinner, StatGrid, StatTile } from '../../ui';
import type { Column } from '../../ui';
import {
  useTreasuryPositions,
  useBalanceMonitoringOverview,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { TreasuryPositionRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Admin treasury/balance-monitoring surface: per-currency available/reserved/
 * net-available positions (TreasuryPositionController) plus the S5
 * balance-monitoring overview (BalanceMonitoringController, feature-flagged).
 */

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
  if (s === 'ACTIVE') return 'success';
  if (s === 'SUSPENDED' || s === 'CLOSED') return 'danger';
  if (s === 'PENDING') return 'warning';
  return 'neutral';
}

function formatAmount(value?: number | string): string {
  if (value == null || value === '') return '—';
  const numeric = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(numeric)) return String(value);
  return numeric.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

interface ModuleTreasuryProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleTreasury({ loader, refreshSignal, sessionExpired }: ModuleTreasuryProps): React.ReactElement {
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const positionsQuery = useTreasuryPositions();
  const overviewQuery = useBalanceMonitoringOverview();

  useLoaderSync(loader, positionsQuery.isFetching || overviewQuery.isFetching);
  useRefreshSignal(refreshSignal, [positionsQuery.refetch, overviewQuery.refetch]);

  useEffect(() => {
    const error = positionsQuery.error;
    if (error instanceof ApiError && error.status === 401) {
      sessionExpired?.();
    }
    if (error instanceof ApiError && error.status === 400) {
      setFeedback({ tone: 'error', message: error.message });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positionsQuery.error]);

  const positions = positionsQuery.data ?? [];
  const overview = overviewQuery.data;

  const netTotal = positions.reduce((sum, row) => {
    const value = typeof row.netAvailable === 'number' ? row.netAvailable : Number(row.netAvailable ?? 0);
    return sum + (Number.isNaN(value) ? 0 : value);
  }, 0);

  const columns: Column<TreasuryPositionRow>[] = [
    { key: 'currency', header: 'Currency', accessor: (r) => r.currency ?? '' },
    { key: 'availableBalance', header: 'Available', accessor: (r) => formatAmount(r.availableBalance), numeric: true },
    { key: 'reservedBalance', header: 'Reserved', accessor: (r) => formatAmount(r.reservedBalance), numeric: true },
    { key: 'netAvailable', header: 'Net available', accessor: (r) => formatAmount(r.netAvailable), numeric: true },
    {
      key: 'status',
      header: 'Status',
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status ?? 'UNKNOWN'}</Badge>,
    },
    { key: 'updatedAt', header: 'Updated', accessor: (r) => formatDate(r.updatedAt), sortable: true, sortValue: (r) => r.updatedAt || '' },
  ];

  const overviewEntries =
    overview && typeof overview === 'object'
      ? Object.entries(overview as Record<string, unknown>).filter(
          ([key]) => key !== 'code' && key !== 'message',
        )
      : [];

  return (
    <div className="cpay-treasury">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      {positionsQuery.isLoading ? <Spinner label="Loading treasury positions" /> : null}
      {positionsQuery.error && !(positionsQuery.error instanceof ApiError && positionsQuery.error.status === 400) ? (
        <Alert variant="error">{errorMessage(positionsQuery.error)}</Alert>
      ) : null}

      <StatGrid>
        <StatTile label="Currencies tracked" value={positions.length} />
        <StatTile label="Net available (sum)" value={formatAmount(netTotal)} />
      </StatGrid>

      <Section title="Treasury positions">
        {!positionsQuery.isLoading && !positionsQuery.error ? (
          <Table
            columns={columns}
            rows={positions}
            rowKey={(r) => r.currency ?? ''}
            pageSize={20}
            emptyText="No treasury positions recorded yet."
          />
        ) : null}
      </Section>

      <Section title="Balance monitoring overview">
        {overviewQuery.isLoading ? <Spinner label="Loading balance monitoring" /> : null}
        {overviewQuery.error ? (
          <Alert variant="warning">
            {errorMessage(overviewQuery.error) || 'Balance monitoring is disabled (feature flag off) or unavailable.'}
          </Alert>
        ) : null}
        {!overviewQuery.isLoading && !overviewQuery.error && overviewEntries.length === 0 ? (
          <p>No balance-monitoring data available. Enable the `balance-monitoring` feature flag to populate this view.</p>
        ) : null}
        {!overviewQuery.isLoading && !overviewQuery.error && overviewEntries.length > 0 ? (
          <div className="ios-table-cards" style={{ display: 'grid', gap: 'var(--ios-space-3)' }}>
            {overviewEntries.map(([key, value]) => (
              <div className="ios-table-card" key={key}>
                <div className="ios-table-card__row">
                  <span className="ios-table-card__label">{key}</span>
                  <span className="ios-table-card__value">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '')}</span>
                </div>
              </div>
            ))}
          </div>
        ) : null}
      </Section>
    </div>
  );
}

export default ModuleTreasury;
