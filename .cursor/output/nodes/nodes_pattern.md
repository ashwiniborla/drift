# Drift Node Patterns

Reference documentation for all Drift node types. Each section documents the mandatory/optional fields, script-capable locations, common parameters, and a minimal JSON sample.

> **Auto-seeded from codebase models and docs.** Updated by the `node-process-existing` skill.

**Workflows processed (API: `GET /workflowDefinition/{name}?version=LATEST&enrichNodeDefinition=true`):** `order_verification_workflow`, `return_workflow`, `rfr_workflow`.

---

## Base Fields (all node types)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String | yes | Unique node definition identifier |
| `name` | String | yes | Human-readable display name |
| `type` | NodeType enum | yes | Discriminator: HTTP, GROOVY, BRANCH, INSTRUCTION, WAIT, CHILD, SUB_WORKFLOW, SUCCESS, FAILURE, DELEGATE, CONTEXT_OVERRIDE, PROCESSOR |
| `parameters` | List\<String\> | no | Runtime parameter names injected from workflow context |
| `version` | String | yes | Version string (e.g., `"1"`, `"LATEST"`, `"SNAPSHOT"`) |

---

## HTTP

**Java model**: `HttpNode.java`
**Purpose**: Execute external REST API calls with optional groovy-based URL/header/body construction and response transformation.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `httpComponents.url` | ComponentDetail | yes | yes (SCRIPT) | — | Target URL. Can be static string or groovy-computed. |
| `httpComponents.headers` | ComponentDetail | no | yes (SCRIPT) | — | Request headers map. **Must be Map&lt;String, String&gt;** (see Notes). |
| `httpComponents.queryParams` | ComponentDetail | no | yes (SCRIPT) | — | Query parameters map. |
| `httpComponents.body` | ComponentDetail | no | yes (SCRIPT) | — | Request body (map, string, or null). |
| `httpComponents.method` | HttpMethod enum | yes | no | — | GET, POST, PUT, DELETE, PATCH |
| `httpComponents.contentType` | HttpContentTypeEnum | no | no | `application/json` | Content-Type header value. |
| `httpComponents.targetClientId` | String | no | no | — | Registered HTTP client ID (if using service discovery). |
| `transformerComponents.transformer` | ScriptedComponentDetail | no | yes (SCRIPT) | — | Groovy script that transforms the HTTP response into a result map. |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| `httpComponents.url.value.data` | `_global`, `_global.nodeParameters`, `_enum_store` | Build URL dynamically |
| `httpComponents.headers.value.data` | `_global`, `_global.threadContext`, `_enum_store` | Set auth/content headers |
| `httpComponents.queryParams.value.data` | `_global`, `_global.nodeParameters` | Build query string |
| `httpComponents.body.value.data` | `_global`, `_global.nodeParameters` | Construct request body |
| `transformerComponents.transformer.value.data` | `_response`, `_global` | Extract fields from HTTP response |

### Common parameters

| Parameter | Accessed as | Typical use |
|-----------|-------------|-------------|
| `incidentId` | `_global.nodeParameters.incidentId` | Pass incident ID to API URL |
| `orderId` | `_global.nodeParameters.orderId` | Pass order ID to API |

### Sample JSON

```json
{
    "id": "get_incident_status",
    "name": "Get Incident Status",
    "type": "HTTP",
    "parameters": ["incidentId"],
    "version": "1",
    "httpComponents": {
        "url": {
            "value": { "type": "STRING", "data": "return 'http://' + _enum_store.ims.vip + '/incidents/' + _global.nodeParameters.incidentId" },
            "type": "SCRIPT"
        },
        "headers": {
            "value": { "type": "STRING", "data": "headers = [:]; headers['Content-Type'] = 'application/json'; return headers" },
            "type": "SCRIPT"
        },
        "queryParams": {
            "value": { "type": "STRING", "data": "return [:]" },
            "type": "SCRIPT"
        },
        "body": {
            "value": { "type": "STRING", "data": "return null" },
            "type": "SCRIPT"
        },
        "method": "GET",
        "contentType": "application/json"
    },
    "transformerComponents": {
        "transformer": {
            "value": { "type": "STRING", "data": "def result = [:]; result['status'] = _response?.statusResponse?.status; return result" },
            "type": "SCRIPT"
        }
    }
}
```

