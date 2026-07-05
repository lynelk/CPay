/**
 * AIInsightsPanel.js
 *
 * An AI/ML-driven analytics component for CPay that provides:
 *  1. Anomaly detection – flags transactions whose amounts deviate
 *     significantly from the rolling mean (Z-score > threshold).
 *  2. Transaction velocity alerts – warns when the number of transactions
 *     per time bucket exceeds the historical average by a configurable factor.
 *  3. Simple trend prediction – projects the next value using linear regression
 *     on recent data points.
 *  4. Summary statistics card – mean, median, standard deviation.
 *
 * All computation is performed client-side without any external ML service.
 */

import React from 'react';
import iosTheme from './iosTheme';

// ---------------------------------------------------------------------------
// Pure ML / statistics helpers (no React)
// ---------------------------------------------------------------------------

/**
 * Compute the mean of a numeric array.
 * Returns 0 for an empty array.
 */
export function mean(values) {
    if (!values || values.length === 0) return 0;
    return values.reduce((sum, v) => sum + v, 0) / values.length;
}

/**
 * Compute the sample standard deviation of a numeric array.
 * Returns 0 for arrays with fewer than 2 elements.
 */
export function stdDev(values) {
    if (!values || values.length < 2) return 0;
    const mu = mean(values);
    const variance = values.reduce((sum, v) => sum + Math.pow(v - mu, 2), 0) / (values.length - 1);
    return Math.sqrt(variance);
}

/**
 * Compute the median of a numeric array.
 */
export function median(values) {
    if (!values || values.length === 0) return 0;
    const sorted = [...values].sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 !== 0
        ? sorted[mid]
        : (sorted[mid - 1] + sorted[mid]) / 2;
}

/**
 * Detect anomalies using Z-score method.
 *
 * @param {Array<{amount: number, [key: string]: any}>} transactions
 * @param {number} threshold  Z-score threshold (default 2.5)
 * @returns {Array} Transactions flagged as anomalies, each augmented with a
 *                  `zScore` property.
 */
export function detectAnomalies(transactions, threshold = 2.5) {
    if (!transactions || transactions.length < 3) return [];
    const amounts = transactions.map(t => Number(t.amount) || 0);
    const mu = mean(amounts);
    const sd = stdDev(amounts);
    if (sd === 0) return [];
    return transactions
        .map((t, i) => ({ ...t, zScore: Math.abs((amounts[i] - mu) / sd) }))
        .filter(t => t.zScore > threshold);
}

/**
 * Simple linear-regression trend prediction.
 *
 * Given an array of numeric values (ordered oldest → newest), returns the
 * predicted next value.
 *
 * @param {number[]} values
 * @returns {number} Predicted next point, or NaN if insufficient data.
 */
export function predictNext(values) {
    if (!values || values.length < 2) return NaN;
    const n = values.length;
    const xs = values.map((_, i) => i);
    const ys = values;
    const xMean = mean(xs);
    const yMean = mean(ys);
    const num = xs.reduce((sum, x, i) => sum + (x - xMean) * (ys[i] - yMean), 0);
    const den = xs.reduce((sum, x) => sum + Math.pow(x - xMean, 2), 0);
    if (den === 0) return yMean;
    const slope = num / den;
    const intercept = yMean - slope * xMean;
    return slope * n + intercept;
}

/**
 * Compute per-bucket transaction velocity.
 *
 * @param {Array<{created_on: string}>} transactions  Transactions with ISO date strings.
 * @param {'hour'|'day'} bucketSize
 * @returns {Object} Map of bucket label → count.
 */
export function transactionVelocity(transactions, bucketSize = 'day') {
    const buckets = {};
    (transactions || []).forEach(t => {
        const d = new Date(t.created_on);
        if (isNaN(d.getTime())) return;
        const label = bucketSize === 'hour'
            ? `${d.toISOString().slice(0, 13)}:00`
            : d.toISOString().slice(0, 10);
        buckets[label] = (buckets[label] || 0) + 1;
    });
    return buckets;
}

// ---------------------------------------------------------------------------
// React component
// ---------------------------------------------------------------------------

const { colors, typography, spacing, radii, shadows } = iosTheme;

const panelStyle = {
    backgroundColor: colors.systemBackground,
    borderRadius: radii.xl,
    boxShadow: shadows.card,
    padding: spacing.lg,
    marginBottom: spacing.md,
    fontFamily: typography.fontFamily,
};

