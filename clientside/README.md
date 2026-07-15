# CPay — Client (Admin & Merchant Portal)

React 18 SPA built with **Vite 8** and **TypeScript**, talking to the CPay
Spring Boot backend over a cookie-based session (`credentials: 'include'`).

Requires Node.js **20.19.0 or later**.

## Stack

- **Vite 8** (Rolldown) + `@vitejs/plugin-react` 6
- **React 18.3** (see `MIGRATION.md` for the React 19 upgrade gate)
- **React Router v7** (`react-router-dom`)
- **TanStack Query v5** for server state
- **Tailwind CSS v4** (Vite plugin)
- **TypeScript 5.7** — incremental, `allowJs` (legacy `.jsx` coexists)
- **Vitest** + Testing Library
- UI: CPay iOS-style component primitives in `src/ui`, brand tokens in `src/index.css`, and modernized legacy module surfaces. The migration away from `rc-easyui` is tracked in `MIGRATION.md`.

## Scripts

| Command | Description |
| --- | --- |
| `npm run dev` | Start the dev server on http://localhost:3000 (proxies `/api`, `/auth`, … to `localhost:8081`). |
| `npm run build` | Production build to `build/`. |
| `npm run preview` | Preview the production build. |
| `npm run typecheck` | `tsc --noEmit`. |
| `npm test` | Run the test suite once (Vitest). |
| `npm run test:watch` | Watch mode. |
| `npm run test:coverage` | Coverage report. |
| `npm run lint` | ESLint (flat config). |

## Configuration

Set the API base (optional; defaults to same-origin so the dev proxy works):

```
VITE_API_BASE=https://api.example.com
```

Accessed via `src/shared/config.ts` (`API_BASE`, `apiUrl()`).

## Conventions

- New HTTP calls go through `src/shared/api/httpClient.ts`; new server state uses
  TanStack Query hooks (see `src/shared/api/hooks.ts`).
- New routing code uses native React Router v7 hooks. Legacy class components use
  the `withRouter`/`useHistory` compatibility shim in `src/shared/router/compat.tsx`
  — do not import from it in new code.
