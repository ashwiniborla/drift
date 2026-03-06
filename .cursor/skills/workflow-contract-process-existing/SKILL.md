---
name: workflow-contract-process-existing
description: Processes existing IMSv3 workflow UI contracts (response + resume samples) to extract stable patterns, tags, templates, and field semantics. Updates drift/.agents/workflow-contract-builder/outputs (fields.md, tags.md, templates.md, existing_contracts.md) and enforces reuse_playbook to avoid new fields/contracts. Use when the user shares an existing contract JSON/response/resume sample, asks to document a contract, or wants to onboard a contract into the reference set.
---

# Process Existing Workflow Contracts

## Goal

Turn messy/verbose existing contract samples into **stable, reusable reference documentation** while preventing contract/field churn.

All outputs must be written to:

- `.agents/workflow-contract-builder/outputs/`

## Inputs expected from user

At least one of:

- Sample **response** payload (JSON) containing `view.layoutId` and `view.inputOptions[]`
- Sample **resume request** payload(s) (JSON) containing `viewResponse.selectedOptions`
- Any notes about channel (`sa` / `ss` / `ehc`), screen purpose, and required resume keys

## Always read these project references first

- `.agents/workflow-contract-builder/outputs/reuse_playbook.md`
- `.agents/workflow-contract-builder/outputs/fields.md`
- `.agents/workflow-contract-builder/outputs/tags.md`
- `.agents/workflow-contract-builder/outputs/templates.md`
- `.agents/workflow-contract-builder/outputs/existing_contracts.md`

## Workflow

### 1) Normalize the contract into a “signature”

For each screen (`view.layoutId`), derive:

- **layoutId**
- **Option set**: list of `(optionId, tag, archetype)`
  - Archetypes: `static_text`, `free_text`, `button_widget`, `dropdown`, `dependent_dropdown`
- **Dependencies**: `parentId` and/or `possibleDependentValues` mapping
- **Resume keys**: the exact `Option.id` keys that appear in `viewResponse.selectedOptions`
- **Value semantics**:
  - If selection: the submitted value is `PossibleValue.value`
  - If free text: the submitted value is the typed string

Do **not** treat `templateId` as stable.

### 1.1) Apply template naming strategy (when authoring/recommending)

When you are **recommending** how a contract should be authored (or when you are processing a contract that you/this agent generated), prefer **generic templateIds** where meaning + variable schema is the same across screens/channels.

Default recommendation:

- Iris-powered static text:
  - `templateId`: `iris_static_message`
  - `templateVariables`: at minimum `enum`
  - optional: `defaultText`, `params`

Keep original `templateId` values as “observed” when documenting legacy/external samples, but add a short note if they can be represented by a generic templateId.

### 2) Extract and catalogue tags

For every `Option.tags.values[]` entry:

- If it already exists in `tags.md`, reuse it.
- If new, ask the user for:
  - **purpose**
  - **channels supported**
  - **expected shape** (possibleValues? instructions? dependent values?)
  - any UI constraints (single vs multiple options, etc.)
Then add it to `tags.md`.

### 3) Extract and catalogue templates

For every `Instruction.templateId`:

- Record which archetype/tag it is used with.
- Record observed `templateVariables` keys and their types/meaning.
- If the template is new, add it to `templates.md`.
  - If it matches a generic pattern (e.g., Iris static message), prefer documenting it under the generic name (`iris_static_message`) and treat the old id as legacy/alias.

### 3.1) Capture UI-only actions (when present)

If an `Option` is a button/widget and any `PossibleValue.metaData` implies a **UI-only action** (no API hit), record it in `existing_contracts.md`:

- which optionId carries the UI-only action
- the `PossibleValue.value` (action enum, if any)
- relevant `metaData` keys (e.g., `uiAction`, `targetLayoutId`, `api: false`)

### 4) Add/refresh the “existing contract” entry

Update `existing_contracts.md` with the normalized, template-name-agnostic description:

- Screen purpose + channel
- layoutId
- option ids + tags + archetypes
- required resume keys
- dependencies

### 5) Field changes are last resort

Only propose changes to `fields.md` / Java model fields if the requirement **cannot** be represented using existing structures:

- `Instruction.templateVariables`
- `PossibleValue.metaData`
- existing `Option` fields

If you believe a new field is needed, explain why the existing fields cannot model it.

## Output format requirements

When writing back to the user:

- Provide a short “signature” summary per screen
- List:
  - new tags added (if any)
  - new templates added (if any)
  - which existing patterns were reused

## Example (signature)

Use this format in your response (not as code changes):

- **layoutId**: `rfr_date_slot_picker`
  - **options**:
    - `date_slot_static_text` / `sa.static_text` / static_text
    - `rfr_date_selection` / `sa.dropdown` / dropdown
    - `rfr_slot_selection` / `sa.dropdown` / dependent_dropdown (depends on `rfr_date_selection`)
    - `rfr_proceed_button_widget` / `sa.button_widget` / button_widget
  - **resume keys**: `rfr_date_selection`, `rfr_slot_selection`, `rfr_proceed_button_widget`

