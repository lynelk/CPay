/**
 * Minimal typed fetch wrapper. Cookie-session aware (credentials: 'include').
 *
 * This is the single place application code should perform HTTP from.
 */
import { apiUrl } from '../config';
import { environmentForCurrentPath } from '../environment';

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
  const response = await apiFetch(path, {
    ...options,
    headers: jsonHeaders(options),
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

export async function apiFetch(input: RequestInfo | URL, options: RequestInit = {}): Promise<Response> {
  return fetch(normalizeInput(input), {
    credentials: 'include',
    ...options,
    headers: {
      'X-Cito-Environment': environmentForCurrentPath(),
      ...options.headers,
    },
  });
}

function normalizeInput(input: RequestInfo | URL): RequestInfo | URL {
  if (typeof input === 'string') {
    return apiUrl(input);
  }
  return input;
}

function jsonHeaders(options: RequestInit): HeadersInit {
  const hasFormBody = typeof FormData !== 'undefined' && options.body instanceof FormData;
  return {
    Accept: 'application/json',
    ...(options.body && !hasFormBody ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  };
}
