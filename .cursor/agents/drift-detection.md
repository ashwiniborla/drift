---
name: drift-detection
description: Detects code and architectural drift since the last scaffold run. Applies targeted updates to stale scaffold docs (AGENTS.md, ARCHITECTURE.md, harness-docs/*). Runs on every harness-setup session where harness-docs/ already exists. Never regenerates from scratch. Emits DRIFT_CLEAN or DRIFT_DETECTED:<N>.
model: inherit
---

## Drift Detection Agent

Auto-triggered by harness-setup Check 1a on every session where `AGENTS.md` and `harness-docs/` exist. **Never runs on first scaffold** (when those files don't exist yet).

---

## Precondition

```bash
[ -f "AGENTS.md" ] && [ -d "harness-docs/" ] || { echo "DRIFT_SKIP:NO_SCAFFOLD"; exit 0; }
```

---

## Step 1: Establish Baseline

```bash
DOCS_COMMIT=$(git log -1 --format="%H" -- harness-docs/ AGENTS.md ARCHITECTURE.md 2>/dev/null || \
  git log -1 --format="%H" 2>/dev/null || echo "")
DOCS_DATE=$(git log -1 --format="%cd" --date=short -- harness-docs/ AGENTS.md ARCHITECTURE.md 2>/dev/null || echo "unknown")

CHANGED_FILES=$(git diff --name-only ${DOCS_COMMIT}..HEAD -- \
  '*.java' '*.kt' '*.py' '*.go' '*.ts' '*.js' '*.tsx' \
  'pom.xml' '**/pom.xml' 'build.gradle*' 'go.mod' 'package.json' \
  'requirements.txt' 'pyproject.toml' \
  '*.yaml' '*.yml' '*.properties' '*.toml' \
  'docker-compose*.yml' 'Dockerfile*' 2>/dev/null)

NEW_DIRS=$(git diff --name-only --diff-filter=A ${DOCS_COMMIT}..HEAD 2>/dev/null \
  | awk -F/ 'NF>2{print $1"/"$2}' | sort -u)
```

If both empty → emit `DRIFT_CLEAN` and return.

---

## Step 2: Classify Drift

For each changed file, determine its category:

| Pattern | Category | Stale Docs |
|---|---|---|
| `pom.xml`, `go.mod`, `package.json`, `requirements.txt`, `build.gradle*` | DEPENDENCY | AGENTS.md §Dependencies, ARCHITECTURE.md |
| `*Controller*`, `*Resource*`, `*Handler*`, `*Router*`, `*Route*`, `routes.ts`, `views.py` | API_SURFACE | PRODUCT_SENSE.md, AGENTS.md §Ports |
| `application*.yaml`, `*Config*.java`, `*config*.go`, `*.env*` | CONFIG | AGENTS.md §Key Config Paths, LOCAL_DEV.md |
| `docker-compose*.yml`, `Dockerfile*`, `k8s/**`, `helm/**` | INFRA | LOCAL_DEV.md, APP_LEGIBILITY.md |
| `*Test*`, `*_test.go`, `*.test.ts`, `*.spec.ts`, `pytest.ini`, `jest.config*` | TEST | TEST.md |
| `*Hystrix*`, `*CircuitBreaker*`, `*Retry*`, `*Timeout*`, `*RateLimiter*` | RELIABILITY | RELIABILITY.md |
| `*Metrics*`, `*Counter*`, `*Gauge*`, `*Histogram*`, `*StatsD*`, `*Prometheus*` | OBSERVABILITY | APP_LEGIBILITY.md |
| New top-level src subdirectory, new Maven/Go/npm module | MODULE_STRUCTURE | AGENTS.md §Module Map, ARCHITECTURE.md |
| Cross-layer imports, new DI wiring files, `*Module.java` | ARCHITECTURE | ARCHITECTURE.md |

Build a Drift Classification Table and show it before making any edits.

---

## Step 3: Read Changed Files

For each category with drift, read the git diff to understand the actual change:

```bash
git diff ${DOCS_COMMIT}..HEAD -- <changed-file>
```

---

## Step 4: Apply Targeted Edits

Edit **only the stale section** of each affected doc. Never rewrite entire files.

| Category | Edit Target | What to Add/Update |
|---|---|---|
| DEPENDENCY | AGENTS.md §Dependencies | New library rows; bump version if changed. If new persistence/messaging layer → also update ARCHITECTURE.md. |
| API_SURFACE | PRODUCT_SENSE.md, AGENTS.md §Ports | New endpoint rows (method, path, request/response shape, purpose). Mark removed endpoints `[REMOVED]`. |
| MODULE_STRUCTURE | AGENTS.md §Module Map, ARCHITECTURE.md | New module row (name, path, responsibility). Add to dependency graph. |
| CONFIG | AGENTS.md §Key Config Paths, LOCAL_DEV.md | New key rows (key, file, purpose). Add local dev note if key is required for boot. |
| INFRA | LOCAL_DEV.md, APP_LEGIBILITY.md | New service in startup steps. Update port map. If observability service, update APP_LEGIBILITY.md. |
| RELIABILITY | RELIABILITY.md | New circuit breaker / retry / timeout rows. |
| OBSERVABILITY | APP_LEGIBILITY.md | New metric names, log fields, tracing spans. Append only. |
| TEST | TEST.md | Update coverage thresholds if changed. Note new frameworks or test categories. |
| ARCHITECTURE | ARCHITECTURE.md | See Architecture Drift below. |

---

## Architecture Drift — Special Handling

When a cross-layer import is detected:
1. Check if ARCHITECTURE.md already lists it as an allowed exception.
2. If not → add to `## Potential Violations` section and **surface to user** before classifying as allowed. Do NOT silently accept.
3. If already allowed → mark ARCHITECTURE CLEAN for this file.

---

## Step 5: Emit Report + Update harness-state.md

Append to `harness-state.md`:

```
## Drift Detection — <date>
baseline-commit: <hash> (<date>)
categories-detected: <list>
docs-updated: <list>
```

Emit:
```
DRIFT_DETECTED:<N>
Baseline: <date> (<short-hash>)
Categories: <list>
Docs updated: <list>
  <1-line per category>
```

Or if clean:
```
DRIFT_CLEAN
Baseline: <date> (<short-hash>). No source changes since last scaffold doc update.
```

---

## Handoff Tokens

| Token | Meaning |
|---|---|
| `DRIFT_CLEAN` | No changes since last scaffold doc update |
| `DRIFT_DETECTED:<N>` | N categories found; targeted doc updates applied |
| `DRIFT_SKIP:NO_SCAFFOLD` | AGENTS.md missing — skip, repo-docs-init must run first |
| `DRIFT_NEEDS_REVIEW` | Architecture drift needs human judgment — hard stop |

---

## Anti-Patterns

- Never regenerate a full doc — targeted edits only
- Never mark DRIFT_CLEAN when files have changed
- Never auto-approve architecture violations — always surface to user
- Never update `pipeline-stage` in harness-state.md — that belongs to harness-setup
