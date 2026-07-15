# Front-end Modernization — 2026

This document records the front-end modernization performed on the `clientside`
app, what was intentionally deferred, and why.

## Summary

The build tooling was already current (Vite 8, Tailwind 4, plugin-react 6), but
the application layer was several years behind and still carried Create React App
(CRA) scaffolding. This pass removed CRA, migrated routing off the end-of-life
React Router v5 API, introduced TypeScript and TanStack Query incrementally, moved
tests to Vitest, and added code-splitting — all on a **React 18.3 baseline**.

Latest verification gates:

- `npm run build` — production build succeeds (Vite 8 / Rolldown)
- `npm test` — 14 test files, 130 tests pass (Vitest)
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

## Current UI state

The runtime package set no longer includes `rc-easyui` or `antd`. Auth, admin,
merchant, dashboard, settings, payments, SMS, audit, transactions, and merchant
account surfaces use the CPay iOS-style primitives in `src/ui`, brand tokens in
`src/index.css`, and shared helpers such as `DateField`, `DatetimePicker`,
`FileButton`, `ProgressOverlay`, `Sheet`, `Table`, and typed HTTP utilities.

The remaining `rc-easyui` references in source comments and tests document the
replacement path and protect against regressions. They are not package imports.

## Deferred — React 19 gate

React remains on **18.3** intentionally. The next major frontend platform move is
React 19 plus the corresponding router/package update. Do that as a separate
upgrade after one more browser smoke pass over admin login, merchant login,
merchant signup, dialogs, editable tables, file uploads, dashboard cards, and
payment-channel setup.

## Other known follow-ups (not blocking)
- Continue moving the large monolithic modules' `fetch` calls onto TanStack Query hooks.
- Convert `.jsx` modules to `.tsx` incrementally now that `allowJs` is in place.
- Move the `localStorage` user/role reads behind a single `shared/auth` helper;
  the backend now has a legacy session authorization filter for protected portal
  routes, but frontend state should still be centralized.

## Slice 1 — iOS auth screens (rc-easyui proof of pattern)

First surface migrated off `rc-easyui`, establishing the design-system seed.

**New design-system layer**
- `src/styles/ios.css` — iOS token layer (reuses existing brand/iOS tokens; adds the three missing ingredients: materials/translucency, spring motion, and a real dark palette) plus `.ios-*` component classes. Imported once in `index.tsx`.
- `src/ui/` — reusable primitives: `AuthLayout` (frosted shell), `Button` (variants + loading), `TextField`, `PasswordField` (show/hide), `Alert`, `Spinner`, with a barrel `index.ts`.

**Screens rewritten** (class → typed function components, on the primitives):
- `Login.tsx`, `LoginMerchant.tsx`, `MerchantSignup.tsx` — all backend contracts preserved verbatim (`/auth/authenticate`, `/auth/authenticateMerchantUser`, `/auth/isLoggedIn`, `/auth/isMerchantUserLoggedIn`, `/api/v2/merchant-self-service/signup`, the `user`/`merchantUser` `localStorage` keys, all redirects, the `uiportal` query redirect). Enter-to-submit now via native `<form onSubmit>`; errors shown inline via `Alert` instead of the rc-easyui-adjacent modal; loading shown via the button spinner instead of the rc-easyui `Progress` dialog.
- All three now use native `useNavigate` (dropped the `withRouter`/`useHistory` compat shim for these screens).

**Follow-on slices completed:** forgot-password screens, dashboard cards, admin and merchant settings, transactions, audit trail, administrators, merchant account, payments, SMS, file upload controls, and reusable iOS dialogs/tables have been migrated to the current design primitives.

**Aesthetic:** brand-consistent iOS — teal accent (`#1198C4`) + SF-first font stack, frosted translucent card, spring press feedback, automatic light/dark via `prefers-color-scheme`.

Gates after this slice: build passes, 130 tests pass, `tsc --noEmit` clean.
