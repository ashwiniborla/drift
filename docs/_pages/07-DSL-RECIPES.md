---
title: "DSL Recipes"
permalink: /07-DSL-RECIPES/
layout: single
sidebar:
  nav: "docs"
---

# Drift DSL Recipes & Guide

This guide provides detailed recipes and explanations for creating Drift DSLs, from defining individual nodes to stitching complex workflows and managing data context.

## 1. Creating Node Definitions

A Node Definition is a reusable template for a step in your workflow. Each type of node has a specific model.

**Key Concepts:**
*   **`id`**: A unique identifier for the node definition. This promotes reusability (multiple steps in a workflow can use the same definition) and uniqueness within the system.
*   **`parameters`**: A list of variable placeholders that the node expects. These values are injected at runtime from the workflow context.

### HTTP Node
Used for making REST API calls.

```json
{
    "id": "get_incident_status",
    "name": "Get Incident Status",
    "type": "HTTP",
    "parameters": [ "incidentId" ],
    "version": "1",
    "httpComponents": {
        "url": {
            "value": {
                "type": "STRING",
                "data": "return 'http://api.example.com/incidents/' + _global.nodeParameters.incidentId"
            },
            "type": "SCRIPT"
        },
        "headers": {
            "value": {
                "type": "STRING",
                "data": "headers = [:]; headers['Content-Type'] = 'application/json'; return headers"
            },
            "type": "SCRIPT"
        },
        "method": "GET",
        "contentType": "application/json"
    },
    "transformerComponents": {
        "transformer": {
            "value": {
                "type": "STRING",
                "data": "def result = [:]; result['status'] = _response?.statusResponse?.status; return result"
            },
            "type": "SCRIPT"
        }
    }
}
```

### GROOVY Node
Used for data transformation and logic execution.

```json
{
    "id": "prepare_data",
    "name": "Prepare Data",
    "type": "GROOVY",
    "version": "1",
    "transformer": {
        "value": {
            "type": "STRING",
            "data": "return ['processed': true, 'timestamp': new Date().toString()]"
        },
        "type": "SCRIPT"
    }
}
```

### BRANCH Node
Used for conditional routing.

```json
{
    "id": "check_return_eligibility",
    "name": "Check Return Eligibility",
    "type": "BRANCH",
    "version": "1",
    "choices": [
        {
            "rule": {
                "value": {
                    "type": "STRING",
                    "data": "return _global.oms_order_details?.isReturnable == true && _global.oms_order_details?.returnWindowExpired == false"
                },
                "type": "SCRIPT"
            },
            "nextNode": "initiate_return"
        },
        {
            "rule": {
                "value": {
                    "type": "STRING",
                    "data": "return _global.oms_order_details?.isReturnable == true && _global.oms_order_details?.returnWindowExpired == true"
                },
                "type": "SCRIPT"
            },
            "nextNode": "show_policy_exception"
        }
    ],
    "defaultNode": "show_not_returnable"
}
```

### WAIT Node
Used to pause workflow execution based on a wait configuration.

#### Scheduler Wait (duration-based)

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




### CHILD Node
Used to invoke another workflow (sub-workflow).

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



Notes:
- Nesting Child inside another child workflow is not supported.
- In ASYNC mode, parent proceeds without waiting for child completion.

### SUB_WORKFLOW Node
Used to **inline** another workflow into the current workflow at fetch time. The referenced workflow’s nodes are merged into the parent’s execution graph (one run, synchronous). Use this when you want a single workflow run that logically includes steps from another workflow definition.

**Node Definition (reference):**

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

**Config:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `includeFirstNode` | Boolean | true | If false, the sub-workflow’s start node is not inlined and the chain starts at the second node — **unless** the start node is a BRANCH, in which case it is always kept (the chain still begins at the BRANCH). |
| `includeLastNode` | Boolean | true | If false, the sub-workflow’s terminal node is not inlined; the chain ends at the predecessor of the terminal. |
| `errorHandlingStrategy` | Enum | PROPAGATE | How failures inside the inlined sub-workflow are handled. See **Error handling** below. |


**Error handling**

- **PROPAGATE** (default): Any failure in the inlined sub-workflow is handled by the **parent workflow's** `defaultFailureNode`. One failure domain for the whole run. *Currently implemented.*
- **ISOLATE**: Intended to use the **sub-workflow's own** `defaultFailureNode` for failures within its scope before falling back to the parent. *Not yet implemented; setting ISOLATE is accepted but runtime behaviour is the same as PROPAGATE.*

