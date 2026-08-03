import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ModulePayoutApprovals from './ModulePayoutApprovals';
import { ApiError } from '../../shared/api/httpClient';

const {
  usePendingPayoutApprovals,
  usePayoutApproveMutation,
  usePayoutRejectMutation,
  usePayoutCancelMutation,
} = vi.hoisted(() => ({
  usePendingPayoutApprovals: vi.fn(),
  usePayoutApproveMutation: vi.fn(),
  usePayoutRejectMutation: vi.fn(),
  usePayoutCancelMutation: vi.fn(),
}));

vi.mock('../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/hooks')>(
    '../../shared/api/hooks',
  );
  return {
    ...actual,
    usePendingPayoutApprovals,
    usePayoutApproveMutation,
    usePayoutRejectMutation,
    usePayoutCancelMutation,
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

const ROW = {
  id: 42,
  payout_reference: 'REF-100',
  merchant_id: 7,
  merchant_number: 'M100',
  amount: 2500000,
  currency: 'UGX',
  channel_code: 'MTN_MOMO',
  country: 'UG',
  beneficiary_reference: '256700000001',
  trigger_reason: 'PER_TRANSACTION_LIMIT',
  queue_status: 'PENDING_APPROVAL',
  requested_by: 'merchant-app',
  requested_at: '2026-08-02T22:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  usePendingPayoutApprovals.mockReturnValue(queryResult({ data: [ROW] }));
  usePayoutApproveMutation.mockReturnValue(mutationResult());
  usePayoutRejectMutation.mockReturnValue(mutationResult());
  usePayoutCancelMutation.mockReturnValue(mutationResult());
});

describe('ModulePayoutApprovals', () => {
  it('shows a loading state while the queue is in flight', () => {
    usePendingPayoutApprovals.mockReturnValue(
      queryResult({ data: undefined, isLoading: true, isFetching: true }),
    );

    render(<ModulePayoutApprovals />);

    expect(
      screen.getByRole('status', { name: 'Loading payout approvals' }),
    ).toBeInTheDocument();
  });

  it('shows an error state when the queue fails', () => {
    usePendingPayoutApprovals.mockReturnValue(
      queryResult({ data: undefined, error: new ApiError('Boom', 500) }),
    );

    render(<ModulePayoutApprovals />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('renders queued payouts with reference, amount, channel, and trigger', () => {
    render(<ModulePayoutApprovals />);

    expect(screen.getByText('REF-100')).toBeInTheDocument();
    expect(screen.getByText(/UGX\s*2500000/)).toBeInTheDocument();
    expect(screen.getByText('MTN_MOMO')).toBeInTheDocument();
    expect(screen.getByText('PER_TRANSACTION_LIMIT')).toBeInTheDocument();
  });

  it('shows an empty state when the queue is clear', () => {
    usePendingPayoutApprovals.mockReturnValue(queryResult({ data: [] }));

    render(<ModulePayoutApprovals />);

    expect(screen.getByText(/No payouts awaiting approval/i)).toBeInTheDocument();
  });

  it('requires a row selection and a checker actor before enabling approval', () => {
    render(<ModulePayoutApprovals />);

    const approveButton = screen.getByRole('button', { name: 'Approve & execute' });
    expect(approveButton).toBeDisabled();

    fireEvent.click(screen.getByText('REF-100'));
    expect(approveButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Approved by (checker — must differ from the requester)'), {
      target: { value: 'finance-checker' },
    });
    expect(approveButton).not.toBeDisabled();
  });

  it('approves the selected payout with the checker actor and reports the result', () => {
    const mutate = vi.fn((_payload, options) =>
      options.onSuccess?.({ status: 'SUBMITTED', transactionId: 'TX-1' }),
    );
    usePayoutApproveMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModulePayoutApprovals />);

    fireEvent.click(screen.getByText('REF-100'));
    fireEvent.change(screen.getByLabelText('Approved by (checker — must differ from the requester)'), {
      target: { value: 'finance-checker' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Approve & execute' }));

    expect(mutate).toHaveBeenCalledWith(
      { queueId: 42, approvedBy: 'finance-checker' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('approved and submitted');
  });

  it('rejects the selected payout with a reason', () => {
    const mutate = vi.fn((_payload, options) => options.onSuccess?.({ status: 'REJECTED' }));
    usePayoutRejectMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModulePayoutApprovals />);

    fireEvent.click(screen.getByText('REF-100'));
    fireEvent.change(screen.getByLabelText('Rejected / cancelled by (checker)'), {
      target: { value: 'finance-checker' },
    });
    fireEvent.change(screen.getByLabelText('Reason'), {
      target: { value: 'duplicate request' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));

    expect(mutate).toHaveBeenCalledWith(
      { queueId: 42, rejectedBy: 'finance-checker', reason: 'duplicate request' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('rejected');
  });

  it('cancels the selected payout without executing it', () => {
    const mutate = vi.fn((_payload, options) => options.onSuccess?.({ status: 'CANCELLED' }));
    usePayoutCancelMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModulePayoutApprovals />);

    fireEvent.click(screen.getByText('REF-100'));
    fireEvent.change(screen.getByLabelText('Rejected / cancelled by (checker)'), {
      target: { value: 'ops-checker' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(mutate).toHaveBeenCalledWith(
      { queueId: 42, cancelledBy: 'ops-checker' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('cancelled');
  });

  it('shows an error banner when approval fails', async () => {
    const mutate = vi.fn((_payload, options) =>
      options.onError?.(new ApiError('Approval requires a different actor', 400)),
    );
    usePayoutApproveMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModulePayoutApprovals />);

    fireEvent.click(screen.getByText('REF-100'));
    fireEvent.change(screen.getByLabelText('Approved by (checker — must differ from the requester)'), {
      target: { value: 'merchant-app' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Approve & execute' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('different actor');
    });
  });
});
