---
name: node-groovy-generate
description: Generates Groovy scripts for Drift workflow nodes — both in-node (embedded) scripts and dynamic (external .groovy file) scripts. Updates the referencing node.json with the evaluate() snippet when generating dynamic scripts. Use when the user needs a groovy script for a node, when node-generate-new triggers script generation, or when modifying existing node scripts.
---

# Generate Groovy Scripts for Nodes

## Goal

Generate correct, well-structured Groovy scripts for Drift workflow nodes:

- **In-node scripts**: short logic embedded directly in node JSON `value.data`
- **Dynamic scripts**: separate `.groovy` files for complex logic, loaded at runtime via `evaluate()`

## Read/write paths

| Purpose | Path |
|---------|------|
| **Patterns file** (read) | `.cursor/output/nodes/nodes_pattern.md` |
| **Node definition** (read/write) | `.cursor/output/nodes/<node_name>/node.json` |
| **Node docs** (read/write) | `.cursor/output/nodes/<node_name>/description.md` |
| **Dynamic groovy scripts** (write) | `src/main/resources/scripts/<workflow_name>/<script_name>.groovy` |
| **Drift docs** (read-only) | `docs/_pages/07-DSL-RECIPES.md` |
| **Existing groovy examples** (read-only) | `src/main/resources/scripts/` (if any exist) |

## Always read these references first

1. `.cursor/output/nodes/nodes_pattern.md` — for script location patterns per node type
2. The relevant `node.json` if this is triggered from node-generate-new
3. `docs/_pages/07-DSL-RECIPES.md` — for groovy script examples and context access patterns

## Groovy context reference

All scripts have access to these context variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `_global` | Full workflow context (all node outputs, params, thread context) | `_global.incidentId` |
| `_global.params` | Workflow start parameters | `_global.params.incidentId` |
| `_global.nodeParameters` | Current node's resolved parameters | `_global.nodeParameters.orderId` |
| `_global.<instanceName>` | Output of a previously executed node | `_global.fetch_order?.status` |
| `_global['<instanceName>:viewResponse']` | User input from an instruction node | `_global['show_options:viewResponse']?.selectedOptions` |
| `_global.threadContext` | Thread metadata (clientId, tenant, perfFlag) | `_global.threadContext?.clientId` |
| `_enum_store` | Configuration/lookup store | `_enum_store.ims.vip` |
| `_response` | HTTP response (only in HTTP node transformer) | `_response?.body`, `_response?.statusCode` |

**Safe navigation**: Always use `?.` operator to avoid NullPointerException.

## Requirements gathering

### If triggered from node-generate-new skill

The following should already be provided:

- Script purpose / logic requirements
- Workflow name (for dynamic script path)
- Script file name
- Available `_global` fields
- Expected return shape
- Whether in-node or dynamic

Confirm these with the user before proceeding. If anything is unclear, **ask**.

### If triggered independently by user

Collect:

1. **Script type**: in-node or dynamic?
   - **In-node**: for simple logic (1-5 lines, string building, simple map construction, basic conditionals)
   - **Dynamic**: for complex logic (data massaging, multiple transformations, loops, external data mapping, complex conditionals)
   - **Rule**: When lots of data massaging is required, always prefer **dynamic groovy script**

