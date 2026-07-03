const API_BASE = process.env.REACT_APP_API_BASE || '';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = data && data.message ? data.message : response.statusText;
    throw new Error(message);
  }

  return data;
}

export function getGatewayChannels() {
  return request('/api/v2/admin/gateways/channels');
}

export function processDueWebhooks(limit = 50) {
  return request(`/api/v2/admin/gateways/webhooks/process-due?limit=${limit}`, { method: 'POST' });
}

export function autoMatchReconciliation() {
  return request('/api/v2/admin/gateways/reconciliation/auto-match', { method: 'POST' });
}

export function getUnmatchedReconciliation(limit = 100) {
  return request(`/api/v2/admin/reconciliation/unmatched?limit=${limit}`);
}

export function getMerchantChannelBalances(merchantNumber, headers = {}) {
  return request(`/api/v2/merchant/balances?merchantNumber=${encodeURIComponent(merchantNumber)}`, { headers });
}
