# LOCAL_DEV.md — Drift Local Development Guide

## Prerequisites

| Tool | Minimum Version | Install |
|------|----------------|---------|
| Java | 17 | `brew install openjdk@17` |
| Maven | 3.8+ | `brew install maven` |
| Docker | 24+ | Rancher Desktop or Docker Desktop |
| docker compose | V2 (plugin) | Included with Docker Desktop / Rancher Desktop |

## App Runtime Mode: LOCAL

This repo uses `app-runtime: local`. The application services (`api` and `worker`) run as **native JVM processes**
on your machine. Only the **observability stack** (Vector + VictoriaLogs) runs in Docker.

## Quick Start

### 1. Start observability stack
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/infra/start.sh
```
This boots Vector (log shipper) and VictoriaLogs (log storage + query).

### 2. Set required environment variables
Copy the template and fill in your values:
```bash
cp /Users/nidhi.b/IdeaProjects/drift/.env.example /Users/nidhi.b/IdeaProjects/drift/.env
# Edit .env with actual Temporal, HBase, Redis connection details
```

Required vars (see `connections.md` for resolved values):
```
TEMPORAL_FRONTEND=<host:port>
TEMPORAL_TASK_QUEUE=<queue-name>
HBASE_CONFIG_BUCKET=<path-to-hbase-site.xml>
REDIS_MASTER=<master-name>
REDIS_SENTINELS=<host1:port1,host2:port2>
REDIS_PREFIX=<local-dev-prefix>
REDIS_PASSWORD=<password-or-empty>
HADOOP_USERNAME=<username>
HADOOP_LOGIN_USER=<login-user>
AUTH_PATH=<path-to-auth-properties>
AB_CONFIG_BUCKET=<path-to-ab-properties>
WORKFLOW_PROPERTY_PATH=<path-to-workflow-properties>
ENUM_STORE_BUCKET=<path-to-enum-properties>
```

### 3. Build the services
```bash
cd /Users/nidhi.b/IdeaProjects/drift
mvn clean package -DskipTests -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### 4. Start API service
```bash
APP=api bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/boot.sh
```

### 5. Start Worker service (in a separate terminal)
```bash
APP=worker bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/boot.sh
```

### 6. Verify health
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/health.sh api
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/health.sh worker
```

## Ports Reference

| Service | Port | URL |
|---------|------|-----|
| API application | 8000 | `http://localhost:8000` |
| API admin/health | 8001 | `http://localhost:8001/healthcheck` |
| Worker application | 7200 | `http://localhost:7200` |
| Worker admin/health | 7201 | `http://localhost:7201/healthcheck` |
| Worker Prometheus | 9090 | `http://localhost:9090/metrics` |
| VictoriaLogs | 9428 | `http://localhost:9428` |

## Log Querying

Logs from both services are shipped to VictoriaLogs via Vector.

Query recent errors:
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/query-logs.sh "error" 50
```

Query by service:
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/query-logs.sh "WorkflowResource" 20
```

Direct VictoriaLogs query:
```bash
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=error' \
  --data-urlencode 'limit=50'
```

## Running Tests
```bash
cd /Users/nidhi.b/IdeaProjects/drift
mvn test -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

## Common Issues

### `GPG signing failed`
Always pass `-Dgpg.skip=true` to any local Maven build.

### `Class not found: com.flipkart.drift...`
Run `mvn clean install -pl java-sdk,commons -DskipTests -Dgpg.skip=true` to install shared modules first.

### `Temporal frontend unreachable`
Check `TEMPORAL_FRONTEND` is set and the Temporal service is accessible (VPN/port-forward if needed).

### `HBase connection failed`
Ensure `HBASE_CONFIG_BUCKET` points to a valid `hbase-site.xml` and HBase is reachable from your network.

### `Redis sentinel refused`
Check `REDIS_SENTINELS` format: should be `host1:port,host2:port,host3:port`.

## Stop Everything
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/infra/stop.sh
# Kill JVM processes manually (boot.sh logs the PID)
```
