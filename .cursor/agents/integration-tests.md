---
name: integration-tests
description: "Integration Test Suite designer — generates <feature-tag>_integration_test_suite.md from PRD + HLD + LLD. Non-blocking background agent: publishes to Confluence, creates Jira story, does NOT gate the pipeline. Tests are executed manually in PG after FLOW CI/CD deployment."
model: sonnet
color: cyan
---

## Integration Test Suite Agent

**Non-blocking background agent.** Runs in parallel with `planner` after LLD is complete. Does NOT gate the pipeline.

**When are these tests run?** After the harness completes locally (PR pushed, image built via FLOW CI/CD, deployed to PG), the team runs these integration tests manually in the playground environment.

**This agent designs test specifications, NOT code.**

---

## Phase 1: Read PRD + HLD + LLD

Load `<feature-tag>-prd-expanded.md`, `<feature-tag>-hld.md`, `<feature-tag>-lld.md`, `AGENTS.md`, `ARCHITECTURE.md`, `connections.md`, `harness-docs/TEST.md`.

Extract: functional requirements (FR-N), acceptance criteria, API contracts, class signatures, database schemas, external dependencies, sequence diagrams, existing test scenarios (LLD Section 11).

## Phase 2: Analyze Integration Points

Map every requirement to testable integration boundaries:

| Boundary | Example |
|---|---|
| API → Service | POST /score → ScorerService → response |
| Service → DAL | ScorerService → ScoreRepository → DB |
| Service → External | ScorerService → GenvoyClient → gRPC |
| Error → Fallback | External down → circuit breaker → fallback |
| Config → Runtime | Feature flag → code path switch |

## Phase 3: Generate `<feature-tag>_integration_test_suite.md`

Write to `harness-docs/design/active/`. Required sections:

1. **End-to-End Flow Tests** — full request lifecycle, preconditions, steps, expected results, feature tags
2. **API Contract Tests** — valid/invalid/unauthorized requests per endpoint from LLD
3. **Data Pipeline Tests** — data flow through components, transformations, idempotency
4. **Cross-Module Tests** — interaction respects ARCHITECTURE.md boundaries
5. **External Integration Tests** — happy path, timeout, error response per external dependency
6. **Error & Resilience Tests** — circuit breakers, fallbacks, partial failures, recovery
7. **Configuration Tests** — feature flags on/off, missing config, invalid config
8. **Security Tests** — input validation, SQL injection, XSS, auth boundaries
9. **Performance Baseline Tests** — response time thresholds, concurrent load
10. **Requirement Traceability Matrix** — every FR-N mapped to test IDs, no gaps
11. **Revision History** — changes tracked here only, never inline notes

**Rules:** Use real class/method names from LLD. Use real API contracts. Every FR must have at least one test. Include feature tag per test for VictoriaLogs verification.

## Phase 4: Publish + Jira Story + Per-Test Sub-tasks (non-blocking)

**Confluence:** Dispatch `confluence-agent PUBLISH DOC_TYPE:INTEGRATION_TESTS`. Record `confluence-test-suite-page` in harness-state.md.

**Jira umbrella story** (if `jira-epic` set, skip if `jira-test-suite-story` already set):
Dispatch `jira-agent CREATE_STORY STORY_TYPE:INTEGRATION_TESTS` — "Integration Tests: <feature-tag>", 5 points, description includes Confluence URL and note that all sub-task tests must pass before this story closes. Attach suite doc. Record `jira-test-suite-story`.

**Per-test Jira sub-tasks** — one sub-task per test case (E2E-1, API-1, DP-1, etc.):
For each test case dispatch `jira-agent CREATE_SUBTASK PARENT_KEY:<jira-test-suite-story>` with title `"<TEST-ID>: <test name>"` and description including category, requirement (FR-N), flow, expected result. On `JIRA_SUBTASK_CREATED:<key>`: update the test case entry in the suite doc with its Jira key.

**Store TEST-ID → Jira key mapping** in `harness-docs/design/active/<feature-tag>_test_jira_map.json`:
```json
{ "feature_tag": "...", "story_key": "...", "tests": { "E2E-1": "PROJ-N", "API-1": "PROJ-N+1" } }
```
Record `jira-test-map: harness-docs/design/active/<feature-tag>_test_jira_map.json` in harness-state.md.

Re-publish the suite doc to Confluence with Jira keys inline. Do NOT change `pipeline-stage`. Emit:
```
━━━ INTEGRATION TEST SUITE PUBLISHED ━━━
Confluence: <url>    Jira: <story-key> (<N> sub-tasks)
Total tests: <N>     Traceability: <N>/<N> requirements

Next: once local validate passes and PR is merged + deployed to PG, say:
  "run integration tests at <pg-base-url>"
```

---

## Phase 5: ⛔ WAIT — PG Deployment Gate

Activates when user says **"run integration tests at \<base-url\>"**.

User must confirm before providing URL:
- Local harness validate returned LGTM ✓
- PR merged to main ✓
- FLOW CI/CD deployed to playground ✓

Record `pg-base-url: <url>` in harness-state.md. Set `pipeline-stage: PG_TESTING`.

---

## Phase 6: Execute Tests Against PG

Read suite doc + Jira map. Execute every test case against `pg-base-url`.

**6A — Affected-component regression first:** Read affected components from HLD. Run their existing integration tests from `harness-docs/TEST.md` / `connections.md` against PG. Log PASS/FAIL per component.

**6B — Feature test suite:** For each test case (in category order): read Steps + Expected Result → execute via `curl` / `grpc_cli` / appropriate tool → verify status + response fields → record `PASS` or `FAIL:<reason>`.

**6C — Results summary:**
```
━━━ PG INTEGRATION TEST RESULTS ━━━
Base URL: <pg-base-url>    Feature: <feature-tag>

Affected-component regression: <component>: PASS/FAIL
Feature tests: <N> passed, <N> failed
Failed: <TEST-ID>: <reason>
```

---

## Phase 7: Close Jira Sub-tasks on Pass

Read `_test_jira_map.json`. For each `PASS` test:
- Dispatch `jira-agent CLOSE_STORY ISSUE_KEY:<test-jira-key>` with comment `"PASS — executed against <pg-base-url> on <date>. <one-line result>"`

For each `FAIL` test:
- Dispatch `jira-agent ADD_COMMENT ISSUE_KEY:<test-jira-key>` with `"FAIL — <reason>. Needs investigation."`

**If ALL tests pass:** Close the umbrella story — dispatch `jira-agent ADD_COMMENT` then `CLOSE_STORY` on `jira-test-suite-story`. Set `pipeline-stage: PG_VALIDATED`.

**If any FAIL:** Leave umbrella open. Emit list of failed tests + their Jira keys. User fixes + redeploys, then says "re-run integration tests at <url>". On re-run: resume Phase 6, skip tests whose sub-task is already `CLOSED`.

---

## Anti-Patterns

- Never write test code — design specs only; execution is via curl/cli in Phase 6
- Never skip the traceability matrix — every FR must be covered
- Never use placeholder names — reference real LLD classes
- Never skip affected-component regression — a broken dependency is a broken deploy
- Never add inline notes — Revision History only
- Never close the umbrella story if any sub-tasks are still open
- Never re-create Jira sub-tasks on re-run — check the map and skip already-CLOSED ones
