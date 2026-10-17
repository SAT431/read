# Complete prebuilt UI target catalogue

The supplied UI specification names 111 target components. This is the complete inventory, not a claim that all are implemented. Existing representative renderers are expanded into this catalogue across the three development phases. Every shipped interactive capability must have a stable action contract and source/activity lifecycle tests.

Status meanings: implemented = a concrete native implementation exists, with device QA still pending; partial = a representative or limited behavior exists; planned = no native implementation yet. Phase 2 connects source/AI/voice behavior and expands components; Phase 3 completes refinements and acceptance gates.

| ID | Component | Current status | Main remaining phase |
|---|---|---|---|
| A01 | Adaptive Workspace Canvas | Implemented | 1 / device QA |
| A02 | Contextual Command Bar | Planned | 2–3 |
| A03 | Universal Activity Switcher | Implemented | 1 / device QA |
| A04 | Floating Component Container | Planned | 2–3 |
| A05 | Intelligent Split Container | Partial | 2–3 |
| A06 | Compact Activity Capsule | Implemented | 1 / device QA |
| A07 | Contextual Dock | Planned | 2–3 |
| A08 | Workspace Overview | Partial | 2–3 |
| A09 | Focus Mode Surface | Partial | 2–3 |
| B01 | Chapter Library Explorer | Implemented | 1 / device QA |
| B02 | Chapter Card | Implemented | 1 / device QA |
| B03 | Page Collection Viewer | Planned | 2–3 |
| B04 | Page Upload Surface | Implemented | 1 / device QA |
| B05 | Engineering Processing Card | Partial | 2–3 |
| B06 | Original Page Preview | Implemented | 1 / device QA |
| B07 | Extraction Verification Workspace | Partial: original/reconstruction comparison, token examples, uncertainties, correction approval/drafts and original-region highlights | 2–3 / orphaned draft management, complex/rotated textbook fidelity and full acceptance |
| B08 | Chapter Progress Timeline | Planned | 2–3 |
| B09 | Recently Studied Content | Partial | 2–3 |
| C01 | Main NCERT Reading Surface | Partial | 2–3 |
| C02 | Paragraph Component | Partial | 2–3 |
| C03 | Sentence Focus Layer | Partial: stable source IDs and native sentence taps | 2 / normal-large text taps device-tested; accessibility/selection-lock QA pending |
| C04 | Guided Reading Indicator | Implemented: explicit-report marker; native/live/enlarged-text tablet checks passed | 3 / broader accessibility, source-change and lifecycle QA |
| C05 | Reading Progress Indicator | Implemented: page confirmation counts; native/live/enlarged-text tablet checks passed | 3 / broader progress histories and acceptance |
| C06 | Text Selection Interface | Implemented | 1 / device QA |
| C07 | Source Annotation Layer | Partial: notes/marks, private recovery, cross-reader orphan overview/search, native explicit draft/separate-note controls | 2–3 / stylus, annotation history, multiple-view and accessibility QA |
| C08 | Inline Explanation Block | Partial | 2–3 |
| C09 | Source-to-Enhancement Connector | Partial | 2–3 |
| C10 | Reading Bookmark | Implemented: exact targets, marker/list and shared actions; native/storage/live/enlarged-text tablet checks passed | 3 / broader accessibility, source-change and lifecycle QA |
| C11 | Original/Reconstructed View Switcher | Planned | 2–3 |
| C12 | Scientific Caption Component | Planned | 2–3 |
| C13 | Reading Appearance Controls | Implemented | 1 / device QA |
| D01 | Minimal AI Speech Bubble | Planned | 2–3 |
| D02 | Expanded AI Response Panel | Partial | 2–3 |
| D03 | Inline AI Explanation | Partial | 2–3 |
| D04 | AI Conversational Timeline | Partial: bounded persisted conversation and execution acknowledgements | 2–3 / timeline refinement and process-death UI QA |
| D05 | Streaming AI Response | Planned | 2–3 |
| D06 | AI Thought/Processing Status | Planned | 2–3 |
| D07 | AI Action Announcement | Planned | 2–3 |
| D08 | AI Suggestion Chips | Planned | 2–3 |
| D09 | AI Source Reference | Partial | 2–3 |
| D10 | AI Confirmation Interface | Planned | 2–3 |
| D11 | AI Error and Recovery Message | Partial: classified HTTP/connection/Gemini protocol failures, mentor deadline and fresh-context retry | 2–3 / live provider reliability, full recovery/accessibility acceptance |
| E01 | Minimal Microphone Control | Implemented | 2 / user capture reached Gemini; full live action QA pending |
| E02 | Active Listening Indicator | Partial | 2–3 |
| E03 | Live Voice Waveform | Planned | 2–3 |
| E04 | User Speech Transcription Surface | Implemented | 2 / user transcript observed; broader language/noise QA pending |
| E05 | AI Speaking Indicator | Planned | 2–3 |
| E06 | AI Voice Caption Surface | Planned | 2–3 |
| E07 | Voice Session Controller | Partial | 2–3 |
| E08 | Push-to-Talk Control | Planned | 2–3 |
| E09 | Voice Activity Status | Partial | 2–3 |
| E10 | Expanded Voice Interface | Partial | 2–3 |
| E11 | Voice Error and Permission UI | Partial | 2–3 |
| E12 | Voice-to-Content Focus Link | Planned | 2–3 |
| E13 | Voice Interruption Indicator | Planned | 2–3 |
| F01 | Question Presentation Surface | Implemented | 1 / device QA |
| F02 | Multiple-Choice Answer Options | Implemented | 1 / device QA |
| F03 | Question Number Indicator | Implemented | 1 / device QA |
| F04 | Question Progress Rail | Implemented: question chips and explicit completion states | 2 / fixture device-tested; broad accessibility QA pending |
| F05 | Practice Timer | Implemented: advisory native controls; UI/storage/lifecycle/live/enlarged-text tablet checks passed | 3 / broader UI/write-interruption recovery and performance acceptance |
| F06 | Answer Submission Control | Implemented | 1 / device QA |
| F07 | Evaluation Feedback Component | Implemented | 1 / device QA |
| F08 | Mistake Explanation Panel | Implemented | 1 / device QA |
| F09 | Question-to-NCERT Reference | Partial: exact source links, passage focus and retained quiz state | 2–3 / real generated-question/source fidelity QA |
| F10 | Paused Practice Capsule | Implemented | 1 / device QA |
| F11 | Question Review Workspace | Partial: current and read-only saved-attempt review, original key/source links and retained drafts | 2–3 / dedicated review layout and real-content acceptance |
| F12 | Active Recall Input | Implemented: source-linked freeform response, private draft recovery and reveal locking | 2–3 / broader accessibility/process-death and real-content acceptance |
| F13 | Confidence Selector | Implemented: optional explicit pre-check reports, selected-answer binding, actor/time and checked locking | 2–3 / broader accessibility and real-content acceptance |
| F14 | Diagram Label Quiz | Planned | 2–3 |
| G01 | Scientific Diagram Viewer | Partial | 2–3 |
| G02 | Diagram Label Highlight | Planned | 2–3 |
| G03 | Diagram Annotation Overlay | Planned | 2–3 |
| G04 | Image Comparison Viewer | Planned | 2–3 |
| G05 | Interactive Process Flow | Planned | 2–3 |
| G06 | Concept Relationship Map | Planned | 2–3 |
| G07 | Visual Step Controller | Planned | 2–3 |
| G08 | Figure-to-Paragraph Connector | Planned | 2–3 |
| G09 | Expanded Visualization Canvas | Partial | 2–3 |
| G10 | Diagram Recall Mask | Planned | 2–3 |
| H01 | Comparison Table | Implemented | 1 / device QA |
| H02 | Key Points Component | Planned | 2–3 |
| H03 | Active Recall Card | Partial: source-linked prompt/reference, optional response, reported recall ratings and retained attempts | 2–3 / concept-level personalization and real-content acceptance |
| H04 | Fill-in-the-Blank Component | Planned | 2–3 |
| H05 | Concept Comparison Surface | Partial | 2–3 |
| H06 | Mnemonic Component | Planned | 2–3 |
| H07 | Scientific Sequence Component | Planned | 2–3 |
| H08 | Misconception Clarifier | Planned | 2–3 |
| H09 | Expandable Deep Explanation | Partial | 2–3 |
| H10 | Recall Checklist | Planned | 2–3 |
| H11 | Learning Unit Summary | Planned | 2–3 |
| I01 | Learning Progress Overview | Partial: native checked-answer/recall counts and confidence filters | 3 / reading, concepts and full-session aggregation |
| I02 | Page Mastery Indicator | Planned | 3 |
| I03 | Weak Concept List | Planned | 3 |
| I04 | Revision Queue | Partial: persistent due/upcoming/completed queue, explicit timing, native practice-based suggestions, exact resume and reported history; touch/AI actions | 3 / concept-level personalization, retention effectiveness and broader lifecycle/accessibility QA |
| I05 | Mistake History | Partial: source-linked checked mistakes across attempts, confidence/time/actors and exact read-only resume | 3 / concept-level tracking and complete learning timeline |
| I06 | Memory Retention Visualization | Planned | 3 |
| I07 | Study Session Summary | Partial: quiz completion/correctness summary | 3 / reading, revision and full-session aggregation |
| I08 | Session Resume Card | Partial | 2–3 |
| I09 | Learning Timeline | Planned | 3 |
| J01 | Universal Search | Partial: native cross-content metadata/cached-passage/bookmark/note/quiz-history/recall search with guarded pagination; full-PDF/semantic coverage pending | 2–3 |
| J02 | Source Location Breadcrumb | Implemented | 1 / device QA |
| J03 | Contextual Action Menu | Planned | 3 |
| J04 | Undo/Redo Control | Implemented | 1 / device QA |
| J05 | Workspace Restore Control | Partial | 2–3 |
| J06 | Notification Toast | Planned | 3 |
| J07 | Error Recovery Surface | Implemented | 1 / device QA |
| J08 | Settings Panel | Implemented | 1 / device QA |
| J09 | Component Resize Handle | Planned | 3 |
| J10 | Contextual Help Surface | Planned | 3 |
| J11 | Keyboard and Stylus Shortcut Guide | Planned | 3 |
| J12 | Offline and Sync Status | Planned | 3 |

## Added home/library primitives

The NEET home adds separately implemented SubjectGrid, SubjectCard, ChapterSearch, ChapterRow, ChapterLibraryScreen, EmptyLibrary, PdfCard, OriginalPdfScreen, AppearanceScreen, DestinationTab, AppTopBar, SemanticButton and the IconRegistry. These appear in COMPONENT_BUILD_PLAN.md.

The six learning registry entries are reader, quiz, explanation, comparison, diagram and recall. The UI inventory includes composed surfaces and controls; they become AI-addressable through semantic actions rather than requiring a Gradle module or renderer registry entry for every label. The separate quiz confidence selector and native practice-based revision suggestions are now implemented; concept-level personalization and learning-effectiveness acceptance remain open.

Limits: registered UI actions do not grant OS permission, bypass pins, execute model-generated code or make an unavailable provider functional. Live microphone/Groq/Gemini and complete source reconstruction belong to Phase 2.
