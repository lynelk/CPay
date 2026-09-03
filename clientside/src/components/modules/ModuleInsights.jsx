import { useEffect } from 'react';
import { withRouter } from '../../shared/router/compat';
import LinearChart from './LinearChart';
import { Button, Badge } from '../../ui';
import { useAuth } from '../../shared/useAuth';
import {
  useAdminDashboardCharts,
  useAdminTransactions,
  usePortalDashboardSummary,
  useLoaderSync,
  useRefreshSignal,
  SessionExpiredError,
} from '../../shared/api/hooks';

const RECENT_ACTIVITY_LIMIT = 8;
const READY_CHANNEL_STATUSES = new Set(['ACTIVE', 'SANDBOX_TESTED', 'SUBMITTED_FOR_APPROVAL']);

function numberValue(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatAmount(value) {
  const amount = numberValue(value);
  if (amount <= 0) return 'No activity';
  return `UGX ${new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(amount)}`;
}

function formatCount(value) {
  const count = numberValue(value);
  return new Intl.NumberFormat('en-US').format(count);
}

function serviceState(status) {
  switch (status) {
    case 'ACTIVE': return { label: 'Operating', tone: 'success' };
    case 'SANDBOX_TESTED':
    case 'SUBMITTED_FOR_APPROVAL': return { label: 'Ready', tone: 'info' };
    case 'DEGRADED': return { label: 'Degraded', tone: 'warning' };
    case 'FAILED':
    case 'DISABLED':
    case 'SUSPENDED': return { label: 'Needs Attention', tone: 'danger' };
    default: return { label: 'Setup Required', tone: 'neutral' };
  }
}

function transactionTone(status) {
  if (status === 'SUCCESSFUL') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'PENDING') return 'warning';
  return 'neutral';
}

