/**
 * Unit tests for clientside/src/components/modules/LinearChart.js
 */

import React from 'react';
import { render, unmountComponentAtNode } from 'react-dom';
import { act } from 'react-dom/test-utils';
import LinearChart from './LinearChart';

// Mock Chart.js so tests don't require a canvas implementation
jest.mock('chart.js', () => {
  return jest.fn().mockImplementation(() => ({
    destroy: jest.fn(),
    update: jest.fn(),
  }));
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
});
