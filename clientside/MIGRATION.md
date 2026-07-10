# Front-end Modernization — 2026

This document records the front-end modernization performed on the `clientside`
app, what was intentionally deferred, and why.

## Summary

The build tooling was already current (Vite 8, Tailwind 4, plugin-react 6), but
the application layer was several years behind and still carried Create React App
(CRA) scaffolding. This pass removed CRA, migrated routing off the end-of-life
React Router v5 API, introduced TypeScript and TanStack Query incrementally, moved
tests to Vitest, and added code-splitting — all on a **React 18.3 baseline**.

Verification gates (all green):

- `npm run build` — production build succeeds (Vite 8 / Rolldown)
- `npm test` — 14 test files, 129 tests pass (Vitest)
- `npm run typecheck` — `tsc --noEmit` clean

## What changed

### Tooling / hygiene
- **Removed CRA entirely**: deleted `react-scripts`, `config-overrides.js`,
  `customize-cra`, `src/serviceWorker.js`, and the CRA `eslintConfig`. Dependency
  count dropped from ~1,000 to ~410 installed packages.
- **Single package manager**: removed `yarn.lock`; npm (`package-lock.json`) is
  canonical.
- **Env**: replaced `process.env.REACT_APP_*` / `PUBLIC_URL` with Vite's
  `import.meta.env.VITE_API_BASE`, centralized in `src/shared/config.ts`. A
  build-time `define` fallback keeps any straggler references working during the
  incremental migration.
- **ESLint**: added a flat config (`eslint.config.js`) for JS/TS/React.
- Added a missing **`prop-types`** dependency (was being resolved transitively
  through CRA's tree and would have broken at runtime once CRA was removed).

### TypeScript (incremental)
- Added `tsconfig.json` with `allowJs: true`, `checkJs: false`, `strict: true`,
  so legacy `.jsx` coexists with new typed code.
- Converted the app wiring to TSX: `index.tsx`, `App.tsx`, `Routers.tsx`.
- Typed shared layer: `shared/config.ts`, `shared/api/httpClient.ts`,
  `shared/api/v2Client.ts`, `shared/api/hooks.ts`, `shared/router/compat.tsx`,
  `shared/queryClient.ts`.

### Routing: React Router v5 → v7
- `Routers` migrated from `<Switch>` + route-as-children to `<Routes>` +
  `element={...}`.
- v6+ removed `withRouter` and `useHistory`. Rather than rewrite 24 (mostly class)
  components, a faithful **compatibility shim** (`src/shared/router/compat.tsx`)
  re-implements both on top of `useNavigate`; the 24 files only changed their
  import line. Behavior is preserved 1:1 (`history.push` → `navigate`,
  `history.goBack` → `navigate(-1)`, etc.).
- **v7, not v8**, deliberately — see the React 19 gate below.

### Server state: TanStack Query
- `QueryClientProvider` wired in `index.tsx`.
- `shared/api/httpClient.ts` (typed, cookie-aware fetch) and
  `shared/api/hooks.ts` (query/mutation hooks for the v2 endpoints) establish the
  pattern. Legacy modules still hand-roll `fetch`; migrate them file by file.

### Tests: CRA/Jest → Vitest
- `npm test` now runs Vitest (jsdom, globals, `vitest.setup.ts` for jest-dom
  matchers).
- Migrated `jest.*` → `vi.*`; fixed ESM-interop mock factories (`{ default: … }`)
  and renamed a JSX-containing `.js` test to `.jsx`.

### Performance
- Route-level **code-splitting**: heavy authenticated layouts
  (`Layout`, `LayoutMerchant`, `OperationsConsole`, `MerchantSignup`) are lazy
  loaded. Initial (login) chunk dropped from ~1,198 kB to ~776 kB; admin/merchant
  bundles now load on demand.

## Deferred — and why (the React 19 gate)

Three recommendations are **intentionally not done yet** because they share a hard
blocker: **`rc-easyui@1.0.39`**. It is a 2018-era library (published under Node 10,
no declared peer deps) and the entire auth + data-grid UI is built on it. React 19
removed `findDOMNode`, legacy context, and string refs — APIs a library of that
vintage relies on — so bumping React would compile but crash the UI at runtime.

Blocked until `rc-easyui` is replaced:

1. **React 18 → 19.**
2. **React Router v7 → v8** (v8's baseline requires React 19).
3. **UI-kit consolidation** (retire `rc-easyui`; standardize on one system —
   recommended: shadcn/ui + Radix on Tailwind v4 for the dark Stripe/Brex/Mercury
   aesthetic, replacing the mixed `rc-easyui` + `antd` setup).

Recommended sequence for the next pass: replace `rc-easyui` surface-by-surface
(auth screens first, then each data-grid module), then bump React 19 → Router v8,
then drop the `react-router-dom` package in favor of `react-router` + `react-router/dom`
(the v8 packaging change).

## Other known follow-ups (not blocking)
- Migrate the large monolithic modules' `fetch` calls onto TanStack Query hooks.
- Convert `.jsx` modules to `.tsx` incrementally now that `allowJs` is in place.
- Move the `localStorage` user/role reads behind a single `shared/auth` helper;
  ensure the **backend** enforces authorization on legacy routes (currently
  `anyRequest().permitAll()` at the Spring Security layer — tracked in the backend
  workstream).

## Slice 1 — iOS auth screens (rc-easyui proof of pattern)

First surface migrated off `rc-easyui`, establishing the design-system seed.

**New design-system layer**
- `src/styles/ios.css` — iOS token layer (reuses existing brand/iOS tokens; adds the three missing ingredients: materials/translucency, spring motion, and a real dark palette) plus `.ios-*` component classes. Imported once in `index.tsx`.
- `src/ui/` — reusable primitives: `AuthLayout` (frosted shell), `Button` (variants + loading), `TextField`, `PasswordField` (show/hide), `Alert`, `Spinner`, with a barrel `index.ts`.

**Screens rewritten** (class → typed function components, on the primitives):
- `Login.tsx`, `LoginMerchant.tsx`, `MerchantSignup.tsx` — all backend contracts preserved verbatim (`/auth/authenticate`, `/auth/authenticateMerchantUser`, `/auth/isLoggedIn`, `/auth/isMerchantUserLoggedIn`, `/api/v2/merchant-self-service/signup`, the `user`/`merchantUser` `localStorage` keys, all redirects, the `uiportal` query redirect). Enter-to-submit now via native `<form onSubmit>`; errors shown inline via `Alert` instead of the rc-easyui-adjacent modal; loading shown via the button spinner instead of the rc-easyui `Progress` dialog.
- All three now use native `useNavigate` (dropped the `withRouter`/`useHistory` compat shim); shim consumers fell from 24 to 21.

**Still on rc-easyui (next slice):** the forgot-password modals `LoginForgotPassword` / `LoginForgotPasswordMerchant`, which `Login`/`LoginMerchant` still open. `MerchantSignup` is fully rc-easyui-free.

**Aesthetic:** brand-consistent iOS — teal accent (`#1198C4`) + SF-first font stack, frosted translucent card, spring press feedback, automatic light/dark via `prefers-color-scheme`.

Gates after this slice: build passes, 130 tests pass, `tsc --noEmit` clean.
