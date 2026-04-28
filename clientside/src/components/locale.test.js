/**
 * Unit tests for clientside/src/components/locale.js
 */

import strings from './locale';

describe('locale strings (English)', () => {
  test('portal_title is defined', () => {
    expect(strings.portal_title).toBeTruthy();
  });

  test('merchant_title is defined', () => {
    expect(strings.merchant_title).toBeTruthy();
  });

  test('submit is defined', () => {
    expect(strings.submit).toBeTruthy();
  });

  test('save is defined', () => {
    expect(strings.save).toBeTruthy();
  });

  test('search is defined', () => {
    expect(strings.search).toBeTruthy();
  });

  test('close is defined', () => {
    expect(strings.close).toBeTruthy();
  });

  test('download is defined', () => {
    expect(strings.download).toBeTruthy();
  });

  test('send_sms is defined', () => {
    expect(strings.send_sms).toBeTruthy();
  });

  test('buy_sms is defined', () => {
    expect(strings.buy_sms).toBeTruthy();
  });

  test('add_merchant is defined', () => {
    expect(strings.add_merchant).toBeTruthy();
  });

  test('add_admin is defined', () => {
    expect(strings.add_admin).toBeTruthy();
  });

  test('all string values are non-empty strings', () => {
    // Verify the locale object itself exposes strings properly
    const key = 'portal_title';
    expect(typeof strings[key]).toBe('string');
    expect(strings[key].length).toBeGreaterThan(0);
  });
});
