#!/bin/sh
# Railpack/Railway start command for the CPay Spring Boot backend.
#
# railway.json's buildCommand runs `mvn -DskipTests clean package` from the repo
# root (via the aggregator pom.xml) and copies the boot jar into ./target so
# Railpack's Java provider finds its expected output at /app/target.
# This script then locates the built fat jar and execs it as PID 1 so the
# container receives SIGTERM directly for graceful shutdown
# (server.shutdown=graceful, SHUTDOWN_PHASE_TIMEOUT).
#
# Required environment variables (set in the Railway service):
#   DB_URL, DB_USERNAME, DB_PASSWORD
#   ACTUATOR_USERNAME, ACTUATOR_PASSWORD
#   ADMIN_API_USERNAME, ADMIN_API_PASSWORD
#   CALLBACK_SIGNING_SECRET, MERCHANT_CHANNEL_ENCRYPTION_KEY
#
# Optional:
#   PORT            - Railway injects this; mapped to HTTP_PORT below.
#   HTTP_PORT       - direct override; wins over PORT when set.
#   JAVA_OPTS       - extra JVM args appended to the defaults.

set -eu

APP_DIR="InitializrSpringbootProjectFresh"

# --- Locate the built jar ---------------------------------------------------
# The railway.json buildCommand runs Maven from the repo root and copies the
# boot jar into ./target so Railpack's Java provider finds its expected output
# at /app/target. Fall back to the module's own target/ for local runs.
JAR_FILE="target/cito-fresh-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE=$(ls target/*.jar 2>/dev/null | grep -v '\.original\.' | head -n 1 || true)
fi
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    JAR_FILE="${APP_DIR}/target/cito-fresh-0.0.1-SNAPSHOT.jar"
fi
if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE=$(ls "${APP_DIR}"/target/*.jar 2>/dev/null | grep -v '\.original\.' | head -n 1 || true)
fi
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "FATAL: no built jar found in ./target/ or ${APP_DIR}/target/." >&2
    echo "Expected cito-fresh-0.0.1-SNAPSHOT.jar. Did the Railpack build step run?" >&2
    exit 1
fi
echo "Starting CPay backend: $JAR_FILE"

# --- Port mapping -----------------------------------------------------------
# The app reads server.port from HTTP_PORT (default 8081). Railway exposes the
# public port via PORT; honor an explicit HTTP_PORT first, else map PORT.
if [ -z "$HTTP_PORT" ] && [ -n "$PORT" ]; then
    export HTTP_PORT="$PORT"
fi

# --- Lockfile directory -----------------------------------------------------
# custom.lockfiledirectory defaults to ./tmp; ensure it exists so cron jobs that
# take file locks don't fail on a read-only or missing path.
mkdir -p tmp

# --- JVM options ------------------------------------------------------------
# Container-aware heap sizing (matches Dockerfile.nginx-era runtime image):
# G1GC with heap capped at 50% of container RAM so metaspace/threads have room.
DEFAULT_JAVA_OPTS="-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxRAMPercentage=50 -XX:MinRAMPercentage=25"
JAVA_OPTS="${JAVA_OPTS:-$DEFAULT_JAVA_OPTS}"

echo "HTTP_PORT=${HTTP_PORT:-8081}"
exec java $JAVA_OPTS -jar "$JAR_FILE"