### Notes
- **Headers script return type**: The runtime model `HttpDetails` expects `headers` as `Map<String, String>`. The script’s return value is converted via Jackson to `HttpDetails`. If any header value is a **list/array** (e.g. `headers['Content-Type'] = ['application/json']`), you get: `Cannot deserialize value of type java.lang.String from Array value (token JsonToken.START_ARRAY) (through reference chain: HttpDetails["headers"]->LinkedHashMap["Content-Type"])`. **When creating HTTP nodes**, the headers script must assign **string** values only, e.g. `headers['Content-Type'] = 'application/json'`, never `headers['Content-Type'] = ['application/json']`. For dynamic values use `.toString()`, e.g. `headers['x_perf_test'] = (_global?.threadContext?.perfFlag ?: 'false').toString()`.
- **queryParams / body when omitted (fault while creating node)**:
  - **What happens**: The worker loads the node definition from the **Drift API / config store**, not from the local `node.json` file. When that stored definition has **no** `queryParams` (or `body`) key, the Java model has `queryParams == null`. `ClientComponentsParser.generateComponentScript()` (worker) then hits the `else` branch for that field and emits `return 'null'` (the **string** literal), so the Groovy script returns the string `"null"` for `queryParams`. Jackson then tries to deserialize that string into `Map<String, String>` and fails with: `Cannot construct instance of java.util.LinkedHashMap ... no String-argument constructor/factory method to deserialize from String value ('null') (through reference chain: HttpDetails["queryParams"])`.
  - **Fault at creation time**: (1) The node JSON that gets stored (e.g. via sync) must **always** include an explicit `queryParams` (and `body` when the API expects a map). (2) The definition the worker executes is the one in the **store**; if you only change the local file and never sync (or the store is fed from another source), the worker keeps using the old definition without `queryParams`, so the error persists.
  - **Correct creation**: In the node JSON, always add e.g. `"queryParams": { "value": { "type": "STRING", "data": "return [:]" }, "type": "SCRIPT" }` for no query params, and ensure this version is what gets written to the API/store (e.g. run `sync_workflow_nodes.py` or your publish pipeline) so the worker loads it.
- `targetClientId` is used when the service is registered in a client registry (e.g. `OMS`); it can also be a full URL (e.g. `http://10.24.0.208:80`).
- Transformer script receives the full HTTP response in `_response`. Use `?.` for safe navigation. May return `_response` as-is, `[:]`, or use dynamic script via `evaluate(script)`.
- **Body script**: Can reference `_global.&lt;nodeInstanceName&gt;` to build request from previous node outputs (e.g. `_global.approve_order_groovy?.approveRequest`).
- **Workflow source**: order_verification_workflow — headers/body from _global, queryParams as map (e.g. `[id: _global.nodeParameters.orderId, entityType: 'ORDER']`).
- **Workflow source**: return_workflow — body can reference `_global['instructionNodeId:viewResponse']?.selectedOptions` for user input from previous INSTRUCTION; body from `_global.nodeParameters.paramKey` (e.g. rippleContextBody); `contentType`: `application/x-www-form-urlencoded` for OAuth-style endpoints; `targetClientId` values: `OMS`, `fk-chore-service`, `prod-centaur`, `prod-varadhi`.
- **Workflow source**: rfr_workflow — HTTP body built entirely from `_global.nodeParameters` (state passes complex paths like `$.nodeId.outputKey`); URL with query string in script (e.g. `'...?session_id=' + _global.nodeParameters.sessionId + '&event_name=' + ...`); `targetClientId`: `prod-ch-cs-session-app`.

---

## GROOVY

**Java model**: `GroovyNode.java`
**Purpose**: Execute arbitrary Groovy scripts for data transformation, logic, or context preparation.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `transformer` | ScriptedComponentDetail | yes | yes (SCRIPT) | — | The groovy script to execute. Return value is stored in `_global` under the node's instance name. |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| `transformer.value.data` | `_global`, `_global.nodeParameters`, `_enum_store` | Data transformation, context preparation, building instruction data |

