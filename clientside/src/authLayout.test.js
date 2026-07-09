const fs = require('fs');
const path = require('path');

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('auth layout viewport safety', () => {
  test('legacy AuthShell CSS still owns the viewport and prevents nested auth scrollbars', () => {
    const css = read('index.css');

    expect(css).toMatch(/\.cpay-auth-screen\s*\{[^}]*height:\s*100vh;[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-auth-card\s*\{[^}]*max-height:\s*calc\(100vh - 48px\);[^}]*overflow:\s*hidden;/s);
    expect(css).toMatch(/\.cpay-auth-form\s+\.form-field\s*\{[^}]*margin-bottom:\s*0;/s);
  });

  test('iOS auth layout owns the viewport without nested scrollbars', () => {
    const css = read('styles/ios.css');

    expect(css).toMatch(/\.ios-auth\s*\{[^}]*min-height:\s*100vh;/s);
    expect(css).toMatch(/\.ios-auth__card\s*\{[^}]*overflow:\s*hidden;/s);
  });

  test('admin, merchant, and signup screens are off rc-easyui and use the iOS AuthLayout', () => {
    const adminLogin = read('components/Login.tsx');
    const merchantLogin = read('components/LoginMerchant.tsx');
    const signup = read('components/MerchantSignup.tsx');

    for (const source of [adminLogin, merchantLogin, signup]) {
      expect(source).toContain('AuthLayout');
      expect(source).toContain("from '../ui'");
      expect(source).not.toContain('rc-easyui');
      expect(source).not.toMatch(/<Panel\b/);
      expect(source).not.toMatch(/height:\s*\d+/);
      expect(source).not.toContain('findDOMNode');
    }
  });

  test('migrated screens no longer depend on the rc-easyui Progress loader', () => {
    const adminLogin = read('components/Login.tsx');
    const merchantLogin = read('components/LoginMerchant.tsx');

    expect(adminLogin).not.toContain("from './Progress'");
    expect(merchantLogin).not.toContain("from './Progress'");
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
