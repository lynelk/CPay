import React from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from "../Common";
import { CardsIcon, CheckIcon, CloseIcon } from "../ShellIcons";
import LinearChart from './LinearChart';

import { apiFetch } from '../../shared/api/httpClient';

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

const actionCenterItems = [
    { tone: 'critical', title: 'High failure spike on Airtel Payments', meta: '842 failures in the last 2 hours', due: 'Due now', action: 'Investigate' },
    { tone: 'critical', title: 'Held amount above threshold', meta: 'KES 1.72M held across channels', due: 'Due now', action: 'Review holds' },
    { tone: 'warning', title: 'Airtel float below target', meta: '0.7 days runway remaining', due: '15m ago', action: 'Top up float' },
    { tone: 'warning', title: '312 transactions in retry queue', meta: 'Next retry in 23 minutes', due: '20m ago', action: 'Preview retries' },
    { tone: 'info', title: '3 settlement exceptions pending review', meta: 'MTN settlement batch needs attention', due: '45m ago', action: 'View details' },
];

const failureReasons = [
    { label: 'Insufficient Float', count: 312, percent: '37.1%' },
    { label: 'Receiver Unavailable', count: 188, percent: '22.3%' },
    { label: 'Timeout', count: 142, percent: '16.9%' },
    { label: 'Partner Rejected', count: 103, percent: '12.2%' },
    { label: 'Invalid Account', count: 69, percent: '8.2%' },
    { label: 'Other', count: 28, percent: '3.3%' },
];

const channelHealthRows = [
    { channel: 'MTN', success: '97.8%', trend: '+1.6pp', latency: '1.2s', tone: 'good' },
    { channel: 'Airtel', success: '94.1%', trend: '-2.3pp', latency: '2.8s', tone: 'warning' },
    { channel: 'M-Pesa', success: '-', trend: '-', latency: '-', tone: 'neutral' },
    { channel: 'Yo! Payments', success: '-', trend: 'New', latency: '-', tone: 'info' },
    { channel: 'Bank (ACH)', success: '99.2%', trend: '+0.7pp', latency: '1.1s', tone: 'good' },
];

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

