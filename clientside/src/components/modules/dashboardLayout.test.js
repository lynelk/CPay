const fs = require('fs');
const path = require('path');

const read = relativePath => fs.readFileSync(path.join(__dirname, '..', '..', relativePath), 'utf8');

describe('admin dashboard card layout', () => {
  test('uses dashboard cards instead of fixed rc-easyui chart panels', () => {
    const dashboard = read('components/modules/ModuleDashboard.jsx');

    expect(dashboard).toContain('cpay-dashboard');
    expect(dashboard).toContain('cpay-dashboard-card');
    expect(dashboard).toContain('Add Snapshot');
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
  });
});