### Common parameters

| Parameter | Accessed as | Typical use |
|-----------|-------------|-------------|
| (varies) | `_global.nodeParameters.<param>` | Pass any runtime value needed by the script |

### Sample JSON

```json
{
    "id": "prepare_data",
    "name": "Prepare Data",
    "type": "GROOVY",
    "version": "1",
    "transformer": {
        "value": { "type": "STRING", "data": "return ['processed': true, 'timestamp': new Date().toString()]" },
        "type": "SCRIPT"
    }
}
```

### Dynamic script example

```json
{
    "id": "complex_transform",
    "name": "Complex Data Transform",
    "type": "GROOVY",
    "version": "1",
    "transformer": {
        "value": {
            "type": "STRING",
            "data": "def script = new File('src/main/resources/scripts/my_workflow/complex_transform.groovy').text; evaluate(script);"
        },
        "type": "SCRIPT"
    }
}
```

### Notes
- The return value of the transformer script is stored in `_global.<instanceName>`.
- For complex logic (data massaging, multiple transformations), prefer dynamic groovy scripts: `def script = new File('src/main/resources/scripts/<workflow_name>/<name>.groovy').text; evaluate(script);`
- **Workflow source**: order_verification_workflow — scripts under `scripts/order_verification/` (e.g. order_verification_validator.groovy, approve_order.groovy, workflow_complete.groovy). BRANCH rules reference `_global.<groovy_node_instance>.<key>` (e.g. `_global.order_verification_groovy_validator.approve`).
- **Workflow source**: return_workflow — script path can come from `_global?.nodeParameters?.bodyScript` (state passes script path as parameter); e.g. `def scriptPath = _global?.nodeParameters?.bodyScript; def script = new File(scriptPath).text; evaluate(script);`.
- **Workflow source**: rfr_workflow — scripts under `scripts/reschedule_workflow/`, `scripts/toa_scripts/`; same dynamic file + evaluate pattern.
- GROOVY node replaces the deprecated PROCESSOR node.

---

## BRANCH

**Java model**: `BranchNode.java`
**Purpose**: Conditional routing — evaluate groovy rules to determine the next node in the workflow.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `choices` | List\<BranchComponents\> | yes | yes (each rule is SCRIPT) | — | Ordered list of condition-target pairs. First matching rule wins. |
| `choices[].rule` | ScriptedComponentDetail | yes | yes (SCRIPT) | — | Groovy script that returns a boolean. |
| `choices[].nextNode` | String | yes | no | — | Target node ID if this rule evaluates to true. |
| `defaultNode` | String | yes | no | — | Fallback node ID when no rule matches. |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| `choices[].rule.value.data` | `_global`, `_global.nodeParameters` | Evaluate a condition (must return boolean) |

### Common parameters

| Parameter | Accessed as | Typical use |
|-----------|-------------|-------------|
| (varies) | `_global.nodeParameters.<param>` | Condition inputs |

### Sample JSON

```json
{
    "id": "check_return_eligibility",
    "name": "Check Return Eligibility",
    "type": "BRANCH",
    "version": "1",
    "choices": [
        {
            "rule": {
                "value": { "type": "STRING", "data": "return _global.oms_order_details?.isReturnable == true" },
                "type": "SCRIPT"
            },
            "nextNode": "initiate_return"
        }
    ],
    "defaultNode": "show_not_returnable"
}
```

### Notes
- Rules are evaluated in order; first `true` wins.
- Rule scripts must return a boolean.
- `defaultNode` is mandatory — it's the fallback when no rule matches.
- **Workflow source**: return_workflow — rules reference `_global['nodeInstanceName']` or `_global['instructionNodeId:viewResponse'].selectedOptions.&lt;key&gt;` for user choices from INSTRUCTION nodes (e.g. `_global['return_possible_options_view:viewResponse'].selectedOptions.possible_actions`).
- **Workflow source**: rfr_workflow — BRANCH rules use `_global.nodeInstance?.property` (optional chaining) and `_global['instructionId:viewResponse'].selectedOptions.containsKey('widget_key') && _global['...'].selectedOptions.widget_key == 'VALUE'` for widget-based routing.

