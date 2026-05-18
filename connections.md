# connections.md — Drift External Connection Discovery

## Purpose

This file documents all external dependencies that Drift needs to run locally, and how each one is accessed.
The Resolved Connection Table must be populated before running the services.

---

## Resolved Connection Table

| Service | Type | Access Mode | Host | Port | Config Key | Auth | Status |
|---------|------|------------|------|------|------------|------|--------|
| Temporal Frontend | grpc-api | B (discovered-endpoint) | (set TEMPORAL_FRONTEND) | 7233 | `TEMPORAL_FRONTEND` | none | PENDING |
| Temporal Task Queue | config | B (env-var) | N/A | N/A | `TEMPORAL_TASK_QUEUE` | none | PENDING |
| HBase | nosql-db | D (port-forward) | (ask team for staging endpoint) | 16000 | `HBASE_CONFIG_BUCKET` (path to hbase-site.xml) | kerberos/hadoop-user | PENDING |
| Redis Sentinel | cache | B (discovered-endpoint) | (set REDIS_SENTINELS) | 26379 | `REDIS_SENTINELS`, `REDIS_MASTER`, `REDIS_PREFIX`, `REDIS_PASSWORD` | password | PENDING |
| Auth Properties | config-bucket | F (skip / local file) | localhost | N/A | `AUTH_PATH` | none | PENDING |
| AB Config Bucket | config-bucket | F (skip / local file) | localhost | N/A | `AB_CONFIG_BUCKET` | none | PENDING |
| Workflow Properties | config-bucket | F (skip / local file) | localhost | N/A | `WORKFLOW_PROPERTY_PATH` | none | PENDING |
| Enum Store Bucket | config-bucket | F (skip / local file) | localhost | N/A | `ENUM_STORE_BUCKET` | none | PENDING |
| Hadoop Identity | config | B (env-var) | N/A | N/A | `HADOOP_USERNAME`, `HADOOP_LOGIN_USER` | none | PENDING |
| VictoriaLogs | observability | A (local Docker) | localhost | 9428 | N/A (docker-compose.yml) | none | RESOLVED |
| Vector | observability | A (local Docker) | localhost | N/A | N/A (docker-compose.yml) | none | RESOLVED |

---

## Discovery Notes

### How dependencies were found

**Config file scan:**
- `api/src/main/resources/config/configuration.yaml` — declares: `redisConfiguration` (REDIS_*), `hbasePropertiesPath` (HBASE_CONFIG_BUCKET), `temporalFrontEnd` (TEMPORAL_FRONTEND), `temporalTaskQueue` (TEMPORAL_TASK_QUEUE), `hadoopUserName`, `hadoopLoginUser`, `authPropertiesPath` (AUTH_PATH), `abPropertiesPath` (AB_CONFIG_BUCKET), `workflowPropertiesPath` (WORKFLOW_PROPERTY_PATH)
- `worker/src/main/resources/config/configuration.yaml` — same set plus `lookupPropertiesPath` (ENUM_STORE_BUCKET)

**Source code scan:**
- `RedisConfiguration.java` — `sentinels` list, `master` name, `prefix`, `password` — Jedis sentinel pool
- `DriftEntityModule.java` / `AbstractEntityDao.java` — HBase client via `IConnectionProvider` SPI
- `WorkflowClientModule.java` — Temporal `WorkflowServiceStubs` connected to `TEMPORAL_FRONTEND`

---

## Access Mode Reference

| Mode | Description |
|------|-------------|
| A (local Docker) | Service runs in docker-compose.yml (observability only) |
| B (discovered-endpoint) | Use the endpoint already configured in source/config |
| C (different endpoint) | User provides a different host:port |
| D (port-forward) | Port-forward from remote Kubernetes/SSH host |
| E (mock/stub) | Use WireMock or local emulator |
| F (skip) | Service is optional for local dev; disable via feature flag |

---

## Setup Instructions

### 1. Temporal Frontend
Drift requires a running Temporal server. For local dev, either:
- **Option A:** Use an existing staging Temporal — set `TEMPORAL_FRONTEND=<host>:<port>` in `.env`
- **Option B:** Run Temporal locally:
  ```bash
  # Using Temporal CLI
  temporal server start-dev
  # Default: localhost:7233
  ```

### 2. HBase
HBase is required for persisting node/workflow definitions and workflow context.
- **Recommended:** Port-forward from a staging/dev HBase cluster (never production).
- `HBASE_CONFIG_BUCKET` should point to a local `hbase-site.xml` file, e.g.:
  ```
  HBASE_CONFIG_BUCKET=/Users/nidhi.b/.drift/config/hbase-site.xml
  ```
- Contact the infrastructure team for the staging HBase endpoint.

  ⚠️ WRITABLE DATA STORE — always use a staging/dev HBase table, never production.

### 3. Redis Sentinel
- **Recommended:** Use an existing staging Redis Sentinel cluster.
- Format: `REDIS_SENTINELS=host1:26379,host2:26379,host3:26379`
- Use a unique `REDIS_PREFIX` for your local dev to avoid colliding with staging data.

### 4. Config Buckets (Auth, AB, Workflow, Enum Store)
These are S3/GCS/local-file paths to properties files. For local dev:
- Create empty properties files if the features are not needed:
  ```bash
  mkdir -p /tmp/drift-config
  touch /tmp/drift-config/auth.properties
  touch /tmp/drift-config/ab.properties
  touch /tmp/drift-config/workflow.properties
  touch /tmp/drift-config/enum.properties
  ```
- Then set:
  ```
  AUTH_PATH=/tmp/drift-config/auth.properties
  AB_CONFIG_BUCKET=/tmp/drift-config/ab.properties
  WORKFLOW_PROPERTY_PATH=/tmp/drift-config/workflow.properties
  ENUM_STORE_BUCKET=/tmp/drift-config/enum.properties
  ```

---

## .env Template

Copy to `.env` and fill in values:
```bash
# Temporal
TEMPORAL_FRONTEND=localhost:7233
TEMPORAL_TASK_QUEUE=drift-task-queue

# HBase (path to hbase-site.xml)
HBASE_CONFIG_BUCKET=/Users/nidhi.b/.drift/config/hbase-site.xml

# Hadoop
HADOOP_USERNAME=nidhi.b
HADOOP_LOGIN_USER=nidhi.b

# Redis Sentinel
REDIS_MASTER=mymaster
REDIS_SENTINELS=localhost:26379
REDIS_PREFIX=drift-local-dev-
REDIS_PASSWORD=

# Config buckets (local files for dev)
AUTH_PATH=/tmp/drift-config/auth.properties
AB_CONFIG_BUCKET=/tmp/drift-config/ab.properties
WORKFLOW_PROPERTY_PATH=/tmp/drift-config/workflow.properties
ENUM_STORE_BUCKET=/tmp/drift-config/enum.properties

# JVM sizing
JVM_XMS=256m
JVM_XMX=1g
```
