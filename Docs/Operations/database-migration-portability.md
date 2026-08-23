# Clean Database Migration Portability

CPay supports clean schema creation on MySQL 8.4 through Flyway. Published versioned migrations are immutable once released: never edit an already-applied `V*` migration to fix a new-environment problem, because doing so changes its Flyway checksum for existing environments.

## V28 audit trigger requirement

`V28__audit_trail_hash_chain.sql` creates database triggers that make `audit_trail` and `merchants_audit_trail` append-only. When MySQL binary logging is enabled, MySQL can require elevated privilege for `CREATE TRIGGER` unless `log_bin_trust_function_creators=1` is enabled.

For a brand-new database where V28 has not yet been applied, use one of these supported operating models:

1. Configure the MySQL server with `log_bin_trust_function_creators=1` before running Flyway. This is the default CPay verification model.
2. On a managed database, arrange an equivalent provider-supported privilege/configuration that permits the migration user to create the V28 triggers.

The migration user must also have ordinary schema DDL permissions and the MySQL `TRIGGER` privilege.

A `beforeMigrate` Flyway callback checks the binary-log trigger prerequisite before versioned migrations execute. If V28 is still pending and the server would reject trigger creation, startup fails with an actionable message instead of leaving V28 half-applied. Databases where V28 is already recorded as successful are unaffected.

## Verification

The repository contains `ops/mysql-verify/Dockerfile`, based on MySQL 8.4 and the official MySQL entrypoint, with the required trigger-creation capability enabled as a server argument. The clean migration CI lane applies the entire Flyway history to an empty schema using the normal non-root application database user and then verifies that the four V28 append-only triggers exist.

## Deployment rule

Before introducing a new MySQL hosting provider or creating a new CPay database:

- verify MySQL 8.4 compatibility;
- verify the application migration user has the required DDL and `TRIGGER` permissions;
- verify the V28 binary-log prerequisite when V28 is pending;
- run the clean migration smoke test against an empty schema;
- do not use `flyway repair` to conceal a failed clean-install test;
- do not rewrite historical migration files to make a new provider pass.

For future major schema generations, a Flyway baseline migration (`B<version>__...`) can be introduced to shorten clean installs while leaving the versioned upgrade history intact for existing environments.
