#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# Adjust environment defaults or override them via exported environment vars.
# -----------------------------------------------------------------------------
export CPAY_ENVIRONMENT="${CPAY_ENVIRONMENT:-staging}"
export CPAY_REPO_URL="${CPAY_REPO_URL:-https://github.com/lynelk/CPay.git}"
export CPAY_BRANCH="${CPAY_BRANCH:-frontend/ios-design-system}"
export CPAY_APP_ROOT="${CPAY_APP_ROOT:-/opt/cpay}"
export CPAY_USER="${CPAY_USER:-cpay}"

DEFAULT_BACKEND_PORT=8081
if [ "${CPAY_ENVIRONMENT}" != "production" ]; then
  DEFAULT_BACKEND_PORT=8082
fi
export CPAY_BACKEND_PORT="${CPAY_BACKEND_PORT:-${DEFAULT_BACKEND_PORT}}"

APP_ROOT="${CPAY_APP_ROOT}/${CPAY_ENVIRONMENT}"
SRC_DIR="${APP_ROOT}/source"
BIN_DIR="${APP_ROOT}/bin"
WWW_DIR="${APP_ROOT}/www"
ENV_FILE="/etc/cpay/${CPAY_ENVIRONMENT}.env"
SERVICE_NAME="cpay-${CPAY_ENVIRONMENT}"
JAR_NAME="cito-fresh-0.0.1-SNAPSHOT.jar"

log() {
  printf '[cpay-update] %s\n' "$*"
}

require_root() {
  if [ "$(id -u)" -ne 0 ]; then
    echo "Error: Update deployment script must be run as root or with sudo." >&2
    exit 1
  fi
}

ensure_java_21() {
  local java_bin candidate_dir candidate_version path java_major
  
  java_bin="$(command -v java || true)"
  if [ -n "${java_bin}" ]; then
    java_major="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1 || echo 0)"
  else
    java_major="0"
  fi

  if [ "${java_major}" -ge 21 ]; then
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    export PATH="${JAVA_HOME}/bin:${PATH}"
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
  else
    echo "Error: Java 21 runtime not found." >&2
    exit 1
  fi
}

load_environment() {
  if [ ! -f "${ENV_FILE}" ]; then
    echo "Error: Environment configuration file not found at ${ENV_FILE}" >&2
    echo "Please run the main server setup script first." >&2
    exit 1
  fi
  set -a
  . "${ENV_FILE}"
  set +a
}

sync_latest_code() {
  log "Fetching latest changes from ${CPAY_REPO_URL} (${CPAY_BRANCH})"
  git config --global --add safe.directory "${SRC_DIR}"

  if [ ! -d "${SRC_DIR}/.git" ]; then
    echo "Error: Source git repository missing at ${SRC_DIR}" >&2
    exit 1
  fi

  git -C "${SRC_DIR}" remote set-url origin "${CPAY_REPO_URL}"
  git -C "${SRC_DIR}" fetch --prune origin
  
  if git -C "${SRC_DIR}" rev-parse --verify "${CPAY_BRANCH}" >/dev/null 2>&1; then
    git -C "${SRC_DIR}" checkout "${CPAY_BRANCH}"
  else
    git -C "${SRC_DIR}" checkout -B "${CPAY_BRANCH}" "origin/${CPAY_BRANCH}"
  fi
  
  git -C "${SRC_DIR}" reset --hard "origin/${CPAY_BRANCH}"
  git -C "${SRC_DIR}" clean -fdx
  chown -R "${CPAY_USER}:${CPAY_USER}" "${SRC_DIR}"
}

build_and_stage_assets() {
  ensure_java_21

  log "Building React frontend..."
  pushd "${SRC_DIR}/clientside" >/dev/null
  npm ci
  npm run build
  rsync -a --delete build/ "${WWW_DIR}/"
  popd >/dev/null

  log "Building Spring Boot backend..."
  pushd "${SRC_DIR}/InitializrSpringbootProjectFresh" >/dev/null
  mvn -DskipTests package
  install -m 640 "target/${JAR_NAME}" "${BIN_DIR}/cpay.jar"
  popd >/dev/null

  chown -R "${CPAY_USER}:${CPAY_USER}" "${BIN_DIR}" "${WWW_DIR}"
  chmod -R 755 "${WWW_DIR}"
}

run_migrations() {
  log "Executing Flyway migrations..."
  pushd "${SRC_DIR}/InitializrSpringbootProjectFresh" >/dev/null
  mvn -DskipTests org.flywaydb:flyway-maven-plugin:migrate \
    -Dflyway.url="${DB_URL}" \
    -Dflyway.user="${DB_USERNAME}" \
    -Dflyway.password="${DB_PASSWORD}" \
    -Dflyway.locations="classpath:db/migration" \
    -Dflyway.baselineOnMigrate=true
  popd >/dev/null
}

restart_and_healthcheck() {
  log "Restarting application service (${SERVICE_NAME})..."
  systemctl restart "${SERVICE_NAME}"
  
  log "Waiting for backend health check on port ${CPAY_BACKEND_PORT}..."
  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${CPAY_BACKEND_PORT}/status/health" >/dev/null 2>&1; then
      log "Backend is healthy!"
      return 0
    fi
    sleep 2
  done

  echo "Error: Backend failed to report healthy status within 120 seconds." >&2
  journalctl -u "${SERVICE_NAME}" -n 100 --no-pager || true
  exit 1
}

main() {
  require_root
  log "Starting update deployment for environment: ${CPAY_ENVIRONMENT}"
  load_environment
  sync_latest_code
  build_and_stage_assets
  run_migrations
  restart_and_healthcheck
  log "Successfully updated ${CPAY_ENVIRONMENT}!"
}

main "$@"