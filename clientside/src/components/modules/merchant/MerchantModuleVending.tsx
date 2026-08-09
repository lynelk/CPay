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
  { value: 'NONE', label: 'None (sandbox only)' },
];

function message(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unable to complete vending operation.';
}

function text(value: unknown): string {
  return value == null ? '' : String(value);
}

export default function MerchantModuleVending({ loader, refreshSignal, sessionExpired }: ModuleProps): React.ReactElement {
  const [overview, setOverview] = useState<Overview>({});
  const [locations, setLocations] = useState<Row[]>([]);
  const [pricing, setPricing] = useState<Row[]>([]);
  const [devices, setDevices] = useState<Row[]>([]);
  const [connectors, setConnectors] = useState<Row[]>([]);
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
  const [authMode, setAuthMode] = useState('BEARER');
  const [authHeaderName, setAuthHeaderName] = useState('');
  const [authValue, setAuthValue] = useState('');
  const [authSecret, setAuthSecret] = useState('');
  const [callbackSecret, setCallbackSecret] = useState('');
  const [responseSuccessField, setResponseSuccessField] = useState('');
  const [responseSuccessValue, setResponseSuccessValue] = useState('');
  const [responseReferenceField, setResponseReferenceField] = useState('');
  const [responseMessageField, setResponseMessageField] = useState('');
  const [callbackEventTypeField, setCallbackEventTypeField] = useState('eventType');
  const [callbackEventIdField, setCallbackEventIdField] = useState('eventId');
  const [callbackDeviceField, setCallbackDeviceField] = useState('deviceId');
  const [callbackRentalField, setCallbackRentalField] = useState('rentalReference');
  const [callbackAssetField, setCallbackAssetField] = useState('assetCode');
  const [callbackAvailableField, setCallbackAvailableField] = useState('availableCount');

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

  async function mutate(path: string, body?: unknown) {
    setBusy(true);
    loader?.('START');
    setError(null);
    setFeedback('');
    try {
      const result = await request<Row>(path, {
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      setFeedback('Saved successfully.');
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
    { key: 'active', header: 'Active', accessor: r => text(r.active_flag) },
    { key: 'secret', header: 'Callback secret', accessor: r => text(r.callback_secret_configured) },
  ];

  return (
    <div className="cpay-vending-merchant">
      {busy && !overview.locations ? <Spinner label="Loading vending operations" /> : null}
      {error ? <Alert variant="error">{message(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}

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
          <Field label="Location"><Select value={locationId} options={locationOptions} onValueChange={setLocationId} /></Field>
          <Field label="Pricing"><Select value={pricingPolicyId} options={pricingOptions} onValueChange={setPricingPolicyId} /></Field>
          <TextField id="vend-connector" label="Connector code" value={deviceConnector} onValueChange={setDeviceConnector} />
          <TextField id="vend-external-device" label="Manufacturer device id" value={externalDeviceId} onValueChange={setExternalDeviceId} />
          <TextField id="vend-slots" label="Slots" value={slotCount} onValueChange={setSlotCount} />
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/devices', { locationId: Number(locationId), pricingPolicyId: Number(pricingPolicyId), deviceCode, deviceType, connectorCode: deviceConnector, externalDeviceId, slotCount: Number(slotCount) })}>Register device</Button>
        </Grid>
      </div></Card>

      <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar><strong>4. ChargeNow / manufacturer contract</strong></Toolbar>
        <p style={{ marginTop: 0 }}>Enter the endpoint, authentication and exact JSON-field mappings from the manufacturer's integration pack. CPay deliberately does not guess unpublished ChargeNow wire details.</p>
        <Grid>
          <TextField id="vend-command-url" label="Command base URL" value={commandBaseUrl} onValueChange={setCommandBaseUrl} placeholder="https://manufacturer.example/api" />
          <TextField id="vend-release-path" label="Release path" value={releasePath} onValueChange={setReleasePath} placeholder="from OEM API pack" />
          <Field label="Authentication"><Select value={authMode} options={AUTH_MODES} onValueChange={setAuthMode} /></Field>
          <TextField id="vend-auth-header" label="Auth header name" value={authHeaderName} onValueChange={setAuthHeaderName} />
          <TextField id="vend-auth-value" label="Auth value / username" value={authValue} onValueChange={setAuthValue} />
          <TextField id="vend-auth-secret" label="Auth secret / password" value={authSecret} onValueChange={setAuthSecret} />
          <TextField id="vend-callback-secret" label="Callback HMAC secret" value={callbackSecret} onValueChange={setCallbackSecret} />
          <TextField id="vend-success-field" label="Response success field" value={responseSuccessField} onValueChange={setResponseSuccessField} />
          <TextField id="vend-success-value" label="Response success value" value={responseSuccessValue} onValueChange={setResponseSuccessValue} />
          <TextField id="vend-reference-field" label="Response reference field" value={responseReferenceField} onValueChange={setResponseReferenceField} />
          <TextField id="vend-message-field" label="Response message field" value={responseMessageField} onValueChange={setResponseMessageField} />
          <TextField id="vend-event-type-field" label="Callback event-type field" value={callbackEventTypeField} onValueChange={setCallbackEventTypeField} />
          <TextField id="vend-event-id-field" label="Callback event-id field" value={callbackEventIdField} onValueChange={setCallbackEventIdField} />
          <TextField id="vend-callback-device-field" label="Callback device-id field" value={callbackDeviceField} onValueChange={setCallbackDeviceField} />
          <TextField id="vend-callback-rental-field" label="Callback rental-ref field" value={callbackRentalField} onValueChange={setCallbackRentalField} />
          <TextField id="vend-callback-asset-field" label="Callback asset field" value={callbackAssetField} onValueChange={setCallbackAssetField} />
          <TextField id="vend-callback-available-field" label="Callback available-count field" value={callbackAvailableField} onValueChange={setCallbackAvailableField} />
        </Grid>
        <label htmlFor="vend-release-template" style={{ display: 'block', marginTop: 'var(--ios-space-3)', fontWeight: 700 }}>Release request JSON template</label>
        <textarea id="vend-release-template" value={releaseTemplate} onChange={e => setReleaseTemplate(e.target.value)} rows={5} style={{ width: '100%', marginTop: 8, borderRadius: 12, padding: 12 }} />
        <div style={{ marginTop: 'var(--ios-space-3)' }}>
          <Button variant="primary" onClick={() => mutate('/api/v2/merchant-self-service/vending/connectors/CHARGENOW', {
            commandBaseUrl, releasePath, releaseRequestTemplate: releaseTemplate, authMode, authHeaderName,
            authValue, authSecret, callbackSecret, responseSuccessField, responseSuccessValue,
            responseReferenceField, responseMessageField, callbackEventTypeField, callbackEventIdField,
            callbackDeviceField, callbackRentalField, callbackAssetField, callbackAvailableCountField: callbackAvailableField, active: true,
          })}>Save manufacturer contract</Button>
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
