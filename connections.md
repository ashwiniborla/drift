# connections.md — Drift Service Connection Map

All external service dependencies for local development.
Resolved entries are confirmed by the developer. Update this file when connection details change.

---

## Resolved Connection Table

| Service | Type | Access Mode | Host | Port(s) | Config Key | Auth | Status |
|---------|------|-------------|------|---------|------------|------|--------|
| Temporal gRPC | gRPC / workflow engine | Use discovered endpoint (Rancher Desktop) | localhost | 7233 | `TEMPORAL_FRONTEND=localhost:7233` | none | RESOLVED |
| Temporal UI | HTTP | Use discovered endpoint (Rancher Desktop) | localhost | 8080 | — | none | RESOLVED |
| HBase (ZooKeeper) | NoSQL / persistence | Use discovered endpoint (Rancher Desktop) | localhost | 2181 | `HBASE_CONFIG_BUCKET` → properties file | Kerberos or none | RESOLVED |
| HBase (Thrift) | Thrift RPC | Use discovered endpoint (Rancher Desktop) | localhost | 9090 | via `hbasePropertiesPath` | Hadoop user identity | RESOLVED |
| Redis Sentinel | Cache + pub/sub | Use discovered endpoint (Rancher Desktop) | localhost | 26379 (sentinel), 6379 (redis) | `REDIS_SENTINELS=localhost:26379`, `REDIS_MASTER=mymaster` | `REDIS_PASSWORD` | RESOLVED |
| VictoriaLogs | Log storage | Local Docker (compose) | localhost | 9428 | hardcoded in vector.yaml | none | RESOLVED |
| Vector | Log shipper | Local Docker (compose) | localhost | — | docker-compose.yml | none | RESOLVED |
| PostgreSQL (Temporal) | RDBMS / Temporal backend | Local Docker (compose) | postgres | 5432 | Temporal server config | `temporal` / `temporal` | RESOLVED |

---

## Service Details

### Temporal (gRPC)
- **Running in**: Rancher Desktop container
- **Local endpoint**: `localhost:7233`
- **Config**: Set env var `TEMPORAL_FRONTEND=localhost:7233`
- **Task queue**: Set env var `TEMPORAL_TASK_QUEUE=drift-task-queue` (or your queue name)
- **UI**: `http://localhost:8080`
- **Note**: Do NOT run a second Temporal in Docker Compose — use the existing Rancher Desktop instance.

### HBase
- **Running in**: Rancher Desktop container
- **ZooKeeper quorum**: `localhost:2181`
- **Thrift port**: `localhost:9090`
- **Config**: The `hbasePropertiesPath` / `HBASE_CONFIG_BUCKET` environment variable must point to a URL that serves an HBase properties file. For local dev, this can be a local file served over HTTP or a static file server.
- **Required properties** (in the properties file):
  ```
  hbase.zookeeper.quorum=localhost
  hbase.zookeeper.property.clientPort=2181
  ```
- **Hadoop user identity**: Set `HADOOP_USERNAME` and `HADOOP_LOGIN_USER` to the user configured in HBase.

### Redis Sentinel
- **Running in**: Rancher Desktop container
- **Sentinel endpoint**: `localhost:26379`
- **Redis primary**: `localhost:6379`
- **Master name**: `mymaster` (verify with your Rancher Desktop setup)
- **Config env vars**:
  ```
  REDIS_SENTINELS=localhost:26379
  REDIS_MASTER=mymaster
  REDIS_PASSWORD=
  REDIS_PREFIX=drift:
  ```

### VictoriaLogs (Docker Compose)
- **Port**: `localhost:9428`
- **Query API**: `http://localhost:9428/select/logsql/query`
- **Health**: `http://localhost:9428/health`
- **Retention**: 7 days (local dev)

### Vector (Docker Compose)
- **Role**: Tail stdout/stderr from API and Worker containers, parse JSON, forward to VictoriaLogs.
- **Config**: `scripts/infra/vector.yaml`

### PostgreSQL (Docker Compose)
- **Role**: Temporal persistence backend (if Temporal needs a fresh database for local dev).
- **Port**: `5432` (internal to Docker network)
- **User**: `temporal` / Password: `temporal`
- **Note**: Only needed if the Rancher Desktop Temporal instance requires its own PostgreSQL. If Temporal already has a database, this service can be commented out.

---

## Drift Services (not in Docker Compose — run as native processes or built Docker images)

| Service | Application Port | Admin Port | Health URL |
|---------|-----------------|------------|------------|
| `drift-api` | 8000 | 8001 | http://localhost:8001/healthcheck |
| `drift-worker` | 7200 | 7201 | http://localhost:7201/healthcheck |
| `drift-worker` (Prometheus) | 9090 | — | http://localhost:9090/metrics |

---

## Pending / Not Applicable

| Service | Type | Notes |
|---------|------|-------|
| `ENUM_STORE_BUCKET` | HTTP properties URL | Worker only. Provide a static file server or mock URL for local dev. |
| `AB_CONFIG_BUCKET` | HTTP properties URL | Worker only. Use a mock or static file server. |
| `AUTH_PATH` | HTTP properties URL | Worker only. Use a mock or static file server. |
| `WORKFLOW_PROPERTY_PATH` | HTTP properties URL | Worker only. Use a mock or static file server. |
