/**
 * TanStack Query hooks for server state.
 *
 * Historically every admin/merchant module hand-rolled its own
 * `fetch` + `useState` + `useEffect` data fetching (see MIGRATION.md "Other
 * known follow-ups" and `src/features/NOTES.md`). This file is the shared
 * home for query/mutation hooks so migrated modules get caching, retries,
 * de-duped in-flight requests, and consistent loading/error state for free
 * instead of each reimplementing it.
 *
 * Two API "shapes" are in play:
 *  - v2 endpoints (`/api/v2/**`) return plain JSON and use HTTP status codes
 *    for errors — these go through `request()` from `./httpClient` directly.
 *  - legacy endpoints (`/transactions/*`, `/auth/*`, etc.) always return
 *    HTTP 200 with a `{ code, message, data|chartData|total, error }`
 *    envelope, where `code !== "000"` is the real error signal, `"107"`
 *    means "not logged in / session expired", and `"110"` means "privilege
 *    check failed". `postLegacyJson` below unwraps that envelope and throws
 *    typed errors so callers can react the same way the old
 *    `if (res.code === "107") { this.sessionExpired(); }` checks did.
 *
 * When migrating a new module: prefer adding a small, purpose-named hook
 * here (e.g. `useAdminTransactions`) over calling `apiFetch`/`request`
 * straight from a component — that keeps query keys and the legacy-envelope
 * unwrapping in one place.
 */
import { useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, request } from './httpClient';

/** Thrown when a legacy endpoint responds with code "107" (not logged in / session expired). */
export class SessionExpiredError extends Error {
  constructor(message = 'Your session expired.') {
    super(message);
    this.name = 'SessionExpiredError';
  }
}

/** Thrown when a legacy endpoint responds with code "110" (privilege/access check failed). */
export class AccessDeniedError extends Error {
  constructor(message = 'You are not allowed access to this section.') {
    super(message);
    this.name = 'AccessDeniedError';
  }
}

/**
 * Thrown by `postLegacyJson` for any non-"000" code that isn't "107"/"110"
 * (including a missing/`undefined` code, e.g. a raw Spring error body coming
 * back from a legacy endpoint that normally always answers 200). Carries the
 * original `code` so callers can rebuild the exact `"Error " + code` style
 * titles the old hand-rolled `fetch` call sites rendered.
 */
export class LegacyRequestError extends Error {
  readonly code?: string;
  constructor(message: string, code?: string) {
    super(message);
    this.name = 'LegacyRequestError';
    this.code = code;
  }
}

interface LegacyEnvelope<T> {
  code?: string;
  message?: string;
  error?: string;
  status?: number;
  data?: T;
  chartData?: T;
  total?: number;
  [key: string]: unknown;
}

/**
 * POST helper for legacy `/transactions/*`-style JSON-envelope endpoints.
 * Preserves the exact request shape the hand-rolled `fetch` calls used
 * (`mode: 'cors'`, `credentials: 'include'`, etc.) so backend behavior is
 * unaffected. Throws `SessionExpiredError` / `AccessDeniedError` for codes
 * `"107"`/`"110"`; any other code that isn't exactly `"000"` — including a
 * missing `code` — throws `LegacyRequestError`, mirroring every hand-rolled
 * call site's `if (res.code === "000") { success } else { error }` branching
 * (a codeless/raw-error response body was always treated as a failure, never
 * as an implicit success).
 */
async function postLegacyJson<T = unknown>(path: string, body: unknown): Promise<LegacyEnvelope<T>> {
  const response = await apiFetch(path, {
    method: 'POST',
    mode: 'cors',
    cache: 'no-cache',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    redirect: 'follow',
    referrer: 'no-referrer',
    body: JSON.stringify(body ?? {}),
  });
  const text = await response.text();
  let parsed: LegacyEnvelope<T>;
  try {
    parsed = text ? JSON.parse(text) : {};
  } catch {
    throw new Error('The server returned an unreadable response.');
  }
  if (parsed.code === '107') throw new SessionExpiredError(parsed.message);
  if (parsed.code === '110') throw new AccessDeniedError(parsed.message);
  if (parsed.code !== '000') {
    const message =
      parsed.message || parsed.error || (parsed.code ? `Request failed with code ${parsed.code}` : 'Request failed.');
    throw new LegacyRequestError(message, parsed.code);
  }
  return parsed;
}

