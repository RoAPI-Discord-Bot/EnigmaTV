# EnigmaTV

Android streaming app mirroring the **CineFree** (movies) and **CineTV** (TV) experience from `direct.html`.

## Features

- **Movies tab** — TMDB trending & popular, search, multi-source WebView player with “Next Server”
- **TV tab** — trending, popular, on-the-air, continue watching, season/episode picker, same embed sources as the web app
- **Dark UI** — Netflix-style layout (`#0a0a0a` background, red `#e50914` movies accent, gold `#a93` TV accent)

## Stream sources (same as direct.html)

**Movies:** VidLink, Vidsrc.to, Vsembed, AutoEmbed, SuperEmbed, 2Embed  

**TV:** VidLink, Vidsrc.to, AutoEmbed, 2Embed, SmashyStream

## Build & run

Requirements: **Android Studio Ladybug+** or **JDK 17+** and Android SDK 35.

1. Open the `EnigmaTV` folder in Android Studio.
2. Let Gradle sync (or run `gradlew.bat assembleDebug` from the project root after the wrapper is initialized).
3. Run on a device/emulator (API 26+).

If `gradle-wrapper.jar` is missing, open the project in Android Studio once — it will download the wrapper automatically.

## Project structure

```
EnigmaTV/
├── app/src/main/java/com/enigma/tv/
│   ├── data/          # TMDB API, stream URLs, continue-watching store
│   ├── ui/            # Compose screens, WebView player, theme
│   └── MainActivity.kt
└── README.md
```

## Redirect & ad protection (vs. the HTML app)

In `direct.html`, a strict iframe `sandbox` blocks embed hosts from working. On Android, **EnigmaTV** uses a `WebViewNavigationGuard` instead:

- **Blocks main-frame redirects** to unrelated domains (casinos, app stores, shopping, etc.)
- **Blocks popups** (`setSupportMultipleWindows(false)`, `onCreateWindow` returns false)
- **Blocks known ad/tracker hosts** for subresources (DoubleClick, Taboola, etc.)
- **Allows** your embed provider domains, their subdomains, and common video CDN suffixes

This won’t remove every in-player overlay ad (those are same-origin JS), but it stops most “tap play → new tab” hijacks. Use **Next Server** if a provider is too aggressive.

## GitHub Actions build

Workflow: [`.github/workflows/android.yml`](.github/workflows/android.yml)

- Runs on push/PR to `main` or `master`, and manual **workflow_dispatch**
- Builds `assembleDebug` and uploads `enigmatv-debug-apk` as an artifact

Push the repo to GitHub, open **Actions**, and download the APK from the latest run. The workflow uses `gradle/actions/setup-gradle` (no committed `gradlew` required).

## Notes

- Uses the same TMDB API key as `direct.html`.
- Playback loads third-party embed sites in a WebView (same approach as the iframe player in the browser app).
- Continue watching for TV is stored locally via DataStore (equivalent to `localStorage` `cinetv_cw`).