**Workflow state (instance):**

```json
"states": {
  "do_validation": {
    "instanceName": "do_validation",
    "resourceId": "sub_validation_step",
    "resourceVersion": "1",
    "type": "SUBWORKFLOW",
    "nextNode": "after_validation",
    "end": false,
    "nodeDefinition": {
      "id": "sub_validation_step",
      "name": "Run validation sub-workflow",
      "type": "SUB_WORKFLOW",
      "version": "1",
      "subWorkflowId": "validation_workflow",
      "subWorkflowVersion": "SNAPSHOT",
      "config": {
        "includeFirstNode": true,
        "includeLastNode": false
      }
    }
  }
}
```

**Notes:**
- The referenced workflow (`subWorkflowId` / `subWorkflowVersion`) must exist and be fetchable; it is flattened recursively (nested SUB_WORKFLOW nodes are supported).
- Circular references (e.g. A → B → A) and exceeding max nesting depth are rejected at fetch time.
- If both `includeFirstNode` and `includeLastNode` are false, ensure the sub-workflow has at least one “middle” node to inline; otherwise the sub is effectively skipped (references are rewired to the sub’s successor).

### Referencing Groovy Scripts
Instead of embedding scripts directly in the JSON, you can reference external script files. This promotes better version control, code reviews, and auditability.

**Preferred Method: Packaged in JAR**
Scripts should be developed in a repository and packaged into the worker's JAR file (e.g., `src/main/resources/scripts/`).

```json
"transformer": {
    "value": {
        "type": "STRING",
        "data": "def script = new File('src/main/resources/scripts/my_complex_logic.groovy').text; evaluate(script);"
    },
    "type": "SCRIPT"
}
```

> **Note**: While we are exploring remote loading of scripts, packaging scripts within the JAR ensures that code is properly versioned, reviewed, and audited before deployment. For a concrete example of how to structure and package these scripts, please refer to the `examples/worker-flipkart` directory in the repository.

## 2. The Instruction Node & Widgets

The **Instruction Node** is a powerful component used to power client-side widgets and UI elements. It defines what the user sees and how they can interact with the workflow.

### Widget Structure (The `Option` Class)
An Instruction Node returns a list of widgets (Options). Each widget consists of:

1.  **Static Component (`Instruction`)**: Defines the display message or label.
    *   **`templateId`**: A reference ID for the message template stored on the client side.
    *   **`templateVariables`**: A map of values used to resolve placeholders in the template.
    *   *Example*: Template `"Hi {{user}}, status is {{status}}"` + Variables `{"user": "Alice", "status": "Open"}` -> "Hi Alice, status is Open".

2.  **Dynamic Component (`PossibleValue`)**: Defines interactive elements.
    *   Can be a list of items for single or multi-selection.
    *   *Example*: A list of refund modes (Bank, Wallet, Card) for the user to choose from.

### Complex Widget Hierarchies
You can create complex, multi-layer widgets using a parent-child hierarchy.
*   *Recursion*: Selecting a value in a parent list can trigger the display of a child list.
*   *Example*: Selecting "Issue Type" (Parent) -> opens "Sub-Issue Type" (Child).

### Layout Configuration
The Instruction Node includes a **`layoutId`**. This acts as a reference to a specific page design or configuration on the client side. Clients use this ID to determine:
*   Orientation (vertical/horizontal)
*   Visibility rules
*   Grouping of widgets
*   Styling and theming

### Recipes: Creating Views

#### Static Text View
To display simple information without input.

```json
{
    "id": "info_display",
    "type": "INSTRUCTION",
    "inputOptions": [
        {
            "id": "status_msg",
            "description": "Status Message",
            "tags": { "values": ["ehc.static_text"] },
            "instructions": [
                {
                    "templateId": "status_template",
                    "templateVariables": { "status": "{{prev_node.status}}" }
                }
            ]
        }
    ]
}
```

#### Dynamic Options via Groovy
You can generate the entire `Option` structure dynamically using a Groovy node and plug it into an Instruction node using `possibleDynamicValues`.

**Step 1: Groovy Node (`prepare_instruction_data`)**
```groovy
// script content
def widget = [
    [
        "id": "dynamic_list",
        "description": "Select an option",
        "instructions": [
            ["templateId": "select_msg", "templateVariables": [:]]
        ],
        "possibleValues": [
            ["displayValue": "Option A", "value": "A"],
            ["displayValue": "Option B", "value": "B"]
        ]
    ]
]
return widget
```

