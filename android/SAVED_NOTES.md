# Saved notes, marks and private drafts

Updated 16 October 2026. Implemented and installed with focused native/device verification. Open Learning → Saved notes and drafts, or say Open notes. This is a projection of existing reader records, not a new copy of learning data.

## Resulting behavior

The native list includes notes, highlights, underlines and unfinished annotation drafts from active, hidden and suspended readers. All, Notes, Marks, Private drafts and Unavailable source filters plus bounded local text/source search are persisted in navigation. Missing document metadata, missing reconstruction and changed sentence targets do not hide saved text. Native UI checks source revision again when reconstruction changes.

Saved annotations can resume a currently resolvable passage/sentence through the shared SavedReadingNavigation path, including pin protection, reader resume, position/context updates and PDF ownership context. Opening does not mark reading complete or change the annotation. A changed sentence cannot silently attach to a different sentence. Older passage annotations contain no original wording fingerprint: their link opens current wording and the screen says it may have changed. These are source references, not proof of unchanged original text.

Edit note creates a separate private draft while retaining the saved annotation. It refuses to replace an existing edit draft. The manager can edit already retained draft text without requiring the original page. The edit dialog uses a local rotation-restorable buffer; Keep draft changes explicitly persists it. Closing keeps the previous saved draft rather than implicitly committing. It acquires native writing protection while open. Saving a new draft as a note retains its original source/sentence and records learner authorship. Applying an existing edit requires the original note’s exact saved text/source/sentence to still match. A changed/missing original remains protected; Save as a separate note explicitly creates a new annotation with the retained source instead. Empty note commits, reused IDs and reader limits are guarded.

Discard requires a native confirmation dialog and an exact current draft fingerprint. It removes only that draft. Older annotation code was also fixed: AI-created/updated/deleted annotations cannot clear unfinished drafts, and deleting a note retains its private edit even for native deletion. The manager therefore makes a previously unreachable orphan edit recoverable without replacing another note or changing its source.

## Runtime and privacy

Typed notes.filter, notes.search and notes.open commands join app.navigate NOTES. notes.open uses the exact native saved-annotation fingerprint and current source resolution. Clear Open notes/Open saved notes wording can resolve on device with the normal acknowledgement; other requests use Gemini.

notes.edit_note, notes.edit_draft, notes.commit_draft and notes.discard_draft use the same typed runtime but require CommandActor.USER and are disabled in provider capabilities/response schema. Draft operations require a SHA-256 fingerprint over the whole saved draft, identity and owning reader. The pure atomic executor rejects stale operations without a partial commit. Committing a draft touches only the intended reader record; original pages, other annotations/drafts, bookmarks, reading reports, answers and scheduling remain unchanged.

Gemini receives at most 200 savedAnnotationReference records with native fingerprints/source availability and the full saved-annotation count. Private draft text, IDs/fingerprints and the native notes search query are redacted; privateDraftCount is only a count. Existing source availability inventories can still contain source anchors associated with drafts, not their text/record IDs. Saved note text was already shared as annotation data. Explicitly saving a private draft as a note makes that text eligible for later saved-note context. The prompt distinguishes these boundaries. The normal writing gate defers AI actions during the local editor.

## Executed evidence

331 unit tests pass with zero failures/errors. Twelve additions cover suspended/missing-source projections, filtering/query bounds/wire format, source/pin/sentence guards, missing-source draft save, native-only mutations, stale atomic batches, changed-original conflicts, precise discard, provider redaction, old annotation mutation retention and separate-note recovery. Architecture policy passes (13 modules / 311 focused production Kotlin files); app/helper builds, lint and export pass. Packaged prompt contents match source.

Seven distinct physical-tablet checks passed for this update:

- SavedNotesUiTest: 1.8-font native list, unavailable source disabled, explicit draft edit, cancel discard, save new note, retention of quiz/bookmarks/reading reports and exact source, then delete-parent/private-edit retention and explicit separate-note recovery. Final run passed with SourceAnnotationUiTest and AnnotationDraftStorageTest in 36.734 seconds.
- SavedNotesStorageTest: actual Room close/reopen and new session retains private edit, search/filter and unavailable target; explicit fingerprinted discard persists while annotations/bookmarks/reports remain unchanged. Repeated on final build with the route test in 21.45 seconds.
- LiveSavedNotesTest: real configured Gemini filters Notes and reopens the exact native saved annotation. The request wrapper checks draft text/search redaction before calling Gemini; learning records stay unchanged. Final run passed in 7.066 seconds.
- SavedNotesAppRouteUiTest: actual installed Learning link opens the new library; passed on final build with storage.
- MainActivityRotationTest: production graph/stored state retention passed with the initial new destination and library build (30.158-second route/rotation batch); later changes concerned draft handling/copy and UI text.
- SourceAnnotationUiTest and AnnotationDraftStorageTest: existing in-reader annotation/edit/delete and private draft persistence regressions passed on final build.

artifacts/qa/saved-notes-large-text.png was recaptured after dialog exit animation and inspected: note/draft actions remain reachable without clipping. Intentional list scrolling may move the heading above the viewport. All fixture mutations use temporary Room databases. Production tests change navigation/rotation only. Both APKs installed with install -r; no learner data/keys were reset. Normal Folio was cold-launched after checks.

## Remaining work

This remains an annotation/draft library. The subsequent STUDY_SEARCH.md update adds unified search over metadata, cached passages, bookmarks, saved notes and practice prompts; entire unprocessed PDFs and private drafts remain outside that index. Large libraries/AI reference pagination, source-text excerpt history/fingerprints for older annotations, global mark deletion/cleanup, richer annotation history/export, full multiline editor process-interruption acceptance, stylus/selection/multiple-view conflicts, TalkBack and broad performance/accessibility remain open. Existing structural recovery still preserves malformed workspace originals rather than attempting per-record repair. Source-cache backup restore/retention, original-file relinking, real textbook/tutor/voice acceptance, unfinished catalogue components and production release gates also remain open. Full development goal remains active.
