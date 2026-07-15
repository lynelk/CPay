#!/bin/bash
set -euo pipefail

PID_FILE="${CPAY_PID_FILE:-/var/run/cpay.pid}"

if [ ! -f "$PID_FILE" ]; then
  echo "CPay pid file not found; nothing to stop."
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Stopped CPay backend with pid $PID."
else
  echo "CPay process $PID is not running."
fi

rm -f "$PID_FILE"
