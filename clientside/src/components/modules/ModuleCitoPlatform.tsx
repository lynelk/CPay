import React from 'react';
import { Alert, Badge, Button, Card, Spinner, Table } from '../../ui';
import type { Column } from '../../ui';
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
import '../../styles/cito-platform.css';

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unable to load Cito control plane.';
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

const entitlementColumns: Column<CitoEntitlementRow>[] = [
  {
    key: 'service',
    header: 'Service',
    accessor: (row) => row.serviceName ?? row.serviceCode ?? '',
  },
  { key: 'environment', header: 'Environment', accessor: (row) => row.environment ?? '' },
  {
    key: 'status',
    header: 'Status',
    render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? 'UNKNOWN'}</Badge>,
  },
  { key: 'plan', header: 'Plan', accessor: (row) => row.planCode ?? 'No plan' },
];

const catalogColumns: Column<CitoServiceCatalogRow>[] = [
  { key: 'service', header: 'Service', accessor: (row) => row.serviceName ?? row.serviceCode ?? '' },
  { key: 'code', header: 'Code', accessor: (row) => row.serviceCode ?? '' },
  { key: 'description', header: 'Description', accessor: (row) => row.description ?? '' },
];

const eventColumns: Column<CitoAccessEventRow>[] = [
  {
    key: 'event',
    header: 'Event',
    accessor: (row) => row.eventType ?? row.action ?? 'Access change',
  },
  {
    key: 'service',
    header: 'Service / detail',
    accessor: (row) => row.serviceCode ?? row.detail ?? '',
  },
  {
    key: 'status',
    header: 'Status',
    render: (row) =>
      row.status ? <Badge tone={statusTone(row.status)}>{row.status}</Badge> : <span>—</span>,
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
  const [merchantId, setMerchantId] = React.useState('');
  const [notice, setNotice] = React.useState('');
  const [form, setForm] = React.useState<{
    serviceCode: string;
    environment: CitoEnvironment;
    status: string;
    planCode: string;
  }>({
    serviceCode: 'CPAY',
    environment: 'SANDBOX',
    status: 'ACTIVE',
    planCode: 'STANDARD',
  });

  const catalogQuery = useCitoServiceCatalog();
  const entitlementsQuery = useCitoMerchantEntitlements(merchantId);
  const eventsQuery = useCitoAccessEvents(merchantId, 50);
  const entitlementMutation = useSetCitoEntitlementMutation();

  const busy =
    catalogQuery.isFetching ||
    entitlementsQuery.isFetching ||
    eventsQuery.isFetching ||
    entitlementMutation.isPending;
  useLoaderSync(loader, busy);
  useRefreshSignal(refreshSignal, [
    catalogQuery.refetch,
    entitlementsQuery.refetch,
    eventsQuery.refetch,
  ]);

  const error = firstQueryError(
    catalogQuery,
    entitlementsQuery,
    eventsQuery,
    entitlementMutation,
  );

  React.useEffect(() => {
    if (error instanceof ApiError && error.status === 401) sessionExpired?.();
  }, [error, sessionExpired]);

  const catalog = catalogQuery.data ?? [];
  const entitlements = merchantId.trim() ? (entitlementsQuery.data ?? []) : [];
  const events = merchantId.trim() ? (eventsQuery.data ?? []) : [];

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
      {
        merchantId: merchant,
        ...form,
        actor: 'ADMIN_PORTAL',
      },
      {
        onSuccess: () => {
          setNotice(`${form.serviceCode} ${form.environment.toLowerCase()} entitlement updated.`);
        },
      },
    );
  }

  return (
    <div className="cito-platform">
      <Card>
        <div className="cito-platform__hero">
          <div>
            <h2>Cito Control Plane</h2>
            <p>Administer merchant product entitlements and review access changes across Cito.</p>
          </div>
          <div className="cito-platform__environment">
            <input
              aria-label="Merchant ID"
              placeholder="Merchant ID"
              inputMode="numeric"
              value={merchantId}
              onChange={(event) => {
                setMerchantId(event.target.value.replace(/[^0-9]/g, ''));
                setNotice('');
              }}
            />
            <Button variant="primary" onClick={refreshMerchant} disabled={!merchantId.trim()}>
              Load merchant
            </Button>
          </div>
        </div>
      </Card>

      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}
      {catalogQuery.isLoading ? <Spinner label="Loading Cito control plane" /> : null}

      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Set entitlement</h4>
          <form className="cito-platform__form" onSubmit={setEntitlement}>
            <div className="cito-platform__field">
              <label htmlFor="cito-service-code">Service</label>
              <select
                id="cito-service-code"
                value={form.serviceCode}
                onChange={(event) => setForm({ ...form, serviceCode: event.target.value })}
              >
                {catalog.map((row) => (
                  <option key={row.serviceCode} value={row.serviceCode}>
                    {row.serviceName ?? row.serviceCode}
                  </option>
                ))}
              </select>
            </div>
            <div className="cito-platform__form-grid">
              <div className="cito-platform__field">
                <label htmlFor="cito-environment">Environment</label>
                <select
                  id="cito-environment"
                  value={form.environment}
                  onChange={(event) =>
                    setForm({ ...form, environment: event.target.value as CitoEnvironment })
                  }
                >
                  <option value="SANDBOX">Sandbox</option>
                  <option value="PRODUCTION">Production</option>
                </select>
              </div>
              <div className="cito-platform__field">
                <label htmlFor="cito-entitlement-status">Status</label>
                <select
                  id="cito-entitlement-status"
                  value={form.status}
                  onChange={(event) => setForm({ ...form, status: event.target.value })}
                >
                  <option value="ACTIVE">Active</option>
                  <option value="REQUESTED">Requested</option>
                  <option value="SUSPENDED">Suspended</option>
                  <option value="REVOKED">Revoked</option>
                </select>
              </div>
            </div>
            <div className="cito-platform__field">
              <label htmlFor="cito-plan-code">Plan code</label>
              <input
                id="cito-plan-code"
                value={form.planCode}
                onChange={(event) => setForm({ ...form, planCode: event.target.value })}
              />
            </div>
            <Button type="submit" variant="primary" disabled={!merchantId.trim() || entitlementMutation.isPending}>
              Apply entitlement
            </Button>
          </form>
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row">
            <h4>Merchant entitlements</h4>
            <Badge tone="neutral">{entitlements.length}</Badge>
          </div>
          <Table
            columns={entitlementColumns}
            rows={entitlements}
            rowKey={(row, index) => `${row.serviceCode ?? index}-${row.environment ?? ''}`}
            emptyText={merchantId.trim() ? 'No entitlements configured.' : 'Load a merchant to inspect entitlements.'}
          />
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row">
            <h4>Service catalogue</h4>
            <Badge tone="neutral">{catalog.length}</Badge>
          </div>
          <Table
            columns={catalogColumns}
            rows={catalog}
            rowKey={(row, index) => row.serviceCode ?? index}
            emptyText="No Cito services configured."
          />
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row">
            <h4>Access events</h4>
            <Badge tone="neutral">{events.length}</Badge>
          </div>
          <Table
            columns={eventColumns}
            rows={events.slice(0, 30)}
            rowKey={(row, index) => row.eventReference ?? row.id ?? index}
            emptyText={merchantId.trim() ? 'No access events recorded.' : 'Load a merchant to inspect access events.'}
            pageSize={15}
          />
        </div>
      </div>
    </div>
  );
}
