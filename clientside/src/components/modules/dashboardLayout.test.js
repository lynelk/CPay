import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const read = relativePath => fs.readFileSync(path.join(__dirname, '..', '..', relativePath), 'utf8');

describe('admin dashboard card layout', () => {
  test('uses dashboard cards instead of fixed rc-easyui chart panels', () => {
    const dashboard = read('components/modules/ModuleDashboard.jsx');

    expect(dashboard).toContain('cpay-dashboard');
    expect(dashboard).toContain('cpay-dashboard-card');
    expect(dashboard).toContain('Customize cards');
    expect(dashboard).not.toContain('Add Snapshot');
    expect(dashboard).not.toMatch(/<Panel\b/);
    expect(dashboard).not.toContain('dashboardChartPanel');
  });

  test('defines the requested balance, notification, collection, and addable snapshot surfaces', () => {
    const dashboard = read('components/modules/ModuleDashboard.jsx');

    expect(dashboard).toContain('Network Balances');
    expect(dashboard).toContain('Collections Trend');
    expect(dashboard).toContain('Notifications');
    expect(dashboard).toContain('availableSnapshotCards');
  });

  test('keeps the dashboard inside the viewport without page scrolling', () => {
    const css = read('index.css');

    expect(css).toMatch(/\.cpay-main-dashboard\s*\{[^}]*height:\s*100vh;[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-dashboard\s*\{[^}]*height:\s*100%;[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-dashboard-grid\s*\{[^}]*grid-template-columns:\s*repeat\(12, minmax\(0, 1fr\)\);[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/@media \(max-width:\s*760px\)[\s\S]*\.cpay-main-dashboard\s*\{[\s\S]*height:\s*auto;/);
    expect(css).toMatch(/\.cpay-dashboard-chart-shell\s*\{[\s\S]*overflow:\s*hidden;/);
  });

  test('snapshot picker can toggle cards on and off', () => {
    const dashboard = read('components/modules/ModuleDashboard.jsx');

    expect(dashboard).toContain('toggleSnapshotCard');
    expect(dashboard).toContain('cpay-dashboard-picker-option-active');
    expect(dashboard).toContain('aria-pressed={isActive}');
  });
});

describe('shared chart cleanup', () => {
  test('merchant chart reuses the shared LinearChart implementation', () => {
    const merchantChart = read('components/modules/merchant/LinearChart.jsx');

    expect(merchantChart).toContain("from '../LinearChart'");
    expect(merchantChart).not.toContain("chart.js/auto");
  });
});
describe('merchant dashboard card layout', () => {
  test('merchant dashboard uses cards instead of fixed rc-easyui chart panels', () => {
    const dashboard = read('components/modules/merchant/MerchantModuleDashboard.jsx');

    expect(dashboard).toContain('cpay-dashboard');
    expect(dashboard).toContain('cpay-dashboard-card');
    expect(dashboard).toContain('Customize cards');
    expect(dashboard).not.toContain('Add Snapshot');
    expect(dashboard).toContain('toggleSnapshotCard');
    expect(dashboard).not.toMatch(/<Panel\b/);
    expect(dashboard).not.toContain('dashboardChartPanel');
  });
});
