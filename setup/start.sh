#!/bin/bash
set -euo pipefail

APP_DIR="${CPAY_APP_DIR:-/opt/cpay}"
JAR_FILE="${CPAY_JAR_FILE:-$APP_DIR/bin/cito-fresh-0.0.1-SNAPSHOT.jar}"
LOG_DIR="${CPAY_LOG_DIR:-/var/log/cpay}"
PID_FILE="${CPAY_PID_FILE:-/var/run/cpay.pid}"
ENV_FILE="${CPAY_ENV_FILE:-/etc/cpay/.env}"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "CPay is already running with pid $(cat "$PID_FILE")."
  exit 0
fi

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

nohup java -jar "$JAR_FILE" > "$LOG_DIR/backend.log" 2>&1 &
echo $! > "$PID_FILE"
echo "Started CPay backend with pid $(cat "$PID_FILE")."
