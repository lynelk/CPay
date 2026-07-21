#!/usr/bin/env bash
set -euo pipefail

CPAY_REPO_URL="${CPAY_REPO_URL:-https://github.com/lynelk/CPay.git}"
CPAY_BRANCH="${CPAY_BRANCH:-frontend/ios-design-system}"
CPAY_ENVIRONMENT="${CPAY_ENVIRONMENT:-production}"
CPAY_APP_ROOT="${CPAY_APP_ROOT:-/opt/cpay}"
CPAY_DOMAIN="${CPAY_DOMAIN:-${CPAY_ENVIRONMENT}.cpay.coresynergi.es}"
CPAY_HTTP_PORT="${CPAY_HTTP_PORT:-80}"
CPAY_HTTPS_PORT="${CPAY_HTTPS_PORT:-443}"
CPAY_USE_CERTBOT="${CPAY_USE_CERTBOT:-false}"
CPAY_CERTBOT_EMAIL="${CPAY_CERTBOT_EMAIL:-}"
CPAY_CERTBOT_STAGING="${CPAY_CERTBOT_STAGING:-false}"
CPAY_SSL_CERT_FILE="${CPAY_SSL_CERT_FILE:-}"
CPAY_SSL_KEY_FILE="${CPAY_SSL_KEY_FILE:-}"
CPAY_SSL_REDIRECT="${CPAY_SSL_REDIRECT:-true}"

CPAY_USER="${CPAY_USER:-cpay}"
DEFAULT_DB_NAME="cpayadmin"
if [ "${CPAY_ENVIRONMENT}" != "production" ]; then
  DEFAULT_DB_NAME="cpayadmin_${CPAY_ENVIRONMENT}"
fi
CPAY_DB_NAME="${CPAY_DB_NAME:-${DEFAULT_DB_NAME}}"
CPAY_DB_USER="${CPAY_DB_USER:-cpay_user}"
CPAY_DB_PASSWORD="${CPAY_DB_PASSWORD:-}"
DEFAULT_BACKEND_PORT=8081
if [ "${CPAY_ENVIRONMENT}" != "production" ]; then
  DEFAULT_BACKEND_PORT=8082
fi
CPAY_BACKEND_PORT="${CPAY_BACKEND_PORT:-${DEFAULT_BACKEND_PORT}}"

APP_ROOT="${CPAY_APP_ROOT}/${CPAY_ENVIRONMENT}"
SRC_DIR="${APP_ROOT}/source"
BIN_DIR="${APP_ROOT}/bin"
WWW_DIR="${APP_ROOT}/www"
LOCK_DIR="/var/opt/cpay/locks/${CPAY_ENVIRONMENT}"
ENV_DIR="/etc/cpay"
ENV_FILE="${ENV_DIR}/${CPAY_ENVIRONMENT}.env"
SERVICE_NAME="cpay-${CPAY_ENVIRONMENT}"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
APACHE_FILE="/etc/httpd/conf.d/${SERVICE_NAME}.conf"
JAR_NAME="cito-fresh-0.0.1-SNAPSHOT.jar"

log() {
  printf '[cpay-deploy] %s\n' "$*"
}

require_root() {
  if [ "$(id -u)" -ne 0 ]; then
    echo "This deployment script must run as root." >&2
    exit 1
  fi
}

pkg_manager() {
  if command -v dnf >/dev/null 2>&1; then
    echo dnf
  elif command -v yum >/dev/null 2>&1; then
    echo yum
  else
    echo "No supported package manager found. Expected dnf or yum." >&2
    exit 1
  fi
}

