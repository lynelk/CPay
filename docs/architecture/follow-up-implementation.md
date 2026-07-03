# Follow-up Implementation Scope

This clean branch starts from current main and excludes temporary files from the diverged branch.

Implemented areas:

1. safer v2 routing through legacy gateway id lookup to avoid prefix collisions
2. normalized merchant channel balance tables and balance read service
3. BigDecimal money value object usage in v2 orchestration amount parsing
4. callback task queue with retry and parked-final state
5. reconciliation tables and endpoints for unmatched, auto match, and operator match
6. merchant balance endpoint
7. gateway operations endpoints
8. frontend path scaffold under clientside

Remaining production hardening:

- run CI and fix any compile failures
- wire callback queue into every transaction callback path
- add provider statement import parsers
- add maker-checker approval for reconciliation adjustments
- add role checks on admin routes
- expand frontend routes and UI after backend validation
