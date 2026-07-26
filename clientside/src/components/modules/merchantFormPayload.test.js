import { buildMerchantPayload, emptyMerchantForm } from './merchantFormPayload';

describe('merchant form payload helpers', () => {
  test('normalizes legacy combo-shaped values before submitting a merchant', () => {
    const payload = buildMerchantPayload({
      name: 'Test Merchant',
      short_name: 'TEST',
      status: { value: 'ACTIVE', text: 'ACTIVE' },
      account_type: { value: 'BUSINESS', text: 'BUSINESS' },
      allowed_apis: [
        { value: 'MOBILE_MONEY_PAYIN', text: 'MOBILE MONEY PAYIN' },
        'API_SEND_SMS',
      ],
      generate_new_keys: 1,
      admins: [
        {
          name: 'Admin User',
          email: 'admin@example.com',
          phone: '256700000000',
          status: { value: 'ACTIVE', text: 'ACTIVE' },
          temporary_password: 'StartHere#123',
          privileges: [
            { value: 'ACCESS_SETTINGS', text: 'ACCESS SETTINGS' },
            'CREATE_BATCH_TX',
          ],
        },
      ],
    });

    expect(payload).toMatchObject({
      name: 'Test Merchant',
      short_name: 'TEST',
      status: 'ACTIVE',
      account_type: 'business',
      allowed_apis: ['MOBILE_MONEY_PAYIN', 'API_SEND_SMS'],
      generate_new_keys: true,
      admins: [
        {
          name: 'Admin User',
          email: 'admin@example.com',
          phone: '256700000000',
          status: 'ACTIVE',
          temporary_password: 'StartHere#123',
          privileges: ['ACCESS_SETTINGS', 'CREATE_BATCH_TX'],
          generate_pw: false,
          delete: false,
        },
      ],
    });
  });

  test('creates an empty merchant form with backend-required keys', () => {
    expect(emptyMerchantForm()).toEqual({
      id: '',
      name: '',
      short_name: '',
      status: 'ACTIVE',
      account_type: 'personal',
      allowed_apis: [],
      admins: [],
      generate_password: false,
      generate_new_keys: false,
      private_key: '',
      public_key: '',
    });
  });

  test('keeps owner-style admin payloads explicit for backend saves', () => {
    const payload = buildMerchantPayload({
      name: 'Acme Merchant',
      short_name: 'ACME',
      account_type: 'BUSINESS',
      allowed_apis: [],
      admins: [
        {
          role: 'Owner',
          name: 'Owner Admin',
          email: 'owner@example.com',
          phone: '0700000000',
          status: 'ACTIVE',
          temporary_password: '',
          privileges: ['ACCESS_ADMIN', 'UPDATE_SETTINGS'],
        },
      ],
    });

    expect(payload.account_type).toBe('business');
    expect(payload.admins[0]).toMatchObject({
      role: 'Owner',
      temporary_password: '',
      privileges: ['ACCESS_ADMIN', 'UPDATE_SETTINGS'],
      generate_pw: false,
      delete: false,
    });
  });
});
