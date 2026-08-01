import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Section,
  StatGrid,
  StatTile,
  Toolbar,
  Button,
  Badge,
  Table,
  Icons,
} from '../ui';
import Messager from '../components/StableMessager';
import { useAuth } from '../shared/useAuth';
import {
  useDeliveryOpsSummary,
  useOperatingControlsSummary,
  useReadinessSummary,
  useAdminChannels,
  useRunCallbacksMutation,
  useAutoMatchMutation,
} from '../shared/api/hooks';
import type { ReadinessCheck } from '../shared/api/hooks';

/**
 * Operations console (Feature F-1).
 *
 * Replaces the original bare scaffold with the real admin operations surface:
 * adapter channel overview, legacy callback + webhook delivery health (counts
 * by status plus stuck rows), open operating-control events by severity, the
 * platform go-live readiness checklist, and the two operational run-now
 * actions (process due callbacks, auto-match reconciliation rows).
 *
 * All data flows through `src/shared/api/hooks.ts` TanStack Query hooks against
 * the session-authenticated `/api/v2/admin/**` endpoints, so loading/error/
 * empty states and mutation-driven cache invalidation come for free.
 */
type BadgeTone = 'neutral' | 'success' | 'danger' | 'warning' | 'info';

function statusTone(status?: string): BadgeTone {
  if (!status) return 'neutral';
  const upper = status.toUpperCase();
  if (upper === 'DONE' || upper === 'DELIVERED' || upper === 'READY') return 'success';
  if (upper === 'PENDING' || upper === 'RETRY' || upper === 'ACTION_REQUIRED') return 'warning';
  if (upper === 'PARKED' || upper === 'FAILED') return 'danger';
  return 'neutral';
}

interface ChannelRow {
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
}

interface CallbackRow {
  id: number;
  merchantId: number;
  merchantName?: string;
  referenceValue?: string;
  taskStatus?: string;
  attemptCount?: number;
  attemptLimit?: number;
  message?: string;
}

interface WebhookRow {
  id: number;
  merchantId: number;
  merchantName?: string;
  eventType?: string;
  eventReference?: string;
  deliveryStatus?: string;
  lastHttpStatus?: number | null;
  lastResponseSummary?: string;
}

