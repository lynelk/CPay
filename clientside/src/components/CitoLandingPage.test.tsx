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
  it('positions Cito as the gateway and CPay as the payments service', () => {
    renderLandingPage();

    expect(screen.getByRole('heading', { level: 1, name: /connect your business through cito/i })).toBeInTheDocument();
    expect(screen.getByText(/cito is the gateway/i)).toBeInTheDocument();
    expect(screen.getAllByText(/cpay is cito's payments service/i).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { level: 2, name: /^gateway$/i })).toBeInTheDocument();
  });

  it('routes every primary access action to the live Cito access screens', () => {
    renderLandingPage();

    const signInLinks = screen.getAllByRole('link', { name: /sign in/i });
    const signUpLinks = screen.getAllByRole('link', { name: /create cito account|get started|create account/i });

    signInLinks.forEach((link) => expect(link).toHaveAttribute('href', '/login'));
    signUpLinks.forEach((link) => expect(link).toHaveAttribute('href', '/signup'));
  });

  it('exposes CPay developer documentation and supported payment channels', () => {
    renderLandingPage();

    expect(screen.getByText(/CPay · Collections/i)).toBeInTheDocument();
    expect(screen.getByText(/CPay · Payouts/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /cpay api documentation/i })[0]).toHaveAttribute(
      'href',
      'https://lynelk.github.io/CPay/',
    );
    expect(screen.getByText(/MTN MoMo · Airtel Money · Airtel OpenAPI · Safaricom M-Pesa · Yo! Payments/i)).toBeInTheDocument();
  });

  it('includes direct Cito sales and support contact paths', () => {
    renderLandingPage();

    expect(screen.getByRole('link', { name: /discuss Cito services for your business/i })).toHaveAttribute(
      'href',
      'mailto:info@citotech.net',
    );
    expect(screen.getByRole('link', { name: /get help with an existing Cito account/i })).toHaveAttribute(
      'href',
      'mailto:support@citotech.net',
    );
  });
});
