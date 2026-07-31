import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import ModuleAuditTrail from './ModuleAuditTrail';
import { ApiError } from '../../shared/api/httpClient';
import { AccessDeniedError, SessionExpiredError } from '../../shared/api/hooks';
import type { AuditTrailRow } from '../../shared/api/hooks';

const { useAdminAuditTrail } = vi.hoisted(() => ({
  useAdminAuditTrail: vi.fn(),
}));

vi.mock('../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/hooks')>('../../shared/api/hooks');
  return {
    ...actual,
    useAdminAuditTrail,
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
  user_id: 'admin-1',
  user_name: 'Jane Admin',
  action: 'LOGIN',
};

beforeEach(() => {
  vi.clearAllMocks();
  useAdminAuditTrail.mockReturnValue(queryResult({ data: [] }));
});

describe('ModuleAuditTrail', () => {
  it('shows a loading state while the query is in flight', () => {
    useAdminAuditTrail.mockReturnValue(queryResult({ data: undefined, isLoading: true, isFetching: true }));

    render(<ModuleAuditTrail />);

    expect(screen.getByRole('status', { name: 'Loading audit trail' })).toBeInTheDocument();
  });

  it('shows an error state when the query fails', () => {
    useAdminAuditTrail.mockReturnValue(queryResult({ data: undefined, error: new ApiError('Boom', 500) }));

    render(<ModuleAuditTrail />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows an empty state when there are no audit records', () => {
    render(<ModuleAuditTrail />);

    expect(screen.getByText(/No audit records to display/i)).toBeInTheDocument();
  });

  it('renders audit rows with user, action, and created-on details', () => {
    useAdminAuditTrail.mockReturnValue(queryResult({ data: [ROW] }));

    render(<ModuleAuditTrail />);

    expect(screen.getByText('Jane Admin')).toBeInTheDocument();
    expect(screen.getByText('admin-1')).toBeInTheDocument();
    expect(screen.getByText('LOGIN')).toBeInTheDocument();
  });

  it('shows an access-denied banner instead of the table when the query fails with code 110', () => {
    useAdminAuditTrail.mockReturnValue(
      queryResult({ data: undefined, error: new AccessDeniedError('You are not allowed access to this section.') }),
    );

    render(<ModuleAuditTrail />);

    expect(screen.getByRole('alert')).toHaveTextContent(/not allowed access/);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('calls sessionExpired when the query fails with a session-expired error', () => {
    const sessionExpired = vi.fn();
    useAdminAuditTrail.mockReturnValue(
      queryResult({ data: undefined, error: new SessionExpiredError('Your session expired.') }),
    );

    render(<ModuleAuditTrail sessionExpired={sessionExpired} />);

    expect(sessionExpired).toHaveBeenCalled();
  });

  it('submits a search on Enter without refetching on every keystroke', () => {
    render(<ModuleAuditTrail />);

    const input = screen.getByLabelText('Search audit trail');
    fireEvent.change(input, { target: { value: 'jane' } });
    expect(useAdminAuditTrail).toHaveBeenLastCalledWith({ value: '', category: 'all' }, 50);

    fireEvent.keyDown(input, { key: 'Enter' });
    expect(useAdminAuditTrail).toHaveBeenLastCalledWith({ value: 'jane', category: 'all' }, 50);
  });

  it('lets a row be selected via its checkbox', () => {
    useAdminAuditTrail.mockReturnValue(queryResult({ data: [ROW] }));

    render(<ModuleAuditTrail />);

    const rowCheckbox = screen.getByRole('checkbox', { name: /Select row for Jane Admin/ });
    expect(rowCheckbox).not.toBeChecked();
    fireEvent.click(rowCheckbox);
    expect(rowCheckbox).toBeChecked();
  });
});