export default function OperationsConsole(): React.ReactElement {
  const navigate = useNavigate();
  const messagerRef = useRef<React.ElementRef<typeof Messager>>(null);
  const { isAuthenticated } = useAuth('admin');

  const deliveryOps = useDeliveryOpsSummary(50);
  const operatingControls = useOperatingControlsSummary();
  const readiness = useReadinessSummary();
  const channels = useAdminChannels();
  const runCallbacks = useRunCallbacksMutation();
  const autoMatch = useAutoMatchMutation();

  const busy =
    deliveryOps.isFetching ||
    operatingControls.isFetching ||
    readiness.isFetching ||
    channels.isFetching ||
    runCallbacks.isPending ||
    autoMatch.isPending;

  // The console is mounted outside the authenticated Layout shell, so guard it
  // like Login does: no stored admin principal -> redirect to the portal login.
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/portal');
    }
  }, [isAuthenticated, navigate]);

  const error =
    (channels.error as Error | null) ||
    (deliveryOps.error as Error | null) ||
    (operatingControls.error as Error | null) ||
    (readiness.error as Error | null);

  useEffect(() => {
    if (!error) return;
    messagerRef.current?.alert({ title: 'Operations Console', icon: 'error', msg: error.message });
  }, [error]);

  const summary = readiness.data ?? {};
  const callbackCounts = deliveryOps.data?.legacyCallbacks?.countsByStatus ?? {};
  const webhookCounts = deliveryOps.data?.webhookDeliveries?.countsByStatus ?? {};
  const parkedCallbacks: CallbackRow[] = deliveryOps.data?.legacyCallbacks?.stuck ?? [];
  const failedWebhooks: WebhookRow[] = deliveryOps.data?.webhookDeliveries?.stuck ?? [];
  const checklist: ReadinessCheck[] = Array.isArray(summary.checklist) ? summary.checklist : [];
  const readyChecks = checklist.filter((check) => check.status === 'READY').length;
  const requiredChecks = checklist.length - readyChecks;

  const channelColumns = [
    { key: 'channelCode', header: 'Channel', render: (row: ChannelRow) => row.displayName || row.channelCode },
    { key: 'countryCode', header: 'Country', accessor: (row: ChannelRow) => row.countryCode },
    { key: 'currencyCode', header: 'Currency', accessor: (row: ChannelRow) => row.currencyCode },
    {
      key: 'capabilities',
      header: 'Capabilities',
      render: (row: ChannelRow) => {
        const parts: string[] = [];
        if (row.collections) parts.push('Collect');
        if (row.payouts) parts.push('Payout');
        if (row.balanceCheck) parts.push('Balance');
        if (row.statusCheck) parts.push('Status');
        if (row.refunds) parts.push('Refund');
        if (row.callbacks) parts.push('Callbacks');
        return parts.join(', ') || '—';
      },
    },
  ];

  const callbackColumns = [
    { key: 'id', header: 'ID', accessor: (row: CallbackRow) => row.id },
    { key: 'merchantName', header: 'Merchant', accessor: (row: CallbackRow) => row.merchantName || `#${row.merchantId}` },
    { key: 'referenceValue', header: 'Reference', accessor: (row: CallbackRow) => row.referenceValue || '—' },
    { key: 'taskStatus', header: 'Status', render: (row: CallbackRow) => <Badge tone={statusTone(row.taskStatus)}>{row.taskStatus}</Badge> },
    { key: 'attemptCount', header: 'Attempts', render: (row: CallbackRow) => `${row.attemptCount ?? 0}/${row.attemptLimit ?? '-'}` },
    { key: 'message', header: 'Message', accessor: (row: CallbackRow) => row.message || '—' },
  ];

  const webhookColumns = [
    { key: 'id', header: 'ID', accessor: (row: WebhookRow) => row.id },
    { key: 'merchantName', header: 'Merchant', accessor: (row: WebhookRow) => row.merchantName || `#${row.merchantId}` },
    { key: 'eventType', header: 'Event', accessor: (row: WebhookRow) => row.eventType || '—' },
    { key: 'eventReference', header: 'Reference', accessor: (row: WebhookRow) => row.eventReference || '—' },
    { key: 'deliveryStatus', header: 'Status', render: (row: WebhookRow) => <Badge tone={statusTone(row.deliveryStatus)}>{row.deliveryStatus}</Badge> },
    { key: 'lastHttpStatus', header: 'HTTP', accessor: (row: WebhookRow) => row.lastHttpStatus ?? '—' },
    { key: 'lastResponseSummary', header: 'Response', accessor: (row: WebhookRow) => row.lastResponseSummary || '—' },
  ];

  return (
    <div className="cpay-ops-console" style={{ padding: 'var(--ios-space-6)' }}>
      <Messager ref={messagerRef} />

      <Toolbar>
        <div>
          <h2 style={{ margin: 0 }}>Operations Console</h2>
          <p style={{ margin: '4px 0 0', color: 'var(--ios-secondary)' }}>
            Payment channels, callbacks, webhooks, operating controls, and readiness at a glance.
          </p>
        </div>
        <Toolbar.Spacer />
        <Button
          variant="primary"
          className="ios-btn--sm"
          disabled={busy}
          onClick={() => runCallbacks.mutate(50, {
            onSuccess: (result) =>
              messagerRef.current?.alert({
                title: 'Callbacks',
                icon: 'info',
                msg: `Processed ${result?.count ?? 0} due callback tasks.`,
              }),
          })}
        >
          <Icons.RefreshIcon size={16} /> Run callbacks
        </Button>
        <Button
          variant="ghost"
          className="ios-btn--sm"
          disabled={busy}
          onClick={() => autoMatch.mutate(undefined, {
            onSuccess: (matched) =>
              messagerRef.current?.alert({
                title: 'Auto-match',
                icon: 'info',
                msg: `Matched ${matched ?? 0} reconciliation records.`,
              }),
          })}
        >
          <Icons.ReconcileIcon size={16} /> Auto-match
        </Button>
      </Toolbar>

      <Section title="Channels">
        <Card flush>
          <Table<ChannelRow>
            columns={channelColumns}
            rows={(channels.data ?? []) as ChannelRow[]}
            rowKey={(row) => row.channelCode ?? 'channel'}
            emptyText="No channels configured."
          />
        </Card>
      </Section>

      <StatGrid>
        <StatTile
          label="Callback Queue"
          value={String(callbackCounts.PENDING ?? 0)}
          delta={`${callbackCounts.RETRY ?? 0} retry · ${callbackCounts.PARKED ?? 0} parked`}
        />
        <StatTile
          label="Webhook Deliveries"
          value={String(webhookCounts.PENDING ?? 0)}
          delta={`${webhookCounts.DELIVERED ?? 0} delivered · ${webhookCounts.FAILED ?? 0} failed`}
        />
        <StatTile
          label="Open Operating Controls"
          value={String(operatingControls.data?.totalOpen ?? 0)}
          delta={`${operatingControls.data?.openHigh ?? 0} high · ${operatingControls.data?.openMedium ?? 0} med · ${operatingControls.data?.openLow ?? 0} low`}
        />
        <StatTile
          label="Readiness"
          value={`${readyChecks}/${checklist.length}`}
          delta={requiredChecks ? `${requiredChecks} action required` : 'All checks ready'}
        />
      </StatGrid>

      <Section title="Stuck Callback Deliveries">
        <Card flush>
          <Table<CallbackRow>
            columns={callbackColumns}
            rows={parkedCallbacks}
            rowKey={(row) => row.id}
            emptyText="No parked callbacks. All clear."
          />
        </Card>
      </Section>

      <Section title="Failed Webhook Deliveries">
        <Card flush>
          <Table<WebhookRow>
            columns={webhookColumns}
            rows={failedWebhooks}
            rowKey={(row) => row.id}
            emptyText="No failed webhook deliveries. All clear."
          />
        </Card>
      </Section>

      <Section title="Go-Live Readiness">
        <Card>
          {checklist.length === 0 ? (
            <p style={{ color: 'var(--ios-secondary)' }}>No readiness checks available.</p>
          ) : (
            <div className="cpay-readiness-list">
              {checklist.map((check, index) => (
                <div
                  className="cpay-readiness-row"
                  key={String(check.id ?? `${check.label}-${index}`)}
                  style={{ display: 'flex', alignItems: 'center', gap: 'var(--ios-space-3)', padding: 'var(--ios-space-3) 0', borderBottom: '1px solid var(--ios-separator)' }}
                >
                  <Badge tone={statusTone(check.status)}>{check.status === 'READY' ? 'READY' : 'ACTION'}</Badge>
                  <div style={{ flex: 1 }}>
                    <strong>{check.label}</strong>
                    {check.action ? <div style={{ color: 'var(--ios-secondary)', fontSize: 'var(--ios-fs-caption)' }}>{check.action}</div> : null}
                  </div>
                  <span style={{ color: 'var(--ios-secondary)' }}>{check.value ?? 0}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </Section>
    </div>
  );
}
