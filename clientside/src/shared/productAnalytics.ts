import React from 'react';
import { apiFetch } from './api/httpClient';
import { environmentForCurrentPath } from './environment';

const allowedProperties = new Set(['cta', 'product', 'source']);

export function trackProductEvent(
  eventName: string,
  properties: Record<string, string> = {},
): void {
  const safeProperties = Object.fromEntries(Object.entries(properties).filter(([key]) => allowedProperties.has(key)));
  void apiFetch('/api/public/analytics/events', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    keepalive: true,
    body: JSON.stringify({
      eventName,
      audience: window.location.pathname.startsWith('/bo/') ? 'PORTAL' : 'PUBLIC',
      pagePath: window.location.pathname,
      environment: environmentForCurrentPath(),
      properties: safeProperties,
    }),
  }).catch(() => undefined);
}

export function usePageView(page: string): void {
  React.useEffect(() => { trackProductEvent('PAGE_VIEW', { product: page }); }, [page]);
}
