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

  test('merchant settings render sensitive rows with password-style editors', () => {
    const settings = read('components/modules/merchant/MerchantModuleSettings.jsx');

    expect(settings).toContain('isSensitiveSetting');
    expect(settings).toContain('<PasswordField');
    expect(settings).toContain('maskedSettingValue');
  });
});
