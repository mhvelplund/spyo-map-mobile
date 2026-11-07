# Spyo Map Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Android app that wraps the live Spyo Map site and adds native Google sign-in, native
multi-photo picking with GPS intact, and a Share-sheet target, plus the small `spyo-map` changes it needs.

**Architecture:** Capacitor app whose WebView loads `https://spyo-map.isntall.net` (`server.url`). A local Kotlin
Capacitor plugin `SpyoNative` provides sign-in (Credential Manager → Google ID token), photo picking and shared photos.
The web upload page detects the app, exchanges the ID token at a new Worker endpoint for the existing `spyo_session`
cookie, and feeds native-picked files into its unchanged upload pipeline.

**Tech Stack:** Capacitor (latest stable major at scaffold time), Kotlin, AndroidX Credential Manager + `googleid`,
JUnit 4; Cloudflare Worker (TS), React 19 + Jest 29 (existing `spyo-map`); GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-07-mobile-app-design.md` (this repo).

## Global Constraints

- Site origin: `https://spyo-map.isntall.net`. App ID: `net.isntall.spyomap`. App name: `Spyo Map`.
- minSdk 36 (Android 16), targetSdk 36. Permissions: `READ_MEDIA_IMAGES`, `ACCESS_MEDIA_LOCATION`, `INTERNET` (no
  legacy storage permission). Handle "Select photos" partial access (`READ_MEDIA_VISUAL_USER_SELECTED`).
- Originals are always opened through `MediaStore.setRequireOriginal(uri)` when the URI is a MediaStore URI.
- Distribution: sideloaded signed APK from GitHub Releases of public repo `mhvelplund/spyo-map-mobile`; no Play Store.
- Sign-out clears the native "signed in before" flag and calls `CredentialManager.clearCredentialState`.
- `spyo-map` rules: branch from fresh `main`, conventional commits (`feat`/`chore`/`feat!`), tests in
  `packages/<pkg>/tests/`, `npx nx run-many -t test` + `npm run lint` green before PR, never commit to `main`.
- Same commit conventions in `spyo-map-mobile`. Commit trailer: the Co-Authored-By / Claude-Session lines.
- Device testing: owner's phone over adb from this PC; Claude runs `adb install -r` / `adb logcat`, owner taps.
- No Capacitor npm dependency in `spyo-map/packages/frontend`; it talks to `window.Capacitor` only.

## Review Focus

1. **Share from a source that strips GPS** (Google Photos default) → photo rejected with in-app copy pointing to "Choose
   photos", not the browser copy "try a computer". Test in Task 4.
2. **Silent sign-in when no account is authorized / user cancels the chooser** → page stays on the signed-out view with
   no error banner (cancel is not a failure). Test in Task 4.
3. **Allow-listed check after a valid token for a non-uploader** → 403, no cookie set, page shows the existing "not
   allowed" copy. Tests in Tasks 2 and 4.
4. **Share arrives while the upload page is already open** (app in background on `/#/upload`) → new photos are processed
   without a reload. Test in Task 4 (listener).
5. **A single unreadable photo in a multi-pick** (cloud-only item, revoked URI) → others still upload; that one is
   listed as rejected with `UPLOAD_FAILED_COPY`. Test in Task 3 (`nativePickPhotos` skips + reports failures).

## Shared contract (used by Tasks 3, 4, 6, 7)

```ts
// spyo-map-mobile/src/definitions.ts (source of truth) — mirrored in spyo-map/packages/frontend/src/utils/native.ts
export interface PickedPhoto { name: string; mimeType: string; size: number; webPath: string }
export interface SpyoNativePlugin {
  signIn(options: { silent: boolean }): Promise<{ idToken: string }>; // rejects code 'CANCELED' | 'NO_CREDENTIAL' | 'FAILED'
  signOut(): Promise<void>;
  hasSignedInBefore(): Promise<{ value: boolean }>;
  pickPhotos(): Promise<{ photos: PickedPhoto[] }>;   // [] when the user cancels
  getSharedPhotos(): Promise<{ photos: PickedPhoto[] }>; // drains the pending share queue
  addListener(event: 'photosShared', cb: () => void): Promise<{ remove(): Promise<void> }>;
}
```

