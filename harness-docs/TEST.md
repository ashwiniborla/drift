# TEST.md — Drift Test Strategy

How tests are structured, run, and what coverage targets apply.

---

## Test Frameworks

| Framework | Version | Usage |
|-----------|---------|-------|
| JUnit 5 (Jupiter) | 5.10.0 | Unit tests (all modules) |
| JUnit 4 | 4.13.1 | Legacy tests (avoid for new code) |
| Mockito | 5.6.0 | Mocking |
| Maven Surefire | 3.1.2 | Test runner |

---

## Coverage Requirements

| Module | Line Coverage Target | Branch Coverage Target |
|--------|---------------------|------------------------|
| `java-sdk` | 80% | 70% |
| `commons` | 80% | 70% |
| `api` | 85% | 75% |
| `worker` | 85% | 75% |

Coverage is measured by JaCoCo during `mvn verify`. Reports land at `<module>/target/site/jacoco/index.html`.

The harness validate.md gate requires **90% coverage on new code** (delta coverage) — not just overall. SonarQube enforces this per-PR.

---

## Test Layout

Tests live in `src/test/java` within each module, mirroring the production package structure:

```
api/src/test/java/com/flipkart/drift/api/
  resources/      ← Resource layer tests
  service/        ← Service layer tests
  module/         ← Guice module wiring tests (optional)

worker/src/test/java/com/flipkart/drift/worker/
  workflows/      ← GenericWorkflowImpl tests
  translator/     ← Node translator tests

commons/src/test/java/com/flipkart/drift/
  persistence/    ← DAO and cache tests (mock HBase client)

java-sdk/src/test/java/com/flipkart/drift/sdk/
  spi/            ← SPI factory/default tests
  model/          ← Model serialization tests
```

---

## Running Tests

```bash
# All modules
mvn clean verify -pl java-sdk,commons,api,worker -am

# Single module
mvn test -pl api -am
mvn test -pl worker -am

# Single class
mvn test -pl api -Dtest=WorkflowResourceTest -am

# Single method
mvn test -pl api -Dtest=WorkflowResourceTest#testStartWorkflow -am

# Skip tests (build only)
mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am
```

---

## Writing New Tests

### Unit test (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private SomeDependency dependency;

    @InjectMocks
    private MyService service;

    @Test
    void shouldDoSomethingWhenConditionIsMet() {
        // Arrange
        when(dependency.getData()).thenReturn("value");

        // Act
        String result = service.process();

        // Assert
        assertThat(result).isEqualTo("expected");
    }
}
```

### Testing HBase DAOs

Mock the HBase `Table` using Mockito. Do not use an embedded HBase for unit tests — it's too slow. Use `HBaseTestingUtility` only for integration tests that are explicitly tagged `@Tag("integration")`.

### Testing Temporal Workflows

Use the Temporal test server (`TestWorkflowEnvironment`) from `io.temporal:temporal-testing`. Do not mock Temporal SDK internals directly.

---

## Static Analysis

```bash
# Checkstyle (if configured)
mvn checkstyle:check -pl api,worker,commons,java-sdk

# SpotBugs (if configured)
mvn spotbugs:check -pl api,worker,commons,java-sdk

# ArchUnit tests run as part of normal test execution
mvn test -pl api,worker,commons,java-sdk -am
```

---

## CI Gates

Before merging, all of the following must pass:
1. `mvn clean verify` passes (all tests green, no Surefire failures)
2. JaCoCo new-code coverage >= 90% (enforced by SonarQube)
3. Zero SonarQube BLOCKER or CRITICAL issues
4. ArchUnit layer boundary tests pass
5. `mvn checkstyle:check` passes (if configured)
