import { describe, expect, it } from 'vitest';

import { apiUrl } from './config';

describe('apiUrl', () => {
  it('prefixes unqualified browser controller paths once', () => {
    expect(apiUrl('/auth/csrf')).toBe('/api/ui/auth/csrf');
  });

  it('does not duplicate the API base when a caller already normalized the path', () => {
    expect(apiUrl('/api/ui/auth/authenticate')).toBe('/api/ui/auth/authenticate');
  });

  it('preserves absolute URLs', () => {
    expect(apiUrl('https://example.com/status')).toBe('https://example.com/status');
  });
});
