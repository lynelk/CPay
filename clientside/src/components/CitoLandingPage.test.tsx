import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CitoLandingPage from './CitoLandingPage';

function renderLandingPage() {
  return render(
    <MemoryRouter>
      <CitoLandingPage />
    </MemoryRouter>,
  );
}

describe('CitoLandingPage', () => {
  it('presents the primary product proposition and access routes', () => {
    renderLandingPage();

    expect(screen.getByRole('heading', { level: 1, name: /one platform to collect, pay and connect/i })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /get started/i })[0]).toHaveAttribute('href', '/signup');
    expect(screen.getAllByRole('link', { name: /sign in/i })[0]).toHaveAttribute('href', '/login');
  });

  it('exposes developer documentation and core CPay capabilities', () => {
    renderLandingPage();

    expect(screen.getByText('Collections')).toBeInTheDocument();
    expect(screen.getByText('Payouts')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /api documentation/i })[0]).toHaveAttribute(
      'href',
      'https://lynelk.github.io/CPay/',
    );
    expect(screen.getByText(/MTN MoMo · Airtel Money · Airtel OpenAPI · Safaricom M-Pesa · Yo! Payments/i)).toBeInTheDocument();
  });

  it('includes direct sales and support contact paths', () => {
    renderLandingPage();

    expect(screen.getByRole('link', { name: /discuss CPay for your business/i })).toHaveAttribute(
      'href',
      'mailto:info@citotech.net',
    );
    expect(screen.getByRole('link', { name: /get help with an existing account/i })).toHaveAttribute(
      'href',
      'mailto:support@citotech.net',
    );
  });
});
