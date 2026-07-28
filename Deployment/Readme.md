# CPay Deployment Guide

This folder contains all scripts needed to deploy and update the CPay application on a CentOS or RHEL Linux server.

---

## Contents

| File | Purpose |
|------|---------|
| `scripts/deploy-server.sh` | Full first-time deployment — installs packages, builds the app, seeds the database, and configures Apache and systemd |
| `scripts/run-production-deploy.sh` | Pre-filled wrapper for production — edit your values here and run one command |
| `scripts/run-staging-deploy.sh` | Pre-filled wrapper for staging — edit your values here and run one command |
| `scripts/run-update-deploy.sh` | Code-only update — pulls latest code, rebuilds, re-runs Flyway migrations, and restarts the service |
| `scripts/build-and-deploy.bat` | Windows helper — builds locally then SSHs into the Linux host to trigger `deploy-server.sh` |

---

## How the deployment works

Running `deploy-server.sh` performs these steps in order:

1. Verify the script is running as root
2. Install required OS packages (`git`, `httpd`, `maven`, `nodejs`, Java 21, MySQL/MariaDB)
3. Enable and start the database service
4. Create the runtime directory tree under `/opt/cpay/<environment>/`
5. Generate the environment file at `/etc/cpay/<environment>.env` — passwords and secrets are auto-generated if not supplied
6. Verify the database connection using the configured credentials
7. Create the `cpay` Linux system user
8. Clone or update the Git repository
9. Build the React frontend (`npm ci && npm run build`)
10. Build the Spring Boot backend (`mvn -DskipTests package`)
11. Import the legacy SQL files in order: `structure.sql` → `seed.sql` → `migration_2024.sql`
    - For `seed.sql`, the script generates a bcrypt hash of the admin password and substitutes the `__ADMIN_PASSWORD_HASH__` placeholder automatically — `seed.sql` is never modified on disk
12. Run Flyway database migrations
13. Write and enable the `systemd` service unit (`cpay-<environment>.service`)
14. Configure Apache `httpd` as a reverse proxy and static file server
15. Start the service and poll the health endpoint for up to 120 seconds
16. Print the admin login credentials to the console

---

## Prerequisites

Before running the deployment script, ensure the following are in place on the target server:

- CentOS / RHEL 8 or later (or a compatible derivative such as AlmaLinux or Rocky Linux)
- Root access or `sudo` privileges
- The server can reach the Git repository (GitHub or equivalent)
- A MySQL or MariaDB database has been created along with a dedicated database user:

```sql
CREATE DATABASE cpayadmin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cpay_user'@'localhost' IDENTIFIED BY 'your_db_password';
GRANT ALL PRIVILEGES ON cpayadmin.* TO 'cpay_user'@'localhost';
FLUSH PRIVILEGES;
```

For staging on the same server, create a second database:

```sql
CREATE DATABASE cpayadmin_staging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON cpayadmin_staging.* TO 'cpay_user'@'localhost';
FLUSH PRIVILEGES;
```

- The domain name is pointed at the server's IP address (required for Certbot TLS)
- Port 80 and 443 are open in the firewall

---

## Environment variables

All behaviour is controlled through environment variables. Every variable has a sensible default where possible.

