import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ModuleFinanceClose from './ModuleFinanceClose';
import { ApiError } from '../../shared/api/httpClient';

const {
  useFinanceCloseSummary,
  useFinanceCloseSubmitMutation,
  useFinanceCloseApproveMutation,
  useFinanceCloseRejectMutation,
} = vi.hoisted(() => ({
  useFinanceCloseSummary: vi.fn(),
  useFinanceCloseSubmitMutation: vi.fn(),
  useFinanceCloseApproveMutation: vi.fn(),
  useFinanceCloseRejectMutation: vi.fn(),
}));

vi.mock('../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/hooks')>(
    '../../shared/api/hooks',
  );
  return {
    ...actual,
    useFinanceCloseSummary,
    useFinanceCloseSubmitMutation,
    useFinanceCloseApproveMutation,
    useFinanceCloseRejectMutation,
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

const SUMMARY = {
  currency: 'UGX',
  statementsReceived: 3,
  unmatchedRecords: 1,
  parkedCallbacks: 2,
  openControls: 0,
  closeStatus: 'NOT_CLOSED',
  pendingSubmissions: [
    {
      closeDate: '2026-08-02',
      currency: 'UGX',
      submittedBy: 'finance-maker',
      submittedAt: '2026-08-02T22:00:00',
      status: 'PENDING_APPROVAL',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  useFinanceCloseSummary.mockReturnValue(queryResult({ data: SUMMARY }));
  useFinanceCloseSubmitMutation.mockReturnValue(mutationResult());
  useFinanceCloseApproveMutation.mockReturnValue(mutationResult());
  useFinanceCloseRejectMutation.mockReturnValue(mutationResult());
});

describe('ModuleFinanceClose', () => {
  it('shows the daily-close summary counters from the backend report', () => {
    render(<ModuleFinanceClose />);

    expect(screen.getByText('Statements received')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Unmatched records')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('Parked callbacks')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Open controls')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('shows a loading state while the finance summary is in flight', () => {
    useFinanceCloseSummary.mockReturnValue(
      queryResult({ data: undefined, isLoading: true, isFetching: true }),
    );

    render(<ModuleFinanceClose />);

    expect(
      screen.getByRole('status', { name: 'Loading finance summary' }),
    ).toBeInTheDocument();
  });

  it('shows an error state when the finance summary fails', () => {
    useFinanceCloseSummary.mockReturnValue(
      queryResult({ data: undefined, error: new ApiError('Boom', 500) }),
    );

    render(<ModuleFinanceClose />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('submits a daily close with the chosen date, currency, and maker actor', () => {
    const mutate = vi.fn((_payload, options) => options.onSuccess?.(99));
    useFinanceCloseSubmitMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleFinanceClose />);

    fireEvent.change(screen.getByLabelText('Close date'), { target: { value: '2026-08-02' } });
    fireEvent.change(screen.getByLabelText('Submitted by (maker)'), {
      target: { value: 'finance-maker' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Submit for approval' }));

    expect(mutate).toHaveBeenCalledWith(
      { date: '2026-08-02', currency: 'UGX', submittedBy: 'finance-maker' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('row #99');
  });

  it('submits with the actor defaulting to system when left blank', () => {
    const mutate = vi.fn();
    useFinanceCloseSubmitMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleFinanceClose />);

    fireEvent.change(screen.getByLabelText('Close date'), { target: { value: '2026-08-02' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit for approval' }));

    expect(mutate).toHaveBeenCalledWith(
      { date: '2026-08-02', currency: 'UGX', submittedBy: 'system' },
      expect.anything(),
    );
  });

  it('approves the close for the selected date with a checker actor', () => {
    const mutate = vi.fn((_payload, options) => options.onSuccess?.({ status: 'CLOSED' }));
    useFinanceCloseApproveMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleFinanceClose />);

    fireEvent.change(screen.getByLabelText('Close date'), { target: { value: '2026-08-02' } });
    fireEvent.change(screen.getByLabelText('Approved by (checker)'), {
      target: { value: 'finance-checker' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Approve close' }));

    expect(mutate).toHaveBeenCalledWith(
      { date: '2026-08-02', currency: 'UGX', approvedBy: 'finance-checker' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('approved and closed');
  });

  it('rejects the close with a reason and a checker actor', () => {
    const mutate = vi.fn((_payload, options) => options.onSuccess?.({ status: 'PENDING_APPROVAL' }));
    useFinanceCloseRejectMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleFinanceClose />);

    fireEvent.change(screen.getByLabelText('Close date'), { target: { value: '2026-08-02' } });
    fireEvent.change(screen.getByLabelText('Rejected by (checker)'), {
      target: { value: 'finance-checker' },
    });
    fireEvent.change(screen.getByLabelText('Reject reason'), {
      target: { value: 'statement not yet reconciled' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reject close' }));

    expect(mutate).toHaveBeenCalledWith(
      {
        date: '2026-08-02',
        currency: 'UGX',
        rejectedBy: 'finance-checker',
        reason: 'statement not yet reconciled',
      },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('rejected');
  });

  it('lists pending close submissions from the summary', () => {
    render(<ModuleFinanceClose />);

    expect(screen.getByText('finance-maker')).toBeInTheDocument();
    expect(screen.getByText('PENDING_APPROVAL')).toBeInTheDocument();
  });

  it('shows an error banner when a mutation fails', async () => {
    const mutate = vi.fn((_payload, options) => options.onError?.(new ApiError('Same actor', 400)));
    useFinanceCloseApproveMutation.mockReturnValue(mutationResult({ mutate }));

    render(<ModuleFinanceClose />);

    fireEvent.change(screen.getByLabelText('Approved by (checker)'), {
      target: { value: 'finance-maker' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Approve close' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Same actor');
    });
  });
});
