import React, { useState } from 'react';
import { Alert, Button, Card, Spinner, TextField, Toolbar } from '../../ui';
import { ApiError, request } from '../../shared/api/httpClient';

interface PriceBookComponent {
  componentType?: string;
  sequenceNo?: number;
  flatAmount?: string | number | null;
  percentageRate?: string | number | null;
  tierDefinition?: string | null;
}

interface PriceBookView {
  id?: number;
  billingTenantId?: number | null;
  serviceCode?: string;
  meterCode?: string;
  chargeType?: string;
  currency?: string;
  versionNo?: number;
  effectiveFrom?: string;
  effectiveTo?: string | null;
  components?: PriceBookComponent[];
  code?: string;
  message?: string;
}

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

export default function ModuleBilling({ loader, sessionExpired }: Props): React.ReactElement {
  const [tenantId, setTenantId] = useState('');
  const [serviceCode, setServiceCode] = useState('PAYMENT');
  const [meterCode, setMeterCode] = useState('TRANSACTION');
  const [chargeType, setChargeType] = useState('TRANSACTION_FEE');
  const [currency, setCurrency] = useState('UGX');
  const [flatAmount, setFlatAmount] = useState('');
  const [percentageRate, setPercentageRate] = useState('');
  const [createdBy, setCreatedBy] = useState('');
  const [view, setView] = useState<PriceBookView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState<string>('');

  function queryString(): string {
    const query = new URLSearchParams({ serviceCode, meterCode, chargeType });
    if (tenantId.trim()) query.set('billingTenantId', tenantId.trim());
    return query.toString();
  }

  async function loadActive() {
    setLoading(true);
    setError(null);
    setFeedback('');
    loader?.('START');
    try {
      const result = await request<PriceBookView>(`/api/v2/admin/billing/price-books?${queryString()}`);
      setView(result);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 401) sessionExpired?.();
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }

  async function publish() {
    const components: Array<Record<string, unknown>> = [];
    if (flatAmount.trim()) components.push({ componentType: 'FLAT', flatAmount: flatAmount.trim() });
    if (percentageRate.trim()) components.push({ componentType: 'PERCENTAGE', percentageRate: percentageRate.trim() });
    if (components.length === 0) {
      setError(new Error('Enter at least a flat amount or percentage rate.'));
      return;
    }
    setLoading(true);
    setError(null);
    setFeedback('');
    loader?.('START');
    try {
      const result = await request<PriceBookView>('/api/v2/admin/billing/price-books', {
        method: 'POST',
        body: JSON.stringify({
          billingTenantId: tenantId.trim() ? Number(tenantId.trim()) : null,
          serviceCode: serviceCode.trim(),
          meterCode: meterCode.trim(),
          chargeType: chargeType.trim(),
          currency: currency.trim().toUpperCase(),
          createdBy: createdBy.trim() || 'admin',
          components,
        }),
      });
      setView(result);
      setFeedback(`Published billing price-book version ${result.versionNo ?? result.id ?? ''}.`);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }

  return (
    <div className="cpay-billing">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}

      <Card>
        <Toolbar>
          <strong>Billing</strong>
          <Toolbar.Spacer />
          <Button variant="ghost" onClick={() => void loadActive()}>Load active pricing</Button>
        </Toolbar>
        <p>Manage the active price-book configuration that drives CPay billing rating. Billing accounting remains posted to the shared ledger.</p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          <TextField id="billing-tenant" label="Billing tenant id" value={tenantId} onValueChange={setTenantId} placeholder="blank = platform default" />
          <TextField id="billing-service" label="Service code" value={serviceCode} onValueChange={setServiceCode} />
          <TextField id="billing-meter" label="Meter code" value={meterCode} onValueChange={setMeterCode} />
          <TextField id="billing-charge" label="Charge type" value={chargeType} onValueChange={setChargeType} />
          <TextField id="billing-currency" label="Currency" value={currency} onValueChange={setCurrency} />
          <TextField id="billing-flat" label="Flat amount" value={flatAmount} onValueChange={setFlatAmount} inputMode="decimal" />
          <TextField id="billing-percent" label="Percentage rate" value={percentageRate} onValueChange={setPercentageRate} inputMode="decimal" />
          <TextField id="billing-actor" label="Changed by" value={createdBy} onValueChange={setCreatedBy} />
        </div>
        <div style={{ marginTop: 16 }}>
          <Button onClick={() => void publish()} loading={loading} loadingLabel="Publishing…">Publish new version</Button>
        </div>
      </Card>

      {loading ? <Spinner label="Loading billing price book" /> : null}
      <Card>
        <strong>Active price book</strong>
        {!view ? <p>No price book loaded.</p> : view.code === 'PRICE_BOOK_NOT_CONFIGURED' ? <p>{view.message}</p> : (
          <div>
            <p><strong>{view.serviceCode}</strong> / {view.meterCode} / {view.chargeType} · {view.currency} · version {view.versionNo}</p>
            <p>Effective: {view.effectiveFrom ?? 'n/a'} {view.effectiveTo ? `to ${view.effectiveTo}` : '(active)'}</p>
            <div style={{ overflowX: 'auto' }}>
              <table className="ios-table">
                <thead><tr><th>Type</th><th>Flat</th><th>Percentage</th><th>Tier definition</th></tr></thead>
                <tbody>
                  {(view.components ?? []).map((component, index) => (
                    <tr key={`${component.componentType ?? 'component'}-${index}`}>
                      <td>{component.componentType ?? ''}</td>
                      <td>{component.flatAmount ?? ''}</td>
                      <td>{component.percentageRate ?? ''}</td>
                      <td><code>{component.tierDefinition ?? ''}</code></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
