import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './httpClient';

export interface TreasuryAccount {
  id: number;
  channelCode: string;
  environment: string;
  countryCode: string;
  currencyCode: string;
  bookBalance: number;
  reservedBalance: number;
  pendingOutgoingBalance: number;
  pendingIncomingBalance: number;
  availableBalance: number;
  providerReportedBalance?: number | null;
  lowFloatThreshold: number;
  lowFloat: boolean;
  reconciliationState: string;
  reconciliationVariance?: number | null;
}

export interface TreasuryAdjustment {
  id: number;
  adjustmentType: 'CREDIT' | 'DEBIT' | 'REBALANCE';
  sourceAccountId?: number | null;
  destinationAccountId?: number | null;
  amount: number;
  reason: string;
  externalReference: string;
  evidenceReference?: string;
  valueDate: string;
  status: string;
  requestedBy: string;
  approvedBy?: string;
}

export interface SharedProviderEntitlement {
  id: number;
  merchantId: number;
  channelCode: string;
  environment: string;
  countryCode: string;
  currencyCode: string;
  operation: 'COLLECT' | 'PAYOUT';
  status: string;
  perTransactionLimit?: number | null;
  dailyLimit?: number | null;
  requestedBy?: string;
  approvedBy?: string;
}

export interface PlatformCredential {
  id: number;
  channelCode: string;
  environment: string;
  countryCode: string;
  currencyCode: string;
  status: string;
  credentials: Record<string, string>;
  updatedBy?: string;
  approvedBy?: string;
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

export function useTreasuryAccounts() {
  return useQuery({ queryKey: ['provider-treasury', 'accounts'], queryFn: () => request<TreasuryAccount[]>('/api/v2/admin/provider-treasury/accounts') });
}

export function useTreasuryAdjustments() {
  return useQuery({ queryKey: ['provider-treasury', 'adjustments'], queryFn: () => request<TreasuryAdjustment[]>('/api/v2/admin/provider-treasury/adjustments') });
}

export function useSharedProviderEntitlements() {
  return useQuery({ queryKey: ['shared-provider', 'entitlements'], queryFn: () => request<SharedProviderEntitlement[]>('/api/v2/admin/shared-provider/entitlements') });
}

export function usePlatformCredentials() {
  return useQuery({ queryKey: ['shared-provider', 'credentials'], queryFn: () => request<PlatformCredential[]>('/api/v2/admin/shared-provider/credentials') });
}

export function useCreateTreasuryAdjustment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => post<TreasuryAdjustment>('/api/v2/admin/provider-treasury/adjustments', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-treasury'] }),
  });
}

export function useApproveTreasuryAdjustment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => post<TreasuryAdjustment>(`/api/v2/admin/provider-treasury/adjustments/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-treasury'] }),
  });
}

export function useRejectTreasuryAdjustment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => post<TreasuryAdjustment>(`/api/v2/admin/provider-treasury/adjustments/${id}/reject`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-treasury'] }),
  });
}

export function useSetLowFloatThreshold() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, lowFloatThreshold }: { id: number; lowFloatThreshold: number }) => post<TreasuryAccount>(`/api/v2/admin/provider-treasury/accounts/${id}/low-float-threshold`, { lowFloatThreshold }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-treasury', 'accounts'] }),
  });
}

export function useReconcileTreasuryAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: Record<string, unknown> }) => post<TreasuryAccount>(`/api/v2/admin/provider-treasury/accounts/${id}/reconcile`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-treasury'] }),
  });
}

export function useCreateSharedEntitlement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => post<SharedProviderEntitlement>('/api/v2/admin/shared-provider/entitlements', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shared-provider', 'entitlements'] }),
  });
}

export function useApproveSharedEntitlement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => post<SharedProviderEntitlement>(`/api/v2/admin/shared-provider/entitlements/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shared-provider', 'entitlements'] }),
  });
}

export function useRejectSharedEntitlement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => post<SharedProviderEntitlement>(`/api/v2/admin/shared-provider/entitlements/${id}/reject`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shared-provider', 'entitlements'] }),
  });
}

export function useSavePlatformCredential() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => post<PlatformCredential>('/api/v2/admin/shared-provider/credentials', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shared-provider', 'credentials'] }),
  });
}

export function useApprovePlatformCredential() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => post<PlatformCredential>(`/api/v2/admin/shared-provider/credentials/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shared-provider', 'credentials'] }),
  });
}
