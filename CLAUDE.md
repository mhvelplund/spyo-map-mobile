# CLAUDE.md

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

- `mise install` — node, Java 21, gh.
- `npx cap sync android` — after changing `capacitor.config.ts` or npm deps.
- `cd android && ./gradlew -Dorg.gradle.java.home="$(mise where java)" assembleDebug testDebugUnitTest` — build +
  unit tests. The `-D` overrides a JDK 17 pin in the global `~/.gradle/gradle.properties`; Capacitor 8 needs JDK 21.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` — install on the connected phone.
- Release: push a `v*` tag; `.github/workflows/release.yml` builds a signed APK onto a GitHub Release.

## Conventions

- minSdk 36 (Android 16). Sideloaded only; no Play Store.
- Conventional commits only: `feat`, `chore`, or `feat!`. Never commit to `main`; feature branch + PR.
- Add or update unit tests with any new native logic.
- Terse chat responses.