`webPath` is fetchable from the page (`fetch(webPath).then(r => r.blob())`). If the spike (Task 0) shows it is not, Task
0 replaces `webPath` with the transport it proved and updates this block before Task 3 starts.

### Task 0: Spike — go/no-go on a real phone (throwaway)

Scratch Capacitor project in the session scratchpad, not in either repo. No tests.

- [ ] **Step 1:** Ask the owner to connect the phone (`adb devices` shows it) and say which gallery apps it has.
- [ ] **Step 2:** Scaffold scratch Capacitor Android app with `server.url = https://spyo-map.isntall.net`, minSdk 36,
  the Global Constraints permissions, and a Kotlin plugin with `pickPhotos` (`ACTION_OPEN_DOCUMENT`, `image/*`,
  `EXTRA_ALLOW_MULTIPLE`; `setRequireOriginal` on MediaStore URIs; copy each to `cacheDir/picked/`; return
  `webPath = bridge.portablePath`-style URL via Capacitor's file-src conversion) and an `ACTION_SEND(_MULTIPLE)`
  receiver doing the same. Log `ExifInterface.latLong` for every file to logcat tag `SpyoSpike`.
- [ ] **Step 3: (a)** Owner picks 5 geotagged photos; `adb logcat -s SpyoSpike` must show non-null lat/long for all.
  Repeat after granting only "Select photos" partial access; record whether originals keep GPS.
- [ ] **Step 4: (b)** Owner shares 2 photos each from the phone's gallery app, Google Photos and Files; record which
  keep GPS.
- [ ] **Step 5: (c)** Via WebView remote debugging (`adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`,
  CDP `Runtime.evaluate` from a small node script), fetch each `webPath` from the live-site origin for 10 × ~8 MB
  photos; record success and total time. If fetch fails, try a chunked base64 `readChunk(id, offset, len)` method and
  record that instead.
- [ ] **Step 6: (d)** Add Credential Manager `GetGoogleIdOption(serverClientId = <web GOOGLE_CLIENT_ID>)`; ask the owner
  for the client ID and to create the Android OAuth client for the spike's debug SHA-1 (`./gradlew signingReport`). Must
  log an ID token whose `aud` is the web client ID.
- [ ] **Step 7:** Append a `## Spike findings` section to the spec with results (a)–(d), the chosen transport, and any
  change to the shared contract; commit `chore: record spike findings`. **If (a) failed, stop and report.**

### Task 1: `spyo-map` — branch and shared type

**Files:** Modify `packages/shared/src/upload.ts`

**Interfaces:** Produces `IdTokenSignInRequest { idToken: string }`; `REJECT_COPY.noGpsInApp`.

- [ ] **Step 1:**
  `cd ~/Documents/Projects/spyo-map && git checkout main && git pull && git checkout -b mobile/native-signin`
- [ ] **Step 2:** Add `export interface IdTokenSignInRequest { idToken: string }` and
  `noGpsInApp: 'No location data — the sharing app may have removed it; use Choose photos instead.'` to `REJECT_COPY`.
- [ ] **Step 3:** `npx nx run-many -t test` still passes (shared has no build; consumers typecheck it). Commit
  `feat: add native sign-in request type and in-app no-GPS copy`.

### Task 2: `spyo-map` — `POST /auth/google/idtoken`

**Files:**

- Modify: `packages/worker/src/auth/google.ts`, `packages/worker/src/auth/routes.ts`
- Test: `packages/worker/tests/auth-idtoken.test.ts` (same mocking style as `auth-routes.test.ts`)

**Interfaces:**

- Produces:
  `verifyIdToken(idToken: string, clientId: string, fetchFn: typeof fetch, nowSec: number): Promise<string | null>`
  exported from `google.ts` (returns verified email or null).
- Endpoint: `POST /auth/google/idtoken`, JSON `IdTokenSignInRequest`. Responses: `200` `MeResponse` + `Set-Cookie:
  spyo_session=…; Max-Age=604800; Path=/; HttpOnly`; `400 {error:"Bad request"}`; `401 {error:"Invalid token"}`;
  `403 {error:"Forbidden"}` (cross-origin); `403 {error:"Not allowed"}` (not an active uploader); `405` other methods;
  `500 {error:"Sign-in is not configured"}`. All `cache-control: no-store`.

- [ ] **Step 1: Write failing tests** — stub `fetchFn` answering
  `https://oauth2.googleapis.com/tokeninfo?id_token=<tok>` with
  `{aud:'client-id', iss:'https://accounts.google.com', email:EMAIL, email_verified:'true', exp:String(now+600)}`:
  - `valid token for an active uploader → 200, displayName, spyo_session cookie verifies to that uploaderId`
  - `aud mismatch → 401, no set-cookie`
  - `iss 'accounts.google.com' (no scheme) is accepted`; `iss 'https://evil.test' → 401`
  - `email_verified 'false' → 401`; `exp in the past → 401`; `tokeninfo non-200 → 401`
  - `valid token, findActiveUploaderByEmail → null → 403 {error:'Not allowed'}, no set-cookie`
  - `missing/foreign Origin → 403 {error:'Forbidden'}` and tokeninfo is never fetched
  - `body not JSON or idToken missing/empty/non-string → 400`
  - `GET → 405`
- [ ] **Step 2:** `npx nx run @spyo-map/worker:test -- auth-idtoken` → FAIL (route not found).
- [ ] **Step 3:** Implement `verifyIdToken` (accept `email_verified` as `true` or `'true'`; iss in
  `{'accounts.google.com','https://accounts.google.com'}`; `Number(exp) > nowSec`) and the route in `handleAuthRequest`,
  reusing `isSameOrigin`, `isConfigured`, `findActiveUploaderByEmail`, `signSession`, `serializeCookie` exactly as
  `callback()` does. Wrap Google/D1/signing throws → 401 for verify, 500 otherwise.
- [ ] **Step 4:** Worker tests pass; full `npx nx run-many -t test` and `npm run lint` pass.
- [ ] **Step 5:** Commit `feat: sign in with a Google ID token for the mobile app`.

### Task 3: `spyo-map` — `native.ts` bridge wrapper and `UploadClient.signInWithIdToken`

**Files:**

- Create: `packages/frontend/src/utils/native.ts`; Modify: `packages/frontend/src/services/UploadClient.ts`
- Test: `packages/frontend/tests/native.test.ts`, extend `packages/frontend/tests/UploadClient.test.ts`

**Interfaces:**

- Consumes: shared contract above (copy the two interfaces into `native.ts`; declare
  `window.Capacitor?: { isNativePlatform(): boolean; Plugins: { SpyoNative?: SpyoNativePlugin } }`).
- Produces (all in `native.ts`):
  - `isNativeApp(): boolean` — true only if `isNativePlatform()` and `SpyoNative` exists.
  - `nativeSignIn(silent: boolean): Promise<string | null>` — idToken; `null` on `CANCELED`/`NO_CREDENTIAL`; throws on
    other errors.
  - `nativeSignOut(): Promise<void>`; `nativeHasSignedInBefore(): Promise<boolean>`
  - `nativePickPhotos(): Promise<NativeFiles>`; `nativeSharedPhotos(): Promise<NativeFiles>` where
    `NativeFiles = { files: File[]; failed: string[] }` (failed = names whose fetch failed).
  - `onNativeShare(cb: () => void): () => void` — returns an unsubscribe.
- Produces in `UploadClient`: `signInWithIdToken(idToken: string): Promise<MeResponse | 'denied'>` — 200 → body, 403
  `Not allowed` → `'denied'`, anything else throws.

- [ ] **Step 1: Failing tests** (mock `window.Capacitor` and `global.fetch`):
  - `isNativeApp false without window.Capacitor, false when SpyoNative missing, true when present`
  - `nativeSignIn returns token; returns null for CANCELED and NO_CREDENTIAL; rethrows FAILED`
  - `nativePickPhotos turns webPaths into Files with name and mimeType, and lists a 404 webPath under failed`
  - `onNativeShare unsubscribe calls handle.remove()` -
    `signInWithIdToken POSTs {idToken} same-origin to /auth/google/idtoken; 200 → MeResponse; 403 → 'denied'; 401 throws`
- [ ] **Step 2:** `npx nx run @spyo-map/frontend:test -- native UploadClient` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Tests pass. Commit `feat: add native app bridge for the upload page`.

### Task 4: `spyo-map` — UploadPage in-app behaviour

**Files:** Modify `packages/frontend/src/components/UploadPage.tsx`; Test: extend
`packages/frontend/tests/UploadPage.test.tsx` (mock `../src/utils/native` with `jest.unstable_mockModule`, like
`UploadClient`).

**Interfaces:** Consumes Task 3's exports and `REJECT_COPY.noGpsInApp`.

Behaviour (only when `isNativeApp()`; browser behaviour and its existing tests unchanged):

- Signed out: render a **button** "Sign in with Google" (not the link) → `nativeSignIn(false)` → `signInWithIdToken` →
  set `me`; `'denied'` shows "This account is not allowed to upload."; thrown error shows "Sign-in failed. Please try
  again."; `null` (cancel) shows nothing.
- On mount: if `getMe()` is null and `nativeHasSignedInBefore()` → `nativeSignIn(true)` → exchange; failures silent.
- Signed in: replace drop zone input with button "Choose photos" (disabled while busy) → `nativePickPhotos()` →
  `handleFiles(files)`; each `failed` name → rejected with `UPLOAD_FAILED_COPY`.
- Once signed in, and on every `onNativeShare` event: `nativeSharedPhotos()` → same path as picking.
- Rejection reason `REJECT_COPY.noGps` is displayed as `REJECT_COPY.noGpsInApp`.
- Log out also calls `nativeSignOut()`.

- [ ] **Step 1: Failing tests:**
  - `in app, signed out shows a Sign in button, and success shows "Signed in as Ada"`
  - `in app, denied token shows the not-allowed copy`; `cancel shows no alert`
  - `in app, silent sign-in runs on mount when signed in before, and not otherwise`
  - `in app, Choose photos uploads picked files and lists failed ones as rejected`
  - `in app, shared photos are processed on sign-in and again when a photosShared event fires`
  - `in app, a no-GPS rejection shows the in-app copy`
  - `in app, log out calls nativeSignOut`
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** `npx nx run-many -t test` and `npm run lint` pass.
- [ ] **Step 5:** Commit `feat: native sign-in, photo picking and sharing on the upload page`.
- [ ] **Step 6:** Push branch, `gh pr create` into `main` (body ends with the Claude Code attribution). After the owner
  merges, deploy is the owner's call (`npx wrangler deploy --config packages/worker/wrangler.toml` after
  `npm run build`); ask before running it.

