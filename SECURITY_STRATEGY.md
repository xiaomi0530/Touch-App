# Touch Enterprise Security Strategy

This document defines the target security strategy for the current Touch Android app and Python backend. It is written for the existing system shape: Android client, NFC tap flow, friend system, profile/file uploads, day-event image attachments, account login by email/password, and the temporary Alibaba Cloud ECS backend.

## Current Security Position

The current system is suitable for private testing only. It already has useful security foundations:

- Passwords are hashed server-side with PBKDF2-HMAC-SHA256 and per-user salts.
- Refresh tokens are stored hashed on the backend.
- Access tokens are short-lived HMAC-signed bearer tokens.
- Meeting tokens are random, short-lived, stored hashed, and one-time use.
- Friend and meeting operations are backend-authoritative.
- SQL access mostly uses parameterized queries.
- Avatar and card-background uploads are authenticated and size-limited.
- The app no longer shows email in friend search UI.

The main gaps before production are:

- The client base URL is still a development HTTP endpoint in private testing; all auth tokens, passwords, uploads, and event data are exposed to network interception until HTTPS is enabled.
- Android stores access and refresh tokens in private `SharedPreferences`; this is not sufficient for production session secrets.
- Friend/profile DTOs still include email broadly, even when the UI does not display it.
- Uploaded avatars/backgrounds are publicly fetchable by path and not authorization-checked.
- Day-event images are stored as base64 JSON in SQLite, not as validated encrypted object files.
- Uploaded images are accepted based on magic bytes/extension only; there is no image re-encoding, metadata stripping, malware scanning, or content moderation.
- The backend has limited rate limiting, audit logging, and operational hardening.
- NFC proof flow still lacks Android Keystore device signatures and server-side signature verification.

## Data Classification

Use these classes consistently across API, database, logs, backups, and UI:

- Public display data: display name, avatar thumbnail, selected default card background.
- Friend-visible profile data: bio, birthday, gender, last-seen time, card background, meeting counts.
- Private account data: email, password verifier, refresh tokens, account recovery state.
- Sensitive interaction data: meeting records, friend graph, block state, remarks, day events, event photos, badge progress derived from meetings or friend relationships.
- Secret material: token signing keys, refresh tokens, device private keys, database credentials, TLS private keys.

Rules:

- Email is private account data. Do not return it in friend search, friend cards, meeting participant payloads, realtime friend events, or day-event participant payloads.
- Friend remarks are viewer-private and must never be returned to the other friend.
- Block state is relationship-sensitive. Return only what the current viewer needs: `blockedByMe` and whether the other side blocks tap interactions if product behavior requires it.
- Event photos are sensitive interaction data. They must be owner-scoped and friend-visible only through event permissions, not public URLs.
- Classroom note transfer photos are sensitive private user content. In the current design they are processed locally on Android only and must not be uploaded to the Touch backend. Generated zip files may be shared only through explicit Android user action.
- Friend-bond badges are sensitive interaction metadata. They may be shown to the owning user for currently accepted friends, but must not be exposed publicly or used to reveal removed friends, blocked relationships, or hidden historical interactions.

## Transport Security

Production must use HTTPS only:

- Put Nginx or a managed load balancer in front of the Python app.
- Use a domain such as `https://api.touch.example`.
- Use TLS 1.2+ with modern ciphers and automatic certificate renewal.
- Redirect HTTP to HTTPS at Nginx, but the Android app must call only HTTPS URLs.
- Remove `android:usesCleartextTraffic="true"` from production manifests.
- Add Android Network Security Config that denies cleartext by default.
- Consider certificate pinning after the API domain and certificate rotation process are stable.

No password, bearer token, refresh token, uploaded image, friend request, or SSE stream may be sent over HTTP in production.

## Authentication and Session Security

Backend target:

- Replace development HMAC JWT handling with a production key-management policy: key IDs, rotation, and emergency revocation.
- Store token signing secrets outside the repo and outside app code, preferably in cloud secret management.
- Keep access token TTL short, currently 15 minutes is acceptable.
- Rotate refresh tokens on every refresh, as currently implemented.
- Detect refresh-token reuse and revoke the whole session family.
- Store refresh-token hashes with a server-side pepper from secret management, not plain SHA-256 only.
- Add per-account and per-IP rate limits for login, registration, refresh, and password reset.
- Add email verification before production use.
- Add password reset with short-lived single-use tokens.
- Upgrade password hashing to Argon2id or bcrypt when third-party backend dependencies are accepted.
- Add MFA-ready account model, even if MFA is not enabled initially.

Android target:

- Store refresh tokens in Android Keystore-backed encrypted storage, such as EncryptedSharedPreferences or a DataStore encrypted with a Keystore key.
- Do not persist access tokens longer than necessary.
- Clear refresh token, access token, NFC payload, cached profile data, and SSE state on logout.
- Disable Android Auto Backup for session/token data. Production backup rules must exclude credential storage.
- Never log passwords, access tokens, refresh tokens, Authorization headers, NFC payloads, or raw API responses that may contain secrets.

