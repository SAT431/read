# Practice-based revision suggestions

A native, adjustable v1 policy now proposes review intervals from checked quiz correctness and explicitly reported recall ratings. The same prebuilt control appears in quiz/recall and on an available queue target. It never schedules itself; the learner can accept the suggestion, ask Gemini to accept it, or choose a manual interval. Existing answers and recall attempts stay intact.

## Policy and evidence

| Recorded observation | Suggested interval |
|---|---|
| Incorrect checked quiz answer | 1 day |
| Correct checked quiz answer | 3 days |
| Recall rating FORGOT | 1 day |
| Recall rating PARTIAL | 3 days |
| First consecutive RECALLED rating | 7 days |
| Second consecutive RECALLED rating | 14 days |
| Third consecutive RECALLED rating | 30 days |
| Fourth consecutive RECALLED rating | 60 days |
| Fifth or later consecutive RECALLED rating | 90 days |

Days are elapsed 24-hour intervals from acceptance. These values are an engineer-authored heuristic, not a calibrated retention model or proof of mastery. An unchecked selection, scrolling, merely revealing the reference, an unrated current attempt, or reporting a review complete produces no new performance observation. Recall intervals use the current explicitly rated attempt and the immediately preceding consecutive RECALLED ratings; FORGOT or PARTIAL breaks that streak. Quiz v1 uses the current attempt’s locked checked answer. Repeated quiz work is now saved separately; current v1 quiz intervals do not automatically increase from that history. Attempt/check identity is included in new evidence, and known quiz attempt numbers are retained with accepted suggestions. See QUIZ_ATTEMPTS.md.

Private response text is excluded from the suggestion calculation and fingerprint. The native evidence uses question/source/reference data plus checked answers or rating timestamps/provenance. Assistant-entered reported recall ratings remain labelled. No comprehension, confusion, mastery score or medical/scientific correctness is inferred by this scheduling policy.

## Runtime and AI contract

RevisionSuggestions computes a serializable RevisionSuggestion (policyVersion, reason, interval, observation/streak counts, assistant-rating flag and evidence fingerprint). It does not modify ActivityData. The prebuilt RevisionSuggestionControl explains the observed reason and dispatches the same semantic command as Gemini.

revision.accept_suggestion takes activityId, questionId (known quiz ID or null for recall), and expectedEvidence. Android recomputes the suggestion, compares the SHA-256 evidence fingerprint, validates the known source and schedules using native time. A changed answer/rating/source/reference or fabricated fingerprint rejects the action atomically. Batch revision, interaction and pin protections remain in force. The original activity answers/attempts are unchanged. The scheduled RevisionItem stores the accepted suggestion separately; choosing a manual interval clears that current suggestion metadata while retaining review completions.

AppCapabilityContext exports up to 200 current suggestions, prioritising the focused activity and excluding unavailable sources. The packaged prompt specifies exact arguments, policy limits and explicit acceptance intent. The model supplies neither a computed delay nor fabricated evidence for this action. The list is an extra bounded context surface; manual scheduling remains available for known targets outside it. Existing app context rules govern conversation/learning history sharing; this feature specifically excludes private response text from its own calculation.

The core:model type, pure core:runtime engine/reducer, shared core:ui control, independent study/library consumers and app composition retain the established module boundaries. Additive optional suggestion data recovers from previous workspace snapshots without a Room schema migration.

## Remaining work

The overall development goal stays active. Concept-level struggle retrieval, confidence records, calibrated long-term retention, richer scheduling preferences, full microphone-to-suggestion acceptance and broader textbook/accessibility/process-death/release gates remain open. No background Android notifications are set by this queue. See IMPLEMENTATION_STATUS.md and PHASE_2_PROGRESS.md for actual build/device evidence.

## Verification — 15 October 2026

218 executed unit tests passed, including eleven new suggestion runtime cases and additive accepted-metadata snapshot recovery. Architecture checks passed for 13 modules / 256 focused production Kotlin files. App/helper builds, app lint and APK export passed. A preliminary overlapping lint invocation failed on a missing intermediate output; the later complete verification run passed. Android initially canceled the app update; after the learner's retry authorization, app and helper installed successfully without removing PDFs or keys.

Five distinct tablet tests passed on this update: RevisionSuggestionUiTest, RevisionQueueStorageTest, RevisionQueueUiTest, RevisionQueueLargeTextUiTest and opt-in LiveRevisionSuggestionTest. Native UI coverage checks unsubmitted/unrated absence, checked-answer suggestions, manual override, 1→7→14-day recall suggestions across explicitly reported attempts, retained answers/history and snapshot recovery. Room close/reopen retains accepted suggestion metadata, completions and the source-linked answer. Enlarged-type coverage uses 1.8 font scale and a long prompt; the inspected artifacts/qa/revision-queue-large-text.png shows a native suggestion and reachable controls. Its capture was moved before the dialog interaction to avoid an Android dismissal-animation artifact; the test passed again. This is not full TalkBack acceptance.

The real saved-key Gemini 3.5 Flash-Lite check applied revision.accept_suggestion with native evidence for an isolated checked sample question. It verified the interval/native time, assistant provenance, no completion and unchanged answers. No raw response bodies or keys were printed, no learner answers changed and no paid/model fallback occurred. The installed normal app was cold-launched after tests. Full performance/retention validation, microphone-driven suggestion acceptance, concept-level learning intelligence and release gates remain open.
