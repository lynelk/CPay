import React from 'react';
import { Alert, Badge, Button, Card, Spinner } from '../../../ui';
import { apiFetch } from '../../../shared/api/httpClient';
import { apiUrl } from '../../../shared/config';
import '../../../styles/cito-platform.css';

type Environment = 'SANDBOX' | 'PRODUCTION';
type TabKey =
  | 'services'
  | 'routing'
  | 'marketplace'
  | 'refunds'
  | 'recurring'
  | 'analytics'
  | 'developer'
  | 'virtual'
  | 'embedded'
  | 'integrations';

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

interface FeatureRow {
  serviceCode?: string;
  serviceName?: string;
  description?: string;
  sandboxStatus?: string;
  productionStatus?: string;
}

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'services', label: 'Services' },
  { key: 'routing', label: 'Routing' },
  { key: 'marketplace', label: 'Marketplace' },
  { key: 'refunds', label: 'Refunds & disputes' },
  { key: 'recurring', label: 'Recurring' },
  { key: 'analytics', label: 'Analytics' },
  { key: 'developer', label: 'Developer' },
  { key: 'virtual', label: 'Virtual accounts' },
  { key: 'embedded', label: 'Embedded Cito' },
  { key: 'integrations', label: 'Integrations' },
];

const tabEndpoints: Record<Exclude<TabKey, 'services'>, string[]> = {
  routing: ['/api/v2/merchant-self-service/routing/decisions?limit=30'],
  marketplace: [
    '/api/v2/merchant-self-service/marketplace/subaccounts',
    '/api/v2/merchant-self-service/marketplace/split-rules',
    '/api/v2/merchant-self-service/marketplace/executions?limit=30',
    '/api/v2/merchant-self-service/marketplace/recovery-events?limit=30',
  ],
  refunds: [
    '/api/v2/merchant-self-service/refunds?limit=30',
    '/api/v2/merchant-self-service/disputes?limit=30',
  ],
  recurring: [
    '/api/v2/merchant-self-service/recurring/plans',
    '/api/v2/merchant-self-service/recurring/mandates',
    '/api/v2/merchant-self-service/recurring/subscriptions',
    '/api/v2/merchant-self-service/recurring/charges?limit=30',
  ],
  analytics: [
    '/api/v2/merchant-self-service/analytics/daily',
    '/api/v2/merchant-self-service/analytics/providers',
    '/api/v2/merchant-self-service/analytics/recommendations',
  ],
  developer: ['/api/v2/merchant-self-service/developer/projects'],
  virtual: [
    '/api/v2/merchant-self-service/virtual-accounts',
    '/api/v2/merchant-self-service/virtual-accounts/transfers?limit=30',
  ],
  embedded: [
    '/api/v2/merchant-self-service/embedded/downstream-merchants',
    '/api/v2/merchant-self-service/embedded/delegations',
  ],
  integrations: [
    '/api/v2/merchant-self-service/integrations/catalog',
    '/api/v2/merchant-self-service/integrations/installations',
  ],
};

function asList(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? (value as Array<Record<string, unknown>>) : [];
}

function text(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function first(row: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value !== null && value !== undefined && String(value).trim() !== '') return text(value);
  }
  return '—';
}

function tone(status: unknown): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
  const normalized = text(status).toUpperCase();
  if (['ACTIVE', 'SUCCESS', 'SUCCESSFUL', 'COMPLETED', 'HEALTHY', 'READY'].includes(normalized)) return 'success';
  if (['FAILED', 'REJECTED', 'DISABLED', 'CANCELLED', 'CANCELED'].includes(normalized)) return 'error';
  if (['PENDING', 'PROCESSING', 'REQUESTED', 'PAST_DUE', 'WAITING_PAYMENT', 'RETRY'].some((item) => normalized.includes(item))) return 'warning';
  return 'info';
}

function statusBadge(value: unknown): React.ReactElement {
  return <Badge tone={tone(value)}>{text(value) || 'Unknown'}</Badge>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }): React.ReactElement {
  return (
    <div className="cito-platform__field">
      <label>{label}</label>
      {children}
    </div>
  );
}

