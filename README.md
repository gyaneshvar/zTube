# zTube

An ad-free YouTube client for Android with the "premium" features built in: background play, picture-in-picture, playback speed control, and offline downloads. No Google account, no tracking — everything (subscriptions, history, playlists) lives on your phone.

> **Personal / educational project.** zTube is not affiliated with YouTube or Google. Blocking ads and downloading videos goes against YouTube's Terms of Service — use it at your own discretion.

## Download

Grab the latest APK from the **[Releases page](../../releases/latest)**.

1. Open the release on your Android phone and download `zTube-<version>.apk`.
2. Tap the file. If Android blocks it, allow **Install unknown apps** for your browser.
3. Install and open zTube.

Requires **Android 8.0 (API 26)** or newer. Updates install over the previous version and keep your library.

## Features

| | |
|---|---|
| **No ads** | Streams are played directly — YouTube's ads are never requested |
| **SponsorBlock** | Auto-skips in-video sponsor reads, intros, self-promo (toggle in the You tab) |
| **Background play** | Keeps playing with the screen off or app in background, with lock-screen / notification controls |
| **Picture-in-picture** | Automatically pops into a floating window when you leave the app |
| **Speed control** | 0.25× – 3×, remembered across videos |
| **Quality selection** | 144p – 1080p, original audio track preferred |
| **Up next & autoplay** | Related videos under the player; autoplay continues in mini-player, PiP and background |
| **Shorts** | Shelf on Home, a Shorts tab for your subscriptions and a Shorts section on each channel; swipe vertically through looping Shorts |
| **Offline downloads** | Pick a quality or audio-only; resumable; plays in airplane mode |
| **Local library** | Subscriptions feed (saved for instant start, refreshed in the background), watch history with resume, playlists |
| **Takeout import** | Import your YouTube subscriptions from Google Takeout `subscriptions.csv` |
| **Google sign-in** | Optional, read-only import of your YouTube subscriptions and playlists (see below) |
| **Gestures** | Double-tap left/right edge to seek ±10 s, rotate for fullscreen |
| **Live streams** | HLS live playback |

## Build from source

Requirements: JDK 21, Android SDK (compileSdk 37.1, build-tools 36).

```bash
git clone <this-repo-url>
cd zTube
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install on a connected phone with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Google sign-in (optional)

Signing in with Google imports your YouTube subscriptions, playlists and Liked videos (the most recent 1000) and keeps them in sync: a sign-in prompt appears once on first launch and once after each update while you're signed out, the app syncs when you open it if the last sync is over 4 hours old, plus a daily background sync, and **Sync now** in the You tab syncs manually. It is strictly read-only: zTube never changes anything on your YouTube account. Synced data stays on your device, and access tokens are never stored — they are requested from Google Play services whenever a sync runs.

- Works only for Gmail accounts that were added as test users of this build (up to 100). Other accounts see "access blocked"; the Takeout import still works for everyone.
- The app stays in Google's Testing mode, so sign-in expires every 7 days — the You tab shows "Sign in again" when that happens.
- YouTube playlists, including Liked videos, are read-only in zTube. Signing out lets you keep the synced items as local ones or remove them; anything you added in zTube is always kept.

## Updates

When you open the app, zTube checks GitHub releases for a newer version (at most once every 12 hours) and offers to download it; **Later** hides that version for 3 days. **Check for updates** in the You tab checks on demand.

### Set up your own Google Cloud project

If you build from source, sign-in needs your own OAuth client:

1. Create a Google Cloud project and enable **YouTube Data API v3**.
2. Configure the OAuth consent screen: user type **External**, publishing status **Testing**, scopes `youtube.readonly` and `email`.
3. Add every Gmail account that should sign in as a test user.
4. Create an **Android** OAuth client with package name `com.ztube` and the SHA-1 of the key you sign with (debug key: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).

No client ID or API key goes into the code. Only APKs signed with the registered key can sign in.

## Tech stack

- **Kotlin**, **Jetpack Compose** + Material 3, Navigation-Compose
- **Media3 / ExoPlayer** in a `MediaSessionService` — background play, media notification, PiP
- **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — search, stream and channel extraction without an API key
- **[SponsorBlock API](https://sponsor.ajay.app/)** — privacy-preserving hashed lookups
- **Hilt**, **Room**, **DataStore**, **WorkManager** (downloads), **OkHttp**, **Coil**

## Project structure

```
app/src/main/java/com/ztube/
├── data/
│   ├── db/            Room database: subscriptions, history, playlists, downloads
│   ├── extractor/     NewPipe downloader + YoutubeRepository (search, streams, channels, paging)
│   ├── feed/          Subscription feed, saved in Room
│   ├── prefs/         DataStore settings (speed, quality, autoplay, SponsorBlock, recent searches)
│   └── sponsorblock/  SponsorBlock client
├── playback/          PlaybackService, stream selection, YouTube data source, autoplay, error recovery
├── download/          DownloadWorker (chunked, resumable) + DownloadManager
└── ui/                Compose screens: home, search, player, channel, library, playlist, settings
```

## How playback works

1. The UI sends a `MediaItem` whose id is the YouTube URL to `PlaybackService` via a shared `MediaController`.
2. The service resolves it — a finished download plays from local files; otherwise NewPipeExtractor fetches the stream info.
3. Video-only and audio streams are wrapped into DASH manifests so ExoPlayer requests small segments (avoids YouTube throttling), and merged for playback. Live streams use HLS.
4. If a stream URL expires mid-video, the service re-resolves it and resumes at the same position.
5. When the current video is the last in the queue, the service enqueues the next related video (autoplay).

## Known limitations

- YouTube changes break extraction from time to time — the fix is usually bumping `NewPipeExtractor` in `gradle/libs.versions.toml`.
- Google sign-in imports subscriptions and playlists only (no history or home feed) and works for test users only; watch history stays local.
- Release APKs are signed with a debug key — fine for sideloading, not for the Play Store.

## Credits

Built on the excellent work of [NewPipe](https://github.com/TeamNewPipe/NewPipe) / NewPipeExtractor and [SponsorBlock](https://sponsor.ajay.app/).

## License

[GPL-3.0](LICENSE) — same as NewPipeExtractor, which zTube bundles.
