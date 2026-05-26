# Push to Varadhi

## Overview
- **ID**: `push_to_varadhi`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Generic node for pushing messages to a Varadhi destination via the Varadhi REST API. Destination type is controlled by `isQueue` (`true` for queue, `false` for topic) and mapped internally to `queues`/`topics` URL segments.

## Parameters

| Parameter | Required | Description | Source (typical) |
|-----------|----------|-------------|------------------|
| `groupId` | no | RESTBUS group ID; if present, set as `X_RESTBUS_GROUP_ID` header | Caller groovy node |
| `messageId` | yes | RESTBUS message ID set as `X_RESTBUS_MESSAGE_ID` | Caller groovy node (e.g. `incidentId + 4-digit random`) |
| `httpUri` | yes | Downstream HTTP URI set as `X_RESTBUS_HTTP_URI` | Caller groovy node (e.g. `_enum_store?.clients?.get('elixir.ch.host') + '/elixir/v1/tickets'`) |
| `method` | yes | HTTP method for the downstream service set as `X_RESTBUS_HTTP_METHOD` | Caller groovy node (e.g. `'POST'`) |
| `extraHeaders` | no | Additional headers as a JSON map (e.g. `{'X_CLIENT_ID': 'CX', 'X_TENANT_ID': 'ELIXIR_FK'}`) | Caller groovy node |
| `body` | yes | Request body map to post to the queue | Caller groovy node |
| `name` | yes | Varadhi destination name used in the URL path | Caller node (e.g. queue/topic name from enum or context) |
| `isQueue` | yes | Destination selector (`true` -> queue, `false` -> topic) | Caller node (literal boolean or boolean-like value) |

## Script Details

### url (in-node)
- **Location**: embedded in `httpComponents.url.value.data`
- **Context access**: `_enum_store?.clients?.get('varadhi.ch.host')`, `_global.nodeParameters.name`, `_global.nodeParameters.isQueue`
- **Logic summary**: Validates `isQueue` must be boolean-like (`true`/`false`), maps to `queues`/`topics`, and builds URL as `{varadhi.ch.host}/{queues|topics}/{name}/messages`
- **Downstream name**: `varadhi.ch.host` key in `_enum_store.clients`

### headers (in-node)
- **Location**: embedded in `httpComponents.headers.value.data`
- **Context access**: `_global.global_params.threadContext`, `_global.nodeParameters.*`, `_enum_store`
- **Logic summary**:
  1. Sets `Content-Type: application/json` and `Accept: application/json`
  2. Sets IMS tracing headers from `_global.global_params.threadContext` (`x_perf_test`, `x_ims_client_id`, `x_ims_tenant`, `x_ims_username`)
  3. Sets `X_RESTBUS_MESSAGE_ID` from `messageId` param
  4. Sets `X_RESTBUS_GROUP_ID` from `groupId` param only if non-null/non-empty
  5. Sets `X_RESTBUS_HTTP_URI` from `httpUri` param
  6. Sets `X_RESTBUS_HTTP_METHOD` from `method` param
  7. Merges `extraHeaders` map from node parameters (if non-null Map) — allows callers to inject arbitrary headers (e.g. `X_CLIENT_ID`, `X_TENANT_ID`)

### body (in-node)
- **Location**: embedded in `httpComponents.body.value.data`
- **Logic summary**: Returns `_global.nodeParameters.body` directly — body is fully prepared by the calling groovy node.

### transformer (in-node)
- **Location**: embedded in `transformerComponents.transformer.value.data`
- **Logic summary**: Returns `_response` as-is. The destination endpoint does not return application data; raw response is passed through for observability.

## Output
- **Returns**: Raw `_response` object from the Varadhi HTTP call. Stored in `_global.<instanceName>`. Downstream nodes should not rely on structured data from this node's output.

## Dependencies
- **Reads from _global**: `_global.global_params.threadContext` (IMS headers), `_global.nodeParameters.*`
- **Reads from _enum_store**: `_enum_store.clients['varadhi.ch.host']` (base URL)
- **Expected previous nodes**: A GROOVY node (e.g. `prepare_create_ticket_details`) that prepares and passes `body`, `extraHeaders`, `httpUri`, `method`, `groupId`, `messageId`, `name`, and `isQueue`

## Notes
- `targetClientId` is the static Varadhi host IP (`http://10.24.0.209:80`). This must match the auth-handled HTTP client registered for Varadhi — auth tokens are managed via the client registration, not in this node's headers.
- `groupId` is optional. If null or empty, `X_RESTBUS_GROUP_ID` is omitted entirely from the request headers.
- `extraHeaders` must be a valid JSON map (string → string). If null, it is silently skipped.
- The node always uses HTTP `POST` method (Varadhi queue ingestion is always POST).
