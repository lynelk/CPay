import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ModuleMerchantsAccount from './ModuleMerchantsAccount';
import { ApiError } from '../../shared/api/httpClient';
import type { MerchantStatementResult } from '../../shared/api/hooks';

const { useAdminMerchantStatement, useRecordMerchantTransactionMutation } = vi.hoisted(() => ({
  useAdminMerchantStatement: vi.fn(),
  useRecordMerchantTransactionMutation: vi.fn(),
}));

vi.mock('../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/hooks')>('../../shared/api/hooks');
  return {
    ...actual,
    useAdminMerchantStatement,
    useRecordMerchantTransactionMutation,
  };
});

const { downloadStatementExport } = vi.hoisted(() => ({
  downloadStatementExport: vi.fn(),
}));

vi.mock('../../shared/export/statementExport', () => ({
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

function mutationResult(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    ...overrides,
  };
}

const STATEMENT: MerchantStatementResult = {
  rows: [
    { id: 1, created_on: '2026-07-29T10:00:00', narrative: 'Payin', description: 'Test payment', amount: 5000, tx_type: 'CR', balances: 10000 },
  ],
  total: 1,
  balances: 'UGX 10,000',
};

const MERCHANT_ACCOUNT = { id: 5, account_number: '1000005', name: 'Acme Ltd' };

const BASE_PROPS = {
  title: 'Merchant (Acme Ltd) - 1000005',
  statementDialogStateOpened: false,
  openOrCloseStatementDialog: vi.fn(),
  openMerchantAccount: MERCHANT_ACCOUNT,
};

beforeEach(() => {
  vi.clearAllMocks();
  useAdminMerchantStatement.mockReturnValue(queryResult({ data: { rows: [], total: 0, balances: '' } }));
  useRecordMerchantTransactionMutation.mockReturnValue(mutationResult());
});

describe('ModuleMerchantsAccount', () => {
  it('shows a loading state while the statement query is in flight', () => {
    useAdminMerchantStatement.mockReturnValue(queryResult({ data: undefined, isLoading: true, isFetching: true }));

    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    expect(screen.getByRole('status', { name: 'Loading account statement' })).toBeInTheDocument();
  });

  it('shows an error state when the statement query fails', () => {
    useAdminMerchantStatement.mockReturnValue(queryResult({ data: undefined, error: new ApiError('Boom', 500) }));

    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows an empty state when there are no statement rows', () => {
    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    expect(screen.getByText(/No statement entries to display/i)).toBeInTheDocument();
  });

  it('renders statement rows and the available balance', () => {
    useAdminMerchantStatement.mockReturnValue(queryResult({ data: STATEMENT }));

    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    expect(screen.getByText('UGX 10,000')).toBeInTheDocument();
    expect(screen.getByText('Payin: Test payment')).toBeInTheDocument();
  });

  it('shows inline validation errors instead of submitting an incomplete record-transaction form', () => {
    const mutate = vi.fn();
    useRecordMerchantTransactionMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    fireEvent.click(screen.getByRole('button', { name: /Record Transaction/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Transaction type is required')).toBeInTheDocument();
    expect(screen.getByText('Description is required')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid amount')).toBeInTheDocument();
    expect(mutate).not.toHaveBeenCalled();
  });

  it('submits a valid record-transaction form with the current merchant id', () => {
    const mutate = vi.fn();
    useRecordMerchantTransactionMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    fireEvent.click(screen.getByRole('button', { name: /Record Transaction/ }));
    fireEvent.change(screen.getByLabelText('Transaction Type'), { target: { value: 'FLOAT CREDIT' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Manual float top-up' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ tx_type: 'FLOAT CREDIT', amount: '100', description: 'Manual float top-up', merchant_id: 5 }),
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it('blocks a statement download until a date range has been searched', async () => {
    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    fireEvent.click(screen.getByRole('button', { name: /Download CSV/ }));

    expect(await screen.findByText(/Select a start and end date/i)).toBeInTheDocument();
    expect(downloadStatementExport).not.toHaveBeenCalled();
  });

  it('downloads the statement from the admin export endpoint once a date range is applied', async () => {
    downloadStatementExport.mockResolvedValue(undefined);
    render(<ModuleMerchantsAccount {...BASE_PROPS} />);

    fireEvent.click(screen.getByRole('button', { name: /^Search$/ }));
    fireEvent.change(screen.getByLabelText('Start Date'), { target: { value: '2026-07-01' } });
    fireEvent.change(screen.getByLabelText('End Date'), { target: { value: '2026-07-31' } });
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));

    fireEvent.click(screen.getByRole('button', { name: /Download CSV/ }));

    await waitFor(() =>
      expect(downloadStatementExport).toHaveBeenCalledWith({
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        format: 'csv',
        path: '/api/v2/admin/merchants/1000005/statements',
      }),
    );
  });
});