function ListPanel({ title, rows }: { title: string; rows: Array<Record<string, unknown>> }): React.ReactElement {
  return (
    <div className="cito-platform__panel">
      <div className="cito-platform__row">
        <h4>{title}</h4>
        <Badge tone="neutral">{rows.length}</Badge>
      </div>
      {rows.length === 0 ? (
        <p className="cito-platform__muted">Nothing to display yet.</p>
      ) : (
        <ul className="cito-platform__list">
          {rows.slice(0, 12).map((row, index) => {
            const heading = first(row, [
              'serviceName', 'projectName', 'connectorName', 'displayName', 'refundReference',
              'disputeReference', 'subscriptionReference', 'planName', 'accountName',
              'decisionReference', 'splitRuleReference', 'executionReference', 'transactionReference',
              'recommendationCode', 'eventReference', 'relationshipReference', 'accountReference',
            ]);
            const reference = first(row, [
              'serviceCode', 'projectReference', 'connectorCode', 'reference', 'transactionReference',
              'originalReference', 'subjectReference', 'channelCode', 'environment', 'currencyCode',
            ]);
            const state = row.status ?? row.outcome ?? row.sandboxStatus ?? row.productionStatus;
            return (
              <li key={`${heading}-${index}`}>
                <div className="cito-platform__row">
                  <strong>{heading}</strong>
                  {state ? statusBadge(state) : null}
                </div>
                {reference !== '—' ? <div className="cito-platform__muted cito-platform__code">{reference}</div> : null}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default function MerchantModuleCitoServices({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [environment, setEnvironment] = React.useState<Environment>('SANDBOX');
  const [tab, setTab] = React.useState<TabKey>('services');
  const [overview, setOverview] = React.useState<Record<string, unknown> | null>(null);
  const [data, setData] = React.useState<Record<string, unknown>>({});
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [notice, setNotice] = React.useState('');

  const [routingForm, setRoutingForm] = React.useState({ operation: 'COLLECT', amount: '1000', country: 'UG', currency: 'UGX', account: '256770000001' });
  const [subaccountForm, setSubaccountForm] = React.useState({ displayName: '', currencyCode: 'UGX', destinationType: 'MERCHANT_LEDGER', destinationReference: '' });
  const [splitForm, setSplitForm] = React.useState({ splitRuleReference: '', grossAmount: '10000', currencyCode: 'UGX' });
  const [refundForm, setRefundForm] = React.useState({ originalReference: '', reference: '', amount: '', reason: '' });
  const [planForm, setPlanForm] = React.useState({ planName: '', amount: '', currencyCode: 'UGX', intervalUnit: 'MONTH', intervalCount: '1' });
  const [projectForm, setProjectForm] = React.useState({ projectName: '', description: '' });
  const [virtualForm, setVirtualForm] = React.useState({ accountName: '', customerReference: '', purposeReference: '', countryCode: 'UG', currencyCode: 'UGX' });
  const [partnerForm, setPartnerForm] = React.useState({ partnerName: '' });
  const [installForm, setInstallForm] = React.useState({ connectorCode: 'GENERIC_WEBHOOK', versionNumber: '1.0.0', displayName: 'Default integration', configurationJson: '{}' });

  const requestJson = React.useCallback(async (path: string, init?: RequestInit): Promise<unknown> => {
    const response = await apiFetch(apiUrl(path), {
      method: init?.method ?? 'GET',
      credentials: 'include',
      cache: 'no-cache',
      headers: {
        'Content-Type': 'application/json',
        'X-CPay-Environment': environment,
        ...(init?.headers ?? {}),
      },
      body: init?.body,
    });
    if (response.status === 401) {
      sessionExpired?.();
      throw new Error('Your session has expired.');
    }
    const raw = await response.text();
    let payload: unknown = {};
    if (raw.trim()) {
      try { payload = JSON.parse(raw); } catch { payload = { message: raw }; }
    }
    if (!response.ok) {
      const message = typeof payload === 'object' && payload && 'message' in payload ? text((payload as Record<string, unknown>).message) : `Request failed (${response.status})`;
      throw new Error(message);
    }
    return payload;
  }, [environment, sessionExpired]);

  const loadOverview = React.useCallback(async () => {
    const value = await requestJson('/api/v2/merchant-self-service/cito/overview');
    setOverview((value ?? {}) as Record<string, unknown>);
  }, [requestJson]);

  const loadTab = React.useCallback(async (key: TabKey) => {
    if (key === 'services') return;
    const endpoints = tabEndpoints[key];
    const values = await Promise.all(endpoints.map((path) => requestJson(path)));
    const next: Record<string, unknown> = {};
    endpoints.forEach((path, index) => { next[path] = values[index]; });
    setData((prev) => ({ ...prev, [key]: next }));
  }, [requestJson]);

  const refresh = React.useCallback(async () => {
    setLoading(true);
    loader?.('START');
    setError('');
    try {
      await Promise.all([loadOverview(), loadTab(tab)]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load Cito services.');
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }, [loadOverview, loadTab, loader, tab]);

  React.useEffect(() => { void refresh(); }, [refresh]);
  React.useEffect(() => {
    if (refreshSignal !== undefined) void refresh();
    // refreshSignal is intentionally an external refresh edge.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSignal]);

  async function runAction(action: () => Promise<unknown>, success: string): Promise<void> {
    setLoading(true);
    loader?.('START');
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      await Promise.all([loadOverview(), loadTab(tab)]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The action could not be completed.');
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }

  const tabData = (data[tab] ?? {}) as Record<string, unknown>;
  const features = asList(overview?.features);

  function endpoint(path: string): Array<Record<string, unknown>> {
    return asList(tabData[path]);
  }

  function renderServices(): React.ReactElement {
    return (
      <div className="cito-platform__section">
        <h3>Service catalogue & entitlements</h3>
        <p>Sandbox is designed for experimentation. Production access is explicit and separately controlled.</p>
        <div className="cito-platform__services" style={{ marginTop: 14 }}>
          {features.map((feature: FeatureRow, index) => (
            <div className="cito-platform__service" key={feature.serviceCode ?? index}>
              <div className="cito-platform__service-head">
                <h4>{feature.serviceName ?? feature.serviceCode}</h4>
                {statusBadge(environment === 'SANDBOX' ? feature.sandboxStatus : feature.productionStatus)}
              </div>
              <p>{feature.description}</p>
              <div className="cito-platform__row" style={{ marginTop: 10 }}>
                <span className="cito-platform__muted">Sandbox</span>{statusBadge(feature.sandboxStatus)}
              </div>
              <div className="cito-platform__row" style={{ marginTop: 6 }}>
                <span className="cito-platform__muted">Production</span>{statusBadge(feature.productionStatus)}
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  function renderRouting(): React.ReactElement {
    const decisions = endpoint('/api/v2/merchant-self-service/routing/decisions?limit=30');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Route simulation</h4>
          <p>Preview provider selection without moving money.</p>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(
              () => requestJson(`/api/v2/merchant-self-service/routing/simulate?operation=${routingForm.operation}`, {
                method: 'POST',
                body: JSON.stringify({
                  amount: routingForm.amount,
                  country: routingForm.country,
                  currency: routingForm.currency,
                  payer: { type: 'MSISDN', value: routingForm.account },
                  payee: { type: 'MSISDN', value: routingForm.account },
                  metadata: { environment },
                }),
              }),
              'Routing simulation completed.',
            );
          }}>
            <div className="cito-platform__form-grid">
              <Field label="Operation"><select value={routingForm.operation} onChange={(e) => setRoutingForm({ ...routingForm, operation: e.target.value })}><option>COLLECT</option><option>PAYOUT</option></select></Field>
              <Field label="Account / MSISDN"><input value={routingForm.account} onChange={(e) => setRoutingForm({ ...routingForm, account: e.target.value })} /></Field>
              <Field label="Amount"><input value={routingForm.amount} onChange={(e) => setRoutingForm({ ...routingForm, amount: e.target.value })} /></Field>
              <Field label="Country"><input value={routingForm.country} onChange={(e) => setRoutingForm({ ...routingForm, country: e.target.value })} /></Field>
              <Field label="Currency"><input value={routingForm.currency} onChange={(e) => setRoutingForm({ ...routingForm, currency: e.target.value })} /></Field>
            </div>
            <Button type="submit" variant="primary">Simulate route</Button>
          </form>
        </div>
        <ListPanel title="Recent routing decisions" rows={decisions} />
      </div>
    );
  }

  function renderMarketplace(): React.ReactElement {
    const subaccounts = endpoint('/api/v2/merchant-self-service/marketplace/subaccounts');
    const rules = endpoint('/api/v2/merchant-self-service/marketplace/split-rules');
    const executions = endpoint('/api/v2/merchant-self-service/marketplace/executions?limit=30');
    const recovery = endpoint('/api/v2/merchant-self-service/marketplace/recovery-events?limit=30');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Create subaccount</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/marketplace/subaccounts', { method: 'POST', body: JSON.stringify(subaccountForm) }), 'Subaccount created.');
          }}>
            <Field label="Display name"><input required value={subaccountForm.displayName} onChange={(e) => setSubaccountForm({ ...subaccountForm, displayName: e.target.value })} /></Field>
            <div className="cito-platform__form-grid">
              <Field label="Currency"><input value={subaccountForm.currencyCode} onChange={(e) => setSubaccountForm({ ...subaccountForm, currencyCode: e.target.value })} /></Field>
              <Field label="Destination type"><input value={subaccountForm.destinationType} onChange={(e) => setSubaccountForm({ ...subaccountForm, destinationType: e.target.value })} /></Field>
            </div>
            <Field label="Destination reference"><input required value={subaccountForm.destinationReference} onChange={(e) => setSubaccountForm({ ...subaccountForm, destinationReference: e.target.value })} /></Field>
            <Button type="submit" variant="primary">Create subaccount</Button>
          </form>
        </div>
        <div className="cito-platform__panel">
          <h4>Simulate split</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/marketplace/split-rules/simulate', { method: 'POST', body: JSON.stringify(splitForm) }), 'Split simulation completed without creating an execution.');
          }}>
            <Field label="Split rule reference"><input required value={splitForm.splitRuleReference} onChange={(e) => setSplitForm({ ...splitForm, splitRuleReference: e.target.value })} /></Field>
            <div className="cito-platform__form-grid">
              <Field label="Gross amount"><input value={splitForm.grossAmount} onChange={(e) => setSplitForm({ ...splitForm, grossAmount: e.target.value })} /></Field>
              <Field label="Currency"><input value={splitForm.currencyCode} onChange={(e) => setSplitForm({ ...splitForm, currencyCode: e.target.value })} /></Field>
            </div>
            <Button type="submit" variant="primary">Preview allocation</Button>
          </form>
        </div>
        <ListPanel title="Subaccounts" rows={subaccounts} />
        <ListPanel title="Split rules" rows={rules} />
        <ListPanel title="Split executions" rows={executions} />
        <ListPanel title="Recovery events" rows={recovery} />
      </div>
    );
  }

  function renderRefunds(): React.ReactElement {
    const refunds = endpoint('/api/v2/merchant-self-service/refunds?limit=30');
    const disputes = endpoint('/api/v2/merchant-self-service/disputes?limit=30');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Request refund</h4>
          <p>Leave amount empty to refund the remaining eligible balance.</p>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/refunds', { method: 'POST', body: JSON.stringify(refundForm) }), 'Refund request submitted.');
          }}>
            <Field label="Original transaction reference"><input required value={refundForm.originalReference} onChange={(e) => setRefundForm({ ...refundForm, originalReference: e.target.value })} /></Field>
            <Field label="Refund reference"><input required value={refundForm.reference} onChange={(e) => setRefundForm({ ...refundForm, reference: e.target.value })} /></Field>
            <Field label="Amount"><input value={refundForm.amount} onChange={(e) => setRefundForm({ ...refundForm, amount: e.target.value })} /></Field>
            <Field label="Reason"><textarea value={refundForm.reason} onChange={(e) => setRefundForm({ ...refundForm, reason: e.target.value })} /></Field>
            <Button type="submit" variant="primary">Request refund</Button>
          </form>
        </div>
        <ListPanel title="Refunds" rows={refunds} />
        <ListPanel title="Disputes" rows={disputes} />
      </div>
    );
  }

  function renderRecurring(): React.ReactElement {
    const plans = endpoint('/api/v2/merchant-self-service/recurring/plans');
    const mandates = endpoint('/api/v2/merchant-self-service/recurring/mandates');
    const subscriptions = endpoint('/api/v2/merchant-self-service/recurring/subscriptions');
    const charges = endpoint('/api/v2/merchant-self-service/recurring/charges?limit=30');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Create plan</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/recurring/plans', {
              method: 'POST',
              body: JSON.stringify({ ...planForm, intervalCount: Number(planForm.intervalCount), retryCount: 2, retryIntervalHours: 24, gracePeriodDays: 3 }),
            }), 'Recurring plan created.');
          }}>
            <Field label="Plan name"><input required value={planForm.planName} onChange={(e) => setPlanForm({ ...planForm, planName: e.target.value })} /></Field>
            <div className="cito-platform__form-grid">
              <Field label="Amount"><input required value={planForm.amount} onChange={(e) => setPlanForm({ ...planForm, amount: e.target.value })} /></Field>
              <Field label="Currency"><input value={planForm.currencyCode} onChange={(e) => setPlanForm({ ...planForm, currencyCode: e.target.value })} /></Field>
              <Field label="Interval"><select value={planForm.intervalUnit} onChange={(e) => setPlanForm({ ...planForm, intervalUnit: e.target.value })}><option>DAY</option><option>WEEK</option><option>MONTH</option></select></Field>
            </div>
            <Button type="submit" variant="primary">Create plan</Button>
          </form>
        </div>
        <ListPanel title="Plans" rows={plans} />
        <ListPanel title="Mandates" rows={mandates} />
        <ListPanel title="Subscriptions" rows={subscriptions} />
        <ListPanel title="Scheduled charges" rows={charges} />
      </div>
    );
  }

  function renderAnalytics(): React.ReactElement {
    const daily = endpoint('/api/v2/merchant-self-service/analytics/daily');
    const providers = endpoint('/api/v2/merchant-self-service/analytics/providers');
    const recommendations = endpoint('/api/v2/merchant-self-service/analytics/recommendations');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Refresh intelligence</h4>
          <p>Rebuild the recent merchant analytics window and operational recommendations.</p>
          <Button variant="primary" onClick={() => void runAction(() => requestJson('/api/v2/merchant-self-service/analytics/refresh', { method: 'POST', body: '{}' }), 'Analytics refreshed.')}>Refresh analytics</Button>
        </div>
        <ListPanel title="Daily metrics" rows={daily} />
        <ListPanel title="Provider performance" rows={providers} />
        <ListPanel title="Recommendations" rows={recommendations} />
      </div>
    );
  }

  function renderDeveloper(): React.ReactElement {
    const projects = endpoint('/api/v2/merchant-self-service/developer/projects');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Create developer project</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/developer/projects', { method: 'POST', body: JSON.stringify(projectForm) }), 'Developer project created with sandbox access.');
          }}>
            <Field label="Project name"><input required value={projectForm.projectName} onChange={(e) => setProjectForm({ ...projectForm, projectName: e.target.value })} /></Field>
            <Field label="Description"><textarea value={projectForm.description} onChange={(e) => setProjectForm({ ...projectForm, description: e.target.value })} /></Field>
            <Button type="submit" variant="primary">Create project</Button>
          </form>
        </div>
        <ListPanel title="Developer projects" rows={projects} />
      </div>
    );
  }

  function renderVirtual(): React.ReactElement {
    const accounts = endpoint('/api/v2/merchant-self-service/virtual-accounts');
    const transfers = endpoint('/api/v2/merchant-self-service/virtual-accounts/transfers?limit=30');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Issue virtual account</h4>
          <p>Sandbox issuance is internal. Production requires a certified configured bank/provider.</p>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/virtual-accounts', {
              method: 'POST',
              body: JSON.stringify({ ...virtualForm, environment, accountType: 'TEMPORARY' }),
            }), 'Virtual account issued.');
          }}>
            <Field label="Account name"><input required value={virtualForm.accountName} onChange={(e) => setVirtualForm({ ...virtualForm, accountName: e.target.value })} /></Field>
            <div className="cito-platform__form-grid">
              <Field label="Customer reference"><input value={virtualForm.customerReference} onChange={(e) => setVirtualForm({ ...virtualForm, customerReference: e.target.value })} /></Field>
              <Field label="Purpose reference"><input value={virtualForm.purposeReference} onChange={(e) => setVirtualForm({ ...virtualForm, purposeReference: e.target.value })} /></Field>
            </div>
            <Button type="submit" variant="primary">Issue {environment.toLowerCase()} account</Button>
          </form>
        </div>
        <ListPanel title="Virtual accounts" rows={accounts} />
        <ListPanel title="Incoming transfers" rows={transfers} />
      </div>
    );
  }

  function renderEmbedded(): React.ReactElement {
    const downstream = endpoint('/api/v2/merchant-self-service/embedded/downstream-merchants');
    const delegations = endpoint('/api/v2/merchant-self-service/embedded/delegations');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Enable Embedded Cito partner profile</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/embedded/partner', { method: 'POST', body: JSON.stringify(partnerForm) }), 'Embedded Cito partner profile is ready.');
          }}>
            <Field label="Partner name"><input required value={partnerForm.partnerName} onChange={(e) => setPartnerForm({ partnerName: e.target.value })} /></Field>
            <Button type="submit" variant="primary">Create / load partner profile</Button>
          </form>
        </div>
        <ListPanel title="Downstream merchants" rows={downstream} />
        <ListPanel title="Service delegations" rows={delegations} />
      </div>
    );
  }

  function renderIntegrations(): React.ReactElement {
    const catalog = endpoint('/api/v2/merchant-self-service/integrations/catalog');
    const installations = endpoint('/api/v2/merchant-self-service/integrations/installations');
    return (
      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Install connector</h4>
          <form className="cito-platform__form" onSubmit={(event) => {
            event.preventDefault();
            void runAction(() => requestJson('/api/v2/merchant-self-service/integrations/installations', {
              method: 'POST',
              body: JSON.stringify({ ...installForm, environment, credentialReference: '' }),
            }), 'Connector installed.');
          }}>
            <div className="cito-platform__form-grid">
              <Field label="Connector code"><input value={installForm.connectorCode} onChange={(e) => setInstallForm({ ...installForm, connectorCode: e.target.value })} /></Field>
              <Field label="Version"><input value={installForm.versionNumber} onChange={(e) => setInstallForm({ ...installForm, versionNumber: e.target.value })} /></Field>
            </div>
            <Field label="Display name"><input value={installForm.displayName} onChange={(e) => setInstallForm({ ...installForm, displayName: e.target.value })} /></Field>
            <Field label="Configuration JSON"><textarea value={installForm.configurationJson} onChange={(e) => setInstallForm({ ...installForm, configurationJson: e.target.value })} /></Field>
            <Button type="submit" variant="primary">Install in {environment.toLowerCase()}</Button>
          </form>
        </div>
        <ListPanel title="Connector catalogue" rows={catalog} />
        <ListPanel title="Installed connectors" rows={installations} />
      </div>
    );
  }

  function renderTab(): React.ReactElement {
    switch (tab) {
      case 'routing': return renderRouting();
      case 'marketplace': return renderMarketplace();
      case 'refunds': return renderRefunds();
      case 'recurring': return renderRecurring();
      case 'analytics': return renderAnalytics();
      case 'developer': return renderDeveloper();
      case 'virtual': return renderVirtual();
      case 'embedded': return renderEmbedded();
      case 'integrations': return renderIntegrations();
      case 'services':
      default: return renderServices();
    }
  }

  const summaryPairs: Array<[string, unknown]> = [
    ['Routing decisions', (overview?.routing as Record<string, unknown> | undefined)?.decisions],
    ['Open disputes', (overview?.refunds as Record<string, unknown> | undefined)?.openDisputes],
    ['Split recovery pending', (overview?.marketplace as Record<string, unknown> | undefined)?.pendingRecoveryEvents],
    ['Active subscriptions', (overview?.recurring as Record<string, unknown> | undefined)?.activeSubscriptions],
    ['Developer projects', (overview?.developer as Record<string, unknown> | undefined)?.activeProjects],
    ['Installed integrations', (overview?.integrations as Record<string, unknown> | undefined)?.activeInstallations],
  ];

  return (
    <div className="cito-platform">
      <Card>
        <div className="cito-platform__hero">
          <div>
            <h2>Cito Services</h2>
            <p>One workspace for Cito entitlements, payment intelligence and merchant platform capabilities.</p>
          </div>
          <div className="cito-platform__environment">
            <span className="cito-platform__muted">Environment</span>
            <select value={environment} onChange={(e) => setEnvironment(e.target.value as Environment)} aria-label="Cito environment">
              <option value="SANDBOX">Sandbox</option>
              <option value="PRODUCTION">Production</option>
            </select>
            <Button variant="ghost" onClick={() => void refresh()}>Refresh</Button>
          </div>
        </div>
      </Card>

      {error ? <Alert variant="error">{error}</Alert> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}
      {loading && !overview ? <Spinner label="Loading Cito services" /> : null}

      {overview ? (
        <div className="cito-platform__metrics">
          {summaryPairs.map(([label, value]) => (
            <div className="cito-platform__metric" key={label}>
              <div className="cito-platform__metric-head"><span className="cito-platform__muted">{label}</span></div>
              <strong>{text(value || 0)}</strong>
            </div>
          ))}
        </div>
      ) : null}

      <Card>
        <div className="cito-platform__tabs" role="tablist" aria-label="Cito services">
          {tabs.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              className="cito-platform__tab"
              onClick={() => { setNotice(''); setError(''); setTab(item.key); }}
            >
              {item.label}
            </button>
          ))}
        </div>
      </Card>

      {renderTab()}
    </div>
  );
}
