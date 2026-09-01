# Administrator initial password change

The administrator login flow can return `PASSWORD_CHANGE_REQUIRED` after valid temporary
credentials are submitted. In that state the server invalidates the authenticated session and
creates a restricted session that cannot access administrator or portal routes.

The login client must then submit the replacement password to:

```http
POST /auth/completeInitialPasswordChange
Content-Type: application/json

{
  "new_password": "<new password>",
  "confirm_password": "<same new password>"
}
```

The endpoint is available only to the restricted session established by a successful temporary
login. Passwords must contain 12 to 72 characters, with uppercase, lowercase, numeric, and symbol
characters and no whitespace. Reuse of the temporary password is rejected. A successful response
has code `000`, clears the mandatory-change flag, revokes existing administrator sessions, and
requires a fresh login.

## Exclusive administrator bootstrap

The disabled-by-default operational bootstrap accepts only a cost-12 bcrypt hash. It uses an
idempotent operation ID and a single database transaction to activate the target administrator,
grant all documented legacy portal privileges plus any privileges already present in the running
database, revoke administrator sessions and MFA registrations, close active impersonation
sessions, remove all other interactive administrator accounts, and verify the final row counts.

The bootstrap properties are `cpay.exclusive-admin.apply`, `operation-id`, `email`, `name`, and
`password-hash`. The raw temporary password must never be placed in application configuration.
