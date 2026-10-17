# AI orchestration contract

All commands use strict schemas in `src/core.ts`. Unknown properties and command/component types are rejected. A response envelope has only `message` and `commands` (maximum 12). No executable markup is accepted as a UI capability. Text remains text even if it resembles code.

| Command | Payload | Preconditions and effect |
|---|---|---|
| CREATE_ACTIVITY | kind, source, content | Existing source; required content validated by kind; unique ID allocated locally |
| FOCUS_SOURCE | source | Known element; reader reactivated and source position updated |
| FOCUS_ACTIVITY | id | Existing activity; visible, active and focused |
| SUSPEND_ACTIVITY | id | Existing, unpinned for AI; hide and suspend without deleting data |
| RESUME_ACTIVITY | id | Existing activity; restore visibility and focus |
| HIDE_COMPONENT | id | Existing, unpinned for AI; hide only, preserve lifecycle |
| SET_COMPONENT_PRIORITY | id, priority | Integer 0–10, pin protected |
| SET_PRESENTATION | id, presentation | Variant supported by registry, pin protected; intent currently stored |

The executor clones state, validates references and capabilities sequentially, and commits only after the whole batch succeeds. Rejected batches are shown in the command inspector. Layout fallback is stacking in normal flow; failures do not destroy workspace data. Layout undo restores presentation only and preserves learner answers, notes and retrieval observations.

Provider adapter: `/api/mentor`, server environment credentials, configurable Gemini model, request size cap and 45-second timeout, token-count logging, explicit errors. Context includes only current-page text (with approved corrections), sentence selection, activity metadata and recent retrieval attempts. Source is marked untrusted data in the provider instruction. Returned JSON is validated on the client before execution. Server currently validates the envelope only; the UI executor remains execution authority. Offline fixture mode is explicitly labelled and supports only the original example source.

Remaining protocol: INSERT_ENRICHMENT, UPDATE_LAYOUT composition trees, SHOW_COMPONENT, PLAY_TRANSITION, RESTORE_WORKSPACE and general capability permission objects. Snapshot restore is currently user-only. Server-side provider schema parity and source lookup instead of client-supplied context should be added before any multi-user service deployment.
