import React from 'react';
import { Pagination } from './Pagination';
import { ChevronRightIcon, ChevronDownIcon } from './Icons';

export interface Column<Row> {
  key: string;
  header: React.ReactNode;
  /** Cell renderer. Falls back to `accessor` then empty. */
  render?: (row: Row, index: number) => React.ReactNode;
  /** Simple value accessor (used for default rendering and sorting). */
  accessor?: (row: Row) => React.ReactNode;
  numeric?: boolean;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  sortValue?: (row: Row) => string | number;
  width?: number | string;
  className?: string;
}

interface SortState {
  key: string;
  dir: 'asc' | 'desc';
}

interface TableProps<Row> {
  columns: Column<Row>[];
  rows: Row[];
  rowKey: (row: Row, index: number) => React.Key;
  onRowClick?: (row: Row) => void;
  isRowSelected?: (row: Row) => boolean;
  /** Group rows under sticky group-header bands (in encounter order). */
  groupBy?: (row: Row) => string;
  renderGroupHeader?: (group: string, rows: Row[]) => React.ReactNode;
  /** When set, each row gets an expander revealing this detail panel. */
  renderDetail?: (row: Row) => React.ReactNode;
  emptyText?: React.ReactNode;
  /** Enable built-in client-side pagination. */
  pageSize?: number;
  initialSort?: SortState;
  stickyHeader?: boolean;
}

function toComparable(v: React.ReactNode): string | number {
  if (typeof v === 'number') return v;
  if (v == null) return '';
  return String(v).toLowerCase();
}

export function Table<Row>({
  columns,
  rows,
  rowKey,
  onRowClick,
  isRowSelected,
  groupBy,
  renderGroupHeader,
  renderDetail,
  emptyText = 'No records to display.',
  pageSize,
  initialSort,
  stickyHeader = true,
}: TableProps<Row>): React.ReactElement {
  const [sort, setSort] = React.useState<SortState | undefined>(initialSort);
  const [page, setPage] = React.useState(1);
  const [expanded, setExpanded] = React.useState<Set<React.Key>>(() => new Set());

  const hasExpander = Boolean(renderDetail);
  const colCount = columns.length + (hasExpander ? 1 : 0);

  const sorted = React.useMemo(() => {
    if (!sort) return rows;
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return rows;
    const value = col.sortValue ?? (col.accessor ? (r: Row) => toComparable(col.accessor!(r)) : undefined);
    if (!value) return rows;
    const factor = sort.dir === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = value(a);
      const bv = value(b);
      if (av < bv) return -1 * factor;
      if (av > bv) return 1 * factor;
      return 0;
    });
  }, [rows, sort, columns]);

  const total = sorted.length;
  const paged = React.useMemo(() => {
    if (!pageSize) return sorted;
    const start = (page - 1) * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, pageSize, page]);

  React.useEffect(() => {
    // keep page in range when rows change
    if (pageSize) {
      const max = Math.max(1, Math.ceil(total / pageSize));
      if (page > max) setPage(max);
    }
  }, [total, pageSize, page]);

  function toggleSort(col: Column<Row>) {
    if (!col.sortable) return;
    setSort((prev) => {
      if (!prev || prev.key !== col.key) return { key: col.key, dir: 'asc' };
      return { key: col.key, dir: prev.dir === 'asc' ? 'desc' : 'asc' };
    });
  }

  function toggleExpand(key: React.Key) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function renderCells(row: Row, index: number) {
    return columns.map((col) => {
      const content = col.render ? col.render(row, index) : col.accessor ? col.accessor(row) : null;
      return (
        <td
          key={col.key}
          className={`${col.numeric ? 'ios-td--num' : ''} ${col.className ?? ''}`.trim()}
          style={col.align && !col.numeric ? { textAlign: col.align } : undefined}
        >
          {content}
        </td>
      );
    });
  }

  const bodyRows: React.ReactNode[] = [];
  let lastGroup: string | null = null;
  paged.forEach((row, i) => {
    if (groupBy) {
      const g = groupBy(row);
      if (g !== lastGroup) {
        lastGroup = g;
        const groupRows = paged.filter((r) => groupBy(r) === g);
        bodyRows.push(
          <tr key={`group-${g}`} className="ios-table__group">
            <td colSpan={colCount}>{renderGroupHeader ? renderGroupHeader(g, groupRows) : g}</td>
          </tr>,
        );
      }
    }
    const key = rowKey(row, i);
    const selected = isRowSelected?.(row);
    const isOpen = expanded.has(key);
    bodyRows.push(
      <tr
        key={key}
        className={`${selected ? 'ios-tr--selected' : ''} ${onRowClick ? 'ios-tr--clickable' : ''}`.trim()}
        onClick={onRowClick ? () => onRowClick(row) : undefined}
      >
        {hasExpander ? (
          <td style={{ width: 36 }}>
            <button
              type="button"
              className="ios-expander"
              aria-expanded={isOpen}
              aria-label={isOpen ? 'Collapse' : 'Expand'}
              onClick={(e) => {
                e.stopPropagation();
                toggleExpand(key);
              }}
            >
              {isOpen ? <ChevronDownIcon size={16} /> : <ChevronRightIcon size={16} />}
            </button>
          </td>
        ) : null}
        {renderCells(row, i)}
      </tr>,
    );
    if (hasExpander && isOpen) {
      bodyRows.push(
        <tr key={`${key}-detail`} className="ios-table__detail">
          <td colSpan={colCount}>
            <div className="ios-table__detail-inner">{renderDetail!(row)}</div>
          </td>
        </tr>,
      );
    }
  });

  return (
    <div>
      <div className="ios-table-wrap">
        <table className="ios-table">
          <thead style={stickyHeader ? undefined : { position: 'static' }}>
            <tr>
              {hasExpander ? <th style={{ width: 36 }} aria-label="Expand" /> : null}
              {columns.map((col) => {
                const isSorted = sort?.key === col.key;
                return (
                  <th
                    key={col.key}
                    className={`${col.numeric ? 'ios-th--num' : ''} ${col.sortable ? 'ios-th--sortable' : ''}`.trim()}
                    style={{
                      width: col.width,
                      textAlign: col.align && !col.numeric ? col.align : undefined,
                    }}
                    onClick={() => toggleSort(col)}
                    aria-sort={isSorted ? (sort!.dir === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    {col.header}
                    {isSorted ? <span aria-hidden> {sort!.dir === 'asc' ? '↑' : '↓'}</span> : null}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {total === 0 ? (
              <tr>
                <td colSpan={colCount}>
                  <div className="ios-table__empty">{emptyText}</div>
                </td>
              </tr>
            ) : (
              bodyRows
            )}
          </tbody>
        </table>
      </div>
      {pageSize ? (
        <Pagination page={page} pageSize={pageSize} total={total} onPageChange={setPage} />
      ) : null}
    </div>
  );
}
