# Folio — personal Biology workspace

The native Android implementation is under `android/`. It calls Gemini and Groq directly with user-entered keys encrypted on the device; PDF processing and caching are native. No hosting/backend is required. See `android/DIRECT_PROVIDER_ARCHITECTURE.md` and `android/README.md`. The web application below is a separate historical prototype.

A runnable first vertical slice of the two supplied specifications. React + strict TypeScript + Vite, a schema-validated adaptive runtime, and a local FastAPI / SQLite / PyMuPDF backend. This is an incremental implementation, not the completed target catalog.

## Run locally

Requires Node 22+ (tested on 24), pnpm, and Python 3.12+.

```powershell
pnpm install
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn backend.app:app --host 127.0.0.1 --port 8000
# In a second terminal:
pnpm dev
```

Open http://127.0.0.1:5173. The development server proxies `/api` and `/assets/source` to Python. `start.ps1` starts both services using the installed local dependencies and prints the address. Stop its two processes when finished.

## Real AI

Set `GEMINI_API_KEY` and `GEMINI_MODEL` in the backend process environment before starting it. Select a model available to your Google account that supports generateContent JSON responses. No credential belongs in `VITE_*`, source control, or the browser. Without both settings the UI explicitly uses deterministic **fixture mode**. Fixture generation only works for the supplied original sample, not arbitrary uploaded text. This sample is not represented as NCERT content.

The Gemini adapter uses Google's REST generateContent API, a JSON response mode and strict client-side validation before state execution. Live provider calls were not tested because no API credentials were supplied. See https://ai.google.dev/gemini-api/docs/generate-content/structured-output for the provider contract. Model names are configurable rather than assumed to be universally available.

## Try the integrated workflow

1. Open the app and select Quiz me. Select an answer without submitting it.
2. Pause & review source. Resume through the Active recall capsule or type “continue my test”.
3. The same answer is selected. Reload and check again. Submit it to record a retrieval observation and next review date.
4. Make a comparison. The same composition runtime shows the table next to the reader. Pin a component, focus another, and undo layout.
5. Upload a PDF and select its 1-based file page. Verify the reconstructed text against the source PNG; approve corrections.
6. Upload another page under the same chapter title. Both remain in the library. Identical uploads return the existing approved page.
7. Open UI explorer for the live registry, composition playground and command trace.

## Storage and privacy

`data/folio.sqlite3` stores pages, correction revisions, workspace and provider token counts. Source assets are under `data/`. Back up the whole folder while the server is stopped. Browser localStorage is an immediate recovery cache, not the sole durable store. Save requests are serialized with revision conflict detection. Reload to resolve a conflict with another tab. Pending browser edits take precedence over an older backend snapshot.

This is a loopback-only, single-user development service, without authentication or TLS. Do not bind it publicly. Tablet access over a network needs a separate authenticated HTTPS deployment; that is not included. Google Fonts are fetched for typography, with local font fallbacks. Uploaded page excerpts are sent to Gemini only when a configured mentor request is made.

## Validation

```powershell
pnpm build
pnpm test
.\.venv\Scripts\python.exe -m pytest backend -q
```

Tests cover atomic rejection, pin protection, invalid content, arbitrary composition, state round trips, layout-only undo, PDF extraction, corrections, duplicate imports, revision conflicts, and origin restrictions. Backend tests generate their own synthetic PDF in memory; no actual NCERT document was provided for acceptance testing.

See `UI_IMPLEMENTATION_STATUS.md` for explicit limits and the six design documents for extension contracts.
