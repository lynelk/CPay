import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CitoAccessGateway from './CitoAccessGateway';

vi.mock('./Login', () => ({ default: () => <div>Platform authentication</div> }));
vi.mock('./LoginMerchant', () => ({ default: () => <div>Merchant authentication</div> }));

function renderAt(entry: string) {
  return render(<MemoryRouter initialEntries={[entry]}><CitoAccessGateway /></MemoryRouter>);
}

describe('CitoAccessGateway', () => {
  it('offers account realms without granting roles', () => {
    renderAt('/login');
    expect(screen.getByRole('heading', { name: /sign in through cito/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /business or merchant/i })).toHaveAttribute('href', '/login?realm=merchant');
    expect(screen.getByRole('link', { name: /cito team or administrator/i })).toHaveAttribute('href', '/login?realm=platform');
    expect(screen.getByText(/selection only chooses the authentication realm/i)).toBeInTheDocument();
  });

  it('delegates merchant credentials to the existing merchant authenticator', () => {
    renderAt('/login?realm=merchant');
    expect(screen.getByText('Merchant authentication')).toBeInTheDocument();
  });

  it('delegates platform credentials to the existing platform authenticator', () => {
    renderAt('/login?realm=platform');
    expect(screen.getByText('Platform authentication')).toBeInTheDocument();
  });
});
