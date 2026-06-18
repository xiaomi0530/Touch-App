# Touch Local Backend

Local development backend for the Touch Android app.

This server intentionally uses only the Python standard library so it can run before external backend dependencies are installed.

## Run

```powershell
cd C:\Users\xiaom\Desktop\Software\TouchBackend
python .\server.py
```

The API listens on:

```text
http://127.0.0.1:8000
```

For an Android emulator, use:

```text
http://10.0.2.2:8000
```

For a physical phone on the same Wi-Fi, use the PC LAN IP:

```text
http://<your-pc-ip>:8000
```

## Endpoints

```text
GET  /health
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /account/me
POST /account/me
PATCH /account/me
POST /account/avatar
GET  /meetings
POST /devices/register
```

## Seed Account

On startup, the local backend ensures this development account exists:

```text
email: test@163.com
password: 12345678
display name: Shinochanwww
```

Demo meeting records are stored under this account and returned by `GET /meetings`.

## Security Position

This is a local development backend, not the final production backend.

It does:

- Hash passwords with PBKDF2-HMAC-SHA256 and per-user salt.
- Sign access tokens with a local HMAC secret.
- Store refresh tokens hashed in SQLite.
- Keep user identity and device registration server-side.
- Store uploaded avatar files under `data/uploads/avatars/`.
- Record avatar paths and display-name changes in SQLite.

It does not yet do:

- HTTPS.
- Email verification.
- Password reset.
- Argon2id/bcrypt password hashing.
- Production-grade rate limiting.
- Device attestation.
- NFC meeting token issuance.

Do not expose this server directly to the internet.
