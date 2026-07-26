import { API_BASE } from './config';

type CsrfPayload = {
  headerName?: string;
  token?: string;
};

declare global {
  interface Window {
    __cpayCsrfFetchInstalled?: boolean;
  }
}

const mutatingMethods = new Set(['DELETE', 'PATCH', 'POST', 'PUT']);

type CsrfState = {
  headerName: string;
  token: string | null;
  load: Promise<string | null> | null;
};

const csrfStates = new Map<string, CsrfState>();

function stateFor(endpoint: string): CsrfState {
  const existing = csrfStates.get(endpoint);
  if (existing) return existing;
  const created = { headerName: 'X-XSRF-TOKEN', token: null, load: null };
  csrfStates.set(endpoint, created);
  return created;
}

function parsedUrl(input: RequestInfo | URL): URL | null {
  if (typeof window === 'undefined') return null;
  const url = input instanceof Request ? input.url : input.toString();
  return new URL(url, window.location.origin);
}

function configuredApiOrigin(): string | null {
  if (typeof window === 'undefined' || !API_BASE) return null;
  return new URL(API_BASE, window.location.origin).origin;
}

function csrfEndpointFor(input: RequestInfo | URL): string | null {
  const parsed = parsedUrl(input);
  if (!parsed || typeof window === 'undefined') return null;

  if (parsed.origin === window.location.origin) {
    return '/auth/csrf';
  }

  const apiOrigin = configuredApiOrigin();
  if (apiOrigin && parsed.origin === apiOrigin) {
    return `${apiOrigin}/auth/csrf`;
  }

  return null;
}

async function loadCsrfToken(nativeFetch: typeof fetch, endpoint: string): Promise<CsrfState> {
  const state = stateFor(endpoint);
  if (state.token) return state;
  if (!state.load) {
    state.load = nativeFetch(endpoint, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
      .then(async (response) => {
        if (!response.ok) return null;
        const payload = (await response.json()) as CsrfPayload;
        state.headerName = payload.headerName || state.headerName;
        state.token = payload.token || null;
        return state.token;
      })
      .catch(() => null)
      .finally(() => {
        state.load = null;
      });
  }
  await state.load;
  return state;
}

function methodFor(input: RequestInfo | URL, init?: RequestInit): string {
  return (init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
}

function withCsrf(init: RequestInit | undefined, input: RequestInfo | URL, state: CsrfState): RequestInit {
  const headers = new Headers(init?.headers || (input instanceof Request ? input.headers : undefined));
  if (state.token) headers.set(state.headerName, state.token);
  return {
    ...init,
    credentials: init?.credentials || 'include',
    headers,
  };
}

export function installCsrfFetch(): void {
  if (typeof window === 'undefined' || window.__cpayCsrfFetchInstalled) return;
  const nativeFetch = window.fetch.bind(window);

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const method = methodFor(input, init);
    const csrfEndpoint = csrfEndpointFor(input);
    if (!mutatingMethods.has(method) || !csrfEndpoint) {
      return nativeFetch(input, init);
    }

    const state = await loadCsrfToken(nativeFetch, csrfEndpoint);
    const guardedInit = state.token ? withCsrf(init, input, state) : init;
    const response = await nativeFetch(input, guardedInit);
    if (response.status !== 403) return response;

    state.token = null;
    const refreshedState = await loadCsrfToken(nativeFetch, csrfEndpoint);
    return nativeFetch(input, refreshedState.token ? withCsrf(init, input, refreshedState) : init);
  };

  window.__cpayCsrfFetchInstalled = true;
}
