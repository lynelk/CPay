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
import { ApiError, apiFetch, request } from './httpClient';

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

// ---------------------------------------------------------------------------
// Reconciliation manual-match workbench (audit O2)
// ---------------------------------------------------------------------------
//
// Backed entirely by `/api/v2/admin/reconciliation/**` (`ReconController`), so
// these go through `request()` directly like the rest of the v2 surface — no
// legacy envelope here. One wrinkle: `POST /manual-match` (like a couple of
// other admin write endpoints in this controller family) returns a bare
// `String` body ("updated"), which is *not* valid JSON on its own — `request()`
// would throw trying to `JSON.parse` it. `postForPlainText` below is a small
// variant that reads the body as text instead, used only for that endpoint.

export interface ReconciliationRecord {
  id: number;
  providerCode?: string;
  channelCode?: string;
  providerReference?: string;
  merchantReference?: string;
  transactionId?: string;
  amount?: number;
  currency?: string;
  matchStatus?: string;
  matchReason?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface CandidateTransaction {
  id: number;
  txUniqueId?: string;
  txMerchantRef?: string;
  txGatewayRef?: string;
  merchantId?: number;
  originalAmount?: number;
  currency?: string;
  status?: string;
  txType?: string;
  createdOn?: string;
  payerNumber?: string;
  [key: string]: unknown;
}

/** POST helper for admin endpoints whose success body is plain text, not JSON. */
async function postForPlainText(path: string): Promise<string> {
  const response = await apiFetch(path, { method: 'POST' });
  const text = await response.text();
  if (!response.ok) {
    let message = text || response.statusText;
    try {
      const parsed = JSON.parse(text) as { message?: string; error?: string };
      message = parsed.message || parsed.error || message;
    } catch {
      // Error body wasn't JSON either; fall back to the raw text/statusText above.
    }
    throw new ApiError(message, response.status);
  }
  return text;
}

/** Unmatched provider statement rows for the workbench's left-hand panel. */
export function useUnmatchedReconciliationRecords(limit = 100) {
  return useQuery({
    queryKey: ['reconciliation', 'unmatched', limit],
    queryFn: () =>
      request<ReconciliationRecord[]>(`/api/v2/admin/reconciliation/unmatched?limit=${limit}`),
  });
}

export interface CandidateTransactionSearch {
  reference?: string;
  amount?: string;
  currency?: string;
  from?: string;
  to?: string;
}

/** Whether `search` has enough to run a candidate-transaction query (mirrors the backend guard). */
export function hasAnyCandidateFilter(search: CandidateTransactionSearch): boolean {
  return Boolean(
    search.reference?.trim() || search.amount?.trim() || search.from?.trim() || search.to?.trim(),
  );
}

/**
 * Candidate CPay transactions for the workbench's right-hand search panel.
 * Mirrors the backend's own guard (`ReconService.candidateTransactions`):
 * disabled until at least one filter is supplied, so an empty search box
 * never triggers a query.
 */
export function useCandidateTransactions(search: CandidateTransactionSearch, limit = 25) {
  const enabled = hasAnyCandidateFilter(search);
  return useQuery({
    queryKey: ['reconciliation', 'candidate-transactions', search, limit],
    queryFn: () => {
      const params = new URLSearchParams();
      if (search.reference?.trim()) params.set('reference', search.reference.trim());
      if (search.amount?.trim()) params.set('amount', search.amount.trim());
      if (search.currency?.trim()) params.set('currency', search.currency.trim());
      if (search.from?.trim()) params.set('from', search.from.trim());
      if (search.to?.trim()) params.set('to', search.to.trim());
      params.set('limit', String(limit));
      return request<CandidateTransaction[]>(
        `/api/v2/admin/reconciliation/candidate-transactions?${params.toString()}`,
      );
    },
    enabled,
  });
}

export function useAutoMatchMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => request<number>('/api/v2/admin/reconciliation/auto-match', { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reconciliation', 'unmatched'] });
    },
  });
}

export interface ManualMatchPayload {
  recordId: number;
  transactionId: string;
  reason?: string;
}