**Step 2: Instruction Node**
```json
{
    "id": "show_dynamic_options",
    "type": "INSTRUCTION",
    "inputOptions": [
        {
            "possibleDynamicValues": "prepare_instruction_data" // Reference to Groovy node output
        }
    ]
}
```

## 3. Stitching the Workflow

A Workflow Definition ties multiple node instances together.

### Instance vs. Definition
*   **`nodeDefinition`**: The blueprint (class).
*   **`instanceName`**: A specific instance of that node in the workflow (object).
*   *Why?* A workflow can have multiple instances of the *same* node definition. For example, you might use the "Send Email" node definition twice—once for "Welcome" and once for "Confirmation". `instanceName` gives each a unique identity.

### Parameter Mapping
Parameters allow you to inject specific data into each node instance.
*   **Workflow Params**: `$.params.incidentId` refers to a parameter passed when starting the workflow.
*   **Node Outputs**: `$.nodeX.someValue` refers to the output of a previous node named `nodeX`.

**Example**:
*   Instance 1 (`get_initial_data`): Uses `$.params.id`
*   Instance 2 (`get_details`): Uses `$.get_initial_data.detailId`

```json
"states": {
    "get_incident_status": {
        "instanceName": "get_incident_status",
        "resourceId": "get_incident_status_def", // Reference to Node Definition ID
        "type": "NODE",
        "parameters": {
            "incidentId": "$.params.incidentId" // Mapping workflow param to node param
        },
        "nextNode": "prepare_view",
        "nodeDefinition": { ... } // Can be inline or referenced
    }
}
```

## 4. Data Context & Referencing

The workflow maintains a running context called `_global`. This stores the output of every executed node.

### Accessing Data
You can access data in Groovy scripts using the `_global` object.

*   **Workflow Params**: `_global.params.incidentId`
*   **Node Output**: `_global.get_incident_status?.statusId`
    *   *Note*: Use the safe navigation operator `?.` to avoid null pointer exceptions.
*   **Nested Data**: `_global.zulu_order_details?.product?.vertical`
*   **User Input**: `_global['show_status:viewResponse']?.selectedOptions?.reason`

### Sample Runtime Context

```json
{
    "id": "WF-12345",
    "context": {
        "incidentId": "IN-98765",
        "workflowId": "WF-12345",
        "threadContext": {
            "clientId": "CLIENT_APP",
            "tenant": "TENANT_ID"
        },
        "params": {
            "incidentId": "IN-98765"
        },
        "get_incident_status": {
            "statusId": 219,
            "statusName": "Solved",
            "statusType": "RESOLVED"
        },
        "prepare_instruction_data": [
            {
                "id": "instruction_widget",
                "instructions": [
                    {
                        "templateId": "status_msg",
                        "templateVariables": {
                            "status_name": "Solved"
                        }
                    }
                ]
            }
        ],
        "show_status:viewResponse": {
            "selectedOptions": {
                "reason": "Quality Issue",
                "comment": "Not as expected"
            }
        }
    }
}
```

## 5. Complete Workflow Example

Here is a complete sample workflow that demonstrates fetching data, transforming it for UI, displaying it, and completing successfully.

**Important Configuration Note:**
The workflow defined below (with ID `sample_status_workflow` and version `2`) **must be configured** in your `workflow.properties` file to be executable. You map it to a specific Issue ID like this:

```properties
# In workflow.properties
workflow.289.key=sample_status_workflow
workflow.289.version=2
```

This mapping tells Drift that when a request comes for issue ID "289", it should execute version 2 of `sample_status_workflow`.

**Note on Node Referencing and Versioning:**
In this example, nodes are referenced using `resourceId` and `resourceVersion: "LATEST"`. This means any updates to the underlying node definition (e.g., changing the HTTP URL or Groovy logic) will automatically be reflected in this workflow without needing to update the workflow version. However, if you need to change the *stitching* (e.g., changing `nextNode`, adding/removing nodes, or altering parameter mappings), you must create a new version of the workflow definition.

