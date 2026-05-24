# connections.md — Drift External Connection Discovery

## Purpose

This file documents all external dependencies that Drift needs to run locally, and how each one is accessed.
The Resolved Connection Table must be populated before running the services.

---

## Resolved Connection Table

| Service | Type | Access Mode | Host | Port | Config Key | Auth | Status |
|---------|------|------------|------|------|------------|------|--------|
| Temporal Frontend | grpc-api | A (local Docker) | localhost | 7233 | `TEMPORAL_FRONTEND` | none | RESOLVED |
| Temporal UI | http | A (local Docker) | localhost | 8080 | N/A | none | RESOLVED |
| Temporal Task Queue | config | B (env-var) | N/A | N/A | `TEMPORAL_TASK_QUEUE` | none | RESOLVED — no pre-creation needed; worker auto-registers on startup |
| HBase Master RPC | nosql-db | A (local Docker) | localhost | 16000 | `HBASE_CONFIG_BUCKET` (path to hbase-site.xml) | none | RESOLVED — running |
| HBase ZooKeeper | nosql-db | A (local Docker) | localhost | 2181 | included in hbase-site.xml | none | RESOLVED — running (imok) |
| Auth Properties | config-bucket | F (skip / local file) | localhost | N/A | `AUTH_PATH` | none | RESOLVED — use empty local file |
| AB Config Bucket | config-bucket | F (skip / local file) | localhost | N/A | `AB_CONFIG_BUCKET` | none | RESOLVED — use empty local file |
| Workflow Properties | config-bucket | F (skip / local file) | localhost | N/A | `WORKFLOW_PROPERTY_PATH` | none | RESOLVED — use empty local file |
| Enum Store Bucket | config-bucket | F (skip / local file) | localhost | N/A | `ENUM_STORE_BUCKET` | none | RESOLVED — use empty local file |
| Hadoop Identity | config | B (env-var) | N/A | N/A | `HADOOP_USERNAME`, `HADOOP_LOGIN_USER` | none | RESOLVED — HBase auth identity; optional for local (no Kerberos); set to any username or leave blank |
| VictoriaLogs | observability | A (local Docker) | localhost | 9428 | N/A (docker-compose.yml) | none | RESOLVED |
| Vector | observability | A (local Docker) | localhost | N/A | N/A (docker-compose.yml) | none | RESOLVED |

---

## Discovery Notes

### How dependencies were found

**Config file scan:**
- `api/src/main/resources/config/configuration.yaml` — declares: `hbasePropertiesPath` (HBASE_CONFIG_BUCKET), `temporalFrontEnd` (TEMPORAL_FRONTEND), `temporalTaskQueue` (TEMPORAL_TASK_QUEUE), `hadoopUserName`, `hadoopLoginUser`, `authPropertiesPath` (AUTH_PATH), `abPropertiesPath` (AB_CONFIG_BUCKET), `workflowPropertiesPath` (WORKFLOW_PROPERTY_PATH)
- `worker/src/main/resources/config/configuration.yaml` — same set plus `lookupPropertiesPath` (ENUM_STORE_BUCKET), `callbackConfig`

**Source code scan:**
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
Running locally in Docker (image: `temporalio/server:1.31.0`).

```bash
# Start (if not running)
docker start temporal temporal-postgresql temporal-elasticsearch temporal-ui

# Verify
curl -s http://localhost:8080   # Temporal UI
```

- **gRPC endpoint:** `localhost:7233`
- **UI:** `http://localhost:8080`
- **Task Queue:** set `TEMPORAL_TASK_QUEUE` in `.env` — use any name e.g. `drift-local`

### 2. HBase
Running locally in Docker (image: `dajobe/hbase`). Currently **stopped** — start before booting the app.

```bash
# Start HBase
docker start hbase

# Verify (wait ~15s for HBase to initialize)
curl -s http://localhost:16010/master-status | grep -o "Master.*running"
```

Ports (all bound to localhost):
| Port | Service |
|------|---------|
| 2181 | ZooKeeper (used by HBase client) |
| 16000 | HBase Master RPC |
| 16010 | HBase Master Web UI |
| 16020 | RegionServer RPC |
| 16030 | RegionServer Web UI |

Generate `hbase-site.xml` for local use:
```bash
mkdir -p /tmp/drift-config
cat > /tmp/drift-config/hbase-site.xml <<'EOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>hbase.zookeeper.quorum</name>
    <value>localhost</value>
  </property>
  <property>
    <name>hbase.zookeeper.property.clientPort</name>
    <value>2181</value>
  </property>
  <property>
    <name>hbase.master</name>
    <value>localhost:16000</value>
  </property>
</configuration>
EOF
```

Then set: `HBASE_CONFIG_BUCKET=file:///tmp/drift-config/hbase-site.xml  # file:// prefix required — Archaius URLConfigurationSource expects a URL`

⚠️ Local HBase only — never use production HBase tables.

### 3. Config Buckets (Auth, AB, Workflow, Enum Store)
These are local file paths for dev. Create empty stubs:
```bash
mkdir -p /tmp/drift-config
touch /tmp/drift-config/auth.properties
touch /tmp/drift-config/ab.properties
touch /tmp/drift-config/workflow.properties
touch /tmp/drift-config/enum.properties
```

---

## .env Template

Copy to `.env` and fill in values. Items marked `# RESOLVED` are confirmed from running containers.

```bash
# ── Temporal ─────────────────────────────────────────────────────────────────
TEMPORAL_FRONTEND=localhost:7233          # RESOLVED — docker container temporal:7233
TEMPORAL_TASK_QUEUE=drift-local          # RESOLVED — any name works; worker auto-registers it on startup

# ── HBase (path to hbase-site.xml) ───────────────────────────────────────────
# RESOLVED — local Docker container 'hbase' (dajobe/hbase); run `docker start hbase` first
# Generate hbase-site.xml using the command in Setup Instructions §2 above
HBASE_CONFIG_BUCKET=file:///tmp/drift-config/hbase-site.xml  # file:// prefix required — Archaius URLConfigurationSource expects a URL

# ── Hadoop ────────────────────────────────────────────────────────────────────
HADOOP_USERNAME=nidhi.b                  # RESOLVED — HBase client identity (no-op for local HBase, non-fatal if blank)
HADOOP_LOGIN_USER=nidhi.b               # RESOLVED — sets UGI login user; optional for local dev (no Kerberos)

# ── Config buckets (local stub files for dev) ─────────────────────────────────
# RESOLVED — create stubs with: mkdir -p /tmp/drift-config && touch /tmp/drift-config/*.properties
AUTH_PATH=/tmp/drift-config/auth.properties
AB_CONFIG_BUCKET=/tmp/drift-config/ab.properties
WORKFLOW_PROPERTY_PATH=/tmp/drift-config/workflow.properties
ENUM_STORE_BUCKET=/tmp/drift-config/enum.properties

# ── JVM sizing ────────────────────────────────────────────────────────────────
JVM_XMS=256m
JVM_XMX=1g
```
