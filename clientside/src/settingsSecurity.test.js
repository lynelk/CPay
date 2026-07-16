import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('settings security cleanup', () => {
  test('admin settings render sensitive rows with password-style editors', () => {
    const settings = read('components/modules/ModuleSettings.jsx');

    expect(settings).toContain('isSensitiveSetting');
    expect(settings).toContain('<PasswordField');
    expect(settings).toContain('maskedSettingValue');
  });

  test('admin settings use the structured settings workspace layout', () => {
    const settings = read('components/modules/ModuleSettings.jsx');

    expect(settings).toContain('cpay-settings-workspace');
    expect(settings).toContain('cpay-settings-status-grid');
    expect(settings).toContain('cpay-settings-savebar');
    expect(settings).toContain('Admin Login Portal');
  });

  test('admin login uses public appearance settings and media auth layout', () => {
    const login = read('components/Login.tsx');

    expect(login).toContain('getAdminLoginAppearance');
    expect(login).toContain('admin_login_hero_image_url');
    expect(login).toContain('photo-1551288049-bebda4e38f71');
    expect(login).toContain('asideVariant="media"');
    expect(login).toContain('asideCards={asideCards}');
  });

  test('merchant login uses the first supplied portal visual and five benefit tiles', () => {
    const login = read('components/LoginMerchant.tsx');
    const css = read('styles/ios.css');

    expect(login).toContain('merchant_login_hero_image_url');
    expect(login).toContain('photo-1573496359142-b8d87734a5a2');
    expect(login).toContain('merchant_login_automation_title');
    expect(login).toContain('merchant_login_benefit_insights_title');
    expect(css).toMatch(/\.ios-auth-merchant\s+\.ios-auth__benefit-strip\s*\{[^}]*repeat\(5, minmax\(0, 1fr\)\)/s);
  });

  test('merchant settings render sensitive rows with password-style editors', () => {
    const settings = read('components/modules/merchant/MerchantModuleSettings.jsx');

    expect(settings).toContain('isSensitiveSetting');
    expect(settings).toContain('<PasswordField');
    expect(settings).toContain('maskedSettingValue');
  });
});