## Email Privacy

Email is used for login and recovery only.

Required API changes:

- Split user serializers into:
  - `private_account_user`: for `/account/me`, login, register, refresh. May include email.
  - `public_profile_user`: for friend search, friend cards, friend requests, meetings, day events, realtime payloads. Must not include email.
- Friend search response should include only `id`, `displayName`, avatar thumbnail URL, and minimal profile preview if intended.
- Friend list response should not include email unless the product explicitly adds friend-visible email sharing.
- Realtime events must not embed email in `fromUser` or `friendship.friend`.
- Android DTOs should make friend email nullable or remove it from `FriendUserDto`.

## Friend System Security

Authorization model:

- Backend is the only authority for request, accept, reject, remove, block, remark, meeting visibility, and event participant visibility.
- Every friend mutation must verify that the authenticated user is one side of the relationship.
- Only the addressee can accept or reject a pending request.
- Only accepted friends can be blocked/unblocked or remarked.
- Removed relationships must not reveal historical meeting data through `/meetings`, calendar filters, day-event participants, or event detail payloads.
- Blocked relationships must not allow new confirmed tap records.

Privacy and abuse controls:

- Add rate limits for search, friend request creation, accept/reject, remove, block, remark, and SSE connections.
- Add duplicate request suppression and cooldowns after rejection/removal.
- Search should use exact or prefix matching with throttling; avoid broad enumeration by unrestricted `%query%` search at scale.
- Do not expose internal IDs more than necessary. The API can use stable opaque public IDs, but not database internals.
- Add audit events for friend request sent, accepted, rejected, removed, blocked, unblocked, and remark changed. Audit payloads should use redacted IDs.

## File Upload Security

Avatars, card backgrounds, and day-event images need different handling.

Current avatar/background upload:

- Authenticated multipart upload.
- Size-limited.
- Stored under server local upload directories.
- Publicly served by URL path.

Production target:

- Store uploads outside the application code directory.
- Use object storage, such as Alibaba Cloud OSS, or a private filesystem path behind Nginx `X-Accel-Redirect`.
- Generate random object names independent of user ID.
- Strip original filenames from storage and logs.
- Validate real MIME type by decoding the image, not by extension alone.
- Re-encode all accepted images to safe formats such as JPEG/WebP/PNG.
- Strip EXIF and metadata, including GPS.
- Resize avatars and backgrounds to controlled dimensions.
- Generate thumbnails server-side.
- Add antivirus/content scanning for original uploads before publishing.
- Enforce per-user upload quotas and daily upload rate limits.
- Serve profile images with safe headers: `Content-Type`, `Content-Length`, `Cache-Control`, `X-Content-Type-Options: nosniff`.
- For private event photos, use authenticated download endpoints or short-lived signed URLs. Do not serve them from public static paths.

Day-event image target:

- Stop sending event photos as base64 JSON.
- Use `POST /day-events/{id}/attachments` multipart uploads or pre-signed object upload URLs.
- Store attachment rows with owner user ID, event ID, object key, content hash, size, width, height, created time, and scan status.
- Encrypt event attachment objects at rest.
- Only the event owner should be able to create/update/delete event attachments.
- View authorization must follow event visibility rules and active friendship filtering.

Note photo transfer target:

- Keep classroom note transfer local-only on Android unless a future product decision explicitly restores cloud transfer.
- Android should stream selected image URIs into a zip and avoid decoding all images into memory.
- Generated zip files should live in app cache and be shared through `FileProvider` one-time read grants or saved through Android's document picker.
- Do not send note photos, selected image URIs, original filenames, or generated zip contents to the backend.
- If cloud transfer is reintroduced later, require a fresh security design covering quotas, retention, malware/content scanning, metadata stripping, object storage, authenticated ownership checks, and large-archive background jobs before implementation.

## Database and At-Rest Encryption

For the current ECS deployment:

- Restrict SQLite file permissions to the backend service user.
- Keep `data/`, uploads, and token secret files out of Git.
- Back up database and upload objects with encrypted backups.
- Protect backup credentials separately.

Production target:

- Move from SQLite to PostgreSQL or a managed database before real public release.
- Enable disk encryption on ECS and database storage.
- Encrypt sensitive columns or use envelope encryption for private data such as emails, event notes, event photos metadata, friend remarks, and recovery tokens.
- Store encryption keys in cloud KMS, not local files.
- Use migrations with reversible schema changes and backup-before-migration policy.
- Define retention and deletion policy for meetings, uploaded images, account data, realtime events, and audit logs.

## API Hardening

Required controls:

- Central authentication middleware with consistent error handling.
- Request ID on every request.
- Structured logs with redaction.
- CORS locked to known frontend origins if a web frontend is added; mobile API should not need broad CORS.
- Consistent request size limits per endpoint.
- Strict JSON schemas for every endpoint.
- Pagination for friends, meetings, events, and realtime history.
- Per-user and per-IP rate limiting.
- Idempotency keys for mutable operations that may retry.
- Security headers for static/image responses.

