# TEST.md — Drift Testing Guide

## Test Framework

| Library | Version | Purpose |
|---------|---------|---------|
| JUnit 5 (Jupiter) | 5.10.0 | Unit and integration tests |
| JUnit 4 | 4.13.1 | Legacy support |
| Mockito | 5.6.0 | Mocking dependencies |
| Temporal Testing | 1.22.2 | `TestWorkflowEnvironment` for workflow tests |

## Test Naming Convention

- Unit tests: `**/*Test.java` (e.g., `WorkflowNodeExecutorTest.java`)
- Integration tests: `**/*IT.java` (e.g., `WorkflowResourceIT.java`)

Maven Surefire picks up both patterns automatically.

## Running Tests

### All tests
```bash
cd /Users/nidhi.b/IdeaProjects/drift
mvn test -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```

### Tests for a specific module
```bash
mvn test -pl worker -Dgpg.skip=true
mvn test -pl api -Dgpg.skip=true
mvn test -pl commons -Dgpg.skip=true
```

### Single test class
```bash
mvn test -pl worker -Dtest=GenericWorkflowImplTest -Dgpg.skip=true
```

### Single test method
```bash
mvn test -pl worker -Dtest="GenericWorkflowImplTest#testStartWorkflow" -Dgpg.skip=true
```

## Writing Tests

### Unit test (Mockito + JUnit 5)
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private NodeDefinitionDao nodeDefinitionDao;

    @InjectMocks
    private NodeDefinitionService nodeDefinitionService;

    @Test
    void testAddNode_validInput_savesToHBase() {
        // arrange
        NodeDefinition node = new HttpNode();
        node.setId("test-node");
        node.setName("Test Node");
        // ...
        // act + assert
    }
}
```

### Workflow test (Temporal TestWorkflowEnvironment)
```java
@Test
void testStartWorkflow_happyPath() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker("test-task-queue");
    worker.registerWorkflowImplementationTypes(GenericWorkflowImpl.class);
    worker.registerActivitiesImplementations(/* mock activities */);
    testEnv.start();

    WorkflowClient client = testEnv.getWorkflowClient();
    GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("test-task-queue").build());

    WorkflowStartRequest request = new WorkflowStartRequest();
    // ... set up request
    workflow.startWorkflow(request);

    testEnv.close();
}
```

## Test Coverage Targets

| Module | Target Coverage |
|--------|----------------|
| commons (domain models + DAOs) | 80%+ |
| api (resources + services) | 75%+ |
| worker (activities + workflow) | 80%+ |
| java-sdk | 70%+ (models are mostly POJOs) |

## Current Test Gaps (known)

- No tests exist yet for any module (4 test-named files found, all are SPIs not tests).
- Priority areas to add tests:
  1. `WorkflowNodeExecutor` — critical routing logic, all NodeType branches
  2. `GenericWorkflowImpl` — workflow lifecycle: start, resume, terminate, failure
  3. `NodeDefinitionService` / `WorkflowDefinitionService` — CRUD validation logic
  4. `GroovyTranslator` — script evaluation edge cases
  5. `HttpExecutor` — retry behavior, error codes
  6. Activity implementations — each `*ActivityImpl` class

## Mocking External Dependencies

For local unit tests, use Mockito to mock:
- `NodeDefinitionDao` / `WorkflowDefinitionDao` / `WorkflowContextHBDao` (HBase)
- `NodeDefinitionCache` / `WorkflowCache` (Redis)
- `TemporalService` (Temporal client stubs)

For integration tests use Temporal's `TestWorkflowEnvironment` — it provides an in-memory Temporal server.

## CI Test Execution

Tests are run as part of `mvn verify`. The CI pipeline skips GPG signing and Javadoc generation:
```bash
mvn verify -Dgpg.skip=true -Dattach.sources.skip=true -Dattach.javadoc.skip=true
```
