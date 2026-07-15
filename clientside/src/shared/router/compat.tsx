/**
 * React Router v5 -> v7 compatibility shim.
 *
 * React Router v6+ removed `withRouter` and the `useHistory` hook. The CPay
 * codebase has 24 components (mostly class components) that rely on the v5
 * `this.props.history.push(...)` / `useHistory()` API. Rather than rewrite each
 * component's body, those files now import `withRouter` / `useHistory` from this
 * shim instead of from `react-router-dom`. Behaviour is preserved 1:1.
 *
 * New code should use the native v7 hooks (`useNavigate`, `useParams`, etc.)
 * directly and must NOT import from this shim.
 */
import React from 'react';
import {
  useNavigate,
  useLocation,
  useParams,
  type NavigateOptions,
  type To,
} from 'react-router-dom';

export interface LegacyHistory {
  push: (to: To, opts?: NavigateOptions) => void;
  replace: (to: To, opts?: NavigateOptions) => void;
  goBack: () => void;
  goForward: () => void;
  go: (delta: number) => void;
}

/** v5-style `useHistory()` backed by the v7 `useNavigate()`. */
export function useHistory(): LegacyHistory {
  const navigate = useNavigate();
  return React.useMemo<LegacyHistory>(
    () => ({
      push: (to, opts) => navigate(to, opts),
      replace: (to, opts) => navigate(to, { ...opts, replace: true }),
      goBack: () => navigate(-1),
      goForward: () => navigate(1),
      go: (delta) => navigate(delta),
    }),
    [navigate],
  );
}

export interface WithRouterProps {
  history: LegacyHistory;
  location: ReturnType<typeof useLocation>;
  match: { params: Readonly<Record<string, string | undefined>> };
  params: Readonly<Record<string, string | undefined>>;
  navigate: ReturnType<typeof useNavigate>;
}

/**
 * v5-style `withRouter` HOC. Injects `history`, `location`, `match`, `params`
 * and the raw v7 `navigate` function as props.
 */
export function withRouter<P extends WithRouterProps>(
  Component: React.ComponentType<P>,
): React.FC<Omit<P, keyof WithRouterProps>> {
  const Wrapped: React.FC<Omit<P, keyof WithRouterProps>> = (props) => {
    const history = useHistory();
    const location = useLocation();
    const params = useParams();
    const navigate = useNavigate();
    const injected = {
      history,
      location,
      match: { params },
      params,
      navigate,
    } as WithRouterProps;
    return <Component {...(props as P)} {...injected} />;
  };
  Wrapped.displayName = `withRouter(${Component.displayName || Component.name || 'Component'})`;
  return Wrapped;
}
