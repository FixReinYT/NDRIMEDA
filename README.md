# NDRIMEDA

NDRIMEDA is a native Android app built with Kotlin and Jetpack Compose. It is not an HTML app and does not use WebView.

## Closed beta build

The current beta is intentionally **offline-only** so testers can open the app without a server account or network setup.

- Choose a display name and enter the app.
- Send test messages in the local feed.
- Messages are saved on the device and remain after restarting the app.
- Up to 300 recent local messages are retained.
- Each tester's feed is local to their own device; messages are not shared between testers.

This is a UI and local-storage beta. Online accounts, shared chats, private conversations, real-time delivery, and server sync are not enabled in this build. Do not use it for sensitive messages.

## Build the Android APK

The GitHub Actions workflow builds a debug APK automatically when code is pushed. Open the repository's **Actions** tab, select the latest successful **Android APK** run, and download the **ndrimeda-debug-apk** artifact. The artifact is a ZIP containing the APK.

## Repository layout

- app/: native Android app and API client.
- backend/: FastAPI prototype with registration, login, and shared messages; not used by the current offline beta.
- database/: PostgreSQL schema notes.
- .github/workflows/ci.yml: automated Android build and backend syntax check.
- docker-compose.yml: local backend and PostgreSQL development setup.

## Planned after the offline beta

- Online accounts and shared messaging.
- Private one-to-one chats.
- Real-time delivery and notifications.
- Media uploads, account recovery, end-to-end encryption, and production hardening.

The backend is still a prototype and must be properly configured and secured before any public online launch.