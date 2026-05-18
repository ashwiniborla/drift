# AGENTS.md — Drift Workflow Platform

## Repo Overview

Drift is a **Temporal-powered, low-code workflow builder and execution platform**. It lets users design and run
multi-step workflows using a graph of typed nodes (HTTP, Groovy, Branch, Wait, Instruction, Processor, etc.).
The platform consists of two runnable services and two shared library modules.

**GitHub:** https://github.com/flipkart-incubator/drift
**Group ID:** `com.flipkart.drift`
**Version:** `1.0-SNAPSHOT` (Maven `revision` property)
**Java:** 17  |  **Build:** Maven  |  **Framework:** Dropwizard 2.0.27 + Temporal 1.22.2

---

## Module Map

| Module       | Artifact ID   | Description                                                                                     |
|--------------|---------------|-------------------------------------------------------------------------------------------------|
| `java-sdk`   | java-sdk      | Public SDK — SPI interfaces (TokenProvider, ABTestingProvider, SchedulerProvider) + request/response models |
| `commons`    | commons       | Internal shared library — all domain models (NodeDefinition subtypes, Workflow, WorkflowNode, enums), persistence DAOs (HBase), Redis cache |
| `api`        | api           | Dropwizard HTTP service — REST endpoints for workflow execution + builder (CRUD for nodes/workflows). Publishes fat JAR. Port 8000 (app) / 8001 (admin) |
| `worker`     | worker        | Dropwizard Temporal worker service — executes workflow nodes as Temporal activities. Port 7200 (app) / 7201 (admin). Prometheus metrics on 9090. |
| `package`    | N/A           | Docker + Helm packaging artifacts (no Java sources)                                             |
| `examples/worker-flipkart` | N/A | Example Flipkart-specific worker extension (SPI implementations for auth/AB testing) |

**Dependency order (build):** `java-sdk` → `commons` → `api` / `worker`

---

## Build Commands

### Full build (all modules, skip GPG signing for local dev)
```bash
cd /Users/nidhi.b/IdeaProjects/drift
mvn clean package -DskipTests -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Build with tests
```bash
mvn clean verify -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Build only API module
```bash
mvn clean package -pl api -am -DskipTests -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Build only Worker module
```bash
mvn clean package -pl worker -am -DskipTests -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Build only shared libraries (java-sdk + commons)
```bash
mvn clean install -pl java-sdk,commons -DskipTests -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Run tests only
```bash
mvn test -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Run a single test class
```bash
mvn test -pl <module> -Dtest=<TestClassName> -Dgpg.skip=true
```

### Clean
```bash
mvn clean -Dgpg.skip=true
```

---

## Ports and Endpoints

### API Service (Dropwizard)
| Endpoint | Port | Description |
|----------|------|-------------|
| Application HTTP | 8000 | All REST API endpoints |
| Admin / Healthcheck | 8001 | Dropwizard admin console + `/healthcheck` |
| Health URL | `http://localhost:8001/healthcheck` | Returns `{"deadlocks":{"healthy":true}}` when up |

**Key REST paths:**
- `POST /v3/workflow/start` — start a workflow
- `PUT /v3/workflow/resume/{workflowId}` — resume a paused/wait workflow
- `DELETE /v3/workflow/terminate/{workflowId}` — terminate a workflow
- `GET /v3/workflow/{workflowId}` — fetch workflow state
- `POST /v3/workflow/{workflowId}/disconnected-node/execute` — execute a disconnected node
- `POST /nodeDefinition/` — create a node definition
- `PUT /nodeDefinition/` — update a node definition
- `GET /nodeDefinition/{id}?version=<v>` — fetch node definition
- `POST /nodeDefinition/{id}/publishNode/` — publish a node
- `POST /workflowDefinition/` — create a workflow definition
- `PUT /workflowDefinition/` — update workflow definition
- `GET /workflowDefinition/{id}?version=<v>&enrichNodeDefinition=false` — fetch workflow
- `POST /workflowDefinition/{id}/publishWorkflow/` — publish workflow
- `POST /workflowDefinition/{id}/activate?version=<N>` — mark a workflow version active
- `GET /workflowDefinition/treeView/{id}?version=<v>&mode=light` — render workflow diagram (PNG)

### Worker Service (Dropwizard)
| Endpoint | Port | Description |
|----------|------|-------------|
| Application HTTP | 7200 | Worker management API |
| Admin / Healthcheck | 7201 | `http://localhost:7201/healthcheck` |
| Prometheus metrics | 9090 | `/metrics` |

### Observability (Docker — local mode)
| Service | Port | Description |
|---------|------|-------------|
| VictoriaLogs | 9428 | Log ingestion + query: `http://localhost:9428` |
| Vector | N/A (internal) | Log shipper from app stdout to VictoriaLogs |

---

## Infrastructure Dependencies

| Dependency | Purpose | Config Variable |
|------------|---------|----------------|
| **Temporal** | Workflow orchestration engine | `TEMPORAL_FRONTEND`, `TEMPORAL_TASK_QUEUE` |
| **HBase** | Persistent store for node/workflow definitions and workflow context | `HBASE_CONFIG_BUCKET` |
| **Redis (Sentinel)** | Cache for node definitions and workflow definitions | `REDIS_MASTER`, `REDIS_SENTINELS`, `REDIS_PREFIX`, `REDIS_PASSWORD` |
| **Config Buckets** (S3/GCS/local) | Auth, AB config, workflow properties, enum store, HBase properties | `AUTH_PATH`, `AB_CONFIG_BUCKET`, `WORKFLOW_PROPERTY_PATH`, `ENUM_STORE_BUCKET` |
| **Hadoop** | HBase client identity (Kerberos/user impersonation) | `HADOOP_USERNAME`, `HADOOP_LOGIN_USER` |

