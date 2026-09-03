import React from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { apiFetch } from '../shared/api/httpClient';
import { Button, Card } from '../ui';

export type AdminSessionState = 'checking' | 'authenticated' | 'unauthenticated' | 'unavailable';

interface SessionEnvelope {
  code?: string;
  message?: string | boolean;
  error?: string;
}

export function classifyAdminSessionResponse(
  status: number,
  ok: boolean,
  payload: SessionEnvelope | null,
): Exclude<AdminSessionState, 'checking'> {
  if (status === 401 || status === 403) return 'unauthenticated';
  if (!ok) return 'unavailable';

  if (payload?.code === '107') return 'unauthenticated';
  if (payload?.code !== '000') return 'unavailable';

  if (payload.message === true || payload.message === 'true') return 'authenticated';
  if (payload.message === false || payload.message === 'false') return 'unauthenticated';

  return 'unavailable';
}

export async function verifyAdminSession(): Promise<Exclude<AdminSessionState, 'checking'>> {
  try {
    const response = await apiFetch('/auth/isLoggedIn', {
      method: 'POST',
      cache: 'no-cache',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    });

    let payload: SessionEnvelope | null = null;
    try {
      const text = await response.text();
      payload = text ? JSON.parse(text) as SessionEnvelope : null;
    } catch {
      return response.status === 401 || response.status === 403 ? 'unauthenticated' : 'unavailable';
    }

    return classifyAdminSessionResponse(response.status, response.ok, payload);
  } catch {
    return 'unavailable';
  }
}

function clearStaleAdminPrincipal(): void {
  try {
    localStorage.removeItem('user');
  } catch {
    // Storage can be unavailable in hardened/private browser modes. The server session remains authoritative.
  }
}

export default function AdminSessionGate({ children }: { children: React.ReactNode }): React.ReactElement {
  const navigate = useNavigate();
  const [state, setState] = React.useState<AdminSessionState>('checking');
  const [attempt, setAttempt] = React.useState(0);

  React.useEffect(() => {
    let active = true;
    setState('checking');

    verifyAdminSession().then((nextState) => {
      if (!active) return;
      if (nextState === 'unauthenticated') clearStaleAdminPrincipal();
      setState(nextState);
    });

    return () => {
      active = false;
    };
  }, [attempt]);

  if (state === 'unauthenticated') {
    return <Navigate to="/bo/admin" replace />;
  }

  if (state === 'authenticated') {
    return <>{children}</>;
  }

  if (state === 'unavailable') {
    return (
      <main className="cito-access-shell" data-testid="admin-session-unavailable">
        <Card className="cito-state cito-state--error" role="alert">
          <h1>Unable to verify your session</h1>
          <p>
            Cito could not confirm the current administrator session. This is different from being signed out;
            you can retry the check or continue to the secure sign-in page.
          </p>
          <div className="ios-actions">
            <Button variant="primary" onClick={() => setAttempt((value) => value + 1)}>Retry session</Button>
            <Button variant="ghost" onClick={() => navigate('/bo/admin', { replace: true })}>Continue to sign in</Button>
          </div>
        </Card>
      </main>
    );
  }

  return (
    <main className="cito-access-shell" aria-busy="true" data-testid="admin-session-checking">
      <Card className="cito-state">
        <h1>Checking your session</h1>
        <p>Confirming secure administrator access…</p>
      </Card>
    </main>
  );
}
