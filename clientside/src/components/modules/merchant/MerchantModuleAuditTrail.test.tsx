import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import MerchantModuleAuditTrail from './MerchantModuleAuditTrail';
import { ApiError } from '../../../shared/api/httpClient';
import { AccessDeniedError, SessionExpiredError } from '../../../shared/api/hooks';
import type { AuditTrailRow } from '../../../shared/api/hooks';

const { useMerchantAuditTrail } = vi.hoisted(() => ({
  useMerchantAuditTrail: vi.fn(),
}));

vi.mock('../../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../../shared/api/hooks')>('../../../shared/api/hooks');
  return {
    ...actual,
    useMerchantAuditTrail,
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

const ROW: AuditTrailRow = {
  id: 1,
  created_on: '2026-07-29T10:00:00',
  user_id: 'merchant-staff-1',
  user_name: 'Sam Staff',
  action: 'PAYIN_ADDED',
};

beforeEach(() => {
  vi.clearAllMocks();
  useMerchantAuditTrail.mockReturnValue(queryResult({ data: [] }));
});

describe('MerchantModuleAuditTrail', () => {
  it('shows a loading state while the query is in flight', () => {
    useMerchantAuditTrail.mockReturnValue(queryResult({ data: undefined, isLoading: true, isFetching: true }));

    render(<MerchantModuleAuditTrail />);

    expect(screen.getByRole('status', { name: 'Loading audit trail' })).toBeInTheDocument();
  });

  it('shows an error state when the query fails', () => {
    useMerchantAuditTrail.mockReturnValue(queryResult({ data: undefined, error: new ApiError('Boom', 500) }));

    render(<MerchantModuleAuditTrail />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows an empty state when there are no audit records', () => {
    render(<MerchantModuleAuditTrail />);

    expect(screen.getByText(/No audit records to display/i)).toBeInTheDocument();
  });

  it('renders audit rows with user, action, and created-on details', () => {
    useMerchantAuditTrail.mockReturnValue(queryResult({ data: [ROW] }));

    render(<MerchantModuleAuditTrail />);

    expect(screen.getByText('Sam Staff')).toBeInTheDocument();
    expect(screen.getByText('merchant-staff-1')).toBeInTheDocument();
    expect(screen.getByText('PAYIN_ADDED')).toBeInTheDocument();
  });

  it('shows an access-denied banner instead of the table when the query fails with code 110', () => {
    useMerchantAuditTrail.mockReturnValue(
      queryResult({ data: undefined, error: new AccessDeniedError('You are not allowed access to this section.') }),
    );

    render(<MerchantModuleAuditTrail />);

    expect(screen.getByRole('alert')).toHaveTextContent(/not allowed access/);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('calls sessionExpired when the query fails with a session-expired error', () => {
    const sessionExpired = vi.fn();
    useMerchantAuditTrail.mockReturnValue(
      queryResult({ data: undefined, error: new SessionExpiredError('Your session expired.') }),
    );

    render(<MerchantModuleAuditTrail sessionExpired={sessionExpired} />);

    expect(sessionExpired).toHaveBeenCalled();
  });

  it('submits a search on Enter without refetching on every keystroke', () => {
    render(<MerchantModuleAuditTrail />);

    const input = screen.getByLabelText('Search audit trail');
    fireEvent.change(input, { target: { value: 'sam' } });
    expect(useMerchantAuditTrail).toHaveBeenLastCalledWith({ value: '', category: 'all' }, 50);

    fireEvent.keyDown(input, { key: 'Enter' });
    expect(useMerchantAuditTrail).toHaveBeenLastCalledWith({ value: 'sam', category: 'all' }, 50);
  });
});
