#!/usr/bin/env bash
set -euo pipefail

CPAY_REPO_URL="${CPAY_REPO_URL:-https://github.com/lynelk/CPay.git}"
CPAY_BRANCH="${CPAY_BRANCH:-frontend/ios-design-system}"
CPAY_APP_ROOT="${CPAY_APP_ROOT:-/opt/cpay}"
CPAY_DOMAIN="${CPAY_DOMAIN:-cpay.coresynergi.es}"

CPAY_USER="${CPAY_USER:-cpay}"
CPAY_DB_NAME="${CPAY_DB_NAME:-cpayadmin}"
CPAY_DB_USER="${CPAY_DB_USER:-cpay_user}"
CPAY_BACKEND_PORT="${CPAY_BACKEND_PORT:-8081}"

SRC_DIR="${CPAY_APP_ROOT}/source"
BIN_DIR="${CPAY_APP_ROOT}/bin"
WWW_DIR="${CPAY_APP_ROOT}/www"
LOCK_DIR="/var/opt/cpay/locks"
ENV_DIR="/etc/cpay"
ENV_FILE="${ENV_DIR}/.env"
SERVICE_FILE="/etc/systemd/system/cpay.service"
NGINX_FILE="/etc/nginx/conf.d/cpay.conf"
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

install_packages() {
  local pm
  pm="$(pkg_manager)"
  "$pm" install -y git curl tar gzip openssl nginx maven rsync
  if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "21'; then
    "$pm" install -y java-21-openjdk-devel
  fi
  if ! command -v node >/dev/null 2>&1 || [ "$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || echo 0)" -lt 20 ]; then
    curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
    "$pm" install -y nodejs
  fi
  if ! command -v mysql >/dev/null 2>&1; then
    "$pm" install -y mysql-server || "$pm" install -y mariadb-server
  fi
}

enable_database() {
  if systemctl list-unit-files | grep -q '^mysqld\.service'; then
    systemctl enable --now mysqld
  elif systemctl list-unit-files | grep -q '^mariadb\.service'; then
    systemctl enable --now mariadb
  else
    echo "Neither mysqld nor mariadb systemd service is available after package installation." >&2
    exit 1
  fi
}

mysql_exec() {
  if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
    MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql -uroot "$@"
  else
    mysql -uroot "$@"
  fi
}

ensure_env_file() {
  mkdir -p "${ENV_DIR}" "${LOCK_DIR}" "${BIN_DIR}" "${WWW_DIR}" "${SRC_DIR}"
  chmod 750 "${ENV_DIR}"
  chmod 755 "${LOCK_DIR}"

  if [ ! -f "${ENV_FILE}" ]; then
    local db_password actuator_password admin_password callback_secret channel_key
    db_password="$(openssl rand -hex 24)"
    actuator_password="$(openssl rand -hex 32)"
    admin_password="$(openssl rand -hex 32)"
    callback_secret="$(openssl rand -base64 48 | tr -d '\n')"
    channel_key="$(openssl rand -base64 48 | tr -d '\n')"
    cat > "${ENV_FILE}" <<EOF
HTTP_PORT=${CPAY_BACKEND_PORT}
DB_URL=jdbc:mysql://localhost:3306/${CPAY_DB_NAME}
DB_USERNAME=${CPAY_DB_USER}
DB_PASSWORD=${db_password}
APP_BASE_URL=https://${CPAY_DOMAIN}
CORS_ALLOWED_ORIGINS=https://${CPAY_DOMAIN},http://${CPAY_DOMAIN}
CUSTOM_LOCKFILEDIRECTORY=${LOCK_DIR}/
CUSTOM_GATEWAYSTATE=SANDBOX
CUSTOM_SSL_SKIP_VERIFY=false
CPAY_SECURITY_NONCE_STORE=jdbc
ACTUATOR_USERNAME=cpay-actuator
ACTUATOR_PASSWORD=${actuator_password}
ADMIN_API_USERNAME=cpay-admin-api
ADMIN_API_PASSWORD=${admin_password}
CALLBACK_SIGNING_SECRET=${callback_secret}
MERCHANT_CHANNEL_ENCRYPTION_KEY=${channel_key}
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
EOF
    chmod 640 "${ENV_FILE}"
  fi
}

env_value() {
  local key="$1"
  grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d= -f2-
}

ensure_database() {
  local db_password
  db_password="$(env_value DB_PASSWORD)"
  mysql_exec <<SQL
CREATE DATABASE IF NOT EXISTS \`${CPAY_DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${CPAY_DB_USER}'@'localhost' IDENTIFIED BY '${db_password}';
ALTER USER '${CPAY_DB_USER}'@'localhost' IDENTIFIED BY '${db_password}';
GRANT ALL PRIVILEGES ON \`${CPAY_DB_NAME}\`.* TO '${CPAY_DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
}

ensure_user() {
  if ! id "${CPAY_USER}" >/dev/null 2>&1; then
    useradd --system --home-dir "${CPAY_APP_ROOT}" --shell /sbin/nologin "${CPAY_USER}"
  fi
  chown -R "${CPAY_USER}:${CPAY_USER}" "${CPAY_APP_ROOT}" "${LOCK_DIR}"
  chgrp "${CPAY_USER}" "${ENV_FILE}"
}

sync_source() {
  if [ ! -d "${SRC_DIR}/.git" ]; then
    rm -rf "${SRC_DIR}"
    git clone --branch "${CPAY_BRANCH}" "${CPAY_REPO_URL}" "${SRC_DIR}"
  else
    git -C "${SRC_DIR}" remote set-url origin "${CPAY_REPO_URL}"
    git -C "${SRC_DIR}" fetch --prune origin
    git -C "${SRC_DIR}" checkout "${CPAY_BRANCH}"
    git -C "${SRC_DIR}" reset --hard "origin/${CPAY_BRANCH}"
  fi
  chown -R "${CPAY_USER}:${CPAY_USER}" "${SRC_DIR}"
}

build_app() {
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
}

write_systemd_service() {
  cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=CPay backend
After=network.target mysqld.service mariadb.service

[Service]
User=${CPAY_USER}
Group=${CPAY_USER}
WorkingDirectory=${CPAY_APP_ROOT}
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
  systemctl enable cpay
}

write_nginx_config() {
  cat > "${NGINX_FILE}" <<EOF
server {
    listen 80;
    server_name ${CPAY_DOMAIN};
    root ${WWW_DIR};
    index index.html;

    client_max_body_size 25m;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location ~ ^/(api|auth|admins|audittrail|merchants|settings|status|transactions|actuator)(/|\$) {
        proxy_pass http://127.0.0.1:${CPAY_BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
  nginx -t
  systemctl enable nginx
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
  systemctl restart cpay
  systemctl restart nginx
  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${CPAY_BACKEND_PORT}/status/health" >/dev/null 2>&1; then
      seed_admin_if_missing
      curl -fsS "http://127.0.0.1:${CPAY_BACKEND_PORT}/status/health"
      return 0
    fi
    sleep 2
  done
  journalctl -u cpay -n 120 --no-pager || true
  echo "CPay backend did not become healthy." >&2
  exit 1
}

main() {
  require_root
  log "Installing packages"
  install_packages
  log "Starting database"
  enable_database
  log "Preparing runtime configuration"
  ensure_env_file
  ensure_database
  ensure_user
  log "Pulling ${CPAY_REPO_URL} branch ${CPAY_BRANCH}"
  sync_source
  build_app
  write_systemd_service
  write_nginx_config
  restart_and_verify
  log "Deployment complete for https://${CPAY_DOMAIN}"
}

main "$@"
