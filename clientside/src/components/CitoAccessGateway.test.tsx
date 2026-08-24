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
  it('offers canonical partner and administrator entry points without granting roles', () => {
    renderAt('/login');
    expect(screen.getByRole('heading', { name: /sign in through cito/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /business, merchant or customer/i })).toHaveAttribute('href', '/partner');
    expect(screen.getByRole('link', { name: /cito team or administrator/i })).toHaveAttribute('href', '/admin');
    expect(screen.getByText(/selection only chooses the authentication realm/i)).toBeInTheDocument();
  });

  it('keeps the legacy merchant realm deep link working', () => {
    renderAt('/login?realm=merchant');
    expect(screen.getByText('Merchant authentication')).toBeInTheDocument();
  });

  it('keeps the legacy platform realm deep link working', () => {
    renderAt('/login?realm=platform');
    expect(screen.getByText('Platform authentication')).toBeInTheDocument();
  });
});
