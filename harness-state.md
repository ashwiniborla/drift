pipeline-stage: VALIDATED
task-type: DESIGN_FIRST
feature-branch: feature/retry-timeout-config
confluence-parent-page: 474522469
confluence-base-url: https://flipkart.atlassian.net/wiki
confluence-review: ENABLED
jira-integration: SKIP
github-integration: SKIP
sonar-project-key: SKIP
feature-tag: retry-timeout-config

prd-confluence-page-id: 474620696
prd-confluence-page-url: https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=474620696
prd-local-path: harness-docs/design/active/retry-timeout-config-prd-expanded.md

hld-confluence-page-id: 474588363
hld-confluence-page-url: https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=474588363
hld-local-path: harness-docs/design/active/retry-timeout-config-hld.md

lld-confluence-page-id: 475268668
lld-confluence-page-url: https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=475268668
lld-local-path: harness-docs/design/active/retry-timeout-config-lld.md

plan-local-path: harness-docs/design/active/retry-timeout-config_execution_plan.md
till-done-path: _till_done.json
last-updated-by: validate

confluence-demo-page: 476158724
confluence-demo-page-url: https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=476158724

stage-completion-log:
  - 2026-05-21 validate: BLOCKED — Static checks PASS (build ok for java-sdk/commons/worker; 11 unit tests pass including 7 ActivityOptionsBuilderTest + 4 WorkerArchTest; activityOptionsV1 references in WorkflowNodeExecutor.java = 0). Runtime BLOCKED — HBase not reachable at localhost:2181 / :16020, worker fails Guice provisioning with RetriesExhaustedException. Pre-existing infra prerequisite (per user memory: one-time HBase setup needed). Pre-existing test-compile failure in api/ArchTest (missing junit-jupiter dep) — unrelated to this feature.
  - 2026-05-21 validate: LGTM — HBase up (ZK :2181, RS :16020). boot.sh --skip-build PASS, API healthy :8001, Worker healthy :7201. health.sh all OK (Temporal, Redis, HBase reachable). query-logs drift-worker ERROR last 5m = 0 entries. query-logs drift-worker all last 5m shows clean boot: ZK session established, HBase scans (NodeDefinition + WorkflowDefinition) OK, Temporal Worker started, Redis cache invalidator subscribed, Jetty serving on :7200/:7201. activityOptionsV1 references in worker/.../workflows/WorkflowNodeExecutor.java = 0. No ProvisionException, no HBase RetriesExhaustedException.
