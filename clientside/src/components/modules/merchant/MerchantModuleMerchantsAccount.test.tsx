import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MerchantModuleMerchantsAccount from './MerchantModuleMerchantsAccount';
import { ApiError } from '../../../shared/api/httpClient';
import type { MerchantStatementResult } from '../../../shared/api/hooks';

const { useMerchantOwnStatement } = vi.hoisted(() => ({
  useMerchantOwnStatement: vi.fn(),
}));

vi.mock('../../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../../shared/api/hooks')>('../../../shared/api/hooks');
  return {
    ...actual,
    useMerchantOwnStatement,
  };
});

const { downloadStatementExport } = vi.hoisted(() => ({
  downloadStatementExport: vi.fn(),
}));

vi.mock('../../../shared/export/statementExport', () => ({
  downloadStatementExport,
}));

function queryResult(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    data: undefined,
    isLoading: false,
    isFetching: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  };
}

const STATEMENT: MerchantStatementResult = {
  rows: [
    { id: 1, created_on: '2026-07-29T10:00:00', narrative: 'Payout', description: 'Vendor payment', amount: 2500, tx_type: 'DR', balances: 7500 },
  ],
  total: 1,
  balances: 'UGX 7,500',
};

beforeEach(() => {
  vi.clearAllMocks();
  useMerchantOwnStatement.mockReturnValue(queryResult({ data: { rows: [], total: 0, balances: '' } }));
});

describe('MerchantModuleMerchantsAccount', () => {
  it('shows a loading state while the statement query is in flight', () => {
    useMerchantOwnStatement.mockReturnValue(queryResult({ data: undefined, isLoading: true, isFetching: true }));

    render(<MerchantModuleMerchantsAccount />);

    expect(screen.getByRole('status', { name: 'Loading account statement' })).toBeInTheDocument();
  });

  it('shows an error state when the statement query fails', () => {
    useMerchantOwnStatement.mockReturnValue(queryResult({ data: undefined, error: new ApiError('Boom', 500) }));

    render(<MerchantModuleMerchantsAccount />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows a first-payment empty state when there are no statement rows', () => {
    render(<MerchantModuleMerchantsAccount />);

    expect(screen.getByText(/No statement entries yet/i)).toBeInTheDocument();
  });

  it('renders statement rows and the available balance', () => {
    useMerchantOwnStatement.mockReturnValue(queryResult({ data: STATEMENT }));

    render(<MerchantModuleMerchantsAccount />);

    expect(screen.getByText('UGX 7,500')).toBeInTheDocument();
    expect(screen.getByText('Payout: Vendor payment')).toBeInTheDocument();
  });

  it('blocks a statement download until a date range has been searched', async () => {
    render(<MerchantModuleMerchantsAccount />);

    fireEvent.click(screen.getByRole('button', { name: /Download CSV/ }));

    expect(await screen.findByText(/Select a start and end date/i)).toBeInTheDocument();
    expect(downloadStatementExport).not.toHaveBeenCalled();
  });

  it('downloads the statement for the applied date range using the default self-service path', async () => {
    downloadStatementExport.mockResolvedValue(undefined);
    render(<MerchantModuleMerchantsAccount />);

    fireEvent.click(screen.getByRole('button', { name: /^Search$/ }));
    fireEvent.change(screen.getByLabelText('Start Date'), { target: { value: '2026-07-01' } });
    fireEvent.change(screen.getByLabelText('End Date'), { target: { value: '2026-07-31' } });
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));

    fireEvent.click(screen.getByRole('button', { name: /Download XLSX/ }));

    await waitFor(() =>
      expect(downloadStatementExport).toHaveBeenCalledWith({
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        format: 'xlsx',
      }),
    );
  });
});
