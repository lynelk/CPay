/**
 * Unit tests for AI/ML utility functions in AIInsightsPanel.js
 */

import {
    mean,
    stdDev,
    median,
    detectAnomalies,
    predictNext,
    transactionVelocity,
} from './AIInsightsPanel';

// ---------------------------------------------------------------------------
// mean
// ---------------------------------------------------------------------------
describe('mean', () => {
    test('returns 0 for empty array', () => {
        expect(mean([])).toBe(0);
    });

    test('returns 0 for null', () => {
        expect(mean(null)).toBe(0);
    });

    test('returns correct mean for single element', () => {
        expect(mean([5])).toBe(5);
    });

    test('returns correct mean for multiple elements', () => {
        expect(mean([2, 4, 6])).toBe(4);
    });

    test('handles decimals', () => {
        expect(mean([1.5, 2.5, 3])).toBeCloseTo(2.333, 2);
    });
});

// ---------------------------------------------------------------------------
// stdDev
// ---------------------------------------------------------------------------
describe('stdDev', () => {
    test('returns 0 for empty array', () => {
        expect(stdDev([])).toBe(0);
    });

    test('returns 0 for single element', () => {
        expect(stdDev([42])).toBe(0);
    });

    test('returns correct standard deviation', () => {
        // Sample standard deviation of [2, 4, 4, 4, 5, 5, 7, 9]
        // Mean = 5, sample variance = sum((x-5)^2) / (n-1) = 32/7 ≈ 4.571, sd ≈ 2.138
        expect(stdDev([2, 4, 4, 4, 5, 5, 7, 9])).toBeCloseTo(2.138, 2);
    });

    test('returns 0 when all values are identical', () => {
        expect(stdDev([5, 5, 5, 5])).toBe(0);
    });
});

// ---------------------------------------------------------------------------
// median
// ---------------------------------------------------------------------------
describe('median', () => {
    test('returns 0 for empty array', () => {
        expect(median([])).toBe(0);
    });

    test('returns the only element for single element array', () => {
        expect(median([7])).toBe(7);
    });

    test('returns the middle element for odd-length array', () => {
        expect(median([3, 1, 2])).toBe(2);
    });

    test('returns average of two middle elements for even-length array', () => {
        expect(median([1, 2, 3, 4])).toBe(2.5);
    });

    test('handles negative numbers', () => {
        expect(median([-5, -1, 0, 4, 10])).toBe(0);
    });
});

// ---------------------------------------------------------------------------
// detectAnomalies
// ---------------------------------------------------------------------------
describe('detectAnomalies', () => {
    test('returns empty array for null input', () => {
        expect(detectAnomalies(null)).toEqual([]);
    });

    test('returns empty array for fewer than 3 transactions', () => {
        expect(detectAnomalies([{ original_amount: 100 }, { original_amount: 200 }])).toEqual([]);
    });

    test('detects obvious outlier', () => {
        // 9 normal transactions + 1 clear outlier; Z-score of outlier ≈ 2.85 > 2.5
        const transactions = [
            ...Array.from({ length: 9 }, (_, i) => ({ id: i + 1, original_amount: 100 })),
            { id: 10, original_amount: 10000 },
        ];
        const anomalies = detectAnomalies(transactions);
        expect(anomalies.length).toBeGreaterThan(0);
        expect(anomalies.some(a => a.id === 10)).toBe(true);
    });

    test('returns no anomalies for uniform amounts', () => {
        const transactions = Array.from({ length: 10 }, (_, i) => ({ id: i, original_amount: 500 }));
        expect(detectAnomalies(transactions)).toEqual([]);
    });

    test('augments result with zScore property', () => {
        const transactions = [
            { id: 1, original_amount: 100 },
            { id: 2, original_amount: 100 },
            { id: 3, original_amount: 100 },
            { id: 4, original_amount: 5000 },
        ];
        const anomalies = detectAnomalies(transactions, 1.0);
        expect(anomalies.length).toBeGreaterThan(0);
        anomalies.forEach(a => expect(typeof a.zScore).toBe('number'));
    });

    test('respects custom threshold', () => {
        const transactions = [
            { id: 1, original_amount: 100 },
            { id: 2, original_amount: 200 },
            { id: 3, original_amount: 150 },
            { id: 4, original_amount: 1000 },
        ];
        // High threshold – nothing detected
        const high = detectAnomalies(transactions, 10);
        expect(high.length).toBe(0);
        // Low threshold – something detected
        const low = detectAnomalies(transactions, 0.5);
        expect(low.length).toBeGreaterThan(0);
    });
});

// ---------------------------------------------------------------------------
// predictNext
// ---------------------------------------------------------------------------
describe('predictNext', () => {
    test('returns NaN for null', () => {
        expect(predictNext(null)).toBeNaN();
    });

    test('returns NaN for a single element', () => {
        expect(predictNext([5])).toBeNaN();
    });

    test('predicts correctly for a perfect linear sequence', () => {
        // y = 2x: [0, 2, 4, 6, 8] → next should be ~10
        expect(predictNext([0, 2, 4, 6, 8])).toBeCloseTo(10, 1);
    });

    test('predicts correctly for a decreasing sequence', () => {
        // y = -x: [5, 4, 3, 2, 1] → next should be ~0
        expect(predictNext([5, 4, 3, 2, 1])).toBeCloseTo(0, 1);
    });

    test('handles a constant sequence (returns the constant)', () => {
        expect(predictNext([7, 7, 7, 7])).toBeCloseTo(7, 1);
    });
});

// ---------------------------------------------------------------------------
// transactionVelocity
// ---------------------------------------------------------------------------
describe('transactionVelocity', () => {
    test('returns empty object for null input', () => {
        expect(transactionVelocity(null)).toEqual({});
    });

    test('returns empty object for empty array', () => {
        expect(transactionVelocity([])).toEqual({});
    });

    test('groups transactions by day', () => {
        const txs = [
            { created_on: '2024-01-15T10:00:00Z' },
            { created_on: '2024-01-15T14:30:00Z' },
            { created_on: '2024-01-16T09:00:00Z' },
        ];
        const result = transactionVelocity(txs, 'day');
        expect(result['2024-01-15']).toBe(2);
        expect(result['2024-01-16']).toBe(1);
    });

    test('groups transactions by hour', () => {
        const txs = [
            { created_on: '2024-01-15T10:05:00Z' },
            { created_on: '2024-01-15T10:45:00Z' },
            { created_on: '2024-01-15T11:00:00Z' },
        ];
        const result = transactionVelocity(txs, 'hour');
        expect(result['2024-01-15T10:00']).toBe(2);
        expect(result['2024-01-15T11:00']).toBe(1);
    });

    test('skips invalid date strings', () => {
        const txs = [
            { created_on: 'invalid-date' },
            { created_on: '2024-03-01T00:00:00Z' },
        ];
        const result = transactionVelocity(txs, 'day');
        expect(Object.keys(result).length).toBe(1);
    });
});
