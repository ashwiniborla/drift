---
name: execute
description: Use when a `_till_done.json` exists and PLAN_APPROVED. Loops through ALL subtasks, implementing each on the feature branch, injecting PROBE:: markers per the Instrumentation Registry, running static gates (build, tests, coverage, lint, arch), and git-committing after each subtask. Does NOT declare done until validate LGTM is received. Verifies feature branch on entry — HARD STOP on mismatch. Implements one subtask, updates `_till_done.json`, commits, then loops; multiple `execute` instances can run in parallel for subtasks in the same layer.
model: inherit
---

## Execute

**Mode:** TILL_DONE — loops through all subtasks in `harness-docs/plans/active/<feature-tag>_till_done.json`.

Follow `coding-instructions.mdc` for logging, tests, architecture layers, and blast-radius rules.

---

## Before starting: verify feature branch

Every subtask commit must land on the branch created by `harness-setup`. Verify before writing any code.

```bash
EXPECTED=$(grep -oP 'feature-branch:\s*\K\S+' harness-state.md)
CURRENT=$(git branch --show-current)
[ -n "$EXPECTED" ] || { echo "HARD STOP: feature-branch missing — run harness-setup." >&2; exit 1; }
[ "$CURRENT" = "$EXPECTED" ] || { echo "HARD STOP: on '$CURRENT' but pipeline expects '$EXPECTED'." >&2; exit 1; }
```

Do NOT `git checkout` to fix a mismatch — surface it to the user.

## Before starting: read harness-state.md

| `pipeline-stage` | Action |
|---|---|
| `UNINITIALIZED` | Run harness-setup first. |
| `SCAFFOLDED` / `PLANNING` | Run coding-instructions to reach `IMPLEMENTING`. |
| `PLAN_APPROVED` / `IMPLEMENTING` | Normal entry — read `_till_done.json` and start loop. |
| `STATIC_VALIDATED` | All static done; orchestrator dispatches validate. |
| `VALIDATING` | validate is running — wait. |
| `VALIDATED` | Feature done; check if LGTM received. |

**State writes (execute):**
- On starting implementation: `pipeline-stage: IMPLEMENTING`, `last-updated-by: execute`.
- When all subtasks reach `STATIC_PASS`: `pipeline-stage: STATIC_VALIDATED`, `last-updated-by: execute`.
- After validate returns LGTM: `pipeline-stage: VALIDATED`, `last-updated-by: execute`.
- **Do not** set `VALIDATED` before receiving LGTM from validate.

---

## Step 1: Locate and read _till_done.json

```bash
FEATURE_TAG=$(grep "feature-tag:" harness-state.md | awk '{print $2}' | tr -d '"')
TILL_DONE="harness-docs/plans/active/${FEATURE_TAG}_till_done.json"
cat "${TILL_DONE}"
```

If `_till_done.json` is missing:
- Check if planner was run: `ls harness-docs/plans/active/${FEATURE_TAG}_execution_plan.md`
- If execution plan exists but no `_till_done.json`: emit a request for the orchestrator to dispatch `planner` to generate it.
- If no execution plan: classify task per `coding-instructions.md` — if large, request planner; if small, proceed without tracker.

---

## Step 2: Resume detection via git diff

Before implementing any subtask, check if work was already done in a previous session:

```bash
git log --oneline --all | grep "feat(${FEATURE_TAG})" | head -20
git diff --name-only HEAD~10..HEAD 2>/dev/null
```

For each subtask in `_till_done.json` with `status: "PENDING"`:

1. **Check if the subtask's files already exist / were modified** by comparing against `_till_done.json.files`:
   ```bash
   git log --oneline --all --grep="feat(${FEATURE_TAG}): complete <subtask-name>" | head -1
   ```
2. **If a commit exists for this subtask:**
   - Verify the commit SHA matches reasonable expectations
   - Update `_till_done.json`: set `status: "STATIC_PASS"`, `commit_sha: "<sha>"`
   - Log: `Resume detected: subtask <name> already completed in commit <sha>`
   - **Skip implementation** — move to next subtask
