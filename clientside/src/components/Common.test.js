/**
 * Comprehensive unit tests for clientside/src/components/Common.js
 *
 * Run with: npm test -- --testPathPattern=Common.test.js
 */

const common = require('./Common');

// ---------------------------------------------------------------------------
// emailValidation
// ---------------------------------------------------------------------------
describe('emailValidation', () => {
  const validate = common.emailValidation.validator;

  test('accepts a standard email address', () => {
    expect(validate('user@example.com')).toBe(true);
  });

  test('accepts email with subdomain', () => {
    expect(validate('name@mail.subdomain.org')).toBe(true);
  });

  test('accepts email with plus-alias', () => {
    expect(validate('user+alias@example.co')).toBe(true);
  });

  test('rejects email without @ symbol', () => {
    expect(validate('userexample.com')).toBe(false);
  });

  test('rejects email without domain extension', () => {
    expect(validate('user@example')).toBe(false);
  });

  test('rejects empty string', () => {
    expect(validate('')).toBe(false);
  });

  test('rejects plain text', () => {
    expect(validate('not-an-email')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// phoneValidation
// ---------------------------------------------------------------------------
describe('phoneValidation', () => {
  const validate = common.phoneValidation.validator;

  test('accepts a valid international phone (e.g. 256700123456)', () => {
    // Pattern: 1-3 digit country code + 3 digits + 6 digits
    expect(validate('256700123456')).toBe(true);
  });

  test('accepts a 10-digit number (1-digit country code)', () => {
    expect(validate('1212345678')).toBe(true);
  });

  test('rejects a number that is too short', () => {
    expect(validate('1234')).toBe(false);
  });

  test('rejects an empty string', () => {
    expect(validate('')).toBe(false);
  });

  test('rejects a number with letters', () => {
    expect(validate('abc123456789')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// numericValidation
// ---------------------------------------------------------------------------
describe('numericValidation', () => {
  const validate = common.numericValidation.validator;

  test('accepts an integer', () => {
    expect(validate(42)).toBe(true);
  });

  test('accepts a decimal number', () => {
    expect(validate(3.14)).toBe(true);
  });

  test('accepts a numeric string', () => {
    expect(validate('1000')).toBe(true);
  });

  test('accepts a decimal numeric string', () => {
    expect(validate('99.99')).toBe(true);
  });

  test('rejects alphabetical input', () => {
    expect(validate('abc')).toBe(false);
  });

  test('rejects mixed alphanumeric string', () => {
    expect(validate('12abc')).toBe(false);
  });

  test('rejects empty string', () => {
    expect(validate('')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// formatDate
// ---------------------------------------------------------------------------
describe('formatDate', () => {
  test('returns empty string for null', () => {
    expect(common.formatDate(null)).toBe('');
  });

  test('returns empty string for empty string', () => {
    expect(common.formatDate('')).toBe('');
  });

  test('formats a date as YYYY-MM-DD', () => {
    const date = new Date(2024, 0, 5); // Jan 5, 2024
    expect(common.formatDate(date)).toBe('2024-01-05');
  });

  test('pads single-digit month and day', () => {
    const date = new Date(2023, 2, 8); // March 8, 2023
    expect(common.formatDate(date)).toBe('2023-03-08');
  });

  test('handles December (month 11)', () => {
    const date = new Date(2022, 11, 31); // Dec 31, 2022
    expect(common.formatDate(date)).toBe('2022-12-31');
  });
});

// ---------------------------------------------------------------------------
// getDateMonthsBefore
// ---------------------------------------------------------------------------
describe('getDateMonthsBefore', () => {
  test('subtracts months within the same year', () => {
    const date = new Date(2024, 5, 15); // June 15, 2024
    const result = common.getDateMonthsBefore(new Date(date), 3);
    expect(result.getFullYear()).toBe(2024);
    expect(result.getMonth()).toBe(2); // March
  });

  test('returns a date in the previous year when crossing a year boundary', () => {
    const date = new Date(2024, 1, 15); // Feb 15, 2024
    const result = common.getDateMonthsBefore(new Date(date), 3);
    // The function returns a date in the previous year
    expect(result.getFullYear()).toBe(2023);
  });

  test('handles end-of-month overflow (Oct 31 - 1 month)', () => {
    // Oct 31 minus 1 month: September doesn't have 31 days, so JS overflows to Oct 1
    // The implementation corrects this to Sep 30
    const date = new Date(2024, 9, 31); // Oct 31, 2024
    const result = common.getDateMonthsBefore(new Date(date), 1);
    expect(result.getMonth()).toBe(8); // September
    expect(result.getDate()).toBe(30);
  });
});

// ---------------------------------------------------------------------------
// getDateMonthsAfter
// ---------------------------------------------------------------------------
describe('getDateMonthsAfter', () => {
  test('adds months within the same year', () => {
    const date = new Date(2024, 0, 15); // Jan 15, 2024
    const result = common.getDateMonthsAfter(new Date(date), 3);
    expect(result.getFullYear()).toBe(2024);
    expect(result.getMonth()).toBe(3); // April
  });

  test('adds months across year boundary', () => {
    const date = new Date(2024, 10, 15); // Nov 15, 2024
    const result = common.getDateMonthsAfter(new Date(date), 3);
    // The function returns a date in 2025
    expect(result.getFullYear()).toBe(2025);
  });

  test('handles end-of-month overflow (Jan 31 + 1 month)', () => {
    // Jan 31 plus 1 month: Feb doesn't have 31 days so JS overflows to Mar 2/3
    // The implementation corrects this to Feb 28/29
    const date = new Date(2023, 0, 31); // Jan 31, 2023
    const result = common.getDateMonthsAfter(new Date(date), 1);
    expect(result.getMonth()).toBe(1); // February
    // Feb 2023 has 28 days
    expect(result.getDate()).toBe(28);
  });
});

// ---------------------------------------------------------------------------
// encodeHTML / decodeHTML
// ---------------------------------------------------------------------------
describe('encodeHTML', () => {
  test('encodes < and > characters', () => {
    const encoded = common.encodeHTML('<script>');
    expect(encoded).not.toContain('<');
    expect(encoded).not.toContain('>');
  });

  test('encodes & character', () => {
    const encoded = common.encodeHTML('a & b');
    expect(encoded).not.toContain('&b');
    expect(encoded).toContain('&#38;');
  });

  test('leaves plain ASCII untouched', () => {
    expect(common.encodeHTML('hello world')).toBe('hello world');
  });
});

describe('decodeHTML', () => {
  test('decodes numeric HTML entities', () => {
    expect(common.decodeHTML('&#65;')).toBe('A');
    expect(common.decodeHTML('&#97;')).toBe('a');
  });

  test('decodes multiple entities in a string', () => {
    const decoded = common.decodeHTML('&#72;&#101;&#108;&#108;&#111;');
    expect(decoded).toBe('Hello');
  });

  test('leaves plain text untouched', () => {
    expect(common.decodeHTML('plain text')).toBe('plain text');
  });
});

describe('encodeHTML and decodeHTML roundtrip', () => {
  test('encodes then decodes back to original for high-range characters', () => {
    const original = '\u00A9 Copyright 2024'; // © Copyright 2024
    const encoded = common.encodeHTML(original);
    const decoded = common.decodeHTML(encoded);
    expect(decoded).toBe(original);
  });
});

// ---------------------------------------------------------------------------
// formatNumber
// ---------------------------------------------------------------------------
describe('formatNumber', () => {
  test('formats millions with commas', () => {
    expect(common.formatNumber(1000000)).toBe('1,000,000');
  });

  test('formats thousands with commas', () => {
    expect(common.formatNumber(50000)).toBe('50,000');
  });

  test('leaves numbers below 1000 unchanged', () => {
    expect(common.formatNumber(999)).toBe('999');
  });

  test('handles zero', () => {
    expect(common.formatNumber(0)).toBe('0');
  });

  test('handles negative numbers', () => {
    expect(common.formatNumber(-1000)).toBe('-1,000');
  });
});

// ---------------------------------------------------------------------------
// privileges / merchant_privileges / tx_types / balance_type / account_types
// ---------------------------------------------------------------------------
describe('static data arrays', () => {
  test('privileges contains required entries', () => {
    const values = common.privileges.map(p => p.value);
    expect(values).toContain('CREATE_MERCHANT');
    expect(values).toContain('ACCESS_TRANSACTION_LOG');
    expect(values).toContain('ACCESS_AUDITTRAIL');
  });

  test('merchant_privileges contains required entries', () => {
    const values = common.merchant_privileges.map(p => p.value);
    expect(values).toContain('CREATE_BATCH_TX');
    expect(values).toContain('APPROVE_BATCH_TX');
    expect(values).toContain('SEND_SMS');
  });

  test('tx_types covers all transaction types', () => {
    const values = common.tx_types.map(t => t.value);
    expect(values).toContain('FLOAT CREDIT');
    expect(values).toContain('FLOAT DEBIT');
    expect(values).toContain('SMS PURCHASE');
  });

  test('balance_type has expected networks', () => {
    const values = common.balance_type.map(b => b.value);
    expect(values).toContain('mtnmm_balance');
    expect(values).toContain('airtelmm_balance');
    expect(values).toContain('safaricom_balance');
  });

  test('account_types includes phone', () => {
    const values = common.account_types.map(a => a.value);
    expect(values).toContain('phone');
  });

  test('each privilege entry has value and text', () => {
    common.privileges.forEach(p => {
      expect(p).toHaveProperty('value');
      expect(p).toHaveProperty('text');
      expect(typeof p.value).toBe('string');
      expect(typeof p.text).toBe('string');
    });
  });
});

// ---------------------------------------------------------------------------
// toReduceGridHeight constant
// ---------------------------------------------------------------------------
describe('toReduceGridHeight', () => {
  test('is a positive number', () => {
    expect(typeof common.toReduceGridHeight).toBe('number');
    expect(common.toReduceGridHeight).toBeGreaterThan(0);
  });
});