### Task 5: `spyo-map-mobile` — scaffold

**Files:** Create `package.json`, `capacitor.config.ts`, `www/index.html`, `src/definitions.ts`, `mise.toml`,
`.gitignore`, `CLAUDE.md`, `README.md`, `android/` (generated).

- [ ] **Step 1:** `main` already has an empty root commit and `design` (spec + plan) sits on it. From `design`:
  `git checkout -b feat/android-app`.
- [ ] **Step 2:** `mise.toml`: node (same version as `spyo-map/mise.toml`), java `21`. `npm init -y`; install
  `@capacitor/core @capacitor/android` and dev `@capacitor/cli`, `typescript`.
- [ ] **Step 3:** `capacitor.config.ts`: `appId 'net.isntall.spyomap'`, `appName 'Spyo Map'`, `webDir 'www'`,
  `server: { url: 'https://spyo-map.isntall.net', cleartext: false }`, `android: { allowMixedContent: false }`.
  `www/index.html`: minimal "Spyo Map needs an internet connection" page. `src/definitions.ts`: the shared contract.
- [ ] **Step 4:** `npx cap add android`; set `minSdkVersion = 36` (and compile/target 36) in `android/variables.gradle`; add Kotlin Android
  plugin to the Gradle build; add JUnit 4 test dependency.
