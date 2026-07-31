import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import { CardsIcon, CheckIcon, CloseIcon } from "../../ShellIcons";
import LinearChart from './LinearChart';
import { dashboardErrorDetails, formatAmount, formatCount, numericValues } from '../ModuleDashboard';

import { apiFetch } from '../../../shared/api/httpClient';
import { apiUrl } from '../../../shared/config';

const merchantDefaultSnapshotCards = [];

const merchantSnapshotCards = [
    { id: 'transactionTypes', title: 'Transaction Types', label: 'Mix', kind: 'chart', chartKey: 'chartDataTxTypes' },
    { id: 'gatewaySplit', title: 'Gateway Split', label: 'MTN / Airtel / Yo! Payments', kind: 'chart', chartKey: 'chartDataTxPerGateway' },
    { id: 'apiReadiness', title: 'API Readiness', label: 'Integration', kind: 'api' },
    { id: 'smsNotifications', title: 'SMS Notifications', label: 'Messaging', kind: 'sms' },
    { id: 'floatWatch', title: 'Float Watch', label: 'Liquidity', kind: 'float' },
    { id: 'payoutControls', title: 'Payout Controls', label: 'Risk', kind: 'risk' },
];

const STORAGE_KEY = 'cpay-merchant-dashboard-snapshots-v2';
const MAX_SNAPSHOT_CARDS = 4;

const sanitizeSnapshotCards = (cards) => {
    const allowed = new Set(merchantSnapshotCards.map(card => card.id));
    const unique = [];
    (Array.isArray(cards) ? cards : merchantDefaultSnapshotCards).forEach(cardId => {
        if (allowed.has(cardId) && !unique.includes(cardId) && unique.length < MAX_SNAPSHOT_CARDS) {
            unique.push(cardId);
        }
    });
    return unique;
};

