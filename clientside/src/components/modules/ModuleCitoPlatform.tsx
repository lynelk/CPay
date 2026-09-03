import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Badge,
  Button,
  Spinner,
  Table,
  Tabs,
  TextField,
} from '../../ui';
import type { Column, TabItem } from '../../ui';
import { ApiError } from '../../shared/api/httpClient';
import { firstQueryError, useLoaderSync, useRefreshSignal } from '../../shared/api/hooks';
import {
  useCitoAccessEvents,
  useCitoMerchantEntitlements,
  useCitoServiceCatalog,
  useSetCitoEntitlementMutation,
} from '../../shared/api/citoHooks';
import type {
  CitoAccessEventRow,
  CitoEntitlementRow,
  CitoEnvironment,
  CitoServiceCatalogRow,
} from '../../shared/api/citoHooks';

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

type WorkspaceView = 'services' | 'access' | 'activity';

type ServiceFamily = {
  code: string;
  mark: string;
  title: string;
  description: string;
  capabilities: string[];
  catalogHints: string[];
  route: string;
  action: string;
};

const workspaceTabs: TabItem[] = [
  { key: 'services', label: 'Service families' },
  { key: 'access', label: 'Merchant access' },
  { key: 'activity', label: 'Access activity' },
];

const serviceFamilies: ServiceFamily[] = [
  {
    code: 'payments',
    mark: 'P',
    title: 'Payments',
    description: 'Collections, payouts, refunds, reconciliation and settlement through Cito Payments / CPay orchestration.',
    capabilities: ['CPay', 'MTN MoMo', 'Airtel Money', 'Yo! Payments', 'FlexiPay', 'M-Pesa'],
    catalogHints: ['CPAY', 'PAYMENT', 'PAYOUT', 'COLLECTION'],
    route: '/bo/admin/money-operations',
    action: 'Open money operations',
  },
  {
    code: 'communications',
    mark: 'C',
    title: 'Communications',
    description: 'Customer and operational messaging with channel-aware routing, provider failover, delivery evidence and billing.',
    capabilities: ['SMS', 'WhatsApp Business', 'USSD', 'Routing', 'Failover', 'Delivery logs'],
    catalogHints: ['SMS', 'WHATSAPP', 'USSD', 'COMMUNICATION', 'MESSAGE'],
    route: '/bo/admin/communicationrouting',
    action: 'Open communications',
  },
  {
    code: 'identity-credit',
    mark: 'I',
    title: 'Identity, Credit & Scoring',
    description: 'Identity verification and credit intelligence delivered through approved providers, with raw evidence retained alongside normalized results.',
    capabilities: ['NIN verification', 'KYC / KYB', 'CRB reports', '0–1000 scoring', 'Bank verification', 'TIN / registry'],
    catalogHints: ['KYC', 'KYB', 'CRB', 'SCORE', 'SCORING', 'IDENTITY', 'NIN', 'CREDIT'],
    route: '/bo/admin/risk-compliance',
    action: 'Open identity & risk',
  },
  {
    code: 'vending',
    mark: 'V',
    title: 'Vending & Value-Added Services',
    description: 'A unified vending layer for airtime, data, utilities, QR/device journeys and manufacturer or service-provider integrations.',
    capabilities: ['Airtime', 'Data', 'Utilities', 'Devices', 'QR journeys', 'Provider callbacks'],
    catalogHints: ['VENDING', 'AIRTIME', 'UTILITY', 'DATA_BUNDLE', 'DEVICE'],
    route: '/bo/admin/vending',
    action: 'Open vending',
  },
  {
    code: 'billing',
    mark: 'B',
    title: 'Billing & Monetisation',
    description: 'Usage metering, rating, invoicing and Billing-as-a-Service with effective-dated pricing, tax and FX evidence.',
    capabilities: ['Metering', 'Rating', 'BaaS', 'Invoices', 'Usage pricing', 'Tax & FX'],
    catalogHints: ['BILLING', 'BAAS', 'INVOICE', 'METERING', 'RATING'],
    route: '/bo/admin/platform',
    action: 'Manage service access',
  },
  {
    code: 'integrations',
    mark: 'A',
    title: 'Integrations & Automation',
    description: 'Provider adapters, APIs, webhooks, routing, certification and automation that connect Cito to merchant and partner systems.',
    capabilities: ['APIs', 'Webhooks', 'Provider adapters', 'Routing', 'Certification', 'Automation'],
    catalogHints: ['API', 'WEBHOOK', 'INTEGRATION', 'CONNECTOR', 'ROUTING'],
    route: '/bo/admin/providers-integrations',
    action: 'Open integrations',
  },
];

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function humanizeError(error: unknown): string {
  const message = error instanceof ApiError
    ? error.message
    : error instanceof Error
      ? error.message
      : 'Unable to load live service configuration.';
  if (/internal application error|internal server error|something went wrong/i.test(message)) {
    return 'Cito could not load one or more live service records. The service workspace remains available and no fallback data has been substituted.';
  }
  return message;
}

