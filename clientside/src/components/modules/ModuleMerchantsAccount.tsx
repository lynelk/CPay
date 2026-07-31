import React, { useEffect, useState } from 'react';
import common from '../Common';
import strings from '../locale';
import {
  Toolbar, Table, Select, Sheet, Button, TextField, TextArea, DateField, Icons, Alert, Spinner,
} from '../../ui';
import type { Column, SelectOption } from '../../ui';
import {
  useAdminMerchantStatement,
  useRecordMerchantTransactionMutation,
  useLoaderSync,
  SessionExpiredError,
  AccessDeniedError,
} from '../../shared/api/hooks';
import type { MerchantStatementRow, MerchantStatementSearchRules } from '../../shared/api/hooks';
import { downloadStatementExport, type StatementExportFormat } from '../../shared/export/statementExport';

/**
 * Audit L2/L3/L4/M5: merchant account statement dialog opened from `ModuleMerchants`. Converted
 * from a class component hand-rolling `fetch`/`setState` to a typed function component backed by
 * `useAdminMerchantStatement`, with the "record transaction" action wired through a mutation that
 * invalidates the statement query instead of an imperative `getData()` re-fetch.
 *
 * Audit M5: the download control previously built an Excel file client-side (via the legacy
 * `ReactExport`/`ExcelExport.js` shim) from whatever rows happened to already be loaded in the
 * table. It now calls the new session-authenticated admin statement export endpoint
 * (`GET /api/v2/admin/merchants/{merchantNumber}/statements`, `AdminMerchantStatementController` /
 * `MerchantStatementExportService#exportForAdmin`) the same way `MerchantModuleMerchantsAccount`'s
 * `Download` control already calls the merchant self-service equivalent - a real server-rendered
 * CSV/XLSX covering the full requested date range, not just the rows currently on screen.
 */

const toOptions = (arr: Array<{ value: string; text: string }>): SelectOption[] =>
  Array.isArray(arr) ? arr.map((t) => ({ value: t.value, label: t.text })) : [];

interface MerchantAccountRecord {
  id?: number | string;
  account_number?: string;
  name?: string;
  [key: string]: unknown;
}

interface ModuleMerchantsAccountProps {
  title?: string;
  statementDialogStateOpened: boolean;
  openOrCloseStatementDialog: (state: boolean) => void;
  openMerchantAccount: MerchantAccountRecord;
  loader?: (op: 'START' | 'STOP') => void;
  sessionExpired?: () => void;
}

interface RecordTxForm {
  tx_type: string;
  amount: string;
  description: string;
  balance_type: string;
}

