# NDRIMEDA

NDRIMEDA is a native Android messenger prototype built with Kotlin and Jetpack Compose.

## Current beta status

- Native Android interface with local chats, groups, channels, voice notes, comments, pinned chats, saved messages, and light/dark themes.
- Offline entry and local chat history are available without a network connection.
- The Network tab can register/sign in on an HTTPS relay node and send direct text messages to another account on that same node.
- Relay endpoints can be configured in the app, but accounts and messages are not federated between different nodes yet.
- Do not use this prototype for sensitive conversations or treat it as a production messenger.

## Network architecture: what works and what does not

The current relay API provides account authentication and server-side delivery to a recipient's inbox. The recipient must have an account on the same relay endpoint. The inbox is refreshed manually in this beta; background push delivery is not implemented.

This is an initial internet messaging step, **not yet a decentralized network**. A genuinely decentralized design still needs interoperable federation between independently operated nodes, peer discovery, relay forwarding, abuse controls, and robust offline delivery.

## Security

- Messages sent through the current relay are stored as readable text on the relay server.
- End-to-end encryption and identity/key verification are not implemented.
- Do not send passwords, financial information, private documents, or other sensitive content in test messages.
- A successful APK build proves compilation only; it does not prove production reliability or security.

## Staged roadmap

1. Implement direct internet delivery through a configurable relay endpoint. (Initial beta implementation.)
2. Add interoperable federation and delivery across independent relay nodes.
3. Add independently reviewed end-to-end encryption and identity verification.
4. Add background notifications, reliable offline queueing, anti-abuse protections, and recovery.
5. Test across real devices, mobile carriers, and different network conditions.
6. Conduct independent security review before any production privacy claims.

## Backend

The `backend/` directory contains the FastAPI API and PostgreSQL persistence. The hosted relay must be deployed and configured with a database and a strong `JWT_SECRET`. Existing databases are migrated to add the recipient field without deleting existing messages.

## Build and download

GitHub Actions builds a debug APK for beta testing and publishes it under Releases. This is a testing build, not a Google Play release.
