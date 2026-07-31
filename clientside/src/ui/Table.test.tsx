import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { Table, type Column } from './Table';

interface Row {
  id: number;
  name: string;
  amount: number;
}

const rows: Row[] = [
  { id: 1, name: 'Alpha', amount: 100 },
  { id: 2, name: 'Beta', amount: 200 },
];

const columns: Column<Row>[] = [
  { key: 'name', header: 'Name', accessor: (r) => r.name },
  { key: 'amount', header: 'Amount', accessor: (r) => r.amount, numeric: true },
];

/**
 * Table.tsx renders either the scrolling table or the stacked-card fallback
 * (never both — see `useCardLayout` in Table.tsx), driven by
 * `window.matchMedia('(max-width: 640px)')`. jsdom has no real layout engine
 * and no `matchMedia` implementation by default, so tests that want the card
 * branch must stub it explicitly; tests that don't call this get the table
 * branch, matching every pre-existing Table consumer's tests untouched.
 */
function mockMatchMedia(matches: boolean) {
  const mql = {
    matches,
    media: '',
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  };
  window.matchMedia = vi.fn().mockReturnValue(mql);
  return mql;
}

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe('Table (default / wide viewport)', () => {
  test('renders the scrolling table and no card markup when matchMedia is unavailable', () => {
    // @ts-expect-error simulate an environment without matchMedia support
    delete window.matchMedia;
    const { container } = render(<Table columns={columns} rows={rows} rowKey={(r) => r.id} />);

    expect(container.querySelectorAll('.ios-table-wrap table tbody tr')).toHaveLength(2);
    expect(container.querySelector('.ios-table-cards')).toBeNull();
  });

  test('renders the table (not cards) when the narrow-viewport query does not match', () => {
    mockMatchMedia(false);
    const { container } = render(<Table columns={columns} rows={rows} rowKey={(r) => r.id} />);

    expect(container.querySelectorAll('.ios-table-wrap table tbody tr')).toHaveLength(2);
    expect(container.querySelector('.ios-table-cards')).toBeNull();
  });
});

describe('Table (narrow / card viewport)', () => {
  test('renders stacked cards instead of the table when the narrow-viewport query matches', () => {
    mockMatchMedia(true);
    const { container } = render(<Table columns={columns} rows={rows} rowKey={(r) => r.id} />);

    expect(container.querySelector('.ios-table-wrap')).toBeNull();
    const cards = container.querySelectorAll('.ios-table-cards .ios-table-card');
    expect(cards).toHaveLength(2);
  });

  test('each card exposes the same column label/value pairs as the equivalent table row', () => {
    mockMatchMedia(true);
    const { container } = render(<Table columns={columns} rows={rows} rowKey={(r) => r.id} />);

    const firstCard = container.querySelectorAll('.ios-table-cards .ios-table-card')[0];
    const labels = Array.from(firstCard.querySelectorAll('.ios-table-card__label')).map((el) => el.textContent);
    const values = Array.from(firstCard.querySelectorAll('.ios-table-card__value')).map((el) => el.textContent);

    expect(labels).toEqual(['Name', 'Amount']);
    expect(values).toEqual(['Alpha', '100']);
  });

  test('clicking a card fires onRowClick the same as clicking a table row would', () => {
    mockMatchMedia(true);
    const onRowClick = vi.fn();
    const { container } = render(
      <Table columns={columns} rows={rows} rowKey={(r) => r.id} onRowClick={onRowClick} />,
    );

    const card = container.querySelectorAll('.ios-table-cards .ios-table-card')[1];
    fireEvent.click(card);
    expect(onRowClick).toHaveBeenCalledWith(rows[1]);
    expect(card.className).toContain('ios-table-card--clickable');
  });

  test('marks the selected card with the selected modifier class', () => {
    mockMatchMedia(true);
    const { container } = render(
      <Table
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        isRowSelected={(r) => r.id === 2}
      />,
    );

    const [first, second] = container.querySelectorAll('.ios-table-cards .ios-table-card');
    expect(first.className).not.toContain('ios-table-card--selected');
    expect(second.className).toContain('ios-table-card--selected');
  });

  test('expandable rows can be toggled open from the card view', () => {
    mockMatchMedia(true);
    render(
      <Table
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        renderDetail={(r) => <div>Detail for {r.name}</div>}
      />,
    );

    const expandButtons = screen.getAllByRole('button', { name: 'Expand' });
    fireEvent.click(expandButtons[0]);
    expect(screen.getByText('Detail for Alpha')).toBeInTheDocument();
  });

  test('shows the empty state in the card container when there are no rows', () => {
    mockMatchMedia(true);
    const { container } = render(
      <Table columns={columns} rows={[]} rowKey={(r) => r.id} emptyText="Nothing here." />,
    );

    const emptyNodes = container.querySelectorAll('.ios-table__empty');
    expect(emptyNodes).toHaveLength(1);
    expect(emptyNodes[0].textContent).toBe('Nothing here.');
  });

  test('subscribes to and unsubscribes from the media query on mount/unmount', () => {
    const mql = mockMatchMedia(true);
    const { unmount } = render(<Table columns={columns} rows={rows} rowKey={(r) => r.id} />);

    expect(mql.addEventListener).toHaveBeenCalledWith('change', expect.any(Function));
    unmount();
    expect(mql.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });
});