function ModuleInsightsC(props) {
  const { loader, refreshSignal, sessionExpired, history } = props;
  const { hasPrivilege } = useAuth('admin');
  const canViewTransactions = hasPrivilege('ACCESS_TRANSACTION_LOG');

  const summaryQuery = usePortalDashboardSummary();
  const charts = useAdminDashboardCharts();
  const recentActivityQuery = useAdminTransactions(
    { value: '', category: 'all' },
    RECENT_ACTIVITY_LIMIT,
    canViewTransactions,
  );

  const busy = summaryQuery.isFetching
    || charts.payinsVsPayouts.isFetching
    || charts.txVolumes.isFetching
    || recentActivityQuery.isFetching;
  useLoaderSync(loader, busy);

  useRefreshSignal(refreshSignal, [
    summaryQuery.refetch,
    charts.payinsVsPayouts.refetch,
    charts.txVolumes.refetch,
    recentActivityQuery.refetch,
  ]);

  const errors = [
    summaryQuery.error,
    charts.payinsVsPayouts.error,
    charts.txVolumes.error,
    recentActivityQuery.error,
  ].filter(Boolean);

  useEffect(() => {
    if (errors.some((error) => error instanceof SessionExpiredError)) {
      sessionExpired?.();
    }
  }, [errors, sessionExpired]);

  const summary = summaryQuery.data || {};
  const channels = Array.isArray(summary.activeChannels) ? summary.activeChannels : [];
  const failedTransactions = numberValue(summary.failedTransactions);
  const transactionCount = numberValue(summary.transactions);
  const successRate = transactionCount > 0
    ? `${Math.max(0, ((transactionCount - failedTransactions) / transactionCount) * 100).toFixed(1)}%`
    : 'No activity';

  const needsAttention = [];
  if (errors.length > 0) {
    needsAttention.push({
      title: 'One or more insight sources could not be refreshed',
      detail: errors[0]?.message || 'Review the data source and retry.',
      route: null,
    });
  }
  if (failedTransactions > 0) {
    needsAttention.push({
      title: `${formatCount(failedTransactions)} failed transaction${failedTransactions === 1 ? '' : 's'}`,
      detail: 'Review failures before they become customer-care or reconciliation issues.',
      route: '/bo/admin/money-operations',
    });
  }
  channels
    .filter((channel) => !READY_CHANNEL_STATUSES.has(channel.status))
    .slice(0, 3)
    .forEach((channel) => {
      const state = serviceState(channel.status);
      needsAttention.push({
        title: `${channel.display_name || channel.channel_code || 'Service'}: ${state.label}`,
        detail: channel.environment ? `Environment: ${channel.environment}` : 'Provider configuration requires review.',
        route: '/bo/admin/providers-integrations',
      });
    });

  const recentRows = Array.isArray(recentActivityQuery.data?.rows)
    ? [...recentActivityQuery.data.rows].sort((a, b) => String(b.created_on || '').localeCompare(String(a.created_on || ''))).slice(0, RECENT_ACTIVITY_LIMIT)
    : [];

  const metrics = [
    { label: 'Collections', value: formatAmount(summary.payIns), meta: 'incoming payments' },
    { label: 'Disbursements', value: formatAmount(summary.payOuts), meta: 'outgoing payments' },
    { label: 'Transactions', value: formatCount(transactionCount), meta: 'recorded today' },
    { label: 'Success rate', value: successRate, meta: 'recorded transactions' },
    { label: 'Merchants', value: formatCount(summary.merchants), meta: 'visible to this account' },
  ];

  return (
    <div className="cpay-dashboard cpay-dashboard--console" data-testid="admin-insights">
      <section className="cpay-dashboard-toolbar">
        <div className="cpay-dashboard-toolbar-copy">
          <h2>Insights</h2>
          <p>A live, action-oriented view of what is happening across Cito right now.</p>
        </div>
        <div className="cpay-dashboard-actions">
          <Badge tone="info">{summary.environment || 'Current environment'}</Badge>
          <Button variant="ghost" onClick={() => history.push('/bo/admin/money-operations')}>Money Operations</Button>
          <Button variant="ghost" onClick={() => history.push('/bo/admin/providers-integrations')}>Providers</Button>
        </div>
      </section>

      <section className="cpay-dashboard-pinned" aria-labelledby="needs-attention-heading">
        <header className="cpay-dashboard-section-header">
          <div>
            <span>Priority</span>
            <h3 id="needs-attention-heading">Needs Attention</h3>
          </div>
          <p>Exceptions and incomplete service states that may require action.</p>
        </header>
        <div className="cpay-dashboard-snapshot-grid">
          {needsAttention.length === 0 ? (
            <article className="cpay-dashboard-card">
              <strong>No active issues detected</strong>
              <p>Available insight sources are not reporting an exception that needs intervention.</p>
            </article>
          ) : needsAttention.map((item, index) => (
            <article className="cpay-dashboard-card" key={`${item.title}-${index}`}>
              <strong>{item.title}</strong>
              <p>{item.detail}</p>
              {item.route ? <Button variant="ghost" onClick={() => history.push(item.route)}>Review</Button> : null}
            </article>
          ))}
        </div>
      </section>

      <section className="cpay-dashboard-pinned" aria-labelledby="today-business-heading">
        <header className="cpay-dashboard-section-header">
          <div>
            <span>Today</span>
            <h3 id="today-business-heading">Today&apos;s Business</h3>
          </div>
          <p>Live totals only. Missing data is shown as no activity rather than being guessed.</p>
        </header>
        <div className="cpay-dashboard-snapshot-grid">
          {metrics.map((metric) => (
            <article className="cpay-dashboard-card" key={metric.label}>
              <span>{metric.label}</span>
              <div className="cpay-dashboard-metric">{metric.value}</div>
              <p>{metric.meta}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="cpay-dashboard-pinned" aria-labelledby="services-heading">
        <header className="cpay-dashboard-section-header">
          <div>
            <span>Capability health</span>
            <h3 id="services-heading">Services</h3>
          </div>
          <p>Payment and service channels grouped by their actual configuration state.</p>
        </header>
        <div className="cpay-dashboard-snapshot-grid">
          {channels.length === 0 ? (
            <article className="cpay-dashboard-card">
              <strong>Setup Required</strong>
              <p>No service channels are reported for this environment.</p>
              <Button variant="ghost" onClick={() => history.push('/bo/admin/providers-integrations')}>Configure services</Button>
            </article>
          ) : channels.map((channel, index) => {
            const state = serviceState(channel.status);
            return (
              <article className="cpay-dashboard-card" key={`${channel.channel_code || channel.display_name}-${index}`}>
                <span>{channel.environment || 'Current environment'}</span>
                <h3>{channel.display_name || channel.channel_code || 'Service'}</h3>
                <Badge tone={state.tone}>{state.label}</Badge>
                <p>{channel.status || 'Not configured'}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section className="cpay-dashboard-pinned" aria-labelledby="recent-activity-heading">
        <header className="cpay-dashboard-section-header">
          <div>
            <span>Operational timeline</span>
            <h3 id="recent-activity-heading">Recent Activity</h3>
          </div>
          <Button variant="ghost" onClick={() => history.push('/bo/admin/money-operations')}>View all transactions</Button>
        </header>
        {!canViewTransactions ? (
          <article className="cpay-dashboard-card">
            <strong>Transaction activity is restricted</strong>
            <p>Your role does not include access to the transaction log.</p>
          </article>
        ) : recentRows.length === 0 ? (
          <article className="cpay-dashboard-card">
            <strong>No recent transaction activity</strong>
            <p>The transaction log has not returned activity for this view.</p>
          </article>
        ) : (
          <div className="cpay-dashboard-card">
            <div className="cpay-health-table">
              <div className="cpay-health-row cpay-health-head">
                <span>Time</span><span>Merchant / reference</span><span>Type</span><span>Status</span>
              </div>
              {recentRows.map((row, index) => (
                <div className="cpay-health-row" key={row.id || `${row.tx_merchant_ref}-${index}`}>
                  <span>{row.created_on || '-'}</span>
                  <strong>{row.merchant_name || row.tx_merchant_ref || row.tx_gateway_ref || '-'}</strong>
                  <span>{row.tx_type || '-'}</span>
                  <Badge tone={transactionTone(row.status)}>{row.status || 'Unknown'}</Badge>
                </div>
              ))}
            </div>
          </div>
        )}
      </section>

      <section className="cpay-dashboard-pinned" aria-labelledby="performance-heading">
        <header className="cpay-dashboard-section-header">
          <div>
            <span>Trend</span>
            <h3 id="performance-heading">Performance</h3>
          </div>
          <p>Live chart sources from the existing admin dashboard APIs.</p>
        </header>
        <div className="cpay-dashboard-grid cpay-dashboard-grid--console">
          <article className="cpay-dashboard-card cpay-dashboard-panel-chart">
            <header className="cpay-dashboard-card-header"><h3>Collections vs Disbursements</h3></header>
            {charts.payinsVsPayouts.data
              ? <LinearChart data={charts.payinsVsPayouts.data} title="Collections vs Disbursements" />
              : <div className="cpay-dashboard-empty">No performance series is available yet.</div>}
          </article>
          <article className="cpay-dashboard-card cpay-dashboard-panel-chart">
            <header className="cpay-dashboard-card-header"><h3>Transaction Volume</h3></header>
            {charts.txVolumes.data
              ? <LinearChart data={charts.txVolumes.data} title="Transaction Volume" />
              : <div className="cpay-dashboard-empty">No transaction-volume series is available yet.</div>}
          </article>
        </div>
      </section>
    </div>
  );
}

const ModuleInsights = withRouter(ModuleInsightsC);
export default ModuleInsights;