2. **Which node and field**: Which node does this script belong to? Which field? (e.g., HTTP node's `url`, `headers`, `body`, `transformer`; GROOVY node's `transformer`; BRANCH node's `rule`)

3. **Workflow name** (for dynamic scripts): e.g., `questionnaire_workflow`, `delay_in_delivery`
   - Script will be stored at: `src/main/resources/scripts/<workflow_name>/<script_name>.groovy`

4. **Logic requirements**:
   - What data does the script need to read? (which `_global` fields)
   - What transformation / computation is needed?
   - What should the script return? (map, list, string, boolean)

5. **Associated node**: Is there an existing `node.json` at `.cursor/output/nodes/<node_name>/`?

## Generation

### In-node script

For simple logic, generate a single-line or multi-line groovy string suitable for embedding in `value.data`.

**Rules**:
- Must be a valid groovy expression/script that returns a value
- Use `_global` to access context, `_global.nodeParameters.<param>` for node parameters
- Use `?.` safe navigation everywhere
- Keep it concise — if it exceeds ~10 lines, recommend switching to dynamic
- Escape newlines as `\n` when embedding in JSON
- For HTTP node fields:
  - `url`: return a String
  - `headers`: return a Map
  - `queryParams`: return a Map
  - `body`: return a Map, String, or null
  - `transformer`: return a Map (extracted/transformed response data)
- For GROOVY node `transformer`: return whatever the node should contribute to `_global`
- For BRANCH node `rule`: return a Boolean

**Output**: Show the script and the JSON snippet where it would be embedded.

### Dynamic groovy script

For complex logic, generate a standalone `.groovy` file.

**File structure**:

```groovy
/**
 * <Script name>
 * <Brief description>
 *
 * Context:
 *   _global.nodeParameters.<param> - <description>
 *   _global.<field> - <description>
 *
 * Returns: <return shape description>
 */

// Access parameters
def param1 = _global.nodeParameters?.param1
def param2 = _global.nodeParameters?.param2

// Access previous node outputs
def previousData = _global.previousNodeInstance?.someField

// --- Core logic ---

// <transformation logic here>

// Return result
return [
    key1: value1,
    key2: value2
]
```

**Rules**:
- Do NOT use `package` declaration (the script is evaluated dynamically)
- Do NOT import classes unless absolutely necessary (Groovy provides most utilities)
- Access all context through `_global` — this is the workflow context object
- Always use `?.` safe navigation
- Always include a file-level doc comment explaining purpose, context variables used, and return shape
- Handle nulls gracefully — never assume a field exists
- Return a Map, List, or primitive — the return value goes into `_global` under the node's instance name

**Write to**: `src/main/resources/scripts/<workflow_name>/<script_name>.groovy`

### Update node.json with evaluate() reference

After generating a dynamic script, update the corresponding field in the node's JSON:

```json
"<field>": {
    "value": {
        "type": "STRING",
        "data": "def script = new File('src/main/resources/scripts/<workflow_name>/<script_name>.groovy').text; evaluate(script);"
    },
    "type": "SCRIPT"
}
```

Update the file at `.cursor/output/nodes/<node_name>/node.json`.

### Update description.md

Add or update the script section in `.cursor/output/nodes/<node_name>/description.md`:

```markdown
### <Field name> (dynamic)
- **File**: `src/main/resources/scripts/<workflow_name>/<script_name>.groovy`
- **Context access**: `_global.<fields used>`
- **Logic summary**: <what the script does>
- **Returns**: <return shape>
```

## Present to user for confirmation

Show:

1. The groovy script content (full)
2. The JSON snippet for the node field (evaluate pattern or embedded)
3. File paths where everything will be written

Ask user to confirm before writing.

## Common patterns reference

### HTTP headers (in-node)

```groovy
headers = [:]; headers['Content-Type'] = 'application/json'; headers['X-Client-Id'] = _global.threadContext?.clientId ?: 'default'; return headers
```

### HTTP URL with parameter (in-node)

```groovy
return 'http://' + _enum_store.service.vip + '/api/v1/orders/' + _global.nodeParameters.orderId
```

### Response transformer (in-node)

```groovy
def result = [:]; result['orderId'] = _response?.body?.orderId; result['status'] = _response?.body?.status; return result
```

### Branch rule (in-node)

```groovy
return _global.fetch_order?.status == 'DELIVERED'
```

### Dynamic script evaluate() pattern

```groovy
def script = new File('src/main/resources/scripts/<workflow_name>/<script_name>.groovy').text; evaluate(script);
```

## Clarification rule

If the logic requirements are unclear, the return shape is ambiguous, or the context fields are unknown, **ask the user** before generating. Do not assume.

See `.cursor/skills/agents-clarify-dont-assume/SKILL.md`.
