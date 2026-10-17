# Android NEET Biology application — revised three-phase report

This report supersedes the earlier OCR-led extraction and fixed study-sequence descriptions. It describes planned development; the listed new capabilities are not implemented by saving this report.

## Confirmed product direction

- Native Kotlin / Jetpack Compose Android tablet app, with Room and private file storage on the tablet.
- Private hosted Python service for Gemini requests and advanced PDF processing; ordinary studying does not require a Windows computer.
- PDF-first input: import a whole chapter PDF, or assign a page range from a larger textbook PDF to a chapter. Image-upload functionality is outside the initial Android scope. Images rendered internally from PDFs remain valid processing inputs.
- A context-aware mentor operates a flexible workspace through typed application tools. Reading, source viewing, explanation, visual exploration, practice and revision are available in any order.
- Gemini is the primary semantic page-reconstruction provider. Embedded PDF text extraction supplies a fidelity reference when available; OCR is an optional diagnostic fallback, never the sole authority.
- Use a PDF-capable Gemini model available on the user's API project's free tier. Do not enable paid fallback or assume unlimited quota. Hosting and model service costs are separate.

## 1. PDF library and on-demand page engineering

Import preserves the original PDF, document checksum, file-page count, chapter identity and chapter page range. Library registration is separate from expensive page reconstruction: uploading a chapter does not require processing every page before opening it.

Each page has a state: not processed, processing, ready with unchecked transcription, reviewed, or failed. The original PDF page is always independently viewable. Reconstructed study content can be used immediately when valid, with its review status visible. Verification is a contextual tool, not a mandatory stop in a learning sequence.

### Engineering algorithm

1. Resolve the requested document and page. Display its original page immediately.
2. Reuse a valid saved reconstruction if its document checksum and engineering version match. Preserve notes, approved corrections and generated resources.
3. For an unprocessed page, prebuilt PDF code selects the exact page and gathers embedded text spans, coordinates, image objects and a source rendering. Avoid sending the full chapter on every request.
4. Send the selected page to Gemini with a strict data schema and transcription instructions. Request original wording, headings, paragraphs, sentences, tables, captions, figure regions, reading order and explicit uncertainty. The instruction must distinguish exact transcription from enrichment and prohibit filling gaps with guessed textbook content.
5. Reconcile Gemini's text with digitally extracted spans where available. Check omissions, unexpected additions, repeated blocks, numerical notation and element coverage. Disagreements remain traceable; a model's self-reported confidence is not proof of accuracy.
6. Recover original figures with prebuilt PDF tools. Use embedded image assets when complete; for vector graphics, multi-object figures or unreliable assets, crop/render the original source region. Gemini can propose figure boundaries and caption relationships; validated crop tools perform the extraction. A figure is never regenerated as a replacement for the source.
7. Validate region bounds, page ownership, nonempty content, references, ordering and schema limits. Run targeted region re-analysis for detected discrepancies, with bounded retries. If disagreement persists, show the original region and offer correction; do not mark it verified automatically.
8. Compile a versioned page model with stable IDs, parent relationships, source coordinates and media references. Keep imported source, machine transcription, approved corrections and AI enrichments in distinct layers.
9. Save the model and original figure assets to the tablet. Reopening the page should reuse them without another extraction request.

Gemini can still omit or alter words. The system therefore uses Gemini PDF vision, deterministic PDF evidence and source comparison together; it does not claim guaranteed perfect transcription.

### Reading-engine compiler

The compiler maps typed page elements onto prebuilt Compose components: heading, paragraph, sentence span, list, table, figure and caption. Paragraphs own sentence anchors; captions reference figures; enhancements reference source anchors.

The reader renders exact accepted transcription in logical order, with integrated original figures. It reflows for tablet orientation while retaining source links. Original-layout viewing is always available for ambiguous reading order or diagrams. AI supplies data and layout intent, never executable Compose, HTML, CSS or extraction scripts.

## 2. Command-driven mentor and persistent context

There is no mandatory import-to-revision workflow. Library actions, navigation and study activities can be interleaved. Verification, practice and voice are optional at the moment of use.

### Example behavior

| User request | Application behavior |
|---|---|
| "I want to study Cell: the Unit of Life" | Resolve the saved chapter and open its last reading position, or its first page on first use. |
| "Study page 5" | Resolve the page in the active chapter, display its source, reuse or initiate its engineering job, and open reconstructed content when available. |
| "Open that PDF" | Show the original document associated with the current chapter or recent reference. |
| "Next page" | Move to the next page in the active chapter range. Reuse saved reconstruction or engineer it on demand. |
| "Explain the second sentence" | Resolve the sentence in the currently focused paragraph, highlight it and create a source-linked explanation. |
| "Show the diagram" | Resolve a figure related to the focused source, or offer the relevant figures if the reference is ambiguous. |
| "Pause this question and show the paragraph" | Preserve quiz input, focus its supporting passage, and keep the quiz restorable. |
| "Continue my test" | Resume the same suspended quiz with its answers and progress. |
| "Show the PDF and quiz together" | Compose both through the registered layout engine without a new screen implementation. |

The mentor maintains current document/chapter/page, confirmed paragraph and sentence focus, visible components, active/suspended activities, recent references, reading history and relevant learning observations. It does not infer eye position or comprehension from scrolling.

