import React, { useEffect, useState } from 'react';
import {
  Card,
  Section,
  Toolbar,
  Table,
  Badge,
  Alert,
  Spinner,
  SearchField,
  DateField,
  TextField,
  Select,
  FileButton,
  Button,
} from '../../ui';
import type { Column } from '../../ui';
import {
  useUnmatchedReconciliationRecords,
  useCandidateTransactions,
  useAutoMatchMutation,
  useManualMatchMutation,
  useImportStatementMutation,
  useLoaderSync,
  useRefreshSignal,
  hasAnyCandidateFilter,
} from '../../shared/api/hooks';
import type {
  ReconciliationRecord,
  CandidateTransaction,
  CandidateTransactionSearch,
} from '../../shared/api/hooks';
import { ApiError } from '../../shared/api/httpClient';

/**
 * Audit O2: the reconciliation manual-match workbench. Backend API
 * (`ReconController` under `/api/v2/admin/reconciliation/**`) already existed;
 * this was the missing admin screen - previously ops staff could only see
 * unmatched provider statement rows and match them by calling the API
 * directly (Postman/curl).
 */

const STATEMENT_PROVIDERS = [
  { value: 'MTN', label: 'MTN MoMo' },
  { value: 'AIRTEL', label: 'Airtel Money' },
  { value: 'AIRTEL_OPENAPI', label: 'Airtel OpenAPI' },
  { value: 'SAFARICOM', label: 'Safaricom M-Pesa' },
  { value: 'YO_PAYMENTS', label: 'Yo! Payments' },
];

const EMPTY_SEARCH: CandidateTransactionSearch = {
  reference: '',
  amount: '',
  currency: '',
  from: '',
  to: '',
};

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function formatDate(value?: string): string {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function formatMoney(amount: number | string | undefined, currency?: string): string {
  if (amount === undefined || amount === null || amount === '') return '';
  return `${currency ?? ''} ${amount}`.trim();
}

function candidateStatusTone(status?: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'SUCCESSFUL') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'PENDING') return 'warning';
  return 'neutral';
}

/** One-shot alert the moment a query/mutation error appears; redirects to login on a 401. */
function useSessionExpiryEffect(error: unknown, sessionExpired?: () => void): void {
  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      sessionExpired?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error]);
}

