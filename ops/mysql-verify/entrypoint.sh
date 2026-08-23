#!/bin/bash
set -euo pipefail

DATADIR=/tmp/cpay-mysql-verify
HEALTH_ROOT=/tmp/cpay-mysql-health

rm -rf "$DATADIR" "$HEALTH_ROOT"
mkdir -p "$DATADIR" "$HEALTH_ROOT/status"
chmod 0777 "$DATADIR"

# Preserve the official MySQL initialization path. It creates the requested database/user from the
# MYSQL_* environment variables before execing the final server.
docker-entrypoint.sh mysqld \
  --datadir="$DATADIR" \
  --log-bin-trust-function-creators=1 \
  --bind-address=0.0.0.0 &
MYSQL_PID=$!

cleanup() {
  kill "$MYSQL_PID" 2>/dev/null || true
  if [[ -n "${HEALTH_PID:-}" ]]; then
    kill "$HEALTH_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 120); do
  if mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
    printf 'ok\n' > "$HEALTH_ROOT/status/health"
    python3 -m http.server "${PORT:-8080}" --bind 0.0.0.0 --directory "$HEALTH_ROOT" &
    HEALTH_PID=$!
    wait "$MYSQL_PID"
    exit $?
  fi

  if ! kill -0 "$MYSQL_PID" 2>/dev/null; then
    wait "$MYSQL_PID"
    exit $?
  fi
  sleep 1
done

echo "MySQL did not become ready within the verification startup window" >&2
exit 1
