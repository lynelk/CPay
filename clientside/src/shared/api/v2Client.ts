/**
 * Typed client for the v2 admin/merchant gateway endpoints.
 * Backed by the shared cookie-session-aware `httpClient`.
 */
import { request } from './httpClient';

export interface PaymentChannel {
  id?: string;
  name?: string;
  provider?: string;
  [key: string]: unknown;
}

export interface ReconciliationRecord {
  id?: string;
  reference?: string;
  amount?: number;
  status?: string;
  [key: string]: unknown;
}

export interface MerchantBalance {
  merchantNumber?: string;
  balance?: number;
  currency?: string;
  [key: string]: unknown;
}

export const v2Client = {
  channels: () => request<PaymentChannel[]>('/api/v2/admin/gateways/channels'),
  runCallbacks: (limit = 50) =>
    request(`/api/v2/admin/gateways/callbacks/run-due?limit=${limit}`, { method: 'POST' }),
  autoMatch: () =>
    request('/api/v2/admin/gateways/reconciliation/auto-match', { method: 'POST' }),
  unmatched: (limit = 100) =>
    request<ReconciliationRecord[]>(`/api/v2/admin/reconciliation/unmatched?limit=${limit}`),
  reviews: (limit = 100) =>
    request<ReconciliationRecord[]>(`/api/v2/admin/reconciliation/reviews/pending?limit=${limit}`),
  merchantBalances: (merchantNumber: string, headers: Record<string, string> = {}) =>
    request<MerchantBalance[]>(
      `/api/v2/merchant/balances?merchantNumber=${encodeURIComponent(merchantNumber)}`,
      { headers },
    ),
};
