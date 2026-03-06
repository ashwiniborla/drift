---
name: node-generate-new
description: Generates new Drift node definitions (JSON) interactively by gathering requirements, validating against known patterns, and producing node.json + description.md + request MD (resume_request.md for INSTRUCTION nodes). Triggers groovy script generation (node-groovy-generate skill) when scripts are needed. Use when the user asks to create a new node, add a workflow step, or needs a node definition JSON.
---

# Generate New Node Definitions

## Goal

Generate a new node definition (JSON) while:

- **reusing existing patterns** from `nodes_pattern.md`
- asking targeted questions to fully understand requirements
- producing documentation alongside the node (`description.md`, and for INSTRUCTION nodes **resume_request.md** with 2–3 scenarios)
- when adding to a workflow, ensuring the workflow has **start_request.md** (generate if missing)
- triggering groovy script generation when needed

## Read/write paths

| Purpose | Path |
|---------|------|
| **Patterns file** (read only) | `.cursor/output/nodes/nodes_pattern.md` |
| **Node output dir** (write) | `.cursor/output/nodes/<node_name>/node.json` |
| **Node docs** (write) | `.cursor/output/nodes/<node_name>/description.md` |
| **Instruction resume request MD** (write, INSTRUCTION only) | `.cursor/output/nodes/<node_name>/resume_request.md` |
| **Workflow start request MD** (write, when adding to workflow) | `.cursor/output/workflows/<workflow_name>/start_request.md` |

**Naming**: Always use **snake_case** for node name and node ID (e.g., `questionnaire_instructions`, `prepare_questionnaire_instructions`).

**Request MD files** (for API/contract documentation):
- **Workflow level**: Each workflow has a **start_request.md** — documents the request to start the workflow (payload, params, sample).
- **Instruction node level**: Each INSTRUCTION node has a **resume_request.md** — documents the resume request with **2–3 possible scenarios** (e.g. user selects option A, option B, timeout/no response).

| Purpose | Path |
|---------|------|
| **Drift docs** (read-only reference) | `docs/_pages/07-DSL-RECIPES.md` |
| **Node model classes** (read-only reference) | `commons/src/main/java/com/flipkart/drift/commons/model/node/*.java` |
| **Workflow context (optional)** | `worker/src/main/resources/scripts/<workflow_name>/context.json` — variable names and semantics; use **only when required** (e.g. parameter names, option keys). Do not force every node to depend on context. |

## Always read these references first

1. `.cursor/output/nodes/nodes_pattern.md`
2. `docs/_pages/07-DSL-RECIPES.md`

## Requirements gathering (keep asking until user says "generate")

Collect the following information through questions. Do **not** assume any value.

### Step 1: Basic identity

- **Node type**: Which type? (HTTP, GROOVY, BRANCH, INSTRUCTION, WAIT, CHILD, SUB_WORKFLOW, SUCCESS, FAILURE, CONTEXT_OVERRIDE, DELEGATE)
- **Node ID**: Unique identifier — **always snake_case** (e.g., `questionnaire_instructions`, `did_fetch_order_details`). Suggest convention: `<use_case>_<action>`.
- **Node name**: Human-readable name — **always snake_case** for the node definition (folder and `id`/`name` fields).
- **Version**: Typically `"1"` for new nodes

### Step 2: Parameters

- **Parameters needed**: What runtime values does this node need?
- For each parameter, clarify:
  - Name (e.g., `orderId`, `incidentId`)
  - Source: workflow context — use **JsonPath-like expressions** in the workflow state’s `parameters` map:
    - Workflow start params: `$.params.x`
    - Top-level context (e.g. order/issue): `$.orderDetails[0].orderId`, `$.issueDetail.issueId`
    - Previous node output: `$.<instanceName>.<field>` (e.g. `$.process_fake_workflow_details.flowDirection`)
  - In Groovy the value is available as: `_global.nodeParameters.<paramName>`
  - **When the workflow has a context file**: If `scripts/<workflow_name>/context.json` exists (e.g. `questionnaire_workflow/context.json`), prefer variable names and keys from it **only when they are relevant** to this node (e.g. param names, option IDs). Do not force every node to reference context.