export function useManualMatchMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ recordId, transactionId, reason }: ManualMatchPayload) => {
      const params = new URLSearchParams({ recordId: String(recordId), transactionId });
      if (reason?.trim()) params.set('reason', reason.trim());
      return postForPlainText(`/api/v2/admin/reconciliation/manual-match?${params.toString()}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reconciliation', 'unmatched'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Audit trail (admin + merchant)
// ---------------------------------------------------------------------------

export interface AuditTrailRow {
  id?: number | string;
  created_on?: string;
  user_id?: string | number;
  user_name?: string;
  action?: string;
  [key: string]: unknown;
}

function useAuditTrailQuery(key: string, endpoint: string, search: TransactionSearch, pageSize: number) {
  return useQuery({
    queryKey: ['audit-trail', key, search, pageSize],
    queryFn: async (): Promise<AuditTrailRow[]> => {
      const res = await postLegacyJson<AuditTrailRow[]>(endpoint, {
        pageSize,
        searchingValue: search,
        sort: 'asc',
      });
      return res.data ?? [];
    },
    staleTime: 30_000,
  });
}

/** Admin `/audittrail/getAudittrails` list backing `ModuleAuditTrail`. */
export function useAdminAuditTrail(search: TransactionSearch, pageSize = 50) {
  return useAuditTrailQuery('admin', '/audittrail/getAudittrails', search, pageSize);
}

/** Merchant `/audittrail/getMerchantAudittrails` list backing `MerchantModuleAuditTrail`. */
export function useMerchantAuditTrail(search: TransactionSearch, pageSize = 50) {
  return useAuditTrailQuery('merchant', '/audittrail/getMerchantAudittrails', search, pageSize);
}

// ---------------------------------------------------------------------------
// Merchant account statement (admin dialog + merchant self-service view)
// ---------------------------------------------------------------------------

export interface MerchantStatementRow {
  id?: number | string;
  created_on?: string;
  narrative?: string;
  description?: string;
  amount?: number | string;
  tx_type?: string;
  balances?: number | string;
  [key: string]: unknown;
}

export interface MerchantStatementSearchRules {
  start_date?: string;
  end_date?: string;
}

export interface MerchantStatementResult {
  rows: MerchantStatementRow[];
  total: number;
  balances: string;
}

/**
 * Admin view of a single merchant's account statement (`ModuleMerchantsAccount`, opened from the
 * merchants list). Mirrors the original hand-rolled fetch's URL choice: a merchant with a resolved
 * `id` uses `/transactions/getMerchantStatement` (server resolves the exact merchant); otherwise it
 * falls back to `/transactions/getMerchantStatementByMerchant` the same way the legacy code did.
 */
export function useAdminMerchantStatement(
  merchantId: number | string | undefined,
  searchRules: MerchantStatementSearchRules,
  pageSize: number,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['merchant-statement', 'admin', merchantId, searchRules, pageSize],
    queryFn: async (): Promise<MerchantStatementResult> => {
      const endpoint = merchantId
        ? '/transactions/getMerchantStatement'
        : '/transactions/getMerchantStatementByMerchant';
      const res = await postLegacyJson<MerchantStatementRow[]>(endpoint, {
        search_rules: {
          start_date: searchRules.start_date || '',
          end_date: searchRules.end_date || '',
        },
        merchant_id: merchantId ?? null,
        pageSize,
        searchingValue: { value: '', category: 'all' },
        sort: 'asc',
      });
      const rows = res.data ?? [];
      return { rows, total: rows.length, balances: String(res.balances ?? '') };
    },
    enabled,
  });
}

/** A merchant portal user's own account statement (`MerchantModuleMerchantsAccount`). */
export function useMerchantOwnStatement(searchRules: MerchantStatementSearchRules, pageSize: number) {
  return useQuery({
    queryKey: ['merchant-statement', 'own', searchRules, pageSize],
    queryFn: async (): Promise<MerchantStatementResult> => {
      const res = await postLegacyJson<MerchantStatementRow[]>('/transactions/getMerchantStatementByMerchant', {
        search_rules: {
          start_date: searchRules.start_date || '',
          end_date: searchRules.end_date || '',
        },
        pageSize,
        searchingValue: { value: '', category: 'all' },
        sort: 'asc',
      });
      const rows = res.data ?? [];
      return { rows, total: typeof res.total === 'number' ? res.total : rows.length, balances: String(res.balances ?? '') };
    },
  });
}

export interface RecordMerchantTransactionPayload {
  merchant_id?: number | string;
  tx_type: string;
  amount: string | number;
  description: string;
  balance_type?: string;
  [key: string]: unknown;
}

/** Admin "record a manual transaction" action on a merchant's account statement. */
export function useRecordMerchantTransactionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RecordMerchantTransactionPayload) =>
      postLegacyJson('/transactions/recordTransaction', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchant-statement', 'admin'] });
    },
  });
}

export interface ImportStatementPayload {
  provider: string;
  importedBy?: string;
  file: File;
}

/** Uploads a provider statement (CSV or XLSX) and triggers an auto-match pass server-side. */
export function useImportStatementMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ provider, importedBy, file }: ImportStatementPayload) => {
      const formData = new FormData();
      formData.append('file', file);
      const params = new URLSearchParams({ provider });
      if (importedBy?.trim()) params.set('importedBy', importedBy.trim());
      return request<number>(`/api/v2/admin/reconciliation/import?${params.toString()}`, {
        method: 'POST',
        body: formData,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reconciliation', 'unmatched'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Operations console (admin) — delivery ops, operating controls, readiness,
// and the legacy run-due / auto-match actions.
//
// All of these live under `/api/v2/admin/**` and are behind the path-based
// `hasRole('ADMIN')` rule in `SecurityConfig` (plus `@PreAuthorize`), so they
// go through `request()` directly like the rest of the admin v2 surface.
// ---------------------------------------------------------------------------

export interface DeliveryOpsSummary {
  legacyCallbacks?: {
    countsByStatus?: Record<string, number>;
    stuck?: Array<{
      id: number;
      merchantId: number;
      merchantName?: string;
      transactionId?: string;
      referenceValue?: string;
      taskStatus?: string;
      attemptCount?: number;
      attemptLimit?: number;
      message?: string;
      nextRunAt?: string | null;
      lastRunAt?: string | null;
    }>;
  };
  webhookDeliveries?: {
    countsByStatus?: Record<string, number>;
    stuck?: Array<{
      id: number;
      merchantId: number;
      merchantName?: string;
      endpointId: number;
      eventType?: string;
      eventReference?: string;
      deliveryStatus?: string;
      attemptCount?: number;
      lastHttpStatus?: number | null;
      lastResponseSummary?: string;
      nextAttemptAt?: string | null;
    }>;
  };
  [key: string]: unknown;
}

/** `/api/v2/admin/delivery-ops/summary` — counts + stuck callback/webhook rows. */
export function useDeliveryOpsSummary(limit = 50) {
  return useQuery({
    queryKey: ['ops', 'delivery', limit],
    queryFn: () => request<DeliveryOpsSummary>(`/api/v2/admin/delivery-ops/summary?limit=${limit}`),
    refetchInterval: 60_000,
  });
}

export interface OperatingControlsSummary {
  openHigh?: number;
  openMedium?: number;
  openLow?: number;
  totalOpen?: number;
  [key: string]: unknown;
}

/** `/api/v2/admin/operating-controls/summary` — open control-event counts by severity. */
export function useOperatingControlsSummary() {
  return useQuery({
    queryKey: ['ops', 'operating-controls'],
    queryFn: () => request<OperatingControlsSummary>('/api/v2/admin/operating-controls/summary'),
    refetchInterval: 120_000,
  });
}

export interface ReadinessCheck {
  id?: string;
  label?: string;
  status?: 'READY' | 'ACTION_REQUIRED' | string;
  value?: number;
  action?: string;
  [key: string]: unknown;
}

export interface ReadinessSummary {
  providerSandboxRuns?: number;
  statementValidationRuns?: number;
  callbackSecrets?: number;
  openAlerts?: number;
  parkedCallbacks?: number;
  dailyCloses?: number;
  adminAuditEvents?: number;
  openComplianceCases?: number;
  approvedProviderEvidence?: number;
  pendingComplianceProfiles?: number;
  checklist?: ReadinessCheck[];
  [key: string]: unknown;
}

/** `/api/v2/admin/readiness/summary` — the platform-wide go-live readiness view. */
export function useReadinessSummary() {
  return useQuery({
    queryKey: ['ops', 'readiness'],
    queryFn: () => request<ReadinessSummary>('/api/v2/admin/readiness/summary'),
    refetchInterval: 300_000,
  });
}

export interface PaymentChannelResponse {
  channelCode?: string;
  displayName?: string;
  countryCode?: string;
  currencyCode?: string;
  collections?: boolean;
  payouts?: boolean;
  balanceCheck?: boolean;
  statusCheck?: boolean;
  refunds?: boolean;
  callbacks?: boolean;
  [key: string]: unknown;
}

/** `/api/v2/admin/gateways/channels` — adapter-backed channel list. */
export function useAdminChannels() {
  return useQuery({
    queryKey: ['ops', 'channels'],
    queryFn: () => request<PaymentChannelResponse[]>('/api/v2/admin/gateways/channels'),
  });
}

export interface RunCallbacksResult {
  count?: number;
  [key: string]: unknown;
}

/** `POST /api/v2/admin/callbacks/run-due` — claims and processes due callback tasks now. */
export function useRunCallbacksMutation() {
  const queryClient = useQueryClient();
  return useMutation<RunCallbacksResult, unknown, number | undefined>({
    mutationFn: (limit) =>
      request<RunCallbacksResult>(
        `/api/v2/admin/callbacks/run-due?limit=${limit ?? 50}`,
        { method: 'POST' },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops', 'delivery'] });
      queryClient.invalidateQueries({ queryKey: ['ops', 'readiness'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Merchant webhook manager (audit N6) — endpoints, deliveries, replay/rotation
//
// All of these live under `/api/v2/merchant-self-service/webhooks` and are
// backed by `MerchantSelfServiceController` (session-scoped to the logged-in
// merchant's own rows), so they go through `request()` directly like the
// rest of the merchant self-service v2 surface.
// ---------------------------------------------------------------------------

export interface MerchantWebhookEndpoint {
  id?: number;
  event_type?: string;
  endpoint_url?: string;
  endpoint_status?: string;
  created_at?: string;
  updated_at?: string;
  [key: string]: unknown;
}

export interface MerchantWebhookDelivery {
  id?: number;
  endpoint_id?: number;
  event_type?: string;
  event_reference?: string;
  delivery_status?: string;
  attempt_count?: number;
  last_http_status?: number | null;
  last_response_summary?: string;
  next_attempt_at?: string;
  created_at?: string;
  updated_at?: string;
  [key: string]: unknown;
}

/** Merchant's registered webhook endpoints (`GET /webhooks`). */
export function useMerchantWebhookEndpoints() {
  return useQuery({
    queryKey: ['merchant-webhooks', 'endpoints'],
    queryFn: () => request<MerchantWebhookEndpoint[]>('/api/v2/merchant-self-service/webhooks'),
    staleTime: 30_000,
  });
}

/** Merchant's webhook delivery log, most recent first (`GET /webhooks/deliveries`). */
export function useMerchantWebhookDeliveries(limit = 50) {
  return useQuery({
    queryKey: ['merchant-webhooks', 'deliveries', limit],
    queryFn: () =>
      request<MerchantWebhookDelivery[]>(
        `/api/v2/merchant-self-service/webhooks/deliveries?limit=${limit}`,
      ),
    refetchInterval: 60_000,
  });
}

export interface RegisterWebhookPayload {
  eventType: string;
  endpointUrl: string;
}

export interface WebhookSecretResult {
  code?: string;
  eventType?: string;
  secret?: string;
  [key: string]: unknown;
}

/** Registers (or updates) an endpoint for one event type (`POST /webhooks`). */
export function useRegisterWebhookMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RegisterWebhookPayload) =>
      request<WebhookSecretResult>('/api/v2/merchant-self-service/webhooks', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchant-webhooks', 'endpoints'] });
    },
  });
}

/** Rotates a webhook secret; the returned secret is shown exactly once (`POST /webhooks/{id}/rotate-secret`). */
export function useRotateWebhookSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (endpointId: number) =>
      request<WebhookSecretResult>(
        `/api/v2/merchant-self-service/webhooks/${endpointId}/rotate-secret`,
        { method: 'POST' },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchant-webhooks', 'endpoints'] });
    },
  });
}

/** Requeues a failed/delivered webhook delivery (`POST /webhooks/deliveries/{id}/replay`). */
export function useReplayWebhookDeliveryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deliveryId: number) =>
      request<{ updated?: number }>(
        `/api/v2/merchant-self-service/webhooks/deliveries/${deliveryId}/replay`,
        { method: 'POST' },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchant-webhooks', 'deliveries'] });
    },
  });
}

