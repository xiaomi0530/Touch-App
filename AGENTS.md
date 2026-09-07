# Touch-to-Meet Android App: Agent Design Specification

This document is the persistent project specification for the "touch-to-meet" Android application. Any coding agent working on this project must read this file before writing or modifying code, and must keep implementations aligned with it.

When user requirements change, update this document in the same change set as the code so it remains the source of truth.

## Product Goal

Build an Android application where each user has a private identity represented by secure, short-lived interaction credentials. When User A opens the app and physically taps or brings their phone close to User B's phone, both users' apps record a verified meeting event containing who met whom and when.

The system must be designed from the beginning for security, privacy, reliability, and future production hardening.

## Non-Negotiable Principles

1. Never treat a raw `userId` read over NFC as proof of a meeting.
2. Never expose permanent user identifiers over NFC.
3. Never trust client-side time as authoritative for confirmed records.
4. Never write a final meeting record locally without server confirmation.
5. Never store private keys, long-lived secrets, or server credentials in app code.
6. Always design network and NFC operations as failure-prone and retryable.
7. Always prefer explicit, auditable state transitions over implicit side effects.
8. Always update this specification when changing architecture, security assumptions, or core flows.
9. Features touching friends, file uploads, email, account sessions, NFC proof, or day events must follow `SECURITY_STRATEGY.md` and identify whether handled data is public, friend-visible, private, sensitive, or secret.

## Target Platform

Primary platform:

- Android native app
- Kotlin preferred
- Android Studio project structure

Core Android capabilities:

- NFC Reader Mode for scanning another device
- NFC Host Card Emulation, HCE, via `HostApduService`
- Android Keystore for non-exportable device private keys
- Room or equivalent local persistence for cached and pending records
- HTTPS-only backend communication

Future platforms such as HarmonyOS may be considered later, but Android is the initial implementation target.

## High-Level Architecture

The application should be split into clear layers:

- UI layer: screens, state rendering, user actions
- Domain layer: meeting flow state machine, validation rules, use cases
- Data layer: local database, backend API clients, repositories
- NFC layer: HCE service, reader mode scanner, APDU protocol handling
- Security layer: device keys, signing, token validation helpers
- Sync layer: pending proof upload, confirmed meeting download, retry handling

The backend is authoritative for:

- User identity
- User authentication and session issuance
- Registered device public keys
- Short-lived meeting tokens
- Meeting confirmation
- Meeting timestamps
- Token usage state
- Abuse prevention decisions
- Audit logs

## Meeting Flow

The secure meeting flow should use a short-lived server-issued token.

1. User B opens "My Tap Code" or equivalent foreground screen.
2. B's app requests a short-lived meeting token from the backend.
3. The backend creates a one-time token associated with B, B's device, expiry time, and issuance metadata.
4. B's app signs token session data using the device private key stored in Android Keystore.
5. B's phone exposes only the short-lived token proof through NFC HCE.
6. User A opens "Tap Someone" or equivalent scanner screen.
7. A's app reads B's NFC HCE payload.
8. A's app signs the observed token proof using A's device private key.
9. A uploads the complete proof to the backend.
10. The backend validates the token, signatures, device bindings, expiry, one-time use, and anti-abuse rules.
11. The backend creates one confirmed meeting record using server time.
12. Both A and B sync the confirmed meeting record from the backend.

The app may show a local "pending confirmation" record before server confirmation, but it must be visually and semantically distinct from a confirmed meeting.

## NFC Payload Requirements

NFC payloads must be compact and versioned.

The HCE payload should contain only data needed to prove a fresh interaction:

```json
{
  "version": 1,
  "token": "short_lived_server_token",
  "nonceB": "random_nonce",
  "issuedAt": 1781709000,
  "signatureB": "device_signature"
}
```

The NFC payload must not include:

- Permanent user ID
- Phone number
- Email address
- Real name
- Long-lived authentication token
- Refresh token
- Private key material
- Backend secrets

## Cryptographic Identity

Each installed app instance must generate or provision a device key pair after authenticated login.

Requirements:

- Private key must be generated and stored using Android Keystore.
- Private key must be non-exportable where supported.
- Public key must be registered with the backend and bound to `userId` and `deviceId`.
- Backend must be able to revoke a device public key.
- Signatures must include context strings to prevent replay across protocol steps.
- Signature inputs must include token, nonce, device ID or key ID, protocol version, and relevant timestamp fields.

