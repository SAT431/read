# Study-state recovery with unavailable source data

Updated 16 October 2026. Implemented, installed in place and verified. Current result: 308 executed unit tests, zero failures/errors; architecture policy (13 modules / 299 production Kotlin files), app/helper builds, lint and export pass. Nine distinct tablet checks passed in this update, including deliberately corrupt source-cache recovery and real Gemini use of cached questions.

## Problem and resulting behavior

A regression test reproduced valid saved work being hidden when its source page was absent: WorkspaceSession.restore used live-source validation and rejected the whole saved workspace. Startup also loaded library, page cache and workspace in one try block, so a page-cache decode failure skipped workspace recovery entirely.

Recovery now validates persisted structure separately from current source availability. Structurally valid answers, confidence, attempts, recall responses, annotations/private drafts, bookmarks, reading reports and diagram transforms are retained with their original source IDs. A source-recovery notice explains unavailable text. No reference is silently redirected, source wording replaced or report marked verified. Missing document metadata is treated as unavailable evidence, not proof that the document was a fixture.

Library and page-cache loads are attempted separately before workspace restoration. Their failures produce static messages without exception payloads and do not block an independent workspace load. The existing adapters preserve damaged store rows and retain their write protection. Malformed workspace structure still fails recovery, preserves the original snapshot and disables overwrite; a workspace storage error takes display priority over a source-cache warning.

## Cached work and source boundaries

Existing cached questions, saved attempts, learning observations and scheduled study targets can be reopened without requiring the original passage. Their stored question/key/source identity must still match; viewing does not fabricate another reading report or change prior work. Source-review links show unavailability and disable when their native target is unknown. Original-source navigation remains guarded. Missing figure assets display a plain unavailable message while retaining saved view settings.

AI creation of new non-reader study content now requires readable cached source evidence for its parent and question/recall references. A known PDF page number alone is insufficient. An empty reader view can still open for a known unprocessed PDF page, so reading preparation remains flexible. New reading confirmations and saved bookmark/checkpoint source resolution keep their separate source/wording guards. This separates reviewing an existing cached item from claiming access to its missing original.

SavedSourceReferences inventories distinct activity, question/history, recall, annotation/draft, bookmark/report, revision/completion, correction and context references. Source availability checks also require matching document/page ownership. The recovery notice rechecks current references and source revision, so resolved/removed missing references stop contributing to it.

Gemini receives unavailableSources (at most 200), unavailableSourceCount for the full saved-reference count, and sourceAvailabilityKnown. An omitted capability catalogue is explicitly unknown, not a known empty result. Assistant sourceContext separately includes activeSourceAvailable and sourcePageAvailable. Wrong document/page payloads are not attached as source evidence. Evidence kinds distinguish explicitly labelled fixture pages, imported PDF references, cached sources without an original PDF and unavailable saved sources. Private annotation drafts remain redacted and writing/pin protection is unchanged.

## Verification

Fourteen new unit cases cover missing page/document recovery, malformed data/original preservation, continued durable writes, recall/diagram retention, timer recovery, cached question/history navigation, creation/original-source guards, bounded availability context, independent startup stages/cancellation/privacy, source ownership and unknown availability. The original failing test now passes. One existing historical-source test was updated to require cached read-only review while continuing to reject original-source navigation; it no longer hides the saved answer solely because the original disappeared.

ColdSourceRecoveryUiTest writes labelled study state and an invalid source-page JSON row into a temporary Room database, closes it, and restores through new repositories/session with the production startup coordinator. It verifies exact initial recovery, cached question navigation, disabled unavailable source review, an accessible checkpoint manager, explicit report clearing, retained answer/confidence/note/draft/bookmark data and a durable reread. The corrupt source row remains unchanged. This is actual Room decode failure and fresh repository recovery within an instrumentation process; it does not replace the separately documented fresh-Android-process checks.

LiveColdSourceRecoveryTest passed with saved encrypted credentials: Gemini navigated an existing cached question while original source access remained unavailable. Its sample reader was explicitly hidden before the request so a retained private draft did not weaken the normal writing guard. LiveRecallTest also passed on the final build, proving valid available-source creation still works under the stronger guard.

ReadingCheckpointManagerUiTest, QuizAttemptsUiTest, RevisionQueueUiTest and LearningMemoryUiTest passed as regressions. MainActivityRotationTest and LearningAppRouteUiTest passed on the final installed app. The fixture-only screenshot artifacts/qa/cold-source-recovery.png was inspected. APK prompts match source. All app/helper updates used install -r, preserved learner PDFs/keys, and normal Folio was cold-launched afterward.

## Remaining work

Damaged source stores remain preserved and write-protected until an explicit learner-confirmed repair. The subsequent SOURCE_CACHE_REPAIR.md update now implements that backed-up repair flow; it does not run automatically. Backup restore/retention management, missing original-file relinking, per-record malformed-workspace repair, annotation/draft orphan management, broad accessibility/performance, interruption during unfinished writes, real textbook/tutor/voice acceptance, remaining components and production release gates are still open. The full development goal remains active.