// NOTE: POST /api/v2/admin/reconciliation/auto-match is covered by
// `useAutoMatchMutation` above (the endpoint returns a plain number).

// ---------------------------------------------------------------------------
// Maker-checker ops surfaces (audit E6) — finance daily-close, settlement
// batch close, payout approval queue, and admin webhook verification.
//
// Backed by ReconFinanceController (/api/v2/admin/recon-finance/**),
// SettlementOpsController (/api/v2/admin/reconciliation/settlements/**),
// PayoutApprovalController (/api/v2/admin/payout-approvals/**), and
// MerchantWebhookController (/api/v2/admin/webhooks/**). All v2 admin JSON.
// ---------------------------------------------------------------------------

export interface FinanceCloseSummary {
  currency?: string;
  statementsReceived?: number;
  unmatchedRecords?: number;
  parkedCallbacks?: number;
  openControls?: number;
  closeStatus?: string;
  pendingSubmissions?: Array<{
    closeDate?: string;
    currency?: string;
    submittedBy?: string;
    submittedAt?: string;
    status?: string;
  }>;
  [key: string]: unknown;
}

/** `/api/v2/admin/recon-finance/summary` — the finance daily-close picture for a currency. */
export function useFinanceCloseSummary(currency = 'UGX') {
  return useQuery({
    queryKey: ['finance-close', 'summary', currency],
    queryFn: () =>
      request<FinanceCloseSummary>(
        `/api/v2/admin/recon-finance/summary?currency=${encodeURIComponent(currency)}`,
      ),
    refetchInterval: 120_000,
  });
}