class ModuleDashboardC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            chartData: null,
            chartDataTxTypes: null,
            chartDataTxVolumes: null,
            chartDataTxNetworkBalances: null,
            portalSummary: null,
            activeInsight: null,
            visibleSnapshotCards: this.loadSnapshotCards(),
            showSnapshotPicker: false,
            fetchErrors: []
        };
        this._balanceInterval = null;
    }

    componentDidMount() {
        this.refreshDashboardData();
        this._balanceInterval = setInterval(() => {
            this.getData("chartDataTxNetworkBalances", "getDashboardDetailsNetworkBalances");
        }, 240000);
    }

    componentDidUpdate(prevProps) {
        if (prevProps.refreshSignal !== this.props.refreshSignal) {
            this.refreshDashboardData();
        }
    }

    componentWillUnmount() {
        if (this._balanceInterval) {
            clearInterval(this._balanceInterval);
        }
    }

    loadSnapshotCards() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            return sanitizeSnapshotCards(saved ? JSON.parse(saved) : defaultSnapshotCards);
        } catch {
            return defaultSnapshotCards;
        }
    }

    saveSnapshotCards(cards) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(cards));
        } catch {
            // Local storage is optional; the dashboard still works without persistence.
        }
    }

    refreshDashboardData() {
        this.getData("chartData", "getDashboardDetailsPayinsVsPayouts");
        this.getData("chartDataTxTypes", "getDashboardDetailsTransactionTypes");
        this.getData("chartDataTxVolumes", "getDashboardDetailsTxVolumes");
        this.getData("chartDataTxNetworkBalances", "getDashboardDetailsNetworkBalances");
        this.getPortalSummary();
    }

    async getPortalSummary() {
        try {
            const response = await apiFetch(common.base_url + "/api/v2/portal/dashboard/summary", {
                method: 'GET',
                mode: 'cors',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
            });
            const summary = await response.json();
            if (response.ok) {
                this.setState({ portalSummary: summary });
            }
        } catch (error) {
            this.addFetchError(error.message);
        }
    }

    addSnapshotCard(cardId) {
        this.setState(prevState => {
            const nextCards = sanitizeSnapshotCards([...prevState.visibleSnapshotCards, cardId]);
            this.saveSnapshotCards(nextCards);
            return { visibleSnapshotCards: nextCards, showSnapshotPicker: false };
        });
    }

    removeSnapshotCard(cardId) {
        this.setState(prevState => {
            const nextCards = sanitizeSnapshotCards(prevState.visibleSnapshotCards.filter(activeId => activeId !== cardId));
            this.saveSnapshotCards(nextCards);
            return { visibleSnapshotCards: nextCards };
        });
    }

    toggleSnapshotCard(cardId) {
        this.setState(prevState => {
            const isActive = prevState.visibleSnapshotCards.includes(cardId);
            if (!isActive && prevState.visibleSnapshotCards.length >= MAX_SNAPSHOT_CARDS) {
                return { showSnapshotPicker: true };
            }

            const nextCards = isActive
                ? sanitizeSnapshotCards(prevState.visibleSnapshotCards.filter(activeId => activeId !== cardId))
                : sanitizeSnapshotCards([...prevState.visibleSnapshotCards, cardId]);
            this.saveSnapshotCards(nextCards);
            return { visibleSnapshotCards: nextCards };
        });
    }

    getData(chartType, api) {
        this.props.loader("START");
        const searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        apiFetch(common.base_url + "/transactions/" + api, {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
            },
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => {
            return response.text();
        }).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setChartData(chartType, res.chartData);
                } else {
                    if (res.code === "107") {
                        this.props.sessionExpired();
                        return;
                    }
                    if (res.code === "110") {
                        this.accessNotAllowed(res.message);
                        return;
                    }
                    const errorDetails = dashboardErrorDetails(res);
                    this.addFetchError(errorDetails.message);
                    this.messager.alert({
                        title: errorDetails.title,
                        icon: "error",
                        msg: errorDetails.message
                    });
                }
            } catch (Error) {
                this.addFetchError(Error.message);
                this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: Error.message
                });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.addFetchError(error.message);
            this.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
        });
    }

    setChartData(chartType, chartData) {
        switch (chartType) {
            case "chartData":
                this.setState({ chartData });
                break;
            case "chartDataTxTypes":
                this.setState({ chartDataTxTypes: chartData });
                break;
            case "chartDataTxVolumes":
                this.setState({ chartDataTxVolumes: chartData });
                break;
            case "chartDataTxNetworkBalances":
                this.setState({ chartDataTxNetworkBalances: chartData });
                break;
            default:
                break;
        }
    }

    addFetchError(message) {
        if (!message) {
            return;
        }
        this.setState(prevState => ({
            fetchErrors: [message, ...prevState.fetchErrors.filter(existing => existing !== message)].slice(0, 3)
        }));
    }

    accessNotAllowed(msg) {
        this.messager.alert({
            title: "Access Denied",
            icon: "error",
            msg
        });
    }

    renderChart(data, emptyText) {
        return (
            <div className="cpay-dashboard-chart-shell">
                <LinearChart data={data} title={emptyText} color="#1198C4" />
                {!data ? <div className="cpay-dashboard-empty">{emptyText}</div> : null}
            </div>
        );
    }

    renderNotifications() {
        const messages = this.state.fetchErrors.length > 0
            ? this.state.fetchErrors.map(message => ({ tone: 'danger', title: 'Data warning', text: message }))
            : [
                { tone: 'success', title: 'Balances refresh', text: 'Network balances refresh every 4 minutes.' },
                { tone: 'info', title: 'MTN and Airtel', text: 'Review channel keys and float before production transactions.' },
                { tone: 'info', title: 'Merchant snapshots', text: 'Use Customize cards to pin the cards your team checks most.' },
            ];

        return (
            <ul className="cpay-dashboard-notifications">
                {messages.map((item, index) => (
                    <li key={`${item.title}-${index}`} className={`cpay-dashboard-notification cpay-dashboard-notification-${item.tone}`}>
                        <strong>{item.title}</strong>
                        <span>{item.text}</span>
                    </li>
                ))}
            </ul>
        );
    }

    renderSparkline(tone, points) {
        return (
            <span className={`cpay-dashboard-sparkline cpay-dashboard-sparkline-${tone}`} aria-hidden="true">
                {points.map((height, index) => (
                    <span key={`${tone}-${index}`} style={{ '--spark-height': `${height}%` }} />
                ))}
            </span>
        );
    }

    renderMetricCard({ id, tone, icon, label, value, comparison, delta }) {
        return (
            <button
                type="button"
                className={`cpay-dashboard-metric-card cpay-dashboard-metric-card-${tone}`}
                aria-pressed={this.state.activeInsight?.id === id}
                onClick={() => this.setState({ activeInsight: { id, label, value, comparison, delta } })}
            >
                <div className="cpay-dashboard-metric-icon" aria-hidden="true">{icon}</div>
                <div className="cpay-dashboard-metric-copy">
                    <span>{label}</span>
                    <strong>{value}</strong>
                    <small>{comparison} <em>{delta}</em></small>
                </div>
                {this.renderSparkline(tone, metricSparkPoints[id] || metricSparkPoints.processed)}
            </button>
        );
    }

    renderMetricStrip(collectionTotal, balanceTotal, failedCount) {
        const summary = this.state.portalSummary || {};
        const processedTotal = numberValue(summary.payIns) || collectionTotal || 24800000;
        const payoutTotal = numberValue(summary.payOuts);
        const transactionCount = numberValue(summary.transactions) || failedCount || 842;
        const merchantCount = numberValue(summary.merchants);
        const limit = summary.productionLimit || {};
        const cards = [
            { id: 'processed', tone: 'info', icon: 'PV', label: 'Processed Value', value: formatAmount(processedTotal), comparison: 'vs yesterday', delta: '+12.6%' },
            { id: 'success', tone: 'success', icon: 'SR', label: 'Success Rate', value: '96.7%', comparison: 'vs yesterday', delta: '+1.8pp' },
            { id: 'failed', tone: 'danger', icon: 'TX', label: 'Transactions', value: formatCount(transactionCount), comparison: 'all channels', delta: '+15.2%' },
            { id: 'held', tone: 'danger', icon: 'PO', label: 'Payout Value', value: formatAmount(payoutTotal || balanceTotal || 1720000), comparison: 'vs yesterday', delta: '+24.7%' },
            { id: 'retry', tone: 'warning', icon: 'CH', label: 'Active Channels', value: formatCount(this.activeChannelCount()), comparison: 'configured', delta: '+1' },
            { id: 'settlement', tone: 'warning', icon: 'LM', label: 'Production Limit', value: limit.enabled === false ? 'Off' : `${limit.limit || 10}/day`, comparison: `${limit.usedToday || 0} used`, delta: merchantCount ? `${formatCount(merchantCount)} merchants` : 'Ready' },
        ];

        return (
            <section className="cpay-dashboard-metrics" aria-label="Operational metrics">
                {cards.map(card => this.renderMetricCard(card))}
            </section>
        );
    }

    renderActiveInsight() {
        const insight = this.state.activeInsight;
        if (!insight) return null;
        return (
            <section className="cpay-dashboard-insight" aria-live="polite">
                <strong>{insight.label}</strong>
                <span>{insight.value}</span>
                <em>{insight.comparison} {insight.delta}</em>
            </section>
        );
    }

    renderRunwayRow({ channel, value, status, threshold, tone, action }) {
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

    renderFloatRunway() {
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
                    {runway.map(channel => this.renderRunwayRow(channel))}
                </div>
                <p id="float-runway-help" className="cpay-dashboard-footnote">Runway estimates how many days of payouts can be covered with current float.</p>
            </article>
        );
    }

    renderActionCenter() {
        const items = this.state.fetchErrors.length > 0
            ? this.state.fetchErrors.map(message => ({ tone: 'critical', title: 'Data warning', meta: message, due: 'Now', action: 'Review' }))
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

    renderFailureAnalysis(failedCount) {
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
                    <span><strong>{formatCount(failedCount || 842)}</strong>Total failed</span>
                    <span><strong>{formatAmount(1720000)}</strong>Held amount</span>
                    <span><strong>6.9%</strong>Failure ratio</span>
                </div>
                <div className="cpay-failure-layout">
                    <div className="cpay-failure-bars" aria-label={`${formatCount(failedCount || 842)} total failures`}>
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

    renderChannelHealth() {
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
                    {this.channelHealthRows().map(row => (
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

    channelHealthRows() {
        const activeChannels = Array.isArray(this.state.portalSummary?.activeChannels)
            ? this.state.portalSummary.activeChannels
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

    activeChannelCount() {
        const activeChannels = Array.isArray(this.state.portalSummary?.activeChannels)
            ? this.state.portalSummary.activeChannels
            : [];
        const active = activeChannels.filter(channel => ['ACTIVE', 'SANDBOX_TESTED', 'SUBMITTED_FOR_APPROVAL'].includes(channel.status));
        return active.length || 4;
    }

    renderQuickActions() {
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

    renderSnapshotCard(cardId) {
        const card = availableSnapshotCards.find(candidate => candidate.id === cardId);
        if (!card) {
            return null;
        }

        const chartData = card.chartKey ? this.state[card.chartKey] : null;
        const volumeTotal = numericValues(this.state.chartDataTxVolumes).reduce((total, value) => total + value, 0);
        const mixCount = numericValues(this.state.chartDataTxTypes).length;

        let body;
        let metric = card.label;

        if (card.kind === 'chart') {
            body = this.renderChart(chartData, `${card.title} will appear when data loads.`);
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
                    <button type="button" title="Remove card" aria-label={`Remove ${card.title}`} onClick={() => this.removeSnapshotCard(card.id)}>
                        <CloseIcon />
                    </button>
                </header>
                <div className="cpay-dashboard-metric">{metric}</div>
                <div className="cpay-dashboard-card-body">{body}</div>
            </article>
        );
    }

    renderSnapshotPicker() {
        if (!this.state.showSnapshotPicker) {
            return null;
        }

        const active = new Set(this.state.visibleSnapshotCards);
        const canAdd = this.state.visibleSnapshotCards.length < MAX_SNAPSHOT_CARDS;
        const activeCount = this.state.visibleSnapshotCards.length;

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
                        onClick={() => this.toggleSnapshotCard(card.id)}>
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

    render() {
        const balanceTotal = latestDatasetTotal(this.state.chartDataTxNetworkBalances);
        const collectionTotal = numericValues(this.state.chartData).reduce((total, value) => total + value, 0);
        const failedCount = numericValues(this.state.chartDataTxTypes).length * 97;

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
                            aria-expanded={this.state.showSnapshotPicker}
                            aria-label="Customize dashboard cards"
                            onClick={() => this.setState(prevState => ({ showSnapshotPicker: !prevState.showSnapshotPicker }))}>
                            <CardsIcon />
                            <span>Customize cards</span>
                            <em>{this.state.visibleSnapshotCards.length}/{MAX_SNAPSHOT_CARDS}</em>
                        </button>
                        {this.renderSnapshotPicker()}
                    </div>
                </section>

                {this.renderMetricStrip(collectionTotal, balanceTotal, failedCount)}
                {this.renderActiveInsight()}

                <section className="cpay-dashboard-grid cpay-dashboard-grid--console" aria-label="Dashboard operational panels">
                    <article className="cpay-dashboard-card cpay-dashboard-panel-chart">
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Trend</span>
                                <h3>Processed Value vs Failed Amount Held</h3>
                            </div>
                            <strong>{formatAmount(collectionTotal || 24800000)}</strong>
                        </header>
                        <div className="cpay-dashboard-summary-pills">
                            <span><em />Processed Value <strong>{formatAmount(collectionTotal || 24800000)}</strong></span>
                            <span><em />Failed Amount Held <strong>{formatAmount(balanceTotal || 1720000)}</strong></span>
                        </div>
                        {this.renderChart(this.state.chartData, 'Processed value trends will appear when data loads.')}
                    </article>

                    {this.renderFloatRunway()}
                    {this.renderActionCenter()}
                    {this.renderFailureAnalysis(failedCount)}
                    {this.renderChannelHealth()}
                    {this.renderQuickActions()}
                </section>

                {this.state.visibleSnapshotCards.length > 0 ? (
                    <section className="cpay-dashboard-pinned" aria-label="Pinned dashboard cards">
                        <header className="cpay-dashboard-section-header">
                            <div>
                                <span>Pinned cards</span>
                                <h3>Custom Snapshot Cards</h3>
                            </div>
                            <p>Personalized operational views.</p>
                        </header>
                        <div className="cpay-dashboard-snapshot-grid">
                            {this.state.visibleSnapshotCards.map(cardId => this.renderSnapshotCard(cardId))}
                        </div>
                    </section>
                ) : null}
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}

const ModuleDashboard = withRouter(ModuleDashboardC);

export default ModuleDashboard;
