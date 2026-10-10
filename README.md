# NDRIMEDA

NDRIMEDA is a native Android messenger prototype built with Kotlin and Jetpack Compose.

## Current beta status

- Native Android interface with local chats, groups, channels, voice notes, comments, pinned chats, saved messages, and light/dark themes.
- Offline entry is available without account registration.
- Messages and voice recordings are currently stored locally on the device.
- The Network screen documents the intended decentralized design and explicitly indicates that peer-to-peer transport is not yet implemented.
- Do not use this prototype for sensitive conversations or treat it as a production messenger.

## Decentralized networking goal

The project aims to support long-distance messaging without dependence on one mandatory central server. This is a design goal, not a current feature.

The implementation must solve peer discovery, NAT traversal, relay/forwarding, offline delivery, abuse resistance, key verification, and end-to-end encryption. Decentralization does not remove the need for an available network path: two distant devices with no internet and no route through other nodes cannot exchange messages instantly.

A suitable staged plan is:

1. Define a transport-independent message envelope and identity/key model.
2. Implement peer discovery and connection establishment using multiple independent entry points rather than one hard-coded endpoint.
3. Add encrypted store-and-forward between reachable peers/relay nodes.
4. Add independently reviewed end-to-end encryption and identity verification.
5. Test across real devices, mobile carriers, NAT types, and offline/reconnect scenarios.
6. Conduct security review before any public claim of private or reliable messaging.

## Existing backend prototype

The backend/ directory contains an early FastAPI API prototype and render.yaml describes a hosted deployment. The existing API is not a decentralized transport and is not required for local-only use. Registration/login will only work if the API is deployed and configured.

## Build and download

GitHub Actions builds a debug APK for beta testing and publishes it under Releases. A successful APK build verifies compilation only; it does not prove that remote messaging or peer-to-peer networking works.

## Current limitations

- No real cross-device messaging or P2P transport is implemented yet.
- Local chats are not synchronized between accounts or devices.
- End-to-end encryption, notifications, account recovery, and production-grade anti-abuse protections are not implemented.
- The prototype requires functional testing and independent security review before production use.