export interface FinanceCloseSubmitPayload {
  date: string;
  currency: string;
  submittedBy: string;
}

/** Maker submit — `POST /api/v2/admin/recon-finance/close` (row -> PENDING_APPROVAL). */
export function useFinanceCloseSubmitMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ date, currency, submittedBy }: FinanceCloseSubmitPayload) => {
      const params = new URLSearchParams({ date });
      params.set('currency', currency || 'UGX');
      if (submittedBy?.trim()) params.set('submittedBy', submittedBy.trim());
      return request<number>(`/api/v2/admin/recon-finance/close?${params.toString()}`, {
        method: 'POST',
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-close'] });
    },
  });
}

/** Checker approval — `POST /api/v2/admin/recon-finance/close/approve`. */
export function useFinanceCloseApproveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ date, currency, approvedBy }: { date: string; currency: string; approvedBy: string }) => {
      const params = new URLSearchParams({ date, approvedBy });
      params.set('currency', currency || 'UGX');
      return request<{ code?: string; closeDate?: string; currency?: string; status?: string }>(
        `/api/v2/admin/recon-finance/close/approve?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-close'] });
    },
  });
}

/** Checker rejection — `POST /api/v2/admin/recon-finance/close/reject`. */
export function useFinanceCloseRejectMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      date,
      currency,
      rejectedBy,
      reason,
    }: {
      date: string;
      currency: string;
      rejectedBy: string;
      reason?: string;
    }) => {
      const params = new URLSearchParams({ date, rejectedBy });
      params.set('currency', currency || 'UGX');
      if (reason?.trim()) params.set('reason', reason.trim());
      return request<{ code?: string; closeDate?: string; currency?: string; status?: string }>(
        `/api/v2/admin/recon-finance/close/reject?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-close'] });
    },
  });
}

