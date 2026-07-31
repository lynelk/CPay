import React, { useEffect, useState } from 'react';
import common from '../../Common';
import strings from '../../locale';
import { Card, Toolbar, Table, Sheet, Button, DateField, Icons, Alert, Spinner } from '../../../ui';
import type { Column } from '../../../ui';
import {
  useMerchantOwnStatement,
  useLoaderSync,
  useRefreshSignal,
  SessionExpiredError,
  AccessDeniedError,
} from '../../../shared/api/hooks';
import type { MerchantStatementRow, MerchantStatementSearchRules } from '../../../shared/api/hooks';
import { downloadStatementExport, type StatementExportFormat } from '../../../shared/export/statementExport';

/**
 * Audit L2/L3/L4: a merchant portal user's own "Account Statement" screen - the merchant-side twin
 * of `ModuleMerchantsAccount` (the admin dialog). Converted from a class component hand-rolling
 * `fetch`/`setState` to a typed function component backed by `useMerchantOwnStatement`. The
 * server-side CSV/XLSX download (audit M5, `downloadStatementExport` against
 * `/api/v2/merchant-self-service/statements`) was already in place here before this pass; this
 * keeps that behavior unchanged while modernizing the rest of the screen around it.
 */

const EMPTY_SEARCH_RULES: MerchantStatementSearchRules = { start_date: '', end_date: '' };

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

interface MerchantModuleMerchantsAccountProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function MerchantModuleMerchantsAccount({
  loader,
  refreshSignal,
  sessionExpired,
}: MerchantModuleMerchantsAccountProps): React.ReactElement {
  const [pendingSearchRules, setPendingSearchRules] = useState<MerchantStatementSearchRules>(EMPTY_SEARCH_RULES);
  const [committedSearchRules, setCommittedSearchRules] = useState<MerchantStatementSearchRules>(EMPTY_SEARCH_RULES);
  const [searchOpen, setSearchOpen] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloadingFormat, setDownloadingFormat] = useState<StatementExportFormat | null>(null);

  const statementQuery = useMerchantOwnStatement(committedSearchRules, 50);

  useLoaderSync(loader, statementQuery.isFetching);
  useRefreshSignal(refreshSignal, [statementQuery.refetch]);

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

  async function handleDownload(format: StatementExportFormat) {
    const { start_date: startDate, end_date: endDate } = committedSearchRules;
    if (!startDate || !endDate) {
      setDownloadError('Select a start and end date (Search) before downloading a statement.');
      return;
    }
    setDownloadError(null);
    setDownloadingFormat(format);
    try {
      await downloadStatementExport({ startDate, endDate, format });
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
    <Card flush>
      <div style={{ padding: 'var(--ios-space-4)' }}>
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
      </div>

      {downloadError ? <Alert variant="error">{downloadError}</Alert> : null}
      {statementQuery.isLoading ? <Spinner label="Loading account statement" /> : null}
      {accessDenied ? <Alert variant="error">{errorMessage(statementQuery.error)}</Alert> : null}
      {otherError ? <Alert variant="error">{errorMessage(statementQuery.error)}</Alert> : null}
      {!statementQuery.isLoading && !accessDenied && !otherError && !sessionExpiredError ? (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row, i) => row.id ?? i}
          pageSize={50}
          emptyText="No statement entries yet. Your first pay-in or pay-out will show up here."
        />
      ) : null}

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
            id="mstmt-start"
            label="Start Date"
            kind="date"
            value={pendingSearchRules.start_date ?? ''}
            onValueChange={(v) => handleSearchFormChange('start_date', v)}
          />
          <DateField
            id="mstmt-end"
            label="End Date"
            kind="date"
            value={pendingSearchRules.end_date ?? ''}
            onValueChange={(v) => handleSearchFormChange('end_date', v)}
          />
        </div>
      </Sheet>
    </Card>
  );
}

export default MerchantModuleMerchantsAccount;
