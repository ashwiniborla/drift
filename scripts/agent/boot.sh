#!/usr/bin/env bash
# boot.sh — Start Drift API or Worker as a native JVM process (app-runtime: local)
# Usage:
#   APP=api bash scripts/agent/boot.sh
#   APP=worker bash scripts/agent/boot.sh

set -euo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"
LOG_DIR="/tmp/drift-logs"
PIDS_DIR="/tmp/drift-pids"

APP="${APP:-}"

# ─── Step 0: Pre-flight checks ────────────────────────────────────────────────
bash "$REPO_ROOT/scripts/agent/check-prereq.sh"

# ─── Validate APP ─────────────────────────────────────────────────────────────
if [[ "$APP" != "api" && "$APP" != "worker" ]]; then
  echo "Usage: APP=api bash scripts/agent/boot.sh"
  echo "       APP=worker bash scripts/agent/boot.sh"
  exit 1
fi

# ─── Load environment variables ───────────────────────────────────────────────
if [ -f "$REPO_ROOT/.env" ]; then
  echo "[boot] Loading environment from $REPO_ROOT/.env"
  set -o allexport
  # shellcheck source=/dev/null
  source "$REPO_ROOT/.env"
  set +o allexport
else
  echo "[warn] No .env file found at $REPO_ROOT/.env"
  echo "       Copy .env.example to .env and set required variables."
fi

# ─── Setup directories ────────────────────────────────────────────────────────
mkdir -p "$LOG_DIR" "$PIDS_DIR"

# ─── Service-specific config ──────────────────────────────────────────────────
if [ "$APP" = "api" ]; then
  MODULE="api"
  MAIN_CLASS=""  # uses fat JAR with manifest main
  CONFIG_FILE="$REPO_ROOT/api/src/main/resources/config/configuration.yaml"
  JAR_PATTERN="$REPO_ROOT/api/target/*-shaded.jar"
  APP_PORT=8000
  ADMIN_PORT=8001
  JVM_XMS="${JVM_XMS:-256m}"
  JVM_XMX="${JVM_XMX:-1g}"
  JVM_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
            --add-opens java.base/java.util=ALL-UNNAMED \
            -Djava.net.preferIPv4Stack=true \
            -Dfile.encoding=UTF-8 \
            -XX:-OmitStackTraceInFastThrow"
  LOG_FILE="$LOG_DIR/api.log"
  PID_FILE="$PIDS_DIR/api.pid"
else
  MODULE="worker"
  MAIN_CLASS="com.flipkart.drift.worker.bootstrap.WorkerApplication"
  CONFIG_FILE="$REPO_ROOT/worker/src/main/resources/config/configuration.yaml"
  JAR_PATTERN="$REPO_ROOT/worker/target/*-shaded.jar"
  APP_PORT=7200
  ADMIN_PORT=7201
  JVM_XMS="${JVM_XMS:-256m}"
  JVM_XMX="${JVM_XMX:-1g}"
  JVM_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
            -Dgroovy.use.classvalue=true \
            -Djava.net.preferIPv4Stack=true \
            -Dfile.encoding=UTF-8 \
            -XX:-OmitStackTraceInFastThrow"
  LOG_FILE="$LOG_DIR/worker.log"
  PID_FILE="$PIDS_DIR/worker.pid"
fi

# ─── Kill existing process if running ─────────────────────────────────────────
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "[boot] Stopping existing $APP process (PID $OLD_PID)..."
    kill "$OLD_PID" 2>/dev/null || true
    sleep 2
  fi
  rm -f "$PID_FILE"
fi

# ─── Check port is free ───────────────────────────────────────────────────────
if lsof -Pi ":$APP_PORT" -sTCP:LISTEN -t &>/dev/null; then
  echo "[error] Port $APP_PORT is already in use. Stop the existing process first." >&2
  exit 1
fi

# ─── Find JAR ────────────────────────────────────────────────────────────────
JAR_FILE=""
for f in $JAR_PATTERN; do
  # Skip *-tests.jar and *-sources.jar
  if [[ "$f" != *"-tests.jar" && "$f" != *"-sources.jar" && -f "$f" ]]; then
    JAR_FILE="$f"
    break
  fi
done

if [ -z "$JAR_FILE" ]; then
  echo "[boot] JAR not found at $JAR_PATTERN. Building $MODULE..."
  cd "$REPO_ROOT"
  mvn clean package -pl "$MODULE" -am -DskipTests -Dgpg.skip=true \
    -Dattach.sources.skip=true -Dattach.javadoc.skip=true
  for f in $JAR_PATTERN; do
    if [[ "$f" != *"-tests.jar" && "$f" != *"-sources.jar" && -f "$f" ]]; then
      JAR_FILE="$f"
      break
    fi
  done
fi

if [ -z "$JAR_FILE" ]; then
  echo "[error] Could not find or build JAR for $APP" >&2
  exit 1
fi

echo "[boot] Using JAR: $JAR_FILE"

# ─── Start the service ────────────────────────────────────────────────────────
echo "[boot] Starting $APP on port $APP_PORT (admin: $ADMIN_PORT)..."
echo "[boot] Logs: $LOG_FILE"

if [ "$APP" = "api" ]; then
  # API uses executable fat JAR with manifest main class
  # shellcheck disable=SC2086
  java $JVM_OPTS \
    -Xms"$JVM_XMS" -Xmx"$JVM_XMX" \
    -server \
    -jar "$JAR_FILE" \
    server "$CONFIG_FILE" \
    > "$LOG_FILE" 2>&1 &
else
  # Worker uses -cp with optional extensions directory first
  CLASSPATH="$JAR_FILE"
  if [ -d "$REPO_ROOT/extensions" ] && ls "$REPO_ROOT/extensions"/*.jar &>/dev/null 2>&1; then
    CLASSPATH="$REPO_ROOT/extensions/*:$JAR_FILE"
  fi
  # shellcheck disable=SC2086
  java $JVM_OPTS \
    -Xms"$JVM_XMS" -Xmx"$JVM_XMX" \
    -server \
    -cp "$CLASSPATH" \
    "$MAIN_CLASS" server "$CONFIG_FILE" \
    > "$LOG_FILE" 2>&1 &
fi

PID=$!
echo "$PID" > "$PID_FILE"
echo "[boot] $APP started with PID $PID"

# ─── Wait for health ──────────────────────────────────────────────────────────
echo "[boot] Waiting for $APP to become healthy on port $ADMIN_PORT..."
MAX_WAIT=60
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
  if curl -s "http://localhost:$ADMIN_PORT/healthcheck" | grep -q '"healthy":true' 2>/dev/null; then
    echo "[boot] $APP is healthy."
    echo "[boot] App URL:   http://localhost:$APP_PORT"
    echo "[boot] Admin URL: http://localhost:$ADMIN_PORT/healthcheck"
    exit 0
  fi
  sleep 2
  WAITED=$((WAITED + 2))
  echo "[boot] Waiting... ($WAITED/$MAX_WAIT seconds)"
done

echo "[error] $APP did not become healthy within ${MAX_WAIT}s. Check logs: $LOG_FILE" >&2
tail -30 "$LOG_FILE" >&2
exit 1
