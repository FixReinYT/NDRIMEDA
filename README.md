# NDRIMEDA

NDRIMEDA is a native Android messenger project. The Android client uses Kotlin and Jetpack Compose; it is not an HTML app and does not use WebView.

## Repository layout
- `app/`: native Android app module.
- `backend/`: FastAPI API with account registration, login, and a shared message feed.
- `database/`: database notes.
- `.github/workflows/ci.yml`: cloud Android APK build.
- `docker-compose.yml`: backend and PostgreSQL services.

## Current state
The repository now contains the first native Android screen and an API starter. The Android screen is not yet connected to the API; account registration and messaging integration in the app are a next implementation step. The API supports registration/login and shared messages, not private one-to-one chats yet.

## Backend development run
1. Install Docker and Docker Compose.
2. Run `docker compose up --build`.
3. Check `http://localhost:8000/health`.

Before deploying publicly, set strong unique `POSTGRES_PASSWORD` and `JWT_SECRET` values using environment variables or your host's secret manager. Never commit real secrets. Use HTTPS for public access.

## Android APK
Open this repository in Android Studio, or open the Actions tab and run the Android APK workflow. The debug APK is uploaded as an Actions artifact after a successful build.

## Not implemented yet
Private chats, real-time delivery, push notifications, end-to-end encryption, media, account recovery, database migrations, and production hardening.