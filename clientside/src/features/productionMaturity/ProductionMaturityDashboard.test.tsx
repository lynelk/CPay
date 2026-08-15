import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ProductionMaturityDashboard from './ProductionMaturityDashboard';

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProductionMaturityDashboard />
    </QueryClientProvider>,
  );
}

describe('ProductionMaturityDashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('renders loading state and calls the real production maturity endpoints', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: true, text: () => Promise.resolve('{"widgets":[]}') })),
    );

    renderDashboard();

    expect(screen.getByLabelText(/production maturity loading state/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v2/product-experience/dashboard/widgets?audience=ADMIN',
        expect.objectContaining({ credentials: 'include' }),
      );
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v2/production-maturity/validation/runs?limit=10',
        expect.objectContaining({ credentials: 'include' }),
      );
    });
  });

  it('renders all production-maturity workflow sections after data loads', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: true, text: () => Promise.resolve('{"rows":[]}') })),
    );

    renderDashboard();

    expect(await screen.findByRole('heading', { name: /production maturity/i })).toBeInTheDocument();
    expect(await screen.findByText(/merchant onboarding/i)).toBeInTheDocument();
    expect(screen.getByText(/developer portal/i)).toBeInTheDocument();
    expect(screen.getByText(/finance operations/i)).toBeInTheDocument();
    expect(screen.getByText(/compliance operations/i)).toBeInTheDocument();
    expect(screen.getByText(/cross-border readiness/i)).toBeInTheDocument();
    expect(screen.getByText(/automation validation/i)).toBeInTheDocument();
  });

  it('renders a failure state when a production maturity endpoint fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: false, statusText: 'Forbidden', text: () => Promise.resolve('{"message":"Forbidden"}') })),
    );

    renderDashboard();

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be loaded/i);
    expect(screen.getByRole('alert')).toHaveTextContent(/forbidden/i);
  });
});