**Workflow side** — the state that runs this node must pass the parameter, e.g.:
`"parameters": { "orderId": "$.orderDetails[0].orderId" }`. See workflow-generate-new skill for the full parameter-passing sample.

### Step 3: Type-specific fields

Based on the node type selected, ask about the required fields from `nodes_pattern.md`.

#### HTTP node questions

- **Downstream / host**: What is the **downstream name** (key in `_enum_store["downstream_ips"]`)? Example: `"chore-service"`, `"oms"`. **Do not hardcode IPs or hosts** — the URL must be built from `_enum_store["downstream_ips"][<downstreamName>]` so it can change per env.
- **URL**: Path and optional query. Base host comes from `_enum_store["downstream_ips"][<downstreamName>]`; script should build full URL as e.g. `return _enum_store?.downstream_ips?[downstreamName] + path` (with safe navigation and optional default).
- **Method**: GET / POST / PUT / DELETE / PATCH
- **Content type**: application/json, form-urlencoded, etc.
- **Headers**: What headers are needed? Static or computed from context?
- **Query params**: Any query parameters? Static or computed?
- **Body**: Request body content? Static JSON or groovy-built?
- **Target client ID**: If using a registered HTTP client, which one?
- **Response transformer**: What fields from the response should be extracted? This determines the transformer script.

#### GROOVY node questions

- **Purpose**: What transformation/logic does this node perform?
- **Input data**: What context fields does it read from `_global`?
- **Output shape**: What should the return value look like? (map, list, primitive)

#### BRANCH node questions

- **Conditions**: How many branches? What is each condition?
- **Next nodes**: Which node does each branch lead to?
- **Default node**: Fallback when no condition matches?
- For each condition: what `_global` fields are checked?

#### INSTRUCTION node questions

- Defer to the **workflow-contract-generate-new** skill for widget/option details.
- Ask: layoutId, disposition, workflowStatus, inputOptions structure.
- **Resume request scenarios**: For the instruction’s **resume_request.md**, collect **2–3 possible resume scenarios** (e.g. “user selects option A”, “user selects option B”, “user does not respond / timeout”). For each scenario ask: short name, description, and what the resume request payload/response looks like (sample fields or example).

#### WAIT node questions

- **Wait type**: SCHEDULER_WAIT (duration), ABSOLUTE_WAIT (timestamp), ON_EVENT
- For SCHEDULER_WAIT: duration (seconds), execution mode (ASYNC/SYNC)
- For ABSOLUTE_WAIT: how is the timestamp determined?
- For ON_EVENT: what event is expected?

#### CHILD node questions

- **Child workflow ID**: Which workflow to invoke?
- **Child workflow version**: LATEST, ACTIVE, or specific version?
- **Execution mode**: ASYNC (parent continues) or SYNC (parent waits)?

#### SUB_WORKFLOW node questions

- **Sub-workflow ID and version**
- **Config**: includeFirstNode? includeLastNode? errorHandlingStrategy (PROPAGATE/ISOLATE)?

#### SUCCESS node questions

- **Comment**: Completion message
- **Execution mode**: If applicable

#### FAILURE node questions

- **Error message**: What error string to return?

#### CONTEXT_OVERRIDE node questions

- **Transformer**: What context transformation is needed?

### Step 4: Script requirements

For every field that is script-capable (per `nodes_pattern.md`), ask:

- **Is this value static or dynamic (computed at runtime)?**
- If dynamic:
  - Is the logic **simple** (1-5 lines)? → **in-node script** (embedded in `value.data`)
  - Is the logic **complex** (data massaging, multiple transformations, conditionals)? → **dynamic groovy script** (external `.groovy` file loaded via `evaluate()`)
- If static:
  - Use `StaticComponentDetail` with literal value

**Rule**: When lots of data massaging is required, prefer a **dynamic groovy script** (external file).

**Context (optional)**: For workflows that have `scripts/<workflow_name>/context.json`, use it to reference variable names / option keys **only when required** for this node. Do not assume every node must read from context.

### Step 5: Workflow name (for dynamic scripts)

If any dynamic groovy scripts are needed:

- Ask for the **workflow name** (e.g., `questionnaire_workflow`, `delay_in_delivery`) — used as the folder under `scripts/`
- Scripts will be stored at: `src/main/resources/scripts/<workflow_name>/`

