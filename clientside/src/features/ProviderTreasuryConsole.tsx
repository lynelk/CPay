import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, Section, Table, Toolbar } from '../ui';
import { useAuth } from '../shared/useAuth';
import {
  PlatformCredential,
  SharedProviderEntitlement,
  TreasuryAccount,
  TreasuryAdjustment,
  useApprovePlatformCredential,
  useApproveSharedEntitlement,
  useApproveTreasuryAdjustment,
  useCreateSharedEntitlement,
  useCreateTreasuryAdjustment,
  usePlatformCredentials,
  useReconcileTreasuryAccount,
  useRejectSharedEntitlement,
  useRejectTreasuryAdjustment,
  useSavePlatformCredential,
  useSetLowFloatThreshold,
  useSharedProviderEntitlements,
  useTreasuryAccounts,
  useTreasuryAdjustments,
} from '../shared/api/providerTreasury';

const fieldStyle: React.CSSProperties = {
  minWidth: 150,
  padding: '10px 12px',
  border: '1px solid var(--ios-separator)',
  borderRadius: 10,
  background: 'var(--ios-card)',
};

const gridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: 12,
  alignItems: 'end',
};

function money(value: unknown, currency?: string): string {
  const n = Number(value ?? 0);
  return `${currency ?? ''} ${Number.isFinite(n) ? n.toLocaleString(undefined, { maximumFractionDigits: 2 }) : '0'}`.trim();
}

function statusTone(status?: string): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  const s = (status ?? '').toUpperCase();
  if (s === 'ACTIVE' || s === 'POSTED' || s === 'MATCHED') return 'success';
  if (s === 'PENDING' || s === 'CONFIGURED' || s === 'UNRECONCILED') return 'warning';
  if (s === 'REJECTED' || s === 'DISABLED' || s === 'VARIANCE') return 'danger';
  return 'neutral';
}

