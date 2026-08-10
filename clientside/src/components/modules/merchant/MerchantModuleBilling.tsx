import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Select, Spinner, Toolbar } from '../../../ui';
import { ApiError, request } from '../../../shared/api/httpClient';

interface PriceComponent {
  componentType?: string;
  flatAmount?: string | number | null;
  percentageRate?: string | number | null;
  tierDefinitionJson?: string | null;
}

interface PriceBook {
  configured?: boolean;
  serviceCode?: string;
  meterCode?: string;
  currency?: string;
  versionNo?: number;
  effectiveFrom?: string;
  components?: PriceComponent[];
}

interface UsageView {
  serviceCode?: string;
  meterCode?: string;
  from?: string;
  to?: string;
  usage?: string | number;
}

const SERVICES = [
  { value: 'PAYMENT|payment_event_count', label: 'Payments' },
  { value: 'SMS|sms_sent_count', label: 'SMS' },
  { value: 'WEBHOOK|webhook_delivered_count', label: 'Webhooks / API delivery' },
  { value: 'INVOICE|invoice_issued_count', label: 'Invoicing' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

export default function MerchantModuleBilling({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [selection, setSelection] = useState(SERVICES[0].value);
  const [price, setPrice] = useState<PriceBook | null>(null);
  const [usage, setUsage] = useState<UsageView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const [serviceCode, meterCode] = selection.split('|');

  async function load() {
    setLoading(true); setError(null); loader?.('START');
    try {
      const query = `serviceCode=${encodeURIComponent(serviceCode)}&meterCode=${encodeURIComponent(meterCode)}`;
      const [priceData, usageData] = await Promise.all([
        request<PriceBook>(`/api/v2/merchant-self-service/billing/price-book?${query}`),
        request<UsageView>(`/api/v2/merchant-self-service/billing/usage?${query}`),
      ]);
      setPrice(priceData);
      setUsage(usageData);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally { setLoading(false); loader?.('STOP'); }
  }

  useEffect(() => { void load(); }, [selection, refreshSignal]);

  return (
    <div className="cpay-merchant-billing">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      <Card>
        <Toolbar><strong>Billing</strong><Toolbar.Spacer /><Button variant="ghost" onClick={() => void load()}>Refresh</Button></Toolbar>
        <p>Review your current customer pricing and usage-to-date. Provider costs remain internal to CPay finance.</p>
        <Select id="merchant-billing-service" label="Service" value={selection} onValueChange={setSelection} options={SERVICES} />
      </Card>
      {loading ? <Spinner label="Loading billing information" /> : null}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
        <Card>
          <strong>Usage this billing period</strong>
          <div style={{ fontSize: '2rem', fontWeight: 700, marginTop: 12 }}>{String(usage?.usage ?? '0')}</div>
          <p>{usage?.from ? `From ${usage.from}` : ''}{usage?.to ? ` to ${usage.to}` : ''}</p>
        </Card>
        <Card>
          <strong>Active pricing</strong>
          {!price?.configured ? <p>No customer price book is configured for this service.</p> : <>
            <p>{price.serviceCode} / {price.meterCode} · {price.currency} · version {price.versionNo}</p>
            <p>Effective from {price.effectiveFrom ?? 'n/a'}</p>
          </>}
        </Card>
      </div>
      {price?.configured ? <Card>
        <strong>Price components</strong>
        <div style={{ overflowX: 'auto', marginTop: 12 }}>
          <table className="ios-table">
            <thead><tr><th>Type</th><th>Flat amount</th><th>Percentage rate</th></tr></thead>
            <tbody>{(price.components ?? []).map((component, index) => <tr key={`${component.componentType ?? 'component'}-${index}`}><td>{component.componentType ?? ''}</td><td>{component.flatAmount ?? ''}</td><td>{component.percentageRate ?? ''}</td></tr>)}</tbody>
          </table>
        </div>
      </Card> : null}
    </div>
  );
}
