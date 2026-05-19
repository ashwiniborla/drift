# PRODUCT_SENSE.md — Drift Product Context

Business context and product decisions for the Drift workflow orchestration platform.

---

## What Problem Does Drift Solve?

Large-scale e-commerce systems like Flipkart have hundreds of cross-cutting workflows that span many microservices (e.g., order processing, return flows, payment reconciliation). Each workflow involves:
- Conditional branching based on business rules
- Calling multiple downstream services in sequence or in parallel
- Retrying on transient failures
- Long-running state (hours to days)
- A/B testing of flow variants

Without a platform like Drift, each team implements their own ad-hoc orchestration — state machines in databases, Kafka chaining, manual retry logic — leading to duplication, inconsistency, and operational burden.

**Drift's value proposition**: define workflows as data (DAGs of NodeDefinitions stored in HBase), not as code. Change workflow logic without redeployment. Get durability and retry semantics for free from Temporal.

---

## Key Concepts

| Concept | Definition |
|---------|------------|
| **NodeDefinition** | A single step in a workflow. Has an HTTP endpoint to call, input/output mappings (Groovy/Mustache templates), and successor node references. |
| **WorkflowDefinition** | The DAG descriptor for a complete workflow: which nodes, what sequence, what branching conditions. |
| **WorkflowContext** | The runtime state of one workflow execution: current node, intermediate data, final result. Stored in HBase. |
| **Task Queue** | The Temporal task queue name that the worker polls. Determines which worker handles which workflows. |
| **SPI Extension Point** | Customisation points (ABTestingProvider, TokenProvider, SchedulerProvider) loaded via Java SPI. Consumer teams provide their own implementations via the `java-sdk` extension mechanism. |

---

## User Roles

| Role | Interaction |
|------|-------------|
| **Platform Team** | Deploys and operates Drift (api + worker). Manages infra, upgrades, node type library. |
| **Consumer Team** | Defines workflows via the REST API (NodeDefinition + WorkflowDefinition registration). Triggers workflow runs. |
| **Workflow Admin** | Monitors workflow execution via Temporal UI. Manually terminates/resumes stuck workflows. |

---

## Architectural Decisions

### Why Temporal?

Temporal provides durable workflow execution: if the worker crashes mid-workflow, Temporal replays the workflow history to resume from the last completed activity. This means Drift workflows are **at-least-once** for activities and **exactly-once** for workflow state transitions.

### Why HBase for workflow state?

Workflow executions generate large amounts of intermediate context data (accumulated node outputs). HBase provides low-latency reads at scale (millions of concurrent workflow executions) with a simple key-value model.

### Why Redis for cache invalidation instead of TTL-only?

NodeDefinitions are read on every activity execution (hundreds per second). Full HBase reads at this rate would be too slow. In-memory caches are used, but workflow definitions change frequently. Redis pub/sub provides near-instant cache invalidation without relying on TTLs, keeping latency low while ensuring correctness.

### Why Dropwizard?

Dropwizard was the Flipkart standard at the time this service was built. It provides a production-ready HTTP server with built-in metrics, health checks, and operational tooling out of the box.

---

## Known Constraints

1. **HBase schema is fixed.** Adding new columns to workflow context requires HBase schema migration — a coordinated operation.
2. **Groovy templates have security implications.** Groovy is executed in the worker for expression evaluation. Malformed templates can cause activity failures or (in worst case) code injection. Template validation at registration time is a future improvement.
3. **Redis Sentinel is not Redis Cluster.** The current Redis setup is Sentinel (HA for a single primary). It does not shard across nodes. For very high write throughput, this is a bottleneck.
4. **Worker extensions are loaded at startup.** Custom SPI implementations (placed in `/usr/share/drift-worker/extensions/`) are discovered via ServiceLoader at startup. Changes require worker restart.
