import React, { useState } from 'react';
import { Alert, Badge, Button, Select, Sheet, Spinner, Table, TextField, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import {
  useMerchantWebhookEndpoints,
  useRegisterWebhookMutation,
  useRotateWebhookSecretMutation,
} from '../../../shared/api/hooks';
import type { MerchantWebhookEndpoint } from '../../../shared/api/hooks';
import {
  WEBHOOK_EVENT_TYPES,
  endpointStatusTone,
  errorMessage,
  formatDateTime,
  isHttp401,
  isValidEndpointUrl,
} from './webhookCommon';

interface MerchantWebhookEndpointsPanelProps {
  sessionExpired?: () => void;
}

/** Audit N6: registered webhook endpoints with register/update and rotate-secret. */
function MerchantWebhookEndpointsPanel({
  sessionExpired,
}: MerchantWebhookEndpointsPanelProps): React.ReactElement {
  const query = useMerchantWebhookEndpoints();
  const registerMutation = useRegisterWebhookMutation();
  const rotateMutation = useRotateWebhookSecretMutation();

  const [open, setOpen] = useState(false);
  const [eventType, setEventType] = useState('payment.pending');
  const [endpointUrl, setEndpointUrl] = useState('');
  const [formError, setFormError] = useState('');
  const [secretMessage, setSecretMessage] = useState('');

  if (query.error && isHttp401(query.error)) {
    sessionExpired?.();
  }
  const error = query.error && !isHttp401(query.error) ? errorMessage(query.error) : '';
  const endpoints = query.data ?? [];

  function openRegister() {
    setEventType('payment.pending');
    setEndpointUrl('');
    setFormError('');
    setSecretMessage('');
    setOpen(true);
  }

  async function submitRegister() {
    if (!isValidEndpointUrl(endpointUrl)) {
      setFormError('Enter a valid https:// endpoint URL.');
      return;
    }
    setFormError('');
    try {
      const result = await registerMutation.mutateAsync({ eventType, endpointUrl });
      setSecretMessage(
        `Endpoint saved. Signing secret: ${result.secret ?? '(none returned)'} — copy it now, it will not be shown again.`,
      );
    } catch (registerError) {
      setFormError(errorMessage(registerError));
    }
  }

  async function rotate(row: MerchantWebhookEndpoint) {
    if (row.id == null) return;
    if (!window.confirm(`Rotate the signing secret for ${row.event_type ?? 'this endpoint'}?`)) return;
    try {
      const result = await rotateMutation.mutateAsync(row.id);
      setSecretMessage(
        `New signing secret: ${result.secret ?? '(none returned)'} — copy it now, it will not be shown again.`,
      );
    } catch (rotateError) {
      setFormError(errorMessage(rotateError));
    }
  }

  const columns: Column<MerchantWebhookEndpoint>[] = [
    { key: 'event_type', header: 'Event', accessor: (r) => r.event_type, sortable: true, sortValue: (r) => r.event_type ?? '' },
    { key: 'endpoint_url', header: 'Endpoint URL', render: (r) => <code>{r.endpoint_url}</code> },
    { key: 'endpoint_status', header: 'Status', render: (r) => <Badge tone={endpointStatusTone(r.endpoint_status)}>{r.endpoint_status ?? 'UNKNOWN'}</Badge>, sortable: true, sortValue: (r) => r.endpoint_status ?? '' },
    { key: 'created_at', header: 'Created', accessor: (r) => formatDateTime(r.created_at), sortable: true, sortValue: (r) => r.created_at ?? '' },
    {
      key: 'actions', header: 'Actions', align: 'center',
      render: (row) => (
        <span className="ios-cell-actions">
          <Button variant="ghost" className="ios-btn--sm" loading={rotateMutation.isPending} onClick={() => rotate(row)}>
            Rotate secret
          </Button>
        </span>
      ),
    },
  ];

  return (
    <>
      <Toolbar>
        <Button variant="primary" className="ios-btn--sm" onClick={openRegister}>
          Register endpoint
        </Button>
        <Toolbar.Spacer />
        <span className="ios-channel-subtitle">{endpoints.length} endpoint(s)</span>
      </Toolbar>

      {query.isLoading ? <Spinner label="Loading webhook endpoints" /> : null}
      {!query.isLoading && query.error ? <Alert variant="error">{error}</Alert> : null}
      {secretMessage ? <Alert variant="success">{secretMessage}</Alert> : null}

      {!query.isLoading && !query.error ? (
        <Table
          columns={columns}
          rows={endpoints}
          rowKey={(row, i) => row.id ?? `${row.event_type}-${i}`}
          pageSize={25}
          emptyText="No webhook endpoints registered. Register one to receive event notifications."
        />
      ) : null}

      <Sheet
        open={open}
        onClose={() => setOpen(false)}
        title="Register webhook endpoint"
        size="sm"
        footer={
          <>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => setOpen(false)}>
              Close
            </Button>
            <Button variant="primary" className="ios-btn--sm" loading={registerMutation.isPending} onClick={submitRegister}>
              Save
            </Button>
          </>
        }
      >
        <div className="ios-form">
          <Select
            id="webhook-event-type"
            label="Event type"
            value={eventType}
            options={WEBHOOK_EVENT_TYPES}
            onValueChange={setEventType}
          />
          <TextField
            id="webhook-endpoint-url"
            label="Endpoint URL"
            value={endpointUrl}
            onValueChange={setEndpointUrl}
            placeholder="https://merchant.example.com/webhooks/cpay"
            invalid={Boolean(formError)}
            autoComplete="off"
          />
          {formError ? (
            <Alert variant="error">{formError}</Alert>
          ) : (
            <p className="ios-channel-subtitle">
              Payloads are signed with a per-endpoint secret and retried with backoff until success.
            </p>
          )}
        </div>
      </Sheet>
    </>
  );
}

export default MerchantWebhookEndpointsPanel;
