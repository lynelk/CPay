## CPay — Core Payments Service Engine

CPay is the core payments orchestration service.
[CitoConnect](https://github.com/lynelk/citoconnect) integrates CPay as
its **Core Payments Service Engine**, which means every payment
integration and channel inside CitoConnect — MTN MoMo, Airtel Money,
Safaricom M-Pesa, Yo! Payments, Stripe, Flutterwave, Pesapal — is
dispatched through CPay's `/api/v1` REST surface or through CPay-owned
Adapter modules. See `docs/citoconnect-integration.md` for the
integration contract and the canonical request/response shapes.

## Available Scripts
Prerequisite
1. Java JDK Version >= 8
3. MySQL

Installation
1. cd to clientside
2. Create and import the data at clientside/db/structure.sql
3. Import currently applied db changes: clientside/db/db_changes.sql
4. Import initial admin user at clientside/db/initialize.sql
5. Initial Username & Password are joseph.tabajjwa@gmail.com : @cpayadmin@domain
6. cd to the setup directory
7. run ./install.sh

| Network | Country | Capabilities |
|---|---|---|
| MTN MoMo | Uganda | Collections, Disbursements, Balance |
| Airtel Money | Uganda / Kenya | Collections, Disbursements (Legacy + OpenAPI) |
| Safaricom M-Pesa | Kenya | STK Push, B2C, Balance |

---

Stop the servers
1. Run: /etc/init.d/cpayadmin/shutdown.sh | /home/centos/cpay/setup/shutdown.sh

Ports
1. Java: 443

## Prerequisites

Compiling React App and Java 
1. cd into ../clientside directory.
2. Run the command: npm run build.
2.1. Copy the following to the head section of the ../clientside/build/index.html

## Environment Variables

Copy `.env.example` to `.env` and fill in all values before starting the application. **Never commit `.env` to version control.**

### Required

2.3. Include the following content in the body tag of ../clientside/build/index.html
onload="onLoad();"

> The application **will not start** if `ACTUATOR_USERNAME` or `ACTUATOR_PASSWORD` are missing. There are no built-in defaults.

### Recommended

| Variable | Description | Default |
|---|---|---|
| `GATEWAY_STATE` | `SANDBOX` or `PRODUCTION` | `SANDBOX` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins for the admin/merchant portals | `http://localhost:3000` |
| `APP_BASE_URL` | Base URL used in password-reset emails (no trailing slash) | `http://localhost:9000` |
| `HTTP_PORT` | Port the application listens on | `9000` |
| `LOCK_FILE_DIR` | Directory for scheduler lock files | `/tmp/cpay/locks/` |

Use the Following Link to the general PKCS12 version of the SSL CERTIFICATE
https://dzone.com/articles/spring-boot-secured-by-lets-encrypt

| Variable | Description |
|---|---|
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |

INSTALLING AND RENEWING CERTIFICATES
1. Log in to the Lightsail server.
2. Stop any service running on Port 80.
3. RUN: sudo certbot renew | sudo certbot certonly -a standalone -d cpaytest.citotech.net
4. Convert the updated certificate to PKCS12: 

To enable HTTPS, uncomment the four SSL lines in `application.properties` and set:

| Variable | Description |
|---|---|
| `SSL_KEY_STORE` | Path to PKCS12 keystore |
| `SSL_KEY_STORE_PASSWORD` | Keystore password |
| `SSL_KEY_ALIAS` | Certificate alias |

See [Spring Boot SSL docs](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.server) and the Let's Encrypt section below.

---

## Database Setup

```bash
# 1. Create the schema and tables
mysql -u root -p < clientside/db/structure.sql

Copy the new version to Cpay Server
scp -i /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/newcpay/new_cpay.pem /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar centos@18.190.63.205:/home/centos/cpay/setup/

scp -i /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/newcpay/new_cpay.pem /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar centos@18.190.63.205:/home/centos/kwiff/setup/

```bash
# Clone and enter the repo
git clone <repo-url>
cd CPay

Compiling with Maven
Run the command: maven package
It will package a JAR file for you.

```bash
cd clientside
npm install
npm run build
```

- ssh -i LightsailDefaultPrivateKey-eu-central-1.pem ubuntu@18.196.18.46 -R 8080:localhost:9000
