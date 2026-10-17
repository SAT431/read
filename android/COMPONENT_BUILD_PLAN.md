# Component-first home and chapter-library build

Confirmed baseline: NEET UG 2026. Home sections: Physics, Chemistry, Botany and Zoology. Biology's Botany/Zoology separation is an application study grouping, not four officially independent NEET syllabus subjects.

Current provider setup is direct Android Gemini/Groq calls with encrypted user API keys, native selected-page PDF extraction/cropping and Room caching. Older private-service references below are superseded; no hosting is required. Full component scope is unchanged.

## Build order

1. Define the inventory below, durable domain models and semantic action IDs.
2. Implement the home, subject/chapter browsing and per-chapter PDF library as small native components.
3. Connect all shipped interactive controls to a discoverable action registry and the same deterministic dispatcher used by AI.
4. Next phase: secure Groq Whisper Large v3 transcription and Gemini mentor/page-engineering adapters, using versioned prompts and runtime capability/context packages.
5. Then expand the remaining reading/assessment/visual catalogue and optimize with real device tests.

## This iteration's inventory

| Component ID | Purpose | Semantic actions |
|---|---|---|
| app.top_bar | Title, context and navigation | app.navigate, app.back |
| app.destination_tab | Home, workspace, UI explorer | app.navigate |
| home.subject_grid | Responsive four-subject composition | subject.open |
| home.subject_card | Subject title, chapter/PDF counts | subject.open |
| subject.header | Subject name and catalogue provenance | app.back |
| subject.chapter_search | Search chapter names | library.search |
| subject.chapter_row | Chapter title, syllabus grouping and document count | chapter.open |
| chapter.library_header | Chapter name and subject context | app.back |
| chapter.pdf_import | Add a PDF to this chapter | library.request_import |
| chapter.empty_library | Explain how to add a chapter PDF | library.request_import |
| chapter.pdf_card | Original PDF metadata and open controls | library.open_pdf, library.study_pdf |
| pdf.original_viewer | Native original PDF page, page selector and zoom/pan | pdf.page, pdf.next, pdf.previous, pdf.zoom, pdf.transform |
| settings.appearance | Theme, reading size and reduced motion | preferences.set |
| assistant.command_input | Text fallback into shared action bus | Known navigation shortcuts on device; other requests -> direct Gemini; both use validated AppCommandBatch |
| assistant.microphone_status | Recording/transcription/error state | Foreground capture -> direct Groq Whisper transcription -> automatic assistant handoff |

Shared icons are registered separately from the controls that use them. Each control has a stable semantic node ID, action ID and supported argument contract. AI addresses actions and entities, not screenshot coordinates or cosmetic icon names. Non-interactive text/decoration is metadata, not a fake executable action.

Home/library/PDF/appearance components in this inventory are implemented in the current debug build. Assistant input calls Gemini directly; microphone capture calls Groq directly using privately saved encrypted device keys. Speak uses automatic end-of-speech submission. The user's actual microphone/transcript path reached Gemini, but complete successful voice-to-action acceptance is still open. See COMPONENT_CATALOG.md for the complete 111-item scope and IMPLEMENTATION_STATUS.md for executed checks.

Existing workspace/assessment actions remain in the workspace command protocol. Home/library actions extend this architecture; they do not create a parallel UI-only control path. Platform file selection, microphone permissions and other Android-owned dialogs remain controlled by the user/OS even when AI requests opening them.

## Provider separation

Groq Whisper Large v3 transcribes recorded audio; it does not plan UI actions. Gemini interprets the resulting text using study context, available semantic actions, component contracts and source data. The app validates commands and executes them locally. User keys are encrypted with Android Keystore, with no hosting/deployment dependency. Live provider checks and known failures are recorded in PHASE_2_PROGRESS.md; no live response is simulated.

Sources: https://neet.nta.nic.in/document/syllabus-for-neet-ug-2026-examination/ and https://console.groq.com/docs/speech-to-text.
