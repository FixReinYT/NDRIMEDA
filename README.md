# NDRIMEDA

NDRIMEDA is a native Android app built with Kotlin and Jetpack Compose.

## Beta 20 status

- The app now has a registration/login screen that calls the NDRIMEDA API.
- Registration requires a unique username and a password of at least 8 characters.
- Test conversations and the previous local profile are cleared once on first launch of Beta 20.
- Chat lists start empty; users create their own local chats and channels.
- Local chat messages, voice notes, channel posts, and comments are still stored on the device and are not synchronized between accounts yet.
- The app is an early public beta and must not be used for sensitive conversations.

## Server deployment

The API is a FastAPI prototype in `backend/`. `render.yaml` defines the Render web service, and the service expects a PostgreSQL connection string in the `DATABASE_URL` environment variable. Before registration can work:

1. Deploy the repository using the Render blueprint.
2. Set `DATABASE_URL` to a valid PostgreSQL connection string (the current blueprint expects an external Neon database).
3. Confirm that `https://ndrimeda-api.onrender.com/health` returns a JSON health response. If Render assigns a different hostname, update `API_BASE_URL` in `app/src/main/java/com/ndrimeda/MainActivity.kt` and rebuild.

GitHub Actions builds a debug APK for beta testing. A successful build does not itself deploy or host the API.

## Download

Open the repository's **Releases** page and download `app-debug.apk` from the latest beta release. Android may warn that the debug APK is not distributed through Google Play.

## Current limitations

- Online account registration and login require the API and PostgreSQL to be deployed and configured.
- Chats and messages are still local to each device; shared one-to-one and group messaging, notifications, account recovery, and end-to-end encryption are not implemented.
- The backend is a prototype and needs additional security review and integration testing before it should be treated as a production messenger.
