import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import ModuleReconciliation from './ModuleReconciliation';
import { ApiError } from '../../shared/api/httpClient';
import type {
  ReconciliationRecord,
  CandidateTransaction,
} from '../../shared/api/hooks';

const {
  useUnmatchedReconciliationRecords,
  useCandidateTransactions,
  useAutoMatchMutation,
  useManualMatchMutation,
  useImportStatementMutation,
} = vi.hoisted(() => ({
  useUnmatchedReconciliationRecords: vi.fn(),
  useCandidateTransactions: vi.fn(),
  useAutoMatchMutation: vi.fn(),
  useManualMatchMutation: vi.fn(),
  useImportStatementMutation: vi.fn(),
}));

vi.mock('../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/hooks')>(
    '../../shared/api/hooks',
  );
  return {
    ...actual,
    useUnmatchedReconciliationRecords,
    useCandidateTransactions,
    useAutoMatchMutation,
    useManualMatchMutation,
    useImportStatementMutation,
  };
});

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

const RECORD: ReconciliationRecord = {
  id: 7,
  providerCode: 'MTN',
  channelCode: 'mtn_momo',
  providerReference: 'PR-100',
  merchantReference: 'MREF-100',
  transactionId: undefined,
  amount: 5000,
  currency: 'UGX',
  matchStatus: 'UNMATCHED',
  matchReason: undefined,
  createdAt: '2026-07-29T10:00:00',
};

const CANDIDATE: CandidateTransaction = {
  id: 42,
  txUniqueId: 'TX-UNIQUE-42',
  txMerchantRef: 'MREF-100',
  originalAmount: 5000,
  currency: 'UGX',
  status: 'SUCCESSFUL',
  txType: 'PAYIN',
  createdOn: '2026-07-29T10:01:00',
  payerNumber: '2567xxxxxxx',
};

beforeEach(() => {
  vi.clearAllMocks();
  useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [] }));
  useCandidateTransactions.mockReturnValue(queryResult({ data: [] }));
  useAutoMatchMutation.mockReturnValue(mutationResult());
  useManualMatchMutation.mockReturnValue(mutationResult());
  useImportStatementMutation.mockReturnValue(mutationResult());
});

describe('ModuleReconciliation', () => {
  it('shows a loading state for the unmatched panel while the query is in flight', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(
      queryResult({ data: undefined, isLoading: true, isFetching: true }),
    );

    render(<ModuleReconciliation />);

    expect(screen.getByRole('status', { name: 'Loading unmatched records' })).toBeInTheDocument();
  });

  it('shows an error state when the unmatched query fails', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(
      queryResult({ data: undefined, error: new ApiError('Boom', 500) }),
    );

    render(<ModuleReconciliation />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows an empty state when there are no unmatched records', () => {
    render(<ModuleReconciliation />);

    expect(screen.getByText(/No unmatched provider statement rows/i)).toBeInTheDocument();
  });

  it('renders unmatched rows with provider, reference, amount, currency, and date', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));

    render(<ModuleReconciliation />);

    expect(screen.getByText('MTN')).toBeInTheDocument();
    expect(screen.getByText('PR-100')).toBeInTheDocument();
    expect(screen.getByText('MREF-100')).toBeInTheDocument();
    expect(screen.getByText(/UGX\s*5000/)).toBeInTheDocument();
  });

  it('prompts for a filter before searching for candidate transactions', () => {
    render(<ModuleReconciliation />);

    expect(
      screen.getByText(/Enter a reference, amount, or date range/i),
    ).toBeInTheDocument();
  });

  it('selecting an unmatched row prefills the candidate search and shows results once available', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));
    useCandidateTransactions.mockReturnValue(queryResult({ data: [CANDIDATE] }));

    render(<ModuleReconciliation />);

    fireEvent.click(screen.getByText('PR-100'));

    expect(screen.getByText('TX-UNIQUE-42')).toBeInTheDocument();
  });

  it('enables Match only once a record and a candidate transaction are both selected, then submits the match', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));
    useCandidateTransactions.mockReturnValue(queryResult({ data: [CANDIDATE] }));
    const mutate = vi.fn();
    useManualMatchMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleReconciliation />);

    const matchButton = screen.getByRole('button', { name: 'Match' });
    expect(matchButton).toBeDisabled();

    fireEvent.click(screen.getByText('PR-100'));
    fireEvent.click(screen.getByText('TX-UNIQUE-42'));
    expect(matchButton).not.toBeDisabled();

    fireEvent.change(screen.getByLabelText('Reason / notes'), {
      target: { value: 'confirmed via bank statement' },
    });
    fireEvent.click(matchButton);

    expect(mutate).toHaveBeenCalledWith(
      { recordId: 7, transactionId: 'TX-UNIQUE-42', reason: 'confirmed via bank statement' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it('shows a success banner and clears the selection when a manual match succeeds', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));
    useCandidateTransactions.mockReturnValue(queryResult({ data: [CANDIDATE] }));
    const mutate = vi.fn((_payload, options) => options.onSuccess?.('updated'));
    useManualMatchMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleReconciliation />);

    fireEvent.click(screen.getByText('PR-100'));
    fireEvent.click(screen.getByText('TX-UNIQUE-42'));
    fireEvent.click(screen.getByRole('button', { name: 'Match' }));

    expect(screen.getByRole('alert')).toHaveTextContent(/Matched record #7/);
  });

  it('shows an error banner when a manual match fails', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));
    useCandidateTransactions.mockReturnValue(queryResult({ data: [CANDIDATE] }));
    const mutate = vi.fn((_payload, options) => options.onError?.(new ApiError('Transaction not found', 404)));
    useManualMatchMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleReconciliation />);

    fireEvent.click(screen.getByText('PR-100'));
    fireEvent.click(screen.getByText('TX-UNIQUE-42'));
    fireEvent.click(screen.getByRole('button', { name: 'Match' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Transaction not found');
  });

  it('calls the auto-match mutation and reports how many records were matched', () => {
    const mutate = vi.fn((_arg, options) => options.onSuccess?.(3));
    useAutoMatchMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleReconciliation />);

    fireEvent.click(screen.getByRole('button', { name: 'Auto-match' }));

    expect(mutate).toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Auto-matched 3 records.');
  });

  it('uploads a selected statement file with the chosen provider', () => {
    const mutate = vi.fn();
    useImportStatementMutation.mockReturnValue(mutationResult({ mutate }));

    const { container } = render(<ModuleReconciliation />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['provider_reference,amount\nPR-1,100\n'], 'statement.csv', {
      type: 'text/csv',
    });

    fireEvent.change(fileInput, { target: { files: [file] } });

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ provider: 'MTN', file }),
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it('disables the Match button while a manual match is pending', () => {
    useUnmatchedReconciliationRecords.mockReturnValue(queryResult({ data: [RECORD] }));
    useCandidateTransactions.mockReturnValue(queryResult({ data: [CANDIDATE] }));
    useManualMatchMutation.mockReturnValue(mutationResult({ isPending: true }));

    render(<ModuleReconciliation />);

    fireEvent.click(screen.getByText('PR-100'));
    fireEvent.click(screen.getByText('TX-UNIQUE-42'));

    expect(screen.getByRole('button', { name: /Matching/ })).toBeDisabled();
  });
});