## Generation

### HTTP node rules

- **Always include queryParams (and body if needed)**: The worker builds the executable script from the **stored** node definition (API/config store). If `queryParams` (or `body`) is **missing** from that definition, `ClientComponentsParser` emits `return 'null'` (string) for that field, and Jackson fails with: `Cannot construct instance of java.util.LinkedHashMap ... deserialize from String value ('null') (through reference chain: HttpDetails["queryParams"])`. So when **creating** an HTTP node, **always** put explicit `queryParams` in node.json, e.g. `"queryParams": { "value": { "type": "STRING", "data": "return [:]" }, "type": "SCRIPT" }`. If the node has no body or expects a map body, include a `body` script too. After editing node.json, the definition must be **synced to the API/store** (e.g. sync_workflow_nodes.py) so the worker loads the updated definition; otherwise the worker keeps using the old one and the error continues.
- **Headers script must return Map&lt;String, String&gt;**: `HttpDetails` expects `headers` as `Map<String, String>`. The script must assign **string** values only (e.g. `headers['Content-Type'] = 'application/json'`). **Never** use arrays/lists (e.g. `headers['Content-Type'] = ['application/json']`) or Jackson will fail with: `Cannot deserialize value of type java.lang.String from Array value`. For dynamic values use `.toString()`.
- **No hardcoded IPs**: **Never** put hardcoded IPs or hostnames in HTTP node URL (or headers that contain host).
- **Always** resolve the base URL from the enum store: `_enum_store["downstream_ips"][<downstreamName>]` (or `_enum_store?.downstream_ips?[downstreamName]` with safe navigation). Ask the user for the **downstream name** key if not already captured.
- URL script should build the full URL by concatenating the resolved base (e.g. `http://10.83.37.28:80`) with the path. Optionally provide a fallback or default if the key is missing.

### 1) Produce node.json

Generate the complete node definition JSON following the patterns in `nodes_pattern.md`.

For **HTTP node url** script: use `_enum_store?.downstream_ips?[downstreamName]` (or `_enum_store["downstream_ips"][downstreamName]`) for the host; do not embed IPs or hosts in the JSON.

For script fields:

- **In-node script**: Embed directly in `value.data` with `"type": "SCRIPT"`. For BRANCH rule scripts (and any script with multiple `return` statements), use **explicit newlines** (`\n`) in the JSON string so the script is multi-line; packing `} return true` on one line can cause Groovy "unexpected token: return" compilation errors.
- **Dynamic groovy script**: Use the evaluate() pattern:

```json
"value": {
    "type": "STRING",
    "data": "def script = new File('src/main/resources/scripts/<workflow_name>/<script_name>.groovy').text; evaluate(script);"
},
"type": "SCRIPT"
```

Write to: `.cursor/output/nodes/<node_name>/node.json`

### 2) Produce description.md

Write documentation to `.cursor/output/nodes/<node_name>/description.md` with this structure:

```markdown
# <Node Name>

## Overview
- **ID**: `<node_id>`
- **Type**: `<NODE_TYPE>`
- **Version**: `<version>`
- **Purpose**: <what this node does>

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| ... | ... | ... |

## Script Details

### <Field name> (in-node / dynamic)
- **Location**: embedded / `src/main/resources/scripts/<workflow_name>/<script>.groovy`
- **Context access**: `_global.<field>`, `_global.nodeParameters.<param>`, and for HTTP URL: `_enum_store["downstream_ips"][<downstreamName>]`
- **Logic summary**: <what the script does>
- **(HTTP only)** **Downstream name**: key used in `_enum_store["downstream_ips"]` for base URL (no hardcoded IP).

## Output
- **Returns**: <shape of the node's output that goes into _global>

## Dependencies
- **Reads from _global**: <list of context fields read>
- **Expected previous nodes**: <which nodes should run before this>

## Notes
- <any constraints, edge cases, or important details>
```

### 3) Produce resume_request.md (INSTRUCTION nodes only)

For **INSTRUCTION** nodes only, write `.cursor/output/nodes/<node_name>/resume_request.md` documenting the resume request with **2–3 possible scenarios**. Use the scenarios gathered in INSTRUCTION node questions.

