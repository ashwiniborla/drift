---
name: node-process-existing
description: Processes existing Drift node definitions (JSON) to extract and document patterns, mandatory/optional fields, script usage, and parameter conventions per node type. Updates drift/.cursor/output/nodes/nodes_pattern.md. Use when the user shares an existing node JSON, asks to document a node type, or wants to onboard a node definition into the reference set.
---

# Process Existing Node Definitions

## Goal

Turn existing node definition JSON samples into **stable, reusable pattern documentation** in `nodes_pattern.md`, preventing field/pattern drift across node types.

## Read/write paths

| Purpose | Path |
|---------|------|
| **Patterns file** (read + write) | `.cursor/output/nodes/nodes_pattern.md` |
| **Drift docs** (read-only reference) | `docs/_pages/06-DSL-CONTRACTS.md`, `docs/_pages/07-DSL-RECIPES.md` |
| **Node model classes** (read-only reference) | `commons/src/main/java/com/flipkart/drift/commons/model/node/*.java` |
| **Component models** (read-only reference) | `commons/src/main/java/com/flipkart/drift/commons/model/clientComponent/*.java` |
| **Component detail models** (read-only reference) | `commons/src/main/java/com/flipkart/drift/commons/model/componentDetail/*.java` |

## Always read these references first

1. `.cursor/output/nodes/nodes_pattern.md` (may not exist yet — create if missing)
2. `docs/_pages/07-DSL-RECIPES.md` for canonical JSON examples
3. The Java model class for the node type being processed (e.g., `HttpNode.java`, `GroovyNode.java`)

## Inputs expected from user

At least one of:

- A **node definition JSON** (full or partial)
- A **node type name** (e.g., "HTTP", "GROOVY") — agent will read the Java model + docs to extract the pattern
- Notes about custom fields, parameters, or script usage

## Workflow

### 1) Identify the node type

From the JSON `"type"` field or user-provided type name, map to one of:

| Type | Java Model |
|------|------------|
| `HTTP` | `HttpNode.java` |
| `GROOVY` | `GroovyNode.java` |
| `BRANCH` | `BranchNode.java` |
| `INSTRUCTION` | `InstructionNode.java` |
| `WAIT` | `WaitNode.java` |
| `CHILD` | `ChildNode.java` |
| `SUB_WORKFLOW` | `SubWorkflowNode.java` |
| `SUCCESS` | `SuccessNode.java` |
| `FAILURE` | `FailureNode.java` |
| `DELEGATE` | `DelegateNode.java` |
| `CONTEXT_OVERRIDE` | `ContextOverrideNode.java` |
| `PROCESSOR` | `ProcessorNode.java` (deprecated) |

### 2) Extract the pattern

For the given node type, document:

#### Base fields (all node types)

| Field | Required | Description |
|-------|----------|-------------|
| `id` | yes | Unique node definition ID |
| `name` | yes | Human-readable name |
| `type` | yes | NodeType enum value |
| `parameters` | no | List of runtime parameter names |
| `version` | yes | Version string |

#### Type-specific fields

Read the Java model class and the provided JSON to extract:

- **Field name**
- **Type** (String, enum, nested object, list, etc.)
- **Required or optional** (check `@NotNull`, `@Valid`, defaults, Jackson annotations)
- **Description** (what it represents)
- **Script support** — is the field a `ScriptedComponentDetail` or `ComponentDetail` that can hold groovy?
- **Default value** (if any)

#### Script locations

For each field that supports groovy scripts, document:

- Whether it's typically **in-node** (embedded in `value.data`) or **dynamic** (loads external `.groovy` file via `evaluate()`)
- The context variable available: `_global` for workflow context, `_global.nodeParameters.<param>` for parameters, `_response` for HTTP response transformer, `_enum_store` for config lookups

#### Parameter conventions

- What parameters are commonly used with this node type
- How parameters are accessed in groovy: `_global.nodeParameters.<paramName>`

### 3) Check for existing entry in nodes_pattern.md

- If the node type already has an entry, **merge** new observations (don't overwrite — add new fields or update descriptions).
- If the node type is new, **add** a new section.

### 4) Write the pattern to nodes_pattern.md

Use this format per node type:

```markdown
## <NODE_TYPE>

**Java model**: `<ClassName>.java`
**Purpose**: <one-line purpose>

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| ... | ... | ... | ... | ... | ... |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| ... | ... | ... |

### Common parameters

| Parameter | Accessed as | Typical use |
|-----------|-------------|-------------|
| ... | ... | ... |

### Sample JSON

\```json
{ ... minimal valid example ... }
\```

### Notes
- <any constraints, gotchas, deprecation notices>
```

### 5) Report back to user

Provide a summary:

- Node type processed
- Fields documented (new / updated)
- Script locations found
- Any questions about unclear fields

## Clarification rule

If any field in the Java model or JSON is ambiguous, or if the user's sample contains fields not in the model, **ask the user** before documenting. Do not assume.

See `.cursor/skills/agents-clarify-dont-assume/SKILL.md`.
