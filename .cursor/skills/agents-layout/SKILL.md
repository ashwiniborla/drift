---
name: agents-layout
description: Standard layout for solution agents and output. Use when creating or moving agents, or when the user asks about .agents/, agent output paths, or review-N.md format.
---

# Agents Layout and Output Format

This skill defines the standard directory layout for solution agents and their output in this repo. Use it whenever you create new agents, move agents from `.cursor` into the standard layout, or need to reference output paths.

## Standard Layout

| Purpose | Path |
|--------|------|
| **Agents** | `.agents/<solution>/agents/` |
| **Output** | `.agents/<solution>/output/` |

- `<solution>` is a short, kebab-case name (e.g. `sub-workflow`, `child-workflow-sync`).
- All agent markdown files for that solution live under `.agents/<solution>/agents/`.
- All generated artifacts (solution doc, review reports) live under `.agents/<solution>/output/`.

## Agent Naming Convention

- Numbered for execution order: `01-solutioning-agent.md`, `02-review-agent.md`, `03-implementation-agent.md`, etc.
- Optional: `problem-context.md` or `README.md` at `.agents/<solution>/` (sibling to `agents/` and `output/`) for requirements and how to run.

## Output Files

| File | Producer | Description |
|------|----------|-------------|
| `solution.md` | 01-solutioning-agent | Single solution document with required sections. |
| `review-1.md`, `review-2.md`, … | 02-review-agent | Numbered review + bugs report; next number = existing count + 1. |

- Review agent **must** write its full output (criteria results + bugs report) to **one** file: `.agents/<solution>/output/review-{N}.md`.
- Determine N by listing `.agents/<solution>/output/` and choosing the next available number (e.g. if `review-1.md` exists, write `review-2.md`).

## Moving Agents from .cursor to .agents

When the user asks to "move agents to the standard layout" or "follow the pattern for agents":

1. **Create directories**
   - `.agents/<solution>/agents/`
   - `.agents/<solution>/output/`

2. **Move and rename agent files**
   - From: `.cursor/agents/*.md` (or any current location)
   - To: `.agents/<solution>/agents/01-<role>-agent.md`, `02-<role>-agent.md`, …
   - Preserve content; only adjust paths and output instructions inside the files.

3. **Update paths inside each agent file**
   - Replace any reference to "output" or "review" paths with:
     - Solution document: `.agents/<solution>/output/solution.md`
     - Review report: `.agents/<solution>/output/review-{N}.md`
   - Replace any reference to "problem context" or "context" with:
     - `.agents/<solution>/problem-context.md` (if present) or `.agents/<solution>/README.md`

4. **Format agents to the standard structure**
   - **Solutioning agent:** Title "Agent 1: Solutioning Agent (<Topic>)", section "Inputs You Must Use", section "Output You Must Produce" with single file path `.agents/<solution>/output/solution.md`, and "Required Sections" (numbered list).
   - **Review agent:** Title "Agent 2: Review Agent (<Topic>)", section "Inputs You Must Use", "Review Criteria" (numbered), **"Output File"** with path `.agents/<solution>/output/review-{N}.md` and rule to determine N from existing files, then "Your Output" (ISSUES FOUND vs APPROVED) and "Rules".

5. **Add/update review agent output instruction**
   - In the review agent, add or keep an explicit **Output File** section that says: write the full review and bugs report to `.agents/<solution>/output/review-{N}.md` (with N = next number).

6. **Create or move problem context**
   - If there is a problem context or design doc, place it at `.agents/<solution>/problem-context.md` (or `.agents/<solution>/README.md`) and reference it from the agents.

7. **Optional: remove or redirect old location**
   - After move, delete or leave a short pointer in `.cursor/agents/` if the user wants to phase out the old path (e.g. "Agents moved to .agents/<solution>/agents/").

## Checklist for New Solutions

- [ ] Create `.agents/<solution>/agents/` and `.agents/<solution>/output/`.
- [ ] Add `01-solutioning-agent.md` with output path `.agents/<solution>/output/solution.md`.
- [ ] Add `02-review-agent.md` with output path `.agents/<solution>/output/review-{N}.md` and bugs report in same file.
- [ ] Add `problem-context.md` (or README) under `.agents/<solution>/` if needed.
- [ ] Ensure review agent instructions say: "Write the review and bugs report to `review-{N}.md`".
- [ ] In each agent, add the **clarification rule**: ask when unclear; do not assume. Reference `.cursor/skills/agents-clarify-dont-assume/SKILL.md`.

## Example (sub-workflow)

- Agents: `.agents/sub-workflow/agents/01-solutioning-agent.md`, `.agents/sub-workflow/agents/02-review-agent.md`
- Output: `.agents/sub-workflow/output/solution.md`, `.agents/sub-workflow/output/review-1.md`, `review-2.md`, …
- Context: `.agents/sub-workflow/problem-context.md`

## Reference

Existing solution that follows this pattern: `.agents/child-workflow-sync/` (agents at root in that legacy layout; new solutions use `.agents/<solution>/agents/` for agent files).
