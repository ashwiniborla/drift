# PRODUCT_SENSE.md — Drift Product Context

## What is Drift?

Drift is a **low-code workflow orchestration platform** for Flipkart engineering teams. It provides:

1. **A workflow builder API** — teams define workflows as graphs of typed nodes (HTTP calls, Groovy transforms,
   conditional branches, wait states, human instructions) via REST APIs. No workflow code needs to be written.

2. **A durable execution engine** — workflows execute with Temporal guarantees: exactly-once activity execution,
   automatic retries, crash recovery, and long-running wait states (timers, events).

3. **An SPI extension model** — teams extend the Worker with custom auth token providers, A/B testing cohort
   resolvers, and scheduler integrations — without modifying Drift's core code.

## Primary Users

| Persona | Need | How Drift helps |
|---------|------|----------------|
| Platform/backend engineer | Orchestrate multi-step processes (order fulfillment, returns, seller workflows) without writing Temporal boilerplate | Define workflow as a JSON graph; Drift handles Temporal lifecycle |
| Operations team | Trigger manual intervention in a paused workflow | `INSTRUCTION` node + `/v3/workflow/resume` API |
| Product engineer | Run A/B experiments inside workflows | `ABTestingProvider` SPI, branch on cohort result |
| Infrastructure engineer | Integrate workflows with scheduling systems | `SchedulerProvider` SPI, `WAIT` node with `SCHEDULER` wait type |

## Key Concepts

### Workflow Definition
A directed graph of `WorkflowNode` entries stored in HBase. Each node references a `NodeDefinition` (the reusable,
versioned node config) and the name of the next node(s) to execute. Workflows are versioned and must be explicitly
published and activated before they can be started.

### Node Definition
A reusable, versioned configuration unit of a single step. Examples:
- HTTP node: `url`, `method`, `headers`, `body` template, response mapping
- Groovy node: `script` (evaluated against workflow context)
- Branch node: conditions evaluated against context → routes to different next nodes
- Wait node: pauses workflow until timer fires, event arrives, or scheduler triggers

### Workflow Context (`threadContext`)
A `Map<String, String>` passed at workflow start and mutated by activities. This is the shared data bus across
all nodes in a workflow execution. Activities read inputs from context, write outputs back.

### Issue-Workflow Mapping
Drift workflows are tied to "issues" (domain entities — e.g., a return request, a seller action). The
`IssueDetail` in `WorkflowStartRequest` identifies which workflow definition to load for a given issue type.

## Product Boundaries

**In scope for Drift:**
- Workflow definition CRUD (builder API)
- Durable workflow execution via Temporal
- Node activity execution (HTTP, Groovy, Branch, Wait, Instruction, Processor, Child)
- Workflow state query and resume
- SPI extension for auth/AB/scheduler

**Out of scope for Drift (handled by consumers):**
- Business logic inside nodes (consumers implement `TokenProvider`, `ABTestingProvider`, etc.)
- UI for workflow building (separate product, consumes Drift APIs)
- Workflow triggering logic (consumers call `POST /v3/workflow/start`)
- Temporal cluster management

## Design Philosophy

- **Low-code, not no-code** — Engineers define workflows via JSON/API, not drag-and-drop UI.
- **Extension over modification** — New node behaviors are added via the Worker extension model (SPI + classpath),
  not by modifying Drift core.
- **Durability by default** — Temporal gives crash recovery, retry, and audit log for free.
- **Open source + internal** — Drift is open-sourced at `github.com/flipkart-incubator/drift` but the
  `worker-flipkart` example shows Flipkart-internal extensions.

## Success Metrics

- Workflow execution latency (p50/p95/p99) — tracked via `@Timed` metrics
- Workflow failure rate by NodeType
- Cache hit rate for node/workflow definition reads
- Worker throughput: activities/sec by node type
