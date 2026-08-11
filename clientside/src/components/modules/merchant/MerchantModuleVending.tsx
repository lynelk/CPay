import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Select, Spinner, Table, TextField, Toolbar } from '../../../ui';
import type { Column } from '../../../ui';
import { ApiError, request } from '../../../shared/api/httpClient';

interface ModuleProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

type Row = Record<string, unknown>;

type Overview = {
  locations?: number;
  devices?: number;
  pricingPolicies?: number;
  recentRentals?: Row[];
};

const AUTH_MODES = [
  { value: 'BEARER', label: 'Bearer token' },
  { value: 'API_KEY_HEADER', label: 'API key header' },
  { value: 'BASIC', label: 'Basic auth' },
  { value: 'HMAC_SHA256_TS_BODY', label: 'HMAC SHA-256' },
  { value: 'NONE', label: 'None (localhost sandbox only)' },
];

const CALLBACK_MODES = [
  { value: 'HMAC_SHA256_TS_NONCE_BODY', label: 'HMAC timestamp + nonce + body' },
  { value: 'HMAC_SHA256_TS_BODY', label: 'HMAC timestamp + body' },
  { value: 'HMAC_SHA256_BODY', label: 'HMAC body' },
  { value: 'STATIC_TOKEN_HEADER', label: 'Static callback token header' },
];

const ENCODINGS = [
  { value: 'BASE64', label: 'Base64' },
  { value: 'HEX', label: 'Hex' },
];

const COMPLETION_MODES = [
  { value: 'CALLBACK', label: 'Callback confirms physical completion' },
  { value: 'IMMEDIATE', label: 'HTTP response confirms completion' },
];

const HTTP_METHODS = [
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'PATCH', label: 'PATCH' },
];

function message(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unable to complete vending operation.';
}

function text(value: unknown): string {
  return value == null ? '' : String(value);
}

function issues(value: unknown): string {
  if (!Array.isArray(value)) return '';
  return value.map(text).filter(Boolean).join(' · ');
}