/**
 * Keep a boolean "network busy" flag in sync with the legacy
 * `loader('START' | 'STOP')` prop that `Layout`/`LayoutMerchant` pass down to
 * drive the shared `<Progress>` overlay. Pass `query.isFetching`,
 * `mutation.isPending`, or an OR of several.
 */
export function useLoaderSync(loader: ((op: 'START' | 'STOP') => void) | undefined, busy: boolean): void {
  useEffect(() => {
    loader?.(busy ? 'START' : 'STOP');
  }, [loader, busy]);
}

/**
 * Re-run `refetchers` whenever `refreshSignal` changes. `Layout`/
 * `LayoutMerchant`'s "Refresh" button bumps a counter prop and re-renders the
 * active module; this is the TanStack Query analogue of the old
 * `componentDidUpdate(prevProps) { if (prevProps.refreshSignal !== this.props.refreshSignal) … }`
 * check, without forcing a full remount/refetch on every render.
 */
export function useRefreshSignal(refreshSignal: unknown, refetchers: Array<() => unknown>): void {
  const prev = useRef(refreshSignal);
  useEffect(() => {
    if (prev.current !== refreshSignal) {
      prev.current = refreshSignal;
      refetchers.forEach((refetch) => refetch());
    }
    // Intentionally only re-run when refreshSignal changes; `refetchers` is a
    // fresh array each render but we only want to act on the signal bump.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSignal]);
}

/** First error across a set of query/mutation results, in argument order. */
export function firstQueryError(...results: Array<{ error: unknown }>): unknown {
  for (const result of results) {
    if (result.error) return result.error;
  }
  return null;
}

// ---------------------------------------------------------------------------
// Portal dashboard summary (shared by the admin and merchant dashboards)
// ---------------------------------------------------------------------------

export interface PortalChannelSummary {
  channel_code?: string;
  display_name?: string;
  status?: string;
  environment?: string;
  [key: string]: unknown;
}

export interface PortalProductionLimit {
  enabled?: boolean;
  limit?: number;
  usedToday?: number;
  remainingToday?: number;
}

export interface PortalDashboardSummary {
  payIns?: number;
  payOuts?: number;
  transactions?: number;
  merchants?: number;
  environment?: string;
  activeChannels?: PortalChannelSummary[];
  productionLimit?: PortalProductionLimit;
  [key: string]: unknown;
}

export function usePortalDashboardSummary() {
  return useQuery({
    queryKey: ['portal', 'dashboard-summary'],
    queryFn: () => request<PortalDashboardSummary>('/api/v2/portal/dashboard/summary'),
  });
}

// ---------------------------------------------------------------------------
// Dashboard trend charts (admin + merchant)
// ---------------------------------------------------------------------------

function useDashboardChartQuery(key: string, endpoint: string, refetchInterval?: number) {
  return useQuery({
    queryKey: ['dashboard-chart', key],
    queryFn: async () => {
      const res = await postLegacyJson(endpoint, { sort: 'asc' });
      return res.chartData ?? null;
    },
    refetchInterval,
  });
}

/** The four chart series behind the admin operations dashboard. */
export function useAdminDashboardCharts() {
  return {
    payinsVsPayouts: useDashboardChartQuery('admin-payins-vs-payouts', '/transactions/getDashboardDetailsPayinsVsPayouts'),
    txTypes: useDashboardChartQuery('admin-tx-types', '/transactions/getDashboardDetailsTransactionTypes'),
    txVolumes: useDashboardChartQuery('admin-tx-volumes', '/transactions/getDashboardDetailsTxVolumes'),
    // Network balances refresh every 4 minutes, mirroring the old setInterval.
    networkBalances: useDashboardChartQuery('admin-network-balances', '/transactions/getDashboardDetailsNetworkBalances', 240_000),
  };
}

/** The four chart series behind the merchant dashboard. */
export function useMerchantDashboardCharts() {
  return {
    payinsVsPayouts: useDashboardChartQuery('merchant-payins-vs-payouts', '/transactions/getDashboardDetailsPayinsVsPayoutsMerchant'),
    txTypes: useDashboardChartQuery('merchant-tx-types', '/transactions/getDashboardDetailsTransactionTypesMerchant'),
    txVolumes: useDashboardChartQuery('merchant-tx-volumes', '/transactions/getDashboardDetailsTxVolumesMerchant'),
    txPerGateway: useDashboardChartQuery('merchant-tx-per-gateway', '/transactions/getDashboardDetailsTxPerGatewayMerchant'),
  };
}

// ---------------------------------------------------------------------------
// Transactions (admin)
// ---------------------------------------------------------------------------

export interface TransactionSearch {
  value: string;
  category: string;
}

export interface TransactionRow {
  id?: number | string;
  created_on?: string;
  merchant_name?: string;
  merchant_number?: string;
  gateway_id?: string;
  payer_number?: string;
  tx_merchant_ref?: string;
  tx_gateway_ref?: string;
  status?: string;
  tx_type?: string;
  original_amount?: number | string;
  original_amount_formatted?: string;
  charges_formatted?: string;
  tx_merchant_description?: string;
  tx_description?: string;
  tx_request_trace?: string;
  tx_update_trace?: string;
  callback_trace?: string;
  selected?: boolean;
  [key: string]: unknown;
}

export interface TransactionsResult {
  rows: TransactionRow[];
  total: number;
}

/** Admin `/transactions/getTransactions` list, paged/filtered client-side same as before. */
export function useAdminTransactions(searchingValue: TransactionSearch, pageSize: number, enabled: boolean) {
  return useQuery({
    queryKey: ['transactions', 'admin', searchingValue, pageSize],
    queryFn: async (): Promise<TransactionsResult> => {
      const res = await postLegacyJson<TransactionRow[]>('/transactions/getTransactions', {
        pageSize,
        searchingValue,
        sort: 'asc',
      });
      const rows = res.data ?? [];
      return { rows, total: rows.length };
    },
    enabled,
  });
}

export interface ResolveTransactionPayload {
  id: number | string;
  tx_gateway_ref?: string;
  resolve_status?: string;
  [key: string]: unknown;
}

export function useResolveTransactionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ResolveTransactionPayload) => postLegacyJson('/transactions/resolveTransaction', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions', 'admin'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Transactions (merchant)
// ---------------------------------------------------------------------------

export interface MerchantTransactionSearchRules {
  start_date?: string;
  end_date?: string;
  status?: string;
  tx_type?: string;
  [key: string]: unknown;
}

/** Merchant `/transactions/getMerchantTransactions` list. */
export function useMerchantTransactions(
  searchingValue: TransactionSearch,
  searchRules: MerchantTransactionSearchRules,
  pageSize: number,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['transactions', 'merchant', searchingValue, searchRules, pageSize],
    queryFn: async (): Promise<TransactionsResult> => {
      const res = await postLegacyJson<TransactionRow[]>('/transactions/getMerchantTransactions', {
        search_rules: searchRules,
        pageSize,
        searchingValue,
        sort: 'asc',
      });
      const rows = res.data ?? [];
      return { rows, total: typeof res.total === 'number' ? res.total : rows.length };
    },
    enabled,
  });
}

export interface AddPayInPayload {
  account: string;
  tx_description: string;
  amount: string | number;
  [key: string]: unknown;
}

export function useAddPayInTransactionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AddPayInPayload) => postLegacyJson('/transactions/addPayInTransaction', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions', 'merchant'] });
    },
  });
}
