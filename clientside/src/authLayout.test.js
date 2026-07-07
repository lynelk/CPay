const fs = require('fs');
const path = require('path');

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('auth layout viewport safety', () => {
  test('auth CSS owns the viewport and prevents nested auth scrollbars', () => {
    const css = read('index.css');

    expect(css).toMatch(/\.cpay-auth-screen\s*\{[^}]*height:\s*100vh;[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-auth-card\s*\{[^}]*max-height:\s*calc\(100vh - 48px\);[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-auth-form\s+\.form-field\s*\{[^}]*margin-bottom:\s*0;/s);
  });

  test('admin and merchant login screens do not use fixed-height rc-easyui panels', () => {
    const adminLogin = read('components/Login.jsx');
    const merchantLogin = read('components/LoginMerchant.jsx');

    expect(adminLogin).toContain("from './AuthShell'");
    expect(merchantLogin).toContain("from './AuthShell'");
    expect(adminLogin).not.toMatch(/<Panel\b/);
    expect(merchantLogin).not.toMatch(/<Panel\b/);
    expect(adminLogin).not.toMatch(/height:\s*\d+/);
    expect(merchantLogin).not.toMatch(/height:\s*\d+/);
  });

  test('login screens avoid legacy ReactDOM lookups for keyboard handling', () => {
    const adminLogin = read('components/Login.jsx');
    const merchantLogin = read('components/LoginMerchant.jsx');

    expect(adminLogin).not.toContain('findDOMNode');
    expect(merchantLogin).not.toContain('findDOMNode');
  });

  test('login shell centers the brand mark and avoids service chips on the login screen', () => {
    const authShell = read('components/AuthShell.jsx');
    const css = read('index.css');

    expect(authShell).not.toContain("['MTN', 'Airtel', 'Pay In', 'Pay Out', 'SMS']");
    expect(authShell).not.toContain('cpay-auth-chip-grid');
    expect(authShell).toContain('cpay-auth-header-centered');
    expect(css).toMatch(/\.cpay-auth-header-centered\s*\{[^}]*justify-content:\s*center;[^}]*text-align:\s*center;/s);
    expect(css).toMatch(/\.cpay-auth-brand-large\s*\{[^}]*margin:\s*0 auto;/s);
  });
});
