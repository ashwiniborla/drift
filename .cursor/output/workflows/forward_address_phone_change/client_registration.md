# Client Registration Requirements for Drift

Two new HTTP clients need to be registered in Drift before the `forward_address_phone_change` workflow can run.

---

## 1. Chore Service (`chore`)

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Client Key   | `chore`                                    |
| `_enum_store` key | `chore.host`                          |
| Base URL (staging) | `http://10.83.37.28` (or configured via env) |
| Auth         | Bearer token via `Authorization` header    |
| Used by nodes | `chore_eligibility`, `chore_feasibility`, `chore_confirm_address` |

### URLs used

| Node                 | Method | Path                                              |
|----------------------|--------|---------------------------------------------------|
| `chore_eligibility`  | POST   | `/api/v3/eligibility/addressChangeEligibility`    |
| `chore_feasibility`  | POST   | `/api/v3/order/changeAddressFeasibility/v2`       |
| `chore_confirm_address` | POST | `/api/v3/order/changeAddressConfirm/v2`          |

### What to do in Drift config

Add to `_enum_store.clients`:
```json
{
  "chore.host": "http://<chore-service-host>"
}
```

Register `chore` as a `targetClientId` in the Drift HTTP client registry (analogous to how `e2e_cs_controller_client` is registered).

---

## 2. User Service (`user_svc`)

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Client Key   | `user_svc`                                 |
| `_enum_store` key | `user_svc.host`                       |
| Base URL (staging) | `http://<user-service-host>`         |
| Auth         | None (internal trust) or X-Auth header     |
| Used by nodes | `get_current_address`, `create_new_address` |

### URLs used

| Node                   | Method | Path                                               |
|------------------------|--------|----------------------------------------------------|
| `get_current_address`  | GET    | `/contacts/{customerId}/{deliveryAddressId}`       |
| `create_new_address`   | POST   | `/contacts/{customerId}`                           |

### What to do in Drift config

Add to `_enum_store.clients`:
```json
{
  "user_svc.host": "http://<user-service-host>"
}
```

Register `user_svc` as a `targetClientId` in the Drift HTTP client registry.

---

## Existing client used

| Client Key            | Used by node          | Notes                             |
|-----------------------|-----------------------|-----------------------------------|
| `e2e_cs_controller_client` | `fetch_order_oxford` | Already registered in Drift |
