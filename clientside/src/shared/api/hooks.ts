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
        `/api/v2/admin/webhooks/endpoints/${endpointId}/rotate-secret`,
        { method: 'POST' },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-webhooks'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Compliance ops (audit P7) — summary, screening events, cases, profiles
//
// Backed by ComplianceReportingController (/api/v2/admin/compliance/**) and
// KycController (/api/v2/admin/kyc/**). These unlock the AML/KYC backends for
// compliance officers: risk-case counts, screening-hit review, case decisions,
// profile status, and the KYB owner/document review workbench.
// ---------------------------------------------------------------------------

export interface ComplianceSummary {
  openControlEvents?: number;
  highSeverityControlEvents?: number;
  providerEndpointRuns?: number;
  failedProviderEndpointRuns?: number;
  parkedCallbacks?: number;
  pendingMerchantChannels?: number;