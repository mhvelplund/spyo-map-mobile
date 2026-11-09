# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Android app (Capacitor) for Spyo Map. The WebView loads the live site `https://spyo-map.isntall.net`; native code
only adds Google sign-in, photo picking with GPS intact, a Share-sheet target, and app polish. Web/API changes belong
in the `spyo-map` repo, not here.

- Design: `docs/superpowers/specs/2026-11-07-mobile-app-design.md` (incl. spike findings). Plan:
  `docs/superpowers/plans/2026-11-07-android-app.md`.
- `src/definitions.ts` is the native↔web contract. Keep it in sync with
  `spyo-map/packages/frontend/src/utils/native.ts`.
- Native code: `android/app/src/main/java/net/isntall/spyomap/` (Kotlin). Pure logic goes in small classes with JUnit
  tests in `android/app/src/test/java/net/isntall/spyomap/`.

## Commands

- `mise install` — Node 24, Java 21, gh (see `mise.toml`). Then `npm ci`. The Android SDK is not in mise; it comes
  from `ANDROID_HOME` (Android Studio's SDK).
- `npx cap sync android` — after changing `capacitor.config.ts` or npm deps.
- `npm run typecheck` — type-checks `src/definitions.ts` (CI runs it).
- `mise run build` — debug APK + unit tests.
- `mise run gradle <args>` — any Gradle task, e.g. `mise run gradle testDebugUnitTest --tests
  net.isntall.spyomap.VersionsTest`. Both tasks pass `-Dorg.gradle.java.home` to override a JDK 17 pin in the global
  `~/.gradle/gradle.properties`; Capacitor 8 needs JDK 21. Don't call `./gradlew` bare.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` — install on the connected phone.
- Debug the WebView from desktop Chrome at `chrome://inspect` (debug builds only).
- Release: push a `v*` tag; `.github/workflows/release.yml` builds a signed APK onto a GitHub Release.

## Architecture

- `MainActivity` routes intents: `SEND`/`SEND_MULTIPLE` image shares (filtered by `ShareIntents`, which rejects the
  app's own FileProvider authority) and in-app `VIEW` links / the Upload launcher shortcut (`LinkPolicy`). Shares go
  to `SpyoNativePlugin.onPhotosShared`, or the static `PendingShares` queue if the plugin isn't loaded yet; then the
  WebView loads `LinkPolicy.UPLOAD_URL`.
- Photo methods ask for `ACCESS_MEDIA_LOCATION` first (without it Android strips GPS; refusal is non-fatal), then
  `PhotoImporter` copies originals into `cacheDir/picked`. The page `fetch()`es them via the Capacitor
  `_capacitor_file_` `webPath`. Unreadable items are returned by display name in `failed`.
- Sign-in: Credential Manager → Google ID token for the **web** OAuth client (`spyoWebClientId` in
  `android/gradle.properties` → `BuildConfig.WEB_CLIENT_ID`); the Worker verifies it at `POST /auth/google/idtoken`.
  Each signing key's SHA-1 needs an Android OAuth client (see README).
- `openGallery` launches the gallery app (`CATEGORY_APP_GALLERY`) for the map page's album button. The location
  permissions in the manifest exist only for `navigator.geolocation` in the WebView (Capacitor prompts at runtime).
- `UpdateCheck` queries GitHub Releases on cold start. Local builds are `0.0.0-dev` and never prompt.
- Versioning: release builds pass `-PappVersion=x.y.z` from the tag. The versionCode formula is duplicated in
  `android/app/build.gradle` and `Versions.code()` — change both together.
- Release signing reads `SPYO_KEYSTORE_PATH`, `SPYO_KEYSTORE_PASSWORD`, `SPYO_KEY_ALIAS`, `SPYO_KEY_PASSWORD`; the
  APK is unsigned if they're absent.
- `www/` is only a fallback page; nothing ships from it.

## Conventions

- minSdk 36 (Android 16). Sideloaded only; no Play Store.
- Conventional commits only: `feat`, `chore`, or `feat!`. Never commit to `main`; feature branch + PR.
- Add or update unit tests with any new native logic. Keep testable logic Android-free (generic `T`, plain strings;
  see `ShareIntents.urisFrom`). Unit tests run with `returnDefaultValues = true` and real `org.json`.
- Terse chat responses.
