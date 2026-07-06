# EnigmaTV Project Rules

## Build System

- **NEVER run Gradle, Java, `./gradlew`, Android Studio, or any local build tool.**
  The user's machine does not have a working Java/Android SDK environment.
  All builds happen exclusively via **GitHub Actions** (`main.yml`).
- **NEVER run `./gradlew`, `gradlew.bat`, or any `gradle` command** — they will fail.
- **DO NOT attempt to compile, lint, or run the app locally** for any reason.
- To verify code correctness, do a careful manual code review and check for obvious syntax errors before pushing.

## Release & Versioning

- **Always bump the version on every code commit**, no exceptions.
  - Increment `versionCode` by 1 (e.g. 31 -> 32).
  - Increment `versionName` by a patch version (e.g. `2.18.5` -> `2.18.6`).
  - Both fields are in `app/build.gradle.kts`.
- **Do NOT bump the version for workflow-only changes** (e.g. editing `.github/workflows/*.yml`, `README.md`, or other non-code files) since those don't produce a new APK.
- **Never manually create git tags.** The `main.yml` GitHub Actions workflow automatically creates and publishes a GitHub Release tagged `vX.X.X` based on the `versionName` in `build.gradle.kts` every time a push lands on `main`.
- **Always write a meaningful commit message body.** The commit message subject + body are automatically used as the GitHub Release notes by the CI workflow.

## Git & Push Workflow

- **Always push immediately after committing.** Never leave commits in a local-only state — the user monitors GitHub Actions and expects changes to appear there promptly.
- **Always use semicolons (`;`) to chain commands in PowerShell**, not `&&`. The `&&` operator is not a valid statement separator in Windows PowerShell 5 and will cause a parse error.
  - Correct: `git add .; git commit -m "msg"; git push`
  - Wrong:   `git add . && git commit -m "msg" && git push`
- Use `git push origin main` and `git push origin <tag>` as separate commands if needed.

## Android / Kotlin Code Rules

- The app targets **Android TV and phones**. Always account for both `ScreenLayout.TV` and `ScreenLayout.PHONE` when writing UI code.
- **Do not use `Dialog` for full-screen overlays** in Compose. Use a `Box` with `zIndex` inside the main composition instead — Android Dialogs break edge-to-edge insets, especially in landscape.
- **Prefer sequential network calls over parallel bursts** when hitting rate-limited APIs (e.g., `streamed.pk`). Use a small `delay()` between requests to avoid Cloudflare 429 errors.
- Always add explicit `connectTimeout` and `readTimeout` to `OkHttpClient` builders to prevent UI hangs on slow connections.
- **FileProvider** is required for `ACTION_INSTALL_PACKAGE` intents on Android 8+. Always declare it in `AndroidManifest.xml` and provide the corresponding `res/xml/file_paths.xml`.
- The `REQUEST_INSTALL_PACKAGES` permission must be declared in the manifest for in-app updater installs to work.

## In-App Updater

- The auto-updater checks the **latest GitHub Release** via the public API (`https://api.github.com/repos/RoAPI-Discord-Bot/EnigmaTV/releases/latest`).
- For the updater to detect a new version, the GitHub Release **must exist** (created by CI) and `versionName` in the APK must be lower than the tag in the release.
- **The update dialog must not appear before the user passes the profile picker screen.** Gate the dialog behind `!state.showProfilePicker && state.openingProfileId == null`.
- When parsing release `body` from the GitHub API, always use `isNull("body")` before `optString()` — the API returns a literal JSON `null` (not a missing key) which causes `optString` to return the string `"null"` if not handled.