- [ ] **Step 5:** `CLAUDE.md` (terse: purpose, commands, conventions copied from `spyo-map`, link to spec/plan, "keep
  `src/definitions.ts` and `spyo-map/packages/frontend/src/utils/native.ts` in sync"); `README.md` (build, sideload,
  Google Cloud Android OAuth client setup, release secrets).
- [ ] **Step 6:** Verify `cd android && ./gradlew assembleDebug testDebugUnitTest` succeeds; `adb install -r` the debug
  APK and the owner confirms the live map loads full-screen. Commit `feat: scaffold Capacitor Android app`.
- [ ] **Step 7:** Ask the owner before creating the public GitHub repo; then
  `gh repo create mhvelplund/spyo-map-mobile --public --source . --remote origin`, push `main`, `design`,
  `feat/android-app`.

### Task 6: `SpyoNative` plugin — sign-in

**Files:** Create `android/app/src/main/java/net/isntall/spyomap/SpyoNativePlugin.kt`, `.../SignInErrors.kt`; Modify
`MainActivity.kt` (`registerPlugin(SpyoNativePlugin::class.java)` before `super.onCreate`), `android/app/build.gradle`
(deps `androidx.credentials:credentials`, `credentials-play-services-auth`,
`com.google.android.libraries.identity.googleid:googleid`; `buildConfigField "String", "WEB_CLIENT_ID"` from
`gradle.properties` key `spyoWebClientId`); Test: `android/app/src/test/java/net/isntall/spyomap/SignInErrorsTest.kt`.