Do not invent custom cryptographic primitives. Use platform and well-reviewed standard libraries.

## Backend Validation Rules

Before confirming a meeting, the backend must verify:

- Token exists.
- Token belongs to the tapped user.
- Token was issued to the expected device.
- Token is not expired.
- Token has not been used before.
- Scanner and scanned users are different.
- Scanner device public key is active.
- Scanned device public key is active.
- Scanner signature is valid.
- Scanned user's token signature is valid.
- Server-side rate limits are not exceeded.
- Duplicate meeting suppression rules are satisfied.

The backend must set the confirmed meeting time using server time.

## Account and Authentication

The app must support account registration, login, session display, and logout.

Current client implementation may include a UI-only authentication scaffold for early product iteration, but it must not be treated as production authentication.

Current account rules:

- Display names are user-facing identifiers and must be non-empty.
- Display names are limited to 40 characters.
- Display names must be unique case-insensitively across users during registration and account profile updates.
- If a requested display name is already occupied, the backend must reject the request and the Android UI must show a textual error instead of silently changing the name.
- User profile cards include display name, avatar, short bio, birthday, gender, card background image or default card background, and server-recorded last-seen time.
- Profile card metadata and uploaded card backgrounds must be stored on the backend and returned through account/friend user payloads. The client may cache display data but must not be the source of truth.
- Last-seen time is written by the backend during authenticated activity such as login, refresh, and account lookup. The client must only display it and must not submit arbitrary last-seen values.
- While editing the user's profile card, changing the default card background or choosing a local background image must update the visible card preview immediately. Background image selection should remain a draft preview until the user taps save, at which point the image is uploaded and the profile update is committed.

Production authentication requirements:

- Registration and login must be verified by the backend.
- Passwords must never be stored in plaintext on the client.
- Passwords must never be logged.
- Authentication secrets must never be hardcoded in the app.
- The backend must issue short-lived access tokens and secure refresh credentials.
- The app must store session credentials using platform-appropriate secure storage.
- Device key generation and public-key registration must happen only after backend-authenticated login.
- Logout must clear local session state and stop exposing NFC tap credentials.
- Account recovery and device migration must include server-side re-authentication.