class MerchantModuleDashboardC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            chartData: null,
            chartDataTxTypes: null,
            chartDataTxVolumes: null,
            chartDataTxPerGateway: null,
            portalSummary: null,
            activeInsight: null,
            visibleSnapshotCards: this.loadSnapshotCards(),
            showSnapshotPicker: false,
            fetchErrors: []
        };
    }

    componentDidMount() {
        this.refreshDashboardData();
    }

    componentDidUpdate(prevProps) {
        if (prevProps.refreshSignal !== this.props.refreshSignal) {
            this.refreshDashboardData();
        }
    }

    refreshDashboardData() {
        this.getData("chartData", "getDashboardDetailsPayinsVsPayoutsMerchant");
        this.getData("chartDataTxTypes", "getDashboardDetailsTransactionTypesMerchant");
        this.getData("chartDataTxVolumes", "getDashboardDetailsTxVolumesMerchant");
        this.getData("chartDataTxPerGateway", "getDashboardDetailsTxPerGatewayMerchant");
        this.getPortalSummary();
    }

    async getPortalSummary() {
        try {
            const response = await apiFetch(apiUrl("/api/v2/portal/dashboard/summary"), {
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

    loadSnapshotCards() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            return sanitizeSnapshotCards(saved ? JSON.parse(saved) : merchantDefaultSnapshotCards);
        } catch {
            return merchantDefaultSnapshotCards;
        }
    }

    saveSnapshotCards(cards) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(cards));
        } catch {
            // Dashboard snapshots are optional; the dashboard still works without persistence.
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
        apiFetch(apiUrl("/transactions/" + api), {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify({ pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' })
        }).then((response) => response.text())
            .then((response_) => {
                this.props.loader("STOP");
                let res;
                try {
                    res = JSON.parse(response_);
                    if (res.code === "000") {
                        this.setChartData(chartType, res.chartData);
                    } else {
                        if (res.code === "107") {
                            this.sessionExpired();
                            return;
                        }
                        if (res.code === "110") {
                            this.accessNotAllowed(res.message);
                            return;
                        }
                        const errorDetails = dashboardErrorDetails(res);
                        this.addFetchError(errorDetails.message);
                        this.messager.alert({ title: errorDetails.title, icon: "error", msg: errorDetails.message });
                    }
                } catch (Error) {
                    this.addFetchError(Error.message);
                    this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
                }
            }).catch((error) => {
                this.props.loader("STOP");
                this.addFetchError(error.message);
                this.messager.alert({ title: "Error", icon: "error", msg: error.message });
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
            case "chartDataTxPerGateway":
                this.setState({ chartDataTxPerGateway: chartData });
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
        this.messager.alert({ title: "Access Denied", icon: "error", msg });
    }

    sessionExpired() {
        const { history } = this.props;
        this.messager.alert({
            title: "Session Expired!",
            icon: "info",
            msg: "Your session has expired.",
            result: () => history.push("/portal")
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
                { tone: 'success', title: 'Merchant profile', text: 'Use your account number, API keys, and callback URL for integrations.' },
                { tone: 'info', title: 'Pay In / Pay Out', text: 'Keep float available before approving payout traffic.' },
                { tone: 'info', title: 'SMS', text: 'SMS notifications require SEND_SMS access and a funded SMS balance.' },
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

    renderActiveInsight() {
        const insight = this.state.activeInsight;
        if (!insight) return null;
        return (
            <section className="cpay-dashboard-insight" aria-live="polite">
                <strong>{insight.title}</strong>
                <span>{insight.value}</span>
                <em>{insight.detail}</em>
            </section>
        );
    }

    renderChannelOverview() {
        const channels = Array.isArray(this.state.portalSummary?.activeChannels)
            ? this.state.portalSummary.activeChannels.slice(0, 6)
            : [];
        const fallback = [
            { channel_code: 'mtn_momo', display_name: 'MTN MoMo', environment: this.state.portalSummary?.environment || 'SANDBOX', status: 'Ready' },
            { channel_code: 'airtel_open_api', display_name: 'Airtel Money', environment: this.state.portalSummary?.environment || 'SANDBOX', status: 'Ready' },
            { channel_code: 'yo_payments', display_name: 'Yo! Payments', environment: this.state.portalSummary?.environment || 'SANDBOX', status: 'Available' },
        ];
        const items = channels.length ? channels : fallback;
        const limit = this.state.portalSummary?.productionLimit || {};
        return (
            <article className="cpay-dashboard-card cpay-dashboard-card-channels">
                <header className="cpay-dashboard-card-header">
                    <div>
                        <span>Channels</span>
                        <h3>Payment Channel Readiness</h3>
                    </div>
                    <strong>{this.state.portalSummary?.environment || 'SANDBOX'}</strong>
                </header>
                <div className="cpay-dashboard-channel-tiles">
                    {items.map(channel => (
                        <button
                            type="button"
                            key={`${channel.channel_code}-${channel.environment}`}
                            onClick={() => this.setState({ activeInsight: {
                                title: channel.display_name || channel.channel_code,
                                value: channel.status || 'Not configured',
                                detail: `${channel.environment || 'SANDBOX'} channel`
                            } })}
                        >
                            <strong>{channel.display_name || channel.channel_code}</strong>
                            <span>{channel.environment || 'SANDBOX'}</span>
                            <em>{channel.status || 'NOT_CONFIGURED'}</em>
                        </button>
                    ))}
                </div>
                <p className="cpay-dashboard-card-copy">
                    Production cap: {limit.enabled === false ? 'disabled' : `${limit.remainingToday ?? limit.limit ?? 10} of ${limit.limit ?? 10} remaining today`}.
                </p>
            </article>
        );
    }

    renderSnapshotCard(cardId) {
        const card = merchantSnapshotCards.find(candidate => candidate.id === cardId);
        if (!card) {
            return null;
        }

        const chartData = card.chartKey ? this.state[card.chartKey] : null;
        const chartCount = numericValues(chartData).length;
        let body;
        let metric = card.label;

        if (card.kind === 'chart') {
            body = this.renderChart(chartData, `${card.title} will appear when data loads.`);
            metric = `${chartCount} data points`;
        } else if (card.kind === 'api') {
            metric = 'Ready';
            body = <p className="cpay-dashboard-card-copy">Use the Pay In, Pay Out, Balance, Status, and SMS endpoints from your system.</p>;
        } else if (card.kind === 'sms') {
            metric = 'Enabled';
            body = <p className="cpay-dashboard-card-copy">Send customer alerts after successful collections and payouts.</p>;
        } else if (card.kind === 'float') {
            metric = 'Monitor';
            body = <p className="cpay-dashboard-card-copy">Review float before payout batches and reversals.</p>;
        } else {
            metric = 'Controlled';
            body = <p className="cpay-dashboard-card-copy">Watch failed callbacks, duplicate references, and pending payouts.</p>;
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
                {merchantSnapshotCards.map(card => {
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
        const payInPayOutTotal = numericValues(this.state.chartData).reduce((total, value) => total + value, 0);
        const volumeTotal = numericValues(this.state.chartDataTxVolumes).reduce((total, value) => total + value, 0);

        return (
            <div className="cpay-dashboard cpay-merchant-dashboard">
                <section className="cpay-dashboard-toolbar">
                    <div className="cpay-dashboard-toolbar-copy">
                        <h2>Merchant snapshot</h2>
                        <p>Pay In, Pay Out, SMS, API, and operational notices in a compact command view.</p>
                    </div>
                    <div className="cpay-dashboard-actions">
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

                <section className="cpay-dashboard-grid" aria-label="Merchant dashboard snapshot cards">
                    <button
                        type="button"
                        className="cpay-dashboard-card cpay-dashboard-card-balance cpay-dashboard-click-card"
                        onClick={() => this.setState({ activeInsight: { title: 'Pay In / Pay Out', value: formatAmount(payInPayOutTotal), detail: 'Transaction trend refreshed from merchant activity.' } })}
                    >
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Pay In / Pay Out</span>
                                <h3>Transaction Trend</h3>
                            </div>
                            <strong>{formatAmount(payInPayOutTotal)}</strong>
                        </header>
                        {this.renderChart(this.state.chartData, 'Pay In and Pay Out trends will appear when data loads.')}
                    </button>

                    <button
                        type="button"
                        className="cpay-dashboard-card cpay-dashboard-card-collections cpay-dashboard-click-card"
                        onClick={() => this.setState({ activeInsight: { title: 'Collections Trend', value: formatAmount(volumeTotal), detail: 'Collection volume across active channels.' } })}
                    >
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Volume</span>
                                <h3>Collections Trend</h3>
                            </div>
                            <strong>{formatAmount(volumeTotal)}</strong>
                        </header>
                        {this.renderChart(this.state.chartDataTxVolumes, 'Collection volumes will appear when data loads.')}
                    </button>

                    <button
                        type="button"
                        className="cpay-dashboard-card cpay-dashboard-card-notifications cpay-dashboard-click-card"
                        onClick={() => this.setState({ activeInsight: { title: 'Notifications', value: formatCount(this.state.fetchErrors.length), detail: 'Operational feed and integration warnings.' } })}
                    >
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Operational feed</span>
                                <h3>Notifications</h3>
                            </div>
                            <strong>{formatCount(this.state.fetchErrors.length)}</strong>
                        </header>
                        {this.renderNotifications()}
                    </button>

                    {this.renderChannelOverview()}
                    {this.renderActiveInsight()}
                    {this.state.visibleSnapshotCards.map(cardId => this.renderSnapshotCard(cardId))}
                    <Messager ref={ref => this.messager = ref}></Messager>
                </section>
            </div>
        );
    }
}

const MerchantModuleDashboard = withRouter(MerchantModuleDashboardC);

export default MerchantModuleDashboard;
