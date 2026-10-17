# Adaptive UI architecture

## Scope and invariants

The implementation establishes a local, single-user vertical slice. Source pages, enrichment content, activity state and layout are separate entities. React renders only engineer-written components. Model strings are rendered as text: there is no dynamic HTML injection, eval, runtime CSS or component compilation.

`src/core.ts` defines domain types, component capabilities, strict Zod commands, transactional execution, fixture responses, and the pure width-dependent layout solver. `src/App.tsx` connects the registry to reader, quiz, diagram, explanation and table renderers. `backend/app.py` provides source import, verification revisions, SQLite snapshots and a server-only Gemini adapter. `src/style.css` holds semantic theme, typography and motion tokens.

## Three interaction loops

Touch, selection, input, zoom and scrolling update local state immediately. Lifecycle operations pass through the deterministic command executor. Semantic requests package the current page, selected sentence identifier, active activity metadata and recent attempts for the server provider. Every returned command is parsed and executed transactionally on a cloned workspace. A failure leaves the previous state unchanged. Requests invalidated by a changed source or cancellation do not execute.

## Data authority

Imported source elements have deterministic IDs, PDF-space bounding boxes, page membership and original extraction text. Verification corrections live in a separate map; prior page versions are archived. Rendering uses approved corrections without overwriting extraction. Generated resources carry a source ID. SQLite is the durable store; a synchronous browser cache protects edits awaiting the serialized server save queue. Revision conflicts are visible.

## Design

Warm paper surfaces, subdued green accents, serif reading type and compact sans-serif controls keep long reading sessions calm. Dark mode and reading size are durable preferences. Source provenance and fixture status are visible. Unverified imports are routed to verification. The app does not infer comprehension from scrolling.

## Extension boundaries

Add a component through a contract in the registry, a validated content schema, a prebuilt renderer, a persisted activity state and executor tests. Do not allow a model-controlled module path or renderer string. The current renderer mapping lives in one file and should be split into component modules as the catalog grows. Full semantic page engineering, richer composition trees, PWA installation and authenticated tablet hosting remain follow-on work.