---

## Node Types

| NodeType | Description |
|----------|-------------|
| `HTTP` | Makes an outbound HTTP call via Retrofit |
| `GROOVY` | Executes a Groovy script locally (local activity) |
| `BRANCH` | Conditional branching based on context evaluation (local activity) |
| `INSTRUCTION` | Renders an instruction for a human operator (local activity) |
| `PROCESSOR` | Invokes an SPI-registered processor |
| `CONTEXT_OVERRIDE` | Overrides workflow context values |
| `DELEGATE` | Delegates execution to another registered handler |
| `CHILD` | Spawns a child workflow |
| `WAIT` | Pauses execution (absolute time, scheduler, or event-based) |
| `SUCCESS` | Terminal success node (local activity) |
| `FAILURE` | Terminal failure node (local activity) |

---

## Architecture Rules

1. **Layer dependencies flow downward only:** `api` and `worker` depend on `commons`; `commons` depends on `java-sdk`; neither `commons` nor `java-sdk` may depend on `api` or `worker`.
2. **`java-sdk` is the public contract** — all SPI interfaces (`TokenProvider`, `ABTestingProvider`, `SchedulerProvider`) and request/response models live here. External consumer code should only depend on `java-sdk`.
3. **No direct cross-service calls** — `api` sends signals/queries to Temporal, not direct HTTP to `worker`. Worker polls Temporal for tasks.
4. **Local activities only for deterministic operations** — `INSTRUCTION`, `BRANCH`, `GROOVY`, `SUCCESS`, `FAILURE` are registered as local Temporal activities (no remote calls, deterministic).
5. **HBase DAOs via `IConnectionProvider`** — all data access goes through `AbstractEntityDao` using the `IConnectionProvider` SPI. Do not add direct HBase client calls in business logic.
6. **Config via environment variables only** — never hardcode URLs or credentials. All config is injected via YAML + `${ENV_VAR}` substitution (Dropwizard `EnvironmentVariableSubstitutor`).
7. **No `@SpringBootApplication`** — this is Guice + Dropwizard, not Spring. Do not add Spring beans or annotations.
8. **Lombok is provided scope** — do not shade Lombok into the final JAR. Use `@Data`, `@Slf4j`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` freely.
9. **Structured JSON logging** — all log statements must use SLF4J with placeholder args (`log.info("msg {}", val)`), never string concatenation. Include `[%X{id}]` MDC in log patterns.
10. **Temporal workflow determinism** — code inside `GenericWorkflowImpl` (Workflow context) must be deterministic; no `System.currentTimeMillis()`, no `Random`, no direct I/O. Use `Workflow.currentTimeMillis()` and side-effect APIs.

---

## Golden Rules

- **Always pass `-Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true`** to any local Maven build — the GPG plugin fails without a signing key and blocks all local builds.
- **Build fat JARs with maven-shade-plugin** — both `api` and `worker` use shade for packaging. The `ServicesResourceTransformer` is critical for SPI discovery (Groovy, etc.). Never break the shade config.
- **Test pattern is `**/*Test.java` and `**/*IT.java`** — use this suffix for all new test classes.
- **Worker task queue = `TEMPORAL_TASK_QUEUE` env var** — never hardcode the Temporal task queue name.
- **Redis cache is prefix-namespaced** — always use `REDIS_PREFIX` to avoid key collisions between environments.
- **All new REST resources must be registered in `DriftApplication.run()`** — Dropwizard does not auto-scan; resources must be explicitly registered.
- **Guice injection only** — use `@Inject` for all dependency injection; never `new SomeService()` in production code.
- **HBase entities use `@PrimaryKey` and `@Version` annotations** — required by `AbstractEntityDao` for reflection-based serialization.
- **Workflow definition IDs are business keys** — they are not UUIDs. Enforce `StringUtils.isNotEmpty()` validation before any HBase write.

---

## Local Development

### Start observability stack (local app-runtime mode)
```bash
bash scripts/infra/start.sh
```

### Start API service (native process)
```bash
APP=api bash scripts/agent/boot.sh
```

### Start Worker service (native process)
```bash
APP=worker bash scripts/agent/boot.sh
```

### Health checks
```bash
bash scripts/agent/health.sh api     # checks http://localhost:8001/healthcheck
bash scripts/agent/health.sh worker  # checks http://localhost:7201/healthcheck
```

### Query logs
```bash
bash scripts/agent/query-logs.sh "error" 50
```

### Stop all infra
```bash
bash scripts/infra/stop.sh
```

---

## CI / GitHub Actions

Workflows are in `.github/workflows/` (not currently in the repo — needs creation).
Maven Central publishing is done via `central-publishing-maven-plugin` with `autoPublish: true`.
GPG signing is required for Central publishing — handled via GitHub Actions secrets `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE`.

---

## Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Root multi-module POM. Set `revision` property for version changes. |
| `api/src/main/resources/config/configuration.yaml` | API service config (ports, Redis, HBase, Temporal endpoints) |
| `worker/src/main/resources/config/configuration.yaml` | Worker service config (ports, Redis, Temporal, Prometheus) |
| `package/docker/api/entrypoint.sh` | API Docker entrypoint — sets JVM flags, starts `api.jar` |
| `package/docker/worker/entrypoint.sh` | Worker Docker entrypoint — builds classpath with extensions, starts worker |
| `connections.md` | Resolved connection table — all external service endpoints for local dev |
| `harness-state.md` | Harness pipeline state — branch, stage, integrations |
| `scripts/agent/boot.sh` | Starts api or worker as a native process |
| `scripts/infra/start.sh` | Boots observability stack (Vector + VictoriaLogs) via Docker Compose |
