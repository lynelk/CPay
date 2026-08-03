import React, { useEffect, useState } from 'react';
import {
  Card,
  Section,
  Toolbar,
  Table,
  Badge,
  Alert,
  Spinner,
  TextField,
  Select,
  Button,
} from '../../ui';
import type { Column } from '../../ui';
import {
  useAdminWebhookEndpoints,
  useAdminTestCallbackMutation,
  useAdminRotateWebhookSecretMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { AdminWebhookEndpoint } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit N7: admin webhook verification surface. Backend
 * (`MerchantWebhookController` under `/api/v2/admin/webhooks/**`) lists a
 * merchant's endpoints, rotates secrets, and queues a synthetic TEST event so
 * a callback URL can be verified before production activation; this is the
 * missing admin screen — previously verification required calling the API
 * directly, and ops could not see at a glance which merchants had callbacks
 * configured.
 */

const EVENT_TYPES = [
  { value: 'payment.completed', label: 'payment.completed' },
  { value: 'payment.failed', label: 'payment.failed' },
  { value: 'payout.completed', label: 'payout.completed' },
  { value: 'payout.failed', label: 'payout.failed' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function formatDate(value?: string): string {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

interface ModuleWebhookOpsProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleWebhookOps({ loader, refreshSignal, sessionExpired }: ModuleWebhookOpsProps): React.ReactElement {
  const [merchantIdInput, setMerchantIdInput] = useState('');
  const [merchantId, setMerchantId] = useState<number | undefined>(undefined);
  const [eventType, setEventType] = useState('payment.completed');
  const [actor, setActor] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const endpointsQuery = useAdminWebhookEndpoints(merchantId);
  const testCallbackMutation = useAdminTestCallbackMutation();
  const rotateMutation = useAdminRotateWebhookSecretMutation();

  useLoaderSync(
    loader,
    endpointsQuery.isFetching || testCallbackMutation.isPending || rotateMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [endpointsQuery.refetch]);

  useEffect(() => {
    if (endpointsQuery.error instanceof ApiError && endpointsQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [endpointsQuery.error]);

  const endpoints = endpointsQuery.data ?? [];

  function handleLoad() {
    const parsed = Number(merchantIdInput.trim());
    if (!Number.isFinite(parsed) || parsed <= 0) {
      setFeedback({ tone: 'error', message: 'Enter a valid merchant ID.' });
      return;
    }
    setFeedback(null);
    setMerchantId(parsed);
  }

  function handleTestCallback() {
    if (!merchantId) return;
    setFeedback(null);
    testCallbackMutation.mutate(
      { merchantId, eventType, actor: actor.trim() || undefined },
      {
        onSuccess: (result) => {
          setFeedback({
            tone: 'success',
            message:
              result.message ??
              (result.queued ? `Queued ${result.queued} test event(s) — watch the delivery log.` : 'No active endpoint for this event type.'),
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleRotate(endpointId: number) {
    setFeedback(null);
    rotateMutation.mutate(endpointId, {
      onSuccess: (result) => {
        setFeedback({
          tone: 'success',
          message: `Secret rotated for endpoint ${endpointId}. New secret: ${result.secret ?? '(unspecified)'} — copy it now, it will not be shown again.`,
        });
      },
      onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
    });
  }

  const columns: Column<AdminWebhookEndpoint>[] = [
    { key: 'id', header: 'Endpoint ID', accessor: (r) => String(r.id ?? '') },
    { key: 'event_type', header: 'Event type', accessor: (r) => r.event_type ?? '' },
    { key: 'endpoint_url', header: 'Callback URL', accessor: (r) => r.endpoint_url ?? '' },
    {
      key: 'endpoint_status',
      header: 'Status',
      render: (r) => (
        <Badge tone={r.endpoint_status === 'ACTIVE' ? 'success' : 'neutral'}>{r.endpoint_status ?? 'UNKNOWN'}</Badge>
      ),
    },
    {
      key: 'created_at',
      header: 'Created',
      accessor: (r) => formatDate(r.created_at),
      sortable: true,
      sortValue: (r) => r.created_at || '',
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r) => (
        <Button variant="ghost" className="ios-btn--sm" onClick={() => r.id && handleRotate(r.id)}>
          Rotate secret
        </Button>
      ),
    },
  ];

  return (
    <div className="cpay-webhook-ops">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Webhook verification</strong>
            <Toolbar.Spacer />
            <div style={{ minWidth: 180, display: 'flex', gap: 'var(--ios-space-2)', alignItems: 'flex-end' }}>
              <TextField
                id="wo-merchant-id"
                label="Merchant ID"
                value={merchantIdInput}
                onValueChange={setMerchantIdInput}
                placeholder="e.g. 7"
              />
              <Button variant="secondary" onClick={handleLoad}>
                Load endpoints
              </Button>
            </div>
          </Toolbar>
        </div>
      </Card>

      {merchantId ? (
        <>
          {endpointsQuery.isLoading ? <Spinner label="Loading webhook endpoints" /> : null}
          {endpointsQuery.error ? <Alert variant="error">{errorMessage(endpointsQuery.error)}</Alert> : null}

          <Section title={`Endpoints for merchant ${merchantId}`}>
            {!endpointsQuery.isLoading && !endpointsQuery.error && endpoints.length === 0 ? (
              <p>This merchant has no webhook endpoints configured.</p>
            ) : null}
            {!endpointsQuery.isLoading && !endpointsQuery.error ? (
              <Table
                columns={columns}
                rows={endpoints}
                rowKey={(r) => r.id ?? 0}
                pageSize={20}
                emptyText="No webhook endpoints."
              />
            ) : null}
          </Section>

          <Card>
            <Toolbar>
              <strong>Send a test callback</strong>
              <div style={{ minWidth: 180 }}>
                <Select id="wo-event-type" value={eventType} options={EVENT_TYPES} onValueChange={setEventType} />
              </div>
              <TextField id="wo-actor" label="" value={actor} onValueChange={setActor} placeholder="Actor (optional)" />
              <Button
                variant="primary"
                loading={testCallbackMutation.isPending}
                loadingLabel="Sending…"
                onClick={handleTestCallback}
              >
                Queue test event
              </Button>
            </Toolbar>
            <p style={{ marginTop: 'var(--ios-space-3)' }}>
              A synthetic <strong>TEST</strong> event (amount 0, status TEST) is queued to the merchant's active
              endpoint(s) of this type. Watch the delivery log for the result.
            </p>
          </Card>
        </>
      ) : (
        <p>Enter a merchant ID above to load its webhook endpoints and verify callbacks before go-live.</p>
      )}
    </div>
  );
}

export default ModuleWebhookOps;
