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
  usePayoutControlConfigs,
  usePayoutControlUpsertMutation,
  usePayoutControlDeleteMutation,
  useLoaderSync,
  useRefreshSignal,
} from '../../shared/api/hooks';
import type { PayoutControlRow } from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit V34: self-service configuration of `payout_controls` rows. Backend
 * (`PayoutConfigController` under `/api/v2/admin/payout-controls/**`, gated by
 * the `payout-controls-config` flag) upserts the same
 * merchant/channel/currency/country key `PayoutControlService.evaluate` reads,
 * so a limit saved here is enforced immediately on the v2 payout path.
 */

const CHANNELS = [
  { value: 'MTN_MOMO', label: 'MTN MoMo (MTN_MOMO)' },
  { value: 'AIRTEL_MONEY', label: 'Airtel Money (AIRTEL_MONEY)' },
  { value: 'AIRTEL_OPENAPI', label: 'Airtel OpenAPI' },
  { value: 'SAFARICOM', label: 'Safaricom M-Pesa' },
  { value: 'YO', label: 'Yo! Payments' },
];

const CURRENCIES = [
  { value: 'UGX', label: 'UGX (Uganda)' },
  { value: 'KES', label: 'KES (Kenya)' },
  { value: 'TZS', label: 'TZS (Tanzania)' },
  { value: 'RWF', label: 'RWF (Rwanda)' },
];

