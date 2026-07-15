import { replaceSmsColumnToken } from './smsTemplate';

describe('replaceSmsColumnToken', () => {
  test('replaces the first matching SMS column placeholder case-insensitively', () => {
    expect(replaceSmsColumnToken('Hello {colb}, ref {COLB}', 'COLB', 'Asha')).toBe('Hello Asha, ref {COLB}');
  });

  test('leaves content unchanged when the placeholder is absent', () => {
    expect(replaceSmsColumnToken('Hello customer', 'COLC', '123')).toBe('Hello customer');
  });

  test('treats replacement values as literal text instead of regex replacement syntax', () => {
    expect(replaceSmsColumnToken('Amount {COLD}', 'COLD', '$12')).toBe('Amount $12');
  });
});
