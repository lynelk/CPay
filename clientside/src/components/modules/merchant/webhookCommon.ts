import type { BadgeTone } from '../../../ui';

/**
 * Audit N6: shared presentation helpers for the merchant webhook manager
 * (`MerchantModuleWebhooks`). Kept separate so the endpoints and delivery
 * panels stay small and the status-to-tone mapping lives in one place.
 */

/** The catalog-valid event types (mirrors `WebhookEventCatalog.all()` server-side). */
export const WEBHOOK_EVENT_TYPES = [
  { value: 'payment.pending', label: 'payment.pending' },
  { value: 'payment.completed', label: 'payment.completed' },
  { value: 'payment.failed', label: 'payment.failed' },
  { value: 'payout.pending', label: 'payout.pending' },
  { value: 'payout.completed', label: 'payout.completed' },
  { value: 'payout.failed', label: 'payout.failed' },
  { value: 'refund.completed', label: 'refund.completed' },
  { value: 'refund.failed', label: 'refund.failed' },
];

export function deliveryStatusTone(status?: string): BadgeTone {
  if (status === 'DELIVERED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'PENDING') return 'warning';
  return 'neutral';
}

export function endpointStatusTone(status?: string): BadgeTone {
  return status === 'ACTIVE' ? 'success' : 'neutral';
}

export function formatDateTime(value?: string): string {
  if (!value) return '—';
  return value.replace('T', ' ').replace(/\.\d+$/, '');
}

export function httpStatusLabel(status: number | null | undefined): string {
  if (status == null) return '—';
  return String(status);
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

export function isHttp401(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    (error as { status?: unknown }).status === 401
  );
}

export function isValidEndpointUrl(value: string): boolean {
  const trimmed = value.trim();
  return /^https?:\/\/.+\..+/.test(trimmed);
}