const headerStyle = {
    ...typography.title3,
    fontFamily: typography.fontFamilyDisplay,
    color: colors.label,
    marginBottom: spacing.md,
    display: 'flex',
    alignItems: 'center',
    gap: spacing.sm,
};

const sectionTitleStyle = {
    ...typography.headline,
    fontFamily: typography.fontFamily,
    color: colors.blue,
    marginBottom: spacing.sm,
    marginTop: spacing.lg,
};

const statCardStyle = {
    backgroundColor: colors.secondarySystemBackground,
    borderRadius: radii.lg,
    padding: spacing.md,
    display: 'inline-block',
    minWidth: 120,
    marginRight: spacing.sm,
    marginBottom: spacing.sm,
    textAlign: 'center',
    verticalAlign: 'top',
};

const statValueStyle = {
    ...typography.title2,
    fontFamily: typography.fontFamilyDisplay,
    color: colors.label,
    display: 'block',
};

const statLabelStyle = {
    ...typography.caption1,
    fontFamily: typography.fontFamily,
    color: colors.secondaryLabel,
    display: 'block',
    marginTop: spacing.xs / 2,
};

const alertBadgeStyle = (severity) => ({
    display: 'inline-block',
    padding: `${spacing.xs / 2}px ${spacing.sm}px`,
    borderRadius: radii.pill,
    fontSize: typography.caption1.fontSize,
    fontWeight: typography.headline.fontWeight,
    backgroundColor: severity === 'high' ? colors.red
        : severity === 'medium' ? colors.orange
        : colors.yellow,
    color: colors.white,
    marginRight: spacing.xs,
});

const rowStyle = {
    display: 'flex',
    alignItems: 'center',
    padding: `${spacing.sm}px 0`,
    borderBottom: `1px solid ${colors.separator}`,
    ...typography.subhead,
    fontFamily: typography.fontFamily,
    color: colors.label,
};

const emptyStateStyle = {
    textAlign: 'center',
    padding: spacing.xl,
    color: colors.secondaryLabel,
    ...typography.callout,
    fontFamily: typography.fontFamily,
};

