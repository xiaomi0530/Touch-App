# Touch

Touch is an Android app for recording real-world "tap-to-meet" interactions, managing friends and profiles, tracking calendar-based meeting history, and collecting staged badges. The repository also includes a lightweight Python backend for local development and device testing.

## What is included

- `Touch/`: Android client written in Kotlin and Jetpack Compose.
- `TouchBackend/`: local Python backend used for login, friends, meetings, badges, and event storage during development.
- `SECURITY_STRATEGY.md`: security roadmap and data-handling rules.
- `AGENTS.md`: persistent project specification for future code changes.

## Main features

- Account registration and login.
- NFC tap-to-meet flow with backend confirmation.
- Friend search, requests, remarks, blocking, and removal.
- Calendar views for week, month, and year meeting history.
- Personal profile card editing with avatar and background image uploads.
- Staged badge collection system.
- Local note photo transfer: select images on the phone, package them into a zip, then share or save it locally.

## Development setup

### Backend

```powershell
cd TouchBackend
python .\server.py
```

The backend is for development and testing. It is not production hardened.

### Android app

Open `Touch/` in Android Studio and run it on an emulator or device. The app is currently wired for local development and testing against the backend.

## Public release notes

This repository is kept public-friendly by excluding runtime data, secrets, local databases, build outputs, and private environment files from Git.

Production deployment still requires:

- HTTPS-only API hosting.
- Keystore-backed session storage on Android.
- Hardened upload handling.
- Device-signature NFC proof validation.

## Documentation

- [Backend README](TouchBackend/README.md)
- [Security strategy](SECURITY_STRATEGY.md)
- [Agent specification](AGENTS.md)