Do not log:

- Passwords.
- Access tokens.
- Refresh tokens.
- Authorization headers.
- NFC payload tokens.
- Raw uploaded images.
- Full email addresses in high-volume logs.

Allowed logs:

- Request ID.
- Endpoint name.
- Status code.
- Latency.
- Redacted user ID hash.
- Error code.
- Security decision category, such as `friend_required`, `blocked`, `rate_limited`, or `invalid_token`.

## Badge Security

The staged badge system must remain server-authoritative:

- Badge level state is stored on the backend and returned through authenticated APIs.
- Android may cache or render progress, but must not create lit or upgrade records locally.
- Badge evaluation must use confirmed server meetings and confirmed server day events only.
- Friend-bond badge responses must be scoped to currently accepted friends and must use public/friend-visible serializers that exclude email.
- Realtime badge events are notification hints only; the client must refresh `GET /badges` before relying on updated state.
- Badge state updates must be idempotent so repeated proof retries, sync retries, or event re-evaluation cannot duplicate lit or upgrade records.
- Logs should record badge codes and redacted user identifiers only. Do not log full friend graphs, event notes, image contents, auth tokens, or NFC payloads while evaluating badges.

## NFC and Device Security

The current NFC flow is acceptable only as a prototype.

Next production step:

- Generate a non-exportable Android Keystore key after login.
- Register the public key with `/devices/register`.
- Bind meet tokens to user ID, device ID, key ID, TTL, and foreground session.
- Include scanned-device signature in the HCE payload.
- Include scanner-device signature in proof submission.
- Verify both signatures server-side.
- Add device revocation.
- Add per-device rate limiting.
- Reject old protocol versions and oversized NFC payloads.

NFC payloads must continue to exclude email, display name, permanent user ID, access token, refresh token, and profile data.

## Deployment Baseline for Alibaba Cloud ECS

Minimum production-like baseline:

- Non-root Linux user for backend service.
- Systemd unit with restricted permissions.
- Nginx TLS reverse proxy.
- Firewall exposing only 80/443 publicly.
- Python backend bound to `127.0.0.1`.
- Secrets loaded from environment files with `0600` permissions or cloud secret manager.
- Automated encrypted backups.
- Log rotation.
- Basic monitoring and alerting for CPU, memory, disk, 5xx rate, auth failures, and upload failures.
- Fail2ban or equivalent protection for repeated abusive traffic.

## Implemented Phase 0 Controls

The following controls have been implemented in the current codebase:

- Public/friend-visible user serializers no longer include email. Email remains only in private account/session payloads.
- Android friend DTO parsing treats email as optional for backwards/forwards compatibility.
- The Python backend has in-memory per-IP rate limits for auth, friend search/mutations, uploads, tap token/proof operations, refresh, and SSE streams.
- Common responses include basic hardening headers including `X-Content-Type-Options: nosniff` and `Referrer-Policy: no-referrer`.
- CORS origin can be restricted through `TOUCH_CORS_ALLOW_ORIGIN`; the default remains permissive for current mobile/local testing.
- Avatar and card-background upload type detection no longer trusts filename extensions when image magic bytes are missing.
- Day-event image base64 payloads must decode successfully and must be JPEG data within a per-image size bound.
- Backend request logging avoids request bodies, tokens, and reverse-DNS lookups.

These controls reduce immediate privacy leakage and abuse risk, but they do not replace HTTPS, Keystore-backed client storage, object storage, or device-signature NFC proof.

## Priority Roadmap

Phase 0: immediate hardening before wider testing

1. Enable HTTPS domain and remove Android cleartext traffic.
2. Stop returning email in public friend/profile payloads.
3. Store Android refresh token using Keystore-backed encrypted storage.
4. Add rate limits for auth, friend search/request, upload, meet-token, proof-submit, and SSE.
5. Add upload image decoding, re-encoding, metadata stripping, and safe response headers.
6. Move day-event images out of base64 JSON and into attachment storage.

Phase 1: production beta

1. PostgreSQL or managed database migration.
2. Object storage with private buckets and signed URLs for sensitive files.
3. Structured audit logs and request IDs.
4. Device key signing for NFC proof.
5. Refresh-token reuse detection and session-family revocation.
6. Email verification and password reset.

Phase 2: enterprise-grade production

1. KMS-managed secrets and envelope encryption for sensitive data.
2. Full monitoring, alerting, and incident response playbooks.
3. Regular dependency and image scanning.
4. Security tests for access control, upload validation, token lifecycle, NFC parsing, and friend privacy.
5. Privacy retention/deletion workflows.
6. Optional MFA and device attestation.

## Engineering Rule

Any feature touching friends, files, email, account sessions, NFC proof, or day events must identify which data class it handles and must state whether it is public, friend-visible, private, sensitive, or secret. The backend must enforce authorization; the Android client may improve UX but must not be trusted as the source of security.
