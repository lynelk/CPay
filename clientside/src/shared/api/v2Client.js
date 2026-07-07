const API_BASE = process.env.REACT_APP_API_BASE || '';

function build(path) {
  return `${API_BASE}${path}`;
}

async function request(path, options) {
  const response = await window.fetch(build(path), options || {});
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error((data && data.message) || response.statusText);
  }
  return data;
}

export const v2Client = {
  channels: () => request('/api/v2/admin/gateways/channels'),
  runCallbacks: (limit = 50) => request(`/api/v2/admin/gateways/callbacks/run-due?limit=${limit}`, { method: 'POST' }),
  autoMatch: () => request('/api/v2/admin/gateways/reconciliation/auto-match', { method: 'POST' }),
  unmatched: (limit = 100) => request(`/api/v2/admin/reconciliation/unmatched?limit=${limit}`),
  reviews: (limit = 100) => request(`/api/v2/admin/reconciliation/reviews/pending?limit=${limit}`),
  merchantBalances: (merchantNumber, headers = {}) => request(`/api/v2/merchant/balances?merchantNumber=${encodeURIComponent(merchantNumber)}`, { headers })
};
