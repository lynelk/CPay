import { describe, expect, test } from 'vitest';
import { classifyAdminSessionResponse } from './AdminSessionGate';

describe('admin session response classification', () => {
  test('treats an active session as authenticated', () => {
    expect(classifyAdminSessionResponse(200, true, { code: '000', message: 'true' })).toBe('authenticated');
    expect(classifyAdminSessionResponse(200, true, { code: '000', message: true })).toBe('authenticated');
  });

  test('treats normal signed-out responses as unauthenticated rather than unavailable', () => {
    expect(classifyAdminSessionResponse(200, true, { code: '000', message: 'false' })).toBe('unauthenticated');
    expect(classifyAdminSessionResponse(200, true, { code: '000', message: false })).toBe('unauthenticated');
    expect(classifyAdminSessionResponse(200, true, { code: '107', message: 'Session expired' })).toBe('unauthenticated');
    expect(classifyAdminSessionResponse(401, false, null)).toBe('unauthenticated');
    expect(classifyAdminSessionResponse(403, false, null)).toBe('unauthenticated');
  });

  test('reserves unavailable for genuine verification failures', () => {
    expect(classifyAdminSessionResponse(500, false, { error: 'backend unavailable' })).toBe('unavailable');
    expect(classifyAdminSessionResponse(200, true, { code: '999', message: 'unexpected' })).toBe('unavailable');
    expect(classifyAdminSessionResponse(200, true, null)).toBe('unavailable');
  });
});
