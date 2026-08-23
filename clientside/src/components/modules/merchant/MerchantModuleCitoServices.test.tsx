import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MerchantModuleCitoServices from './MerchantModuleCitoServices';
import { apiFetch } from '../../../shared/api/httpClient';

vi.mock('../../../shared/api/httpClient', () => ({ apiFetch: vi.fn() }));
vi.mock('../../../shared/config', () => ({ apiUrl: (path: string) => path }));

function response(payload: unknown, status = 200): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    text: async () => JSON.stringify(payload),
  } as Response;
}

describe('MerchantModuleCitoServices', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockImplementation(async (url) => {
      const path = String(url);
      if (path.includes('/cito/overview')) {
        return response({
          features: [
            {
              serviceCode: 'INTELLIGENT_ROUTING',
              serviceName: 'Intelligent Payment Routing',
              description: 'Policy-driven provider routing',
              sandboxStatus: 'ACTIVE',
              productionStatus: 'REQUESTED',
            },
          ],
          routing: { decisions: 4 },
          refunds: { openDisputes: 1 },
          marketplace: { pendingRecoveryEvents: 0 },
          recurring: { activeSubscriptions: 2 },
          developer: { activeProjects: 1 },
          integrations: { activeInstallations: 3 },
        });
      }
      if (path.includes('/routing/decisions')) {
        return response([{ decisionReference: 'ROUTE-1', outcome: 'SUCCESS' }]);
      }
      return response([]);
    });
  });

  it('shows entitlements and all ten platform work areas', async () => {
    render(<MerchantModuleCitoServices />);

    expect(await screen.findByText('Intelligent Payment Routing')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Cito Services' })).toBeInTheDocument();

    const labels = [
      'Services',
      'Routing',
      'Marketplace',
      'Refunds & disputes',
      'Recurring',
      'Analytics',
      'Developer',
      'Virtual accounts',
      'Embedded Cito',
      'Integrations',
    ];
    labels.forEach((label) => {
      expect(screen.getByRole('tab', { name: label })).toBeInTheDocument();
    });
  });

  it('loads routing activity when the routing work area is selected', async () => {
    const user = userEvent.setup();
    render(<MerchantModuleCitoServices />);
    await screen.findByText('Intelligent Payment Routing');

    await user.click(screen.getByRole('tab', { name: 'Routing' }));

    expect(await screen.findByText('ROUTE-1')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringContaining('/routing/decisions'),
      expect.objectContaining({ credentials: 'include' }),
    );
  });
});
