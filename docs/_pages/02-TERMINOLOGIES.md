---
title: "Terminologies"
permalink: /02-TERMINOLOGIES/
layout: single
sidebar:
  nav: "docs"
---

# Terminologies

### 1. Native Nodes

**Native Nodes** are reusable, self-contained building blocks that represent a single unit of work within a workflow.

#### **Reusability**
A single node instance (or sub-workflow composed of nodes) can be integrated across multiple distinct workflows.

#### **Node Types**

| Node Type             | Purpose                                                                                  |
|-----------------------|------------------------------------------------------------------------------------------|
| **HTTP**              | Executes external API calls (e.g., fetching data, triggering services).                  |
| **WAIT**              | Pauses workflow execution for a specified duration or until a scheduled time.            |
| **BRANCH**            | Implements conditional logic to direct the workflow to different paths.                  |
| **GROOVY**            | Executes custom transformation and business logic using Groovy scripts.                  |
| **INSTRUCTION**       | Handles UI interactions and necessary I/O (human-in-the-loop).                     |
| **SUCCESS / FAILURE** | Terminal nodes that explicitly mark the conclusion or failure state of the workflow. |
| **CHILD**             | Invokes a single sub-workflow (currently async; parent continues).                    |
| **SUB_WORKFLOW**      | Inlines a referenced workflow into the parent at fetch time; nodes are merged into one execution graph (sync, single workflow). |

#### **SUB_WORKFLOW vs CHILD**

- **CHILD**: Starts a *separate* Temporal child workflow; the parent may continue without waiting. Two workflow runs.
- **SUB_WORKFLOW**: The referenced workflow is *flattened* into the parent at DSL fetch time. One workflow run; the sub-workflow’s nodes execute as part of the parent. Supports `includeFirstNode` / `includeLastNode` to include or skip the sub-workflow’s start and terminal nodes.

---

### 2. Domain-Specific Language (DSL)

The **Domain-Specific Language (DSL)** is the JSON specification that defines the complete structure and execution flow of a workflow.

#### **Function**
Dictates how native nodes are stitched together, orchestrating the sequence of operations.

#### **Structure**
Typically follows a **Directed Acyclic Graph (DAG)** structure, where nodes execute in a predefined, sequential order.

#### **Capability**
Supports complex, iterative use cases by allowing **explicit looping mechanisms**.

---
### 3. Workflow and Node Definition (Specs)
Workflow Definition and Node Definition are the two foundational data structures derived from the DSL that drive runtime execution.

- Workflow Spec is the definitive, version-controlled blueprint for the entire automation. It contains the complete list of constituent Node Specs and the explicit transition rules (edges) that define the execution flow.

- Node Spec is the detailed configuration object for a single unit of work (a Native Node). It holds all the runtime parameters—such as the target URL for an HTTP node, or the script for a Groovy node—that the Worker requires to execute that specific step.

In short, the Workflow Spec defines the structure and sequence, while the Node Specs define the content and parameters for every step within that structure. Both are cached for fast lookup and persisted for durability.

---
**Next**: [Architecture](/03-ARCHITECTURE-OVERVIEW/)

