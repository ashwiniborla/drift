---
name: workflow-generate-new
description: Generates and incrementally builds Drift workflow definition JSON interactively. Creates workflow.json under .cursor/output/workflows/<workflow_name>/. Asks for all fields, does not assume. Use when the user asks to create a new workflow, build a workflow JSON, add states to a workflow, or update a workflow definition.
---

# Generate New Workflow Definition

## Goal

Interactively build a Drift workflow definition JSON by:

- asking for every required field — **do not assume anything**
- creating and incrementally updating the workflow JSON
- supporting add/update of individual states over multiple interactions

## Read/write paths

| Purpose | Path |
|---------|------|
| **Workflow output** (write) | `.cursor/output/workflows/<workflow_name>/workflow.json` |
| **Workflow docs** (write) | `.cursor/output/workflows/<workflow_name>/description.md` |
| **Workflow start request MD** (write) | `.cursor/output/workflows/<workflow_name>/start_request.md` |
| **Node output** (read, for linking) | `.cursor/output/nodes/<node_name>/node.json` |

**Naming**: Always use **snake_case** for workflow name/ID and state keys (e.g., `questionnaire_workflow`, `process_fake_workflow_details`).

**Request MD**: Each workflow has a **start_request.md** that documents the request to start the workflow (payload, params, sample). Generate it when creating a new workflow or when adding a node to a workflow that doesn’t have it yet.

## Workflow JSON structure

```json
{
    "id": "<workflow_id>",
    "comment": "<description>",
    "startNode": "<first_state_key>",
    "version": "<version>",
    "defaultFailureNode": "<failure_state_key>",
    "states": {
        "<state_key>": {
            "instanceName": "<state_key>",
            "resourceId": "<node_definition_id>",
            "resourceVersion": "SNAPSHOT",
            "type": "NODE",
            "parameters": {},
            "nextNode": "<next_state_key>",
            "end": false
        }
    }
}
```

### Field definitions

| Field | Required | Description |
|-------|----------|-------------|
| `id` | yes | Unique workflow identifier (snake_case) |
| `comment` | yes | Human-readable description of the workflow |
| `startNode` | yes | Key in `states` that is the entry point |
| `version` | yes | Version string (e.g., `"1"`) |
| `defaultFailureNode` | yes | Key in `states` for the default failure handler |
| `states` | yes | Map of state entries (see below) |

### State entry fields

| Field | Required | Description |
|-------|----------|-------------|
| `instanceName` | yes | **Must be identical to the state key** — never a different string for “context override” |
| `resourceId` | yes | ID of the node definition this state uses |
| `resourceVersion` | yes | Use **`"SNAPSHOT"`** when adding nodes to a workflow. Other values: `"LATEST"`, `"ACTIVE"`, or a specific version. |
| `type` | yes | Always `"NODE"` |
| `parameters` | no | Map of parameter bindings — keys are param names, values are JsonPath expressions (e.g., `"$.orderDetails[0].orderId"`) or JS-like expressions (e.g., `"_global.params?.client"`) |
| `nextNode` | no | Key of the next state to execute (omit if branching is handled by the node itself) |
| `end` | yes | `true` if this is a terminal state, `false` otherwise |

**`instanceName` rule (non-negotiable)**: For every entry in `states`, `states.<key>.instanceName` **must equal** `<key>`. Output appears in context as `_global.<instanceName>`; mismatched names break JsonPath and flattening expectations.

**Important**: Do NOT include node config/definition details (scripts, HTTP config, groovy, etc.) in the workflow JSON. States only reference nodes by `resourceId`.

### Passing parameters to a node

The `parameters` map binds workflow context into the node. Keys are the node’s parameter names; values are **JsonPath-like expressions** resolved against the workflow context (e.g. `_global`). The node receives them as `_global.nodeParameters.<paramName>`.

| Source | Example expression | In node (Groovy) |
|--------|--------------------|------------------|
| Workflow start params | `$.params.orderId` | `_global.nodeParameters.orderId` |
| Top-level context (e.g. order details) | `$.orderDetails[0].orderId` | `_global.nodeParameters.orderId` |
| Previous node output | `$.process_fake_workflow_details.flowDirection` | `_global.nodeParameters.flowDirection` |
| Previous node output (nested) | `$.prepare_questionnaire_instructions.inputOptions` | `_global.nodeParameters.inputOptions` |

**Example state** — pass `orderId` from the first order in workflow context to the node:

```json
"get_order_details_ov": {
    "instanceName": "get_order_details_ov",
    "resourceId": "get_order_details_ov",
    "resourceVersion": "SNAPSHOT",
    "type": "NODE",
    "parameters": {
        "orderId": "$.orderDetails[0].orderId"
    },
    "nextNode": "get_ae_verification_status",
    "end": false
}
```

The node definition must declare `"parameters": ["orderId"]` so the runtime injects the resolved value.

## Workflow: creating a new workflow

### Step 1: Ask for workflow identity

Collect from the user (ask explicitly, do not assume):

1. **Workflow name / ID** — the `id` field. **Always use snake_case** (e.g., `questionnaire_workflow`, `token_of_apology`).
2. **Comment** — what does this workflow do?
3. **Version** — version string (suggest `"1"` for new workflows, but ask)

