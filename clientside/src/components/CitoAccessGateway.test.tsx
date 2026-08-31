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
  it('offers canonical /bo partner and administrator entry points without granting roles', () => {
    renderAt('/bo');
    expect(screen.getByRole('heading', { name: /sign in through cito/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /business, merchant or customer/i })).toHaveAttribute('href', '/bo/partner');
    expect(screen.getByRole('link', { name: /cito team or administrator/i })).toHaveAttribute('href', '/bo/admin');
    expect(screen.getByText(/selection only chooses the authentication realm/i)).toBeInTheDocument();
  });

  it('supports the merchant realm deep link under /bo', () => {
    renderAt('/bo?realm=merchant');
    expect(screen.getByText('Merchant authentication')).toBeInTheDocument();
  });

  it('supports the platform realm deep link under /bo', () => {
    renderAt('/bo?realm=platform');
    expect(screen.getByText('Platform authentication')).toBeInTheDocument();
  });
});
