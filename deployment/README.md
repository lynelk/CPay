# CPay Deployment Guide

This folder contains the Linux deployment helper for the CPay application.

## What the script does

The script in `scripts/deploy-server.sh` is designed to be run on a CentOS/RHEL Linux host and will:

1. install the required OS packages
2. enable and start MySQL or MariaDB
3. create the runtime directories under `/opt/cpay`
4. create an environment file under `/etc/cpay`
5. pull the selected Git branch into the deployment workspace
6. build the frontend and Spring Boot backend artifacts
7. create a dedicated `systemd` service
8. create an Apache `httpd` virtual host for the selected environment
9. restart the service and verify health

## Supported deployment model

The script supports running both production and staging on the same server by setting the `CPAY_ENVIRONMENT` variable.

## Required runtime assumptions

- You are running the script as `root`
- The target host has access to the Git repository
- Java 21 is installed or will be installed by the script
- Node.js 20+ is installed or will be installed by the script
- A MySQL-compatible database is available on the server
- Apache `httpd` is the public web server

## Environment variables

The script supports the following variables:

- `CPAY_ENVIRONMENT` — `production` or `staging` (default: `production`)
- `CPAY_REPO_URL` — Git repository URL (default: GitHub repo)
- `CPAY_BRANCH` — branch to deploy (default: `frontend/ios-design-system`)
- `CPAY_APP_ROOT` — base app directory (default: `/opt/cpay`)
- `CPAY_DOMAIN` — public domain for the deployment
- `CPAY_HTTP_PORT` — Apache HTTP port (default: `80`)
- `CPAY_HTTPS_PORT` — Apache HTTPS port (default: `443`)
- `CPAY_USE_CERTBOT` — set to `true` to let Certbot create and manage the TLS certificate
- `CPAY_CERTBOT_EMAIL` — email used by Certbot for renewal and account registration
- `CPAY_CERTBOT_STAGING` — set to `true` to use Let’s Encrypt staging when testing
- `CPAY_SSL_CERT_FILE` — full path to the TLS certificate file
- `CPAY_SSL_KEY_FILE` — full path to the TLS private key file
- `CPAY_SSL_REDIRECT` — set to `true` to redirect HTTP to HTTPS when SSL is configured
- `CPAY_DB_NAME` — database name (defaults to `cpayadmin` for production and `cpayadmin_staging` for staging)
- `CPAY_DB_USER` — DB user name (default: `cpay_user`)
- `CPAY_DB_PASSWORD` — optional DB password override
- `CPAY_BACKEND_PORT` — Spring Boot backend port (defaults to `8081` for production and `8082` for staging)
- `CPAY_USER` — Linux service user (default: `cpay`)
- `MYSQL_ROOT_PASSWORD` — optional root DB password if the local MySQL root account requires one

## Example: production deployment

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_DB_NAME=cpayadmin \
     CPAY_DB_USER=cpay_user \
     CPAY_BACKEND_PORT=8081 \
     bash /path/to/deploy-server.sh
```

## Example: staging deployment on the same server

```bash
sudo CPAY_ENVIRONMENT=staging \
     CPAY_DOMAIN=staging.cpay.example.com \
     CPAY_DB_NAME=cpayadmin_staging \
     CPAY_DB_USER=cpay_user \
     CPAY_BACKEND_PORT=8082 \
     bash /path/to/deploy-server.sh
```

## One-file staging runner

A single wrapper file is now included in the deployment scripts folder so the full staging environment can be executed with one command:

```bash
sudo bash /path/to/deployment/scripts/run-staging-deploy.sh
```

The wrapper file contains the exact environment values needed for the staging deployment, including the database name, backend port, and Certbot settings.

## Example: HTTPS deployment with Certbot

If you want the script to request a Let’s Encrypt certificate through Certbot, set `CPAY_USE_CERTBOT=true` and provide a valid email address. The script will enable the Apache SSL proxy configuration, create the selected vhost, and let Certbot issue or renew the certificate for the requested domain.

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_HTTP_PORT=80 \
     CPAY_HTTPS_PORT=443 \
     CPAY_USE_CERTBOT=true \
     CPAY_CERTBOT_EMAIL=admin@example.com \
     CPAY_CERTBOT_STAGING=false \
     CPAY_SSL_REDIRECT=true \
     bash /path/to/deploy-server.sh
```

If you are testing certificate issuance and want to use Let’s Encrypt staging:

```bash
sudo CPAY_ENVIRONMENT=staging \
     CPAY_DOMAIN=staging.cpay.example.com \
     CPAY_USE_CERTBOT=true \
     CPAY_CERTBOT_EMAIL=admin@example.com \
     CPAY_CERTBOT_STAGING=true \
     bash /path/to/deploy-server.sh
```

If you already have a certificate file and private key on the server, you can skip Certbot and provide the paths directly:

```bash
sudo CPAY_ENVIRONMENT=production \
     CPAY_DOMAIN=cpay.example.com \
     CPAY_HTTP_PORT=80 \
     CPAY_HTTPS_PORT=443 \
     CPAY_SSL_CERT_FILE=/etc/ssl/certs/cpay.crt \
     CPAY_SSL_KEY_FILE=/etc/ssl/private/cpay.key \
     CPAY_SSL_REDIRECT=true \
     bash /path/to/deploy-server.sh
```

### Certbot notes

- `CPAY_USE_CERTBOT=true` is the preferred option when the host is reachable on the public internet and the domain is already pointed to the server.
- `CPAY_CERTBOT_STAGING=true` is useful for validation runs before switching to the production certificate endpoint.
- The deployment script expects Apache `httpd` to be active and listening on the chosen HTTP/HTTPS ports so Certbot can complete the domain validation and renewal flow.

## Notes

- The script is meant for controlled server-side deployment and should be run from the target Linux host.
- The runtime environment file is stored outside the repository under `/etc/cpay/<environment>.env`.
- Production-only secrets should never be committed into the repo.
- Use different domains and backend ports for production and staging so both can coexist on the same server.
- Before Flyway executes, the deployment flow imports the legacy SQL files in this order: `structure.sql`, `initialize.sql`, `cpayadmin.sql`, and `migration_2024.sql`.
- The Apache config generated by the script lives under `/etc/httpd/conf.d/`.

## Post-deployment verification

After deployment, confirm the backend health endpoint:

```bash
curl http://127.0.0.1:8081/status/health
```

If you are deploying staging and it uses port `8082`, use:

```bash
curl http://127.0.0.1:8082/status/health
```