Template:

```markdown
# Resume Request — <Node Name>

## Overview
- **Node ID**: `<node_id>`
- **Purpose**: <what this instruction presents and what resume is for>

## Resume scenarios

Document 2–3 possible ways the workflow can be resumed from this instruction.

### Scenario 1: <short name>
- **Description**: <what happens>
- **Sample request / payload**: <key fields or example JSON>
- **Outcome**: <next step or terminal state>

### Scenario 2: <short name>
- **Description**: <what happens>
- **Sample request / payload**: <key fields or example JSON>
- **Outcome**: <next step or terminal state>

### Scenario 3: <short name> (optional)
- **Description**: <what happens>
- **Sample request / payload**: <key fields or example JSON>
- **Outcome**: <next step or terminal state>

## Notes
- <any constraints, edge cases, or important details>
```

### 4) Present to user for confirmation

Show:

1. The full `node.json`
2. A summary of the `description.md`
3. For **INSTRUCTION** nodes: a summary of the `resume_request.md` (2–3 scenarios)
4. List of groovy scripts needed (if any), with:
   - Script name
   - In-node vs dynamic
   - Brief purpose

Ask user to confirm before writing files.

### 5) Do not update nodes_pattern.md

- **Do not** add generated node names, use-case names, or per-node notes to `nodes_pattern.md`.
- Keep the patterns file small and pattern-only (field definitions, script locations, conventions). Node-specific docs live in `.cursor/output/nodes/<node_name>/description.md`.

### 6) Post-confirmation: trigger groovy generation

If any dynamic groovy scripts are needed:

- Inform the user that groovy script generation is required
- Trigger the **node-groovy-generate** skill automatically (or ask user if they want to proceed now)
- Pass to the groovy skill:
  - Script purpose/logic requirements
  - Workflow name (script folder under `src/main/resources/scripts/`)
  - Script file name
  - Context fields available (`_global` fields the script can read)
  - Expected return shape
  - The node.json reference for wiring the `evaluate()` snippet

### 7) Post-confirmation: add to workflow and request MDs

After the node is generated (and groovy scripts triggered if needed):

- **Ask the user**: "Do you want to add this node to a workflow?"
- If yes:
  - Ask **which workflow** (by name). List existing workflows found under `.cursor/output/workflows/` if any. If unsure, ask for the workflow name.
  - Trigger the **workflow-generate-new** skill's "Adding a node to a workflow" flow, passing:
    - Node name / resource ID
    - Node parameters (so the user can define bindings)
  - The workflow skill will handle collecting state entry details (nextNode, parameters, end, etc.) and updating the workflow JSON.
  - **Workflow-level start_request.md**: If the workflow does not yet have `.cursor/output/workflows/<workflow_name>/start_request.md`, generate it (see workflow-generate-new skill for template) or ask the user for start-request details (payload, params, sample) and then generate it.
- If no, skip.

**Instruction nodes**: Whenever an **INSTRUCTION** node is generated, always produce `resume_request.md` with 2–3 scenarios as part of the same generation (Step 3 above).

### 8) API sync (sync_workflow_nodes.py) and node type

- The **type** in `node.json` (e.g. GROOVY, BRANCH, INSTRUCTION) must match how the node is stored on the Drift API when updating. The API’s update (PUT) merges the request into the existing node and requires the same concrete type; a type mismatch causes `ClassCastException` (e.g. BranchNode cannot be cast to GroovyNode).
- If a node was first created on the API as a **stub** (e.g. GROOVY with `return [:]`) and the local file is later changed to a different type (e.g. BRANCH, INSTRUCTION), the sync script will **skip** the update and report a type mismatch. The user must **delete that node on the API** (via DB or admin) and re-run the sync so the node is created (POST) with the correct type from the local file.
- When generating a new node, set the **type** correctly from the start so it matches the intended behaviour (BRANCH for choices, INSTRUCTION for UI, GROOVY for scripts, etc.).

## Clarification rule

If any requirement is unclear, ambiguous, or missing, **ask the user** before generating. Do not assume.

See `.cursor/skills/agents-clarify-dont-assume/SKILL.md`.