### Step 2: Ask for failure handling

4. **Default failure node** — what is the `defaultFailureNode`?
   - If the user doesn't have one yet, note it as a placeholder and come back to it

### Step 3: Ask for the first state

5. **Start node** — which state is the entry point (`startNode`)?
   - This must be a key in `states`, so ask the user to define it (or reference an existing node)

### Step 4: Build states incrementally

For each state the user wants to add, collect:

1. **State key** — the key in the `states` map (typically same as node name, snake_case)
2. **Resource ID** — which node definition does this state use? (same as state key if 1:1)
3. **Resource version** — use **`"SNAPSHOT"`** when adding a node to a workflow (so the workflow pins the node version). Ask only if a different version is required.
4. **Parameters** — does this state need parameter bindings?
   - If yes, for each parameter:
     - Parameter name (key)
     - Value expression (JsonPath like `$.field` or expression like `_global.params?.x`)
5. **Next node** — what state comes after this one? (omit if the node handles routing itself, e.g., branch nodes)
6. **Is terminal?** — is this an end state? (`end: true/false`)

After adding each state, ask: **"Do you want to add another state, or should I generate the workflow?"**

### Step 5: Validate and generate

Before writing:

- Verify `startNode` exists in `states`
- Verify `defaultFailureNode` exists in `states`
- Verify all `nextNode` references point to existing state keys (warn if any are missing)
- Verify at least one state has `"end": true`
- Verify **every** state has `instanceName` **exactly equal** to its key in `states` (see `.cursor/rules/drift-workflow-instancename-statekey.mdc`)

If validation finds issues, report them and ask the user how to proceed.

### Step 6: Ask for start request details (for start_request.md)

Collect from the user (or infer from workflow params):

1. **Start request purpose** — what triggers this workflow? (e.g. “Issue created”, “User initiates refund”)
2. **Request payload / params** — what does the start request contain? (e.g. `issueId`, `orderId`, `client`)
3. **Sample request** — key fields or example JSON for the start request (optional but recommended)

### Step 7: Write files

1. Write `workflow.json` to `.cursor/output/workflows/<workflow_name>/workflow.json`
2. Write `description.md` to `.cursor/output/workflows/<workflow_name>/description.md`
3. Write `start_request.md` to `.cursor/output/workflows/<workflow_name>/start_request.md`

#### description.md template

```markdown
# <Workflow Name>

## Overview
- **ID**: `<workflow_id>`
- **Version**: `<version>`
- **Start node**: `<startNode>`
- **Default failure node**: `<defaultFailureNode>`
- **Comment**: <comment>

## States

| State | Resource ID | Next Node | Terminal | Parameters |
|-------|-------------|-----------|----------|------------|
| ... | ... | ... | ... | ... |

## Flow

<Brief description of the execution flow from start to end>

## Notes
- <Any constraints, edge cases, or important details>
```

#### start_request.md template

```markdown
# Start Request — <Workflow Name>

## Overview
- **Workflow ID**: `<workflow_id>`
- **Purpose**: <what triggers this workflow / when it is started>

## Request payload / parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ... | ... | ... | ... |

## Sample request

\`\`\`json
{
  "<key>": "<example value>"
}
\`\`\`

## Notes
- <any constraints, edge cases, or important details>
```

### Step 8: Present for confirmation

Show:

1. The full `workflow.json`
2. A summary of the flow (start → ... → end)
3. A summary of `start_request.md` (purpose, main payload/params, sample)
4. Any validation warnings

Ask the user to confirm before writing files.

## Workflow: updating an existing workflow

When the user wants to add or modify states in an existing workflow:

1. Read the current `workflow.json` from `.cursor/output/workflows/<workflow_name>/workflow.json`
2. Ask what changes are needed (add state, modify state, change nextNode, update parameters, etc.)
3. Apply the changes
4. Re-run validation (Step 5 above)
5. If the workflow has no `start_request.md` yet, generate it (ask for start request details, then use the start_request.md template).
6. Show the updated JSON and ask for confirmation before writing

## Adding a node to a workflow (cross-skill integration)

When called from the **node-generate-new** skill (or when the user says "add this node to a workflow"):

1. Ask **which workflow** to add the node to (by name). List existing workflows found under `.cursor/output/workflows/` if any.
2. Read the workflow's `workflow.json`
3. **Use `resourceVersion: "SNAPSHOT"`** for every new state entry (do not use `"LATEST"` when adding nodes to a workflow).
4. Ask for the state entry details:
   - State key (suggest the node name)
   - Parameters (if the node has parameters, ask how to bind them)
   - Next node (where does flow go after this state?)
   - Is it terminal?
5. Add the state entry to `states` with `resourceVersion: "SNAPSHOT"`
6. Ask if `startNode`, `defaultFailureNode`, or any existing `nextNode` references should be updated to point to this new state
7. **start_request.md**: If `.cursor/output/workflows/<workflow_name>/start_request.md` does not exist, generate it (ask for start request payload/params/sample, then write using the start_request.md template above).
8. Show updated workflow JSON and confirm before writing

## Clarification rule

If any requirement is unclear, ambiguous, or missing, **ask the user** before generating. Do not assume.

See `.cursor/skills/agents-clarify-dont-assume/SKILL.md`.
