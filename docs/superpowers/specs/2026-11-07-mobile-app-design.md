# Spyo Map Mobile — design & plan

## Context

Android browsers strip EXIF GPS from photos chosen in `<input type=file>`, so the Worker rejects every upload made
from an Android phone. We want a dedicated app for using the map on the go and, above all, uploading geotagged photos
from the phone's gallery. The app should be a thin wrapper around the existing web app at
`https://spyo-map.isntall.net`, adding native photo picking (with GPS intact) and native Google sign-in. Android first;
the approach must allow an iPhone build later (iOS web upload may turn out to work — TBD).

New repo: `~\Documents\Projects\spyo-map-mobile`. Plus one small PR in `spyo-map` (worker + frontend).

## Goals (user's)

- Pick existing photos from the gallery later (e.g. after a trip), several at a time, and upload them with GPS intact.
- Two entry points: Upload page inside the app (native multi-photo picker) **and** Android Share sheet target
  ("Share → Spyo Map" from a gallery app lands the photos on the upload page).
- Native Google sign-in (SSO) instead of the web OAuth redirect.
- Rest of the app is the live web map, unchanged.

## Anti-goals (user's)

- **Painful to release:** building/installing a new APK must not be manual and fiddly.
- **Feels like a website:** no browser-ish chrome, hardware back must work, external links must not hijack the app
  view, no frequent re-login.

## Non-goals

- Play Store publication (users: the owner and a few allow-listed uploaders; sideloaded APK).
- iOS build in v1 (keep it possible: no Android-only decisions in shared TS code).
- Offline mode, in-app camera capture, background upload queue.

## Constraints / facts from the existing code

- Sign-in today: `/auth/login/google` → Google OAuth (PKCE) → `/auth/callback/google` sets HttpOnly `spyo_session`
  cookie (7 days) — `packages/worker/src/auth/routes.ts`, `session.ts`. Google blocks its OAuth page inside embedded
  WebViews (`disallowed_useragent`), so this flow can't run in the app as-is.
- Uploads: `POST /api/uploads/check` + `PUT /api/uploads/:sha256` require `Origin` == site origin and the session cookie
  (`packages/worker/src/uploads/handlers.ts`). Web pipeline: `inspectFile` → `checkHashes` → `planBatch` → `uploadFile`
  (`packages/frontend/src/components/UploadPage.tsx`, `utils/uploadBatch.ts`, `services/UploadClient.ts`).
- Android Photo Picker and many share sources redact location. Reading GPS requires `READ_MEDIA_IMAGES` +
  `ACCESS_MEDIA_LOCATION` and `MediaStore.setRequireOriginal(uri)`. Fine for sideloading.
- Google Photos (default setting) strips location when sharing; such shared photos will be rejected like on the web.
  Acceptable — the in-app picker is the reliable path; the upload page should say so in the rejection hint.

## Approach (decided)

