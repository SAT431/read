# Private native API service

**Superseded:** Folio now calls Gemini/Groq directly from Android and processes/caches PDFs on the device. This service is a historical experiment, not an app prerequisite. Do not set up hosting for Folio. See android/DIRECT_PROVIDER_ARCHITECTURE.md. The remainder records the earlier service design.

This service is separate from backend/app.py, the earlier local browser prototype. It implements authenticated Gemini orchestration, Groq Whisper Large v3 transcription and selected-page PDF engineering. Native text, selected-page engineering/cache and voice transcription adapters are wired. The native app can keep transcripts for review or forward them automatically using a persisted preference. It is not yet deployed or verified with live providers. [Strictly free hosting setup](HOSTING_SETUP.md) describes the prepared VM/HTTPS deployment files and pending account steps.

Provider keys and model identifiers come only from the server environment. Create a random FOLIO_ACCESS_TOKEN of at least 32 printable ASCII characters and configure GEMINI_API_KEY, GEMINI_MODEL and GROQ_API_KEY privately. The tablet receives only the service URL and access token. Never paste keys into chat or place them in tracked files. service/.env.example lists names, not secrets. Select a model available to your actual free-tier project; the service does not select a paid fallback, retry provider calls or change your billing settings.

From the repository root, after setting private environment variables:

```powershell
python -m uvicorn service.app:create_app --factory --host 127.0.0.1 --port 8000 --workers 1 --no-access-log
python -m pytest service/tests -q
```

Production hosting must terminate HTTPS and forward only to this service, with one worker for the current single-user rolling budget. Container and Compose recipes are provided, but build/deployment have not been executed. Do not expose the development command as a production deployment. Set proxy body limits to 16 MiB for ordinary requests and 101 MiB for the bounded PDF multipart route; configure secret rotation and disable body/auth-header logging.

Wire endpoints:

- GET /healthz returns minimal unauthenticated liveness.
- GET /v1/status reports configured capabilities without returning secrets.
- POST /v1/mentor accepts version 1 MentorRequest with utterance, requestId, capabilityContext, sourceContext and executionResults. Returns message and a nullable AppCommandBatch. Strict outer action shape, advertised availability, IDs and revision are validated before delivery. Android's strict decoder/runtime is still required for nested educational-data/source/state/pin/interaction validation; the server does not execute commands.
- POST /v1/transcribe accepts multipart file (maximum 12 MB), optional language en/hi; omission enables language detection. Uses whisper-large-v3 transcription, not translation or action planning. No audio is persisted by this service.

- POST /v1/documents streams multipart PDFs to immutable checksum-addressed storage (maximum 100 MB).
- GET /v1/documents/{documentId}/pages/{zeroBasedIndex}/evidence returns deterministic text/image evidence.
- POST /v1/documents/{documentId}/pages/{zeroBasedIndex}/crops accepts four finite source bounds and renders original pixels.
- GET /v1/assets/{mediaId} serves original crops under authentication.
- POST /v1/documents/{documentId}/pages/{zeroBasedIndex}/engineer reconstructs one selected page with Gemini and caches the native-compatible page plus a separate fidelity report. Cache identity includes immutable document checksum, page and engineering version. All returned machine pages remain UNCHECKED, with no approved corrections. Missing/additional token counts flag discrepancies; they do not prove fidelity or replace visual comparison, especially for scans.

Configure FOLIO_DATA_ROOT on private persistent storage; the container provides /app/data owned by the service user. Container execution/deployment remains unverified. Backups, retention/deletion controls and worker resource hardening remain release requirements. Status capability flags describe configured adapters, not successful live-provider verification.

Every endpoint except healthz requires Authorization: Bearer <service access token>. Requests are counted before provider execution, concurrency is bounded, oversized bodies are rejected before parsing, errors omit provider bodies and no redirect is followed. Current tests use HTTP mock providers; they do not prove live credentials or free quota availability.

Native data:remote implements HTTPS transport and typed mentor, PDF and transcription envelopes. AssistantViewModel instantiates providers from Android Keystore-encrypted service settings. Native text requests, execution acknowledgements, PDF engineering and reviewed recording/transcription are wired. Actual microphone capture, hosted HTTPS and live-provider verification remain pending.

Provider references: [Gemini generateContent](https://ai.google.dev/api/generate-content), [Groq speech-to-text](https://console.groq.com/docs/speech-to-text).
