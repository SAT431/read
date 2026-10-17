# Implementation status — first vertical slice

## Implemented and checked

- React/TypeScript production build, light/dark tokens, responsive reading surface and adjustable typography.
- Five registered component kinds with independent persistent activities, unique instances, source links, pins, hide/focus/suspend/resume and layout-only undo.
- Strict transactional command validation and adaptive width-based composition.
- Sample reader, paragraph/sentence selection, annotations and explicit read checkpoints.
- MCQ selection, deterministic evaluation, source review, pause/resume, retrieval observations and simple next-review dates.
- Comparison tables, explanations and original extracted-image viewer with zoom controls.
- PDF selected-page extraction using PyMuPDF; image import; original page preview; text correction approval; revision history; duplicate import preservation.
- Local SQLite workspace persistence, immediate browser recovery cache and serialized revision-checked writes.
- Explorer, live composition controls and accepted/rejected command trace.
- Browser speech recognition where supported; interruptible device speech synthesis; visible state and text fallback.
- Server-side Gemini REST adapter with timeout and errors, and a clearly separated deterministic fixture mode.

## Partial / important limits

- PDF extraction is block based, not AI layout engineering. Heading classification, sentences with stable source IDs, tables, figure labels/captions and cross-page continuations are not automatically reconstructed.
- Embedded raster images can be cropped. Vector-only diagrams are not detected. Scanned pages require manual transcription; OCR and uncertain-region reprocessing are not implemented.
- Sources and corrections are separate, but semantic confidence estimation and extraction diff tooling are not implemented.
- Layout supports arbitrary stacks/columns. Inline enrichment, floating/drag/resize containers, relational compositions, full attention locking and complete presentation variants remain planned.
- Revision scheduling is a transparent 1-day incorrect / 3-day correct heuristic, not an adaptive mastery model.
- Voice is browser transcription into the composer plus device TTS. It is not a streaming real-time conversational provider or synchronised narration.
- No API key was supplied: live model accuracy, billing, provider schema compatibility and live AI end-to-end behavior remain unverified.
- Browser localStorage is used instead of IndexedDB. PWA installation, offline source caching, multi-device sync, account security and authenticated HTTPS tablet deployment are not provided.
- Full accessibility audit, modal focus traps, larger compact touch targets, stylus gestures, performance profiling and the remaining target component catalog are outstanding.

## Acceptance evidence

Automated runtime and backend tests cover the critical invariants and synthetic PDF import/approval. Browser checks exercise quiz selection, pause/source review, resume and reload, comparison composition and the explorer. No actual NCERT page was attached, so the specified real-document acceptance scenario remains to be run on the user's textbook.

Next work should prioritize semantic extraction/OCR with source verification, sentence identities, full layout relationships, accessible component refactoring, and a real configured-provider acceptance run.

Final validation: production build passed; 9 Vitest runtime tests and 4 pytest backend tests passed. Browser checks at 800×1280 and 1280×800 reported document scrollWidth equal to clientWidth. One upstream Starlette test-client deprecation warning remains. These are functional checks, not a performance benchmark or a complete accessibility audit.