export default function MerchantModuleVending({ loader, refreshSignal, sessionExpired }: ModuleProps): React.ReactElement {
  const [overview, setOverview] = useState<Overview>({});
  const [locations, setLocations] = useState<Row[]>([]);
  const [pricing, setPricing] = useState<Row[]>([]);
  const [devices, setDevices] = useState<Row[]>([]);
  const [connectors, setConnectors] = useState<Row[]>([]);
  const [readiness, setReadiness] = useState<Row | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState<string>('');

  const [locationCode, setLocationCode] = useState('');
  const [locationName, setLocationName] = useState('');
  const [locationAddress, setLocationAddress] = useState('');

  const [policyCode, setPolicyCode] = useState('POWERBANK_UG');
  const [policyName, setPolicyName] = useState('Uganda power-bank standard');
  const [currency, setCurrency] = useState('UGX');
  const [depositAmount, setDepositAmount] = useState('20000');
  const [unitPrice, setUnitPrice] = useState('2000');
  const [billingMinutes, setBillingMinutes] = useState('60');

  const [deviceCode, setDeviceCode] = useState('');
  const [deviceType, setDeviceType] = useState('POWER_BANK_CABINET');
  const [locationId, setLocationId] = useState('');
  const [pricingPolicyId, setPricingPolicyId] = useState('');
  const [deviceConnector, setDeviceConnector] = useState('SIMULATED');
  const [externalDeviceId, setExternalDeviceId] = useState('');
  const [slotCount, setSlotCount] = useState('8');

  const [commandBaseUrl, setCommandBaseUrl] = useState('');
  const [releasePath, setReleasePath] = useState('');
  const [releaseTemplate, setReleaseTemplate] = useState('{"stationId":"{{externalDeviceId}}","requestId":"{{commandReference}}","rentalReference":"{{rentalReference}}"}');
  const [releaseIdempotencyHeader, setReleaseIdempotencyHeader] = useState('');
  const [releaseCompletionMode, setReleaseCompletionMode] = useState('CALLBACK');

  const [authMode, setAuthMode] = useState('BEARER');
  const [authHeaderName, setAuthHeaderName] = useState('');
  const [authTimestampHeader, setAuthTimestampHeader] = useState('');
  const [authKeyHeader, setAuthKeyHeader] = useState('');
  const [authSignatureEncoding, setAuthSignatureEncoding] = useState('BASE64');
  const [authSigningTemplate, setAuthSigningTemplate] = useState('{{timestamp}}\n{{commandReference}}\n{{body}}');
  const [authValue, setAuthValue] = useState('');
  const [authSecret, setAuthSecret] = useState('');

  const [callbackSecret, setCallbackSecret] = useState('');
  const [callbackSignatureMode, setCallbackSignatureMode] = useState('HMAC_SHA256_TS_NONCE_BODY');
  const [callbackSignatureEncoding, setCallbackSignatureEncoding] = useState('BASE64');
  const [callbackSignatureHeader, setCallbackSignatureHeader] = useState('X-CPay-Vending-Signature');
  const [callbackTimestampHeader, setCallbackTimestampHeader] = useState('X-CPay-Vending-Timestamp');
  const [callbackNonceHeader, setCallbackNonceHeader] = useState('X-CPay-Vending-Nonce');
  const [callbackEventTypeField, setCallbackEventTypeField] = useState('eventType');
  const [callbackEventIdField, setCallbackEventIdField] = useState('eventId');
  const [callbackDeviceField, setCallbackDeviceField] = useState('deviceId');
  const [callbackRentalField, setCallbackRentalField] = useState('rentalReference');
  const [callbackCommandReferenceField, setCallbackCommandReferenceField] = useState('');
  const [callbackProviderReferenceField, setCallbackProviderReferenceField] = useState('');
  const [callbackAssetField, setCallbackAssetField] = useState('assetCode');
  const [callbackAvailableField, setCallbackAvailableField] = useState('availableCount');

  const [responseSuccessField, setResponseSuccessField] = useState('');
  const [responseSuccessValue, setResponseSuccessValue] = useState('');
  const [responseReferenceField, setResponseReferenceField] = useState('');
  const [responseMessageField, setResponseMessageField] = useState('');

  const [statusHttpMethod, setStatusHttpMethod] = useState('GET');
  const [statusPath, setStatusPath] = useState('/stations/{{externalDeviceId}}/status');
  const [statusTemplate, setStatusTemplate] = useState('');

  async function load() {
    setBusy(true);
    loader?.('START');
    setError(null);
    try {
      const [o, l, p, d, c] = await Promise.all([
        request<Overview>('/api/v2/merchant-self-service/vending/overview'),
        request<Row[]>('/api/v2/merchant-self-service/vending/locations'),
        request<Row[]>('/api/v2/merchant-self-service/vending/pricing'),
        request<Row[]>('/api/v2/merchant-self-service/vending/devices'),
        request<Row[]>('/api/v2/merchant-self-service/vending/connectors'),
      ]);
      setOverview(o);
      setLocations(l);
      setPricing(p);
      setDevices(d);
      setConnectors(c);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }

  useEffect(() => { void load(); }, [refreshSignal]); // eslint-disable-line react-hooks/exhaustive-deps

  async function mutate(path: string, body?: unknown, successMessage = 'Saved successfully.') {
    setBusy(true);
    loader?.('START');
    setError(null);
    setFeedback('');
    try {
      const result = await request<Row>(path, {
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      setFeedback(successMessage);
      await load();
      return result;
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
      return null;
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }

  async function checkReadiness() {
    setBusy(true);
    loader?.('START');
    setError(null);
    try {
      const result = await request<Row>('/api/v2/merchant-self-service/vending/connectors/CHARGENOW/readiness');
      setReadiness(result);
      setFeedback(text(result.status));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
      loader?.('STOP');
    }
  }

  const locationOptions = useMemo(
    () => [{ value: '', label: 'Select location' }, ...locations.map(r => ({ value: text(r.id), label: `${text(r.location_code)} · ${text(r.name)}` }))],
    [locations],
  );
  const pricingOptions = useMemo(
    () => [{ value: '', label: 'Select pricing policy' }, ...pricing.map(r => ({ value: text(r.id), label: `${text(r.policy_code)} · ${text(r.name)}` }))],
    [pricing],
  );

  const rentalColumns: Column<Row>[] = [
    { key: 'ref', header: 'Rental', accessor: r => text(r.rental_reference) },
    { key: 'customer', header: 'Customer', accessor: r => text(r.customer_mask) },
    { key: 'status', header: 'Status', accessor: r => text(r.status) },
    { key: 'deposit', header: 'Deposit', accessor: r => `${text(r.currency)} ${text(r.deposit_amount)}` },
    { key: 'usage', header: 'Usage', accessor: r => `${text(r.currency)} ${text(r.usage_amount)}` },
    { key: 'refund', header: 'Refund', accessor: r => `${text(r.currency)} ${text(r.refund_amount)}` },
    {
      key: 'sync', header: '', render: r => (
        <Button variant="ghost" className="ios-btn--sm" onClick={() => mutate(`/api/v2/merchant-self-service/vending/rentals/${encodeURIComponent(text(r.rental_reference))}/sync`)}>Sync</Button>
      ),
    },
  ];

  const deviceColumns: Column<Row>[] = [
    { key: 'device', header: 'Device', accessor: r => text(r.device_code) },
    { key: 'location', header: 'Location', accessor: r => text(r.location_name) },
    { key: 'connector', header: 'Connector', accessor: r => text(r.connector_code) },
    { key: 'status', header: 'Status', accessor: r => text(r.status) },
    { key: 'available', header: 'Available', accessor: r => `${text(r.available_count)} / ${text(r.slot_count)}` },
    {
      key: 'probe', header: '', render: r => text(r.connector_code) === 'CHARGENOW' ? (
        <Button variant="ghost" className="ios-btn--sm" onClick={async () => {
          const result = await mutate(
            `/api/v2/merchant-self-service/vending/devices/${encodeURIComponent(text(r.device_code))}/probe`,
            { commandType: 'QUERY_STATUS' },
            'Manufacturer status probe completed.',
          );
          if (result) setFeedback(`${text(result.status)}: ${text(result.message)}`);
        }}>Probe OEM</Button>
      ) : null,
    },
    {
      key: 'qr', header: '', render: r => (
        <Button variant="ghost" className="ios-btn--sm" onClick={async () => {
          const result = await mutate(`/api/v2/merchant-self-service/vending/devices/${encodeURIComponent(text(r.device_code))}/rotate-public-token`);
          if (result) setFeedback(`QR target: ${text(result.qrPayload)}`);
        }}>Rotate QR</Button>
      ),
    },
  ];

  const connectorColumns: Column<Row>[] = [
    { key: 'connector', header: 'Connector', accessor: r => text(r.connector_code) },
    { key: 'url', header: 'Command host', accessor: r => text(r.command_base_url) },
    { key: 'auth', header: 'Auth', accessor: r => text(r.auth_mode) },
    { key: 'callback', header: 'Callback auth', accessor: r => text(r.callback_signature_mode) },
    { key: 'active', header: 'Active', accessor: r => text(r.active_flag) },
    { key: 'secret', header: 'Callback secret', accessor: r => text(r.callback_secret_configured) },
  ];

  return (
    <div className="cpay-vending-merchant">
      {busy && !overview.locations ? <Spinner label="Loading vending operations" /> : null}
      {error ? <Alert variant="error">{message(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}
      {readiness && text(readiness.status) !== 'READY_FOR_OEM_SANDBOX' ? <Alert variant="warning">ChargeNow setup: {text(readiness.status)}. {issues(readiness.issues)}</Alert> : null}

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>Vending estate</strong><Button variant="ghost" className="ios-btn--sm" onClick={() => void load()}>Refresh</Button></Toolbar>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)' }}>
          <Metric label="Locations" value={overview.locations ?? 0} />
          <Metric label="Devices" value={overview.devices ?? 0} />
          <Metric label="Pricing policies" value={overview.pricingPolicies ?? 0} />
          <Metric label="Recent rentals" value={(overview.recentRentals ?? []).length} />
        </div>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>1. Add location</strong></Toolbar>
        <Grid>
          <TextField id="vend-location-code" label="Location code" value={locationCode} onValueChange={setLocationCode} />
          <TextField id="vend-location-name" label="Location name" value={locationName} onValueChange={setLocationName} />
          <TextField id="vend-location-address" label="Address" value={locationAddress} onValueChange={setLocationAddress} />
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/locations', { locationCode, name: locationName, address: locationAddress })}>Save location</Button>
        </Grid>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>2. Pricing policy</strong></Toolbar>
        <Grid>
          <TextField id="vend-policy-code" label="Policy code" value={policyCode} onValueChange={setPolicyCode} />
          <TextField id="vend-policy-name" label="Name" value={policyName} onValueChange={setPolicyName} />
          <TextField id="vend-currency" label="Currency" value={currency} onValueChange={setCurrency} />
          <TextField id="vend-deposit" label="Deposit" value={depositAmount} onValueChange={setDepositAmount} />
          <TextField id="vend-unit" label="Unit price" value={unitPrice} onValueChange={setUnitPrice} />
          <TextField id="vend-block" label="Billing block minutes" value={billingMinutes} onValueChange={setBillingMinutes} />
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/pricing', { policyCode, name: policyName, currency, depositAmount, unitPrice, billingBlockMinutes: Number(billingMinutes), minimumBillingBlocks: 1, refundMode: 'ORIGINAL_ROUTE' })}>Save pricing</Button>
        </Grid>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>3. Register device</strong></Toolbar>
        <Grid>
          <TextField id="vend-device-code" label="Device code" value={deviceCode} onValueChange={setDeviceCode} />
          <TextField id="vend-device-type" label="Device type" value={deviceType} onValueChange={setDeviceType} />
          <Field label="Location"><Select id="vend-device-location" value={locationId} options={locationOptions} onValueChange={setLocationId} /></Field>
          <Field label="Pricing"><Select id="vend-device-pricing" value={pricingPolicyId} options={pricingOptions} onValueChange={setPricingPolicyId} /></Field>
          <TextField id="vend-connector" label="Connector code" value={deviceConnector} onValueChange={setDeviceConnector} />
          <TextField id="vend-external-device" label="Manufacturer device id" value={externalDeviceId} onValueChange={setExternalDeviceId} />
          <TextField id="vend-slots" label="Slots" value={slotCount} onValueChange={setSlotCount} />
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/devices', { locationId: Number(locationId), pricingPolicyId: Number(pricingPolicyId), deviceCode, deviceType, connectorCode: deviceConnector, externalDeviceId, slotCount: Number(slotCount) })}>Register device</Button>
        </Grid>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>4. ChargeNow manufacturer contract</strong><Button variant="ghost" className="ios-btn--sm" onClick={() => void checkReadiness()}>Check readiness</Button></Toolbar>
        <p style={{ marginTop: 0 }}>Enter the exact endpoint, authentication and field mappings from the OEM integration pack. An accepted HTTP response defaults to RELEASE_PENDING; billing begins only after the configured release callback confirms the cabinet actually dispensed the asset.</p>
        <Grid>
          <TextField id="vend-command-url" label="Command base URL" value={commandBaseUrl} onValueChange={setCommandBaseUrl} placeholder="https://manufacturer.example/api" />
          <TextField id="vend-release-path" label="Release path" value={releasePath} onValueChange={setReleasePath} placeholder="/stations/release" />
          <TextField id="vend-release-idempotency" label="OEM idempotency header" value={releaseIdempotencyHeader} onValueChange={setReleaseIdempotencyHeader} placeholder="e.g. Idempotency-Key" />
          <Field label="Release completion"><Select id="vend-release-completion" value={releaseCompletionMode} options={COMPLETION_MODES} onValueChange={setReleaseCompletionMode} /></Field>
          <Field label="Outbound authentication"><Select id="vend-auth-mode" value={authMode} options={AUTH_MODES} onValueChange={setAuthMode} /></Field>
          <TextField id="vend-auth-header" label="Signature/API-key header" value={authHeaderName} onValueChange={setAuthHeaderName} />
          <TextField id="vend-auth-timestamp-header" label="HMAC timestamp header" value={authTimestampHeader} onValueChange={setAuthTimestampHeader} />
          <TextField id="vend-auth-key-header" label="HMAC public-key header" value={authKeyHeader} onValueChange={setAuthKeyHeader} />
          <Field label="Outbound signature encoding"><Select id="vend-auth-encoding" value={authSignatureEncoding} options={ENCODINGS} onValueChange={setAuthSignatureEncoding} /></Field>
          <TextField id="vend-auth-value" label="Auth value / username / public key" value={authValue} onValueChange={setAuthValue} />
          <TextField id="vend-auth-secret" label="Auth secret / password" value={authSecret} onValueChange={setAuthSecret} />
        </Grid>
        <label htmlFor="vend-auth-signing-template" style={{ display: 'block', marginTop: 'var(--ios-space-3)', fontWeight: 700 }}>Outbound HMAC signing template</label>
        <textarea id="vend-auth-signing-template" value={authSigningTemplate} onChange={e => setAuthSigningTemplate(e.target.value)} rows={3} style={{ width: '100%', marginTop: 8, borderRadius: 12, padding: 12 }} />
        <label htmlFor="vend-release-template" style={{ display: 'block', marginTop: 'var(--ios-space-3)', fontWeight: 700 }}>Release request JSON template</label>
        <textarea id="vend-release-template" value={releaseTemplate} onChange={e => setReleaseTemplate(e.target.value)} rows={5} style={{ width: '100%', marginTop: 8, borderRadius: 12, padding: 12 }} />

        <Toolbar><strong>Response and callback mapping</strong></Toolbar>
        <Grid>
          <TextField id="vend-success-field" label="Response success field" value={responseSuccessField} onValueChange={setResponseSuccessField} />
          <TextField id="vend-success-value" label="Response success value" value={responseSuccessValue} onValueChange={setResponseSuccessValue} />
          <TextField id="vend-reference-field" label="Response OEM reference field" value={responseReferenceField} onValueChange={setResponseReferenceField} />
          <TextField id="vend-message-field" label="Response message field" value={responseMessageField} onValueChange={setResponseMessageField} />
          <Field label="Callback authentication"><Select id="vend-callback-auth" value={callbackSignatureMode} options={CALLBACK_MODES} onValueChange={setCallbackSignatureMode} /></Field>
          <Field label="Callback signature encoding"><Select id="vend-callback-encoding" value={callbackSignatureEncoding} options={ENCODINGS} onValueChange={setCallbackSignatureEncoding} /></Field>
          <TextField id="vend-callback-secret" label="Callback secret / token" value={callbackSecret} onValueChange={setCallbackSecret} />
          <TextField id="vend-callback-signature-header" label="Callback signature/token header" value={callbackSignatureHeader} onValueChange={setCallbackSignatureHeader} />
          <TextField id="vend-callback-timestamp-header" label="Callback timestamp header" value={callbackTimestampHeader} onValueChange={setCallbackTimestampHeader} />
          <TextField id="vend-callback-nonce-header" label="Callback nonce header" value={callbackNonceHeader} onValueChange={setCallbackNonceHeader} />
          <TextField id="vend-event-type-field" label="Callback event-type field" value={callbackEventTypeField} onValueChange={setCallbackEventTypeField} />
          <TextField id="vend-event-id-field" label="Callback event-id field" value={callbackEventIdField} onValueChange={setCallbackEventIdField} />
          <TextField id="vend-callback-device-field" label="Callback device-id field" value={callbackDeviceField} onValueChange={setCallbackDeviceField} />
          <TextField id="vend-callback-rental-field" label="Callback rental-ref field" value={callbackRentalField} onValueChange={setCallbackRentalField} />
          <TextField id="vend-callback-command-field" label="Fallback callback command-ref field" value={callbackCommandReferenceField} onValueChange={setCallbackCommandReferenceField} />
          <TextField id="vend-callback-provider-field" label="Fallback callback OEM-ref field" value={callbackProviderReferenceField} onValueChange={setCallbackProviderReferenceField} />
          <TextField id="vend-callback-asset-field" label="Callback asset field" value={callbackAssetField} onValueChange={setCallbackAssetField} />
          <TextField id="vend-callback-available-field" label="Callback available-count field" value={callbackAvailableField} onValueChange={setCallbackAvailableField} />
        </Grid>
        <div style={{ display: 'flex', gap: 'var(--ios-space-2)', flexWrap: 'wrap', marginTop: 'var(--ios-space-3)' }}>
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/connectors/CHARGENOW', {
            commandBaseUrl, releasePath, releaseRequestTemplate: releaseTemplate, releaseCompletionMode,
            idempotencyHeaderName: releaseIdempotencyHeader, authMode, authHeaderName, authTimestampHeader,
            authKeyHeader, authSignatureEncoding, authSigningTemplate, authValue, authSecret, callbackSecret,
            callbackSignatureMode, callbackSignatureEncoding, callbackSignatureHeader, callbackTimestampHeader,
            callbackNonceHeader, responseSuccessField, responseSuccessValue, responseReferenceField,
            responseMessageField, callbackEventTypeField, callbackEventIdField, callbackDeviceField,
            callbackRentalField, callbackAssetField, callbackAvailableCountField: callbackAvailableField, active: true,
          })}>Save manufacturer contract</Button>
          <Button variant="ghost" onClick={() => mutate('/api/v2/merchant-self-service/vending/connectors/CHARGENOW/callback-correlation', {
            callbackCommandReferenceField, callbackProviderReferenceField,
          })}>Save callback correlation</Button>
        </div>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>5. ChargeNow status / diagnostic operation</strong></Toolbar>
        <p style={{ marginTop: 0 }}>Map a read-only OEM status operation, then use Probe OEM on a registered ChargeNow device. The path supports <code>{'{{externalDeviceId}}'}</code>.</p>
        <Grid>
          <Field label="HTTP method"><Select id="vend-status-method" value={statusHttpMethod} options={HTTP_METHODS} onValueChange={setStatusHttpMethod} /></Field>
          <TextField id="vend-status-path" label="Status path" value={statusPath} onValueChange={setStatusPath} />
        </Grid>
        <label htmlFor="vend-status-template" style={{ display: 'block', marginTop: 'var(--ios-space-3)', fontWeight: 700 }}>Optional status request JSON template</label>
        <textarea id="vend-status-template" value={statusTemplate} onChange={e => setStatusTemplate(e.target.value)} rows={3} style={{ width: '100%', marginTop: 8, borderRadius: 12, padding: 12 }} />
        <div style={{ marginTop: 'var(--ios-space-3)' }}>
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/connectors/CHARGENOW/operations/QUERY_STATUS', {
            httpMethod: statusHttpMethod, commandPath: statusPath, requestTemplate: statusTemplate,
            completionMode: 'IMMEDIATE', active: true,
          })}>Save status operation</Button>
        </div>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}><Toolbar><strong>Devices & QR targets</strong></Toolbar></div><Table columns={deviceColumns} rows={devices} rowKey={r => text(r.id)} pageSize={20} emptyText="No vending devices registered." /></Card>
      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}><Toolbar><strong>Manufacturer connectors</strong></Toolbar></div><Table columns={connectorColumns} rows={connectors} rowKey={r => text(r.id)} pageSize={10} emptyText="No manufacturer contracts configured." /></Card>
      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}><Toolbar><strong>Recent rentals</strong></Toolbar></div><Table columns={rentalColumns} rows={overview.recentRentals ?? []} rowKey={r => text(r.id)} pageSize={20} emptyText="No vending rentals yet." /></Card>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }): React.ReactElement {
  return <div style={{ padding: 'var(--ios-space-3)', border: '1px solid var(--ios-separator)', borderRadius: 14 }}><div style={{ fontSize: 12, opacity: .7 }}>{label}</div><strong style={{ fontSize: 26 }}>{value}</strong></div>;
}

function Grid({ children }: { children: React.ReactNode }): React.ReactElement {
  return <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(190px,1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>{children}</div>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }): React.ReactElement {
  return <div><label style={{ display: 'block', marginBottom: 6 }}>{label}</label>{children}</div>;
}
