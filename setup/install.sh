#!/bin/bash
set -euo pipefail

APP_DIR="${CPAY_APP_DIR:-/opt/cpay}"
BIN_DIR="$APP_DIR/bin"
FRONTEND_DIR="$APP_DIR/frontend"
LOCK_DIR="${CPAY_LOCK_DIR:-/var/opt/cpay/locks}"
LOG_DIR="${CPAY_LOG_DIR:-/var/log/cpay}"
INIT_DIR="${CPAY_INIT_DIR:-/etc/init.d/cpay}"
BACKEND_JAR="../InitializrSpringbootProjectFresh/target/cito-fresh-0.0.1-SNAPSHOT.jar"
FRONTEND_BUILD="../clientside/build"

mkdir -p "$BIN_DIR" "$FRONTEND_DIR" "$LOCK_DIR" "$LOG_DIR" "$INIT_DIR"

cp start.sh "$INIT_DIR/start.sh"
cp shutdown.sh "$INIT_DIR/shutdown.sh"
cp restart.sh "$INIT_DIR/restart.sh"
chmod +x "$INIT_DIR/start.sh" "$INIT_DIR/shutdown.sh" "$INIT_DIR/restart.sh"

if [ ! -f "$BACKEND_JAR" ]; then
  echo "Backend jar not found at $BACKEND_JAR. Run: cd InitializrSpringbootProjectFresh && mvn clean package"
  exit 1
fi

cp "$BACKEND_JAR" "$BIN_DIR/cito-fresh-0.0.1-SNAPSHOT.jar"

if [ -d "$FRONTEND_BUILD" ]; then
  cp -R -f "$FRONTEND_BUILD/." "$FRONTEND_DIR/"
else
  echo "Frontend build not found at $FRONTEND_BUILD. Run: cd clientside && npm install && npm run build"
fi

echo "CPay files installed under $APP_DIR."