Suggested account endpoints:

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /account/me
PATCH /account/me
POST /account/avatar
POST /account/card-background
```

The UI must clearly distinguish authenticated, unauthenticated, pending, and failed authentication states.

## Anti-Abuse Rules

Initial anti-abuse controls should include:

- One-time token usage.
- Short token TTL, initially 30 to 120 seconds.
- Per-device rate limit for token generation.
- Per-device rate limit for scan submissions.
- Per-user duplicate suppression within a short window.
- No self-meeting records.
- Device revocation.
- Audit logging for rejected and accepted proof submissions.

Future stronger controls may include:

- Push confirmation to the tapped user.
- Risk scoring.
- Device attestation.
- Foreground-only token validity.
- Optional biometric confirmation before exposing tap token.

## Reliability Model

The NFC and network path must be modeled as unreliable.

Use an explicit meeting interaction state machine:

```text
Idle
PreparingToken
ReadyToTap
NfcDetected
UploadingProof
PendingSync
Confirmed
FailedRetryable
FailedFinal
```

Failure handling:

- NFC read failure: allow immediate retry.
- Token expired: refresh token automatically if the screen is still foreground.
- Network failure after NFC read: save a pending proof locally and retry.
- Backend rejection: do not create a confirmed meeting; show a clear failed state.
- App restart: resume pending sync safely without duplicating confirmed records.

All retry operations must be idempotent from the backend perspective.

## Local Persistence

Local storage should support:

- Pending meeting proofs waiting for upload.
- Confirmed meeting records synced from backend.
- Local display cache for user profile metadata.
- Device registration state.

Confirmed local records must include backend meeting IDs. Pending local records must not be presented as confirmed history.

Sensitive local values must be minimized. Do not persist unnecessary NFC payloads after confirmation unless needed for audit/debugging and explicitly approved by the design.

## Privacy Requirements

The product requirement is only to record "who met whom and when."

Do not collect GPS location by default.

Do not expose permanent identifiers through NFC.

Do not display internal user IDs in normal UI.

Do not upload contact lists, nearby device scans, or unrelated Bluetooth/NFC metadata unless a future requirement explicitly adds it and this specification is updated.

Minimum meeting record fields:

```text
meetingId
userAId
userBId
metAtServerTime
method
status
createdAt
```

Display fields may include:

```text
displayName
avatarUrl
metAtLocalFormatted
confirmationStatus
```

## Friend System

The app now has an explicit friend relationship model. Confirmed meeting records must only be created when the two users are accepted friends and neither side has blocked touch interactions.

Friend management requirements:

- Users can search for other users by display name and send friend requests.
- Users can accept or reject incoming friend requests.
- Users can delete an accepted friend relationship.
- Deleting a friend must require a confirmation dialog. After deletion, the deleted friend must no longer appear in that user's meeting history or personal day-event participant lists. Events that only contain the deleted friend should be hidden from that user; events with other still-active friends should remain visible with the deleted friend removed from the participant display.
- Users can block or unblock a friend for touch interactions.
- The accepted-friend list should not show a separate always-visible block button. Blocking or unblocking should be offered inside the delete-friend confirmation dialog as a less destructive alternative alongside cancel and confirm delete.
- Users can set a private remark for an accepted friend. Remarks are viewer-specific and must be stored server-side, not only in local UI state.
- The friends page can open a friend card from an accepted friend's avatar. The card should show friend identity, the viewer's remark, and recent meeting counts with that friend.
- Friend cards should also show the friend's cloud-backed public profile card fields: avatar, card background, bio, birthday, gender, and last-seen time.
- Friend management sections, including search, incoming requests, accepted friends, and outgoing requests, should use full-width white section cards consistently even when a section is empty. Search input and search button should be height-aligned and visually balanced.
- A blocked friendship remains visible as a relationship state, but blocked touch attempts must not create confirmed meeting records.
- If two non-friends complete the NFC tap proof exchange, the backend must return a `friend_required` state instead of creating a meeting. The client may ask whether to send a friend request.
- Calendar week/month/year views must support filtering by all meeting records or by one accepted friend.
- Friend management should be available from a dedicated app page.
- Accepted friends should be sorted by the recent 10-day versus previous 10-day meeting ratio, descending. Block state should not affect friend list ordering. Friend rows should show this ratio instead of tap availability/block status text.

Current friend endpoints:

```text
GET  /friends
GET  /friends/search?q=
POST /friends/requests
POST /friends/requests/respond
POST /friends/remove
POST /friends/block
POST /friends/remark
GET  /meetings?friendUserId=
```

Security rules:

- The backend remains authoritative for friendship status.
- The Android client must not locally create confirmed records for non-friend taps.
- The NFC payload must still avoid permanent identifiers; friend-required decisions happen only after backend token validation resolves the tapped account.
- Friend search must not expose internal-only secrets or authentication tokens.
- Email is private account data for login/recovery and must not be returned in friend search, friend cards, meeting participants, day-event participants, or realtime friend payloads.

## Badge System

The app has a fourth bottom-level page named `Badges`, but the core reward model is now a staged badge system rather than one-time achievement honors. Badges should feel closer to QQ-style interaction marks or sparks: long-lived, collectible, and upgradeable through repeated meaningful interaction.

Badge requirements:

- Badge levels must be backend-authoritative. The Android client may render progress and notices, but must not locally decide that a badge is lit or upgraded.
- Badges are split into personal badges and friend-bond badges.
- Each badge has 3-5 stages. The current first-phase system uses five stages: `微光初现`, `星火新燃`, `清辉渐盛`, `流光相映`, and `长明不息`.
- Personal badges derive from confirmed meeting count, unique accepted-friend meeting count, confirmed meeting streaks, saved day-event activity, and image-backed day-event activity.
- Friend-bond badges derive from confirmed meetings with one accepted friend, consecutive-day streaks with that friend, recent interaction frequency, shared day events, and image-backed shared day events.
- Badge progress must be calculated from server-confirmed meetings and server-confirmed day events only.
- Removed friends must not continue to expose friend-bond badges or private friend metadata to that user. Friend-bond badge responses should be scoped to currently accepted friends in normal API responses.
- After a friend is deleted, the Android UI must immediately hide that friend's friend-bond badge rows using the current accepted-friend cache, then refresh authoritative badge state from the backend.
- Badge payloads may include friend-visible profile fields for accepted friends, but must not include email, account secrets, authentication tokens, NFC tokens, or raw internal audit data.
- Badge state updates must be idempotent. Re-evaluating badges after repeated sync, proof retry, or app restart must not create duplicate lit or upgrade records.
- The backend may emit realtime `badge_lit` and `badge_upgraded` events after creating a new badge state or level increase. The client must treat realtime events as notification hints and refresh authoritative badge state from `GET /badges`.
- The achievements page should render as a polished badge gallery: high-quality cards, clear level/progress, restrained theme-managed color, and no cheap or noisy gamification.
- Unfinished personal badges should appear before max-level personal badges. The bottom max-level group is only for personal badges; friend-bond badges must stay inside their corresponding friend expansion and must not be duplicated in the global max-level section.
- Friend-bond badges should first appear as friend summary cards showing lit count versus total count. Tapping a friend summary expands that friend's badge rows. Within a friend expansion, unfinished badges appear first sorted by next-stage progress, and max-level badges appear at the bottom sorted by upgrade time.
- Badge artwork should use distinct, code-specific icon drawings instead of reusing one generic medal or relying on centered text labels. Icons should remain lightweight, theme-aware, and visually readable at small card sizes. Tapping a badge/card should open a detail dialog that shows the badge description, current stage, progress, and upgrade time.
- Badge page motion should feel premium but stay low-cost: progress bars should ease toward new values, friend-bond expansion indicators may rotate subtly, lit badges may use restrained glow/highlight motion, and card backgrounds may use static low-alpha theme shapes. Avoid large layout shifts, heavy image assets, or constant full-screen animation.

Current first-phase badges:

```text
Personal:
- 逢迹成册: total confirmed meetings, stages 1 / 10 / 50 / 100 / 365
- 千线相连: unique met friends, stages 1 / 3 / 10 / 30 / 100
- 朝夕不辍: confirmed meeting day streak, stages 3 / 7 / 14 / 30 / 60
- 浮生日笺: saved day events, stages 1 / 5 / 20 / 50 / 100
- 光影留痕: saved image-backed day events, stages 1 / 5 / 20 / 50 / 100

