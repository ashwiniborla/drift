---
name: agents-clarify-dont-assume
description: Every agent must ask questions when confused or unclear; do not assume. Use when running solutioning, review, or implementation agents, or when requirements/context are ambiguous.
---

# Ask Questions When Unclear — Do Not Assume

This skill applies to **all agents** (solutioning, review, implementation, and any other agent or assistant run in this repo). Follow it whenever you are working from problem context, solution docs, or user instructions.

## Rule

**If something is unclear or confusing, ask the user (or product owner) a question. Do not assume.**

- Do **not** guess intent, fill in missing requirements, or infer behaviour that is not stated.
- Do **not** proceed with an implementation or design choice when the problem context, solution doc, or user message is ambiguous.
- Do **ask** a clear, specific question and wait for clarification before continuing.

## When to Ask

Ask when any of the following is true:

1. **Ambiguous requirement** — The problem context or user request could mean more than one thing. Ask which interpretation is correct.
2. **Missing information** — A decision depends on information that is not in the context (e.g. version, environment, existing behaviour). Ask for the missing detail.
3. **Conflicting information** — The problem context, solution doc, and codebase (or multiple docs) disagree. Ask which source is correct or how to resolve the conflict.
4. **Edge case not specified** — The required behaviour for an edge case (e.g. empty input, duplicate keys, nested depth) is not defined. Ask how it should behave.
5. **Unclear scope** — It is not clear whether something is in scope (e.g. “context is already handled” but the boundary is fuzzy). Ask what is in scope.
6. **Unclear ownership** — It is not clear who decides (user vs product vs existing convention). Ask who should decide.

## How to Ask

- **Be specific:** State exactly what is unclear and what the alternatives are (e.g. “Should X do A or B?”).
- **One place:** Prefer one short list of questions rather than many scattered assumptions.
- **Context:** Include the relevant quote or file/section so the user knows what you are referring to.

Example:
```text
Before proceeding, I need to clarify:

1. In problem-context, "use contextOverrideKey when building the DSL" — if a node has no contextOverrideKey, should we always use a prefixed identity (e.g. subWorkflowNodeInstanceName_originalInstanceName), or is there another fallback?

2. For "include first node" when the start node is a BranchNode: should we inline the BranchNode and treat it as the effective first node, or skip it and treat all its successors as multiple "first" nodes (fan-out from parent)?

Please confirm so I don’t assume.
```

## What Not to Do

- Do **not** write “I’ll assume X” and then proceed without asking.
- Do **not** pick one interpretation silently and document it as a “design decision” when the requirement was ambiguous.
- Do **not** leave a vague “this might need clarification” note in the doc without actually asking the user.

## For Agent Prompts

When you **create or update** agent files (e.g. in `.agents/<solution>/agents/`), include a short instruction that references this behaviour:

- **Solutioning agent:** “If any requirement in the problem context or design is ambiguous or missing, ask the user before producing the solution. Do not assume.”
- **Review agent:** “If the solution doc or problem context is unclear or contradictory, list the questions for the user and do not mark criteria as PASS/FAIL by assumption.”
- **Implementation agent:** “If the solution doc or codebase leaves behaviour undefined for an edge case, ask before implementing. Do not assume.”

You can add a line such as: “See `.cursor/skills/agents-clarify-dont-assume/SKILL.md` — ask when unclear; do not assume.”

## Summary

| Do | Don’t |
|----|--------|
| Ask when unclear or confused | Assume intent or fill in requirements |
| Be specific and cite context | Proceed with a guess and document it later |
| List alternatives when asking | Silently pick one interpretation |

**Every agent can and should ask questions when confused; they should not assume anything.**
