import React, { useEffect, useState } from 'react';
import {
  Card,
  Toolbar,
  Table,
  Alert,
  Spinner,
  TextField,
  Select,
  Button,
} from '../../ui';
import type { Column } from '../../ui';
import {
  useCommunicationProviders,
  useCommunicationRoutingRules,
  useCommunicationEffectiveRule,
  useCommunicationRuleUpsertMutation,
  useCommunicationRuleDeleteMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { CommunicationProviderRow, CommunicationRuleRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * B1a admin surface for SMS provider routing. Backed by
 * `CommunicationRoutingController` (`/api/v2/admin/communication/routing/**`):
 * browse the provider catalog, manage routing rules, and preview which adapter a
 * merchant+channel resolves to. Writes are immediate — the backend
 * `ProviderRouter` reads these tables on every send.
 */

const CHANNELS = [{ value: 'SMS', label: 'SMS' }];

const YES_NO = [
  { value: 'YES', label: 'YES' },
  { value: 'NO', label: 'NO' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface ModuleCommunicationRoutingProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleCommunicationRouting({
  loader,
  refreshSignal,
  sessionExpired,
}: ModuleCommunicationRoutingProps): React.ReactElement {
  const [merchantId, setMerchantId] = useState('');
  const [channel, setChannel] = useState('SMS');
  const [providerCode, setProviderCode] = useState('LEGACY_SETTINGS');
  const [priority, setPriority] = useState('100');
  const [enabledFlag, setEnabledFlag] = useState('YES');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(
    null,
  );
  const [saveError, setSaveError] = useState<unknown>(null);

  const providersQuery = useCommunicationProviders();
  const rulesQuery = useCommunicationRoutingRules();
  const effectiveQuery = useCommunicationEffectiveRule(merchantId, channel);
  const upsertMutation = useCommunicationRuleUpsertMutation();
  const deleteMutation = useCommunicationRuleDeleteMutation();

  useLoaderSync(
    loader,
    providersQuery.isFetching ||
      rulesQuery.isFetching ||
      effectiveQuery.isFetching ||
      upsertMutation.isPending ||
      deleteMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [
    providersQuery.refetch,
    rulesQuery.refetch,
    effectiveQuery.refetch,
  ]);

  useEffect(() => {
    if (providersQuery.error instanceof ApiError && providersQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providersQuery.error]);

  const providers = providersQuery.data ?? [];
  const rules = rulesQuery.data ?? [];
  const effective = effectiveQuery.data;
  const providerOptions = providers.map((p) => ({
    value: p.providerCode ?? '',
    label: `${p.providerName ?? p.providerCode} (${p.providerCode ?? ''}${
      p.enabledFlag === 'YES' ? '' : ' — disabled'
    })`,
  }));

  function handleSave() {
    const merchantNumeric =
      merchantId.trim() === '' ? null : Number(merchantId.trim());
    if (merchantId.trim() !== '' && (!Number.isFinite(merchantNumeric) || (merchantNumeric ?? 0) <= 0)) {
      setSaveError(new Error('merchantId must be a positive number or blank for the platform default'));
      return;
    }
    const priorityNumeric = Number(priority.trim());
    if (!Number.isFinite(priorityNumeric)) {
      setSaveError(new Error('priority must be a number (lower wins)'));
      return;
    }
    setSaveError(null);
    setFeedback(null);
    upsertMutation.mutate(
      {
        id: null,
        channel,
        merchantId: merchantNumeric,
        priority: priorityNumeric,
        providerCode,
        enabledFlag,
      },
      {
        onSuccess: () => {
          setFeedback({
            tone: 'success',
            message: merchantId.trim()
              ? `Rule saved for merchant ${merchantNumeric} (${channel} → ${providerCode}).`
              : `Platform default saved for ${channel} → ${providerCode}.`,
          });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleDelete(ruleId: number | undefined) {
    if (!ruleId) return;
    setSaveError(null);
    setFeedback(null);
    deleteMutation.mutate(ruleId, {
      onSuccess: () => {
        setFeedback({ tone: 'success', message: `Routing rule #${ruleId} deleted.` });
      },
      onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
    });
  }

  const ruleColumns: Column<CommunicationRuleRow>[] = [
    { key: 'id', header: 'ID', accessor: (r) => String(r.id ?? '') },
    { key: 'channel', header: 'Channel', accessor: (r) => r.channel ?? '' },
    {
      key: 'merchant_id',
      header: 'Merchant',
      accessor: (r) => (r.merchantId == null ? '(platform default)' : String(r.merchantId)),
    },
    { key: 'priority', header: 'Priority', accessor: (r) => String(r.priority ?? '') },
    { key: 'provider_code', header: 'Provider', accessor: (r) => r.providerCode ?? '' },
    { key: 'enabled_flag', header: 'Enabled', accessor: (r) => r.enabledFlag ?? '' },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <Button
          variant="ghost"
          className="ios-btn--sm"
          onClick={() => handleDelete(r.id)}
        >
          Delete
        </Button>
      ),
    },
  ];

  const providerColumns: Column<CommunicationProviderRow>[] = [
    { key: 'provider_code', header: 'Code', accessor: (r) => r.providerCode ?? '' },
    { key: 'provider_name', header: 'Provider', accessor: (r) => r.providerName ?? '' },
    { key: 'channel', header: 'Channel', accessor: (r) => r.channel ?? '' },
    { key: 'adapter_class', header: 'Adapter', accessor: (r) => r.adapterClass ?? '' },
    { key: 'enabled_flag', header: 'Enabled', accessor: (r) => r.enabledFlag ?? '' },
  ];

  return (
    <div className="cpay-communication-routing">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}
      {saveError ? <Alert variant="error">{errorMessage(saveError)}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>
              SMS provider routing. A saved rule takes effect on the next pending-send sweep
              (unconfigured deployments keep the legacy settings gateway).
            </strong>
          </Toolbar>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <div>
              <label htmlFor="cr-channel">Channel</label>
              <Select id="cr-channel" value={channel} options={CHANNELS} onValueChange={setChannel} />
            </div>
            <div>
              <label htmlFor="cr-provider">Provider</label>
              <Select id="cr-provider" value={providerCode} options={providerOptions} onValueChange={setProviderCode} />
            </div>
            <TextField id="cr-merchant" label="Merchant id (blank = platform default)" value={merchantId} onValueChange={setMerchantId} placeholder="e.g. 7" />
            <TextField id="cr-priority" label="Priority (lower wins)" value={priority} onValueChange={setPriority} placeholder="e.g. 100" />
            <div>
              <label htmlFor="cr-enabled">Enabled</label>
              <Select id="cr-enabled" value={enabledFlag} options={YES_NO} onValueChange={setEnabledFlag} />
            </div>
            <Button variant="primary" onClick={handleSave} loading={upsertMutation.isPending} loadingLabel="Saving…">
              Save rule
            </Button>
          </div>
        </div>
      </Card>

      {rulesQuery.isLoading ? <Spinner label="Loading routing rules" /> : null}
      {rulesQuery.error ? <Alert variant="error">{errorMessage(rulesQuery.error)}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Routing rules</strong>
          </Toolbar>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <TextField id="cr-effective-merchant" label="Preview: merchant id" value={merchantId} onValueChange={setMerchantId} placeholder="e.g. 7" />
            <Button variant="ghost" className="ios-btn--sm" onClick={() => effectiveQuery.refetch()}>
              Refresh
            </Button>
          </div>
          {effectiveQuery.isLoading ? <Spinner label="Resolving rule" /> : null}
          {effective && effective.resolved && effective.rule ? (
            <Alert variant="success">
              {merchantId.trim() ? `Merchant ${merchantId.trim()}` : 'Platform default'} uses{' '}
              <strong>{effective.provider?.providerName ?? effective.rule.providerCode}</strong> (
              {effective.rule.providerCode}) via rule #{effective.rule.id}.
            </Alert>
          ) : null}
          {effective && !effective.resolved ? (
            <Alert variant="error">No enabled rule resolves for this merchant + channel — the legacy gateway is the fallback.</Alert>
          ) : null}
        </div>
      </Card>

      <Table
        columns={ruleColumns}
        rows={rules}
        rowKey={(r) => r.id ?? 0}
        pageSize={20}
        emptyText="No routing rules configured. The seeded platform default routes SMS to the legacy gateway."
      />

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Provider catalog (read-only)</strong>
          </Toolbar>
        </div>
      </Card>
      <Table
        columns={providerColumns}
        rows={providers}
        rowKey={(p) => p.id ?? 0}
        pageSize={20}
        emptyText="No providers registered."
      />
    </div>
  );
}

export default ModuleCommunicationRouting;
