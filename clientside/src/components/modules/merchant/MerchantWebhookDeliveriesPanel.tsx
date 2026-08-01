import React from 'react';
import { Alert, Badge, Button, Spinner, Table, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import {
  useMerchantWebhookDeliveries,
  useReplayWebhookDeliveryMutation,
} from '../../../shared/api/hooks';
import type { MerchantWebhookDelivery } from '../../../shared/api/hooks';
import {
  deliveryStatusTone,
  errorMessage,
  formatDateTime,
  httpStatusLabel,
  isHttp401,
} from './webhookCommon';

interface MerchantWebhookDeliveriesPanelProps {
  sessionExpired?: () => void;
}

/** Audit N6: delivery log with per-attempt detail and replay of failed deliveries. */
function MerchantWebhookDeliveriesPanel({
  sessionExpired,
}: MerchantWebhookDeliveriesPanelProps): React.ReactElement {
  const query = useMerchantWebhookDeliveries(50);
  const replayMutation = useReplayWebhookDeliveryMutation();

  if (query.error && isHttp401(query.error)) {
    sessionExpired?.();
  }
  const error = query.error && !isHttp401(query.error) ? errorMessage(query.error) : '';
  const deliveries = query.data ?? [];

  async function replay(row: MerchantWebhookDelivery) {
    if (row.id == null) return;
    if (!window.confirm(`Replay delivery ${row.id}${row.event_reference ? ` (${row.event_reference})` : ''}?`)) return;
    try {
      await replayMutation.mutateAsync(row.id);
    } catch (replayError) {
      window.alert(errorMessage(replayError));
    }
  }

  const columns: Column<MerchantWebhookDelivery>[] = [
    { key: 'id', header: 'ID', accessor: (r) => r.id, sortable: true, sortValue: (r) => r.id ?? 0, width: 64 },
    { key: 'event_type', header: 'Event', accessor: (r) => r.event_type, sortable: true, sortValue: (r) => r.event_type ?? '' },
    { key: 'event_reference', header: 'Reference', accessor: (r) => r.event_reference },
    { key: 'delivery_status', header: 'Status', render: (r) => <Badge tone={deliveryStatusTone(r.delivery_status)}>{r.delivery_status ?? 'UNKNOWN'}</Badge>, sortable: true, sortValue: (r) => r.delivery_status ?? '' },
    { key: 'attempt_count', header: 'Attempts', accessor: (r) => r.attempt_count ?? 0, sortable: true, sortValue: (r) => r.attempt_count ?? 0 },
    { key: 'last_http_status', header: 'HTTP', render: (r) => <Badge tone={r.last_http_status == null || r.last_http_status === 0 ? 'neutral' : r.last_http_status >= 200 && r.last_http_status < 300 ? 'success' : 'danger'}>{httpStatusLabel(r.last_http_status)}</Badge> },
    { key: 'created_at', header: 'Created', accessor: (r) => formatDateTime(r.created_at), sortable: true, sortValue: (r) => r.created_at ?? '' },
    {
      key: 'actions', header: 'Actions', align: 'center',
      render: (row) => {
        const replayable = row.delivery_status === 'FAILED' || row.delivery_status === 'DELIVERED';
        return (
          <span className="ios-cell-actions">
            <Button
              variant="ghost"
              className="ios-btn--sm"
              disabled={!replayable}
              loading={replayMutation.isPending}
              onClick={() => replay(row)}
              aria-label={`Replay delivery ${row.id ?? ''}`}
            >
              Replay
            </Button>
          </span>
        );
      },
    },
  ];

  return (
    <>
      <Toolbar>
        <span className="ios-channel-subtitle">Most recent first — up to 50 deliveries</span>
      </Toolbar>

      {query.isLoading ? <Spinner label="Loading webhook deliveries" /> : null}
      {!query.isLoading && query.error ? <Alert variant="error">{error}</Alert> : null}

      {!query.isLoading && !query.error ? (
        <Table
          columns={columns}
          rows={deliveries}
          rowKey={(row, i) => row.id ?? `${row.event_reference}-${i}`}
          pageSize={50}
          emptyText="No webhook deliveries yet."
          renderDetail={(row) => (
            <div className="ios-grid">
              <div><strong>Event:</strong> {row.event_type ?? '—'}</div>
              <div><strong>Reference:</strong> {row.event_reference ?? '—'}</div>
              <div><strong>Status:</strong> {row.delivery_status ?? '—'}</div>
              <div><strong>Attempts:</strong> {row.attempt_count ?? 0}</div>
              <div><strong>Last HTTP status:</strong> {httpStatusLabel(row.last_http_status)}</div>
              <div><strong>Next attempt:</strong> {formatDateTime(row.next_attempt_at)}</div>
              <div><strong>Response:</strong> {row.last_response_summary ? <code>{row.last_response_summary}</code> : '—'}</div>
            </div>
          )}
        />
      ) : null}
    </>
  );
}

export default MerchantWebhookDeliveriesPanel;
