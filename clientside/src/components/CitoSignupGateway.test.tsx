import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CitoSignupGateway from './CitoSignupGateway';
import { apiFetch } from '../shared/api/httpClient';

vi.mock('./MerchantSignup', () => ({ default: () => <div>Merchant self-service signup</div> }));
vi.mock('../shared/api/httpClient', () => ({ apiFetch: vi.fn() }));
vi.mock('../shared/config', () => ({ apiUrl: (path: string) => path }));

function renderAt(entry: string) {
  return render(<MemoryRouter initialEntries={[entry]}><CitoSignupGateway /></MemoryRouter>);
}

describe('CitoSignupGateway', () => {
  it('separates merchant self-registration from privileged access requests', () => {
    renderAt('/signup');
    expect(screen.getByRole('link', { name: /business or merchant/i })).toHaveAttribute('href', '/signup?type=merchant');
    expect(screen.getByRole('link', { name: /admin, staff, specialist, or partner/i })).toHaveAttribute('href', '/signup?type=access');
    expect(screen.getByText(/privileged roles cannot be self-assigned/i)).toBeInTheDocument();
  });

  it('keeps merchant onboarding on the established self-service flow', () => {
    renderAt('/signup?type=merchant');
    expect(screen.getByText('Merchant self-service signup')).toBeInTheDocument();
  });

  it('submits privileged access as pending review without collecting a password', async () => {
    vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ accepted: true, status: 'PENDING', message: 'Request received. Access is not provisioned until an authorized administrator reviews and approves it.' }), { status: 202, headers: { 'Content-Type': 'application/json' } }));
    renderAt('/signup?type=access');

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Amina Example' } });
    fireEvent.change(screen.getByLabelText(/work email/i), { target: { value: 'amina@example.com' } });
    fireEvent.change(screen.getByLabelText(/organization/i), { target: { value: 'Example Ltd' } });
    fireEvent.change(screen.getByLabelText(/business reason/i), { target: { value: 'Operations access for approved support duties.' } });
    fireEvent.click(screen.getByRole('button', { name: /submit access request/i }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/not provisioned until an authorized administrator reviews/i));
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/public/access-requests', expect.objectContaining({ method: 'POST' }));
  });
});
