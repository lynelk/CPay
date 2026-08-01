import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import MerchantModuleWebhooks from './MerchantModuleWebhooks';
import { ApiError } from '../../../shared/api/httpClient';

const {
  useMerchantWebhookEndpoints,
  useMerchantWebhookDeliveries,
  useRegisterWebhookMutation,
  useRotateWebhookSecretMutation,
  useReplayWebhookDeliveryMutation,
} = vi.hoisted(() => ({
  useMerchantWebhookEndpoints: vi.fn(),
  useMerchantWebhookDeliveries: vi.fn(),
  useRegisterWebhookMutation: vi.fn(),
  useRotateWebhookSecretMutation: vi.fn(),
  useReplayWebhookDeliveryMutation: vi.fn(),
}));

vi.mock('../../../shared/api/hooks', async () => {
  const actual = await vi.importActual<typeof import('../../../shared/api/hooks')>('../../../shared/api/hooks');
  return {
    ...actual,
    useMerchantWebhookEndpoints,
    useMerchantWebhookDeliveries,
    useRegisterWebhookMutation,
    useRotateWebhookSecretMutation,
    useReplayWebhookDeliveryMutation,
  };
});

beforeEach(() => {
  vi.clearAllMocks();
  useMerchantWebhookEndpoints.mockReturnValue({ data: [], isLoading: false, isFetching: false, error: null, refetch: vi.fn() });
  useMerchantWebhookDeliveries.mockReturnValue({ data: [], isLoading: false, isFetching: false, error: null, refetch: vi.fn() });
  useRegisterWebhookMutation.mockReturnValue({ mutateAsync: vi.fn().mockResolvedValue({ secret: 'S1' }), isPending: false });
  useRotateWebhookSecretMutation.mockReturnValue({ mutateAsync: vi.fn().mockResolvedValue({ secret: 'S2' }), isPending: false });
  useReplayWebhookDeliveryMutation.mockReturnValue({ mutateAsync: vi.fn().mockResolvedValue({ updated: 1 }), isPending: false });
});

describe('MerchantModuleWebhooks', () => {
  it('shows the endpoints panel by default with endpoints from the query', () => {
    useMerchantWebhookEndpoints.mockReturnValue({
      data: [{ id: 7, event_type: 'payment.completed', endpoint_url: 'https://merchant.test/hook', endpoint_status: 'ACTIVE' }],
      isLoading: false, isFetching: false, error: null, refetch: vi.fn(),
    });

    render(<MerchantModuleWebhooks />);

    expect(screen.getByRole('tab', { name: 'Endpoints' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Deliveries' })).toBeInTheDocument();
    expect(screen.getByText('payment.completed')).toBeInTheDocument();
    expect(screen.getByText('https://merchant.test/hook')).toBeInTheDocument();
  });

  it('shows an error alert when the endpoints query fails', () => {
    useMerchantWebhookEndpoints.mockReturnValue({
      data: undefined, isLoading: false, isFetching: false, error: new ApiError('Boom', 500), refetch: vi.fn(),
    });

    render(<MerchantModuleWebhooks />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('shows an empty state when there are no endpoints', () => {
    render(<MerchantModuleWebhooks />);

    expect(screen.getByText(/No webhook endpoints registered/i)).toBeInTheDocument();
  });

  it('registers an endpoint and reveals the signing secret once', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({ secret: 'S1' });
    useRegisterWebhookMutation.mockReturnValue({ mutateAsync, isPending: false });

    render(<MerchantModuleWebhooks />);
    fireEvent.click(screen.getByRole('button', { name: 'Register endpoint' }));

    const urlInput = screen.getByLabelText('Endpoint URL');
    fireEvent.change(urlInput, { target: { value: 'https://merchant.test/wh' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(mutateAsync).toHaveBeenCalledWith({ eventType: 'payment.pending', endpointUrl: 'https://merchant.test/wh' });
    expect(await screen.findByText(/S1 — copy it now/)).toBeInTheDocument();
  });

  it('rejects an invalid endpoint URL without submitting', () => {
    const mutateAsync = vi.fn();
    useRegisterWebhookMutation.mockReturnValue({ mutateAsync, isPending: false });

    render(<MerchantModuleWebhooks />);
    fireEvent.click(screen.getByRole('button', { name: 'Register endpoint' }));
    fireEvent.change(screen.getByLabelText('Endpoint URL'), { target: { value: 'not-a-url' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(mutateAsync).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/valid https/i);
  });

  it('switches to the deliveries tab and lists deliveries with replay', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    useMerchantWebhookDeliveries.mockReturnValue({
      data: [{ id: 9, event_type: 'payment.failed', event_reference: 'TX-1', delivery_status: 'FAILED', attempt_count: 5 }],
      isLoading: false, isFetching: false, error: null, refetch: vi.fn(),
    });
    const replay = vi.fn().mockResolvedValue({ updated: 1 });
    useReplayWebhookDeliveryMutation.mockReturnValue({ mutateAsync: replay, isPending: false });

    render(<MerchantModuleWebhooks />);
    fireEvent.click(screen.getByRole('tab', { name: 'Deliveries' }));

    expect(screen.getByText('TX-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Replay delivery 9' }));
    expect(replay).toHaveBeenCalledWith(9);
    confirmSpy.mockRestore();
  });

  it('calls sessionExpired when the endpoints query returns 401', () => {
    const sessionExpired = vi.fn();
    useMerchantWebhookEndpoints.mockReturnValue({
      data: undefined, isLoading: false, isFetching: false, error: new ApiError('Unauthorized', 401), refetch: vi.fn(),
    });

    render(<MerchantModuleWebhooks sessionExpired={sessionExpired} />);

    expect(sessionExpired).toHaveBeenCalled();
  });
});
