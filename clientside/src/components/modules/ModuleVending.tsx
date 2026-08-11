import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Spinner, Table, TextField, Toolbar } from '../../ui';
import type { Column } from '../../ui';
import { ApiError, request } from '../../shared/api/httpClient';

type Row = Record<string, unknown>;
type Overview = Record<string, unknown> & { recentRentals?: Row[] };

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

const text = (value: unknown): string => value == null ? '' : String(value);
const errorMessage = (error: unknown): string => error instanceof Error ? error.message : 'Unable to load vending operations.';

export default function ModuleVending({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [merchantId, setMerchantId] = useState('');
  const [overview, setOverview] = useState<Overview>({});
  const [callbacks, setCallbacks] = useState<Row[]>([]);
  const [commands, setCommands] = useState<Row[]>([]);
  const [events, setEvents] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function load() {
    setBusy(true); loader?.('START'); setError(null);
    const query = merchantId.trim() && Number(merchantId) > 0 ? `?merchantId=${encodeURIComponent(merchantId.trim())}` : '';
    const suffix = query ? `${query}&limit=100` : '?limit=100';
    try {
      const [o, c, cmd, ev] = await Promise.all([
        request<Overview>(`/api/v2/admin/vending/overview${query}`),
        request<Row[]>(`/api/v2/admin/vending/callbacks${suffix}`),
        request<Row[]>(`/api/v2/admin/vending/commands${suffix}`),
        request<Row[]>(`/api/v2/admin/vending/events${suffix}`),
      ]);
      setOverview(o); setCallbacks(c); setCommands(cmd); setEvents(ev);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally { setBusy(false); loader?.('STOP'); }
  }

  useEffect(() => { void load(); }, [refreshSignal]); // eslint-disable-line react-hooks/exhaustive-deps

  const rentalColumns: Column<Row>[] = [
    { key: 'merchant', header: 'Merchant', accessor: r => text(r.merchant_id) },
    { key: 'ref', header: 'Rental', accessor: r => text(r.rental_reference) },
    { key: 'device', header: 'Device', accessor: r => text(r.device_code) },
    { key: 'customer', header: 'Customer', accessor: r => text(r.customer_mask) },
    { key: 'status', header: 'Status', accessor: r => text(r.status) },
    { key: 'amount', header: 'Deposit', accessor: r => `${text(r.currency)} ${text(r.deposit_amount)}` },
    { key: 'created', header: 'Created', accessor: r => text(r.created_at) },
  ];
  const callbackColumns: Column<Row>[] = [
    { key: 'merchant', header: 'Merchant', accessor: r => text(r.merchant_id) },
    { key: 'connector', header: 'Connector', accessor: r => text(r.connector_code) },
    { key: 'event', header: 'Event', accessor: r => text(r.event_type) },
    { key: 'external', header: 'External event', accessor: r => text(r.external_event_id) },
    { key: 'sig', header: 'Signature', accessor: r => text(r.signature_status) },
    { key: 'status', header: 'Processing', accessor: r => text(r.processing_status) },
    { key: 'error', header: 'Error', accessor: r => text(r.error_message) },
  ];
  const commandColumns: Column<Row>[] = [
    { key: 'merchant', header: 'Merchant', accessor: r => text(r.merchant_id) },
    { key: 'ref', header: 'Command', accessor: r => text(r.command_reference) },
    { key: 'type', header: 'Type', accessor: r => text(r.command_type) },
    { key: 'connector', header: 'Connector', accessor: r => text(r.connector_code) },
    { key: 'status', header: 'Status', accessor: r => text(r.status) },
    { key: 'provider', header: 'Provider ref', accessor: r => text(r.provider_reference) },
  ];
  const eventColumns: Column<Row>[] = [
    { key: 'merchant', header: 'Merchant', accessor: r => text(r.merchant_id) },
    { key: 'event', header: 'Event', accessor: r => text(r.event_type) },
    { key: 'entity', header: 'Entity', accessor: r => `${text(r.entity_type)} · ${text(r.entity_reference)}` },
    { key: 'actor', header: 'Actor', accessor: r => text(r.actor) },
    { key: 'created', header: 'Created', accessor: r => text(r.created_at) },
  ];

  const metrics: Array<[string, unknown]> = [
    ['Locations', overview.locations], ['Devices', overview.devices], ['Assets', overview.assets],
    ['Rentals', overview.rentals], ['Active', overview.activeRentals], ['Payment pending', overview.pendingPayments],
    ['Refund attention', overview.refundPending], ['Offline devices', overview.offlineDevices], ['Failed callbacks', overview.failedCallbacks],
  ];

  return <div className="cpay-vending-admin">
    {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
    {busy && !Object.keys(overview).length ? <Spinner label="Loading vending estate" /> : null}
    <Card flush><div style={{ padding: 'var(--ios-space-4)' }}>
      <Toolbar><strong>Vending operations</strong><Button variant="ghost" className="ios-btn--sm" onClick={() => void load()}>Refresh</Button></Toolbar>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(220px,360px) auto', gap: 'var(--ios-space-3)', alignItems: 'end', marginTop: 'var(--ios-space-3)' }}>
        <TextField id="vending-admin-merchant" label="Merchant id filter" value={merchantId} onValueChange={setMerchantId} placeholder="blank = all tenants" />
        <Button variant="primary" onClick={() => void load()}>Apply filter</Button>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-4)' }}>
        {metrics.map(([label, value]) => <div key={String(label)} style={{ border: '1px solid var(--ios-separator)', borderRadius: 14, padding: 'var(--ios-space-3)' }}><div style={{ fontSize: 12, opacity: .7 }}>{label}</div><strong style={{ fontSize: 24 }}>{text(value || 0)}</strong></div>)}
      </div>
    </div></Card>
    <Section title="Recent rentals"><Table columns={rentalColumns} rows={overview.recentRentals ?? []} rowKey={r => text(r.id)} pageSize={20} emptyText="No vending rentals." /></Section>
    <Section title="Manufacturer callbacks"><Table columns={callbackColumns} rows={callbacks} rowKey={r => text(r.id)} pageSize={20} emptyText="No device callbacks." /></Section>
    <Section title="Device commands"><Table columns={commandColumns} rows={commands} rowKey={r => text(r.id)} pageSize={20} emptyText="No device commands." /></Section>
    <Section title="Operational events"><Table columns={eventColumns} rows={events} rowKey={r => text(r.id)} pageSize={20} emptyText="No vending events." /></Section>
  </div>;
}

function Section({ title, children }: { title: string; children: React.ReactNode }): React.ReactElement {
  return <Card flush><div style={{ padding: 'var(--ios-space-4)' }}><Toolbar><strong>{title}</strong></Toolbar></div>{children}</Card>;
}
