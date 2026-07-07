import { buildMerchantPayload, emptyMerchantForm } from './merchantFormPayload';

describe('merchant form payload helpers', () => {
  test('normalizes rc-easyui combo values before submitting a merchant', () => {
    const payload = buildMerchantPayload({
      name: 'Test Merchant',
      short_name: 'TEST',
      status: { value: 'ACTIVE', text: 'ACTIVE' },
      account_type: { value: 'business', text: 'BUSINESS' },
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
});
