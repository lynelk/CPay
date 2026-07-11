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

let csrfHeaderName = 'X-XSRF-TOKEN';
let csrfToken: string | null = null;
let csrfLoad: Promise<string | null> | null = null;

function isSameOrigin(input: RequestInfo | URL): boolean {
  if (typeof window === 'undefined') return false;
  const url = input instanceof Request ? input.url : input.toString();
  const parsed = new URL(url, window.location.origin);
  return parsed.origin === window.location.origin;
}

async function loadCsrfToken(nativeFetch: typeof fetch): Promise<string | null> {
  if (csrfToken) return csrfToken;
  if (!csrfLoad) {
    csrfLoad = nativeFetch('/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
      .then(async (response) => {
        if (!response.ok) return null;
        const payload = (await response.json()) as CsrfPayload;
        csrfHeaderName = payload.headerName || csrfHeaderName;
        csrfToken = payload.token || null;
        return csrfToken;
      })
      .catch(() => null)
      .finally(() => {
        csrfLoad = null;
      });
  }
  return csrfLoad;
}

function methodFor(input: RequestInfo | URL, init?: RequestInit): string {
  return (init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
}

function withCsrf(init: RequestInit | undefined, input: RequestInfo | URL, token: string): RequestInit {
  const headers = new Headers(init?.headers || (input instanceof Request ? input.headers : undefined));
  headers.set(csrfHeaderName, token);
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
    if (!mutatingMethods.has(method) || !isSameOrigin(input)) {
      return nativeFetch(input, init);
    }

    const token = await loadCsrfToken(nativeFetch);
    const guardedInit = token ? withCsrf(init, input, token) : init;
    const response = await nativeFetch(input, guardedInit);
    if (response.status !== 403) return response;

    csrfToken = null;
    const refreshedToken = await loadCsrfToken(nativeFetch);
    return nativeFetch(input, refreshedToken ? withCsrf(init, input, refreshedToken) : init);
  };

  window.__cpayCsrfFetchInstalled = true;
}
