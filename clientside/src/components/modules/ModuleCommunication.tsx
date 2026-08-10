import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Select, Spinner, Table, Tabs, TextArea, TextField, Toolbar } from '../../ui';
import type { Column } from '../../ui';
import { ApiError, request } from '../../shared/api/httpClient';

interface ProviderRow { id?: number; providerCode?: string; providerName?: string; channel?: string; enabledFlag?: string; }
interface RuleRow { id?: number; channel?: string; merchantId?: number | null; priority?: number; providerCode?: string; enabledFlag?: string; }
interface UssdRow { id?: number; sessionId?: string; merchantId?: number; msisdnHash?: string; lastInput?: string; responseText?: string; status?: string; updatedAt?: string; }

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

export default function ModuleCommunication(): React.ReactElement {
  const [tab, setTab] = useState('routing');
  const [providers, setProviders] = useState<ProviderRow[]>([]);
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [ussdRows, setUssdRows] = useState<UssdRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState('');

  const [routeChannel, setRouteChannel] = useState('SMS');
  const [routeMerchant, setRouteMerchant] = useState('');
  const [routeProvider, setRouteProvider] = useState('');
  const [routePriority, setRoutePriority] = useState('100');

  const [waMerchant, setWaMerchant] = useState('');
  const [waRecipients, setWaRecipients] = useState('');
  const [waContent, setWaContent] = useState('');
  const [ussdMerchant, setUssdMerchant] = useState('');

  async function loadRouting() {
    setLoading(true); setError(null);
    try {
      const [providerData, ruleData] = await Promise.all([
        request<{ providers?: ProviderRow[] }>('/api/v2/admin/communication/routing/providers'),
        request<{ rules?: RuleRow[] }>('/api/v2/admin/communication/routing/rules'),
      ]);
      setProviders(providerData.providers ?? []);
      setRules(ruleData.rules ?? []);
    } catch (e) { setError(e); } finally { setLoading(false); }
  }

  async function loadUssd() {
    setLoading(true); setError(null);
    try {
      const suffix = ussdMerchant.trim() ? `?merchantId=${encodeURIComponent(ussdMerchant.trim())}` : '';
      setUssdRows(await request<UssdRow[]>(`/api/v2/admin/communication/ussd/sessions${suffix}`));
    } catch (e) { setError(e); } finally { setLoading(false); }
  }

  useEffect(() => { void loadRouting(); }, []);
  useEffect(() => { if (tab === 'ussd') void loadUssd(); }, [tab]);

  const providerOptions = providers
    .filter((p) => p.channel === routeChannel && String(p.enabledFlag).toUpperCase() === 'YES')
    .map((p) => ({ value: p.providerCode ?? '', label: p.providerName ?? p.providerCode ?? '' }));

  useEffect(() => {
    if (!providerOptions.some((p) => p.value === routeProvider)) {
      setRouteProvider(providerOptions[0]?.value ?? '');
    }
  }, [routeChannel, providers]);

  async function saveRoute() {
    setError(null); setFeedback('');
    if (!routeProvider) { setError(new Error(`No enabled ${routeChannel} provider is registered.`)); return; }
    try {
      await request('/api/v2/admin/communication/routing/rules', {
        method: 'POST',
        body: JSON.stringify({
          id: null,
          channel: routeChannel,
          merchantId: routeMerchant.trim() ? Number(routeMerchant.trim()) : null,
          priority: Number(routePriority || '100'),
          providerCode: routeProvider,
          enabledFlag: 'YES',
        }),
      });
      setFeedback(`${routeChannel} routing rule saved.`);
      await loadRouting();
    } catch (e) { setError(e); }
  }

  async function sendWhatsApp() {
    setError(null); setFeedback('');
    try {
      const result = await request<{ status?: string; successful?: boolean }>('/api/v2/admin/communication/whatsapp/send', {
        method: 'POST',
        body: JSON.stringify({ merchantId: Number(waMerchant), recipients: waRecipients, content: waContent }),
      });
      setFeedback(`WhatsApp send result: ${result.status ?? (result.successful ? 'SENT' : 'UNKNOWN')}.`);
    } catch (e) { setError(e); }
  }

  const ruleColumns = useMemo<Column<RuleRow>[]>(() => [
    { key: 'channel', header: 'Channel', accessor: (r) => r.channel ?? '' },
    { key: 'merchant', header: 'Customer', accessor: (r) => r.merchantId == null ? 'Platform default' : String(r.merchantId) },
    { key: 'provider', header: 'Provider', accessor: (r) => r.providerCode ?? '' },
    { key: 'priority', header: 'Priority', accessor: (r) => String(r.priority ?? '') },
    { key: 'enabled', header: 'Enabled', accessor: (r) => r.enabledFlag ?? '' },
  ], []);

  const ussdColumns = useMemo<Column<UssdRow>[]>(() => [
    { key: 'session', header: 'Session', accessor: (r) => r.sessionId ?? '' },
    { key: 'merchant', header: 'Customer', accessor: (r) => String(r.merchantId ?? '') },
    { key: 'msisdn', header: 'MSISDN hash', accessor: (r) => r.msisdnHash ?? '' },
    { key: 'input', header: 'Last input', accessor: (r) => r.lastInput ?? '' },
    { key: 'response', header: 'Response', accessor: (r) => r.responseText ?? '' },
    { key: 'status', header: 'Status', accessor: (r) => r.status ?? '' },
    { key: 'updated', header: 'Updated', accessor: (r) => r.updatedAt ?? '' },
  ], []);

  return (
    <div className="cpay-communication">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {feedback ? <Alert variant="success">{feedback}</Alert> : null}
      <Card>
        <Toolbar><strong>Communication</strong><Toolbar.Spacer /><Button variant="ghost" onClick={() => tab === 'ussd' ? void loadUssd() : void loadRouting()}>Refresh</Button></Toolbar>
        <p>Manage communication routing and operations across SMS, WhatsApp and session-based USSD.</p>
        <Tabs items={[{ key: 'routing', label: 'Channels & Routing' }, { key: 'whatsapp', label: 'WhatsApp' }, { key: 'ussd', label: 'USSD Sessions' }]} active={tab} onChange={setTab} />
      </Card>

      {loading ? <Spinner label="Loading communication data" /> : null}

      {tab === 'routing' ? <>
        <Card>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
            <Select id="comm-channel" label="Channel" value={routeChannel} onValueChange={setRouteChannel} options={[{ value: 'SMS', label: 'SMS' }, { value: 'WHATSAPP', label: 'WhatsApp' }]} />
            <Select id="comm-provider" label="Provider" value={routeProvider} onValueChange={setRouteProvider} options={providerOptions} placeholder="Select provider" />
            <TextField id="comm-merchant" label="Customer id" value={routeMerchant} onValueChange={setRouteMerchant} placeholder="blank = platform default" />
            <TextField id="comm-priority" label="Priority" value={routePriority} onValueChange={setRoutePriority} inputMode="numeric" />
          </div>
          <div style={{ marginTop: 16 }}><Button onClick={() => void saveRoute()}>Save routing rule</Button></div>
        </Card>
        <Table columns={ruleColumns} rows={rules} rowKey={(r, i) => r.id ?? i} pageSize={20} emptyText="No communication routing rules configured." />
      </> : null}

      {tab === 'whatsapp' ? <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          <TextField id="wa-merchant" label="Customer id" value={waMerchant} onValueChange={setWaMerchant} inputMode="numeric" />
          <TextField id="wa-recipient" label="Recipient(s)" value={waRecipients} onValueChange={setWaRecipients} placeholder="2567..., comma-separated" />
        </div>
        <TextArea id="wa-content" label="Message" value={waContent} onValueChange={setWaContent} rows={5} />
        <Button onClick={() => void sendWhatsApp()}>Send WhatsApp message</Button>
      </Card> : null}

      {tab === 'ussd' ? <>
        <Card><div style={{ display: 'flex', gap: 12, alignItems: 'end', flexWrap: 'wrap' }}><TextField id="ussd-merchant" label="Customer id" value={ussdMerchant} onValueChange={setUssdMerchant} placeholder="optional" /><Button variant="ghost" onClick={() => void loadUssd()}>Filter sessions</Button></div></Card>
        <Table columns={ussdColumns} rows={ussdRows} rowKey={(r, i) => r.id ?? i} pageSize={20} emptyText="No USSD sessions recorded." />
      </> : null}
    </div>
  );
}
