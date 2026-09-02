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
  it('positions Cito as the gateway and Cito Payments as the payments product', () => {
    renderLandingPage();

    expect(screen.getByRole('heading', { level: 1, name: /connect your business through cito/i })).toBeInTheDocument();
    expect(screen.getByText(/cito is the gateway/i)).toBeInTheDocument();
    expect(screen.getAllByText(/cito payments is the payment product/i).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { level: 2, name: /^gateway$/i })).toBeInTheDocument();
  });

  it('routes every primary access action to the live Cito access screens', () => {
    renderLandingPage();

    const signInLinks = screen.getAllByRole('link', { name: /sign in/i });
    const signUpLinks = screen.getAllByRole('link', { name: /create cito account|get started|create account/i });

    signInLinks.forEach((link) => expect(link).toHaveAttribute('href', '/login'));
    signUpLinks.forEach((link) => expect(link).toHaveAttribute('href', '/signup'));
  });

  it('exposes Cito Payments developer documentation and supported payment channels', () => {
    renderLandingPage();

    expect(screen.getByText(/Cito Payments · Collections/i)).toBeInTheDocument();
    expect(screen.getByText(/Cito Payments · Payouts/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /cito payments api documentation/i })[0]).toHaveAttribute(
      'href',
      'https://lynelk.github.io/CPay/',
    );
    expect(screen.getByText(/MTN MoMo · Airtel Money · Airtel OpenAPI · Safaricom M-Pesa · Yo! Payments/i)).toBeInTheDocument();
  });

  it('includes direct Cito sales and support contact paths', () => {
    renderLandingPage();

    expect(screen.getByRole('link', { name: /discuss Cito services for your business/i })).toHaveAttribute('href', '/contact');
    expect(screen.getByRole('link', { name: /get help with an existing Cito account/i })).toHaveAttribute(
      'href',
      'mailto:support@citotech.net',
    );
  });

  it('shows Core-Synergies as the copyright owner', () => {
    renderLandingPage();
    expect(screen.getByText(/© .* Core-Synergies/i)).toBeInTheDocument();
  });
});