| Variable | Default | Description |
|----------|---------|-------------|
| `CPAY_ENVIRONMENT` | `production` | Deployment environment name. Use `staging` to run a second instance on the same server. |
| `CPAY_REPO_URL` | GitHub repo URL | Git repository to clone. |
| `CPAY_BRANCH` | `main` | Branch to deploy. |
| `CPAY_APP_ROOT` | `/opt/cpay` | Root directory for all deployment environments on this host. |
| `CPAY_DOMAIN` | `<env>.cpay.coresynergi.es` | Public domain name for this deployment. |
| `CPAY_HTTP_PORT` | `80` | Port Apache listens on for HTTP. |
| `CPAY_HTTPS_PORT` | `443` | Port Apache listens on for HTTPS. |
| `CPAY_USE_CERTBOT` | `false` | Set to `true` to have the script request a Let's Encrypt certificate via Certbot. |
| `CPAY_CERTBOT_EMAIL` | _(empty)_ | Email address Certbot uses for certificate registration and renewal notices. Required when `CPAY_USE_CERTBOT=true`. |
| `CPAY_CERTBOT_STAGING` | `false` | Set to `true` to use the Let's Encrypt staging endpoint (rate-limit safe for testing). |
| `CPAY_SSL_CERT_FILE` | _(empty)_ | Full path to an existing TLS certificate file. Use instead of Certbot if you manage your own certificates. |
| `CPAY_SSL_KEY_FILE` | _(empty)_ | Full path to the matching TLS private key. |
| `CPAY_SSL_REDIRECT` | `true` | Redirect HTTP to HTTPS when TLS is configured. |
| `CPAY_DB_NAME` | `cpayadmin` / `cpayadmin_<env>` | Database name. Defaults to `cpayadmin` for production and `cpayadmin_<environment>` for any other environment. |
| `CPAY_DB_USER` | `cpay_user` | Database user the application connects as. |
| `CPAY_DB_PASSWORD` | _(auto-generated)_ | Database password. Auto-generated and stored in the env file if not supplied. |
| `CPAY_ADMIN_EMAIL` | `admin@example.com` | Email address of the initial super-admin account created during first deployment. |
| `CPAY_ADMIN_PASSWORD` | _(auto-generated)_ | Plain-text password for the initial super-admin account. The script hashes it with bcrypt before inserting it into the database. Auto-generated if not supplied. Printed to the console and stored in the env file at the end of deployment. |
| `CPAY_BACKEND_PORT` | `8081` / `8082` | Port the Spring Boot backend listens on. Defaults to `8081` for production and `8082` for staging. |
| `CPAY_USER` | `cpay` | Linux system user the service runs as. |

---

## First-time deployment

### Option 1 — Edit the runner script (recommended)

Two pre-filled runner scripts are provided — one for each environment. Open the appropriate file, fill in every `change_me` value, and run it with a single command. Both scripts call `sudo` internally, so they do not need to be run as root directly.

#### Production

Open `scripts/run-production-deploy.sh` and fill in the `change_me` values:

```bash
export CPAY_ENVIRONMENT="production"
export CPAY_DOMAIN="cpay.example.com"
export CPAY_DB_NAME="cpayadmin"
export CPAY_DB_USER="cpay_user"
export CPAY_DB_PASSWORD='a_strong_db_password'
export CPAY_ADMIN_EMAIL="admin@yourcompany.com"
export CPAY_ADMIN_PASSWORD='a_strong_admin_password'
export CPAY_BACKEND_PORT="8081"
export CPAY_HTTP_PORT="80"
export CPAY_HTTPS_PORT="443"
export CPAY_USE_CERTBOT="true"
export CPAY_CERTBOT_EMAIL="admin@yourcompany.com"
export CPAY_CERTBOT_STAGING="false"
export CPAY_SSL_REDIRECT="true"
```

Then run:

```bash
bash /path/to/Deployment/scripts/run-production-deploy.sh
```

#### Staging

Open `scripts/run-staging-deploy.sh` and fill in the `change_me` values:

```bash
export CPAY_ENVIRONMENT="staging"
export CPAY_DOMAIN="staging.cpay.example.com"
export CPAY_DB_NAME="cpayadmin_staging"
export CPAY_DB_USER="cpay_user"
export CPAY_DB_PASSWORD='a_strong_db_password'
export CPAY_ADMIN_EMAIL="admin@yourcompany.com"
export CPAY_ADMIN_PASSWORD='a_strong_admin_password'
export CPAY_BACKEND_PORT="8082"
export CPAY_HTTP_PORT="80"
export CPAY_HTTPS_PORT="443"
export CPAY_USE_CERTBOT="true"
export CPAY_CERTBOT_EMAIL="admin@yourcompany.com"
export CPAY_CERTBOT_STAGING="false"
export CPAY_SSL_REDIRECT="true"
```