function statusTone(value: unknown): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  const status = text(value).toUpperCase();
  if (status === 'ACTIVE' || status === 'APPROVED' || status === 'COMPLETED') return 'success';
  if (status === 'SUSPENDED' || status === 'REVOKED' || status === 'REJECTED') return 'danger';
  if (status.includes('PENDING') || status === 'REQUESTED') return 'warning';
  return 'info';
}

function formatDate(value: unknown): string {
  const raw = text(value);
  if (!raw) return '';
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleString();
}

function familyIsCatalogued(family: ServiceFamily, catalog: CitoServiceCatalogRow[]): boolean {
  return catalog.some((row) => {
    const source = `${row.serviceCode ?? ''} ${row.serviceName ?? ''} ${row.description ?? ''}`.toUpperCase();
    return family.catalogHints.some((hint) => source.includes(hint));
  });
}

const entitlementColumns: Column<CitoEntitlementRow>[] = [
  { key: 'service', header: 'Service', accessor: (row) => row.serviceName ?? row.serviceCode ?? '' },
  { key: 'environment', header: 'Environment', accessor: (row) => row.environment ?? '' },
  {
    key: 'status',
    header: 'Status',
    render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? 'UNKNOWN'}</Badge>,
  },
  { key: 'plan', header: 'Plan', accessor: (row) => row.planCode ?? 'No plan' },
];

const eventColumns: Column<CitoAccessEventRow>[] = [
  { key: 'event', header: 'Event', accessor: (row) => row.eventType ?? row.action ?? 'Access change' },
  { key: 'service', header: 'Service / detail', accessor: (row) => row.serviceCode ?? row.detail ?? '' },
  {
    key: 'status',
    header: 'Status',
    render: (row) => row.status ? <Badge tone={statusTone(row.status)}>{row.status}</Badge> : <span>—</span>,
  },
  {
    key: 'created',
    header: 'Created',
    accessor: (row) => formatDate(row.createdAt),
    sortable: true,
    sortValue: (row) => text(row.createdAt),
  },
];

