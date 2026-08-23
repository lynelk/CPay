import React from 'react';
import { Alert, Badge, Button, Card, Spinner } from '../../ui';
import { apiFetch } from '../../shared/api/httpClient';
import { apiUrl } from '../../shared/config';
import '../../styles/cito-platform.css';

interface Props {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

type Row = Record<string, unknown>;

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function asList(value: unknown): Row[] {
  return Array.isArray(value) ? (value as Row[]) : [];
}

function statusTone(value: unknown): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  const status = text(value).toUpperCase();
  if (status === 'ACTIVE' || status === 'APPROVED' || status === 'COMPLETED') return 'success';
  if (status === 'SUSPENDED' || status === 'REVOKED' || status === 'REJECTED') return 'danger';
  if (status.includes('PENDING') || status === 'REQUESTED') return 'warning';
  return 'info';
}

export default function ModuleCitoPlatform({ loader, refreshSignal, sessionExpired }: Props): React.ReactElement {
  const [merchantId, setMerchantId] = React.useState('');
  const [catalog, setCatalog] = React.useState<Row[]>([]);
  const [entitlements, setEntitlements] = React.useState<Row[]>([]);
  const [events, setEvents] = React.useState<Row[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [notice, setNotice] = React.useState('');
  const [form, setForm] = React.useState({ serviceCode: 'CPAY', environment: 'SANDBOX', status: 'ACTIVE', planCode: 'STANDARD' });

  const request = React.useCallback(async (path: string, init?: RequestInit): Promise<unknown> => {
    const response = await apiFetch(apiUrl(path), {
      method: init?.method ?? 'GET',
      credentials: 'include',
      cache: 'no-cache',
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
      body: init?.body,
    });
    if (response.status === 401) {
      sessionExpired?.();
      throw new Error('Administrator session expired.');
    }
    const raw = await response.text();
    const payload = raw.trim() ? JSON.parse(raw) : {};
    if (!response.ok) throw new Error(text((payload as Row).message) || `Request failed (${response.status})`);
    return payload;
  }, [sessionExpired]);

  const loadCatalog = React.useCallback(async () => {
    setCatalog(asList(await request('/api/v2/admin/cito/service-catalog')));
  }, [request]);

  const loadMerchant = React.useCallback(async () => {
    if (!merchantId.trim()) {
      setEntitlements([]);
      setEvents([]);
      return;
    }
    const [entitlementRows, eventRows] = await Promise.all([
      request(`/api/v2/admin/cito/entitlements?merchantId=${encodeURIComponent(merchantId)}`),
      request(`/api/v2/admin/cito/access-events?merchantId=${encodeURIComponent(merchantId)}&limit=50`),
    ]);
    setEntitlements(asList(entitlementRows));
    setEvents(asList(eventRows));
  }, [merchantId, request]);

  const refresh = React.useCallback(async () => {
    setLoading(true);
    loader?.('START');
    setError('');
    try {
      await Promise.all([loadCatalog(), loadMerchant()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load Cito control plane.');
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
  }, [loadCatalog, loadMerchant, loader]);

  React.useEffect(() => { void refresh(); }, []);
  React.useEffect(() => {
    if (refreshSignal !== undefined) void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSignal]);

  async function setEntitlement(event: React.FormEvent) {
    event.preventDefault();
    if (!merchantId.trim()) {
      setError('Enter a merchant ID first.');
      return;
    }
    setLoading(true);
    loader?.('START');
    setError('');
    setNotice('');
    try {
      await request('/api/v2/admin/cito/entitlements', {
        method: 'POST',
        body: JSON.stringify({ merchantId: Number(merchantId), ...form, actor: 'ADMIN_PORTAL' }),
      });
      setNotice(`${form.serviceCode} ${form.environment.toLowerCase()} entitlement updated.`);
      await loadMerchant();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update entitlement.');
    } finally {
      setLoading(false);
      loader?.('STOP');
    }
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
              value={merchantId}
              onChange={(e) => setMerchantId(e.target.value.replace(/[^0-9]/g, ''))}
            />
            <Button variant="primary" onClick={() => void refresh()}>Load merchant</Button>
          </div>
        </div>
      </Card>

      {error ? <Alert variant="error">{error}</Alert> : null}
      {notice ? <Alert variant="success">{notice}</Alert> : null}
      {loading && catalog.length === 0 ? <Spinner label="Loading Cito control plane" /> : null}

      <div className="cito-platform__grid">
        <div className="cito-platform__panel">
          <h4>Set entitlement</h4>
          <form className="cito-platform__form" onSubmit={setEntitlement}>
            <div className="cito-platform__field">
              <label>Service</label>
              <select value={form.serviceCode} onChange={(e) => setForm({ ...form, serviceCode: e.target.value })}>
                {catalog.map((row) => <option key={text(row.serviceCode)} value={text(row.serviceCode)}>{text(row.serviceName || row.serviceCode)}</option>)}
              </select>
            </div>
            <div className="cito-platform__form-grid">
              <div className="cito-platform__field">
                <label>Environment</label>
                <select value={form.environment} onChange={(e) => setForm({ ...form, environment: e.target.value })}>
                  <option value="SANDBOX">Sandbox</option>
                  <option value="PRODUCTION">Production</option>
                </select>
              </div>
              <div className="cito-platform__field">
                <label>Status</label>
                <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                  <option value="ACTIVE">Active</option>
                  <option value="REQUESTED">Requested</option>
                  <option value="SUSPENDED">Suspended</option>
                  <option value="REVOKED">Revoked</option>
                </select>
              </div>
            </div>
            <div className="cito-platform__field">
              <label>Plan code</label>
              <input value={form.planCode} onChange={(e) => setForm({ ...form, planCode: e.target.value })} />
            </div>
            <Button type="submit" variant="primary">Apply entitlement</Button>
          </form>
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row"><h4>Merchant entitlements</h4><Badge tone="neutral">{entitlements.length}</Badge></div>
          {entitlements.length === 0 ? <p className="cito-platform__muted">Load a merchant to inspect entitlements.</p> : (
            <ul className="cito-platform__list">
              {entitlements.map((row, index) => (
                <li key={`${text(row.serviceCode)}-${text(row.environment)}-${index}`}>
                  <div className="cito-platform__row">
                    <strong>{text(row.serviceName || row.serviceCode)}</strong>
                    <Badge tone={statusTone(row.status)}>{text(row.status)}</Badge>
                  </div>
                  <div className="cito-platform__muted">{text(row.environment)} · {text(row.planCode || 'No plan')}</div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row"><h4>Service catalogue</h4><Badge tone="neutral">{catalog.length}</Badge></div>
          <ul className="cito-platform__list">
            {catalog.map((row, index) => (
              <li key={`${text(row.serviceCode)}-${index}`}>
                <strong>{text(row.serviceName || row.serviceCode)}</strong>
                <div className="cito-platform__muted cito-platform__code">{text(row.serviceCode)}</div>
              </li>
            ))}
          </ul>
        </div>

        <div className="cito-platform__panel">
          <div className="cito-platform__row"><h4>Access events</h4><Badge tone="neutral">{events.length}</Badge></div>
          {events.length === 0 ? <p className="cito-platform__muted">No events loaded.</p> : (
            <ul className="cito-platform__list">
              {events.slice(0, 30).map((row, index) => (
                <li key={`${text(row.eventReference || row.id)}-${index}`}>
                  <div className="cito-platform__row">
                    <strong>{text(row.eventType || row.action || 'Access change')}</strong>
                    {row.status ? <Badge tone={statusTone(row.status)}>{text(row.status)}</Badge> : null}
                  </div>
                  <div className="cito-platform__muted">{text(row.serviceCode || row.detail || row.createdAt)}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
