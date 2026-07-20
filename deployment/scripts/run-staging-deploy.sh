#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy-server.sh"

export CPAY_ENVIRONMENT="change_me"
export CPAY_DOMAIN="change_me"
export CPAY_DB_NAME="change_me"
export CPAY_DB_USER="change_me"
export CPAY_DB_PASSWORD='change_me'
export CPAY_BACKEND_PORT="change_me"
export CPAY_HTTP_PORT="80"
export CPAY_HTTPS_PORT="443"
export CPAY_USE_CERTBOT="true"
export CPAY_CERTBOT_EMAIL="change_me"
export CPAY_CERTBOT_STAGING="false"
export CPAY_SSL_REDIRECT="true"

if [ ! -f "${DEPLOY_SCRIPT}" ]; then
  echo "Deployment script not found: ${DEPLOY_SCRIPT}" >&2
  exit 1
fi

exec sudo env \
  CPAY_ENVIRONMENT="${CPAY_ENVIRONMENT}" \
  CPAY_DOMAIN="${CPAY_DOMAIN}" \
  CPAY_DB_NAME="${CPAY_DB_NAME}" \
  CPAY_DB_USER="${CPAY_DB_USER}" \
  CPAY_DB_PASSWORD="${CPAY_DB_PASSWORD}" \
  CPAY_BACKEND_PORT="${CPAY_BACKEND_PORT}" \
  CPAY_HTTP_PORT="${CPAY_HTTP_PORT}" \
  CPAY_HTTPS_PORT="${CPAY_HTTPS_PORT}" \
  CPAY_USE_CERTBOT="${CPAY_USE_CERTBOT}" \
  CPAY_CERTBOT_EMAIL="${CPAY_CERTBOT_EMAIL}" \
  CPAY_CERTBOT_STAGING="${CPAY_CERTBOT_STAGING}" \
  CPAY_SSL_REDIRECT="${CPAY_SSL_REDIRECT}" \
  bash "${DEPLOY_SCRIPT}"
