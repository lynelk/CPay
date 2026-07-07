/**
 * Unit tests for clientside/src/components/modules/LinearChart.js
 */

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import LinearChart from './LinearChart';
import MerchantLinearChart from './merchant/LinearChart';
import Chart from 'chart.js/auto';

// Mock Chart.js so tests don't require a canvas implementation
jest.mock('chart.js/auto', () => {
  const ChartMock = jest.fn();
  ChartMock.prototype.destroy = jest.fn();
  ChartMock.prototype.update = jest.fn();
  return ChartMock;
});

let container = null;
let root = null;

const renderChart = (component) => {
  act(() => {
    root.render(component);
  });
};

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  if (root) {
    act(() => {
      root.unmount();
    });
  }
  container.remove();
  container = null;
  root = null;
  Chart.mockClear();
  Chart.prototype.destroy.mockClear();
  Chart.prototype.update.mockClear();
});

describe('LinearChart', () => {
  test('renders a canvas element', () => {
    renderChart(<LinearChart data={null} title="Test Chart" color="#fff" />);
    expect(container.querySelector('canvas')).not.toBeNull();
  });

  test('renders without crashing when data is null', () => {
    renderChart(<LinearChart data={null} title="No Data" color="#aaa" />);
    expect(container.querySelector('canvas')).toBeTruthy();
  });

  test('renders without crashing when data is a valid chart config', () => {
    const chartData = {
      type: 'line',
      data: {
        labels: ['Jan', 'Feb', 'Mar'],
        datasets: [{ label: 'Payins', data: [100, 200, 300] }],
      },
    };
    renderChart(<LinearChart data={chartData} title="Payins vs Payouts" color="#70CAD1" />);
    expect(container.querySelector('canvas')).toBeTruthy();
  });

  test('re-renders without crashing on prop update', () => {
    renderChart(<LinearChart data={null} title="Initial" color="#fff" />);
    renderChart(<LinearChart data={null} title="Updated" color="#000" />);
    expect(container.querySelector('canvas')).toBeTruthy();
  });

  test.each([
    ['admin dashboard', LinearChart],
    ['merchant dashboard', MerchantLinearChart],
  ])('destroys the active Chart.js instance before replacing it on %s updates', (_, ChartComponent) => {
    const firstData = {
      type: 'line',
      data: {
        labels: ['Jan'],
        datasets: [{ label: 'Payins', data: [100] }],
      },
    };
    const secondData = {
      type: 'line',
      data: {
        labels: ['Feb'],
        datasets: [{ label: 'Payouts', data: [75] }],
      },
    };

    renderChart(<ChartComponent data={firstData} title="Initial" color="#fff" />);
    renderChart(<ChartComponent data={secondData} title="Updated" color="#000" />);

    expect(Chart.prototype.destroy).toHaveBeenCalledTimes(1);
    expect(Chart).toHaveBeenCalledTimes(2);
  });

  test.each([
    ['admin dashboard', LinearChart],
    ['merchant dashboard', MerchantLinearChart],
  ])('destroys the active Chart.js instance when %s chart unmounts', (_, ChartComponent) => {
    const chartData = {
      type: 'bar',
      data: {
        labels: ['UGX'],
        datasets: [{ label: 'Volume', data: [3] }],
      },
    };

    renderChart(<ChartComponent data={chartData} title="Unmount" color="#fff" />);

    act(() => {
      root.unmount();
    });
    root = null;

    expect(Chart.prototype.destroy).toHaveBeenCalledTimes(1);
  });
});