3. **If subtask files were partially modified** (git diff shows changes but no commit):
   - Resume from where it left off — do not re-implement completed parts
   - Check test status before re-running static gates

This prevents duplicate work when the agent is restarted mid-feature.

---

## Step 3: Verify plan questions are resolved

When an execution plan exists, scan it for **unresolved questions** before writing any code.

### Check 1 — Clarifications & Resolved Ambiguities table

Look for rows where `User Response` is empty, `pending`, or `TBD`:

```bash
grep -i "pending\|TBD\|UNRESOLVED\|| *|" harness-docs/plans/active/${FEATURE_TAG}_execution_plan.md \
  | grep -i "clarif\|ambigu" | head -20
```

### Check 2 — External Dependencies table

Look for entries where `User Confirmed` is `pending` or `no`:

```bash
grep -i "pending\|no" harness-docs/plans/active/${FEATURE_TAG}_execution_plan.md \
  | grep -i "depend\|service\|docker\|endpoint" | head -20
```

### Check 3 — Open Questions / Risks section

Any items not yet resolved.

**If ANY unresolved items exist — STOP and ask the user before coding:**

```
UNRESOLVED PLAN QUESTIONS — cannot start implementation

The execution plan has open items that must be resolved first:

Clarifications pending:
  1. <question from Clarifications table>

External dependencies pending:
  1. <service> — access mode not confirmed

Open questions:
  1. <question from Risks section>

Please provide answers so I can proceed. I will log your responses
in the execution plan and execution log.
```

**Wait for the user's response.** Log every answer verbatim in:
- `harness-docs/plans/active/<feature-tag>_execution_plan.md` — Clarifications table + External Dependencies table
- `harness-docs/plans/active/<feature-tag>_execution_log.md` — User Clarifications table

---

## Step 4: Implement subtasks (the main loop)

For each iteration of the loop:

### 4A: Select next subtask

Read `_till_done.json`. Find the next subtask to implement:

1. Filter subtasks where `status` is `PENDING` or `IN_PROGRESS`
2. Respect layer order: only pick subtasks whose `depends_on` entries all have `status` >= `STATIC_PASS`
3. If multiple subtasks in the same layer are ready, pick the first one (sequential within this agent; the orchestrator may dispatch parallel `execute` agents for the same layer)

### 4B: Mark subtask IN_PROGRESS

Update `_till_done.json`:
```json
{ "id": "<subtask-id>", "status": "IN_PROGRESS", "started_at": "<ISO-8601>" }
```

### 4C: Implement the subtask (including probe injection)

1. Read the subtask's details from `_till_done.json` and the execution plan
2. Read `acceptance_criteria` — these are your success conditions
3. Read the **Instrumentation Registry** from the execution plan for this subtask — it specifies which probes to inject (methods, probe types, feature tags)
4. Implement with minimal feature-tagged logs on **new** code only (`feature=<subtask-tag>`, `operation=<methodName>`)
5. **Inject PROBE:: markers** as you write new code, following the planner's Instrumentation Registry:
   - For each new public method: add ENTRY + EXIT probes
   - For each new external call: add CALL + RESULT probes
   - For each new error handler: add ERROR probe
   - Place `// PROBE::<subtask-tag>::<TYPE>` marker on the line before each injected log
   - Use `_probeStartMs` / `_probe_` prefix for timing variables
   - Use DEBUG level only — never INFO
   - Never instrument pre-existing code, getters, constructors, or delegation-only methods
   - Never log sensitive data (API keys, tokens, passwords, PII)
6. Write tests alongside code
7. Update the Instrumentation Registry in the execution plan with actual file paths and line numbers

### 4D: Run static gates

Use `git diff --name-only HEAD` to scope build/test. Map paths to modules per `AGENTS.md`.

**Sequence:**
1. Install/build affected modules (skip tests): e.g. `mvn install -DskipTests -pl <modules> -am` for Maven
2. Run tests for affected modules only
3. Coverage for affected scope
4. Lint + architecture checks

**On failure:** fix application/test code, restart static gates. If the **same** root cause fails **3 times**, stop and ask the user.