Then run:

```bash
bash /path/to/Deployment/scripts/run-staging-deploy.sh
```

---

### Option 2 — Inline environment variables

Pass variables directly on the command line:

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_DB_NAME=cpayadmin \
     CPAY_DB_USER=cpay_user \
     CPAY_DB_PASSWORD='a_strong_db_password' \
     CPAY_ADMIN_EMAIL=admin@yourcompany.com \
     CPAY_ADMIN_PASSWORD='a_strong_admin_password' \
     CPAY_BACKEND_PORT=8081 \
     CPAY_USE_CERTBOT=true \
     CPAY_CERTBOT_EMAIL=admin@yourcompany.com \
     bash /path/to/Deployment/scripts/deploy-server.sh
```

---

### Option 3 — Windows build machine

If you are building locally on Windows and deploying to a remote Linux server, use `scripts/build-and-deploy.bat`. It builds both the frontend and backend locally, then SSHs into the target host to run `deploy-server.sh`.

Set the following environment variables before running (or edit the defaults inside the script):

| Variable | Description |
|----------|-------------|
| `CPAY_DEPLOY_HOST` | SSH hostname or alias of the Linux server (default: `instance-dev`) |
| `CPAY_DEPLOY_USER` | SSH user on the remote host (default: `opc`) |
| `CPAY_DEPLOY_PORT` | SSH port (default: `22`) |
| `CPAY_DEPLOY_SSH_KEY` | Path to your SSH private key (default: `%USERPROFILE%\.ssh\id_rsa`) |
| `CPAY_BRANCH` | Branch to deploy (default: `main`) |
| `CPAY_REPO_URL` | Git repository URL |
| `CPAY_DOMAIN` | Public domain for the deployment |
| `CPAY_APP_ROOT` | App root on the remote host (default: `/opt/cpay`) |

Then run from a Command Prompt or PowerShell:

```bat
scripts\build-and-deploy.bat
```

The script requires `mvn`, `npm`, `ssh`, and `scp` to be available in `PATH`. Java 21 and Node.js 20+ must be installed on the Windows build machine.

---

## TLS / HTTPS configuration

### Let's Encrypt via Certbot (recommended for internet-facing servers)

Set `CPAY_USE_CERTBOT=true` and provide a valid email address. The script will issue a certificate for `CPAY_DOMAIN`, configure Apache, and set up automatic renewal.

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_USE_CERTBOT=true \
     CPAY_CERTBOT_EMAIL=admin@yourcompany.com \
     CPAY_CERTBOT_STAGING=false \
     bash deploy-server.sh
```

To test certificate issuance without consuming your rate limit, set `CPAY_CERTBOT_STAGING=true` first. Once you have confirmed that validation works, re-run with `CPAY_CERTBOT_STAGING=false` to issue a real certificate.

### Existing certificate

If you manage your own certificates, provide the file paths directly and skip Certbot:

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_SSL_CERT_FILE=/etc/ssl/certs/cpay.crt \
     CPAY_SSL_KEY_FILE=/etc/ssl/private/cpay.key \
     bash deploy-server.sh
