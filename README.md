# NDRIMEDA

NDRIMEDA is a native Android messenger project built with Kotlin and Jetpack Compose. It is not an HTML app and does not use WebView.

## Repository layout
- `app/`: native Android app and API client.
- `backend/`: FastAPI API with account registration, login, and shared messages.
- `database/`: PostgreSQL schema notes.
- `.github/workflows/ci.yml`: cloud debug APK build.
- `docker-compose.yml`: backend and PostgreSQL services.

## Current implementation
The Android app has registration/login forms, a shared message feed, refresh, and message sending. The backend API implements registration, login, password hashing, bearer-token authentication, and PostgreSQL persistence. The feed is shared by all accounts; private one-to-one chats and real-time delivery are not implemented yet.

## Run the backend for development
1. Install Docker and Docker Compose.
2. Run `docker compose up --build`.
3. Check `http://localhost:8000/health`.

Before exposing the API publicly, set strong unique `POSTGRES_PASSWORD` and `JWT_SECRET` values through environment variables or your hosting provider's secret manager. Never commit real secrets. Public traffic must use HTTPS. The default secrets are for local development only.

## Build the Android APK
Open the repository in Android Studio, or open the Actions tab and inspect the `Android APK` workflow. A successful run uploads `ndrimeda-debug-apk` as an artifact.

## Not implemented yet
Private chats, real-time delivery, push notifications, end-to-end encryption, media uploads, account recovery, versioned database migrations, and production hardening.