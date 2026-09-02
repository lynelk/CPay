import { useRef, useState, useEffect } from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import { CardsIcon, CheckIcon, CloseIcon } from "../ShellIcons";
import LinearChart from './LinearChart';

import {
    useAdminDashboardCharts,
    usePortalDashboardSummary,
    useLoaderSync,
    useRefreshSignal,
    SessionExpiredError,
    AccessDeniedError,
    LegacyRequestError,
} from '../../shared/api/hooks';

export const dashboardErrorDetails = (res) => {
    const hasCode = res && res.code !== undefined && res.code !== null && String(res.code).trim() !== "";
    const message = (res && (res.message || res.error))
        || (res && res.status ? `Request failed with status ${res.status}` : "Request failed.");

    return {
        title: hasCode ? `Error ${res.code}` : "Error",
        message
    };
};

export const defaultSnapshotCards = [];

export const availableSnapshotCards = [
    { id: 'transactionMix', title: 'Transaction Mix', label: 'Types', kind: 'chart', chartKey: 'chartDataTxTypes' },
    { id: 'collectionVolume', title: 'Collection Volume', label: 'Amounts', kind: 'chart', chartKey: 'chartDataTxVolumes' },
    { id: 'gatewayHealth', title: 'Gateway Health', label: 'MTN / Airtel', kind: 'status' },
    { id: 'smsNotifications', title: 'SMS Notifications', label: 'Messaging', kind: 'sms' },
    { id: 'floatWatch', title: 'Float Watch', label: 'Liquidity', kind: 'float' },
    { id: 'settlementRisk', title: 'Settlement Risk', label: 'Controls', kind: 'risk' },
];

const STORAGE_KEY = 'cpay-admin-dashboard-snapshots-v2';
const MAX_SNAPSHOT_CARDS = 4;

const metricSparkPoints = {
    processed: [34, 38, 35, 42, 40, 48, 52, 46, 55, 50, 58, 70],
    success: [70, 72, 68, 75, 73, 78, 82, 76, 84, 81, 86, 90],
    failed: [28, 24, 34, 30, 38, 46, 32, 29, 35, 50, 42, 57],
    held: [30, 36, 34, 44, 48, 42, 52, 58, 49, 62, 57, 68],
    retry: [46, 40, 48, 38, 44, 36, 42, 33, 39, 31, 35, 28],
    settlement: [36, 42, 39, 44, 50, 46, 53, 58, 52, 61, 57, 64],
};

// Dashboard operational lists are populated only from live APIs. Empty arrays are intentional:
// no production-looking examples or inferred incidents may be substituted for absent data.
const actionCenterItems = [];
const failureReasons = [];
const channelHealthRows = [];

const quickActions = ['Add Merchant', 'Make Payout', 'Top Up Float', 'View Settlements', 'Preview Retries', 'Download Report'];

export const numericValues = (chartData) => {
    const datasets = chartData && chartData.data && Array.isArray(chartData.data.datasets)
        ? chartData.data.datasets
        : [];
    return datasets.flatMap(dataset => Array.isArray(dataset.data) ? dataset.data : [])
        .map(value => Number(value))
        .filter(value => Number.isFinite(value));
};

const latestDatasetTotal = (chartData) => {
    const datasets = chartData && chartData.data && Array.isArray(chartData.data.datasets)
        ? chartData.data.datasets
        : [];
    return datasets.reduce((total, dataset) => {
        const values = Array.isArray(dataset.data) ? dataset.data : [];
        const latest = Number(values[values.length - 1]);
        return Number.isFinite(latest) ? total + latest : total;
    }, 0);
};

export const formatAmount = (value) => {
    if (!Number.isFinite(value) || value <= 0) {
        return 'Awaiting data';
    }
    return `UGX ${new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}`;
};

export const formatCount = (value) => {
    if (!Number.isFinite(value) || value <= 0) {
        return '0';
    }
    return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
};

