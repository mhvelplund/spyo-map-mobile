# Spyo Map for Android

> [!TIP]
> 
> This is a companion app for the [spyo-map](https://github.com/mhvelplund/spyo-map) project.

<img src="docs/img/slop-small.jpg" alt="For robots, by robots. Vibed without love or skill." height="24" />

An Android app for [Spyo Map](https://spyo-map.isntall.net). It shows the live web map full-screen and adds what the
browser can't do on Android:

- **Upload with location intact.** Android browsers strip GPS from photos you pick, so the site rejects them. The app
  uses the system document picker, which keeps the original location data.
- **Share to Spyo Map** from Google Photos, Files or your gallery app.
- **Google sign-in** with the account on your phone.

Requires Android 16 or newer. Only allow-listed uploaders can upload; anyone can browse.

## Install

Download the latest `spyo-map-vX.Y.Z.apk` from
[Releases](https://github.com/mhvelplund/spyo-map-mobile/releases/latest) on the phone and open it (allow installing
from your browser when asked). The app offers new versions itself when you open it.

## Develop

```sh
mise install
npm ci
npx cap sync android
mise run build                 # debug APK + unit tests
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Needs an Android SDK with `ANDROID_HOME` set (Android Studio's is fine). Run other Gradle tasks with
`mise run gradle <task>`.

Inspect the WebView from desktop Chrome at `chrome://inspect` (debug builds only).

## One-time setup

### Google sign-in

Native sign-in uses Android Credential Manager and returns an ID token for the site's existing **web** OAuth client,
which the Worker verifies (`POST /auth/google/idtoken`).

1. Put the web client ID in `android/gradle.properties` as `spyoWebClientId=...apps.googleusercontent.com`.
2. In Google Cloud Console → APIs & Services → Credentials (same project as the web client), create an **Android** OAuth
   client for package `net.isntall.spyomap` for **each** signing key's SHA-1:
   - debug: `mise run gradle signingReport` (variant `debug`)
   - release: `keytool -list -v -keystore spyo-release.jks -alias spyo`

### Release signing

```sh
keytool -genkeypair -v -keystore spyo-release.jks -alias spyo -keyalg RSA -keysize 4096 -validity 10000
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 spyo-release.jks)
gh secret set SPYO_KEYSTORE_PASSWORD
gh secret set SPYO_KEY_ALIAS      # spyo
gh secret set SPYO_KEY_PASSWORD
```

Keep `spyo-release.jks` and its passwords safe: installed apps only accept updates signed with the same key.

### Releasing

```sh
git tag v0.1.0 && git push origin v0.1.0
```
