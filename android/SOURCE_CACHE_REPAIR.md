# Explicit readable-page cache repair

Updated 16 October 2026. Implemented, installed in place and verified with focused native/device checks. This extends COLD_SOURCE_RECOVERY.md; it does not repair malformed learner snapshots or missing original PDFs.

## Learner flow

Appearance → Check readable page cache runs a local check. A healthy cache reloads into memory, restores writes after a transient read failure and shows a no-damage result without creating a backup or changing stored rows. A damaged cache opens a separate native dialog with total/damaged page/report counts. Cancel changes no stored rows. Back up and repair is an explicit native learner action. It backs up every original source_pages row before removing invalid machine-page records and clearing only invalid comparison-report JSON. Healthy machine text and valid reports stay exact. The completion receipt identifies the private backup and counts.

Original PDFs/cropped assets, library metadata and the workspace are outside this transaction. Answers, confidence, attempts, recall drafts, notes/private annotation drafts, bookmarks, reading reports, corrections and scheduling records keep their original references. Reprocessing a removed page is a separate action from its original PDF; there is no automatic Gemini call, automatic source retargeting or claim that reconstruction is verified.

## Boundaries and data safety

SourcePageCacheCodec shares strict page validation with ordinary recovery. Repair classification retains valid machine text when only a comparison report is broken. A deterministic SHA-256 fingerprint covers every field of every original row in stable primary-key order, with length-delimited canonical row bytes. Preview carries metadata only.

RoomPageRepository shares one mutex across load, save, preview and repair. Confirmation recomputes the plan and rejects a changed fingerprint before backup. PrivateSourceCacheBackup writes a unique UTF-8 JSONL file under noBackupFilesDir/source-cache-backups, flushes/fsyncs it, reads back every header/row and compares exact values, then renames it from .pending to .jsonl. Backup failure aborts before database mutation. No old backups are automatically pruned. Backups contain source-page text and remain inside private app storage; they are not sent to providers or included in Android backup.

SourcePageDao.applyRepair runs in a Room transaction. It compares all current rows with the backed-up originals again inside the transaction, then deletes only invalid page keys and resets invalid reports. A stale plan or transaction failure cannot commit a partial deletion. Only a successful commit publishes remaining validated pages, clears pending diagnostics, restores cache writes and advances the native source revision. A healthy recheck also reloads validated pages after a transient read failure, without rewriting stored rows.

SourceCacheRepairSession belongs to the application scope, survives ordinary navigation/rotation and keeps no durable approval. Native preview creates an opaque one-use token. Pure command validation never consumes it: the whole batch must succeed before effects run. The effect coordinator consumes it before starting backup/commit; duplicate/stale effects do not repeat repair. Once confirmed, coroutine cancellation does not interrupt the critical backup/commit sequence. Process death does not replay approval: SQLite transaction recovery and the next normal load determine state. A completed backup may remain after a failed transaction; an incomplete file may remain with .pending. These are retained rather than silently discarded.

## Shared AI and touch controls

source.cache.check and source.cache.dismiss use the same typed command/effect path as native controls. Gemini can request the check/preview, but source.cache.confirm_repair is disabled in provider capabilities/schema and rejected for CommandActor.AI even with a token. Native confirmation also requires the exact live preview token. Provider repair context exposes phase/counts and nativeConfirmationRequired only; no token, raw stored rows or backup contents. The native prompt documents that opening a preview is not a successful repair.

## Verification

319 unit tests pass with zero failures/errors. Eleven new unit cases cover damage classification, deterministic fingerprints, exact lossless backup, healthy-page retention, repaired-cache writes, stale preview/transaction checks, backup failure, healthy no-op/transient-read retry, native-only one-use confirmation, invalid atomic batches, lost approval after dismissal/recreation, safe errors and provider capability filtering. Architecture policy passes for 13 modules / 304 focused production Kotlin files; app/helper builds, lint and export pass. All packaged provider prompts match their source.

Seven distinct physical-tablet checks passed for this update:

- SourceCacheRepairStorageTest: actual Room repair with an exact original-row backup, unchanged workspace/library/generated original PDF, repository reload and subsequent cache writes; SQLite trigger failure after an earlier deletion proves full transaction rollback while retaining the verified backup. Two tests passed, repeated on the final build in the 17.439-second route/storage batch.
- SourceCacheRepairUiTest: enlarged text (1.8), preview/cancel/confirm/receipt through the real shared executor; the store is an isolated fixture. Passed in the 16.966-second initial three-test batch. artifacts/qa/source-cache-repair-large-text.png was inspected for reachability/clipping.
- LiveSourceCacheRepairTest: real configured Gemini opens the preview, leaves original damaged fixture rows unchanged, creates no backup and cannot approve with an AI-actor command. Passed initially (3.932 seconds) and on the final build (3.45 seconds).
- SourceCacheRepairAppRouteUiTest: the actual installed Appearance control opens the production check and closes/cancels it without confirming repair; passed in the final route/storage batch.
- ColdSourceRecoveryUiTest and ReadingCheckpointManagerUiTest regressions passed together (29.816 seconds), retaining saved-study access and source guards.

All destructive test cases use temporary databases/private fixture directories, not the learner database. App/helper installation used install -r, with no uninstall/reset or credential inspection. Normal Folio was cold-launched after verification. The new repair is available to the learner; no damaged learner cache was repaired automatically.

## Remaining gates

A backup browser/export/restore and explicit retention management remain to be built. These private backups are not a complete original-PDF/media/workspace backup or cross-device sync. Low-storage/large-library performance, actual power-loss/in-flight process interruption, per-record malformed-workspace repair, missing original-file relinking, orphan draft/annotation management, full accessibility/TalkBack and broad textbook/tutor/voice quality remain open. The full component/refinement/release objective stays active; this focused feature does not establish production readiness.
