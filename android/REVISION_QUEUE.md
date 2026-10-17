# Local revision queue

Folio now lets a learner schedule a known quiz question or active recall card, return to the exact saved work, report completing a review and schedule another. The learning sequence stays flexible. The queue now also offers explicit acceptance of native practice-based interval suggestions; see REVISION_SUGGESTIONS.md for the v1 policy and current verification. It makes no mastery estimate.

## User flow and state

Quiz and recall renderers share a prebuilt Review later panel with Now / In 1 day / In 3 days / In 7 days. Days mean elapsed 24-hour intervals. The Revision tab groups due, upcoming and completed entries, ordered by due time or latest completion. While visible, the queue refreshes its clock every minute. It sets no Android notification or background alarm.

Open review resumes the target activity and, for a quiz, selects the exact scheduled question. Existing checked answers remain locked; recall responses, reveal/rating state and prior attempts remain intact. Opening never implies review completion. Mark reviewed records an explicit report with a native timestamp and actor provenance. Rescheduling upserts one target, retains prior reported completions and makes it pending again. Completion reports retain their original prompt and source evidence if the current target later changes. They are not grades or retention estimates. Removal asks for confirmation and deletes only the schedule/completion log, retaining activity work.

Unavailable or changed targets remain visible as unavailable records. They cannot be opened; the learner can retain/remove the record or explicitly schedule the current known activity again. Existing original PDF, machine source, corrections and enriched study data remain separate. Approved source corrections do not automatically regenerate questions or their reference answers; the study renderers retain source-check notices.

## Shared AI and touch actions

| Action | Arguments | Behavior |
|---|---|---|
| revision.schedule | activityId, questionId (quiz ID or null for recall), delayDays 0..365 | Upsert known target using Android time; preserve history |
| revision.open | id | Resume exact target without altering answers or completion |
| revision.complete | id, expectedVersion | Record explicitly reported completion once |
| revision.remove | id, expectedVersion | Remove the queue record only |
| app.navigate | destination REVISION | Open the queue |

Gemini gets actual item IDs/versions, sources, activities, native context time and the same registered actions used by controls. The packaged native-tool prompt requires explicit review timing/completion requests and forbids fabricated history or notification claims. AI requests retain batch revision, atomic execution, interaction and pin protections. Clear "Open revision queue" navigation resolves on-device; general scheduling intent uses Gemini. OS permission or notification capabilities are not implied.

## Boundaries and storage

core:model owns RevisionItem/RevisionCompletion and typed actions. core:runtime owns target identity/resolution, validation, mutation and resume navigation. feature:study owns the shared scheduling panel; feature:library owns the queue screen/card; app wires the route and tab. ComponentRenderContext carries queue data; the existing learning bridge dispatches scheduling through AppCommandExecutor. No new module or dynamic UI execution was introduced.

The additive Workspace.revisionItems map persists with Room workspace snapshots; older snapshots default to an empty map. Layout undo cannot roll it back. Bounds: 1000 target records, 100 completions per record, 10000 total completion reports, one million prompt characters for current targets and one million for completion evidence, with existing snapshot-size bounds still enforced. Repeated/stale completion/removal and version/date overflow are rejected. Orphan queue records are retained on recovery so their history is not silently discarded. Write failures use the app's existing visible storage-recovery path.

## Initial queue verification — 15 October 2026

207 executed unit tests passed, including 13 revision runtime cases and two additive snapshot cases. Architecture checks passed for 13 modules / 253 focused production Kotlin files. App/helper builds, app lint and APK export passed. Both APKs installed in place; learner keys/PDFs retained, and normal MainActivity cold-launched.

Seven distinct physical-tablet checks passed: RevisionQueueUiTest, RevisionQueueStorageTest, RevisionQueueLargeTextUiTest, RevisionAppRouteUiTest, RecallUiTest, QuizPracticeUiTest and opt-in LiveRevisionQueueTest. Core queue/storage/live checks were repeated after adding original completion evidence. The final source-title display wording refinement was built/linted and installed afterward. The enlarged-type check uses 1.8 font scale and a long sample prompt; its inspected screenshot is artifacts/qa/revision-queue-large-text.png. This proves reachable controls in that case, not full TalkBack or all-screen accessibility acceptance. The production route test changes navigation only.

The live Gemini test uses privately saved Gemini 3.5 Flash-Lite credentials and isolated labelled sample state to schedule a review, open the Revision tab and record only an explicitly requested completion. It verifies native timing, versions, provenance and retained activity data. No raw provider bodies or keys were printed; no learner review was marked complete by the test. Full microphone-to-revision commands, personalized scheduling effectiveness, real textbook acceptance, abrupt-process/rotation/performance/accessibility testing and remaining catalogue/release gates are still open.
