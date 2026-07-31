import React, { useEffect, useState } from 'react';
import { Card, Toolbar, Table, Select, SearchField, Checkbox, Badge, Alert, Spinner } from '../../ui';
import type { Column } from '../../ui';
import {
  useAdminAuditTrail,
  useLoaderSync,
  useRefreshSignal,
  SessionExpiredError,
  AccessDeniedError,
} from '../../shared/api/hooks';
import type { AuditTrailRow, TransactionSearch } from '../../shared/api/hooks';

/**
 * Audit L2/L3/L4: the admin audit trail list, previously a class component hand-rolling its own
 * `fetch` + `setState` data loading (see `MerchantModuleAuditTrail` for the merchant-side twin).
 * Converted to a typed function component backed by `useAdminAuditTrail` so it gets caching,
 * de-duped in-flight requests, and consistent loading/error state for free; row selection is now
 * tracked locally by row identity instead of mutating fetched rows in place.
 */

const CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'user_id', label: 'User ID' },
  { value: 'action', label: 'Action' },
  { value: 'user_name', label: 'User Name' },
];

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function rowKey(row: AuditTrailRow, index: number): React.Key {
  return row.id ?? `${row.user_id}-${row.created_on}-${index}`;
}

interface ModuleAuditTrailProps {
  loader?: (op: 'START' | 'STOP') => void;
  refreshSignal?: unknown;
  sessionExpired?: () => void;
}

function ModuleAuditTrail({ loader, refreshSignal, sessionExpired }: ModuleAuditTrailProps): React.ReactElement {
  const [category, setCategory] = useState('all');
  const [pendingValue, setPendingValue] = useState('');
  const [search, setSearch] = useState<TransactionSearch>({ value: '', category: 'all' });
  const [selected, setSelected] = useState<Set<AuditTrailRow>>(() => new Set());

  const query = useAdminAuditTrail(search, 50);

  useLoaderSync(loader, query.isFetching);
  useRefreshSignal(refreshSignal, [query.refetch]);

  useEffect(() => {
    if (query.error instanceof SessionExpiredError) {
      sessionExpired?.();
    }
    // Intentionally only re-run when the error itself changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query.error]);

  const rows = query.data ?? [];
  const accessDenied = query.error instanceof AccessDeniedError;
  const sessionExpiredError = query.error instanceof SessionExpiredError;
  const otherError = query.error && !accessDenied && !sessionExpiredError;
  const allChecked = rows.length > 0 && rows.every((row) => selected.has(row));

  function toggleAll(checked: boolean) {
    setSelected(checked ? new Set(rows) : new Set());
  }

  function toggleRow(row: AuditTrailRow, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(row);
      else next.delete(row);
      return next;
    });
  }

  function submitSearch(value: string) {
    setPendingValue(value);
    setSearch({ value, category });
  }

  const columns: Column<AuditTrailRow>[] = [
    {
      key: 'ck',
      width: 44,
      header: <Checkbox checked={allChecked} onCheckedChange={toggleAll} ariaLabel="Select all audit trail rows" />,
      render: (row) => (
        <Checkbox
          checked={selected.has(row)}
          onCheckedChange={(checked) => toggleRow(row, checked)}
          ariaLabel={`Select row for ${row.user_name ?? row.user_id ?? 'audit entry'}`}
        />
      ),
    },
    { key: 'created_on', header: 'Created On', accessor: (r) => r.created_on, sortable: true, sortValue: (r) => r.created_on || '', width: 190 },
    { key: 'user_id', header: 'User ID', accessor: (r) => r.user_id, sortable: true, sortValue: (r) => r.user_id || '', width: 150 },
    { key: 'user_name', header: 'User Name', accessor: (r) => r.user_name, sortable: true, sortValue: (r) => r.user_name || '', width: 160 },
    { key: 'action', header: 'Action', render: (r) => <Badge tone="info">{r.action}</Badge> },
  ];

  return (
    <Card flush>
      <div style={{ padding: 'var(--ios-space-4)' }}>
        <Toolbar>
          <div style={{ minWidth: 200 }}>
            <Select id="audit-category" value={category} options={CATEGORIES} onValueChange={setCategory} />
          </div>
          <Toolbar.Spacer />
          <SearchField
            value={pendingValue}
            onValueChange={setPendingValue}
            onSubmit={submitSearch}
            placeholder="Search audit trail"
            ariaLabel="Search audit trail"
          />
        </Toolbar>
      </div>

      {query.isLoading ? <Spinner label="Loading audit trail" /> : null}
      {accessDenied ? <Alert variant="error">{errorMessage(query.error) || 'You are not allowed access to this section.'}</Alert> : null}
      {otherError ? <Alert variant="error">{errorMessage(query.error)}</Alert> : null}

      {!query.isLoading && !accessDenied && !otherError && !sessionExpiredError ? (
        <Table
          columns={columns}
          rows={rows}
          rowKey={rowKey}
          pageSize={50}
          isRowSelected={(row) => selected.has(row)}
          renderDetail={(row) => (
            <div className="ios-grid">
              <div><strong>Created On:</strong> {row.created_on}</div>
              <div><strong>Action:</strong> {row.action}</div>
            </div>
          )}
          emptyText="No audit records to display."
        />
      ) : null}
    </Card>
  );
}

export default ModuleAuditTrail;
