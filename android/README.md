# Folio native Android

Open this directory in Android Studio. The Kotlin/Compose app opens a four-subject home, chapter libraries and an original-PDF viewer. Gemini and Groq are called directly from Android using user-entered keys encrypted with Android Keystore. PDF selection, deterministic text extraction, original-figure cropping and Room page caching run on the device. No backend, server URL, cloud account or hosting is required. Live Gemini checks passed; full voice-to-action acceptance remains pending. Recent assistant messages and failed commands are stored privately for recovery, with no automatic replay of interrupted app actions.

Build and check:

```powershell
.\gradlew.bat :core:catalog:test :core:runtime:test :data:local:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug exportDebugApk
python tools/check_architecture.py
```

Install the exported `artifacts/folio-debug.apk`. Choose a subject and chapter, add a PDF through Android's picker, then open its original pages. In Assistant settings, privately enter your Gemini and Groq keys; Folio automatically selects Gemini 3.8 Flash (gemini-3.8-flash, verified 15 October 2026 as the latest stable Flash with a free tier). Processing sends only the selected PDF page to Gemini; code extracts original figure regions and saves validated unchecked source pages locally. Tap Speak, talk, then pause: local end-of-speech detection finishes capture, Groq Whisper Large v3 transcribes, and the words automatically go to the same Gemini command path. Stop/Review are optional fallback controls. UI explorer contains labelled fixtures, not imported textbook evidence.

With an authorized USB-debugging tablet or working emulator:

```powershell
adb install -r artifacts/folio-debug.apk
# Install the current app-debug-androidTest.apk from the configured build output with -r.
adb shell am instrument -w -r com.example.read.test/androidx.test.runner.AndroidJUnitRunner
```

Build output defaults to Gradle's private cache when the checkout is inside OneDrive, avoiding synced-directory locks. `exportDebugApk` copies the current app build into the stable artifacts path; do not install old APKs under previous build directories. `-PfolioBuildRoot=<absolute-path>` overrides build storage; `-PfolioTestRoot=<absolute-path>` overrides JVM test reports.

Use update-in-place installation to preserve user data. The Gradle connected-test runner may uninstall its packages after testing; do not use it against a tablet holding study data.

Read ARCHITECTURE.md, DIRECT_PROVIDER_ARCHITECTURE.md, IMPLEMENTATION_STATUS.md and COMPONENT_CATALOG.md before extending the app. Provider prompts are packaged from api-contracts/prompts/. User API keys belong only in encrypted device storage and provider authentication headers, never chat, source code, workspace snapshots, prompts or logs.

Reader annotation panels support notes, highlights and underlines separately from source wording. Unfinished note/edit drafts recover locally and are excluded from Gemini requests. Active note writing protects the workspace from AI actions until saved, canceled or discarded; ordinary touch navigation remains available. Source selection/stylus protection and full production acceptance are still pending.
