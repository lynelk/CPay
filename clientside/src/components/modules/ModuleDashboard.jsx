import React from 'react';
import { Messager } from 'rc-easyui';
import { withRouter } from "react-router-dom";
import common from "../Common";
import { CardsIcon, CheckIcon, CloseIcon } from "../ShellIcons";
import LinearChart from './LinearChart';

export const dashboardErrorDetails = (res) => {
    const hasCode = res && res.code !== undefined && res.code !== null && String(res.code).trim() !== "";
    const message = (res && (res.message || res.error))
        || (res && res.status ? `Request failed with status ${res.status}` : "Request failed.");

    return {
        title: hasCode ? `Error ${res.code}` : "Error",
        message
    };
};

export const defaultSnapshotCards = ['transactionMix', 'collectionVolume', 'gatewayHealth'];

export const availableSnapshotCards = [
    { id: 'transactionMix', title: 'Transaction Mix', label: 'Types', kind: 'chart', chartKey: 'chartDataTxTypes' },
    { id: 'collectionVolume', title: 'Collection Volume', label: 'Amounts', kind: 'chart', chartKey: 'chartDataTxVolumes' },
    { id: 'gatewayHealth', title: 'Gateway Health', label: 'MTN / Airtel', kind: 'status' },
    { id: 'smsNotifications', title: 'SMS Notifications', label: 'Messaging', kind: 'sms' },
    { id: 'floatWatch', title: 'Float Watch', label: 'Liquidity', kind: 'float' },
    { id: 'settlementRisk', title: 'Settlement Risk', label: 'Controls', kind: 'risk' },
];

const STORAGE_KEY = 'cpay-admin-dashboard-snapshots';
const MAX_SNAPSHOT_CARDS = 4;

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

const sanitizeSnapshotCards = (cards) => {
    const allowed = new Set(availableSnapshotCards.map(card => card.id));
    const unique = [];
    (Array.isArray(cards) ? cards : defaultSnapshotCards).forEach(cardId => {
        if (allowed.has(cardId) && !unique.includes(cardId) && unique.length < MAX_SNAPSHOT_CARDS) {
            unique.push(cardId);
        }
    });
    return unique.length > 0 ? unique : defaultSnapshotCards;
};

class ModuleDashboardC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            chartData: null,
            chartDataTxTypes: null,
            chartDataTxVolumes: null,
            chartDataTxNetworkBalances: null,
            visibleSnapshotCards: this.loadSnapshotCards(),
            showSnapshotPicker: false,
            fetchErrors: []
        };
        this._balanceInterval = null;
    }

    componentDidMount() {
        this.getData("chartData", "getDashboardDetailsPayinsVsPayouts");
        this.getData("chartDataTxTypes", "getDashboardDetailsTransactionTypes");
        this.getData("chartDataTxVolumes", "getDashboardDetailsTxVolumes");
        this.getData("chartDataTxNetworkBalances", "getDashboardDetailsNetworkBalances");
        this._balanceInterval = setInterval(() => {
            this.getData("chartDataTxNetworkBalances", "getDashboardDetailsNetworkBalances");
        }, 240000);
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
        } catch (error) {
            return defaultSnapshotCards;
        }
    }

    saveSnapshotCards(cards) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(cards));
        } catch (error) {
            // Local storage is optional; the dashboard still works without persistence.
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
        let searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        fetch(common.base_url + "/transactions/" + api, {
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
                <LinearChart data={data} title={emptyText} color="#2563eb" />
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

        return (
            <div className="cpay-dashboard">
                <section className="cpay-dashboard-toolbar">
                    <div className="cpay-dashboard-toolbar-copy">
                        <h2>Operations snapshot</h2>
                        <p>Balances, collections, notifications, and pinned cards in a compact command view.</p>
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

                <section className="cpay-dashboard-grid" aria-label="Dashboard snapshot cards">
                    <article className="cpay-dashboard-card cpay-dashboard-card-balance">
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Live liquidity</span>
                                <h3>Network Balances</h3>
                            </div>
                            <strong>{formatAmount(balanceTotal)}</strong>
                        </header>
                        {this.renderChart(this.state.chartDataTxNetworkBalances, 'Network balances will appear when data loads.')}
                    </article>

                    <article className="cpay-dashboard-card cpay-dashboard-card-collections">
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Collections</span>
                                <h3>Collections Trend</h3>
                            </div>
                            <strong>{formatAmount(collectionTotal)}</strong>
                        </header>
                        {this.renderChart(this.state.chartData, 'Collection trends will appear when data loads.')}
                    </article>

                    <article className="cpay-dashboard-card cpay-dashboard-card-notifications">
                        <header className="cpay-dashboard-card-header">
                            <div>
                                <span>Operational feed</span>
                                <h3>Notifications</h3>
                            </div>
                            <strong>{formatCount(this.state.fetchErrors.length)}</strong>
                        </header>
                        {this.renderNotifications()}
                    </article>

                    {this.state.visibleSnapshotCards.map(cardId => this.renderSnapshotCard(cardId))}
                    <Messager ref={ref => this.messager = ref}></Messager>
                </section>
            </div>
        );
    }
}

const ModuleDashboard = withRouter(ModuleDashboardC);

export default ModuleDashboard;
