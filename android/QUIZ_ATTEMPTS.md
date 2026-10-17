# Repeated quiz attempts and read-only saved answers

Folio can now save current quiz work and start another attempt, including when the current attempt is partial. Earlier questions, answer keys, source links, choices, confidence and check metadata remain available read-only. Reviewing a saved answer does not overwrite the current attempt.

## Learner behavior

Start another attempt appears once the current quiz has a selected answer. Touch confirmation explains that current work will be retained read-only and the fresh attempt will contain no answers or confidence. Unchecked choices are saved without being graded. The current attempt number advances once; duplicate/stale restart requests reject. Paused/hidden quizzes must be resumed before history actions.

Saved-attempt buttons reopen the saved question position. Read-only navigation can visit each original question, show checked feedback against its saved key, show unchecked choices without revealing/claiming a checked result, and follow the original source links. Answer controls stay disabled. Return to current attempt restores the untouched current choices/confidence and source position. Historical review cannot select/check/change confidence/restart; the learner must return to current first. A current-question command explicitly exits historical mode.

The saved question/key text and source-anchor links are retained, not duplicated original PDF/page files. Source and page repositories keep their established ownership. Results clearly state that they use the saved key; an approved-source-correction notice prompts comparison with the source. A source that is no longer known does not erase an archived record, but cannot be opened through source-aware navigation. Full orphan-source recovery remains a separate acceptance area.

## Records and observations

QuizAttempt stores its number, original questions/keys/source links, last question position, selected answers, submitted IDs, confidence, check metadata, selection actors and native archive time/actor. Archive time means when the attempt was saved, not a fabricated completion time. Partial attempts retain unsubmitted work.

New QuizCheck records stamp native check time and check actor, with selection actor recorded separately when known. Existing checks with no recorded time/author remain unknown. A selection carried from an old snapshot with no recorded actor stays unknown when checked; no user/assistant author is invented. Earlier confidence retains its own explicit report time and actor.

LearningMemory projects both saved and current checked results, identified by quiz attempt number. The observation ID for a checked answer stays stable when archived; different attempts stay distinct. Summary counts include all saved checks, not just the newest attempt. Opening an old quiz observation shows the exact old question/key/source read-only; opening a current observation shows current work. No review completion, mastery or retention estimate is inferred.

The v1 quiz interval policy remains based on the current checked answer; history does not automatically promote the interval. Suggestion evidence now includes current attempt/check identity, and accepted metadata identifies its quiz attempt when known. Historical-view mode offers no current suggestion; recorded prior suggestions remain separate history. Recall's existing reported-streak policy is unchanged.

## Shared actions and architecture

| Action | Arguments | Result |
|---|---|---|
| quiz.restart | activityId, expectedAttempt | Save current work and start a fresh attempt |
| quiz.review_attempt | activityId, past attemptNumber, optional questionId | Read-only old question, defaulting to saved position |
| quiz.review_attempt | activityId, attemptNumber null, questionId null | Return to current work |
| learning.open_observation | native observation ID | Resume the exact current or saved quiz question |

The packaged prompt requires explicit restart intent, exact current counters and read-only history rules. Native guards validate state, source ownership, history bounds and atomic batches. Gemini cannot inject learner history into a new quiz. Both touch and AI use the same learning bridge/executor.

core:model owns attempt/check/selection types. core:runtime owns archive/review, validation, observation projection and source/PDF context. feature:study owns independent attempt controls and the prebuilt read-only review surface. Source-aware resume is shared with Learning and Revision. No module boundary changed.

The additive fields persist through normal workspace snapshots/Room; older snapshots default to attempt 1 with empty history/metadata. Limits are 100 saved attempts per quiz, 20 questions per attempt and one million question/key/option characters across a quiz and its saved attempts, plus existing workspace snapshot limits. Reaching a bound rejects the new operation; earlier work is not silently deleted. Whole-app long-term scale and storage-failure acceptance remain open.

Provider activity context includes the latest three saved attempts plus any currently viewed older attempt, with explicit quizHistoryOmitted counts. The actual current counter and local full history remain intact. Gemini can open any known past attempt number with null questionId to obtain its saved position and full currently viewed record next time. Learning-observation bounds/truncation remain explicit, while summary counts cover full native state.

## Verification — 15 October 2026

248 executed unit tests passed, including twelve new archive/history/runtime cases and an additive snapshot case. Architecture passed for 13 modules / 274 focused production Kotlin files. App/helper builds, app lint and APK export passed; both APKs installed in place, preserving learner PDFs/keys.

Seven distinct tablet checks passed: QuizAttemptsUiTest, QuizAttemptsStorageTest, QuizAttemptLargeTextUiTest, LiveQuizAttemptsTest, LearningMemoryUiTest, RevisionQueueUiTest and LearningAppRouteUiTest. They cover partial save/cancel/confirm, untouched current draft, disabled historical choices, confidence and native check records, stable memory reopening, database close/reopen, existing learning/revision flows and the real production route. The live Gemini 3.5 Flash-Lite check uses isolated sample state and saved on-device keys for restart → saved review → return, with no learner records changed and no raw replies/keys printed.

An enlarged-type screenshot initially exposed pale disabled labels; explicit readable theme colors corrected that while preserving disabled semantics and text/symbol correctness cues. Build/lint/unit checks passed after the change; the 1.8-font check and real route passed on the final installed app. artifacts/qa/quiz-attempt-large-text.png was visually inspected. This is not full TalkBack or every-screen accessibility acceptance.

Remaining gates include real microphone-to-history commands, larger-history performance/storage behavior, full lifecycle/process-death/offline recovery, scalable long-term learning records, timers, concept-level intelligence, question/source-quality acceptance, remaining catalogue and signed release. The overall production goal remains active.