const YES_NO = [
  { value: 'YES', label: 'YES' },
  { value: 'NO', label: 'NO' },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface ModulePayoutControlsProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModulePayoutControls({ loader, refreshSignal, sessionExpired }: ModulePayoutControlsProps): React.ReactElement {
  const [merchantFilter, setMerchantFilter] = useState('');
  const [merchantId, setMerchantId] = useState('');
  const [channelCode, setChannelCode] = useState('MTN_MOMO');
  const [currency, setCurrency] = useState('UGX');
  const [dailyAmount, setDailyAmount] = useState('');
  const [monthlyAmount, setMonthlyAmount] = useState('');
  const [perTxAmount, setPerTxAmount] = useState('');
  const [velocityLimit, setVelocityLimit] = useState('');
  const [approvalFlag, setApprovalFlag] = useState('NO');
  const [enabledFlag, setEnabledFlag] = useState('YES');
  const [changedBy, setChangedBy] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);
  const [saveError, setSaveError] = useState<unknown>(null);

  const numericFilter = merchantFilter.trim() && !Number.isNaN(Number(merchantFilter.trim())) ? merchantFilter.trim() : '';
  const configsQuery = usePayoutControlConfigs(numericFilter);
  const upsertMutation = usePayoutControlUpsertMutation();
  const deleteMutation = usePayoutControlDeleteMutation();

  useLoaderSync(
    loader,
    configsQuery.isFetching || upsertMutation.isPending || deleteMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [configsQuery.refetch]);

  useEffect(() => {
    if (configsQuery.error instanceof ApiError && configsQuery.error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configsQuery.error]);

  const rows = configsQuery.data ?? [];

  function blankToUndefined(value: string): string | undefined {
    return value.trim() === '' ? undefined : value.trim();
  }

  function handleSave() {
    const merchantNumeric = Number(merchantId.trim());
    if (!Number.isFinite(merchantNumeric) || merchantNumeric <= 0) {
      setSaveError(new Error('merchantId is required'));
      return;
    }
    setSaveError(null);
    setFeedback(null);
    upsertMutation.mutate(
      {
        merchantId: merchantNumeric,
        channelCode,
        currency,
        dailyAmountLimit: blankToUndefined(dailyAmount),
        monthlyAmountLimit: blankToUndefined(monthlyAmount),
        perTransactionLimit: blankToUndefined(perTxAmount),
        beneficiaryVelocityLimit: blankToUndefined(velocityLimit),
        approvalRequiredFlag: approvalFlag,
        enabledFlag,
        changedBy: changedBy.trim() || undefined,
      },
      {
        onSuccess: () => {
          setFeedback({ tone: 'success', message: `Payout control saved for merchant ${merchantNumeric} (${channelCode} / ${currency}).` });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleDelete(controlId: number | undefined, reference: string) {
    if (!controlId) return;
    setSaveError(null);
    setFeedback(null);
    deleteMutation.mutate(controlId, {
      onSuccess: () => {
        setFeedback({ tone: 'success', message: `Payout control ${reference} deleted.` });
      },
      onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
    });
  }

  const columns: Column<PayoutControlRow>[] = [
    { key: 'id', header: 'ID', accessor: (r) => String(r.id ?? '') },
    { key: 'merchant_id', header: 'Merchant', accessor: (r) => String(r.merchant_id ?? '') },
    { key: 'channel_code', header: 'Channel', accessor: (r) => r.channel_code ?? '' },
    { key: 'currency', header: 'Currency', accessor: (r) => r.currency ?? '' },
    { key: 'country', header: 'Country', accessor: (r) => r.country ?? '' },
    { key: 'daily_amount_limit', header: 'Daily limit', accessor: (r) => r.daily_amount_limit ?? '' },
    { key: 'monthly_amount_limit', header: 'Monthly limit', accessor: (r) => r.monthly_amount_limit ?? '' },
    { key: 'per_transaction_limit', header: 'Per-tx limit', accessor: (r) => r.per_transaction_limit ?? '' },
    { key: 'beneficiary_velocity_limit', header: 'Beneficiary vel.', accessor: (r) => r.beneficiary_velocity_limit ?? '' },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <Button
          variant="ghost"
          className="ios-btn--sm"
          onClick={() => handleDelete(r.id, `#${r.id ?? ''}`)}
        >
          Delete
        </Button>
      ),
    },
  ];

  return (
    <div className="cpay-payout-controls">
      {feedback ? <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert> : null}
      {saveError ? <Alert variant="error">{errorMessage(saveError)}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Configure payout risk controls. A saved limit is enforced immediately on the v2 payout path.</strong>
          </Toolbar>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <TextField id="pc-merchant-filter" label="Filter by merchant id" value={merchantFilter} onValueChange={setMerchantFilter} placeholder="optional" />
            <Button variant="ghost" className="ios-btn--sm" onClick={() => configsQuery.refetch()}>
              Refresh
            </Button>
          </div>
        </div>
      </Card>

      {configsQuery.isLoading ? <Spinner label="Loading payout controls" /> : null}
      {configsQuery.error ? <Alert variant="error">{errorMessage(configsQuery.error)}</Alert> : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>New / update control (merchant + channel + currency + country is the unique key)</strong>
          </Toolbar>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <TextField id="pc-merchant-id" label="Merchant id *" value={merchantId} onValueChange={setMerchantId} placeholder="e.g. 7" />
            <div>
              <label htmlFor="pc-channel">Channel</label>
              <Select id="pc-channel" value={channelCode} options={CHANNELS} onValueChange={setChannelCode} />
            </div>
            <div>
              <label htmlFor="pc-currency">Currency</label>
              <Select id="pc-currency" value={currency} options={CURRENCIES} onValueChange={setCurrency} />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <TextField id="pc-daily" label="Daily amount limit" value={dailyAmount} onValueChange={setDailyAmount} placeholder="blank = no limit" />
            <TextField id="pc-monthly" label="Monthly amount limit" value={monthlyAmount} onValueChange={setMonthlyAmount} placeholder="blank = no limit" />
            <TextField id="pc-per-tx" label="Per-transaction limit" value={perTxAmount} onValueChange={setPerTxAmount} placeholder="blank = no limit" />
            <TextField id="pc-velocity" label="Beneficiary velocity limit" value={velocityLimit} onValueChange={setVelocityLimit} placeholder="e.g. 5" />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--ios-space-3)', marginTop: 'var(--ios-space-3)', alignItems: 'flex-end' }}>
            <div>
              <label htmlFor="pc-approval-flag">Approval required (first beneficiary / review)</label>
              <Select id="pc-approval-flag" value={approvalFlag} options={YES_NO} onValueChange={setApprovalFlag} />
            </div>
            <div>
              <label htmlFor="pc-enabled-flag">Enabled</label>
              <Select id="pc-enabled-flag" value={enabledFlag} options={YES_NO} onValueChange={setEnabledFlag} />
            </div>
            <TextField id="pc-changed-by" label="Changed by" value={changedBy} onValueChange={setChangedBy} placeholder="e.g. ops-admin" />
            <Button variant="primary" onClick={handleSave} loading={upsertMutation.isPending} loadingLabel="Saving…">
              Save control
            </Button>
          </div>
        </div>
      </Card>

      <Table
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id ?? 0}
        pageSize={20}
        emptyText="No payout-control rows configured. Enter values above and save one."
      />
    </div>
  );
}

export default ModulePayoutControls;