export interface SettlementCloseResult {
  code?: string;
  reference?: string;
  status?: string;
}

/** Maker submit for a settlement batch close — returns the close row id as plain text ("closed=N"). */
export function useSettlementCloseSubmitMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reference, closedBy }: { reference: string; closedBy: string }) => {
      const params = new URLSearchParams({ reference });
      if (closedBy?.trim()) params.set('closedBy', closedBy.trim());
      return postForPlainText(
        `/api/v2/admin/reconciliation/settlements/close?${params.toString()}`,
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settlement-close'] });
    },
  });
}

/** Checker approval for a submitted settlement batch close. */
export function useSettlementCloseApproveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reference, approvedBy }: { reference: string; approvedBy: string }) => {
      const params = new URLSearchParams({ reference, approvedBy });
      return request<SettlementCloseResult>(
        `/api/v2/admin/reconciliation/settlements/close/approve?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settlement-close'] });
    },
  });
}

/** Checker rejection for a submitted settlement batch close. */
export function useSettlementCloseRejectMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      reference,
      rejectedBy,
      reason,
    }: {
      reference: string;
      rejectedBy: string;
      reason?: string;
    }) => {
      const params = new URLSearchParams({ reference, rejectedBy });
      if (reason?.trim()) params.set('reason', reason.trim());
      return request<SettlementCloseResult>(
        `/api/v2/admin/reconciliation/settlements/close/reject?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settlement-close'] });
    },
  });
}

