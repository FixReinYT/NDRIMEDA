# NDRIMEDA

NDRIMEDA is a native Android messenger prototype built with Kotlin and Jetpack Compose.

## Current beta status

- Native Android interface with local chats, groups, channels, voice notes, comments, pinned chats, saved messages, and light/dark themes.
- Offline entry and local chat history work without a network connection.
- The Network tab supports internet messaging through HTTPS relay nodes.
- Addresses can use the form `username@node.example` when the sender and recipient nodes have configured reciprocal federation trust.
- This is an early federation implementation, not a production-ready decentralized network.

## Federation setup

Each independently operated node must configure:
- `NODE_HOST`: the public hostname of that node, without `https://`.
- `FEDERATION_PEERS`: a JSON object mapping each trusted peer hostname to a pairwise shared secret, for example `{"peer.example":"replace-with-a-long-random-secret"}`.

Both nodes must list one another and use the exact same secret for that peer relationship. In the app, send to `username@peer.example`. Accounts must already exist on the destination node. Federation is opt-in; there is no mandatory global relay. Do not expose federation secrets publicly.

## Known limitations and security

- Inbox refresh is manual; push notifications and reliable background delivery are not implemented.
- The relay stores message text in readable form. End-to-end encryption (E2EE) and identity/key verification are not implemented.
- Federation currently uses configured shared secrets per peer; public-key node identity and safer key management are future work.
- Peer discovery, general open federation, abuse controls, and production-grade offline delivery are not implemented.
- Do not send sensitive information through this test build. A successful APK build only proves compilation.

## Backend

The `backend/` directory contains the FastAPI API and PostgreSQL persistence. The hosted relay needs a database and a strong `JWT_SECRET`. Database upgrades add fields without intentionally deleting existing messages.

## Build and download

GitHub Actions builds a debug APK for beta testing and publishes it under Releases. This is a test build, not a Google Play release.