export default function ModuleCitoPlatform({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const navigate = useNavigate();
  const [view, setView] = React.useState<WorkspaceView>('services');
  const [merchantId, setMerchantId] = React.useState('');
  const [notice, setNotice] = React.useState('');
  const [form, setForm] = React.useState<{
    serviceCode: string;
    environment: CitoEnvironment;
    status: string;
    planCode: string;
  }>({ serviceCode: 'CPAY', environment: 'SANDBOX', status: 'ACTIVE', planCode: 'STANDARD' });

  const catalogQuery = useCitoServiceCatalog();
  const entitlementsQuery = useCitoMerchantEntitlements(merchantId);
  const eventsQuery = useCitoAccessEvents(merchantId, 50);
  const entitlementMutation = useSetCitoEntitlementMutation();

  const busy = catalogQuery.isFetching || entitlementsQuery.isFetching || eventsQuery.isFetching || entitlementMutation.isPending;
  useLoaderSync(loader, busy);
  useRefreshSignal(refreshSignal, [catalogQuery.refetch, entitlementsQuery.refetch, eventsQuery.refetch]);

  const error = firstQueryError(catalogQuery, entitlementsQuery, eventsQuery, entitlementMutation);
  React.useEffect(() => {
    if (error instanceof ApiError && error.status === 401) sessionExpired?.();
  }, [error, sessionExpired]);

  const catalog = catalogQuery.data ?? [];
  const entitlements = merchantId.trim() ? (entitlementsQuery.data ?? []) : [];
  const events = merchantId.trim() ? (eventsQuery.data ?? []) : [];
  const cataloguedFamilies = serviceFamilies.filter((family) => familyIsCatalogued(family, catalog)).length;

  React.useEffect(() => {
    if (!catalog.length) return;
    if (!catalog.some((row) => row.serviceCode === form.serviceCode)) {
      setForm((current) => ({ ...current, serviceCode: catalog[0].serviceCode ?? current.serviceCode }));
    }
  }, [catalog, form.serviceCode]);

  function refreshMerchant(): void {
    setNotice('');
    void Promise.all([entitlementsQuery.refetch(), eventsQuery.refetch()]);
  }

  function setEntitlement(event: React.FormEvent): void {
    event.preventDefault();
    const merchant = Number(merchantId);
    if (!merchantId.trim() || !Number.isSafeInteger(merchant) || merchant <= 0) return;
    setNotice('');
    entitlementMutation.mutate(
      { merchantId: merchant, ...form, actor: 'ADMIN_PORTAL' },
      { onSuccess: () => setNotice(`${form.serviceCode} ${form.environment.toLowerCase()} entitlement updated.`) },
    );
  }

  function renderServices(): React.ReactElement {
    return (
      <>
        <div className="cito-service-grid" aria-label="Cito service families">
          {serviceFamilies.map((family) => {
            const catalogued = familyIsCatalogued(family, catalog);
            return (
              <article className="cito-service-card" key={family.code}>
                <div className="cito-service-card__top">
                  <span className="cito-service-card__mark" aria-hidden="true">{family.mark}</span>
                  <Badge tone={catalogued ? 'success' : 'neutral'}>
                    {catalogued ? 'In service catalogue' : 'Provider / catalogue setup'}
                  </Badge>
                </div>
                <h3>{family.title}</h3>
                <p>{family.description}</p>
                <div className="cito-service-card__capabilities">
                  {family.capabilities.map((capability) => <span key={capability}>{capability}</span>)}
                </div>
                <div className="cito-service-card__actions">
                  <button className="cito-service-card__link" type="button" onClick={() => navigate(family.route)}>
                    {family.action} →
                  </button>
                </div>
              </article>
            );
          })}
        </div>

        <section className="cito-compliance-panel">
          <div className="cito-section-heading">
            <div>
              <h3>Extensible service catalogue</h3>
              <p>Additional configured services remain available without forcing another top-level navigation item.</p>
            </div>
            <Badge tone="neutral">{catalog.length} configured</Badge>
          </div>
          {catalogQuery.isLoading ? <Spinner label="Loading service catalogue" /> : null}
          {!catalogQuery.isLoading && catalog.length === 0 ? (
            <div className="cito-purpose-empty">
              <div><strong>No service catalogue records are available</strong><p>Service families above describe Cito's product architecture; production availability still depends on explicit provider configuration, certification and merchant entitlement.</p></div>
            </div>
          ) : (
            <div className="cito-service-card__capabilities">
              {catalog.map((row) => <span key={row.serviceCode ?? row.serviceName}>{row.serviceName ?? row.serviceCode}</span>)}
            </div>
          )}
        </section>
      </>
    );
  }

  function renderAccess(): React.ReactElement {
    return (
      <div className="cito-management-panel">
        <section>
          <div className="cito-section-heading">
            <div><h3>Merchant access</h3><p>Grant only the services and environment a merchant is approved to use.</p></div>
          </div>
          <div style={{ display: 'grid', gap: 12 }}>
            <TextField id="cito-merchant-id" label="Merchant ID" inputMode="numeric" value={merchantId} onValueChange={(value) => { setMerchantId(value.replace(/[^0-9]/g, '')); setNotice(''); }} />
            <Button variant="ghost" onClick={refreshMerchant} disabled={!merchantId.trim()}>Load merchant access</Button>
          </div>
          <form className="cito-platform__form" onSubmit={setEntitlement} style={{ marginTop: 18 }}>
            <div className="cito-platform__field">
              <label htmlFor="cito-service-code">Service</label>
              <select id="cito-service-code" value={form.serviceCode} onChange={(event) => setForm({ ...form, serviceCode: event.target.value })}>
                {catalog.map((row) => <option key={row.serviceCode} value={row.serviceCode}>{row.serviceName ?? row.serviceCode}</option>)}
              </select>
            </div>
            <div className="cito-platform__form-grid">
              <div className="cito-platform__field">
                <label htmlFor="cito-environment">Environment</label>
                <select id="cito-environment" value={form.environment} onChange={(event) => setForm({ ...form, environment: event.target.value as CitoEnvironment })}>
                  <option value="SANDBOX">Sandbox</option><option value="PRODUCTION">Production</option>
                </select>
              </div>
              <div className="cito-platform__field">
                <label htmlFor="cito-entitlement-status">Status</label>
                <select id="cito-entitlement-status" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>
                  <option value="ACTIVE">Active</option><option value="REQUESTED">Requested</option><option value="SUSPENDED">Suspended</option><option value="REVOKED">Revoked</option>
                </select>
              </div>
            </div>
            <div className="cito-platform__field"><label htmlFor="cito-plan-code">Plan code</label><input id="cito-plan-code" value={form.planCode} onChange={(event) => setForm({ ...form, planCode: event.target.value })} /></div>
            <Button type="submit" variant="primary" disabled={!merchantId.trim() || entitlementMutation.isPending || !catalog.length}>Apply entitlement</Button>
          </form>
        </section>
        <section>
          <div className="cito-section-heading"><div><h3>Current entitlements</h3><p>One view of what the selected merchant may use.</p></div><Badge tone="neutral">{entitlements.length}</Badge></div>
          <Table columns={entitlementColumns} rows={entitlements} rowKey={(row, index) => `${row.serviceCode ?? index}-${row.environment ?? ''}`} emptyText={merchantId.trim() ? 'No entitlements configured for this merchant.' : 'Enter a merchant ID to inspect access.'} />
        </section>
      </div>
    );
  }

  function renderActivity(): React.ReactElement {
    return (
      <section className="cito-compliance-panel">
        <div className="cito-section-heading">
          <div><h3>Access activity</h3><p>Auditable service-entitlement changes for the selected merchant.</p></div>
          <div style={{ minWidth: 220 }}><TextField id="cito-activity-merchant" label="Merchant ID" inputMode="numeric" value={merchantId} onValueChange={(value) => setMerchantId(value.replace(/[^0-9]/g, ''))} /></div>
        </div>
        <Table columns={eventColumns} rows={events.slice(0, 30)} rowKey={(row, index) => row.eventReference ?? row.id ?? index} emptyText={merchantId.trim() ? 'No access activity recorded.' : 'Enter a merchant ID to inspect access activity.'} pageSize={15} />
      </section>
    );
  }

  return (
    <div className="cito-service-hub">
      <header className="cito-workspace-hero">
        <div>
          <p className="cito-workspace-hero__eyebrow">Cito service portfolio</p>
          <h2>Services & Products</h2>
          <p>Manage the capabilities Cito delivers rather than exposing internal modules. Payments, communications, identity and credit intelligence, vending, billing and integrations are treated as first-class service families.</p>
        </div>
        <div className="cito-workspace-hero__actions">
          <Badge tone={cataloguedFamilies ? 'success' : 'neutral'}>{cataloguedFamilies}/{serviceFamilies.length} families represented in catalogue</Badge>
          <Button variant="ghost" onClick={() => navigate('/bo/admin/providers-integrations')}>Provider readiness</Button>
        </div>
      </header>

      {error ? <div className="cito-inline-error" role="alert"><div><strong>Some live service data is unavailable</strong><p>{humanizeError(error)}</p></div><Button variant="ghost" onClick={() => void catalogQuery.refetch()}>Retry</Button></div> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}

      <Tabs items={workspaceTabs} active={view} onChange={(key) => setView(key as WorkspaceView)} />
      {view === 'services' ? renderServices() : null}
      {view === 'access' ? renderAccess() : null}
      {view === 'activity' ? renderActivity() : null}
    </div>
  );
}
