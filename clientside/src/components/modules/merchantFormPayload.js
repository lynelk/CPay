export function normalizeComboValue(value, fallback = '') {
  if (value && typeof value === 'object' && Object.prototype.hasOwnProperty.call(value, 'value')) {
    return value.value ?? fallback;
  }
  return value ?? fallback;
}

export function normalizeComboArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .map(item => normalizeComboValue(item, ''))
    .filter(item => item !== '');
}

export function normalizeAccountType(value) {
  const normalized = String(normalizeComboValue(value, 'personal')).trim().toLowerCase();
  return normalized === 'business' || normalized === 'personal' ? normalized : 'personal';
}

export function emptyMerchantForm() {
  return {
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
  };
}

export function buildMerchantPayload(formd = {}) {
  return {
    ...formd,
    name: formd.name ?? '',
    short_name: formd.short_name ?? '',
    status: normalizeComboValue(formd.status, 'ACTIVE'),
    account_type: normalizeAccountType(formd.account_type),
    allowed_apis: normalizeComboArray(formd.allowed_apis),
    generate_password: Boolean(formd.generate_password),
    generate_new_keys: Boolean(formd.generate_new_keys),
    admins: Array.isArray(formd.admins)
      ? formd.admins.map(admin => ({
          ...admin,
          name: admin.name ?? '',
          email: admin.email ?? '',
          phone: admin.phone ?? '',
          status: normalizeComboValue(admin.status, 'ACTIVE'),
          temporary_password: admin.temporary_password ?? '',
          privileges: normalizeComboArray(admin.privileges),
          generate_pw: Boolean(admin.generate_pw),
          delete: Boolean(admin.delete),
          id: admin.id ?? '',
        }))
      : [],
  };
}