**Capacitor (Claude's call, user approved)**, loading the live site via `server.url = https://spyo-map.isntall.net`.
Because the WebView *is* the real site, routing, deep links, the cookie session and the existing upload endpoints all
work unchanged — and most future changes ship by deploying the web app, with no new APK.

Native additions (one local Capacitor plugin, `SpyoNative`, Kotlin, with a TS interface that an iOS impl can satisfy
later):

1. **Sign-in** — `signIn(): { idToken }` via Android Credential Manager (`GetGoogleIdOption`, server client ID =
   existing web `GOOGLE_CLIENT_ID`). The web page POSTs the token same-origin to a new Worker endpoint which sets the
   same `spyo_session` cookie. Silent re-sign-in: on app start / upload page load, if `/api/me` is signed out and the
   user signed in before, call `signIn({ silent: true })` (auto-select authorized account) — no prompt in the common
   case.
2. **Photo picking** — `pickPhotos(): PickedPhoto[]` opens a multi-select gallery picker that returns original bytes
   (GPS intact) after requesting the media + media-location permissions. Exact mechanism (`ACTION_OPEN_DOCUMENT` +
   `setRequireOriginal`, or a MediaStore query) is decided by the spike.
3. **Share target** — intent filter for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` `image/*`; the activity navigates the
   WebView to `/#/upload` and exposes the shared photos through `getSharedPhotos()` (same `PickedPhoto` shape).
4. **Bytes to the page** — each `PickedPhoto` (name, mime, size, a fetchable URL or base64) is turned into a `File` in
   the web layer and fed into the **existing** `handleFiles` pipeline. Transport (Capacitor file URL via
   `convertFileSrc`, chunked base64, or a local-file proxy) is decided by the spike; must cope with ~10 × 8 MB.

Native-feel items in the wrapper: hardware back → `history.back()` (exit at map root), links to other hosts open in
the system browser / Custom Tab, app icon + splash, status bar themed to the site, `/#/upload` reachable from a
launcher shortcut.

Release (anti-goal 1): GitHub Actions in `spyo-map-mobile` builds a **signed** release APK on tag `v*` and attaches it
to a GitHub Release (keystore + passwords in repo secrets). The app checks the latest GitHub Release on start and
offers "Update available" → opens the APK download. Bump of versionCode derived from the tag.

## Changes in `spyo-map` (one PR, branch from fresh `main`)

Per CLAUDE.md order: shared types → worker → frontend.

- `packages/shared`: request/response type for the token sign-in endpoint.
- `packages/worker`: `POST /auth/google/idtoken` in `src/auth/routes.ts`. Requires same-origin `Origin`. Verifies the
  ID token via Google `tokeninfo` (aud == `GOOGLE_CLIENT_ID`, iss is accounts.google.com, `email_verified`, not
  expired) — add `verifyIdToken` beside `exchangeForVerifiedEmail` in `src/auth/google.ts`, using the injected
  `fetchFn` like existing code. Then reuse `findActiveUploaderByEmail` + `signSession` + `serializeCookie` exactly as
  `callback()` does. 200 / 403 (not allowed) / 401 (bad token). Tests in `packages/worker/tests/`.
- `packages/frontend`: `src/utils/native.ts` — detects `window.Capacitor?.isNativePlatform()` and wraps the
  `SpyoNative` plugin calls (no Capacitor npm dep needed in the web app; type the global). `UploadPage`: in-app, the
  "Sign in with Google" button calls native sign-in → endpoint → reload `me`; the file input is replaced by a
  "Choose photos" button calling `pickPhotos()`; on mount, consume `getSharedPhotos()`. Converted Files go through the
  unchanged `handleFiles`. Tests in `packages/frontend/tests/` (mock the global).
- Google Cloud console (manual, documented): add an **Android** OAuth client (package name + signing cert SHA-1) to
  the same project so Credential Manager can issue tokens with the web client ID as audience.

## New repo `spyo-map-mobile` layout

```
spyo-map-mobile/
├── capacitor.config.ts        server.url → live site, appId net.isntall.spyomap (Claude's call)
├── package.json               @capacitor/core, cli, android; jest for TS tests
├── src/native.ts              TS interface for SpyoNative (shared contract, documented)
├── android/                   generated by `npx cap add android`, plus:
│   └── app/src/main/java/.../SpyoNativePlugin.kt, MainActivity.kt (back, external links, share intents)
├── .github/workflows/release.yml
├── docs/superpowers/specs/2026-11-07-mobile-app-design.md   (this design, committed)
├── CLAUDE.md, README.md (build, sideload, Google Cloud setup)
└── mise.toml (node, java 21)
```

Conventions copied from `spyo-map`: conventional commits (`feat`/`chore`/`feat!`), feature branches + PRs, mise.

## Sequence

0. After approval: commit this design to `spyo-map-mobile/docs/superpowers/specs/`, then write the detailed
   implementation plan with superpowers:writing-plans.
1. **Spike (throwaway)** in a scratch Capacitor project on the owner's phone over adb: (a) multi-pick from gallery with GPS
   intact via `setRequireOriginal`; (b) GPS survival via Share from Samsung Gallery / Google Photos / Files; (c) moving
   10 × 8 MB photos into the page as `File`s; (d) Credential Manager ID token against the existing web client ID.
   Record findings in the design doc. If (a) fails, stop and rethink.
2. `spyo-map` PR: shared type → `/auth/google/idtoken` + tests → frontend native hooks + tests. Deploy.
3. `spyo-map-mobile`: scaffold repo, Capacitor + Android, `SpyoNative` plugin (sign-in, pick, share), native-feel
   items, release workflow + update check, docs.

## Decisions from review

- **Repo is public (user)** → update check uses the unauthenticated GitHub `releases/latest` API and the release APK
  asset URL directly.
- **Device testing (user):** the owner's phone, connected to this PC over adb (USB or wireless). Claude installs
  builds (`adb install -r`) and reads `adb logcat`; the owner does the tapping. The spike gate runs this way.
- **minSdk 29 / Android 10+ (Claude's call):** `ACCESS_MEDIA_LOCATION`/`setRequireOriginal` need 10+. Permissions:
  `READ_MEDIA_IMAGES` on 13+, `READ_EXTERNAL_STORAGE` (maxSdk 32) below. On 14+ "Select photos" partial access, check
  in the spike whether originals stay readable; if not, explain and ask for full access.
- **Sign out (Claude's call):** clears a native "signed in before" flag and calls Credential Manager
  `clearCredentialState`, so silent re-sign-in doesn't undo it. The next sign-in shows the account chooser, so you can
  switch accounts.

## Left to the builder

App icon/splash art (reuse site favicon), exact Kotlin structure, update-check UI, plugin transport details per spike.

## Verification

- `spyo-map`: `npx nx run-many -t test` and `npm run lint` green; new worker tests cover valid token, wrong aud,
  unverified email, non-allow-listed email, cross-origin request.
- Mobile: `./gradlew assembleRelease` in CI produces a signed APK; install on a phone and check: map loads full-screen,
  back button navigates/exits, external links open in browser; native sign-in sets session and survives app restart;
  pick 5 geotagged gallery photos → all "Added"; Share 2 photos from Samsung Gallery → land on upload page and upload;
  re-pick same photos → "Duplicates skipped"; tagging `v0.0.2` produces a release the installed app offers as update.
