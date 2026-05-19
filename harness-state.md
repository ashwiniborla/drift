# Harness State

session-id: drift-harness-setup-2026-05-19
pipeline-stage: SCAFFOLDED
task-type: UNINITIALIZED
feature-branch:
feature-branch-base:
feature-branch-created-at:

## Repo Classification

repo-type: SERVICE
# Multi-module Maven monorepo: java-sdk (library), commons (library),
# api (Dropwizard HTTP :8000/:8001), worker (Dropwizard Temporal worker :7200/:7201)
# Both api and worker are runnable services with Dockerfiles.
# java-sdk and commons are library modules consumed by api and worker.

## Runtime Mode

app-runtime: local
# app-runtime: local — services are started as native JVM processes via boot.sh
# Observability stack (Vector + VictoriaLogs) runs in Docker Compose.
# External services (Temporal, HBase, Redis Sentinel) run in Rancher Desktop.

## External Dependencies

# - Temporal (gRPC :7233) — running in Rancher Desktop
# - Temporal UI (:8080) — running in Rancher Desktop
# - HBase (ZooKeeper :2181, Thrift :9090) — running in Rancher Desktop
# - Redis Sentinel (:26379, Redis :6379) — running in Rancher Desktop
# - VictoriaLogs (:9428) — Docker Compose (observability)
# - Vector — Docker Compose (log shipper)

## Integration Config

confluence-parent-page:
confluence-base-url:
confluence-review: PENDING

jira-initiative:
jira-epic:
jira-base-url:

github-integration: PENDING

sonar-project-key:
sonar-base-url:

cross-repo: SINGLE
cross-repo-paths:
  - /Users/ashwiniborla.vc/Desktop/Flipkart/drift

## Scaffold Status

# harness-docs/:              DONE — repo-docs-init complete
#   AGENTS.md:                DONE
#   ARCHITECTURE.md:          DONE
#   harness-docs/LOCAL_DEV.md:       DONE
#   harness-docs/TEST.md:            DONE
#   harness-docs/RELIABILITY.md:     DONE
#   harness-docs/PRODUCT_SENSE.md:   DONE
#   harness-docs/APP_LEGIBILITY.md:  DONE
#   harness-docs/ARCHITECTURE_RULES.md: DONE
#
# docker-compose.yml:         DONE — observability only (VictoriaLogs + Vector)
# scripts/infra/vector.yaml:  DONE
# scripts/infra/start.sh:     DONE
# scripts/infra/stop.sh:      DONE
# scripts/infra/status.sh:    DONE
#
# connections.md:             DONE — all services documented and resolved
#
# scripts/agent/check-prereq.sh: DONE
# scripts/agent/boot.sh:         DONE
# scripts/agent/health.sh:       DONE
# scripts/agent/query-logs.sh:   DONE
# scripts/agent/api-snapshot.sh: DONE
# scripts/agent/verify-pipeline.sh: DONE
#
# arch rules (ArchUnit):      DONE
#   api/src/test/.../ApiArchTest.java:         DONE
#   worker/src/test/.../WorkerArchTest.java:   DONE
#   commons/src/test/.../CommonsArchTest.java: DONE
#   java-sdk/src/test/.../JavaSdkArchTest.java: DONE
#   archunit-junit5 added to pom.xml (parent + all modules): DONE
#
# validate-guard hook:        EXISTS (scripts/agent/validate-guard.sh)
# confluence.sh:              EXISTS
# jira.sh:                    EXISTS
# github.sh:                  EXISTS
# sonar.sh:                   EXISTS
# mmdc:                       CONFIRMED (v11.15.0)
# .env.example:               DONE

## Stage Completion Log

| timestamp            | stage              | notes                                               |
|----------------------|--------------------|-----------------------------------------------------|
| 2026-05-19T00:00:00  | UNINITIALIZED      | harness-state.md created                            |
| 2026-05-19T12:00:00  | SCAFFOLD           | repo-docs-init: AGENTS.md, ARCHITECTURE.md, harness-docs/ |
| 2026-05-19T12:01:00  | SCAFFOLD           | local-infra: docker-compose.yml, vector.yaml, infra scripts |
| 2026-05-19T12:02:00  | SCAFFOLD           | connections.md: all connections resolved             |
| 2026-05-19T12:03:00  | SCAFFOLD           | app-legibility: boot.sh, check-prereq.sh, health.sh, query-logs.sh, api-snapshot.sh, verify-pipeline.sh |
| 2026-05-19T12:04:00  | SCAFFOLD           | arch-enforcer: ArchUnit tests for all 4 modules, archunit-junit5 added to poms |
| 2026-05-19T12:05:00  | SCAFFOLDED         | All scaffold stages complete                         |