### 4E: Update _till_done.json on static pass

```json
{
  "id": "<subtask-id>",
  "status": "STATIC_PASS",
  "static_status": "PASS",
  "completed_at": "<ISO-8601>"
}
```

Also update each acceptance criterion's `met` field based on what was proven by static gates.

### 4F: Update Jira status (if enabled)

If the subtask has a `jira_key` in `_till_done.json`, transition the Jira sub-task to "Done" and add a comment with commit SHA + gate results:

```bash
JIRA_KEY=$(python3 -c "import json; data=json.load(open('${TILL_DONE}')); print(next((s.get('jira_key','') for s in data['subtasks'] if s['id']=='<subtask-id>'), ''))")
if [ -n "$JIRA_KEY" ]; then
  bash scripts/agent/jira.sh transition "$JIRA_KEY" "Done"
  bash scripts/agent/jira.sh add-comment "$JIRA_KEY" "Subtask completed. Static gates PASS. Commit: $(git rev-parse HEAD)"
fi
```

Skip silently if `jira_key` is absent or `jira-epic` is not set.

### 4G: Git commit

**⚠️ Each commit MUST be independently testable.** After this commit the repo must build, tests pass, and the feature delta is verifiable. Run the subtask's `test_command` before committing.

```bash
# Run subtask test_command to verify commit is green
TEST_CMD=$(python3 -c "import json; data=json.load(open('${TILL_DONE}')); print(next((s.get('test_command','') for s in data['subtasks'] if s['id']=='<subtask-id>'), ''))")
[ -n "$TEST_CMD" ] && eval "$TEST_CMD"  # Fix before committing if this fails

# ⚠️ NEVER use git add -A or git add . — they stage untracked local files
# (credentials, IDE config, generated artifacts) that must not be pushed.
# Stage only: (1) tracked modifications/deletions, (2) new files declared in this subtask.

# 1. Stage all tracked modifications and deletions
git add -u

# 2. Stage new files explicitly listed in this subtask's 'files' array
SUBTASK_FILES=$(python3 -c "
import json, sys
with open('${TILL_DONE}') as f:
    data = json.load(f)
for st in data['subtasks']:
    if st['id'] == '<subtask-id>':
        for fp in st.get('files', []):
            print(fp)
        sys.exit(0)
")
for f in $SUBTASK_FILES; do
  [ -f "$f" ] && git add "$f"
done

# 3. Pre-commit guard — surface any untracked files not covered by .gitignore
UNTRACKED=$(git ls-files --others --exclude-standard)
if [ -n "$UNTRACKED" ]; then
  echo "⚠️  UNTRACKED FILES (NOT staged — will not be committed):"
  echo "$UNTRACKED"
  echo "Add to subtask 'files' list in _till_done.json if these are implementation files."
  echo "Add to .gitignore if they are local config / generated / secret files."
fi

# 4. Verify something is staged
STAGED=$(git diff --cached --name-only)
[ -z "$STAGED" ] && { echo "⚠️  Nothing staged for subtask '<subtask-id>'. Check implementation." >&2; exit 1; }

git commit -m "$(cat <<'EOF'
feat(<feature-tag>): complete <subtask-name>

Subtask: <subtask-id>
Story: <jira-story-key>
Feature tag: <subtask-feature-tag>
Module: <module>
Files: <file1>, <file2>
Static: build PASS, tests PASS, coverage PASS, lint PASS, arch PASS
Test: <test_command> PASS
Till-done: <completed>/<total> subtasks complete
EOF
)"
```

Record the commit SHA in `_till_done.json`:
```bash
COMMIT_SHA=$(git rev-parse HEAD)
```
Update `_till_done.json`: `"commit_sha": "<sha>"`

### 4H: Check if a User Story is now fully static-complete → push per-story PR

After each subtask reaches `STATIC_PASS`, check if all subtasks for any User Story are now complete:

```python
import json
with open(TILL_DONE) as f:
    data = json.load(f)
static_pass_ids = {st['id'] for st in data['subtasks'] if st['status'] == 'STATIC_PASS'}
for us in data.get('user_stories', []):
    if us.get('status') in ('STATIC_COMPLETE', 'VALIDATED'):
        continue
    if all(sid in static_pass_ids for sid in us['subtask_ids']):
        print(f"USER_STORY_STATIC_COMPLETE: {us['id']} — {us['title']}")
```

If a user story is newly `STATIC_COMPLETE` and `github-integration: ENABLED`, push a PR for it now:

1. Generate PR body from `user_stories` entry: user story (`as_a` + `statement`), demo script, acceptance tests checklist, list of subtask commits
2. Write to `harness-docs/plans/active/${FEATURE_TAG}_us_<us_id>_pr_body.md`
3. `bash scripts/agent/github.sh push-branch origin`
4. `bash scripts/agent/github.sh ensure-pr "<user_story.title>" "<pr_body_path>"`
5. Update `_till_done.json`: `user_stories[id].status = "STATIC_COMPLETE"`, `pr_number`, `pr_url`, `pr_pushed_at`

Do NOT push a single feature-level PR — each User Story gets its own PR.

### 4I: Loop back

Return to Step 4A. Read `_till_done.json` again and pick the next PENDING subtask.

---

## Step 5: All subtasks complete — emit handoff for validate

When the loop in Step 4 finds no more PENDING subtasks (all are `STATIC_PASS`):

### 5A: Update _till_done.json

```json
{ "status": "ALL_SUBTASKS_COMPLETE" }
```

### 5B: Update harness-state.md

```yaml
pipeline-stage: STATIC_VALIDATED
last-updated-by: execute
```

### 5C: Emit handoff

```
ALL SUBTASKS COMPLETE — TRIGGERING VALIDATE
=============================================
Feature tag:     <feature-tag>
Till-done:       harness-docs/plans/active/<feature-tag>_till_done.json
Subtasks:        <N> complete, 0 pending
Commits:
  - <sha1>: <subtask-1-name>
  - <sha2>: <subtask-2-name>
  ...

-> validate: run full runtime validation for the complete feature.
   Execute will NOT declare done until validate returns LGTM.
```

The orchestrator (harness-setup) reads this handoff and dispatches the `validate` subagent.

---

## Step 6: Wait for validate verdict

After the orchestrator dispatches validate, wait for its verdict:

### LGTM (success)

When validate returns:
```
LGTM — RUNTIME VALIDATION PASSED
```

1. Update `_till_done.json`:
   ```json
   {
     "validate_verdict": "LGTM",
     "status": "DONE"
   }
   ```
   Also set every subtask's `validate_status` to `"PASS"`.

2. Update `harness-state.md`:
   ```yaml
   pipeline-stage: VALIDATED
   last-updated-by: execute
   ```

3. Git commit the final state (only tracked changes — `_till_done.json` update):
   ```bash
   git add -u
   git commit -m "$(cat <<'EOF'
   chore(<feature-tag>): validate LGTM — feature complete

   All subtasks validated. Runtime gates passed.
   Till-done tracker: harness-docs/plans/active/<feature-tag>_till_done.json
   EOF
   )"
   ```

4. Emit a handoff requesting the orchestrator dispatch `cleanup`. Cleanup strips probes, finalizes docs, and archives the plan.

5. Output intermediate report (cleanup will emit the final report):
   ```
   FEATURE VALIDATED — TRIGGERING CLEANUP
   ========================================
   Feature tag:     <feature-tag>
   Subtasks:        <N>/<N> VALIDATED
   Validate:        LGTM
   Commits:         <N> subtask commits + 1 validation commit
   Till-done:       harness-docs/plans/active/<feature-tag>_till_done.json

   → cleanup: strip probes, finalize docs, archive plan.
      Execute does NOT declare DONE until cleanup completes.
   ```

6. Wait for cleanup to complete. Only then is the feature truly done.

### RUNTIME VALIDATION FAILURE (fix and retry)

When validate returns a failure:
```
RUNTIME VALIDATION FAILURE
===========================
Subtask:     <failing-subtask>
Feature tag: <tag>
Gate:        <which gate failed>
Evidence:    <what was observed>
Fix required: <what needs to change>
```

