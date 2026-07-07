const fs = require('fs');
const path = require('path');

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('settings security cleanup', () => {
  test('admin settings render sensitive rows with password-style editors', () => {
    const settings = read('components/modules/ModuleSettings.jsx');

    expect(settings).toContain('isSensitiveSetting');
    expect(settings).toContain('<PasswordBox');
    expect(settings).toContain('maskedSettingValue');
  });

  test('merchant settings render sensitive rows with password-style editors', () => {
    const settings = read('components/modules/merchant/MerchantModuleSettings.jsx');

    expect(settings).toContain('isSensitiveSetting');
    expect(settings).toContain('<PasswordBox');
    expect(settings).toContain('maskedSettingValue');
  });
});