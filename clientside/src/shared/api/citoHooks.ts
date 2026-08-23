import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './httpClient';

export type CitoEnvironment = 'SANDBOX' | 'PRODUCTION';

export interface CitoServiceCatalogRow {
  serviceCode?: string;
  serviceName?: string;
  description?: string;
  [key: string]: unknown;
}

export interface CitoEntitlementRow {
  id?: number;
  merchantId?: number;
  serviceCode?: string;
  serviceName?: string;
  environment?: CitoEnvironment | string;
  status?: string;
  planCode?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface CitoAccessEventRow {
  id?: number | string;
  eventReference?: string;
  eventType?: string;
  action?: string;
  status?: string;
  serviceCode?: string;
  detail?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface UpsertCitoEntitlementPayload {
  merchantId: number;
  serviceCode: string;
  environment: CitoEnvironment;
  status: string;
  planCode: string;
  actor: string;
}

function merchantIdValue(merchantId: string): string {
  return merchantId.trim();
}

export function useCitoServiceCatalog() {
  return useQuery({
    queryKey: ['cito', 'admin', 'service-catalog'],
    queryFn: () => request<CitoServiceCatalogRow[]>('/api/v2/admin/cito/service-catalog'),
  });
}

export function useCitoMerchantEntitlements(merchantId: string) {
  const value = merchantIdValue(merchantId);
  return useQuery({
    queryKey: ['cito', 'admin', 'entitlements', value],
    queryFn: () =>
      request<CitoEntitlementRow[]>(
        `/api/v2/admin/cito/entitlements?merchantId=${encodeURIComponent(value)}`,
      ),
    enabled: Boolean(value),
  });
}

export function useCitoAccessEvents(merchantId: string, limit = 50) {
  const value = merchantIdValue(merchantId);
  return useQuery({
    queryKey: ['cito', 'admin', 'access-events', value, limit],
    queryFn: () =>
      request<CitoAccessEventRow[]>(
        `/api/v2/admin/cito/access-events?merchantId=${encodeURIComponent(value)}&limit=${limit}`,
      ),
    enabled: Boolean(value),
  });
}

export function useSetCitoEntitlementMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpsertCitoEntitlementPayload) =>
      request<unknown>('/api/v2/admin/cito/entitlements', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }),
    onSuccess: (_data, payload) => {
      const merchantId = String(payload.merchantId);
      queryClient.invalidateQueries({ queryKey: ['cito', 'admin', 'entitlements', merchantId] });
      queryClient.invalidateQueries({ queryKey: ['cito', 'admin', 'access-events', merchantId] });
    },
  });
}
