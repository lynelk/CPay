/**
 * Unit tests for clientside/src/components/modules/LinearChart.js
 */

import React from 'react';
import { render, unmountComponentAtNode } from 'react-dom';
import { act } from 'react-dom/test-utils';
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

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
});

afterEach(() => {
  unmountComponentAtNode(container);
  container.remove();
  container = null;
  Chart.mockClear();
  Chart.prototype.destroy.mockClear();
  Chart.prototype.update.mockClear();
});

describe('LinearChart', () => {
  test('renders a canvas element', () => {
    act(() => {
      render(<LinearChart data={null} title="Test Chart" color="#fff" />, container);
    });
    expect(container.querySelector('canvas')).not.toBeNull();
  });

  test('renders without crashing when data is null', () => {
    act(() => {
      render(<LinearChart data={null} title="No Data" color="#aaa" />, container);
    });
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
    act(() => {
      render(
        <LinearChart data={chartData} title="Payins vs Payouts" color="#70CAD1" />,
        container
      );
    });
    expect(container.querySelector('canvas')).toBeTruthy();
  });

  test('re-renders without crashing on prop update', () => {
    act(() => {
      render(<LinearChart data={null} title="Initial" color="#fff" />, container);
    });
    act(() => {
      render(<LinearChart data={null} title="Updated" color="#000" />, container);
    });
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

    act(() => {
      render(<ChartComponent data={firstData} title="Initial" color="#fff" />, container);
    });

    act(() => {
      render(<ChartComponent data={secondData} title="Updated" color="#000" />, container);
    });

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

    act(() => {
      render(<ChartComponent data={chartData} title="Unmount" color="#fff" />, container);
    });

    act(() => {
      unmountComponentAtNode(container);
    });

    expect(Chart.prototype.destroy).toHaveBeenCalledTimes(1);
  });
});