const numberValue = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
};

const sanitizeSnapshotCards = (cards) => {
    const allowed = new Set(availableSnapshotCards.map(card => card.id));
    const unique = [];
    (Array.isArray(cards) ? cards : defaultSnapshotCards).forEach(cardId => {
        if (allowed.has(cardId) && !unique.includes(cardId) && unique.length < MAX_SNAPSHOT_CARDS) {
            unique.push(cardId);
        }
    });
    return unique;
};

function loadSnapshotCards() {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        return sanitizeSnapshotCards(saved ? JSON.parse(saved) : defaultSnapshotCards);
    } catch {
        return defaultSnapshotCards;
    }
}

function saveSnapshotCards(cards) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(cards));
    } catch {
        // Local storage is optional; the dashboard still works without persistence.
    }
}

/**
 * React to a query's `error` the same way the old imperative
 * `getData().then(...)` callback reacted to a failed fetch: a one-shot
 * `messager.alert(...)` the moment the error appears, `sessionExpired()` for
 * code "107", an "Access Denied" alert for code "110", and otherwise append
 * to the rolling `fetchErrors` list rendered by the Action Center / failure
 * panels. Only re-runs when `error` itself changes (a new failed attempt),
 * not on every render — same one-alert-per-failure behavior as before.
 */