```json
{
    "id": "sample_status_workflow",
    "startNode": "get_incident_status",
    "version": "2",
    "defaultFailureNode": "default_failure",
    "states": {
        "get_incident_status": {
            "instanceName": "get_incident_status",
            "resourceId": "get_incident_status",
            "resourceVersion": "LATEST",
            "type": "NODE",
            "parameters": {
                "incidentId": "$.params.incidentId"
            },
            "nextNode": "prepare_instruction_data",
            "end": false,
            "nodeDefinition": {
                "id": "get_incident_status",
                "name": "Get Incident Status",
                "type": "HTTP",
                "parameters": [
                    "incidentId"
                ],
                "version": "1",
                "httpComponents": {
                    "url": {
                        "value": {
                        "type": "STRING",
                        "data": "return 'http://' + _enum_store.ims.vip + '/incidents/' + _global.nodeParameters.incidentId"
                    },
                    "type": "SCRIPT"
                    },
                    "headers": {
                        "value": {
                            "type": "STRING",
                            "data": "headers = [:]\nheaders['X_IMS_USERNAME'] = 'fk-dobby'\nheaders['X_IMS_CLIENT_ID'] = 'fk-dobby'\nheaders['X_IMS_TENANT'] = 'cs'\nheaders['X_PERF_TEST'] = _global?.threadContext?.perfFlag ?: 'false'\nheaders['Content-Type'] = 'application/json'\nreturn headers"
                        },
                        "type": "SCRIPT"
                    },
                    "queryParams": {
                        "value": {
                            "type": "STRING",
                            "data": "return [:]"
                        },
                        "type": "SCRIPT"
                    },
                    "body": {
                        "value": {
                            "type": "STRING",
                            "data": "return null"
                        },
                        "type": "SCRIPT"
                    },
                    "method": "GET",
                    "contentType": "application/json"
                },
                "transformerComponents": {
                    "transformer": {
                        "value": {
                            "type": "STRING",
                            "data": "def result = [:]\nresult['statusResponse'] = _response?.statusResponse ?: [:]\nresult['statusId'] = result['statusResponse']?.id\nresult['statusName'] = result['statusResponse']?.status\nresult['statusType'] = result['statusResponse']?.statusType\nreturn result"
                        },
                        "type": "SCRIPT"
                    }
                }
            }
        },
        "prepare_instruction_data": {
            "instanceName": "prepare_instruction_data",
            "resourceId": "prepare_instruction_data",
            "resourceVersion": "LATEST",
            "type": "NODE",
            "nextNode": "show_status",
            "end": false,
            "nodeDefinition": {
                "id": "prepare_instruction_data",
                "name": "Prepare Instruction Data",
                "type": "GROOVY",
                "version": "2",
                "transformer": {
                    "value": {
                        "type": "STRING",
                        "data": "def generateASCIntruction = [[\"id\": \"asc_instruction\", \"description\": \"ASC instructions\", \"tags\": [\"values\": [\"ehc.static_text\"]], \"instructions\": [[\"templateId\": \"asc_instruction\", \"templateVariables\": [\"status_name\": _global.get_incident_status?.statusName ?: 'Unknown']]]]]; return generateASCIntruction"
                    },
                    "type": "SCRIPT"
                }
            }
        },
        "show_status": {
            "instanceName": "show_status",
            "resourceId": "display_status_instruction",
            "resourceVersion": "LATEST",
            "type": "NODE",
            "nextNode": "complete_workflow",
            "end": false,
            "nodeDefinition": {
                "id": "display_status_instruction",
                "name": "Display Status Information",
                "type": "INSTRUCTION",
                "version": "1",
                "inputOptions": [
                    {
                        "possibleDynamicValues": "prepare_instruction_data"
                    }
                ],
                "disposition": {
                    "attribute": {
                        "value": {
                            "type": "STRING",
                            "data": "return 'STATUS_DISPLAYED'"
                        },
                        "type": "SCRIPT"
                    }
                }
            }
        },
        "complete_workflow": {
            "instanceName": "complete_workflow",
            "resourceId": "success_node",
            "resourceVersion": "LATEST",
            "type": "NODE",
            "end": true,
            "nodeDefinition": {
                "id": "success_node",
                "name": "Workflow Completed Successfully",
                "type": "SUCCESS",
                "version": "1",
                "comment": "Success"
            }
        },
        "default_failure": {
            "instanceName": "default_failure",
            "resourceId": "default_failure",
            "resourceVersion": "LATEST",
            "type": "NODE",
            "end": true,
            "nodeDefinition": {
                "id": "default_failure",
                "name": "Return failure node",
                "type": "FAILURE",
                "version": "3",
                "error": "Workflow completed by running default_failure"
            }
        }
    },
    "postWorkflowCompletionNodes": []
}
```
