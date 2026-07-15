/**
 * TanStack Query hooks for the v2 endpoints — the reference pattern for moving
 * server state off hand-rolled `fetch` + component state. Legacy modules should
 * migrate their data fetching onto hooks like these one file at a time.
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { v2Client } from './v2Client';

export function useChannels() {
  return useQuery({ queryKey: ['v2', 'channels'], queryFn: v2Client.channels });
}

export function useUnmatched(limit = 100) {
  return useQuery({
    queryKey: ['v2', 'reconciliation', 'unmatched', limit],
    queryFn: () => v2Client.unmatched(limit),
  });
}

export function usePendingReviews(limit = 100) {
  return useQuery({
    queryKey: ['v2', 'reconciliation', 'reviews', limit],
    queryFn: () => v2Client.reviews(limit),
  });
}

export function useMerchantBalances(merchantNumber: string) {
  return useQuery({
    queryKey: ['v2', 'merchant', 'balances', merchantNumber],
    queryFn: () => v2Client.merchantBalances(merchantNumber),
    enabled: Boolean(merchantNumber),
  });
}

export function useAutoMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: v2Client.autoMatch,
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ['v2', 'reconciliation'] }),
  });
}
