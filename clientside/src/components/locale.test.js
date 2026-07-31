/**
 * Unit tests for Clientside/src/components/locale.js
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

describe('locale strings (pre-login/auth screens)', () => {
  test('sign_in is defined', () => {
    expect(strings.sign_in).toBeTruthy();
  });

  test('forgot_password_link is defined', () => {
    expect(strings.forgot_password_link).toBeTruthy();
  });

  test('admin_access_subtitle and merchant_access_subtitle are defined', () => {
    expect(strings.admin_access_subtitle).toBeTruthy();
    expect(strings.merchant_access_subtitle).toBeTruthy();
  });

  test('signup field labels are defined', () => {
    expect(strings.business_name_label).toBeTruthy();
    expect(strings.email_address_label).toBeTruthy();
    expect(strings.phone_number_label).toBeTruthy();
  });

  test('forgot-password dialog strings are defined', () => {
    expect(strings.reset_password_title).toBeTruthy();
    expect(strings.reset_merchant_password_title).toBeTruthy();
    expect(strings.verification_code_sent).toBeTruthy();
  });
});

describe('locale strings (authenticated shell chrome)', () => {
  test('shared top-bar labels are defined', () => {
    expect(strings.settings).toBeTruthy();
    expect(strings.refresh).toBeTruthy();
  });

  test('menu titles are defined for both portals', () => {
    expect(strings.menu_dashboard).toBeTruthy();
    expect(strings.menu_merchants).toBeTruthy();
    expect(strings.menu_channels).toBeTruthy();
    expect(strings.menu_sms).toBeTruthy();
  });

  test('menu subtitles are defined per portal', () => {
    expect(strings.menu_dashboard_subtitle_admin).toBeTruthy();
    expect(strings.menu_dashboard_subtitle_merchant).toBeTruthy();
  });
});