**Interfaces:** Produces plugin methods `signIn`, `signOut`, `hasSignedInBefore` per the shared contract.
`SignInErrors.codeFor(e: Exception): String` maps `GetCredentialCancellationException → "CANCELED"`,
`NoCredentialException → "NO_CREDENTIAL"`, else `"FAILED"`. Flag in `SharedPreferences("spyo", …)` key `signedInBefore`.

- [ ] **Step 1:** Failing JUnit tests for `codeFor` (three cases). `./gradlew testDebugUnitTest` → FAIL.
- [ ] **Step 2:** Implement. `signIn({silent:true})` uses `GetGoogleIdOption(filterByAuthorizedAccounts = true,
  autoSelectEnabled = true)`; `silent:false` uses `filterByAuthorizedAccounts = false`. On success set the flag.
  `signOut` clears flag + `clearCredentialState(ClearCredentialStateRequest())`.
- [ ] **Step 3:** Unit tests pass. Owner adds Android OAuth client(s) for the debug and release SHA-1s (print with
  `./gradlew signingReport`) and gives the web client ID for `gradle.properties`.
- [ ] **Step 4:** On device (after Task 4 is deployed): sign in on `/#/upload` → "Signed in as …"; force-stop and reopen
  → still signed in; log out → reopen → signed out with no silent re-sign-in.
- [ ] **Step 5:** Commit `feat: native Google sign-in`.

### Task 7: `SpyoNative` plugin — pick and share

**Files:** Create `.../PhotoImporter.kt` (URI → cached original + `PickedPhoto`), `.../ShareIntents.kt`; Modify
`SpyoNativePlugin.kt`, `MainActivity.kt` (`onNewIntent`), `AndroidManifest.xml` (permissions; intent filters
`ACTION_SEND` and `ACTION_SEND_MULTIPLE` for `image/*`; `launchMode="singleTask"`); Test: `.../ShareIntentsTest.kt`.

**Interfaces:**

- `ShareIntents.urisFrom(action: String?, type: String?, single: Uri?, multiple: List<Uri>?): List<Uri>` — empty unless
  action is SEND/SEND_MULTIPLE and type starts with `image/`.
- `PhotoImporter.import(context, uri): PickedPhoto?` — null when unreadable; applies `setRequireOriginal` for MediaStore
  authority URIs; copies to `cacheDir/picked/<uuid>-<displayName>`; clears files older than 1 day on each call.
- Pending shares held in the plugin; `getSharedPhotos` drains them; a new share calls `notifyListeners("photosShared")`
  and navigates the WebView to `https://spyo-map.isntall.net/#/upload`.

- [ ] **Step 1:** Failing JUnit tests for `urisFrom` (SEND image, SEND_MULTIPLE, non-image type, other action, nulls).
- [ ] **Step 2:** Implement; `pickPhotos` requests `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION`
  via Capacitor `@Permission` aliases, then launches `ACTION_OPEN_DOCUMENT` multi-select (or the mechanism Task 0
  proved). Unit tests pass.