function useDashboardQueryErrorEffect(error, messagerRef, sessionExpired, addFetchError) {
    useEffect(() => {
        if (!error) return;
        if (error instanceof SessionExpiredError) {
            sessionExpired?.();
            return;
        }
        if (error instanceof AccessDeniedError) {
            messagerRef.current?.alert({ title: "Access Denied", icon: "error", msg: error.message });
            return;
        }
        const errorDetails = dashboardErrorDetails({
            code: error instanceof LegacyRequestError ? error.code : undefined,
            message: error.message,
        });
        addFetchError(errorDetails.message);
        messagerRef.current?.alert({ title: errorDetails.title, icon: "error", msg: errorDetails.message });
        // Intentionally only re-run when the error itself changes; the
        // callbacks are stable in behavior even when their identity changes
        // across renders (bound methods from the parent Layout).
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [error]);
}

function ModuleDashboardC(props) {
    const { loader, refreshSignal, sessionExpired } = props;
    const messagerRef = useRef(null);

    const [visibleSnapshotCards, setVisibleSnapshotCards] = useState(loadSnapshotCards);
    const [showSnapshotPicker, setShowSnapshotPicker] = useState(false);
    const [activeInsight, setActiveInsight] = useState(null);
    const [fetchErrors, setFetchErrors] = useState([]);

    const charts = useAdminDashboardCharts();
    const portalSummaryQuery = usePortalDashboardSummary();

    const chartData = charts.payinsVsPayouts.data ?? null;
    const chartDataTxTypes = charts.txTypes.data ?? null;
    const chartDataTxVolumes = charts.txVolumes.data ?? null;
    const chartDataTxNetworkBalances = charts.networkBalances.data ?? null;
    const portalSummary = portalSummaryQuery.data ?? null;

    useLoaderSync(
        loader,
        charts.payinsVsPayouts.isFetching
        || charts.txTypes.isFetching
        || charts.txVolumes.isFetching
        || charts.networkBalances.isFetching
        || portalSummaryQuery.isFetching,
    );

    useRefreshSignal(refreshSignal, [
        charts.payinsVsPayouts.refetch,
        charts.txTypes.refetch,
        charts.txVolumes.refetch,
        charts.networkBalances.refetch,
        portalSummaryQuery.refetch,
    ]);

    function addFetchError(message) {
        if (!message) return;
        setFetchErrors(prev => [message, ...prev.filter(existing => existing !== message)].slice(0, 3));
    }

    useDashboardQueryErrorEffect(charts.payinsVsPayouts.error, messagerRef, sessionExpired, addFetchError);
    useDashboardQueryErrorEffect(charts.txTypes.error, messagerRef, sessionExpired, addFetchError);
    useDashboardQueryErrorEffect(charts.txVolumes.error, messagerRef, sessionExpired, addFetchError);
    useDashboardQueryErrorEffect(charts.networkBalances.error, messagerRef, sessionExpired, addFetchError);
    useDashboardQueryErrorEffect(portalSummaryQuery.error, messagerRef, sessionExpired, addFetchError);

    function removeSnapshotCard(cardId) {
        const nextCards = sanitizeSnapshotCards(visibleSnapshotCards.filter(activeId => activeId !== cardId));
        saveSnapshotCards(nextCards);
        setVisibleSnapshotCards(nextCards);
    }

    function toggleSnapshotCard(cardId) {
        const isActive = visibleSnapshotCards.includes(cardId);
        if (!isActive && visibleSnapshotCards.length >= MAX_SNAPSHOT_CARDS) {
            setShowSnapshotPicker(true);
            return;
        }

        const nextCards = isActive
            ? sanitizeSnapshotCards(visibleSnapshotCards.filter(activeId => activeId !== cardId))
            : sanitizeSnapshotCards([...visibleSnapshotCards, cardId]);
        saveSnapshotCards(nextCards);
        setVisibleSnapshotCards(nextCards);
    }

    function renderChart(data, emptyText) {
        return (
            <div className="cpay-dashboard-chart-shell">
                <LinearChart data={data} title={emptyText} />
                {!data ? <div className="cpay-dashboard-empty">{emptyText}</div> : null}
            </div>
        );
    }

    function renderSparkline(tone, points) {
        return (
            <span className={`cpay-dashboard-sparkline cpay-dashboard-sparkline-${tone}`} aria-hidden="true">
                {points.map((height, index) => (
                    <span key={`${tone}-${index}`} style={{ '--spark-height': `${height}%` }} />
                ))}
            </span>
        );
    }

    function renderMetricCard({ id, tone, icon, label, value, comparison, delta }) {
        return (
            <button
                type="button"
                className={`cpay-dashboard-metric-card cpay-dashboard-metric-card-${tone}`}
                aria-pressed={activeInsight?.id === id}
                onClick={() => setActiveInsight({ id, label, value, comparison, delta })}
            >
                <div className="cpay-dashboard-metric-icon" aria-hidden="true">{icon}</div>
                <div className="cpay-dashboard-metric-copy">
                    <span>{label}</span>
                    <strong>{value}</strong>
                    <small>{comparison} <em>{delta}</em></small>
                </div>
                {renderSparkline(tone, metricSparkPoints[id] || metricSparkPoints.processed)}
            </button>
        );
    }

    function computeActiveChannelCount() {
        const activeChannels = Array.isArray(portalSummary?.activeChannels)
            ? portalSummary.activeChannels
            : [];
        const active = activeChannels.filter(channel => ['ACTIVE', 'SANDBOX_TESTED', 'SUBMITTED_FOR_APPROVAL'].includes(channel.status));
        return active.length;
    }

    function renderMetricStrip(collectionTotal, balanceTotal, failedCount) {
        const summary = portalSummary || {};
        const processedTotal = numberValue(summary.payIns) || collectionTotal;
        const payoutTotal = numberValue(summary.payOuts);
        const transactionCount = numberValue(summary.transactions);
        const merchantCount = numberValue(summary.merchants);
        const limit = summary.productionLimit || {};
        const successRate = transactionCount > 0 ? `${(((transactionCount - failedCount) / transactionCount) * 100).toFixed(1)}%` : 'Awaiting data';
        const cards = [
            { id: 'processed', tone: 'info', icon: 'PV', label: 'Processed Value', value: formatAmount(processedTotal), comparison: 'live source', delta: '' },
            { id: 'success', tone: 'success', icon: 'SR', label: 'Success Rate', value: successRate, comparison: 'recorded transactions', delta: '' },
            { id: 'failed', tone: 'danger', icon: 'TX', label: 'Transactions', value: formatCount(transactionCount), comparison: 'all channels', delta: '' },
            { id: 'held', tone: 'danger', icon: 'PO', label: 'Payout Value', value: formatAmount(payoutTotal), comparison: 'successful payouts', delta: '' },
            { id: 'retry', tone: 'warning', icon: 'CH', label: 'Active Channels', value: formatCount(computeActiveChannelCount()), comparison: 'configured', delta: '' },
            { id: 'settlement', tone: 'warning', icon: 'LM', label: 'Production Limit', value: limit.enabled === false ? 'Off' : (limit.limit != null ? `${limit.limit}/day` : 'Not configured'), comparison: `${limit.usedToday || 0} used`, delta: merchantCount ? `${formatCount(merchantCount)} merchants` : '' },
        ];

        return (
            <section className="cpay-dashboard-metrics" aria-label="Operational metrics">
                {cards.map(card => renderMetricCard(card))}
            </section>
        );
    }

    function renderActiveInsight() {
        if (!activeInsight) return null;
        return (
            <section className="cpay-dashboard-insight" aria-live="polite">
                <strong>{activeInsight.label}</strong>
                <span>{activeInsight.value}</span>
                <em>{activeInsight.comparison} {activeInsight.delta}</em>
            </section>
        );
    }

    function renderRunwayRow({ channel, value, status, threshold, tone, action }) {
        const percent = Math.max(0, Math.min(100, (value / 5) * 100));
        return (
            <div className="cpay-runway-row">
                <span className={`cpay-channel-logo cpay-channel-logo-${tone}`}>{channel.slice(0, 2)}</span>
                <div className="cpay-runway-channel">
                    <strong>{channel}</strong>
                    <span>{value > 0 ? `${value.toFixed(1)} days` : 'Not configured'}</span>
                </div>
                <span className={`cpay-status-pill cpay-status-pill-${tone}`}>{status}</span>
                <div className="cpay-runway-progress" aria-hidden="true">
                    <span style={{ '--runway-value': `${percent}%` }} />
                </div>
                <em>{threshold}</em>
                {action ? <button type="button">{action}</button> : null}
            </div>
        );
    }

    function renderFloatRunway() {
        const runway = [
            { channel: 'MTN', value: 3.8, status: 'Healthy', threshold: '> 1.0 days', tone: 'good' },
            { channel: 'Airtel', value: 0.7, status: 'Critical', threshold: '> 1.5 days', tone: 'warning', action: 'Top up' },
            { channel: 'M-Pesa', value: 0, status: 'Not configured', threshold: '-', tone: 'neutral', action: 'Configure' },
        ];

        return (
            <article className="cpay-dashboard-card cpay-dashboard-panel-runway">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Float runway</span>
                        <h3>Float Runway by Channel</h3>
                    </div>
                    <a href="#float-runway-help" className="cpay-dashboard-inline-link">How it works</a>
                </header>
                <div className="cpay-runway-table">
                    <div className="cpay-runway-head">
                        <span>Channel</span>
                        <span>Status</span>
                        <span>Runway</span>
                        <span>Threshold</span>
                    </div>
                    {runway.map(channel => renderRunwayRow(channel))}
                </div>
                <p id="float-runway-help" className="cpay-dashboard-footnote">Runway estimates how many days of payouts can be covered with current float.</p>
            </article>
        );
    }

    function renderActionCenter() {
        const items = fetchErrors.length > 0
            ? fetchErrors.map(message => ({ tone: 'critical', title: 'Data warning', meta: message, due: 'Now', action: 'Review' }))
            : actionCenterItems;

        return (
            <article className="cpay-dashboard-card cpay-dashboard-panel-actions" id="action-center-all">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Priority queue</span>
                        <h3>Action Center</h3>
                    </div>
                    <strong>{items.length}</strong>
                </header>
                <div className="cpay-action-list">
                    {items.map((item, index) => (
                        <div className={`cpay-action-item cpay-action-item-${item.tone}`} key={`${item.title}-${index}`}>
                            <span className="cpay-action-dot" aria-hidden="true" />
                            <div>
                                <strong>{item.title}</strong>
                                <span>{item.meta}</span>
                            </div>
                            <em>{item.due}</em>
                            <button type="button">{item.action}</button>
                        </div>
                    ))}
                </div>
                <a href="#action-center-all" className="cpay-dashboard-inline-link cpay-action-view-all">View all unresolved items</a>
            </article>
        );
    }

    function renderFailureAnalysis(failedCount) {
        return (
            <article className="cpay-dashboard-card cpay-dashboard-panel-failure">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Exceptions</span>
                        <h3>Failure Analysis</h3>
                    </div>
                    <a href="#failure-reasons" className="cpay-dashboard-inline-link">View all</a>
                </header>
                <div className="cpay-failure-summary">
                    <span><strong>{formatCount(failedCount)}</strong>Total failed</span>
                    <span><strong>Unavailable</strong>Held amount</span>
                    <span><strong>Unavailable</strong>Failure ratio</span>
                </div>
                <div className="cpay-failure-layout">
                    <div className="cpay-failure-bars" aria-label={`${formatCount(failedCount)} total failures`}>
                        {failureReasons.slice(0, 4).map(reason => (
                            <div className="cpay-failure-bar" key={reason.label}>
                                <span>{reason.label}</span>
                                <strong>{reason.percent}</strong>
                                <em style={{ '--failure-width': reason.percent }} />
                            </div>
                        ))}
                    </div>
                    <div className="cpay-failure-table" id="failure-reasons">
                        <div className="cpay-failure-table-head">
                            <span>Top Failure Reasons</span>
                            <span>Count</span>
                            <span>%</span>
                        </div>
                        {failureReasons.map(reason => (
                            <div className="cpay-failure-row" key={reason.label}>
                                <span>{reason.label}</span>
                                <strong>{reason.count}</strong>
                                <em>{reason.percent}</em>
                            </div>
                        ))}
                    </div>
                </div>
            </article>
        );
    }

    function computeChannelHealthRows() {
        const activeChannels = Array.isArray(portalSummary?.activeChannels)
            ? portalSummary.activeChannels
            : [];
        if (activeChannels.length === 0) {
            return channelHealthRows;
        }
        const seen = new Set();
        const rows = activeChannels
            .filter(channel => !seen.has(channel.channel_code) && seen.add(channel.channel_code))
            .slice(0, 6)
            .map(channel => ({
                channel: channel.display_name || channel.channel_code,
                success: channel.status === 'ACTIVE' || channel.status === 'SANDBOX_TESTED' ? 'Ready' : '-',
                trend: channel.environment || '-',
                latency: channel.status || 'Not configured',
                tone: channel.status === 'ACTIVE' || channel.status === 'SANDBOX_TESTED' ? 'good' : 'neutral',
            }));
        return rows.length ? rows : channelHealthRows;
    }

    function renderChannelHealth() {
        return (
            <article className="cpay-dashboard-card cpay-dashboard-panel-health">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Availability</span>
                        <h3>Channel Health</h3>
                    </div>
                    <a href="#channel-health-table" className="cpay-dashboard-inline-link">View details</a>
                </header>
                <div className="cpay-health-table" id="channel-health-table">
                    <div className="cpay-health-row cpay-health-head">
                        <span>Channel</span>
                        <span>Success Rate</span>
                        <span>vs yesterday</span>
                        <span>Avg. Latency</span>
                    </div>
                    {computeChannelHealthRows().map(row => (
                        <div className="cpay-health-row" key={row.channel}>
                            <strong><span className={`cpay-channel-dot cpay-channel-dot-${row.tone}`} />{row.channel}</strong>
                            <span>{row.success}</span>
                            <em>{row.trend}</em>
                            <mark>{row.latency}</mark>
                        </div>
                    ))}
                </div>
            </article>
        );
    }

    function renderQuickActions() {
        return (
            <article className="cpay-dashboard-card cpay-dashboard-panel-quick">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Shortcuts</span>
                        <h3>Quick Actions</h3>
                    </div>
                </header>
                <div className="cpay-quick-grid">
                    {quickActions.map(action => (
                        <button type="button" key={action}>
                            <span aria-hidden="true">{action.split(' ').map(word => word[0]).join('').slice(0, 2)}</span>
                            <strong>{action}</strong>
                        </button>
                    ))}
                </div>
            </article>
        );
    }

    function renderSnapshotCard(cardId) {
        const card = availableSnapshotCards.find(candidate => candidate.id === cardId);
        if (!card) {
            return null;
        }

        const chartsByKey = { chartData, chartDataTxTypes, chartDataTxVolumes, chartDataTxNetworkBalances };
        const cardChartData = card.chartKey ? chartsByKey[card.chartKey] : null;
        const volumeTotal = numericValues(chartDataTxVolumes).reduce((total, value) => total + value, 0);
        const mixCount = numericValues(chartDataTxTypes).length;

        let body;
        let metric = card.label;

        if (card.kind === 'chart') {
            body = renderChart(cardChartData, `${card.title} will appear when data loads.`);
            metric = card.id === 'collectionVolume' ? formatAmount(volumeTotal) : `${mixCount} active series`;
        } else if (card.kind === 'status') {
            metric = 'Ready';
            body = (
                <div className="cpay-dashboard-status-grid">
                    <span><strong>MTN</strong><em>Configured</em></span>
                    <span><strong>Airtel</strong><em>Configured</em></span>
                </div>
            );
        } else if (card.kind === 'sms') {
            metric = 'Available';
            body = <p className="cpay-dashboard-card-copy">SMS notifications are available to merchants with SEND_SMS access.</p>;
        } else if (card.kind === 'float') {
            metric = 'Monitor';
            body = <p className="cpay-dashboard-card-copy">Track minimum float thresholds before approving payouts.</p>;
        } else {
            metric = 'Low';
            body = <p className="cpay-dashboard-card-copy">Watch failed callbacks, reversals, and settlement exceptions.</p>;
        }

        return (
            <article className="cpay-dashboard-card cpay-dashboard-snapshot-card" key={card.id}>
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>{card.label}</span>
                        <h3>{card.title}</h3>
                    </div>
                    <button type="button" title="Remove card" aria-label={`Remove ${card.title}`} onClick={() => removeSnapshotCard(card.id)}>
                        <CloseIcon />
                    </button>
                </header>
                <div className="cpay-dashboard-metric">{metric}</div>
                <div className="cpay-dashboard-card-body">{body}</div>
            </article>
        );
    }

    function renderSnapshotPicker() {
        if (!showSnapshotPicker) {
            return null;
        }

        const active = new Set(visibleSnapshotCards);
        const canAdd = visibleSnapshotCards.length < MAX_SNAPSHOT_CARDS;
        const activeCount = visibleSnapshotCards.length;

        return (
            <div className="cpay-dashboard-picker" role="menu" aria-label="Customize dashboard cards">
                <div className="cpay-dashboard-picker-heading">
                    <strong>Dashboard cards</strong>
                    <span>{activeCount}/{MAX_SNAPSHOT_CARDS} shown</span>
                </div>
                {availableSnapshotCards.map(card => {
                    const isActive = active.has(card.id);
                    const disabled = !isActive && !canAdd;
                    return (
                    <button
                        key={card.id}
                        type="button"
                        role="menuitemcheckbox"
                        aria-pressed={isActive}
                        disabled={disabled}
                        className={`cpay-dashboard-picker-option${isActive ? ' cpay-dashboard-picker-option-active' : ''}`}
                        onClick={() => toggleSnapshotCard(card.id)}>
                        <span className="cpay-dashboard-picker-state">{isActive ? <CheckIcon /> : <CardsIcon />}</span>
                        <span className="cpay-dashboard-picker-copy">
                        <strong>{card.title}</strong>
                        <span>{card.label}</span>
                        </span>
                        <em>{isActive ? 'Shown' : disabled ? 'Limit reached' : 'Add'}</em>
                    </button>
                    );
                })}
            </div>
        );
    }

    const balanceTotal = latestDatasetTotal(chartDataTxNetworkBalances);
    const collectionTotal = numericValues(chartData).reduce((total, value) => total + value, 0);
    const failedCount = numberValue(portalSummary?.failedTransactions);

    return (
        <div className="cpay-dashboard cpay-dashboard--console">
            <section className="cpay-dashboard-toolbar">
                <div className="cpay-dashboard-toolbar-copy">
                    <h2>Operations Workspace</h2>
                    <p>All metrics compare to the same elapsed time yesterday for a paced view of performance.</p>
                </div>
                <div className="cpay-dashboard-actions">
                    <div className="cpay-dashboard-segmented" aria-label="Dashboard period">
                        <button type="button" aria-pressed="true">Today</button>
                        <button type="button" aria-pressed="false">7d</button>
                        <button type="button" aria-pressed="false">30d</button>
                    </div>
                    <button
                        className="cpay-card-manager-button"
                        type="button"
                        aria-expanded={showSnapshotPicker}
                        aria-label="Customize dashboard cards"
                        onClick={() => setShowSnapshotPicker(prev => !prev)}>
                        <CardsIcon />
                        <span>Customize cards</span>
                        <em>{visibleSnapshotCards.length}/{MAX_SNAPSHOT_CARDS}</em>
                    </button>
                    {renderSnapshotPicker()}
                </div>
            </section>

            {renderMetricStrip(collectionTotal, balanceTotal, failedCount)}
            {renderActiveInsight()}

            <section className="cpay-dashboard-grid cpay-dashboard-grid--console" aria-label="Dashboard operational panels">
                <article className="cpay-dashboard-card cpay-dashboard-panel-chart">
                    <header className="cpay-dashboard-card-header">
                        <div>
                            <span>Trend</span>
                            <h3>Processed Value vs Failed Amount Held</h3>
                        </div>
                        <strong>{formatAmount(collectionTotal)}</strong>
                    </header>
                    <div className="cpay-dashboard-summary-pills">
                        <span><em />Processed Value <strong>{formatAmount(collectionTotal)}</strong></span>
                        <span><em />Network Balance <strong>{formatAmount(balanceTotal)}</strong></span>
                    </div>
                    {renderChart(chartData, 'Processed value trends will appear when data loads.')}
                </article>

                {renderFloatRunway()}
                {renderActionCenter()}
                {renderFailureAnalysis(failedCount)}
                {renderChannelHealth()}
                {renderQuickActions()}
            </section>

            {visibleSnapshotCards.length > 0 ? (
                <section className="cpay-dashboard-pinned" aria-label="Pinned dashboard cards">
                    <header className="cpay-dashboard-section-header">
                        <div>
                            <span>Pinned cards</span>
                            <h3>Custom Snapshot Cards</h3>
                        </div>
                        <p>Personalized operational views.</p>
                    </header>
                    <div className="cpay-dashboard-snapshot-grid">
                        {visibleSnapshotCards.map(cardId => renderSnapshotCard(cardId))}
                    </div>
                </section>
            ) : null}
            <Messager ref={messagerRef}></Messager>
        </div>
    );
}

const ModuleDashboard = withRouter(ModuleDashboardC);

export default ModuleDashboard;