Simple known navigation executes locally once intent is resolved. Semantic or ambiguous requests use Gemini with the smallest sufficient context. A missing chapter produces a useful library/import action; multiple matching PDFs or page-number mappings trigger a concise clarification rather than an arbitrary choice.

File-page indices and printed textbook page labels are stored separately. Bare page numbers use the displayed navigation numbering; explicitly named printed page numbers use the saved mapping. If no reliable mapping exists, the app asks rather than silently opening the wrong page.

Page-engineering results belong to their document/page/job IDs. Navigating away may allow a job to finish and cache its result, but that result must not replace the newly focused page. "Stop" cancels the current mentor action or speech; it does not delete the source or activity history.

### Application tool contracts

Add versioned tools for FIND_CHAPTER, OPEN_CHAPTER, OPEN_DOCUMENT, OPEN_PAGE, NEXT_PAGE, PREVIOUS_PAGE, ENSURE_PAGE_ENGINEERED and SHOW_ORIGINAL. Connect these with FOCUS_SOURCE, SHOW_DIAGRAM, INSERT_ENRICHMENT, CREATE_ACTIVITY, SUSPEND_ACTIVITY, RESUME_ACTIVITY and UPDATE_LAYOUT.

All tools validate references, permissions and capabilities. The application remains the execution authority. Tool results update the mentor's context with what actually happened. PDF text and figure labels are untrusted content, not application instructions.

## 3. Revised development phases

### Phase 1 — Native PDF workspace and reliable local control

Build the Android app shell and installable APK, chapter-PDF import, page-range mapping, original PDF rendering, page navigation, Room persistence, bookmarks/notes and activity recovery. Establish the native component registry, validated commands, composition foundation and context store. Implement typed-command and touch controls for chapter/page navigation, so the same tools can later be called by Gemini.

Move the existing PDF processing code behind a private authenticated service, preserving the original-source rules. Define the page-engineering contract, job identity, cache key and reader compiler interfaces before connecting live AI. Port the useful prototype invariants and tests into Android.

**Completion:** the tablet opens an imported chapter PDF, navigates independently of a PC, preserves current page and activities across restart, and supports source viewing and study controls in any order. Real AI page reconstruction is completed in Phase 2; Phase 1 does not substitute OCR output and claim it is verified NCERT reconstruction.

### Phase 2 — Gemini page engineering and flexible AI-operated learning

Implement the Gemini-plus-PDF algorithm, original image/region extraction, reconstruction validation, contextual verification, compiled reading engine and durable caching. Connect Gemini tool calling to library navigation and contextual study actions. Add contextual questions/evaluation, comparison tables, explanations, diagram exercises, recall cards, concept maps and process views through registered components.

Complete split/inline/floating/overlay/minimized compositions, pins, snapshots, layout undo, attention protection and the component explorer. No fixed sequence or permanent AI sidebar is required.

**Completion:** with a real chapter PDF, spoken intent entered as text can open a chapter, select an unprocessed page, engineer it, explain a sentence, show its original figure, start practice, reopen the PDF, navigate forward, and resume the paused question. Every generated resource has valid source references. Previously processed pages reopen without additional engineering calls.

### Phase 3 — Voice control, learning memory and finished release

Connect microphone/transcription and interruptible speech to the same command tools. Add streaming voice only through a provider supported by the user's configured project; free-tier availability must be checked. Keep text controls and device speech output as fallbacks. Preserve compact voice UI while reading or exploring a figure.

Add durable retrieval history, performance-informed revision, optional guided study, backup/restore, database upgrades, tablet gesture refinement, accessibility and performance profiling. Test quota exhaustion, poor connectivity, app termination, overlapping jobs and failed commands. Package the signed private Android release and deploy the authenticated backend once its credentials and hosting configuration are available.

**Completion:** natural voice commands control chapter/PDF/page navigation and learning activities on the target tablet; the full workflow remains user-directed; state survives interruption and upgrades; cached learning remains available offline; new online AI actions fail clearly when the free API is unavailable.

## Acceptance tests and operating limits

- Compare reconstructed text and figure crops against actual NCERT PDFs, including digital pages, scanned pages, multi-column layouts, tables, captions and vector figures. Automated checks supplement human source comparison.
- Test flexible ordering: original PDF first, practice before explanation, diagram during a quiz, chapter switching and later return.
- Test commands with ambiguous chapter names, printed/file-page mismatches, first/last-page boundaries and references to suspended activities.
- Test invalid model commands and stale jobs without workspace corruption or unwanted navigation.
- Test caching, approved-correction preservation, duplicate imports, retry limits, provider rate limits and offline recovery.
- Test Android orientation, touch/stylus conflicts, microphone denial, speech interruption, process termination and update migrations.

Use one configured free-tier provider project, visible usage/error reporting, page caching, targeted context and bounded retries. Do not evade quotas or automatically switch to paid models. Free AI API access does not make backend hosting automatically free. Live extraction accuracy and free-tier capabilities require testing with the actual API project.

## Official provider references

- Gemini PDF understanding: https://ai.google.dev/gemini-api/docs/document-processing
- API quotas: https://ai.google.dev/gemini-api/docs/rate-limits
- Model pricing and free-tier availability: https://ai.google.dev/gemini-api/docs/pricing
