# ARCHITECTURE_RULES.md — Drift Module Boundary Rules

Enforced architecture rules for the Drift monorepo.
These rules are validated by ArchUnit tests that run as part of `mvn test`.

---

## Module Dependency Rules

### Rule 1: java-sdk is the foundation — no upward imports

`java-sdk` must not import any class from `commons`, `api`, or `worker`.

ArchUnit test: `JavaSdkArchTest`
Violation pattern: any class in `com.flipkart.drift.sdk.*` importing `com.flipkart.drift.commons.*`, `com.flipkart.drift.api.*`, or `com.flipkart.drift.worker.*`.

### Rule 2: commons does not know about services

`commons` must not import any class from `api` or `worker`.

ArchUnit test: `CommonsArchTest`
Violation pattern: any class in `com.flipkart.drift.commons.*` or `com.flipkart.drift.persistence.*` or `com.flipkart.drift.workflows.*` importing `com.flipkart.drift.api.*` or `com.flipkart.drift.worker.*`.

### Rule 3: api and worker are siblings — no cross-imports

`api` must not import any class from `worker`.
`worker` must not import any class from `api`.

ArchUnit tests: `ApiArchTest`, `WorkerArchTest`

### Rule 4: HBase access through DAO only

No class outside `com.flipkart.drift.persistence.dao.*` may import `org.apache.hadoop.hbase.client.Table` or `org.apache.hadoop.hbase.client.Connection` directly.

ArchUnit test: `PersistenceLayerTest`

### Rule 5: Workflow code must be deterministic

No class implementing `io.temporal.workflow.WorkflowInterface` may call:
- `System.currentTimeMillis()`
- `new java.util.Random()`
- `java.util.UUID.randomUUID()`
- `Thread.sleep()`

Use Temporal's `Workflow.currentTimeMillis()`, `Workflow.sleep()`, and `Workflow.randomUUID()` instead.

ArchUnit test: `TemporalDeterminismTest`

---

## Package Naming Rules

| Module | Root Package | Sub-packages |
|--------|-------------|--------------|
| `java-sdk` | `com.flipkart.drift.sdk` | `model.*`, `spi.*` |
| `commons` | `com.flipkart.drift.commons`, `com.flipkart.drift.persistence`, `com.flipkart.drift.workflows` | `utils`, `exception`, `entity`, `dao`, `cache`, `model` |
| `api` | `com.flipkart.drift.api` | `bootstrap`, `config`, `module`, `resources`, `service`, `filters`, `exception` |
| `worker` | `com.flipkart.drift.worker` | `bootstrap`, `config`, `resources`, `workflows`, `translator`, `util`, `temporal` |

---

## Annotation Rules

- All REST resource classes (`@Path`) must be in the `resources` sub-package of their module.
- All Guice modules must extend `AbstractModule` and live in the `module` (api) or `bootstrap` (worker) package.
- All exception mappers must implement `ExceptionMapper` and live in the `exception` sub-package.
- All Temporal workflow implementations must be annotated with `@WorkflowInterface` (interface) and implement a class in the `workflows` package.

---

## ArchUnit Test Locations

Add ArchUnit test classes in:
- `api/src/test/java/com/flipkart/drift/api/arch/ApiArchTest.java`
- `worker/src/test/java/com/flipkart/drift/worker/arch/WorkerArchTest.java`
- `commons/src/test/java/com/flipkart/drift/arch/CommonsArchTest.java`
- `java-sdk/src/test/java/com/flipkart/drift/sdk/arch/JavaSdkArchTest.java`

ArchUnit dependency (add to parent pom or individual module pom):
```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.2.1</version>
    <scope>test</scope>
</dependency>
```
