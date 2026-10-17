# Study interface redesign

16 October 2026. The redesigned native app and updated test helper are installed in place. Nine distinct physical-tablet checks passed, and the compact light, dark/enlarged-text and wide screenshots were inspected. This establishes the focused navigation/home/control redesign; broader accessibility/performance and full-app production acceptance remain open.

## What changed

- Responsive navigation separates study content from app destinations. Wide windows use a sidebar; compact windows use four primary bottom destinations. Revision, saved notes, Appearance and the UI explorer are grouped under More on compact screens. Back and all existing shared action IDs remain available. Wide sidebar items expose selection semantics.
- Home now has a focused study-studio card, horizontal subject cards, search, real recently added PDFs and a small library summary. Screenshot review led to a shorter banner with an inline assistant action on wide screens; all four subjects are visible initially in the tested normal-text portrait and wide layouts. No invented progress or fake document results. The subject grid reflows into one column for enlarged text. Recent documents use existing guarded OpenPdf commands; search and subject cards retain their shared typed capabilities.
- A warm paper/ink theme, restrained botanical/lavender/ochre accents, consistent rounded surfaces, custom native line icons, subtle satin button highlights and press springs replace the flat button wall. UI headings use sans-serif; reading body typography remains serif. The dashboard has a short entrance reveal. Native reduced-motion preferences snap these new animations; there are no perpetual decorative animations or downloaded assets.
- PDF previous/next and study actions remain direct. Zoom, reset and page jump live under Page tools; page jump is a focused dialog. AI continues to invoke the same registered PDF commands directly, without operating visual menus.
- The voice bar is smaller and retains hands-free Speak, stop/cancel/error/review behavior. Completed assistant status no longer occupies another permanent bar. Busy/waiting/failure/cancellation feedback remains visible. Saved provider settings start folded when configured; key drafts remain owned outside the folded section so toggling cannot discard them. No key persistence/provider architecture changed.

This is native Compose, split across focused existing/new screen and navigation files. AI still selects prebuilt controls and supplies structured data; no generated UI code or extraction scripts run.

## Verification to date

375 unit tests pass, zero failures/errors/skips. Architecture passes (13 modules / 334 focused production Kotlin files), app and helper builds, lint/export and all five packaged prompt/source comparisons pass. The latest app APK installed with install -r. Existing PDFs and encrypted keys were not removed/reset.

Nine distinct tablet checks passed on the final tightened layout:

- FolioDashboardUiTest (three): real portrait light and dark 1.8-font views, wide sidebar, More access, stable selected semantics, subject/search/recent-document command bindings and all four subjects initially visible at normal text size. The dark view uses reduced motion and reflows to one column. Original screenshot review prompted the compactness refinement. A five-test batch with the controls below passed in 86.01 seconds; the final three-dashboard screenshot refresh passed in 67.9 seconds. The additional screenshot wait lets Android orientation surface animations settle; it is test-only.
- GroupedStudyControlsUiTest (two): actual generated original-PDF rendering with shared validated zoom/reset, invalid page-jump rejection, valid jump/dialog dismissal and previous/next limits; original bytes, note/private-draft activities and Room reread are retained. The configured provider editor starts folded, preserves both synthetic input values through hide/show and submits the configured model on explicit Save at 1.8 font. No real keys are read or screenshot.
- SourceCacheRepairAppRouteUiTest, MainActivityRotationTest and RevisionAppRouteUiTest: actual installed Appearance/cache preview/cancel, production graph/data retention across real rotation and secondary Revision navigation. Passed together in 60.589 seconds. No repair or learner-content mutation was requested.
- StudySearchAppRouteUiTest: actual Home/search/exact chapter-library opening with retained learning data, then returns Home. Passed in 35.077 seconds.

The first install rejection and old-helper class-not-found attempt are resolved, not counted as passed checks. App and helper updates used install -r; no app reset/uninstall. Normal Folio was cold-launched on Home afterward. Safe fixture screenshots are artifacts/qa/dashboard-light.png, dashboard-dark-large-text.png and dashboard-wide.png. All three final captures were visually inspected after the orientation animation settled. These screenshots show labelled temporary content, never the learner's PDFs/keys/notes.

## Product priorities retained

The full directives are preserved in development/requirements/PDF_HARNESS_DIRECTIVE.md and ENGINEERING_DIRECTIVE.md, with an AGENTS.md reference for future work. The user prioritizes complete document engineering and an AI-controllable modular harness. The UI is part of that workflow; this design pass does not imply comprehensive document analysis exists.

Code inspection confirms import already bounds size, checks PDF header/renderability/page count, hashes/stores private original bytes, deduplicates same-checksum entries within a chapter and persists Room metadata. Rename/move/delete/restore/storage accounting and per-record library recovery are not complete. Whole-document structure/topic indexing and selection-driven extraction are also incomplete; selected-page preparation is verified separately in PAGE_PREPARATION_CONTINUATION.md. Implement the missing document lifecycle first with original/learner-data retention and shared contracts, then structure indexing/tool selection and original-linked presentation transformations. Complete/test each journey before opening another major feature.

Broader NCERT fidelity, tutor/voice quality, TalkBack, stylus/gesture conflicts, performance and release gates remain open. The complete development goal stays active.
