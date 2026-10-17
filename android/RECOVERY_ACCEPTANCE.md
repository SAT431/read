# Focused Android recovery acceptance

Updated 16 October 2026. Four additional instrumentation stages passed on the connected Xiaomi Android 14 tablet. The current app implementation did not require a production-code change for these checks. The refreshed helper installed in place; learner PDFs and credentials were not replaced.

## Actual orientation changes

PracticeTimerRotationTest passed in 15.885 seconds. The test requested the opposite orientation and required a different Activity instance with the requested configuration. A native elapsedRealtime timer kept RUNNING/STARTED and its control version across recreation. Its sample answer, bookmark and explicit reading report remained unchanged. Moving the recreated host to the background then paused time with BACKGROUNDED. The test uses the same PracticeTimerLifecycleObserver as MainActivity and an isolated WorkspaceSession; it does not inject a running quiz into the learner workspace.

MainActivityRotationTest passed in 16.396 seconds on actual MainActivity. After the real Learning route loaded, an orientation request recreated the activity. The application graph remained the same object; saved activity data, source context, revision records, source corrections, PDF library and navigation stayed equal, with no storage error. Constant assertion messages keep learner contents out of failure output. This covers the production graph/ViewModel route independently of the running-timer fixture. Requested orientation is restored afterward.

## Fresh-process data recovery

ProcessRecoveryTest has two explicitly invoked stages. The seed stage passed in 1.773 seconds and wrote only its own cache database and marker. It saved a previous quiz attempt, a current unchecked answer with explicit confidence, a note, an unfinished private draft, a bookmark, an explicit reading report and a RUNNING timer after a native elapsed-time checkpoint. The awaited snapshot was reread from Room and matched before the stage finished.

The harness then force-stopped the app and launched the verification stage in a fresh Android process. Verification passed in 0.775 seconds and required a different process ID. WorkspaceSession.restore recovered the exact durable snapshot, with only the native recovery transition: the timer became PAUSED/RECOVERED, its saved elapsed time stayed unchanged, and no absent-process time was added. The complete saved learning state matched, including private draft and source links. Foregrounding did not resume time automatically; an explicit start resumed counting. The recovered snapshot was durably reread from Room. Fixture database and marker were removed only after verification succeeded.

The recovery fixture never reads or overwrites the production workspace or provider keys. It uses the actual Room adapter, snapshot codec, shared action/session runtime, source validators and native timer clock. The saved marker contains labelled sample state only and is never printed by the harness.

## Reproducing the staged check

Install the app and helper with adb install -r. Use a new run identifier containing only lowercase letters, digits and hyphens (at most 48 characters). Invoke the seed and verification methods separately with the same folioRecoveryRun argument, force-stopping com.example.read between them. The methods are:

- com.example.read.ProcessRecoveryTest#seedDurableWorkForProcessTermination
- com.example.read.ProcessRecoveryTest#verifyFreshProcessRestoresOnlyDurableCheckpointAndAllLearningState

Use com.example.read.test/androidx.test.runner.AndroidJUnitRunner with am instrument -w. Running the methods without the explicit argument skips this staged harness. Reusing an existing run identifier rejects instead of overwriting prior evidence. Foreground the test host when running the orientation check on this tablet; those elapsed times include foreground waits and are not performance metrics.

## Scope and remaining gates

These checks add to the earlier 16 native/live/enlarged-text acceptance tests. The unchanged local baseline is 287 passing unit tests, app/helper builds and lint/export, plus architecture policy for 13 modules / 294 production Kotlin files. The added work is instrumentation coverage, not new application behavior.

The process check terminates after a completed durable write. It does not prove recovery from power loss or termination during an unfinished write, every UI/editor/service restoration path, or every Android version/device. Cross-document scroll restoration, broad TalkBack/stylus/selection behavior, multiple-view editing conflicts, real-source fidelity, source-change cleanup and sustained performance remain open, alongside the full component catalogue and release/signing gates. The complete development goal remains active.