Friend-bond:
- 星火渐燃: meetings with this friend, stages 1 / 3 / 10 / 30 / 100
- 长明相伴: consecutive meeting days with this friend, stages 3 / 7 / 14 / 30 / 60
- 同频共振: recent meetings with this friend, stages 2 / 3 / 5 / 15 / 25
- 同写一页: shared day events with this friend, stages 1 / 3 / 10 / 30 / 60
- 并影成章: image-backed shared day events with this friend, stages 1 / 5 / 15 / 30 / 60
```

Current badge endpoints:

```text
GET /badges
```

## Note Photo Transfer

The app includes a top-level transfer page for quickly preparing classroom note/PPT photos on the phone. The current design is local-only and intentionally removes the previous backend/desktop transfer mode.

Product requirements:

- The Android client selects images from the phone gallery and preserves the user-selected order as the document order.
- The Android client generates a zip locally on the phone and names entries deterministically as `0001.jpg`, `0002.jpg`, etc.
- The zip should be created by streaming image data from content URIs; do not decode all selected images into memory.
- Before writing an image into the zip, the Android client should keep images at or below 5 MB. Images already at or below 5 MB should be copied as-is. Images over 5 MB should be locally recompressed with minimal practical quality loss, preferring modest resolution reduction and high-quality JPEG output until the result is below 5 MB.
- Because phone photos are already compressed, local zip generation may use no-compression zip entries to reduce CPU time for large batches.
- After generation, the user can share the zip through the Android system share sheet, including WeChat if installed, or save it through the Android document picker.
- The feature must not upload note photos to the Touch backend and must not require the desktop helper tool.
- This feature is independent of friend/meeting history and must not expose note photos to friends or public profile APIs.

Security requirements:

- Note photos are sensitive private user content.
- Note photos stay local to the phone unless the user explicitly shares or saves the generated zip through Android system UI.
- Generated zip files are temporary cache files exposed to other apps only through `FileProvider` with one-time read grants.
- Do not log selected image URIs, original filenames, zip contents, or attachment bytes.
- Any future reintroduction of cloud transfer must update this specification and `SECURITY_STRATEGY.md` before code changes.

## File Upload, Email, and Enterprise Security

The detailed security roadmap for friend privacy, file upload handling, email exposure, session storage, deployment hardening, and production encryption is maintained in:

```text
SECURITY_STRATEGY.md
```

Current production-hardening requirements:

- Production traffic must be HTTPS-only; the temporary HTTP ECS backend is for real-device testing only.
- Android refresh tokens must move from plain private `SharedPreferences` to Keystore-backed encrypted storage before production.
- Public/friend-visible user serializers must not include email. The current backend has separate private account and public profile serializers; do not merge them.
- Uploaded images must be decoded, re-encoded, metadata-stripped, size-limited, and served through safe authorization-aware paths or signed URLs according to their data class.
- Day-event images must move out of base64 JSON storage before production.
- Friend operations, upload endpoints, auth endpoints, meet-token creation, proof submission, and realtime streams must have rate limits and auditable security events.

## API Design Guidelines

All API calls must use HTTPS.

Suggested endpoints:

```text
POST /devices/register
POST /meet-tokens
POST /meetings/proofs
GET  /meetings
GET  /day-events?date=
POST /day-events
DELETE /day-events/{eventId}
POST /devices/revoke
```

Proof submission must be idempotent. The client should send a stable `clientSubmissionId` so retries cannot create duplicate meetings.

Backend responses should distinguish:

- Confirmed
- Pending server review
- Rejected retryable
- Rejected final

## UI Requirements

The app must clearly separate:

- A unified user-facing "Start tap" flow
- Internal HCE token-serving state
- Internal ReaderMode scanning state
- Pending meeting records
- Confirmed meeting records
- Failed attempts

The tap screen should make readiness obvious:

- Preparing
- Ready to tap
- Tapping detected
- Confirming
- Confirmed
- Failed, retry available

Do not claim a meeting is recorded until backend confirmation succeeds.

NFC interaction requirements:

- The user-facing NFC workflow should prefer one unified "start tap" action. Internally the app may run both HCE token serving and ReaderMode scanning, and whichever side succeeds first must stop the other side.
- Reader mode should minimize accidental system card/payment handling by using Touch HCE reader flags, skipping NDEF checks, suppressing platform sounds, and disabling reader mode immediately after a Touch payload is read.
- HCE APDU SELECT matching must tolerate standard SELECT AID variants such as optional trailing `Le` bytes so different Android devices can read each other reliably.
- The scanning user must get an immediate local success notice after server confirmation, without relying only on realtime SSE.
- The tapped user's foreground UI must show an immediate "payload read, waiting for confirmation" notice when the HCE payload is served, then rely on backend realtime/polling for the confirmed meeting notice.
- UI copy should tell users to align the NFC areas of both phones and hold steady for about 1-2 seconds.

Current interaction style:

- Major panel switches should prefer short Crossfade or fade-only transitions.
- Collapse/exit animations should avoid height-shrinking, spring, and scale transitions in scrollable layouts when they feel janky; prefer very short fade-only exits.
- Inline show/hide interactions should use short fade plus very small vertical motion. Avoid large translations, strong bounce, and layout-size animation in scrollable content.
- Press feedback should stay subtle and low-cost; prefer short tweened scale feedback over bouncy springs for repeated controls.
- Keep account editing and calendar details visually inline with the main page unless the user explicitly asks for modal behavior.
- The app uses a bottom three-tab structure: main screen, friends, and mine. Friend management belongs in the friends tab, and account/user management belongs in the mine tab.
- The bottom three-tab bar should feel like a polished app navigation control, not plain form buttons: use a floating rounded container, subtle theme-colored active state, compact glyphs, restrained motion, and globally theme-managed colors.
- Profile-card editing should provide a clear secondary discard action near save. Discarding must close the editor and restore unsaved draft text/background preview state without submitting profile changes.
- Calendar friend filtering should stay compact. Prefer a small muted text selector that expands into a lightweight scrollable rectangle over always showing many friend filter buttons.
- Calendar friend filtering should sit on the same horizontal level as the active date/range label. Selecting a friend filter must not immediately close the filter list; it should remain open until the user taps elsewhere, then close with a restrained slower fade/slide exit. Filtering should use local cached meeting records so the calendar does not briefly flash through empty/today colors while awaiting a network refresh.
- Calendar month/year cells should show people counts only in the all-friends view. When filtered to one friend, cells should use the one-person heat color and omit `x人` count labels.
- The default calendar mode when entering the main screen is month view.
- Week day tap opens inline details. If the inline detail for that same day is already open, tapping that day again collapses it. While week inline details are open, tapping non-day areas such as the calendar title/range or the detail panel collapses the inline detail. Tapping another week day switches the inline detail to that day. Week day long-press opens the focused day detail dialog.
- Week date cards should show met friends as compact, evenly spaced avatar stacks instead of text names. Avatars must remain circular, horizontally aligned, and clear even when three or more friends are shown. Meeting counts or overflow counts should use a small non-overlapping badge near the avatar stack instead of a low-position text line that can be clipped. Week cards with meetings should use heat colors based on meeting count, including the Mizuki palette. Inline week detail rows should keep the existing time/name layout but place the friend's current avatar to the left of the name. These avatars must be resolved from the latest friend profile data and fall back gracefully to initials when no avatar is available.
- Entering week view directly should open the current week detail list by default. Tapping a specific week day can still replace that with the single-day inline detail, and tapping outside can still collapse it.
- Month day tap opens that week view. Month day long-press opens that day's detail dialog directly without switching to week view.
- Day detail dialogs may show met friends as compact bubbles and allow creating a personal "today's event" with selected friend participants, text, and up to six images.
- The create-event participant picker must only show people who actually have a confirmed meeting record on that selected day. If no such people exist, the participant picker should not be shown.
- Day detail dialogs should show a lightweight animated loading hint while saved day events are being fetched.
- Saved day event cards should support tap-to-open read-only details, including text and attachment photo preview.
- Long-pressing a saved day event card should open an action dialog asking whether to edit or delete. Delete still requires a second confirmation.
- Editing a saved day event should allow changing text, participants, and images.
- Event details should support multi-image viewing. Tapping an attachment should open a larger gallery with elegant page-like thumbnails and horizontal drag/selection behavior.
- Enlarged event images should support pinch-to-zoom and pan gestures.
- New and edited event image strips should support a delete mode: long-press an image to lift the image strip into an elevated arrangement, then swipe an image upward to remove it.
- Saved day event cards should support long-press delete with an explicit confirmation dialog. Deletion must be confirmed by the backend before removing the card from the visible list.
- Inline panels should avoid complex enter/exit animations; prefer immediate state changes plus lightweight press feedback.
- Text input fields should enter edit mode normally when tapped, then clear focus and hide the cursor/keyboard when the user taps outside input controls.
- Save actions that close an editor should avoid abrupt disappearance. Prefer showing a short success state, then closing via the existing fade/slide exit timing so the interaction feels buffered and intentional.
- Cache derived calendar data with `remember`/`derivedStateOf` instead of recalculating filters and sorting during unrelated UI state changes.
- Calendar builders should pre-group records by date/month instead of repeatedly filtering the full record list for every visible cell.
- Animation specs should prefer short `FastOutSlowInEasing` tweens, low-amplitude movement, and fade-only exits in scrollable areas. Avoid spring rebound and repeated image decoding during recomposition.
- The home screen should use lazy composition for vertical scrolling so off-screen sections do not participate in every layout pass.
- Primary tappable controls and calendar cells should use a subtle press scale effect.
- Calendar week/month/year mode changes should animate with fade plus light scale.
- Calendar content should support horizontal swipe navigation: in week view swipe left/right moves to the next/previous week, in month view to the next/previous month, and in year view to the next/previous year.
- Calendar week view must render a natural Monday-to-Sunday week and must not truncate at month boundaries.
- Elevated calendar panels and week day cards must reserve enough drawing space for shadows without narrowing the visible main card width relative to sibling page cards. Do not place shadowed cards flush against scroll or animation container bounds where elevation can be visibly clipped.
- The app supports three user-selectable visual palettes from the Mine page: Muelsyse, Shu, and Mizuki. The selected palette is stored locally and should apply globally after switching.
- Buttons, text buttons, and outlined buttons must use theme-managed colors from the current `TouchColors` palette, including disabled states. Avoid default Material button text colors when they produce pure black text or overly pale text against the active visual theme.
- The current Touch visual system is a light card-based palette. Do not allow Android system dark mode or dynamic colors to make Material input text, labels, or controls use low-contrast light colors on the app's light surfaces. Text entered in every input field must remain clearly visible.
- Mizuki is the default palette.
- Muelsyse uses cool mint, aqua, ivory, deep teal-gray, and restrained pale gold accents.
- Shu uses warmer rice-field tones: olive green, grain gold, ivory, muted earth, and calm tea-gold heat states.
- Mizuki uses cooler oceanic tones: deep sea blue, blue-violet, luminous aqua, pale mist background, and lavender emphasis.
- Avoid harsh high-saturation cartoon green/red palettes. Error colors should use muted coral or muted violet only where warning semantics require it. Keep the UI cute and animated, but make the overall color impression clean, coordinated, and characterful. Avoid allowing Android dynamic color to override the app's intentional brand colors.
- Main, Friends, and Mine pages should share a restrained premium visual language: consistent page headers, lightly elevated white cards, quiet spacing, subtle theme-colored accents, and no decorative elements that feel generic or AI-generated. Keep the existing palette choices intact unless the user explicitly asks to change colors.

## Testing Requirements

Code changes touching core meeting behavior should include tests at the appropriate level:

- Unit tests for token/proof models and state transitions.
- Unit tests for duplicate suppression and idempotency logic where implemented locally.
- Integration tests or fake repositories for pending proof retry behavior.
- NFC protocol parsing tests with valid and invalid payload examples.
- Backend validation tests when backend code exists.

Security-sensitive parsing must reject malformed, oversized, unsupported-version, expired, or unsigned payloads.

## Logging and Observability

Logs must never contain:

- Private keys
- Authentication tokens
- Refresh tokens
- Raw long-lived identifiers exposed unnecessarily
- Full NFC payloads in production logs

Logs may contain:

- Meeting flow state transitions
- Token request result category
- Proof submission result category
- Backend error code
- Redacted IDs or short hashes for debugging

## Implementation Discipline for Coding Agents

Before writing code:

1. Read this file.
2. Identify which section of the specification the change affects.
3. Preserve the security model unless the user explicitly changes it.
4. If the user requests a feature that conflicts with this file, explain the conflict and update the specification only after choosing a safer design.

When writing code:

1. Keep changes scoped.
2. Follow existing project patterns.
3. Prefer strongly typed models over raw maps or ad hoc strings.
4. Prefer explicit state machines for meeting flow logic.
5. Keep NFC, security, networking, and UI responsibilities separated.
6. Avoid adding dependencies unless they clearly reduce risk or complexity.

After writing code:

1. Run relevant tests or explain why they could not be run.
2. Update this file if behavior, architecture, security assumptions, or API contracts changed.
3. Summarize the exact safety and reliability impact of the change.

## Open Decisions

These decisions are intentionally unresolved and should be confirmed before production implementation:

- Backend stack and hosting environment.
- Authentication method.
- Exact device registration flow.
- Token TTL.
- Whether tapped user must confirm every meeting.
- Whether offline meetings are allowed beyond pending upload.
- Whether to support BLE or Nearby Connections as a fallback.
- Data retention period.
- Account recovery and device migration policy.
- Abuse handling and support workflow.

## Current Design Position

The initial product should use NFC HCE only as a proximity-triggered proof exchange mechanism. The backend remains the source of truth for confirmed meeting records. This avoids relying on deprecated Android phone-to-phone NFC transfer behavior and prevents permanent identity leakage through NFC.

## Local Development Backend

The current local backend lives in:

```text
TouchBackend/
```

It is a Python standard-library HTTP server with SQLite storage. This choice is only for local development and avoids requiring third-party backend packages before the project is ready for deployment.

Local backend properties:

- Listens on `http://127.0.0.1:8000`.
- Android emulator clients should use `http://10.0.2.2:8000`.
- Uses SQLite under `TouchBackend/data/`.
- Supports registration, login, refresh, logout, current-account lookup, and device public-key registration.
- Supports display-name updates and avatar uploads.
- Supports authenticated personal meeting history retrieval through `GET /meetings`.
- Hashes passwords with PBKDF2-HMAC-SHA256 and per-user salts.
- Stores refresh tokens hashed.
- Issues short-lived HMAC-signed access tokens.
- Stores uploaded avatar files under `TouchBackend/data/uploads/avatars/` and records avatar paths in SQLite.
- The current Android client calls this local backend for registration and login before entering the main app screen.
- The current Android client can update display names and upload avatar images after authenticated login.
- The current Android client loads calendar and recent-meeting data from the authenticated user's backend meeting records instead of hardcoded UI samples.
- The current Android client persists the authenticated session in private `SharedPreferences` so login survives app restarts until explicit logout.
- On startup, the current Android client restores the saved session immediately and attempts to refresh it through `POST /auth/refresh`.
- Persistent secure token storage with Android Keystore or EncryptedSharedPreferences has not been implemented yet.
- The local backend can optionally seed demo data when explicitly enabled through environment variables.
- The seeded development account also has accepted demo friends. Demo meeting records are bound to those demo friend user IDs so the friend management page and calendar friend filter can be tested with the seeded data.