export interface PayoutApprovalRow {
  id?: number;
  payout_reference?: string;
  merchant_id?: number;
  merchant_number?: string;
  amount?: number | string;
  currency?: string;
  channel_code?: string;
  country?: string;
  beneficiary_reference?: string;
  trigger_reason?: string;
  queue_status?: string;
  requested_by?: string;
  requested_at?: string;
  [key: string]: unknown;
}

export interface ApprovedPayoutResult {
  reference?: string;
  transactionId?: string;
  status?: string;
  message?: string;
  [key: string]: unknown;
}

/** `/api/v2/admin/payout-approvals` — payouts parked by a limit/velocity control awaiting maker-checker. */
export function usePendingPayoutApprovals(limit = 100) {
  return useQuery({
    queryKey: ['payout-approvals', 'pending', limit],
    queryFn: () => request<PayoutApprovalRow[]>(`/api/v2/admin/payout-approvals?limit=${limit}`),
    refetchInterval: 60_000,
  });
}

/** Checker approval — re-executes the stored payout through the normal orchestrator path. */
export function usePayoutApproveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ queueId, approvedBy }: { queueId: number; approvedBy: string }) => {
      const params = new URLSearchParams({ approvedBy });
      return request<ApprovedPayoutResult>(
        `/api/v2/admin/payout-approvals/${queueId}/approve?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payout-approvals'] });
    },
  });
}

/** Checker rejection for a queued payout. */
export function usePayoutRejectMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      queueId,
      rejectedBy,
      reason,
    }: {
      queueId: number;
      rejectedBy: string;
      reason?: string;
    }) => {
      const params = new URLSearchParams({ rejectedBy });
      if (reason?.trim()) params.set('reason', reason.trim());
      return request<{ code?: string; queueId?: number; status?: string }>(
        `/api/v2/admin/payout-approvals/${queueId}/reject?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payout-approvals'] });
    },
  });
}

/** Cancellation for a queued payout before a decision. */
export function usePayoutCancelMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ queueId, cancelledBy }: { queueId: number; cancelledBy: string }) => {
      const params = new URLSearchParams({ cancelledBy });
      return request<{ code?: string; queueId?: number; status?: string }>(
        `/api/v2/admin/payout-approvals/${queueId}/cancel?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payout-approvals'] });
    },
  });
}

export interface AdminWebhookEndpoint {
  id?: number;
  merchant_id?: number;
  event_type?: string;
  endpoint_url?: string;
  endpoint_status?: string;
  created_at?: string;
  updated_at?: string;
  [key: string]: unknown;
}

/** Admin view of a merchant's registered webhook endpoints — `/api/v2/admin/webhooks/merchants/{id}`. */
export function useAdminWebhookEndpoints(merchantId: number | undefined) {
  return useQuery({
    queryKey: ['admin-webhooks', 'endpoints', merchantId ?? 'none'],
    queryFn: () =>
      request<AdminWebhookEndpoint[]>(
        `/api/v2/admin/webhooks/merchants/${merchantId}`,
      ),
    enabled: Boolean(merchantId && merchantId > 0),
    staleTime: 30_000,
  });
}

export interface TestCallbackPayload {
  merchantId: number;
  eventType: string;
  actor?: string;
}

/** Queues a synthetic webhook event so a merchant callback URL can be verified before go-live. */
export function useAdminTestCallbackMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ merchantId, eventType, actor }: TestCallbackPayload) => {
      const params = new URLSearchParams({ eventType });
      if (actor?.trim()) params.set('actor', actor.trim());
      return request<{ code?: string; merchantId?: number; eventType?: string; queued?: number; message?: string }>(
        `/api/v2/admin/webhooks/merchants/${merchantId}/test-callback?${params.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-webhooks'] });
    },
  });
}

/** Admin secret rotation for a merchant webhook endpoint. */
export function useAdminRotateWebhookSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (endpointId: number) =>
      request<{ code?: string; secret?: string }>(
        `/api/v2/admin/webhooks/${endpointId}/rotate-secret`,
        { method: 'POST' },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-webhooks'] });
    },
  });
}