1. **Do not** argue with the report — treat it as source of truth for runtime behavior
2. Update `_till_done.json`: set the failing subtask's `status` back to `IN_PROGRESS`, `validate_status` to `"FAIL"`
3. Fix **code, config, or logging** as needed
4. Re-run static gates for the affected subtask
5. On static pass: update `_till_done.json` -> `STATIC_PASS`, git commit the fix:
   ```bash
   git add -u
   git commit -m "$(cat <<'EOF'
   fix(<feature-tag>): address validate failure in <subtask-name>

   Gate: <which gate>
   Fix: <brief description>
   EOF
   )"
   ```
6. Emit a fresh `STATIC_VALIDATED` handoff for the orchestrator to re-dispatch validate
7. Repeat until LGTM or BLOCKED

### BLOCKED (escalate)

If validate reports BLOCKED (e.g., missing credentials, unreachable service):
1. Update `_till_done.json`: `"validate_verdict": "BLOCKED"`
2. Stop and present the blockage to the user — do NOT declare done

---

## Library repos

If `harness-state.md` has `repo-type: LIBRARY`:

1. Run the full subtask loop (Steps 1–4) with static gates only
2. On all subtasks `STATIC_PASS`, **⛔ prompt user to publish SNAPSHOT JAR to JFrog** (`! mvn deploy -DskipTests`). JAR version must end with `-SNAPSHOT` so re-publishes don't need version bumps.
3. **Wait for user confirmation** that JAR is published before triggering validate.
4. Update `_till_done.json`: `{ "status": "ALL_SUBTASKS_COMPLETE", "jar_published": true }`
5. Trigger validate — which boots the consumer app repo (`library-app-repo` from `harness-state.md`) and runs the full Docker validate loop there.

---

## Static-only phase tracker (per subtask)

```
EXECUTE AGENT — per-subtask checklist:
[ ] _till_done.json read and next subtask identified
[ ] Resume check via git diff (skip if already committed)
[ ] All plan questions resolved (clarifications, external deps)
[ ] Subtask scope read from _till_done.json + execution plan
[ ] Code + tests + probes injected (per Instrumentation Registry)
[ ] Build passes (affected modules)
[ ] Tests pass (affected modules)
[ ] Coverage meets threshold on new code
[ ] Lint passes (0 errors)
[ ] Arch rules pass (0 violations)
[ ] _till_done.json updated (status: STATIC_PASS, commit_sha)
[ ] Git commit created with structured message
[ ] Loop continues to next subtask
```

---

## Resolve ambiguities (implementation)

Even after plan questions are resolved, new ambiguities may surface during implementation. Ask the user immediately — do not guess. Log Q&A in `harness-docs/plans/active/<feature-tag>_execution_log.md` and execution plan Clarifications section.

---

## Anti-patterns

- Never run `docker compose`, `boot.sh`, `verify-pipeline.sh`, or `query-logs.sh` for **service** validation — that is validate
- Never declare "DONE" without validate LGTM (for service repos)
- Never remove `PROBE::` markers — only cleanup strips probes after validation passes
- Always inject probes per the Instrumentation Registry in the execution plan — do not skip probes or add unauthorized ones
- Never skip the git commit after a subtask — the commit SHA is the resume anchor
- Never implement a subtask whose `depends_on` entries are not yet `STATIC_PASS`
- Never re-implement a subtask that git diff shows was already committed
- Never update `_till_done.json` with `STATIC_PASS` before all static gates actually pass
- Never set `validate_verdict: "LGTM"` yourself — only validate sets this
- Execute pushes one PR per User Story (when all the story's subtasks reach `STATIC_PASS`), not per individual subtask and not one giant feature PR. The push happens at story completion, not at validate LGTM — validate's job is to post LGTM evidence on the existing per-story PRs.
- Subtasks with `source: "github-pr-comment"` (injected by `pr-review`) are normal subtasks — implement, commit, let validate run. validate posts the PR summary comment after LGTM.