Local backend limitations:

- No HTTPS.
- No production-grade rate limiting.
- No email verification.
- No password reset.
- No Argon2id/bcrypt dependency yet.
- No production token/key rotation policy.
- Android currently permits cleartext traffic for local development only. Production builds must remove this and use HTTPS.

Do not expose the local backend directly to the internet. Production deployment must replace or harden this backend before real users are allowed.

## Current Server Deployment

The app is currently configured to call the deployed Alibaba Cloud ECS backend at:

```text
<development-backend-url>
```

This is a temporary HTTP deployment for real-device connectivity testing. The Android manifest currently allows cleartext traffic so the app can call this server before a domain and TLS certificate are configured.

Before production release:

- Replace the IP-based base URL with an HTTPS domain, for example `https://api.example.com`.
- Configure Nginx TLS termination with a valid certificate.
- Remove broad cleartext traffic allowance from the Android manifest.
- Keep the backend behind Nginx and do not expose the Python service port directly.

## Current Core Tap Implementation

The current implementation includes the first working NFC tap-to-meet loop:

- The backend supports `POST /meet-tokens`.
- The backend supports `POST /meetings/proofs`.
- The Android app can request a short-lived server token and expose it through NFC HCE.
- The Android app can enable NFC Reader Mode, read another Touch phone's HCE payload, submit it to the backend, and refresh the confirmed meeting list.
- The Android UI opens a dedicated tap progress dialog for both tap modes. "Show my tap" uses outward wave animation, while "Tap someone" uses inward wave animation.
- The backend writes confirmed meeting records for both users using server time.
- Proof submissions include `clientSubmissionId` and are idempotent for retries.
- Short-lived meet tokens are one-time use and expire after 120 seconds.
- Self-meetings are rejected by the backend.
- NFC payloads contain only protocol version, short-lived token, issued time, and expiry time. They do not include email, permanent user ID, refresh token, access token, phone number, or display name.
- The backend now writes user-scoped realtime events for friend requests, friend request responses, friend removals, and confirmed meetings.
- The Android app opens an authenticated foreground SSE connection to `GET /events/stream` after login.
- Realtime events are treated as notifications to refresh authoritative backend state, not as client-side proof of friendship or meeting state.
- When a confirmed meeting event is received, the app refreshes meeting data and shows a lightweight animated floating success notice.
- When a friend event is received, the app refreshes friend data and shows a lightweight animated floating notice.
- On app startup, historical realtime events must be used only to establish the latest event baseline and refresh state. They must not show floating notices after the fact; only events created after the current foreground realtime session starts should trigger pop-up notices.

