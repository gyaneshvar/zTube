# zTube — notes for Claude

Ad-free YouTube client for Android (personal project, sideloaded, public repo `gyaneshvar/zTube`, GPL-3.0). User-facing docs are in `README.md`; this file covers what an agent needs to work here.

## Build

- JDK 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (set in git-ignored `local.properties`). compileSdk 37.1, minSdk 26, targetSdk 36, build-tools 36.0.0.
- Build: `./gradlew assembleDebug -q` → `app/build/outputs/apk/debug/app-debug.apk`. Always use the wrapper (Gradle 9.6.0).
- Dependency versions live only in `gradle/libs.versions.toml` (AGP 9.5.2 with built-in Kotlin, Kotlin 2.4.21, KSP, Hilt, Compose BOM, Media3, Room, WorkManager, OkHttp, Coil, NewPipeExtractor from JitPack, play-services-auth).
- No tests in this project. Do not add any unless asked. Verification = a clean `assembleDebug` plus manual testing on the phone.

## Architecture

Single module `app/`, package `com.ztube`, Hilt everywhere, Compose + Material 3, ViewModels exposing `StateFlow`.

- `data/extractor` — NewPipeExtractor init (`OkHttpDownloader`) and `YoutubeRepository` (search, stream info, channels, paging; small LRU cache).
- `data/db` — Room `ZTubeDatabase`: subscriptions, history, playlists, playlist videos, downloads.
- `data/feed` — `FeedRepository`: subscription feed persisted in Room (`feed_videos`, `feed_channels`) and refreshed per channel when stale (45 min); Home reads the DB, `FeedRefreshWorker` refreshes every 8 h on unmetered network.
- `data/prefs` — DataStore `SettingsRepository` (speed, quality, autoplay, SponsorBlock, recents, Google account state).
- `data/update` — `UpdateChecker` (GitHub `releases/latest`, checked on app open at most every 12 h, per-version 3-day snooze); the dialog is `ui/update` (`UpdateViewModel` + `UpdateHost`, hosted in `ZTubeRoot`, manual check in Settings).
- `data/sponsorblock` — SponsorBlock client (hashed lookup).
- `data/account` — optional Google sign-in (`GoogleAuth` on `AuthorizationClient`), YouTube Data API v3 client, `YoutubeSync` (read-only mirror of subscriptions + playlists), `YoutubeSyncWorker` (daily periodic, plus a one-time run enqueued on app foreground when the last sync is over 4 hours old), `YoutubeAccountManager` (entry point for UI). The sign-in flow is shared by Settings and the once-per-version first-launch prompt (`ui/account`: `GoogleSignInViewModel` + `GoogleSignInHost`).
- `playback` — `PlaybackService` (`MediaSessionService` owning ExoPlayer). A `MediaItem`'s `mediaId` is the YouTube URL and is resolved in `onAddMediaItems`: finished downloads first, otherwise NewPipe streams wrapped as DASH and merged. Custom `YoutubeHttpDataSourceFactory` sets the per-client User-Agent and request params. The service owns autoplay and error recovery (re-resolve and resume). UI talks to it through the `PlayerConnection` singleton (`MediaController`).
- `download` — `DownloadWorker` (WorkManager, chunked and resumable) and `DownloadManager`.
- `ui` — screens per package; shared pieces in `ui/components`; navigation in `ui/ZTubeRoot.kt` (bottom tabs Home / Search / Library / You, where You is the Settings screen with the account card and the avatar as its icon); splash overlay in `MainActivity`.

## Rules that are easy to break

- **Room schema changes need a real migration.** Bump `version` in `ZTubeDatabase`, add a `Migration` in `addMigrations(...)`, and commit the exported JSON in `app/schemas/`. There is deliberately no `fallbackToDestructiveMigration`, because it would wipe users' libraries. Entity `@ColumnInfo(defaultValue)` must match the migration SQL.
- **Subscription writes go through the `SubscriptionDao` helpers** (`subscribe`, `importAll`, `unsubscribe`, `restore`), never a plain insert. Rows carry `fromYoutube`, `addedLocally` and `muted`. Unsubscribing a YouTube-sourced channel mutes it. UI queries must filter `muted = 0`.
- **YouTube-sourced playlists** (`youtubePlaylistId != null`) are read-only in the UI and excluded from the add-to-playlist picker.
- **Google auth:** never persist or log access tokens. Only the account email, channel info, last sync time and `needsReauth` go to DataStore.
- When extraction breaks (YouTube changes), the fix is almost always bumping `newpipeExtractor` in the version catalog.

## Code style

Match neighbouring files: plain Kotlin, simple, no clever abstractions. Near-zero comments; only explain non-obvious constraints. HTTP calls use OkHttp + `org.json` (see `SponsorBlockClient`), not Retrofit or kotlinx.serialization.

## Signing and Google sign-in

- APKs are signed with the debug key `~/.android/debug.keystore` on this Mac. Updates only install over earlier versions if built with the same key, so keep it backed up.
- The Google Cloud project's Android OAuth client is bound to package `com.ztube` + this key's SHA-1 `B9:0E:AC:B4:7B:1B:B5:39:54:38:E0:7C:DD:6D:DB:AA:87:7C:A9:16`. No client ID or secret is in the code. The consent screen stays in Testing mode: only Gmail accounts listed as test users (max 100) can sign in, and grants expire after 7 days.

## Release process

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleDebug -q`, copy the APK to `zTube-<version>.apk`.
3. `gh release create v<version> <apk> --repo gyaneshvar/zTube --target main --title "zTube <version>" --notes-file <notes> --latest`.
4. Check the download link returns a valid APK (starts with `PK`).

The in-app update checker reads `releases/latest`, so the tag must be `v<versionName>` and the APK asset name must end in `.apk`.

The README links to `releases/latest`, so it needs no change per release.

## Installing on the test phone

`adb` has never connected to the phone (realme 15 Pro 5G). Instead, serve the APK folder on the LAN (`python3 -m http.server 8765 --bind <mac-lan-ip>`), open it in the phone's browser, install, then stop the server.

## Git

- Repo-local identity: `Gyaneshvar Singh <gyaneshvarsingh@gmail.com>` (personal account, not the work one). Default branch `main`, remote over HTTPS.
- `gh` must have the `gyaneshvar` account active for PRs and releases on this repo.
- No AI attribution in commits, PRs or release notes. Ask before every push.