```

### HTTP only

If `CPAY_USE_CERTBOT` is `false` and no certificate paths are provided, the script configures Apache for HTTP only. You can add TLS later by re-running with the certificate variables set.

---

## Running production and staging on the same server

Use `run-production-deploy.sh` for production and `run-staging-deploy.sh` for staging. The script namespaces every resource (directories, service unit, Apache config, env file) by environment name, so they never collide.

| Resource | Production | Staging |
|----------|-----------|---------|
| Runner script | `run-production-deploy.sh` | `run-staging-deploy.sh` |
| App directory | `/opt/cpay/production/` | `/opt/cpay/staging/` |
| Env file | `/etc/cpay/production.env` | `/etc/cpay/staging.env` |
| systemd service | `cpay-production.service` | `cpay-staging.service` |
| Apache config | `/etc/httpd/conf.d/cpay-production.conf` | `/etc/httpd/conf.d/cpay-staging.conf` |
| Backend port | `8081` | `8082` |
| Database | `cpayadmin` | `cpayadmin_staging` |

---

## Admin credentials

At the end of a first-time deployment, the script prints the super-admin login credentials:

```
[cpay-deploy] Admin login email:    admin@yourcompany.com
[cpay-deploy] Admin login password: <generated or supplied password>
[cpay-deploy] Record the password above; it is also stored in /etc/cpay/<environment>.env
```

The password is stored in plain text inside the environment file (`/etc/cpay/<environment>.env`, readable only by root and the `cpay` service user). **Change the admin password through the application as soon as the deployment is complete.**

The script hashes the password with bcrypt (cost 10) before writing it to the database. The `seed.sql` source file is never modified — the substitution happens entirely in memory during deployment.

On subsequent deployments, `seed.sql` uses `INSERT IGNORE`, so the admin row is not re-inserted if it already exists. The admin password is therefore only set during the very first deployment.

---

## Updating an existing deployment

To pull the latest code, rebuild, re-run Flyway migrations, and restart the service without touching the database seed or re-generating secrets:

```bash
sudo bash /path/to/Deployment/scripts/run-update-deploy.sh
```

Or with an explicit environment override:

```bash
sudo CPAY_ENVIRONMENT=production bash /path/to/Deployment/scripts/run-update-deploy.sh
```

The update script:
1. Loads the existing env file — no credentials need to be supplied again
2. Pulls the latest commit from the configured branch
3. Rebuilds the frontend and backend
4. Runs any pending Flyway migrations
5. Restarts the service and verifies the health endpoint

The update script does **not** re-import the legacy SQL files (`structure.sql`, `seed.sql`, `migration_2024.sql`). Those are imported only once during first deployment.

---

## Directory layout on the server

```
/opt/cpay/
└── <environment>/
    ├── source/          # Git working tree
    ├── bin/
    │   └── cpay.jar     # Compiled Spring Boot JAR
    └── www/             # Compiled React frontend (served by Apache)

/etc/cpay/
└── <environment>.env    # Runtime secrets and configuration (chmod 640)

/var/opt/cpay/locks/
└── <environment>/       # Lock files used by the application

/etc/systemd/system/
└── cpay-<environment>.service

/etc/httpd/conf.d/
└── cpay-<environment>.conf
```

---

## Post-deployment verification

The script automatically polls the health endpoint after starting the service. You can also verify manually:

```bash
# Production (port 8081)
curl http://127.0.0.1:8081/status/health

# Staging (port 8082)
curl http://127.0.0.1:8082/status/health
```

---

## Service management

Replace `cpay-production` or `cpay-staging` below depending on which environment you are managing.

```bash
# View live service logs
sudo journalctl -u cpay-production.service -f
sudo journalctl -u cpay-staging.service -f

# Restart the service
sudo systemctl restart cpay-production.service

# Stop the service
sudo systemctl stop cpay-production.service

# Check service status
sudo systemctl status cpay-production.service

# View the last 100 log lines (useful after a failed start)
sudo journalctl -u cpay-production.service -n 100 --no-pager
```

---

## Notes

- The environment file (`/etc/cpay/<environment>.env`) is the single source of truth for all runtime secrets. It is written once during the first deployment and re-used by the update script on every subsequent run. Do not delete it between updates.
- Never commit real credentials or production secrets to the repository. All secrets are generated at deploy time and stored only on the server.
- The Apache configuration proxies these paths to the Spring Boot backend: `/api`, `/auth`, `/admins`, `/audittrail`, `/merchants`, `/settings`, `/status`, `/transactions`, `/actuator`. All other paths are served from the React build and fall back to `index.html` for client-side routing.
- The deployment script is idempotent for most operations but is intended for first-time setup. Use `run-update-deploy.sh` for routine code updates.
