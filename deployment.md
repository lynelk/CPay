# CPay Deployment Guide

This guide covers a Linux service deployment for the CPay backend and the production frontend artifact. It assumes the release has already passed the build, test, provider, security, finance, and operations gates listed in `docs/readiness/market-readiness-gates.md`.

## Runtime

| Item | Value |
|---|---|
| Backend | Spring Boot 4.1, Java 21 |
| Backend artifact | `InitializrSpringbootProjectFresh/target/cito-fresh-0.0.1-SNAPSHOT.jar` |
| Default backend port | `8081` through `HTTP_PORT` |
| Frontend | React 18, Vite 8, output in `clientside/build` |
| Database | MySQL 8 compatible |
| Sessions | Spring Session JDBC tables managed by Flyway baseline migration |

## Build Artifacts

Backend:

```bash
cd InitializrSpringbootProjectFresh
mvn clean package
```

Frontend:

```bash
cd clientside
npm install
npm run typecheck
npm test
npm run build
```

## Server Directories

Create the backend service directories:

```bash
sudo mkdir -p /opt/cpay/bin
sudo mkdir -p /etc/cpay
sudo mkdir -p /var/opt/cpay/locks
sudo chown -R cpay:cpay /opt/cpay /var/opt/cpay
sudo chmod 750 /etc/cpay
sudo chmod 755 /var/opt/cpay/locks
```

Copy the backend artifact:

```bash
sudo cp InitializrSpringbootProjectFresh/target/cito-fresh-0.0.1-SNAPSHOT.jar /opt/cpay/bin/
sudo chown cpay:cpay /opt/cpay/bin/cito-fresh-0.0.1-SNAPSHOT.jar
```

The legacy helper scripts under `setup/` have also been updated to use `/opt/cpay`, `/var/log/cpay`, `/var/opt/cpay/locks`, and the `cito-fresh-0.0.1-SNAPSHOT.jar` artifact. Prefer the systemd service below for production, and use the scripts only for controlled manual installs or transitional hosts.

## Environment File

Create `/etc/cpay/.env` and keep it outside source control.

```bash
DB_URL=jdbc:mysql://db-host:3306/cpayadmin
DB_USERNAME=cpay_user
DB_PASSWORD=replace_with_secret

HTTP_PORT=8081
APP_BASE_URL=https://cpay.coresynergi.es
CORS_ALLOWED_ORIGINS=https://cpay.coresynergi.es,https://portal.coresynergi.es

CUSTOM_GATEWAYSTATE=PRODUCTION
CUSTOM_SSL_SKIP_VERIFY=false
CUSTOM_LOCKFILEDIRECTORY=/var/opt/cpay/locks

MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=replace_with_secret
MAIL_PASSWORD=replace_with_secret

ACTUATOR_USERNAME=replace_with_secret
ACTUATOR_PASSWORD=replace_with_secret
ADMIN_API_USERNAME=replace_with_secret
ADMIN_API_PASSWORD=replace_with_secret

CALLBACK_SIGNING_SECRET=replace_with_long_random_secret
MERCHANT_CHANNEL_ENCRYPTION_KEY=replace_with_long_random_secret

SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
CPAY_SECURITY_NONCE_STORE=jdbc
```

Production channel credentials should be configured through the application settings and merchant channel setup flow. Do not place provider secrets, private keys, merchant signing material, or callback signing values in the repository.

## Systemd Service

Create `/etc/systemd/system/cpay.service`:

```ini
[Unit]
Description=CPay Core Payments Gateway Backend
After=network.target mysqld.service

[Service]
User=cpay
Group=cpay
WorkingDirectory=/opt/cpay
EnvironmentFile=/etc/cpay/.env
ExecStart=/usr/bin/java -jar /opt/cpay/bin/cito-fresh-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

Start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable cpay
sudo systemctl start cpay
sudo systemctl status cpay
```

View logs:

```bash
sudo journalctl -u cpay -f
```

## Frontend Deployment

Build the frontend from `clientside` and deploy `clientside/build` behind HTTPS. The frontend should be served from an origin listed in `CORS_ALLOWED_ORIGINS`.

For local testing, Vite runs on `http://localhost:3000` and proxies backend routes to `http://localhost:8081`. Production should use the public HTTPS origin and a reverse proxy/load balancer.

## Health and Smoke Checks

After deployment, verify:

```bash
curl -i https://cpay.coresynergi.es/status/health
curl -i https://cpay.coresynergi.es/auth/csrf
curl -i -u "$ACTUATOR_USERNAME:$ACTUATOR_PASSWORD" https://cpay.coresynergi.es/actuator/health
```

Then run functional smoke checks for:

- admin login and logout
- merchant login and logout
- merchant self-signup
- merchant payment-channel save, test, and submit
- v1 collect, payout, status, balance, and SMS endpoints
- v2 native collect and payout
- callback signing and callback requeue
- MTN, Airtel, Airtel OpenAPI, and Safaricom sandbox scenarios

## Production Controls

Before enabling live traffic, confirm:

- Flyway migrations have been tested against a staging database copy.
- `CUSTOM_GATEWAYSTATE=PRODUCTION`.
- `CUSTOM_SSL_SKIP_VERIFY=false`.
- `SPRINGDOC_API_DOCS_ENABLED=false`.
- `SPRINGDOC_SWAGGER_UI_ENABLED=false`.
- `CPAY_SECURITY_NONCE_STORE=jdbc` for clustered or multi-worker production deployments.
- `CORS_ALLOWED_ORIGINS` contains only approved HTTPS origins.
- Admin and actuator credentials are separate, strong, and stored outside source control.
- Provider production endpoint URLs and credentials are configured for the correct merchant/channel environment.
- Finance, provider, security, monitoring, and compliance signoffs are recorded.
