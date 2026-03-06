---
name: workflow-contract-generate-new
description: Generates new IMSv3 workflow UI contracts (response + resume samples) using existing patterns from drift/.agents/workflow-contract-builder/outputs. Enforces reuse_playbook (match by tag/archetype/resume keys) to avoid generating new contracts/fields unnecessarily. Use when the user asks to create a new contract/screen/layout, add a new UI flow, or needs a response+resume contract for a workflow node.
---

# Generate New Workflow Contracts

## Goal

Generate a new contract (response + resume request samples) while:

- **reusing existing option archetypes/tags** whenever possible
- **avoiding new Java fields**
- keeping template naming flexible (templateIds can vary)

Additional conventions:

- **Only tags are channel-prefixed** (e.g., `ss.static_text`). `view.layoutId` should not be channel-prefixed.
- Prefer **generic templateIds** when meaning + variable schema is the same (see “Template naming strategy”).

All references and approvals must update:

- `.agents/workflow-contract-builder/outputs/`

## Always read these project references first

- `.agents/workflow-contract-builder/outputs/reuse_playbook.md`
- `.agents/workflow-contract-builder/outputs/fields.md`
- `.agents/workflow-contract-builder/outputs/tags.md`
- `.agents/workflow-contract-builder/outputs/templates.md`
- `.agents/workflow-contract-builder/outputs/existing_contracts.md`

**Workflow context (optional)**: If the contract is for a workflow that has a context file at `worker/src/main/resources/scripts/<workflow_name>/context.json` (e.g. `questionnaire_workflow/context.json`), use it to reference variable names, option IDs, or resume keys **only when required** for the screen. Do not force every contract to depend on context.

## Requirements gathering (keep asking until “generate”)

Collect the minimum needed to generate a correct, reusable contract:

- **channel(s)**: `sa` / `ss` / `ehc` (or multiple)
- **screen purpose**: what UI must render + what user/agent must do
- **layoutId**: desired screen/layout identifier
- **components needed** (map each to an archetype):
  - static text
  - dropdown (simple)
  - dropdown (dependent)
  - button widget (which actions?)
  - free text (what is captured?)
- **resume keys**: exact data IMS needs back on `/resume` (when the workflow has `context.json`, prefer key names from it only when relevant)
- **dependencies**: parent-child relationships between inputs
- **dynamic vs static values**:
  - static lists → `possibleValues[]`
  - cascading lists → `possibleDependentValues{}`
  - dynamic → consider `possibleDynamicValues` (string hook) or provide values at runtime
- **templates**:
  - each static/free-text instruction needs a `templateId`
  - list `templateVariables` keys + meaning + type

### Routed actions (disconnected-node) vs /resume

For every button/action, explicitly determine whether UI should:

- call `/resume` (backend action), or
- perform a **UI-only action** (no API hit) like opening/closing a modal, navigation, etc.

If it’s UI-only, encode intent in `PossibleValue.metaData` (UI-defined), e.g.:

- `uiAction: "OPEN_MODAL"`
- `targetLayoutId: "<layoutId>"`
- `api: false`

## Template naming strategy (generic, reuse-first)

`templateId` should represent the **message contract type** (what is being rendered + what variables are required), not the exact UI/UX.

Defaults:

- **Iris-powered static text**:
  - `templateId`: `iris_static_message`
  - `templateVariables`: at minimum `enum`
  - optional: `defaultText`, `params` (if parameterization is needed)

Use unique `templateId`s only when:

- the meaning is domain-specific and should not be reused by accident, or
- the variable schema differs and you want strictness

## Reuse-first workflow

### 1) Try to reuse an existing screen pattern

Search `existing_contracts.md` by:

- option archetypes
- tags
- resume keys

If you find a close match, reuse the same structure and only change:

- `layoutId` (if needed)
- `Option.id` / `description`
- `Instruction.templateId` / `templateVariables`
- `PossibleValue.value` enums (for buttons) or dropdown value sets

### 2) Only introduce new tag if UI truly needs a new component type

If a requirement cannot be expressed using existing tags/archetypes:

- introduce a new **channel-prefixed** tag (e.g., `sa.xyz_component`)
- update `tags.md` with its expected shape

### 3) Only introduce new fields as last resort

Prefer:

- `Instruction.templateVariables` for extra data
- `PossibleValue.metaData` for per-value UI metadata

Avoid adding new Java fields unless unavoidable.

## Output format (to user)

Produce:

1) **Sample response JSON** (normalized to code shape):
   - `WorkflowResponse` with `view.layoutId` and `view.inputOptions[]`
2) **Sample resume request JSON**:
   - `viewResponse.selectedOptions{ optionId -> submittedValue }`
3) A short **signature** summary:
   - layoutId
   - options `(id, tag, archetype)`
   - resume keys

After user approval, update these docs:

- `existing_contracts.md` (add the new screen signature)
- `templates.md` (add templateIds and variables used)
- `tags.md` (only if new tags introduced)

### Post-confirmation step (required)

After the user confirms the generated contract:

- Re-read the final response/resume JSON you generated
- Run the **process-existing** workflow on it (as if invoked via `/workflow-contract-process-existing`)
- Ensure `.agents/workflow-contract-builder/outputs/` is updated with:
  - new/updated tags
  - new/updated templates (prefer generic templateIds like `iris_static_message` when applicable)
  - the new/updated screen signature entry

## Example mapping rules

- Dropdown submitted value is always the chosen `PossibleValue.value`.
- Button submitted value is always the chosen `PossibleValue.value` (action enum).
- Free-text submitted value is the typed string.