export default function ProviderTreasuryConsole(): React.ReactElement {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth('admin');
  const accounts = useTreasuryAccounts();
  const adjustments = useTreasuryAdjustments();
  const entitlements = useSharedProviderEntitlements();
  const credentials = usePlatformCredentials();
  const createAdjustment = useCreateTreasuryAdjustment();
  const approveAdjustment = useApproveTreasuryAdjustment();
  const rejectAdjustment = useRejectTreasuryAdjustment();
  const createEntitlement = useCreateSharedEntitlement();
  const approveEntitlement = useApproveSharedEntitlement();
  const rejectEntitlement = useRejectSharedEntitlement();
  const saveCredential = useSavePlatformCredential();
  const approveCredential = useApprovePlatformCredential();
  const setThreshold = useSetLowFloatThreshold();
  const reconcile = useReconcileTreasuryAccount();

  const [notice, setNotice] = useState('');
  const [adjustment, setAdjustment] = useState({ adjustmentType: 'CREDIT', sourceAccountId: '', destinationAccountId: '', amount: '', reason: '', externalReference: '', evidenceReference: '', valueDate: new Date().toISOString().slice(0, 10) });
  const [entitlement, setEntitlement] = useState({ merchantId: '', channelCode: 'airtel_money', environment: 'PRODUCTION', countryCode: 'UG', currencyCode: 'UGX', operation: 'COLLECT', perTransactionLimit: '', dailyLimit: '', notes: '' });
  const [credential, setCredential] = useState({
    channelCode: 'airtel_money', environment: 'PRODUCTION', countryCode: 'UG', currencyCode: 'UGX',
    collectUrl: '', payoutUrl: '', authHeaderName: '', authHeaderValue: '', tokenAlias: '',
    baseUrl: '', targetEnvironment: '', baseCurrency: 'UGX', callbackHost: '', callbackUrl: '',
    collectionApiUser: '', collectionApiKey: '', collectionSubscriptionKey: '', collectionSecondarySubscriptionKey: '',
    disbursementApiUser: '', disbursementApiKey: '', disbursementSubscriptionKey: '', disbursementSecondarySubscriptionKey: '',
  });

  useEffect(() => {
    if (!isAuthenticated) navigate('/admin');
  }, [isAuthenticated, navigate]);

  const allErrors = [accounts.error, adjustments.error, entitlements.error, credentials.error].filter(Boolean) as Error[];
  useEffect(() => {
    if (allErrors.length) setNotice(allErrors[0].message);
  }, [allErrors.length]);

  const pendingAdjustments = useMemo(() => (adjustments.data ?? []).filter((row) => row.status === 'PENDING'), [adjustments.data]);
  const pendingEntitlements = useMemo(() => (entitlements.data ?? []).filter((row) => row.status === 'PENDING'), [entitlements.data]);

  const submitAdjustment = (event: FormEvent) => {
    event.preventDefault();
    const payload: Record<string, unknown> = {
      adjustmentType: adjustment.adjustmentType,
      amount: adjustment.amount,
      reason: adjustment.reason,
      externalReference: adjustment.externalReference,
      evidenceReference: adjustment.evidenceReference,
      valueDate: adjustment.valueDate,
    };
    if (adjustment.sourceAccountId) payload.sourceAccountId = Number(adjustment.sourceAccountId);
    if (adjustment.destinationAccountId) payload.destinationAccountId = Number(adjustment.destinationAccountId);
    createAdjustment.mutate(payload, { onSuccess: () => setNotice('Treasury adjustment submitted for independent approval.'), onError: (e) => setNotice((e as Error).message) });
  };

  const submitEntitlement = (event: FormEvent) => {
    event.preventDefault();
    createEntitlement.mutate({
      ...entitlement,
      merchantId: Number(entitlement.merchantId),
      perTransactionLimit: entitlement.perTransactionLimit || null,
      dailyLimit: entitlement.dailyLimit || null,
    }, { onSuccess: () => setNotice('Shared-provider entitlement submitted for independent approval.'), onError: (e) => setNotice((e as Error).message) });
  };

  const submitCredential = (event: FormEvent) => {
    event.preventDefault();
    const providerCredentials: Record<string, string> = credential.channelCode === 'mtn_momo'
      ? {
        baseUrl: credential.baseUrl,
        targetEnvironment: credential.targetEnvironment,
        baseCurrency: credential.baseCurrency,
        callbackHost: credential.callbackHost,
        callbackUrl: credential.callbackUrl,
        collectionApiUser: credential.collectionApiUser,
        collectionApiKey: credential.collectionApiKey,
        collectionSubscriptionKey: credential.collectionSubscriptionKey,
        disbursementApiUser: credential.disbursementApiUser,
        disbursementApiKey: credential.disbursementApiKey,
        disbursementSubscriptionKey: credential.disbursementSubscriptionKey,
      }
      : { collectUrl: credential.collectUrl, payoutUrl: credential.payoutUrl };
    if (credential.channelCode === 'mtn_momo') {
      if (credential.collectionSecondarySubscriptionKey) providerCredentials.collectionSecondarySubscriptionKey = credential.collectionSecondarySubscriptionKey;
      if (credential.disbursementSecondarySubscriptionKey) providerCredentials.disbursementSecondarySubscriptionKey = credential.disbursementSecondarySubscriptionKey;
    } else {
      if (credential.authHeaderName) providerCredentials.authHeaderName = credential.authHeaderName;
      if (credential.authHeaderValue) providerCredentials.authHeaderValue = credential.authHeaderValue;
      if (credential.tokenAlias) providerCredentials.tokenAlias = credential.tokenAlias;
    }
    saveCredential.mutate({
      channelCode: credential.channelCode,
      environment: credential.environment,
      countryCode: credential.countryCode,
      currencyCode: credential.currencyCode,
      credentials: providerCredentials,
    }, { onSuccess: () => setNotice('Platform credential saved in encrypted form. A different operator must approve it.'), onError: (e) => setNotice((e as Error).message) });
  };

  const accountColumns = [
    { key: 'provider', header: 'Provider', render: (row: TreasuryAccount) => <strong>{row.channelCode}</strong> },
    { key: 'account', header: 'Account', render: (row: TreasuryAccount) => <span>{row.accountRole}{row.prefundRequired === 'YES' ? ' · prefund' : ''}</span> },
    { key: 'scope', header: 'Scope', render: (row: TreasuryAccount) => `${row.environment} · ${row.countryCode} · ${row.currencyCode}` },
    { key: 'book', header: 'Book', render: (row: TreasuryAccount) => money(row.bookBalance, row.currencyCode) },
    { key: 'reserved', header: 'Reserved', render: (row: TreasuryAccount) => money(row.reservedBalance, row.currencyCode) },
    { key: 'pending', header: 'Pending out / in', render: (row: TreasuryAccount) => `${money(row.pendingOutgoingBalance, row.currencyCode)} / ${money(row.pendingIncomingBalance, row.currencyCode)}` },
    { key: 'available', header: 'Available', render: (row: TreasuryAccount) => <Badge tone={row.lowFloat ? 'danger' : 'success'}>{money(row.availableBalance, row.currencyCode)}</Badge> },
    { key: 'reported', header: 'Provider reported', render: (row: TreasuryAccount) => row.providerReportedBalance == null ? '—' : money(row.providerReportedBalance, row.currencyCode) },
    { key: 'recon', header: 'Reconciliation', render: (row: TreasuryAccount) => <Badge tone={statusTone(row.reconciliationState)}>{row.reconciliationState}</Badge> },
    {
      key: 'controls', header: 'Controls', render: (row: TreasuryAccount) => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => {
            const raw = window.prompt('Low-float threshold', String(row.lowFloatThreshold ?? 0));
            if (raw != null) setThreshold.mutate({ id: row.id, lowFloatThreshold: Number(raw) }, { onError: (e) => setNotice((e as Error).message) });
          }}>Threshold</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => {
            const providerReportedBalance = window.prompt('Provider-reported balance');
            const statementReference = window.prompt('Provider statement/reference');
            if (providerReportedBalance && statementReference) reconcile.mutate({ id: row.id, body: { providerReportedBalance, statementReference } }, { onError: (e) => setNotice((e as Error).message) });
          }}>Reconcile</Button>
        </div>
      ),
    },
  ];

  const adjustmentColumns = [
    { key: 'type', header: 'Type', accessor: (row: TreasuryAdjustment) => row.adjustmentType },
    { key: 'accounts', header: 'From → To', render: (row: TreasuryAdjustment) => `${row.sourceAccountId ?? 'External'} → ${row.destinationAccountId ?? 'External'}` },
    { key: 'amount', header: 'Amount', accessor: (row: TreasuryAdjustment) => row.amount },
    { key: 'reference', header: 'Reference', accessor: (row: TreasuryAdjustment) => row.externalReference },
    { key: 'status', header: 'Status', render: (row: TreasuryAdjustment) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
    { key: 'maker', header: 'Maker', accessor: (row: TreasuryAdjustment) => row.requestedBy },
    {
      key: 'actions', header: 'Checker action', render: (row: TreasuryAdjustment) => row.status !== 'PENDING' ? '—' : (
        <div style={{ display: 'flex', gap: 6 }}>
          <Button variant="primary" className="ios-btn--sm" onClick={() => approveAdjustment.mutate(row.id, { onError: (e) => setNotice((e as Error).message) })}>Approve</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => rejectAdjustment.mutate(row.id, { onError: (e) => setNotice((e as Error).message) })}>Reject</Button>
        </div>
      ),
    },
  ];

  const entitlementColumns = [
    { key: 'merchant', header: 'Merchant', accessor: (row: SharedProviderEntitlement) => row.merchantId },
    { key: 'channel', header: 'Channel', accessor: (row: SharedProviderEntitlement) => row.channelCode },
    { key: 'scope', header: 'Scope', render: (row: SharedProviderEntitlement) => `${row.operation} · ${row.environment} · ${row.countryCode}/${row.currencyCode}` },
    { key: 'limits', header: 'Per tx / day', render: (row: SharedProviderEntitlement) => `${row.perTransactionLimit ?? '∞'} / ${row.dailyLimit ?? '∞'}` },
    { key: 'status', header: 'Status', render: (row: SharedProviderEntitlement) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
    { key: 'maker', header: 'Maker', accessor: (row: SharedProviderEntitlement) => row.requestedBy ?? '—' },
    {
      key: 'actions', header: 'Checker action', render: (row: SharedProviderEntitlement) => row.status !== 'PENDING' ? '—' : (
        <div style={{ display: 'flex', gap: 6 }}>
          <Button variant="primary" className="ios-btn--sm" onClick={() => approveEntitlement.mutate(row.id, { onError: (e) => setNotice((e as Error).message) })}>Approve</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => rejectEntitlement.mutate(row.id, { onError: (e) => setNotice((e as Error).message) })}>Reject</Button>
        </div>
      ),
    },
  ];

  const credentialColumns = [
    { key: 'channel', header: 'Channel', accessor: (row: PlatformCredential) => row.channelCode },
    { key: 'scope', header: 'Scope', render: (row: PlatformCredential) => `${row.environment} · ${row.countryCode}/${row.currencyCode}` },
    { key: 'status', header: 'Status', render: (row: PlatformCredential) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
    { key: 'masked', header: 'Credential material', render: (row: PlatformCredential) => Object.entries(row.credentials ?? {}).map(([k, v]) => `${k}=${v}`).join(' · ') || '—' },
    { key: 'maker', header: 'Editor', accessor: (row: PlatformCredential) => row.updatedBy ?? '—' },
    { key: 'checker', header: 'Approver', accessor: (row: PlatformCredential) => row.approvedBy ?? '—' },
    { key: 'action', header: 'Action', render: (row: PlatformCredential) => row.status === 'CONFIGURED' ? <Button variant="primary" className="ios-btn--sm" onClick={() => approveCredential.mutate(row.id, { onError: (e) => setNotice((e as Error).message) })}>Approve</Button> : '—' },
  ];

  return (
    <div style={{ padding: 'var(--ios-space-6)' }}>
      <Toolbar>
        <div>
          <h2 style={{ margin: 0 }}>Provider Treasury & Shared Channels</h2>
          <p style={{ margin: '4px 0 0', color: 'var(--ios-secondary)' }}>CPay-owned Airtel, MTN and Safaricom credentials, float, merchant permissions and reconciliation.</p>
        </div>
        <Toolbar.Spacer />
        <Button variant="ghost" onClick={() => navigate('/admin/operations')}>Operations</Button>
      </Toolbar>

      {notice ? <Card><strong>{notice}</strong></Card> : null}

      <Section title="Provider Float Accounts">
        <Card flush><Table<TreasuryAccount> columns={accountColumns} rows={accounts.data ?? []} rowKey={(row) => row.id} emptyText="No provider treasury accounts are configured." /></Card>
      </Section>

      <Section title="Credit, Debit or Rebalance Float">
        <Card>
          <form onSubmit={submitAdjustment} style={gridStyle}>
            <label>Action<select style={fieldStyle} value={adjustment.adjustmentType} onChange={(e) => setAdjustment({ ...adjustment, adjustmentType: e.target.value })}><option>CREDIT</option><option>DEBIT</option><option>REBALANCE</option></select></label>
            <label>Source account<select style={fieldStyle} value={adjustment.sourceAccountId} onChange={(e) => setAdjustment({ ...adjustment, sourceAccountId: e.target.value })}><option value="">External / none</option>{(accounts.data ?? []).map((a) => <option key={a.id} value={a.id}>{a.id} · {a.channelCode} · {a.accountRole} · {a.currencyCode}</option>)}</select></label>
            <label>Destination account<select style={fieldStyle} value={adjustment.destinationAccountId} onChange={(e) => setAdjustment({ ...adjustment, destinationAccountId: e.target.value })}><option value="">External / none</option>{(accounts.data ?? []).map((a) => <option key={a.id} value={a.id}>{a.id} · {a.channelCode} · {a.accountRole} · {a.currencyCode}</option>)}</select></label>
            <label>Amount<input required style={fieldStyle} inputMode="decimal" value={adjustment.amount} onChange={(e) => setAdjustment({ ...adjustment, amount: e.target.value })} /></label>
            <label>Reason<input required style={fieldStyle} value={adjustment.reason} onChange={(e) => setAdjustment({ ...adjustment, reason: e.target.value })} /></label>
            <label>Bank/provider reference<input required style={fieldStyle} value={adjustment.externalReference} onChange={(e) => setAdjustment({ ...adjustment, externalReference: e.target.value })} /></label>
            <label>Evidence reference<input style={fieldStyle} value={adjustment.evidenceReference} onChange={(e) => setAdjustment({ ...adjustment, evidenceReference: e.target.value })} /></label>
            <label>Value date<input required type="date" style={fieldStyle} value={adjustment.valueDate} onChange={(e) => setAdjustment({ ...adjustment, valueDate: e.target.value })} /></label>
            <Button variant="primary" type="submit" disabled={createAdjustment.isPending}>Submit for approval</Button>
          </form>
        </Card>
        <Card flush><Table<TreasuryAdjustment> columns={adjustmentColumns} rows={adjustments.data ?? []} rowKey={(row) => row.id} emptyText="No treasury adjustments." /></Card>
      </Section>

      <Section title={`Shared-Provider Merchant Entitlements${pendingEntitlements.length ? ` · ${pendingEntitlements.length} pending` : ''}`}>
        <Card>
          <form onSubmit={submitEntitlement} style={gridStyle}>
            <label>Merchant ID<input required style={fieldStyle} inputMode="numeric" value={entitlement.merchantId} onChange={(e) => setEntitlement({ ...entitlement, merchantId: e.target.value })} /></label>
            <label>Channel<select style={fieldStyle} value={entitlement.channelCode} onChange={(e) => setEntitlement({ ...entitlement, channelCode: e.target.value })}><option value="airtel_money">Airtel Money</option><option value="airtel_open_api">Airtel Open API</option><option value="mtn_momo">MTN MoMo</option><option value="safaricom_mpesa">Safaricom M-Pesa</option></select></label>
            <label>Operation<select style={fieldStyle} value={entitlement.operation} onChange={(e) => setEntitlement({ ...entitlement, operation: e.target.value })}><option>COLLECT</option><option>PAYOUT</option></select></label>
            <label>Environment<select style={fieldStyle} value={entitlement.environment} onChange={(e) => setEntitlement({ ...entitlement, environment: e.target.value })}><option>PRODUCTION</option><option>SANDBOX</option></select></label>
            <label>Country<input required style={fieldStyle} value={entitlement.countryCode} onChange={(e) => setEntitlement({ ...entitlement, countryCode: e.target.value.toUpperCase() })} /></label>
            <label>Currency<input required style={fieldStyle} value={entitlement.currencyCode} onChange={(e) => setEntitlement({ ...entitlement, currencyCode: e.target.value.toUpperCase() })} /></label>
            <label>Per-transaction limit<input style={fieldStyle} inputMode="decimal" value={entitlement.perTransactionLimit} onChange={(e) => setEntitlement({ ...entitlement, perTransactionLimit: e.target.value })} /></label>
            <label>Daily limit<input style={fieldStyle} inputMode="decimal" value={entitlement.dailyLimit} onChange={(e) => setEntitlement({ ...entitlement, dailyLimit: e.target.value })} /></label>
            <label>Notes<input style={fieldStyle} value={entitlement.notes} onChange={(e) => setEntitlement({ ...entitlement, notes: e.target.value })} /></label>
            <Button variant="primary" type="submit" disabled={createEntitlement.isPending}>Submit entitlement</Button>
          </form>
        </Card>
        <Card flush><Table<SharedProviderEntitlement> columns={entitlementColumns} rows={entitlements.data ?? []} rowKey={(row) => row.id} emptyText="No shared-provider entitlements." /></Card>
      </Section>

      <Section title="CPay Platform Provider Credentials">
        <Card>
          <p style={{ color: 'var(--ios-secondary)' }}>Secrets are encrypted at rest. After save, only masked values are returned. Credential editor and approver must be different operators.</p>
          <form onSubmit={submitCredential} style={gridStyle}>
            <label>Channel<select style={fieldStyle} value={credential.channelCode} onChange={(e) => {
              const channelCode = e.target.value;
              setCredential({ ...credential, channelCode, ...(channelCode === 'mtn_momo' ? { baseCurrency: credential.environment === 'SANDBOX' ? 'EUR' : 'UGX', currencyCode: credential.environment === 'SANDBOX' ? 'EUR' : 'UGX', baseUrl: credential.environment === 'SANDBOX' ? 'https://sandbox.momodeveloper.mtn.com' : '', targetEnvironment: credential.environment === 'SANDBOX' ? 'sandbox' : 'mtnuganda' } : {}) });
            }}><option value="airtel_money">Airtel Money</option><option value="airtel_open_api">Airtel Open API</option><option value="mtn_momo">MTN MoMo</option><option value="safaricom_mpesa">Safaricom M-Pesa</option></select></label>
            <label>Environment<select style={fieldStyle} value={credential.environment} onChange={(e) => {
              const environment = e.target.value;
              setCredential({ ...credential, environment, ...(credential.channelCode === 'mtn_momo' ? { baseCurrency: environment === 'SANDBOX' ? 'EUR' : 'UGX', currencyCode: environment === 'SANDBOX' ? 'EUR' : 'UGX', baseUrl: environment === 'SANDBOX' ? 'https://sandbox.momodeveloper.mtn.com' : '', targetEnvironment: environment === 'SANDBOX' ? 'sandbox' : 'mtnuganda' } : {}) });
            }}><option>PRODUCTION</option><option>SANDBOX</option></select></label>
            <label>Country<input required style={fieldStyle} value={credential.countryCode} onChange={(e) => setCredential({ ...credential, countryCode: e.target.value.toUpperCase() })} /></label>
            <label>Currency<input required style={fieldStyle} value={credential.currencyCode} onChange={(e) => setCredential({ ...credential, currencyCode: e.target.value.toUpperCase() })} /></label>
            {credential.channelCode === 'mtn_momo' ? <>
              <label>MTN API base URL<input required type="url" style={fieldStyle} value={credential.baseUrl} onChange={(e) => setCredential({ ...credential, baseUrl: e.target.value })} /></label>
              <label>X-Target-Environment<input required style={fieldStyle} value={credential.targetEnvironment} onChange={(e) => setCredential({ ...credential, targetEnvironment: e.target.value })} /></label>
              <label>MTN base currency<input required style={fieldStyle} value={credential.baseCurrency} onChange={(e) => setCredential({ ...credential, baseCurrency: e.target.value.toUpperCase() })} /></label>
              <label>Registered callback host<input required placeholder="payments.example.com" style={fieldStyle} value={credential.callbackHost} onChange={(e) => setCredential({ ...credential, callbackHost: e.target.value })} /></label>
              <label>CPay callback URL<input required type="url" style={fieldStyle} value={credential.callbackUrl} onChange={(e) => setCredential({ ...credential, callbackUrl: e.target.value })} /></label>
              <label>Collection API user<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.collectionApiUser} onChange={(e) => setCredential({ ...credential, collectionApiUser: e.target.value })} /></label>
              <label>Collection API key<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.collectionApiKey} onChange={(e) => setCredential({ ...credential, collectionApiKey: e.target.value })} /></label>
              <label>Collection primary subscription key<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.collectionSubscriptionKey} onChange={(e) => setCredential({ ...credential, collectionSubscriptionKey: e.target.value })} /></label>
              <label>Collection secondary key<input type="password" autoComplete="new-password" style={fieldStyle} value={credential.collectionSecondarySubscriptionKey} onChange={(e) => setCredential({ ...credential, collectionSecondarySubscriptionKey: e.target.value })} /></label>
              <label>Disbursement API user<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.disbursementApiUser} onChange={(e) => setCredential({ ...credential, disbursementApiUser: e.target.value })} /></label>
              <label>Disbursement API key<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.disbursementApiKey} onChange={(e) => setCredential({ ...credential, disbursementApiKey: e.target.value })} /></label>
              <label>Disbursement primary subscription key<input required type="password" autoComplete="new-password" style={fieldStyle} value={credential.disbursementSubscriptionKey} onChange={(e) => setCredential({ ...credential, disbursementSubscriptionKey: e.target.value })} /></label>
              <label>Disbursement secondary key<input type="password" autoComplete="new-password" style={fieldStyle} value={credential.disbursementSecondarySubscriptionKey} onChange={(e) => setCredential({ ...credential, disbursementSecondarySubscriptionKey: e.target.value })} /></label>
            </> : <>
              <label>Collect URL<input required={credential.environment === 'PRODUCTION'} style={fieldStyle} value={credential.collectUrl} onChange={(e) => setCredential({ ...credential, collectUrl: e.target.value })} /></label>
              <label>Payout URL<input required={credential.environment === 'PRODUCTION'} style={fieldStyle} value={credential.payoutUrl} onChange={(e) => setCredential({ ...credential, payoutUrl: e.target.value })} /></label>
              <label>Auth header name<input style={fieldStyle} value={credential.authHeaderName} onChange={(e) => setCredential({ ...credential, authHeaderName: e.target.value })} /></label>
              <label>Auth header value<input type="password" style={fieldStyle} value={credential.authHeaderValue} onChange={(e) => setCredential({ ...credential, authHeaderValue: e.target.value })} /></label>
              <label>Token alias<input style={fieldStyle} value={credential.tokenAlias} onChange={(e) => setCredential({ ...credential, tokenAlias: e.target.value })} /></label>
            </>}
            <Button variant="primary" type="submit" disabled={saveCredential.isPending}>Save encrypted credential</Button>
          </form>
        </Card>
        <Card flush><Table<PlatformCredential> columns={credentialColumns} rows={credentials.data ?? []} rowKey={(row) => row.id} emptyText="No CPay platform credentials configured." /></Card>
      </Section>

      {pendingAdjustments.length ? <p style={{ color: 'var(--ios-secondary)' }}>{pendingAdjustments.length} treasury adjustment(s) await an independent checker.</p> : null}
    </div>
  );
}
