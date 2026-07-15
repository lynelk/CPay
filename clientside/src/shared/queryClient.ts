/**
 * Shared TanStack Query client.
 *
 * Server state (channels, balances, reconciliation, etc.) should move onto
 * TanStack Query hooks incrementally. `shared/api/hooks.ts` demonstrates the
 * pattern for the v2 admin endpoints; legacy modules can adopt it file by file.
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