const EMPTY_SEARCH_RULES: MerchantStatementSearchRules = { start_date: '', end_date: '' };
const EMPTY_RECORD_TX: RecordTxForm = { tx_type: '', amount: '', description: '', balance_type: '' };

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function ModuleMerchantsAccount({
  title,
  statementDialogStateOpened,
  openOrCloseStatementDialog,
  openMerchantAccount,
  loader,
  sessionExpired,
}: ModuleMerchantsAccountProps): React.ReactElement {
  const isOpen = !statementDialogStateOpened;
  const merchantId = openMerchantAccount?.id;

  const [pendingSearchRules, setPendingSearchRules] = useState<MerchantStatementSearchRules>(EMPTY_SEARCH_RULES);
  const [committedSearchRules, setCommittedSearchRules] = useState<MerchantStatementSearchRules>(EMPTY_SEARCH_RULES);
  const [searchOpen, setSearchOpen] = useState(false);
  const [recordTxOpen, setRecordTxOpen] = useState(false);
  const [recordTx, setRecordTx] = useState<RecordTxForm>(EMPTY_RECORD_TX);
  const [recordTxErrors, setRecordTxErrors] = useState<Record<string, string>>({});
  const [recordTxError, setRecordTxError] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloadingFormat, setDownloadingFormat] = useState<StatementExportFormat | null>(null);

  const statementQuery = useAdminMerchantStatement(merchantId, committedSearchRules, 50, isOpen);
  const recordTxMutation = useRecordMerchantTransactionMutation();

  useLoaderSync(loader, statementQuery.isFetching || recordTxMutation.isPending);

  useEffect(() => {
    if (statementQuery.error instanceof SessionExpiredError) {
      sessionExpired?.();
    }
    // Intentionally only re-run when the error itself changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statementQuery.error]);

  const rows = statementQuery.data?.rows ?? [];
  const balances = statementQuery.data?.balances ?? '';
  const accessDenied = statementQuery.error instanceof AccessDeniedError;
  const sessionExpiredError = statementQuery.error instanceof SessionExpiredError;
  const otherError = Boolean(statementQuery.error) && !accessDenied && !sessionExpiredError;

  function handleSearchFormChange(name: keyof MerchantStatementSearchRules, value: string) {
    setPendingSearchRules((prev) => ({ ...prev, [name]: value }));
  }

  function clearSearch() {
    setPendingSearchRules(EMPTY_SEARCH_RULES);
  }

  function applySearch() {
    setCommittedSearchRules(pendingSearchRules);
    setSearchOpen(false);
  }

  function handleRecordFormChange(name: keyof RecordTxForm, value: string) {
    setRecordTx((prev) => ({ ...prev, [name]: value }));
  }

  function resetRecordTxForm() {
    setRecordTx(EMPTY_RECORD_TX);
    setRecordTxErrors({});
    setRecordTxError(null);
  }

  function closeRecordTxSheet() {
    resetRecordTxForm();
    setRecordTxOpen(false);
  }

  function validateRecordTx(): boolean {
    const errors: Record<string, string> = {};
    if (!recordTx.tx_type) errors.tx_type = 'Transaction type is required';
    if (!recordTx.description) errors.description = 'Description is required';
    if (recordTx.amount === '' || Number.isNaN(Number(recordTx.amount))) errors.amount = 'Enter a valid amount';
    setRecordTxErrors(errors);
    return Object.keys(errors).length === 0;
  }

  function submitRecordTx() {
    if (!validateRecordTx()) return;
    setRecordTxError(null);
    recordTxMutation.mutate(
      { ...recordTx, merchant_id: merchantId },
      {
        onSuccess: () => {
          resetRecordTxForm();
          setRecordTxOpen(false);
        },
        onError: (error) => setRecordTxError(errorMessage(error)),
      },
    );
  }

  async function handleDownload(format: StatementExportFormat) {
    if (!committedSearchRules.start_date || !committedSearchRules.end_date) {
      setDownloadError('Select a start and end date (Search) before downloading a statement.');
      return;
    }
    if (!openMerchantAccount?.account_number) {
      setDownloadError('No merchant selected.');
      return;
    }
    setDownloadError(null);
    setDownloadingFormat(format);
    try {
      await downloadStatementExport({
        startDate: committedSearchRules.start_date,
        endDate: committedSearchRules.end_date,
        format,
        path: `/api/v2/admin/merchants/${openMerchantAccount.account_number}/statements`,
      });
    } catch (error) {
      setDownloadError(errorMessage(error));
    } finally {
      setDownloadingFormat(null);
    }
  }

  const columns: Column<MerchantStatementRow>[] = [
    { key: 'rownum', header: '#', width: 44, render: (_row, i) => i + 1 },
    { key: 'created_on', header: 'Created On', accessor: (r) => r.created_on, width: 170 },
    { key: 'description', header: 'Description', render: (r) => `${r.narrative}: ${r.description}` },
    {
      key: 'amount',
      header: 'Amount',
      numeric: true,
      render: (r) => (
        <span style={{ color: r.tx_type === 'CR' ? 'var(--ios-success)' : 'var(--ios-danger)', fontWeight: 600 }}>
          {common.formatNumber(r.amount ?? 0)}
        </span>
      ),
    },
    { key: 'balances', header: 'Balance', numeric: true, accessor: (r) => r.balances },
  ];

  return (
    <>
      <Sheet
        open={isOpen}
        onClose={() => openOrCloseStatementDialog(true)}
        title={title}
        size="xl"
        footer={
          <>
            <Button variant="primary" className="ios-btn--sm" onClick={() => setRecordTxOpen(true)}>
              <Icons.PaymentsIcon size={16} />
              {strings.record_tx}
            </Button>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => openOrCloseStatementDialog(true)}>
              Close
            </Button>
          </>
        }
      >
        <Toolbar>
          <span style={{ color: 'var(--ios-text-secondary)', fontSize: 'var(--ios-fs-footnote)' }}>Available Balances:</span>
          <strong style={{ color: 'var(--ios-success)' }}>{balances}</strong>
          <Toolbar.Spacer />
          <Button variant="ghost" className="ios-btn--sm" onClick={() => setSearchOpen(true)}>
            <Icons.SearchIcon size={16} />
            {strings.search}
          </Button>
          <Button
            variant="ghost"
            className="ios-btn--sm"
            disabled={downloadingFormat !== null}
            loading={downloadingFormat === 'csv'}
            loadingLabel="Downloading…"
            onClick={() => handleDownload('csv')}
          >
            <Icons.DownloadIcon size={16} />
            {strings.download} CSV
          </Button>
          <Button
            variant="ghost"
            className="ios-btn--sm"
            disabled={downloadingFormat !== null}
            loading={downloadingFormat === 'xlsx'}
            loadingLabel="Downloading…"
            onClick={() => handleDownload('xlsx')}
          >
            <Icons.DownloadIcon size={16} />
            {strings.download} XLSX
          </Button>
        </Toolbar>

        {downloadError ? <Alert variant="error">{downloadError}</Alert> : null}

        <div style={{ marginTop: 'var(--ios-space-4)' }}>
          {statementQuery.isLoading ? <Spinner label="Loading account statement" /> : null}
          {accessDenied ? <Alert variant="error">{errorMessage(statementQuery.error)}</Alert> : null}
          {otherError ? <Alert variant="error">{errorMessage(statementQuery.error)}</Alert> : null}
          {!statementQuery.isLoading && !accessDenied && !otherError && !sessionExpiredError ? (
            <Table
              columns={columns}
              rows={rows}
              rowKey={(row, i) => row.id ?? i}
              pageSize={50}
              emptyText="No statement entries to display for this merchant yet."
            />
          ) : null}
        </div>
      </Sheet>

      <Sheet
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        title="Search"
        size="sm"
        footer={
          <>
            <Button variant="ghost" className="ios-btn--sm" onClick={clearSearch}>
              {strings.clear}
            </Button>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => setSearchOpen(false)}>
              {strings.close}
            </Button>
            <Button variant="primary" className="ios-btn--sm" onClick={applySearch}>
              {strings.go}
            </Button>
          </>
        }
      >
        <div className="ios-form">
          <DateField
            id="stmt-start"
            label="Start Date"
            kind="date"
            value={pendingSearchRules.start_date ?? ''}
            onValueChange={(v) => handleSearchFormChange('start_date', v)}
          />
          <DateField
            id="stmt-end"
            label="End Date"
            kind="date"
            value={pendingSearchRules.end_date ?? ''}
            onValueChange={(v) => handleSearchFormChange('end_date', v)}
          />
        </div>
      </Sheet>

      <Sheet
        open={recordTxOpen}
        onClose={closeRecordTxSheet}
        title={strings.record_tx}
        size="sm"
        footer={
          <>
            <Button variant="ghost" className="ios-btn--sm" onClick={closeRecordTxSheet}>
              {strings.close}
            </Button>
            <Button
              variant="primary"
              className="ios-btn--sm"
              loading={recordTxMutation.isPending}
              loadingLabel="Saving…"
              onClick={submitRecordTx}
            >
              {strings.save}
            </Button>
          </>
        }
      >
        <div className="ios-form">
          {recordTxError ? <Alert variant="error">{recordTxError}</Alert> : null}
          <Select
            id="rtx-balance"
            label="Balance Type"
            value={recordTx.balance_type}
            placeholder="Select"
            options={toOptions(common.balance_type)}
            onValueChange={(v) => handleRecordFormChange('balance_type', v)}
          />
          <Select
            id="rtx-type"
            label="Transaction Type"
            value={recordTx.tx_type}
            placeholder="Select"
            invalid={Boolean(recordTxErrors.tx_type)}
            options={toOptions(common.tx_types)}
            onValueChange={(v) => handleRecordFormChange('tx_type', v)}
          />
          {recordTxErrors.tx_type ? (
            <span role="alert" style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>
              {recordTxErrors.tx_type}
            </span>
          ) : null}
          <TextField
            id="rtx-amount"
            label="Amount"
            value={recordTx.amount}
            invalid={Boolean(recordTxErrors.amount)}
            onValueChange={(v) => handleRecordFormChange('amount', v)}
          />
          {recordTxErrors.amount ? (
            <span role="alert" style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>
              {recordTxErrors.amount}
            </span>
          ) : null}
          <TextArea
            id="rtx-desc"
            label="Description"
            rows={3}
            value={recordTx.description}
            invalid={Boolean(recordTxErrors.description)}
            onValueChange={(v) => handleRecordFormChange('description', v)}
          />
          {recordTxErrors.description ? (
            <span role="alert" style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>
              {recordTxErrors.description}
            </span>
          ) : null}
        </div>
      </Sheet>
    </>
  );
}

export default ModuleMerchantsAccount;
