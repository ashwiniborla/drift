# LOCAL_DEV.md — Drift Local Development Guide

How to build, run, and develop the Drift services locally.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17 | Compile and run services |
| Maven | 3.5.0+ | Build |
| Docker | 20.x+ | Observability stack (Vector + VictoriaLogs) |
| Rancher Desktop | any | Container runtime (Temporal, HBase, Redis already running here) |
| `mmdc` | 11.x | Mermaid diagram rendering (harness only) |

Rancher Desktop must be running with these services already started:
- Temporal Server (gRPC :7233, UI :8080)
- HBase (ZooKeeper :2181, Thrift :9090)
- Redis Sentinel (:26379 sentinel, :6379 redis)

---

## Quick Start — Full Stack (Docker mode)

```bash
# 1. Copy env file and fill in values
cp .env.example .env
# Edit .env and set all required variables (see Environment Variables section in AGENTS.md)

# 2. Start observability stack
docker compose up -d

# 3. Build all modules
mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am

# 4. Boot services
bash scripts/agent/boot.sh
```

---

## Environment Variables (.env)

Create `.env` at the repo root before running locally:

```bash
# Redis Sentinel
REDIS_PASSWORD=
REDIS_MASTER=mymaster
REDIS_SENTINELS=localhost:26379
REDIS_PREFIX=drift:

# HBase
HBASE_CONFIG_BUCKET=http://localhost/hbase-local.properties
HADOOP_USERNAME=hadoop
HADOOP_LOGIN_USER=hadoop

# Temporal
TEMPORAL_FRONTEND=localhost:7233
TEMPORAL_TASK_QUEUE=drift-task-queue

# API JVM
JVM_XMS=512m
JVM_XMX=1g

# Worker additional
ENUM_STORE_BUCKET=http://localhost/lookup.properties
AB_CONFIG_BUCKET=http://localhost/ab.properties
AUTH_PATH=http://localhost/auth.properties
WORKFLOW_PROPERTY_PATH=http://localhost/workflow.properties
```

---

## Build Targets

```bash
# Full clean build (all modules, no tests)
mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am

# Run all tests
mvn clean verify -pl java-sdk,commons,api,worker -am

# API only (builds upstream deps too)
mvn clean package -DskipTests -pl api -am

# Worker only (builds upstream deps too)
mvn clean package -DskipTests -pl worker -am
```

---

## Running Services Locally (native process)

The `scripts/agent/boot.sh` script handles both services. Individual invocations:

### API Service

```bash
# After building api module:
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -Xms512m -Xmx1g \
  -Djava.net.preferIPv4Stack=true \
  -jar api/target/api-*.jar \
  server api/src/main/resources/config/configuration.yaml
```

### Worker Service

```bash
# After building worker module:
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Dgroovy.use.classvalue=true \
  -Xms512m -Xmx1g \
  -Djava.net.preferIPv4Stack=true \
  -cp worker/target/worker-*.jar \
  com.flipkart.drift.worker.bootstrap.WorkerApplication \
  server worker/src/main/resources/config/configuration.yaml
```

---

## Health Checks

```bash
# API service health
curl -s http://localhost:8001/healthcheck | python3 -m json.tool

# Worker service health
curl -s http://localhost:7201/healthcheck | python3 -m json.tool

# Quick ping
curl -s http://localhost:8001/ping      # should return "pong"
curl -s http://localhost:7201/ping      # should return "pong"
```

---

## Observability

After `docker compose up -d`, VictoriaLogs is available at `http://localhost:9428`.

Query logs via:
```bash
# All API logs (last 5 minutes)
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=service:drift-api' \
  --data-urlencode 'start=5m'

# All Worker logs
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=service:drift-worker' \
  --data-urlencode 'start=5m'

# Error logs across all services
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=level:ERROR' \
  --data-urlencode 'start=5m'
```

Or use the script:
```bash
bash scripts/agent/query-logs.sh drift-api ERROR 5m
```

---

## Running in Docker Compose (full mode)

When `app-runtime: docker` is set in `harness-state.md`:

```bash
# Build jars first
mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am

# Build Docker images
docker build -f package/docker/api/Dockerfile -t drift-api:local .
docker build -f package/docker/worker/Dockerfile -t drift-worker:local .

# Start all services
docker compose up -d
```

---

## Temporal UI

Browse workflow runs at `http://localhost:8080` (Rancher Desktop hosted).

---

## Common Issues

### "Connection refused" to Temporal :7233
Rancher Desktop may not be running or Temporal container is down. Check Rancher Desktop dashboard.

### HBase connection timeout
Verify HBase is running in Rancher Desktop. The `hbasePropertiesPath` URL must be reachable.

### Redis "No reachable sentinel" error
Ensure Redis Sentinel is running in Rancher Desktop. Verify `REDIS_SENTINELS` env var points to the correct host:port.

### OutOfMemoryError on build
Increase Maven memory: `export MAVEN_OPTS="-Xmx2g"` before running mvn.
