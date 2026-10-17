# Explicit confidence and native learning observations

Folio now has optional pre-check quiz confidence and a Learning overview of saved practice observations. The app retains learner work independently of visual components; the assistant can retrieve these records through its normal context and use the same typed controls as touch.

## Confidence

Choose an answer, optionally report Unsure / Somewhat sure / Very sure, and check whenever desired. Confidence is not required. Changing the selected answer clears its pre-check confidence; choosing the same option keeps it. Confidence can be changed or cleared before checking. Checking locks it with that answer. Missing confidence remains unknown and is never treated as low confidence.

AnswerConfidence stores LOW/MEDIUM/HIGH, the corresponding selected option, a native timestamp and whether the report was entered by the assistant. quiz.confidence takes activityId, current questionId, expectedOption, expectedConfidence and confidence (null clears). It requires a visible active quiz/current unchecked question and the current selected option/report; stale requests reject. Gemini is prompted to record only an explicit learner statement, never infer confidence from correctness, speed, wording or scrolling. AI-created quizzes cannot contain learner answers, submissions or confidence history. Touch uses the same learning bridge and executor.

Confidence remains distinct from the current v1 revision policy: that policy still derives intervals from checked correctness and explicit recall ratings. It does not pretend that a confidence report calibrates long-term retention.

## Learning overview

LearningMemory is a read-only projection of saved activities, not another editable or duplicated history store. It includes checked quiz answers and explicitly rated past/current recall attempts. Unchecked selections and unrated current recall attempts do not create observations. Private freeform recall response text is excluded from this projection and its IDs. Existing assistant context rules for activity/history sharing remain unchanged.

| Filter | Included observations |
|---|---|
| All | Saved checks and explicit recall reports |
| Mistakes | Checked as incorrect against the saved question key |
| Confident mistakes | Checked incorrect with an explicit HIGH report |
| Correct while unsure | Checked correct with an explicit LOW report |
| Recall reports | Explicit recall ratings and their recorded native times/actors |

The overview shows counts, the saved prompt/source, confidence or reported recall rating and available provenance. Correctness follows the question's saved key. These are observations, not mastery scores, diagnosed misconceptions or measured retention. New checks record native time and known selection/check actors. Older quiz checks with no recorded time remain unknown; no check date or complete chronological timeline is fabricated. Confidence dates are explicitly labelled as confidence-report dates.

Open saved question restores that exact quiz question/source and preserves all checked answers/confidence. Repeated quiz attempts now retain separate observations with stable IDs across archiving; old observations open their saved question/key read-only and keep the current draft intact. See QUIZ_ATTEMPTS.md. Open current recall card resumes current work with earlier attempts retained; it never copies an old response over the current one. Opening never completes a revision item. A common StudyTargetNavigation helper provides the same source validation, pin-protected capsule expansion and PDF/context handling to both revision and learning-memory routes.

## Architecture and provider context

core:model owns confidence/observation types, filter state and typed commands; core:runtime owns confidence validation, observation projection and source-owned resume; feature:study owns the confidence panel; feature:library owns the Learning overview/cards. The app wires the tab and a separately implemented Learning icon. Feature modules remain independent and no model-generated UI executes.

learning.filter selects a native filter and opens the Learning tab. learning.open_observation requires the exact current observation ID; unknown/changed IDs reject. app.navigate LEARNING opens the overview. Clear Open learning / Open learning memory shortcuts resolve on device; other requests use Gemini. AppCapabilityContext exports up to 200 focused-first observations, a full LearningSummary and an explicit truncation flag. The packaged prompt documents observations versus hypotheses, exact tools, missing confidence and locked reports. It must not claim the bounded list is exhaustive when truncated.

Confidence and Learning filter state are additive workspace snapshot fields. Existing records default to empty confidence/ALL; no Room schema migration is needed. Pauses, hiding and layout undo cannot erase learner reports. Corrupt or mismatched confidence records are rejected during normal workspace validation. Native state is the authority, with existing revision/interaction/atomic guards and storage-error recovery retained.

## Verification — 15 October 2026

235 executed unit tests passed, including eight new confidence runtime cases, seven observation/navigation/context cases and two additive snapshot cases. Architecture checks passed for 13 modules / 268 focused production Kotlin files. App/helper builds, app lint and APK export passed; both APKs installed in place without removing learner keys/PDFs.

Six tablet checks passed: LearningMemoryUiTest, ConfidenceStorageTest, LiveLearningMemoryTest, LearningMemoryLargeTextUiTest, LearningAppRouteUiTest and RevisionQueueUiTest. UI coverage exercises optional confidence, clearing when an answer changes, checking/locking, source-linked filters, snapshot recovery, exact question resume and retaining the current recall response when opening an older report. Room close/reopen preserves selected-option confidence, native time/actor and filter state. The production route check changes navigation only.

The live saved-key Gemini 3.5 Flash-Lite test records only an explicit confidence statement, retrieves confident mistakes after a native check and reopens the recorded question with checked work unchanged. It uses isolated labelled sample state; no learner answer, key or raw provider body is printed. The 1.8 font-scale/long-prompt check proves reachable filters and source-linked controls in that case; artifacts/qa/learning-memory-large-text.png was visually inspected. This is not full TalkBack or real microphone-to-memory acceptance.

## Still required

The full app goal remains active. Concept-level struggle tracking, related-concept retrieval beyond bounded context, explanation-request memory, long-term mastery/retention models, full learning/session timelines, remaining target components/gestures/layouts, real textbook/tutor quality, broad voice/accessibility/lifecycle/performance and signed release gates remain open. Neither current counts nor selected passing tests establish production readiness.
