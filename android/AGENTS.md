# Native Android development rules

Read `ARCHITECTURE.md` and `IMPLEMENTATION_STATUS.md` before changing the native app.

Current user product/engineering directives are preserved in `development/requirements/PDF_HARNESS_DIRECTIVE.md` and `development/requirements/ENGINEERING_DIRECTIVE.md`, and `development/requirements/PDF_HARNESS_EXPANSION.md`. Apply them to subsequent work: complete verifiable user journeys, infer necessary supporting behavior, keep capabilities registered and native UI prebuilt, and report remaining gaps honestly. The current priorities are document management/engineering and modular AI orchestration; UI refinement must integrate those actual flows.

- Keep domain models/runtime free of Android and Compose imports. Keep feature modules independent of other features and concrete data/provider implementations. The app composition root wires adapters.
- Follow `architecture/module-boundaries.json`. Add a new module to this policy only with an intentional documented boundary change.
- Give production Kotlin files one focused responsibility. The review limit is 200 lines; split by meaningful behavior before exceeding it. Do not create empty or decorative scaffolding to inflate the architecture.
- Components, animations, gestures and interactions are engineer-written. AI may select validated capabilities and supply structured educational data. Never execute model-generated UI code or extraction scripts.
- Add component metadata and its prebuilt renderer together. Registry coverage must remain complete. Do not advertise unimplemented interactions, variants or gestures.
- Preserve source assets and machine transcription separately from corrections and enrichment. Validate stable source anchors and page ownership.
- Keep activity lifetime independent of visual instances. Hidden/suspended components cannot delete answers, annotations or reading positions. Layout undo cannot roll back learner input.
- Keep local interaction paths responsive and independent of network calls. Respect text selection, writing, pins, accessibility and reduced motion.
- Use clearly labelled fixtures until real provider/document adapters exist. Update implementation status honestly.
- Run `python tools/check_architecture.py` and relevant Gradle tests/builds. Instrumentation compilation does not count as an executed device test.