function formatAmount(amount, currency = 'UGX') {
    return `${currency} ${Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/**
 * AIInsightsPanel
 *
 * Props:
 *  - transactions {Array}  Array of transaction objects with at least:
 *      { id, amount, tx_type, status, created_on, payer_number }
 *  - currency     {string} Currency label (default 'UGX')
 *  - title        {string} Panel title (default 'AI Insights')
 */
class AIInsightsPanel extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            anomalies: [],
            stats: { mean: 0, median: 0, stdDev: 0, total: 0 },
            predicted: NaN,
            velocityAlerts: [],
        };
    }

    componentDidMount() {
        this.analyse(this.props.transactions);
    }

    componentDidUpdate(prevProps) {
        if (prevProps.transactions !== this.props.transactions) {
            this.analyse(this.props.transactions);
        }
    }

    analyse(transactions) {
        if (!transactions || transactions.length === 0) {
            this.setState({
                anomalies: [],
                stats: { mean: 0, median: 0, stdDev: 0, total: 0 },
                predicted: NaN,
                velocityAlerts: [],
            });
            return;
        }

        const amounts = transactions.map(t => Number(t.amount) || 0);
        const mu = mean(amounts);
        const sd = stdDev(amounts);
        const med = median(amounts);
        const anomalies = detectAnomalies(transactions);

        // Use the last 30 data points for prediction
        const recent = amounts.slice(-30);
        const predicted = predictNext(recent);

        // Velocity alerts: days where count > mean * 1.5
        const velocity = transactionVelocity(transactions, 'day');
        const counts = Object.values(velocity);
        const avgCount = mean(counts);
        const velocityAlerts = Object.entries(velocity)
            .filter(([, count]) => count > avgCount * 1.5 && avgCount > 0)
            .map(([date, count]) => ({ date, count, avgCount }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 5);

        this.setState({
            anomalies,
            stats: { mean: mu, median: med, stdDev: sd, total: amounts.reduce((s, v) => s + v, 0) },
            predicted,
            velocityAlerts,
        });
    }

    render() {
        const { currency = 'UGX', title = 'AI Insights' } = this.props;
        const { anomalies, stats, predicted, velocityAlerts } = this.state;
        const txCount = (this.props.transactions || []).length;

        return (
            <div style={panelStyle}>
                {/* Header */}
                <div style={headerStyle}>
                    <span style={{ fontSize: 22 }}>🤖</span>
                    <span>{title}</span>
                </div>

                {txCount === 0 ? (
                    <div style={emptyStateStyle}>No transaction data available for analysis.</div>
                ) : (
                    <div>
                        {/* Summary Statistics */}
                        <div style={sectionTitleStyle}>Summary Statistics</div>
                        <div>
                            <div style={statCardStyle}>
                                <span style={statValueStyle}>
                                    {formatAmount(stats.mean, currency)}
                                </span>
                                <span style={statLabelStyle}>Mean Amount</span>
                            </div>
                            <div style={statCardStyle}>
                                <span style={statValueStyle}>
                                    {formatAmount(stats.median, currency)}
                                </span>
                                <span style={statLabelStyle}>Median Amount</span>
                            </div>
                            <div style={statCardStyle}>
                                <span style={statValueStyle}>
                                    {formatAmount(stats.stdDev, currency)}
                                </span>
                                <span style={statLabelStyle}>Std Deviation</span>
                            </div>
                            <div style={statCardStyle}>
                                <span style={statValueStyle}>{txCount}</span>
                                <span style={statLabelStyle}>Transactions</span>
                            </div>
                            {!isNaN(predicted) && (
                                <div style={{ ...statCardStyle, borderLeft: `3px solid ${colors.blue}` }}>
                                    <span style={{ ...statValueStyle, color: colors.blue }}>
                                        {formatAmount(predicted, currency)}
                                    </span>
                                    <span style={statLabelStyle}>Predicted Next</span>
                                </div>
                            )}
                        </div>

                        {/* Anomaly Detection */}
                        <div style={sectionTitleStyle}>
                            ⚠️ Anomaly Detection
                            {anomalies.length > 0 && (
                                <span style={{ ...alertBadgeStyle('high'), marginLeft: spacing.sm }}>
                                    {anomalies.length} flagged
                                </span>
                            )}
                        </div>
                        {anomalies.length === 0 ? (
                            <div style={{ color: colors.green, ...typography.subhead, fontFamily: typography.fontFamily }}>
                                ✅ No anomalies detected in the current dataset.
                            </div>
                        ) : (
                            <div>
                                {anomalies.slice(0, 10).map((tx, i) => (
                                    <div key={tx.id || i} style={rowStyle}>
                                        <span style={alertBadgeStyle(tx.zScore > 4 ? 'high' : 'medium')}>
                                            Z={tx.zScore.toFixed(2)}
                                        </span>
                                        <span style={{ marginLeft: spacing.sm, flex: 1 }}>
                                            {tx.tx_type || 'Transaction'} &nbsp;·&nbsp;
                                            <strong>{formatAmount(tx.amount, currency)}</strong>
                                        </span>
                                        <span style={{ color: colors.secondaryLabel, fontSize: typography.caption1.fontSize }}>
                                            {tx.payer_number || ''}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* Velocity Alerts */}
                        <div style={sectionTitleStyle}>
                            🚀 Transaction Velocity
                            {velocityAlerts.length > 0 && (
                                <span style={{ ...alertBadgeStyle('medium'), marginLeft: spacing.sm }}>
                                    {velocityAlerts.length} spike{velocityAlerts.length !== 1 ? 's' : ''}
                                </span>
                            )}
                        </div>
                        {velocityAlerts.length === 0 ? (
                            <div style={{ color: colors.green, ...typography.subhead, fontFamily: typography.fontFamily }}>
                                ✅ Transaction velocity is within normal range.
                            </div>
                        ) : (
                            <div>
                                {velocityAlerts.map(({ date, count, avgCount }) => (
                                    <div key={date} style={rowStyle}>
                                        <span style={alertBadgeStyle('medium')}>
                                            {count} tx
                                        </span>
                                        <span style={{ marginLeft: spacing.sm, flex: 1 }}>
                                            {date}
                                        </span>
                                        <span style={{ color: colors.secondaryLabel, fontSize: typography.caption1.fontSize }}>
                                            avg {avgCount.toFixed(1)}/day
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        );
    }
}

export default AIInsightsPanel;
