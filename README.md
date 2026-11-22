# LAMIA AI (LAMU) Android starter

## Build APK
1. Open this project in Android Studio (JDK 17, Android SDK 35).
2. Sync Gradle and run `:app:assembleDebug`, or push to GitHub and run Actions workflow.
3. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Backend setup (required for AI replies)
Deploy `backend/` to a trusted HTTPS server. Install Node.js 20+, run `npm install`, then set:
- `AI_API_URL` = your AI provider's chat-completions endpoint
- `AI_API_KEY` = secret API key (server environment secret only)
- `AI_MODEL` = model name
- `PORT` = optional port

Never put the key in Android source or GitHub repository. Use server-side rate limiting/authentication before public deployment. The Android app currently has a placeholder backend URL in `AppConfig.kt`; replace it with your deployed HTTPS endpoint before building.

## Implemented
- Dark Compose UI, chat input/history in current session
- Android speech recognition request (Bengali locale; device service may support English)
- Android TextToSpeech Bengali preferred, falls back to system English
- Optional foreground microphone service with persistent notification and Stop action
- Backend proxy endpoint with key held server-side
- Educational trading system prompt (no guaranteed signals)

## Not implemented / limitations
- On-device wake-word detection for "Lamu/লামু": no wake-word model/engine bundled. Microphone service does NOT listen for wake word; notification explicitly says so. Add a licensed offline engine and model to implement it.
- Speech recognition is delegated to installed Android recognition provider; Bengali/English availability and female voice depend on device/provider.
- Chat history is in-memory only; app restart clears it.
- User memory controls and persistent editable memory are not implemented yet.
- No live market feed, trading signal engine, Quotex integration, or automatic trading. Trading is educational only.
- Mic service is a permissioned foreground service placeholder, not continuous wake-word recognizer.
- This starter has not been built or device-tested in this environment; do not treat it as a verified release APK.

## Privacy
Audio is handled by Android's speech recognition service when you tap the microphone. The optional service shows a persistent notification and can be stopped. Review the recognition provider's privacy policy. No audio is sent to the LAMIA backend by this starter; only typed/recognized text is sent when you ask the AI.