Current Android NFC files:

```text
Touch/app/src/main/java/com/shinochanwww/touch/NfcTapService.kt
Touch/app/src/main/res/xml/touch_apdu_service.xml
Touch/app/src/main/AndroidManifest.xml
```

Current backend NFC-related storage:

```text
meet_tokens
meeting_proof_submissions
meetings
user_events
```

Current implementation limitations:

- Device key generation and Android Keystore signatures are not yet wired into the proof flow.
- Backend proof validation currently verifies token existence, expiry, one-time use, scanner/scanned identity, self-meeting prevention, and idempotency, but not cryptographic device signatures.
- Pending offline proof storage is not implemented. If NFC succeeds but the network request fails, the user must retry.
- The current deployment still uses HTTP by IP for testing. Production tap proof submission must use HTTPS.
- Session persistence currently uses private `SharedPreferences`; production builds must migrate refresh-token storage to Android Keystore-backed encrypted storage.
- Realtime delivery currently uses foreground SSE only. Production background delivery should add vendor push or FCM, while keeping backend event persistence for missed-event recovery.

Next hardening step:

- Generate a non-exportable Android Keystore key after login.
- Register the public key with `/devices/register`.
- Include signed HCE and scanner proof fields in the NFC payload/proof submission.
- Verify both signatures server-side before confirming a meeting.