---

## INSTRUCTION

**Java model**: `InstructionNode.java`
**Purpose**: Generate widgetized UI responses for user/agent interaction (WAITING state). Powers the client-side views.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `inputOptions` | List\<Option\> | yes | no (but see `possibleDynamicValues`) | — | Widget definitions (see workflow-contract skills for details). |
| `disposition` | VariableAttributeComponent | no | yes (attribute can be SCRIPT) | — | Dynamic disposition value. |
| `workflowStatus` | VariableAttributeComponent | no | yes (attribute can be SCRIPT) | — | Dynamic workflow status. |
| `layoutId` | VariableAttributeComponent | no | yes (attribute can be SCRIPT) | — | Dynamic layout ID for client rendering. |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| `disposition.attribute.value.data` | `_global` | Compute disposition dynamically |
| `workflowStatus.attribute.value.data` | `_global` | Compute workflow status dynamically |
| `layoutId.attribute.value.data` | `_global` | Compute layout ID dynamically |

### Sample JSON

```json
{
    "id": "display_status",
    "name": "Display Status",
    "type": "INSTRUCTION",
    "version": "1",
    "inputOptions": [
        {
            "id": "status_msg",
            "description": "Status Message",
            "tags": { "values": ["ehc.static_text"] },
            "instructions": [
                { "templateId": "status_template", "templateVariables": { "status": "{{prev_node.status}}" } }
            ]
        }
    ],
    "disposition": {
        "attribute": {
            "value": { "type": "STRING", "data": "return 'STATUS_DISPLAYED'" },
            "type": "SCRIPT"
        }
    }
}
```

### possibleDynamicValues (observed in workflows)

