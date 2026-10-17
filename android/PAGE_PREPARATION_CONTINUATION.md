# Awaited native page preparation

Implemented 16 October 2026. A single request can prepare its selected original PDF page and then explain the resulting source without a second Send. Native controls and the assistant join the same app-owned jobs. This extends ASSISTANT_CONTINUATION.md; pickers, microphone permissions and native approvals still require their own interaction.

## Behavior and ownership

PagePreparationJobs owns a bounded queue of 20 active requests, two workers and bounded completed records. Work is keyed by document checksum, document ID, page and engineering version. Different callers join active work; the same job identity never silently retries a failure. A new explicit identity rechecks the actual cache, including after native repair. Page controls are a thin facade over this service.

PagePreparationAwaiter supports exclusively EngineerPage effects. It checks PDF identity/range before and after completion and requires actual cached source. AssistantPreparationWait stops follow-up on study target, library, learner input or semantic timer changes; viewport/presentation-only updates do not falsely invalidate a completed page. Jobs only cache and never navigate. Cancelling the assistant, changing page or reaching its deadline stops further planning while an already-requested shared job may finish caching. Failure never generates an explanation from a missing job result or enables unsafe replay.

The assistant uses PREPARING, a reachable Cancel control and a durable native completion result. The next bounded round receives fresh page/fidelity context, with machine reconstruction explicitly unchecked. All source, pin, writing and native-only approval restrictions remain. Three-round/90-second limits include waiting. Interrupted PREPARING history restores cancelled with no automatic job replay. Reader scroll reporting now skips unchanged initial positions so mounting freshly prepared source does not fabricate a study revision.

## Geometry correction discovered through live checks

The initial live reconstruction failed strict native absolute-region validation. The model wire protocol now explicitly declares NORMALIZED_1000_YXYX and four box_2d values. Native decoding validates finite 0..1000 ordered coordinates and converts top/left/bottom/right to stored PDF-point left/top/right/bottom. Undeclared units, old bounds fields, invented evidence IDs, out-of-range and inverted boxes are rejected without guessing/clamping. New app requests use engineering version 2; older readable caches remain intact.

This convention follows [Google's image-understanding geometry documentation](https://ai.google.dev/gemini-api/docs/image-understanding). The first strict schema also received HTTP 400; removing large/zero optional list cardinalities from the provider schema resolved the focused live checks. The limits still apply in native decoding. Schema size rejection is a documented [structured-output limitation](https://ai.google.dev/gemini-api/docs/structured-output); the HTTP result alone does not establish the exact rejected keyword. Error bodies/credentials were never printed or inspected.

## Executed evidence

375 unit tests passed with zero errors/failures: 12 preparation/job/recovery checks and four geometry/schema checks added to the previous 359. App/helper builds, lint/export, architecture and all five packaged prompt/source comparisons passed. At that acceptance point the architecture had 13 modules / 329 production Kotlin files.

Eight distinct tablet checks passed before the following visual redesign:

- AssistantPreparationUiTest: three tests at 1.8 font scale for manual/AI job sharing, source-before-explanation, cancellation and PDF page changes. Original bytes, private drafts, notes and durable records are asserted. The version-2 update passed in 32.215 seconds. A labelled enlarged-text screenshot was inspected.
- LiveAssistantPreparationTest and LivePdfEngineeringTest passed together in 12.609 seconds after the final schema correction. Real saved Gemini settings were used only inside Android. The generated two-page fixture processed only its selected page and a later model round quoted/explained fresh Amber/group text. The separate three-page fixture preserved ATP/DNA sentences, cropped original red figure pixels and recovered cached page/fidelity data offline. Originals and temporary selected-page cleanup were checked.
- MainActivityRotationTest and two AssistantContinuationUiTest checks passed together in 47.942 seconds: actual production state retention across rotation and prior one-send search/open/cancel behavior.

The initial two geometry failures and one HTTP 400 failure are retained here as diagnostic history, not counted as passing checks. Fixture data is isolated and labelled. App/helper were installed with install -r without uninstalling/resetting learner data.

## Limits and next priorities

This establishes selected generated-page behavior, not comprehensive NCERT/scanned/rotated/dense-layout fidelity, physical-microphone compound acceptance or full production readiness. Comprehensive document indexing/topic mapping, complete PDF rename/move/delete/duplicate/storage lifecycle, richer tool selection and original-linked presentation transformations remain priorities under the user's PDF/harness directive. Picker/approval resumption, abrupt interruption during writes, broad accessibility/performance, the full component catalogue and release gates remain open. The full development goal stays active.
