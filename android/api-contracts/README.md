# Next API phase contract

Current architecture uses direct Android Gemini/Groq adapters, encrypted device keys and native PDF extraction/cache. Prompts in this directory are packaged as Android assets. References below to private-service hosting are historical and superseded by DIRECT_PROVIDER_ARCHITECTURE.md.

Confirmed providers: Groq Whisper Large v3 for speech-to-text; Gemini for contextual reasoning, PDF reconstruction and semantic UI orchestration. Credentials remain on the private backend. This directory supplies the orchestration prompt used by service/providers/gemini.py. The private service implements mentor, transcription and PDF-engineering HTTP endpoints; data:remote implements their native adapters. Native recording supports review or optional automatic forwarding through the same assistant. Deployment, live credentials and actual microphone capture checks remain pending.

The control bus is AppCommandBatch -> AppCommandCodec -> AppCommandExecutor -> WorkspaceSession. AppActionRegistry exposes IDs, arguments, platform-interaction requirements and capability availability. AppCapabilityContext exports actual subjects, chapters, PDFs, navigation, preferences, study context and existing activity/component state, including explicit protocol/default fields. Existing workspace commands continue through workspace.command. Touch and future AI invoke the same handlers; no click-coordinate automation is needed.

The next implementation must return a message and an AppCommandBatch matching these types, validate it on the server, execute it locally, and return an execution result to the mentor. Add live microphone/recording/STT/TTS actions only when their adapters work. Provider prompts describe capabilities; they cannot enable an unimplemented function.

Use server-side provider keys and user project quotas. Groq Whisper handles audio transcription; Gemini plans supported app actions. Native PDF viewing and cached learning stay available when online providers fail.

Mentor wire envelope: schemaVersion=1, requestId, utterance, capabilityContext, sourceContext and executionResults. Response: schemaVersion=1, requestId, message and batch (null for clarification/no action). The server validates outer action shape/availability/references; Android remains responsible for complete nested educational/state/source/pin validation before execution. See service/README.md.