interface ModuleReconciliationProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleReconciliation({ loader, refreshSignal, sessionExpired }: ModuleReconciliationProps): React.ReactElement {
  const [selectedRecordId, setSelectedRecordId] = useState<number | null>(null);
  const [selectedTransaction, setSelectedTransaction] = useState<CandidateTransaction | null>(null);
  const [reason, setReason] = useState('');
  const [search, setSearch] = useState<CandidateTransactionSearch>(EMPTY_SEARCH);
  const [provider, setProvider] = useState('MTN');
  const [importedBy, setImportedBy] = useState('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);

  const unmatchedQuery = useUnmatchedReconciliationRecords(100);
  const candidateQuery = useCandidateTransactions(search, 25);
  const autoMatchMutation = useAutoMatchMutation();
  const manualMatchMutation = useManualMatchMutation();
  const importMutation = useImportStatementMutation();

  useLoaderSync(
    loader,
    unmatchedQuery.isFetching
      || candidateQuery.isFetching
      || autoMatchMutation.isPending
      || manualMatchMutation.isPending
      || importMutation.isPending,
  );
  useRefreshSignal(refreshSignal, [unmatchedQuery.refetch]);
  useSessionExpiryEffect(unmatchedQuery.error, sessionExpired);
  useSessionExpiryEffect(candidateQuery.error, sessionExpired);

  const unmatchedRows = unmatchedQuery.data ?? [];
  const candidateRows = candidateQuery.data ?? [];
  const selectedRecord = unmatchedRows.find((row) => row.id === selectedRecordId) ?? null;
  const canMatch = Boolean(selectedRecord && selectedTransaction?.txUniqueId);
  const searchActive = hasAnyCandidateFilter(search);

  function selectRecord(record: ReconciliationRecord) {
    setSelectedRecordId(record.id);
    setSelectedTransaction(null);
    // Prefill the candidate search from the record being matched - the whole
    // point of selecting a left-hand row is to go find its counterpart.
    setSearch({
      reference: record.merchantReference || record.providerReference || '',
      amount: record.amount !== undefined && record.amount !== null ? String(record.amount) : '',
      currency: record.currency || '',
      from: '',
      to: '',
    });
  }

  function resetAfterMatch() {
    setSelectedRecordId(null);
    setSelectedTransaction(null);
    setReason('');
    setSearch(EMPTY_SEARCH);
  }

  function handleAutoMatch() {
    setFeedback(null);
    autoMatchMutation.mutate(undefined, {
      onSuccess: (count) => {
        setFeedback({ tone: 'success', message: `Auto-matched ${count} record${count === 1 ? '' : 's'}.` });
      },
      onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
    });
  }

  function handleManualMatch() {
    if (!selectedRecord || !selectedTransaction?.txUniqueId) return;
    setFeedback(null);
    manualMatchMutation.mutate(
      { recordId: selectedRecord.id, transactionId: selectedTransaction.txUniqueId, reason },
      {
        onSuccess: () => {
          setFeedback({
            tone: 'success',
            message: `Matched record #${selectedRecord.id} to ${selectedTransaction.txUniqueId}.`,
          });
          resetAfterMatch();
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  function handleImport(files: FileList) {
    const file = files[0];
    if (!file) return;
    setFeedback(null);
    importMutation.mutate(
      { provider, importedBy, file },
      {
        onSuccess: (importId) => {
          setFeedback({ tone: 'success', message: `Imported statement (import #${importId}); auto-match ran.` });
        },
        onError: (error) => setFeedback({ tone: 'error', message: errorMessage(error) }),
      },
    );
  }

  const unmatchedColumns: Column<ReconciliationRecord>[] = [
    { key: 'providerCode', header: 'Provider', accessor: (r) => r.providerCode },
    { key: 'providerReference', header: 'Provider Ref', accessor: (r) => r.providerReference },
    { key: 'merchantReference', header: 'Merchant Ref', accessor: (r) => r.merchantReference },
    {
      key: 'amount',
      header: 'Amount',
      numeric: true,
      render: (r) => formatMoney(r.amount, r.currency),
      sortable: true,
      sortValue: (r) => Number(r.amount) || 0,
    },
    {
      key: 'createdAt',
      header: 'Date',
      accessor: (r) => formatDate(r.createdAt),
      sortable: true,
      sortValue: (r) => r.createdAt || '',
    },
  ];

  const candidateColumns: Column<CandidateTransaction>[] = [
    { key: 'txUniqueId', header: 'Transaction ID', accessor: (t) => t.txUniqueId },
    { key: 'txMerchantRef', header: 'Merchant Ref', accessor: (t) => t.txMerchantRef },
    {
      key: 'originalAmount',
      header: 'Amount',
      numeric: true,
      render: (t) => formatMoney(t.originalAmount, t.currency),
      sortable: true,
      sortValue: (t) => Number(t.originalAmount) || 0,
    },
    {
      key: 'status',
      header: 'Status',
      render: (t) => <Badge tone={candidateStatusTone(t.status)}>{t.status}</Badge>,
    },
    {
      key: 'createdOn',
      header: 'Date',
      accessor: (t) => formatDate(t.createdOn),
      sortable: true,
      sortValue: (t) => t.createdOn || '',
    },
  ];

  return (
    <div className="cpay-recon">
      {feedback ? (
        <Alert variant={feedback.tone === 'success' ? 'success' : 'error'}>{feedback.message}</Alert>
      ) : null}

      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <strong>Statement import</strong>
            <div style={{ minWidth: 160 }}>
              <Select id="recon-provider" value={provider} options={STATEMENT_PROVIDERS} onValueChange={setProvider} />
            </div>
            <div style={{ minWidth: 160 }}>
              <TextField
                id="recon-imported-by"
                label=""
                value={importedBy}
                onValueChange={setImportedBy}
                placeholder="Imported by (optional)"
              />
            </div>
            <FileButton accept=".csv,.xlsx" onFiles={handleImport}>
              {importMutation.isPending ? 'Uploading…' : 'Upload statement'}
            </FileButton>
            <Toolbar.Spacer />
            <Button variant="secondary" onClick={handleAutoMatch} loading={autoMatchMutation.isPending} loadingLabel="Auto-matching…">
              Auto-match
            </Button>
          </Toolbar>
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)', gap: 'var(--ios-space-4)' }}>
        <Section
          title="Unmatched provider statement rows"
          actions={
            <Button variant="ghost" className="ios-btn--sm" onClick={() => unmatchedQuery.refetch()}>
              Refresh
            </Button>
          }
        >
          {unmatchedQuery.isLoading ? <Spinner label="Loading unmatched records" /> : null}
          {unmatchedQuery.error ? <Alert variant="error">{errorMessage(unmatchedQuery.error)}</Alert> : null}
          {!unmatchedQuery.isLoading && !unmatchedQuery.error ? (
            <Table
              columns={unmatchedColumns}
              rows={unmatchedRows}
              rowKey={(r) => r.id}
              isRowSelected={(r) => r.id === selectedRecordId}
              onRowClick={selectRecord}
              pageSize={20}
              emptyText="No unmatched provider statement rows. Everything is reconciled."
            />
          ) : null}
        </Section>

        <Section title="Find a CPay transaction">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: 'var(--ios-space-3)',
              marginBottom: 'var(--ios-space-3)',
            }}
          >
            <SearchField
              value={search.reference ?? ''}
              onValueChange={(v) => setSearch((s) => ({ ...s, reference: v }))}
              onSubmit={(v) => setSearch((s) => ({ ...s, reference: v }))}
              placeholder="Reference"
              ariaLabel="Search by reference"
            />
            <TextField
              id="recon-search-amount"
              label="Amount"
              value={search.amount ?? ''}
              onValueChange={(v) => setSearch((s) => ({ ...s, amount: v }))}
            />
            <TextField
              id="recon-search-currency"
              label="Currency"
              value={search.currency ?? ''}
              onValueChange={(v) => setSearch((s) => ({ ...s, currency: v }))}
            />
            <DateField
              id="recon-search-from"
              label="From"
              value={search.from ?? ''}
              onValueChange={(v) => setSearch((s) => ({ ...s, from: v }))}
            />
            <DateField
              id="recon-search-to"
              label="To"
              value={search.to ?? ''}
              onValueChange={(v) => setSearch((s) => ({ ...s, to: v }))}
            />
          </div>
          {!searchActive ? <p>Enter a reference, amount, or date range to search for a candidate transaction.</p> : null}
          {searchActive && candidateQuery.isLoading ? <Spinner label="Searching transactions" /> : null}
          {candidateQuery.error ? <Alert variant="error">{errorMessage(candidateQuery.error)}</Alert> : null}
          {searchActive && !candidateQuery.isLoading && !candidateQuery.error ? (
            <Table
              columns={candidateColumns}
              rows={candidateRows}
              rowKey={(t) => t.id}
              isRowSelected={(t) => t.id === selectedTransaction?.id}
              onRowClick={setSelectedTransaction}
              pageSize={20}
              emptyText="No matching transactions found."
            />
          ) : null}
        </Section>
      </div>

      <Card>
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'flex-end',
            gap: 'var(--ios-space-4)',
          }}
        >
          <div>
            <strong>Record: </strong>
            {selectedRecord
              ? `#${selectedRecord.id} — ${selectedRecord.providerReference || selectedRecord.merchantReference || 'no reference'}`
              : 'None selected'}
          </div>
          <div>
            <strong>Transaction: </strong>
            {selectedTransaction ? selectedTransaction.txUniqueId : 'None selected'}
          </div>
          <div style={{ flex: '1 1 240px' }}>
            <TextField
              id="recon-match-reason"
              label="Reason / notes"
              value={reason}
              onValueChange={setReason}
              placeholder="Optional note for the audit trail"
            />
          </div>
          <Button
            variant="primary"
            disabled={!canMatch}
            loading={manualMatchMutation.isPending}
            loadingLabel="Matching…"
            onClick={handleManualMatch}
          >
            Match
          </Button>
        </div>
      </Card>
    </div>
  );
}

export default ModuleReconciliation;
