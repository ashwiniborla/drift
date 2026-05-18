#!/usr/bin/env bash
# check-prereq.sh — Pre-flight checks before any Docker or JVM operation
# Called by boot.sh and infra scripts before attempting anything.

set -euo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"
PASS=0
FAIL=1

echo "=== Drift Pre-flight Checks ==="

# ─── Step 1: Fix known ~/.docker/config.json 'credsStore' key bug ────────────
DOCKER_CFG="$HOME/.docker/config.json"
if [ -f "$DOCKER_CFG" ] && grep -q '"credsStore"' "$DOCKER_CFG" 2>/dev/null; then
  sed -i.bak 's/"credsStore"/"credStore"/g' "$DOCKER_CFG"
  echo "[fix] ~/.docker/config.json: renamed 'credsStore' -> 'credStore'"
fi

# ─── Step 2: Check Docker daemon ─────────────────────────────────────────────
if ! timeout 5 docker info --format '{{.ServerVersion}}' &>/dev/null; then
  echo "[warn] Docker daemon not running. Attempting: minikube start ..."
  if command -v minikube &>/dev/null; then
    minikube start
    eval "$(minikube docker-env)"
    if ! timeout 10 docker info --format '{{.ServerVersion}}' &>/dev/null; then
      echo "[error] minikube started but Docker daemon still unreachable." >&2
      echo "        Fix: ensure Rancher Desktop or Docker Desktop is running." >&2
      exit $FAIL
    fi
    echo "[ok] Docker available via minikube."
  else
    echo "[error] Docker daemon not running and minikube not found." >&2
    echo "        Fix: start Rancher Desktop or Docker Desktop, then retry." >&2
    echo "        Or install minikube: brew install minikube && minikube start" >&2
    exit $FAIL
  fi
else
  DOCKER_VERSION=$(timeout 5 docker info --format '{{.ServerVersion}}' 2>/dev/null)
  echo "[ok] Docker daemon running (version: $DOCKER_VERSION)"
fi

# ─── Step 3: Check docker compose V2 ─────────────────────────────────────────
if ! docker compose version &>/dev/null; then
  echo "[error] 'docker compose' (V2 plugin) not found." >&2
  echo "        Fix: update Docker Desktop / Rancher Desktop to include Compose V2." >&2
  echo "        Or add to PATH: export PATH=\"\$HOME/.rd/bin:\$PATH\"" >&2
  exit $FAIL
else
  COMPOSE_VERSION=$(docker compose version --short 2>/dev/null)
  echo "[ok] docker compose V2 available (version: $COMPOSE_VERSION)"
fi

# ─── Step 4: Check docker-compose.yml exists ──────────────────────────────────
if [ ! -f "$REPO_ROOT/docker-compose.yml" ]; then
  echo "[warn] docker-compose.yml not found at $REPO_ROOT"
  echo "       Run the local-infra agent to generate it."
else
  echo "[ok] docker-compose.yml found"
fi

# ─── Step 5: Check Java 17 ────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo "[error] Java not found. Install Java 17: brew install openjdk@17" >&2
  exit $FAIL
fi
JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '(?<=version ")[0-9]+')
if [ "${JAVA_VER:-0}" -lt 17 ]; then
  echo "[error] Java 17+ required. Found: $(java -version 2>&1 | head -1)" >&2
  echo "        Install: brew install openjdk@17" >&2
  exit $FAIL
fi
echo "[ok] Java $JAVA_VER found"

# ─── Step 6: Check Maven ──────────────────────────────────────────────────────
if ! command -v mvn &>/dev/null; then
  echo "[error] Maven not found. Install: brew install maven" >&2
  exit $FAIL
fi
MVN_VER=$(mvn --version 2>/dev/null | head -1)
echo "[ok] Maven found ($MVN_VER)"

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "ALL PREREQUISITES MET"
exit $PASS
