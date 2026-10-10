# NDRIMEDA

NDRIMEDA is a native Android messenger project with a Python backend and PostgreSQL database.

## Components
- `android/`: native Kotlin + Jetpack Compose Android app (no HTML, no WebView).
- `backend/`: FastAPI REST API with bearer-token authentication.
- `database/`: database notes and schema documentation.
- `.github/workflows/android.yml`: cloud APK build.

## First development milestone
The starter implementation provides account registration/login and a shared message feed backed by PostgreSQL. This is a development MVP, not a production-ready secure messenger yet. Do not use a real password you use elsewhere.

## Run the backend locally
1. Install Docker and Docker Compose.
2. Run `docker compose up --build`.
3. API health check: `http://localhost:8000/health`.

Set `JWT_SECRET` to a long random secret before exposing the API publicly. Keep secrets out of Git.

## Android
Open the `android` directory in Android Studio, or use the GitHub Actions workflow to build a debug APK. In the app, set the server URL to the reachable HTTPS address of your deployed backend. `localhost` on a phone means the phone itself, not your server.

## Current limitations
- The message feed is shared between registered users; private one-to-one conversations are not implemented yet.
- No push notifications, end-to-end encryption, media uploads, or account recovery yet.
- Deploy the backend behind HTTPS and configure secrets before real-world use.