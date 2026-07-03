const API_BASE = process.env.REACT_APP_API_BASE || '';

async function call(path, options) {
  const response = await window.fetch(`${API_BASE}${path}`, options || {});
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch (e) {
    return text;
  }
}

export const opsClient = {
  summary: () => call('/api/v2/admin/ops-dashboard/summary'),
  callbacks: () => call('/api/v2/admin/gateways/callbacks/run-due?limit=50', { method: 'POST' }),
  sandbox: channel => call(`/api/v2/admin/provider-sandbox/run?channel=${encodeURIComponent(channel)}`, { method: 'POST' }),
  finance: () => call('/api/v2/admin/recon-finance/summary'),
  closeDay: date => call(`/api/v2/admin/recon-finance/close?date=${encodeURIComponent(date)}`, { method: 'POST' }),
  rotateSecret: merchantId => call(`/api/v2/admin/callback-admin/rotate?merchantId=${merchantId}`, { method: 'POST' }),
  retryTask: taskId => call(`/api/v2/admin/callback-admin/retry-task?taskId=${taskId}`, { method: 'POST' })
};
