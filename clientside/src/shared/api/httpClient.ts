/**
 * Minimal typed fetch wrapper. Cookie-session aware (credentials: 'include').
 *
 * This is the single place new code should perform HTTP from. Legacy modules
 * still hand-roll `fetch`; migrate them onto this client incrementally.
 */
import { apiUrl } from '../config';

export class ApiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function request<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(apiUrl(path), {
    credentials: 'include',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message =
      (data && typeof data === 'object' && 'message' in data
        ? String((data as { message?: unknown }).message)
        : '') || response.statusText;
    throw new ApiError(message, response.status);
  }
  return data as T;
}