- [ ] **Step 3:** On device: pick 5 geotagged photos → all "Added"; re-pick → "Duplicates skipped"; share 2 from the
  gallery app (cold and while open on `/#/upload`) → uploaded; share from Google Photos → in-app no-GPS copy if
  stripped.
- [ ] **Step 4:** Commit `feat: native photo picking and share target`.

### Task 8: Native feel

**Files:** Create `.../LinkPolicy.kt`; Modify `MainActivity.kt`, `AndroidManifest.xml`, `res/` (icon, splash, colors,
`xml/shortcuts.xml`); Test: `.../LinkPolicyTest.kt`.

**Interfaces:** `LinkPolicy.isInApp(url: String): Boolean` — true only for `https://spyo-map.isntall.net/**`; everything
else (incl. `https://spyo-img.isntall.net`, `mailto:`, social share URLs) opens via `Intent.ACTION_VIEW`.

- [ ] **Step 1:** Failing tests for `isInApp` (site root, hash route, other host, img host, `http://` site, mailto).
- [ ] **Step 2:** Implement: link interception through Capacitor's `shouldOverrideLoad` hook / `WebViewClient`; back
  button → `webView.canGoBack()` ? `goBack()` : finish; status bar colour matching the site header; launcher icon +
  splash from the site favicon; static shortcut "Upload" → `/#/upload`.
- [ ] **Step 3:** Tests pass; on device: back from an image route returns to map, back at map exits, a share link opens
  outside the app, long-press icon → Upload works.
- [ ] **Step 4:** Commit `feat: back navigation, external links, icon and shortcut`.

### Task 9: Release pipeline and update check

**Files:** Create `.github/workflows/release.yml`, `.../UpdateCheck.kt`, `.../Versions.kt`; Modify
`android/app/build.gradle` (release signing from env `SPYO_KEYSTORE_PATH`, `SPYO_KEYSTORE_PASSWORD`, `SPYO_KEY_ALIAS`,
`SPYO_KEY_PASSWORD`; `versionName`/`versionCode` from `-PappVersion=x.y.z`, default `0.0.0`); Test:
`.../VersionsTest.kt`.

**Interfaces:**

- `Versions.parse(tag: String): Triple<Int,Int,Int>?` (accepts `v1.2.3` and `1.2.3`);
  `Versions.code(v): Int = major*10000 + minor*100 + patch`;
  `Versions.isNewer(latestTag: String, current: String): Boolean`.
- `UpdateCheck.run(activity)` — on cold start, background GET
  `https://api.github.com/repos/mhvelplund/spyo-map-mobile/releases/latest`; if `isNewer(tag_name, VERSION_NAME)` and an
  asset ends in `.apk`, show a dialog "Update available (vX)" with "Download" → `ACTION_VIEW` on `browser_download_url`;
  errors ignored.
- Workflow: on push of tag `v*`: checkout, setup node (from mise) + java 21, `npm ci`, `npx cap sync android`, decode
  secret `ANDROID_KEYSTORE_BASE64`, `./gradlew assembleRelease testReleaseUnitTest -PappVersion=${tag#v}`,
  `gh release create $tag spyo-map-$tag.apk --generate-notes`.

- [ ] **Step 1:** Failing tests: `parse` (valid, missing v, garbage → null), `code(1.2.3) == 10203`,
  `isNewer('v0.2.0','0.1.9')`, `!isNewer('v0.1.0','0.1.0')`, `!isNewer('garbage','0.1.0')`.
- [ ] **Step 2:** Implement; tests pass.
- [ ] **Step 3:** Owner creates the release keystore (`keytool -genkeypair …`), sets the four repo secrets with
  `gh secret set`, and registers the release SHA-1 in the Android OAuth client.
- [ ] **Step 4:** Merge PR, tag `v0.1.0`, confirm the release has the APK; install it over adb. Tag `v0.1.1`; reopen the
  app → update dialog → download installs over it.
- [ ] **Step 5:** Commit `feat: signed release builds and in-app update check`; open PR into `main`.
