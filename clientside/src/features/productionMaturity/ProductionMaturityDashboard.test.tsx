import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import ProductionMaturityDashboard from './ProductionMaturityDashboard';

describe('ProductionMaturityDashboard', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        }),
      )
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('renders all production-maturity workflow sections and checks endpoint reachability', async () => {
    render(<ProductionMaturityDashboard />);

    expect(screen.getByRole('heading', { name: /production maturity dashboard/i })).toBeInTheDocument();
    expect(screen.getByText(/merchant onboarding/i)).toBeInTheDocument();
    expect(screen.getByText(/developer portal/i)).toBeInTheDocument();
    expect(screen.getByText(/finance operations/i)).toBeInTheDocument();
    expect(screen.getByText(/compliance operations/i)).toBeInTheDocument();
    expect(screen.getByText(/cross-border readiness/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v2/product/onboarding/progress',
        expect.objectContaining({ credentials: 'include' })
      );
    });
  });
});
