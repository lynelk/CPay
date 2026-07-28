/**
 * Shared TanStack Query client.
 *
 * Server state (channels, balances, reconciliation, etc.) should move onto
 * TanStack Query incrementally as modules adopt shared data-fetching helpers.
 */
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