| Format | Example | Description |
|--------|---------|-------------|
| `&lt;nodeInstanceName&gt;` | `generate_reason_subreason` | Reference to full output of a previous node (entire `_global.&lt;instanceName&gt;`). |
| `&lt;nodeInstanceName&gt;.&lt;outputKey&gt;` | `get_ae_verification_status.verify_order_input`, `generate_create_return_instructions.returnCreateInstruction`, `create_refund_options_view.refundOptionsContextVar` | Reference to a specific key from a previous node's result. |
| `nodeParameters.&lt;paramKey&gt;` | `nodeParameters.sa_workflow_complete_response` | Reference to a workflow parameter (state's `parameters` map). |

### `iris_static_message` (Iris-backed static text)

Use `templateId` **`iris_static_message`** when copy is resolved via Iris.

| `templateVariables` | Required | Description |
|---------------------|----------|-------------|
| `irisKey` | yes (for this shape) | Iris lookup key, often dotted (e.g. `elixir.question.subtitle`). |
| `defaultText` | recommended | Fallback string if Iris does not resolve. |
| `params` | no | Optional map for parameterized Iris messages. |

**Legacy / alternate shapes** (only if product still requires them):

- `enum` + `defaultText` — older Iris enum style for some screens.
- **`Deprecated`**: `templateVariables.key` for `iris_static_message` — use **`irisKey`** instead. Option-level business identifiers (`Option.id`, `possibleValues[].value`, questionnaire `questions[].key`) remain `key`; only the instruction variable name changed.

Minimal example:

```json
{
  "templateId": "iris_static_message",
  "templateVariables": {
    "defaultText": "To help us plan, could you let us know your preference for the recent reschedule request",
    "irisKey": "elixir.question.subtitle"
  }
}
```

### Notes
- For complex widget structures, use the `workflow-contract-generate-new` and `workflow-contract-process-existing` skills.
- `possibleDynamicValues` in an option references a previous node's output or workflow parameters for dynamic widget generation.
- **Workflow source**: order_verification_workflow — layoutId SCRIPT returns layout IDs like `order_verification_screen`, `sa_workflow_complete_screen`.
- **Workflow source**: return_workflow — INSTRUCTION with static `inputOptions` (id, description, tags, instructions, possibleValues); `disposition` SCRIPT returns disposition strings (e.g. `TNSRVP_REJECT`, `OBD`, `PARTIAL_REFUND`, `ASC`, `RETURN_DENIED`, `INLINE_RMT`). Terminal instruction nodes use `end: true`.
- **Workflow source**: rfr_workflow — layoutId SCRIPT returns layout IDs (e.g. `rfr_date_slot_picker`, `add_notes_instruction`, `rfr_success_instructions`, `sa_workflow_complete_screen`); disposition `SA_WORKFLOW_COMPLETE`; inputOptions tags include `sa.static_text`, `sa.free_text`, `sa.button_widget`; possibleDynamicValues from GROOVY node name.

---

## WAIT

**Java model**: `WaitNode.java`
**Purpose**: Pause workflow execution based on a time duration, absolute timestamp, or external event.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `config` | WaitConfig | yes | no | — | Wait configuration (polymorphic by `waitType`). |
| `config.waitType` | WaitType enum | yes | no | — | SCHEDULER_WAIT, ABSOLUTE_WAIT, ON_EVENT |

#### SCHEDULER_WAIT config

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `duration` | long | yes | — | Wait duration in seconds |
| `executionMode` | ExecutionMode | yes | — | ASYNC or SYNC |

#### ABSOLUTE_WAIT config

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `timestamp` | long/String | yes | — | Absolute time to wait until |

#### ON_EVENT config

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| (event details) | — | yes | — | Event to wait for |

### Sample JSON (scheduler wait)

```json
{
    "id": "hold_for_15s",
    "name": "Wait for 15 seconds",
    "type": "WAIT",
    "version": "1",
    "config": {
        "waitType": "SCHEDULER_WAIT",
        "duration": 15,
        "executionMode": "ASYNC"
    }
}
```

### Notes
- SCHEDULER_WAIT is the most common; used for delays between workflow steps.
- ON_EVENT is used when the workflow should pause until an external signal arrives.

---

## CHILD

**Java model**: `ChildNode.java`
**Purpose**: Trigger a separate Temporal child workflow (async or sync). The child runs as its own workflow execution.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `executionMode` | ExecutionMode | yes | no | — | ASYNC (parent continues) or SYNC (parent waits for child). |
| `childWorkflowId` | String | yes | no | — | ID of the child workflow to invoke. |
| `childWorkflowVersion` | String | yes | no | — | Version of the child workflow (LATEST, ACTIVE, or specific). |

### Sample JSON

```json
{
    "id": "launch_child_wf",
    "name": "Launch child workflow",
    "type": "CHILD",
    "version": "1",
    "executionMode": "ASYNC",
    "childWorkflowId": "refund_child_workflow",
    "childWorkflowVersion": "LATEST"
}
```

### Notes
- Nesting CHILD inside another child workflow is not supported.
- In ASYNC mode, parent proceeds immediately without waiting for child completion.

---

## SUB_WORKFLOW

**Java model**: `SubWorkflowNode.java`
**Purpose**: Inline another workflow's nodes into the current workflow at fetch time. Synchronous — one run, one execution graph.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `subWorkflowId` | String | yes | no | — | ID of the sub-workflow to inline. |
| `subWorkflowVersion` | String | yes | no | — | Version of the sub-workflow. |
| `config` | SubWorkflowConfig | no | no | — | Inlining configuration. |
| `config.includeFirstNode` | boolean | no | no | `true` | Whether to include the sub-workflow's start node. |
| `config.includeLastNode` | boolean | no | no | `true` | Whether to include the sub-workflow's terminal node. |
| `config.errorHandlingStrategy` | ErrorHandlingStrategy | no | no | `PROPAGATE` | PROPAGATE (parent handles failures) or ISOLATE (not yet implemented). |

### Sample JSON

```json
{
    "id": "sub_validation_step",
    "name": "Run validation sub-workflow",
    "type": "SUB_WORKFLOW",
    "version": "1",
    "subWorkflowId": "validation_workflow",
    "subWorkflowVersion": "SNAPSHOT",
    "config": {
        "includeFirstNode": true,
        "includeLastNode": false,
        "errorHandlingStrategy": "PROPAGATE"
    }
}
```

### Notes
- Sub-workflow nodes are flattened recursively (nested SUB_WORKFLOW is supported).
- Circular references are rejected at fetch time.
- ISOLATE error handling is accepted but runtime behaviour is currently the same as PROPAGATE.

---

## SUCCESS

**Java model**: `SuccessNode.java`
**Purpose**: Terminal node indicating successful workflow completion.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `comment` | String | no | no | — | Optional completion message. |
| `executionMode` | ExecutionMode | no | no | — | Execution mode (if applicable). |

### Sample JSON

```json
{
    "id": "success_node",
    "name": "Workflow Completed Successfully",
    "type": "SUCCESS",
    "version": "1",
    "comment": "Success",
    "executionMode": "SYNC"
}
```

### Notes
- **`executionMode` is required** (`@NotNull`). Use `"SYNC"` unless there's a reason for `"ASYNC"`.

---

## FAILURE

**Java model**: `FailureNode.java`
**Purpose**: Terminal node indicating workflow failure.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `error` | String | yes | no | — | Error message/description. |

### Sample JSON

```json
{
    "id": "default_failure",
    "name": "Return failure node",
    "type": "FAILURE",
    "version": "1",
    "error": "Workflow completed by running default_failure"
}
```

---

## DELEGATE

**Java model**: `DelegateNode.java`
**Purpose**: Delegation node — no extra fields beyond the base.

### Fields

No additional fields beyond base fields.

### Sample JSON

```json
{
    "id": "delegate_step",
    "name": "Delegate",
    "type": "DELEGATE",
    "version": "1"
}
```

---

## CONTEXT_OVERRIDE

**Java model**: `ContextOverrideNode.java`
**Purpose**: Transform/override workflow context before continuing execution.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `transformerComponents` | TransformerComponents | yes | yes (SCRIPT) | — | Groovy script that transforms context. |

### Script locations

| Field path | Context variable | Typical usage |
|------------|-----------------|---------------|
| `transformerComponents.transformer.value.data` | `_global`, `_enum_store` | Override or enrich workflow context |

### Sample JSON

```json
{
    "id": "override_context",
    "name": "Override Context",
    "type": "CONTEXT_OVERRIDE",
    "version": "1",
    "transformerComponents": {
        "transformer": {
            "value": { "type": "STRING", "data": "def result = [:]; result['overriddenField'] = 'newValue'; return result" },
            "type": "SCRIPT"
        }
    }
}
```

---

## PROCESSOR (deprecated)

**Java model**: `ProcessorNode.java`
**Purpose**: Legacy node type. Use GROOVY instead.

### Fields

| Field | Type | Required | Script-capable | Default | Description |
|-------|------|----------|----------------|---------|-------------|
| `instructionNodeRef` | String | no | no | — | Reference to an instruction node. |

### Notes
- **Deprecated**. Use GROOVY node for all new logic/transformation nodes.

---

## Component Detail Types Reference

### ScriptedComponentDetail (type: "SCRIPT")

```json
{
    "value": { "type": "STRING", "data": "<groovy script string>" },
    "type": "SCRIPT"
}
```

### StaticComponentDetail (type: "STATIC")

```json
{
    "value": { "type": "STRING", "data": "<literal value>" },
    "type": "STATIC"
}
```

### Value types

| Type | Description |
|------|-------------|
| `STRING` | Plain string value |
| `MAP` | Map/dictionary |
| `JSON` | JSON structure |
| `BOOLEAN` | Boolean flag |

---

## Dynamic Script Pattern

When logic is complex, use an external `.groovy` file loaded via `evaluate()`:

```json
"value": {
    "type": "STRING",
    "data": "def script = new File('src/main/resources/scripts/<workflow_name>/<script_name>.groovy').text; evaluate(script);"
},
"type": "SCRIPT"
```

Script files are stored at: `src/main/resources/scripts/<workflow_name>/<script_name>.groovy`

**When to use dynamic scripts**:
- Complex data massaging (multiple field extractions, transformations)
- Logic exceeding ~10 lines
- Reusable across multiple nodes
- Needs proper code review and version control
