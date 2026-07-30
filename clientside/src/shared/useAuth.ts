/**
 * Centralized auth/session state reader.
 *
 * Both the admin portal (`localStorage["user"]`) and merchant portal
 * (`localStorage["merchantUser"]`) persist the logged-in principal to
 * `localStorage` once `/auth/authenticate` / `/auth/authenticateMerchantUser`
 * succeeds (see `Login.tsx` / `LoginMerchant.tsx`). Historically, every
 * module that needed the current user or a privilege check re-implemented
 * the same inline read:
 *
 *   const user = localStorage.getItem("user") != null ? JSON.parse(localStorage.getItem("user")) : {};
 *   if (user.privileges) { for (...) if (user.privileges[i].privilege === "X") return true; }
 *
 * across a dozen-plus files (see `ModuleAdmins.jsx`, `ModuleTransactions.jsx`,
 * `ModuleMerchants.jsx`, and the merchant-portal equivalents). This hook
 * centralizes that read and exposes a typed `hasPrivilege()` helper instead.
 *
 * This does NOT perform authentication itself (no network call) — it mirrors
 * whatever `Login`/`LoginMerchant` last wrote to `localStorage`, the same
 * source of truth the rest of the app already relies on. It re-reads on
 * mount and on `storage` events (cross-tab login/logout), and exposes
 * `refresh()`/`clear()` for same-tab updates.
 *
 * Usage:
 *
 *   const { user, hasPrivilege, isAuthenticated } = useAuth('admin');
 *   if (!hasPrivilege('ACCESS_TRANSACTION_LOG')) { ... }
 *
 *   const { user } = useAuth('merchant');
 *   <UserChip name={user.name ?? 'Merchant User'} meta={user.email} />
 *
 * Not every existing `localStorage.getItem('user'|'merchantUser')` call site
 * has been migrated onto this hook yet — that's a larger follow-up (see
 * MIGRATION.md). Adopt it in new/touched modules going forward.
 */
import { useCallback, useEffect, useState } from 'react';

export type Portal = 'admin' | 'merchant';

export interface Privilege {
  privilege: string;
  [key: string]: unknown;
}

export interface AuthUser {
  name?: string;
  username?: string;
  email?: string;
  account_number?: string;
  privileges?: Privilege[];
  [key: string]: unknown;
}

const STORAGE_KEYS: Record<Portal, string> = {
  admin: 'user',
  merchant: 'merchantUser',
};

function readStoredUser(portal: Portal): AuthUser {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS[portal]);
    return raw != null ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export interface UseAuthResult {
  /** The portal this hook instance is reading (`'admin'` -> `user`, `'merchant'` -> `merchantUser`). */
  portal: Portal;
  /** The stored principal, or `{}` if nothing is stored / it failed to parse. */
  user: AuthUser;
  /** Flattened `privilege` values off `user.privileges`, e.g. `['ACCESS_TRANSACTION_LOG', ...]`. */
  privileges: string[];
  /** `true` when `user.privileges` includes the given privilege string. */
  hasPrivilege: (privilege: string) => boolean;
  /** `true` when something is stored for this portal (does not verify the session server-side). */
  isAuthenticated: boolean;
  /** Re-read from localStorage (e.g. right after a same-tab login/logout write). */
  refresh: () => void;
  /** Remove the stored principal for this portal and reset local state to `{}`. */
  clear: () => void;
}

/**
 * Read the current admin or merchant user out of `localStorage` and expose a
 * typed privilege-check helper. See file header for the full rationale.
 */
export function useAuth(portal: Portal = 'admin'): UseAuthResult {
  const [user, setUser] = useState<AuthUser>(() => readStoredUser(portal));

  const refresh = useCallback(() => {
    setUser(readStoredUser(portal));
  }, [portal]);

  const clear = useCallback(() => {
    try {
      localStorage.removeItem(STORAGE_KEYS[portal]);
    } catch {
      /* storage unavailable (private mode, etc.) — state reset below still applies */
    }
    setUser({});
  }, [portal]);

  useEffect(() => {
    refresh();
    // Keep in sync with logins/logouts from other tabs, and same-tab writes
    // made directly against localStorage elsewhere in the app.
    function onStorage(event: StorageEvent): void {
      if (event.key === STORAGE_KEYS[portal] || event.key === null) {
        refresh();
      }
    }
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [portal, refresh]);

  const privileges = Array.isArray(user.privileges)
    ? user.privileges
        .map((p) => p?.privilege)
        .filter((p): p is string => typeof p === 'string')
    : [];

  // Not memoized: `privileges` is recomputed from `user` every render anyway,
  // so a `useCallback` here would still change identity every render.
  const hasPrivilege = (privilege: string): boolean => privileges.includes(privilege);

  return {
    portal,
    user,
    privileges,
    hasPrivilege,
    isAuthenticated: Object.keys(user).length > 0,
    refresh,
    clear,
  };
}