ensure_java_21() {
  local java_bin java_major candidate_dir candidate_version path

  java_bin="$(command -v java || true)"
  if [ -n "${java_bin}" ]; then
    java_major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1 || echo 0)"
  else
    java_major="0"
  fi

  if [ "${java_major}" -ge 21 ]; then
    return 0
  fi

  candidate_dir=""
  for path in /usr/lib/jvm/*; do
    [ -x "${path}/bin/java" ] || continue
    candidate_version="$("${path}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1 || echo 0)"
    if [ "${candidate_version}" = "21" ]; then
      candidate_dir="${path}"
      break
    fi
  done

  if [ -n "${candidate_dir}" ]; then
    export JAVA_HOME="${candidate_dir}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    if command -v alternatives >/dev/null 2>&1; then
      alternatives --set java "${candidate_dir}/bin/java" >/dev/null 2>&1 || true
    fi
  fi

  java_major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1 || echo 0)"
  if [ "${java_major}" -lt 21 ]; then
    echo "Java 21 is required for the CPay Maven build, but the active java command on this host is still older than 21." >&2
    exit 1
  fi
}

install_packages() {
  local pm
  local node_major

  pm="$(pkg_manager)"
  "$pm" install -y git curl tar gzip openssl httpd mod_ssl maven rsync
  if [ "${CPAY_USE_CERTBOT}" = "true" ]; then
    "$pm" install -y certbot python3-certbot-apache
  fi
  if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "21'; then
    "$pm" install -y java-21-openjdk-devel
  fi

  node_major="0"
  if command -v node >/dev/null 2>&1; then
    node_major="$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || echo 0)"
  fi

  if [ "${node_major}" -lt 20 ]; then
    if ! command -v node >/dev/null 2>&1; then
      log "Installing Node.js 20 runtime from NodeSource"
      curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
    else
      log "Detected Node.js ${node_major}; attempting a conflict-tolerant install of the Node.js 20 runtime for the frontend build"
    fi

    if rpm -q npm >/dev/null 2>&1; then
      log "Removing AppStream npm package that conflicts with the NodeSource Node.js 20 package"
      "$pm" remove -y npm || true
    fi
    if rpm -q nodejs-full-i18n >/dev/null 2>&1; then
      log "Removing AppStream nodejs-full-i18n package that conflicts with the NodeSource Node.js 20 package"
      "$pm" remove -y nodejs-full-i18n || true
    fi

    "$pm" install --allowerasing -y nodejs
    node_major="$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || echo 0)"
    if [ "${node_major}" -lt 20 ]; then
      echo "Node.js 20+ is still not available after the package install step. Please configure the host with a compatible Node.js 20 runtime before retrying the deployment." >&2
      exit 1
    fi
  fi

  if ! command -v mysqld >/dev/null 2>&1; then
    "$pm" install -y mysql-server || "$pm" install -y mariadb-server
  fi

  ensure_java_21
}

enable_database() {
  local service_name=""

  if systemctl list-unit-files | grep -q '^mysqld\.service'; then
    service_name="mysqld"
  elif systemctl list-unit-files | grep -q '^mariadb\.service'; then
    service_name="mariadb"
  elif systemctl list-unit-files | grep -q '^mysql\.service'; then
    service_name="mysql"
  fi

  if [ -n "${service_name}" ]; then
    systemctl enable --now "${service_name}"
    return
  fi

  if command -v service >/dev/null 2>&1; then
    if service mysql status >/dev/null 2>&1; then
      service mysql start
      return
    fi
    if service mysqld status >/dev/null 2>&1; then
      service mysqld start
      return
    fi
    if service mariadb status >/dev/null 2>&1; then
      service mariadb start
      return
    fi
  fi

  echo "No supported MySQL/MariaDB service could be started after package installation." >&2
  exit 1
}

mysql_exec() {
  if [ -z "${DB_USERNAME:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    echo "The application database user credentials were not loaded from the environment file." >&2
    exit 1
  fi

  mysql -h localhost -u "${DB_USERNAME}" -p"${DB_PASSWORD}" "${CPAY_DB_NAME}" "$@"
}

ensure_env_file() {
  mkdir -p "${ENV_DIR}" "${LOCK_DIR}" "${BIN_DIR}" "${WWW_DIR}" "${SRC_DIR}"
  chmod 750 "${ENV_DIR}"
  chmod 755 "${LOCK_DIR}"

  local db_password actuator_password admin_password callback_secret channel_key
  db_password="${CPAY_DB_PASSWORD:-$(openssl rand -hex 24)}"
  actuator_password="$(openssl rand -hex 32)"
  admin_password="$(openssl rand -hex 32)"
  callback_secret="$(openssl rand -base64 48 | tr -d '\n')"
  channel_key="$(openssl rand -base64 48 | tr -d '\n')"

  cat > "${ENV_FILE}" <<EOF
HTTP_PORT=${CPAY_BACKEND_PORT}
DB_URL=jdbc:mysql://localhost:3306/${CPAY_DB_NAME}
DB_USERNAME=${CPAY_DB_USER}
DB_PASSWORD=$(quote_env_value "${db_password}")
APP_BASE_URL=https://${CPAY_DOMAIN}
CORS_ALLOWED_ORIGINS=https://${CPAY_DOMAIN},http://${CPAY_DOMAIN}
CUSTOM_LOCKFILEDIRECTORY=${LOCK_DIR}/
CUSTOM_GATEWAYSTATE=SANDBOX
CUSTOM_SSL_SKIP_VERIFY=false
CPAY_SECURITY_NONCE_STORE=jdbc
ACTUATOR_USERNAME=cpay-actuator
ACTUATOR_PASSWORD=$(quote_env_value "${actuator_password}")
ADMIN_API_USERNAME=cpay-admin-api
ADMIN_API_PASSWORD=$(quote_env_value "${admin_password}")
CALLBACK_SIGNING_SECRET=$(quote_env_value "${callback_secret}")
MERCHANT_CHANNEL_ENCRYPTION_KEY=$(quote_env_value "${channel_key}")
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
EOF
  chmod 640 "${ENV_FILE}"
}

load_env_file() {
  set -a
  . "${ENV_FILE}"
  set +a
}

env_value() {
  local key="$1"
  grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d= -f2-
}

quote_env_value() {
  local value="$1"
  value=${value//\'/\'"\'"\'}
  printf "'%s'" "${value}"
}

ensure_database() {
  log "Using existing application database '${CPAY_DB_NAME}' and database user '${CPAY_DB_USER}'"

  if ! mysql -h localhost -u "${DB_USERNAME}" -p"${DB_PASSWORD}" -e "USE \`${CPAY_DB_NAME}\`;" >/dev/null 2>&1; then
    echo "Unable to connect to the existing database '${CPAY_DB_NAME}' using the configured application credentials." >&2
    echo "Please confirm that the database and user were created on the target server before running the deployment script." >&2
    exit 1
  fi
}

normalize_legacy_sql_file() {
  local source_file="$1"
  local target_file="$2"

  sed -E \
    -e 's/ADD COLUMN IF NOT EXISTS /ADD COLUMN /g' \
    -e 's/CREATE INDEX IF NOT EXISTS /CREATE INDEX /g' \
    -e 's/DROP INDEX IF EXISTS /DROP INDEX /g' \
    -e 's/DROP TABLE IF EXISTS /DROP TABLE /g' \
    "${source_file}" > "${target_file}"
}

import_legacy_database_scripts() {
  local legacy_dir="${SRC_DIR}/clientside/db"
  local import_order=(
    "structure.sql"
    "initialize.sql"
    "cpayadmin.sql"
    "migration_2024.sql"
  )
  local tmp_sql_file=""

  log "Importing legacy database SQL before Flyway"
  for sql_file in "${import_order[@]}"; do
    if [ -f "${legacy_dir}/${sql_file}" ]; then
      log "Applying ${sql_file}"
      tmp_sql_file="$(mktemp)"
      normalize_legacy_sql_file "${legacy_dir}/${sql_file}" "${tmp_sql_file}"
      mysql -h localhost -u "${DB_USERNAME}" -p"${DB_PASSWORD}" "${CPAY_DB_NAME}" < "${tmp_sql_file}"
      rm -f "${tmp_sql_file}"
    else
      echo "Legacy SQL file not found: ${legacy_dir}/${sql_file}" >&2
      exit 1
    fi
  done
}

run_database_migrations() {
  log "Running Flyway migrations"
  pushd "${SRC_DIR}/InitializrSpringbootProjectFresh" >/dev/null
  mvn -DskipTests org.flywaydb:flyway-maven-plugin:migrate \
    -Dflyway.url="${DB_URL}" \
    -Dflyway.user="${DB_USERNAME}" \
    -Dflyway.password="${DB_PASSWORD}" \
    -Dflyway.locations="classpath:db/migration" \
    -Dflyway.baselineOnMigrate=true
  popd >/dev/null
}

ensure_user() {
  if ! id "${CPAY_USER}" >/dev/null 2>&1; then
    useradd --system --home-dir "${CPAY_APP_ROOT}" --shell /sbin/nologin "${CPAY_USER}"
  fi
  chown -R "${CPAY_USER}:${CPAY_USER}" "${CPAY_APP_ROOT}" "${LOCK_DIR}"
  chgrp "${CPAY_USER}" "${ENV_FILE}"
}

sync_source() {
  git config --global --add safe.directory "${SRC_DIR}"

  if [ ! -d "${SRC_DIR}/.git" ]; then
    rm -rf "${SRC_DIR}"
    git clone --branch "${CPAY_BRANCH}" "${CPAY_REPO_URL}" "${SRC_DIR}"
  else
    git -C "${SRC_DIR}" remote set-url origin "${CPAY_REPO_URL}"
    git -C "${SRC_DIR}" fetch --prune origin
    if git -C "${SRC_DIR}" rev-parse --verify "${CPAY_BRANCH}" >/dev/null 2>&1; then
      git -C "${SRC_DIR}" checkout "${CPAY_BRANCH}"
    else
      git -C "${SRC_DIR}" checkout -B "${CPAY_BRANCH}" "origin/${CPAY_BRANCH}"
    fi
    git -C "${SRC_DIR}" reset --hard "origin/${CPAY_BRANCH}"
    git -C "${SRC_DIR}" clean -fdx
  fi
  chown -R "${CPAY_USER}:${CPAY_USER}" "${SRC_DIR}"
}

build_app() {
  ensure_java_21
  export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  export PATH="${JAVA_HOME}/bin:${PATH}"

  log "Building frontend"
  pushd "${SRC_DIR}/clientside" >/dev/null
  npm ci
  npm run build
  rm -rf "${WWW_DIR:?}/"*
  rsync -a --delete build/ "${WWW_DIR}/"
  popd >/dev/null

  log "Building backend"
  pushd "${SRC_DIR}/InitializrSpringbootProjectFresh" >/dev/null
  mvn -DskipTests package
  install -m 640 "target/${JAR_NAME}" "${BIN_DIR}/cpay.jar"
  popd >/dev/null

  chown -R "${CPAY_USER}:${CPAY_USER}" "${BIN_DIR}" "${WWW_DIR}"
  # Grant read and execute permissions to all users (including httpd/apache) for web assets
  chmod -R 755 "${WWW_DIR}"
}

write_systemd_service() {
  cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=CPay backend (${CPAY_ENVIRONMENT})
After=network.target mysqld.service mariadb.service

[Service]
User=${CPAY_USER}
Group=${CPAY_USER}
WorkingDirectory=${APP_ROOT}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${BIN_DIR}/cpay.jar
Restart=always
RestartSec=10
SuccessExitStatus=143
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable "${SERVICE_NAME}"
}

enable_apache_proxy_modules() {
  local apache_module_conf="/etc/httpd/conf.modules.d/00-proxy.conf"
  mkdir -p "$(dirname "${apache_module_conf}")"

  cat > "${apache_module_conf}" <<EOF
LoadModule proxy_module modules/mod_proxy.so
LoadModule proxy_http_module modules/mod_proxy_http.so
LoadModule rewrite_module modules/mod_rewrite.so
LoadModule headers_module modules/mod_headers.so
LoadModule mime_module modules/mod_mime.so
EOF
}

write_apache_config() {
  cat > "${APACHE_FILE}" <<EOF
<VirtualHost *:${CPAY_HTTP_PORT}>
    ServerName ${CPAY_DOMAIN}
    DocumentRoot "${WWW_DIR}"
    DirectoryIndex index.html

    ErrorLog /var/log/httpd/${SERVICE_NAME}_error.log
    CustomLog /var/log/httpd/${SERVICE_NAME}_access.log combined

    <Directory "${WWW_DIR}">
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted

        AddType application/javascript .js
        AddType text/css .css
    </Directory>

    ProxyPreserveHost On

    ProxyPass /api http://127.0.0.1:${CPAY_BACKEND_PORT}/api
    ProxyPassReverse /api http://127.0.0.1:${CPAY_BACKEND_PORT}/api
    ProxyPass /auth http://127.0.0.1:${CPAY_BACKEND_PORT}/auth
    ProxyPassReverse /auth http://127.0.0.1:${CPAY_BACKEND_PORT}/auth
    ProxyPass /admins http://127.0.0.1:${CPAY_BACKEND_PORT}/admins
    ProxyPassReverse /admins http://127.0.0.1:${CPAY_BACKEND_PORT}/admins
    ProxyPass /audittrail http://127.0.0.1:${CPAY_BACKEND_PORT}/audittrail
    ProxyPassReverse /audittrail http://127.0.0.1:${CPAY_BACKEND_PORT}/audittrail
    ProxyPass /merchants http://127.0.0.1:${CPAY_BACKEND_PORT}/merchants
    ProxyPassReverse /merchants http://127.0.0.1:${CPAY_BACKEND_PORT}/merchants
    ProxyPass /settings http://127.0.0.1:${CPAY_BACKEND_PORT}/settings
    ProxyPassReverse /settings http://127.0.0.1:${CPAY_BACKEND_PORT}/settings
    ProxyPass /status http://127.0.0.1:${CPAY_BACKEND_PORT}/status
    ProxyPassReverse /status http://127.0.0.1:${CPAY_BACKEND_PORT}/status
    ProxyPass /transactions http://127.0.0.1:${CPAY_BACKEND_PORT}/transactions
    ProxyPassReverse /transactions http://127.0.0.1:${CPAY_BACKEND_PORT}/transactions
    ProxyPass /actuator http://127.0.0.1:${CPAY_BACKEND_PORT}/actuator
    ProxyPassReverse /actuator http://127.0.0.1:${CPAY_BACKEND_PORT}/actuator

    RewriteEngine On
    RewriteCond %{DOCUMENT_ROOT}%{REQUEST_FILENAME} -f [OR]
    RewriteCond %{DOCUMENT_ROOT}%{REQUEST_FILENAME} -d
    RewriteRule ^ - [L]

    RewriteCond %{REQUEST_URI} !^/(api|auth|admins|audittrail|merchants|settings|status|transactions|actuator)(/.*)?$
    RewriteRule ^ /index.html [L]

    RequestHeader set X-Forwarded-Proto "http"
    RequestHeader set X-Forwarded-Port "${CPAY_HTTP_PORT}"
</VirtualHost>
EOF

  if [ "${CPAY_USE_CERTBOT}" = "true" ]; then
    if [ -z "${CPAY_CERTBOT_EMAIL}" ]; then
      echo "CPAY_CERTBOT_EMAIL is required when CPAY_USE_CERTBOT=true." >&2
      exit 1
    fi

    local certbot_args=(--apache --non-interactive --agree-tos --email "${CPAY_CERTBOT_EMAIL}" -d "${CPAY_DOMAIN}")
    if [ "${CPAY_CERTBOT_STAGING}" = "true" ]; then
      certbot_args+=(--staging)
    fi

    if ! command -v certbot >/dev/null 2>&1; then
      echo "certbot is not available after package installation." >&2
      exit 1
    fi

    certbot "${certbot_args[@]}"
    CPAY_SSL_CERT_FILE="/etc/letsencrypt/live/${CPAY_DOMAIN}/fullchain.pem"
    CPAY_SSL_KEY_FILE="/etc/letsencrypt/live/${CPAY_DOMAIN}/privkey.pem"
  fi

  if [ -n "${CPAY_SSL_CERT_FILE}" ] && [ -n "${CPAY_SSL_KEY_FILE}" ]; then
    cat > "${APACHE_FILE}.ssl" <<EOF
<VirtualHost *:${CPAY_HTTPS_PORT}>
    ServerName ${CPAY_DOMAIN}
    DocumentRoot "${WWW_DIR}"
    DirectoryIndex index.html

    SSLEngine on
    SSLCertificateFile ${CPAY_SSL_CERT_FILE}
    SSLCertificateKeyFile ${CPAY_SSL_KEY_FILE}

    ErrorLog /var/log/httpd/${SERVICE_NAME}_ssl_error.log
    CustomLog /var/log/httpd/${SERVICE_NAME}_ssl_access.log combined

    <Directory "${WWW_DIR}">
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted

        AddType application/javascript .js
        AddType text/css .css
    </Directory>

    ProxyPreserveHost On

    ProxyPass /api http://127.0.0.1:${CPAY_BACKEND_PORT}/api
    ProxyPassReverse /api http://127.0.0.1:${CPAY_BACKEND_PORT}/api
    ProxyPass /auth http://127.0.0.1:${CPAY_BACKEND_PORT}/auth
    ProxyPassReverse /auth http://127.0.0.1:${CPAY_BACKEND_PORT}/auth
    ProxyPass /admins http://127.0.0.1:${CPAY_BACKEND_PORT}/admins
    ProxyPassReverse /admins http://127.0.0.1:${CPAY_BACKEND_PORT}/admins
    ProxyPass /audittrail http://127.0.0.1:${CPAY_BACKEND_PORT}/audittrail
    ProxyPassReverse /audittrail http://127.0.0.1:${CPAY_BACKEND_PORT}/audittrail
    ProxyPass /merchants http://127.0.0.1:${CPAY_BACKEND_PORT}/merchants
    ProxyPassReverse /merchants http://127.0.0.1:${CPAY_BACKEND_PORT}/merchants
    ProxyPass /settings http://127.0.0.1:${CPAY_BACKEND_PORT}/settings
    ProxyPassReverse /settings http://127.0.0.1:${CPAY_BACKEND_PORT}/settings
    ProxyPass /status http://127.0.0.1:${CPAY_BACKEND_PORT}/status
    ProxyPassReverse /status http://127.0.0.1:${CPAY_BACKEND_PORT}/status
    ProxyPass /transactions http://127.0.0.1:${CPAY_BACKEND_PORT}/transactions
    ProxyPassReverse /transactions http://127.0.0.1:${CPAY_BACKEND_PORT}/transactions
    ProxyPass /actuator http://127.0.0.1:${CPAY_BACKEND_PORT}/actuator
    ProxyPassReverse /actuator http://127.0.0.1:${CPAY_BACKEND_PORT}/actuator

    RewriteEngine On
    RewriteCond %{DOCUMENT_ROOT}%{REQUEST_FILENAME} -f [OR]
    RewriteCond %{DOCUMENT_ROOT}%{REQUEST_FILENAME} -d
    RewriteRule ^ - [L]

    RewriteCond %{REQUEST_URI} !^/(api|auth|admins|audittrail|merchants|settings|status|transactions|actuator)(/.*)?$
    RewriteRule ^ /index.html [L]

    RequestHeader set X-Forwarded-Proto "https"
    RequestHeader set X-Forwarded-Port "${CPAY_HTTPS_PORT}"
</VirtualHost>
EOF
  fi

  if [ "${CPAY_SSL_REDIRECT}" = "true" ] && [ -n "${CPAY_SSL_CERT_FILE}" ] && [ -n "${CPAY_SSL_KEY_FILE}" ]; then
    cat > "${APACHE_FILE}" <<EOF
<VirtualHost *:${CPAY_HTTP_PORT}>
    ServerName ${CPAY_DOMAIN}
    RewriteEngine On
    RewriteCond %{HTTPS} !=on
    RewriteRule ^/(.*)$ https://%{HTTP_HOST}/$1 [R=301,L]
</VirtualHost>
EOF
  fi

  apachectl -t
  systemctl enable httpd
}

seed_admin_if_missing() {
  local seed_hash
  seed_hash="e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7"
  mysql_exec "${CPAY_DB_NAME}" <<SQL
INSERT IGNORE INTO admins (name, email, phone, status, password)
VALUES ('Super Admin', 'svcs@coresynergi.es', '256701438948', 'ACTIVE', '${seed_hash}');
SET @super_id = (SELECT id FROM admins WHERE email='svcs@coresynergi.es');
INSERT IGNORE INTO admin_privileges (admin_id, privilege) VALUES
  (@super_id, 'ACCESS_ADMIN'),
  (@super_id, 'CREATE_ADMIN'),
  (@super_id, 'UPDATE_ADMIN'),
  (@super_id, 'DELETE_ADMIN'),
  (@super_id, 'ACCESS_AUDITTRAIL'),
  (@super_id, 'ACCESS_TRANSACTION_LOG'),
  (@super_id, 'ACCESS_SMS_LOG'),
  (@super_id, 'CREATE_MERCHANT'),
  (@super_id, 'UPDATE_MERCHANT'),
  (@super_id, 'DELETE_MERCHANT'),
  (@super_id, 'CREDIT_MERCHANT'),
  (@super_id, 'SEND_SMS'),
  (@super_id, 'CREATE_BATCH_TX'),
  (@super_id, 'RESOLVE_TRANSACTIONS');
SQL
}

restart_and_verify() {
  systemctl restart "${SERVICE_NAME}"
  systemctl restart httpd
  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${CPAY_BACKEND_PORT}/status/health" >/dev/null 2>&1; then
      seed_admin_if_missing
      curl -fsS "http://127.0.0.1:${CPAY_BACKEND_PORT}/status/health"
      return 0
    fi
    sleep 2
  done
  journalctl -u "${SERVICE_NAME}" -n 120 --no-pager || true
  echo "CPay backend did not become healthy." >&2
  exit 1
}

main() {
  require_root
  log "Deploying environment: ${CPAY_ENVIRONMENT}"
  log "Installing packages"
  install_packages
  log "Starting database"
  enable_database
  log "Preparing runtime configuration"
  ensure_env_file
  load_env_file
  ensure_database
  ensure_user
  log "Pulling ${CPAY_REPO_URL} branch ${CPAY_BRANCH}"
  sync_source
  build_app
  import_legacy_database_scripts
  run_database_migrations
  write_systemd_service
  enable_apache_proxy_modules
  write_apache_config
  restart_and_verify
  log "Deployment complete for ${CPAY_DOMAIN} (${CPAY_ENVIRONMENT})"
}

main "$@"