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
| **SponsorBlock** | Auto-skips in-video sponsor reads, intros, self-promo (toggle in Settings) |
| **Background play** | Keeps playing with the screen off or app in background, with lock-screen / notification controls |
| **Picture-in-picture** | Automatically pops into a floating window when you leave the app |
| **Speed control** | 0.25× – 3×, remembered across videos |
| **Quality selection** | 144p – 1080p, original audio track preferred |
| **Up next & autoplay** | Related videos under the player; autoplay continues in mini-player, PiP and background |
| **Offline downloads** | Pick a quality or audio-only; resumable; plays in airplane mode |
| **Local library** | Subscriptions feed, watch history with resume, playlists |
| **Takeout import** | Import your YouTube subscriptions from Google Takeout `subscriptions.csv` |
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
│   ├── feed/          Subscription feed
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
- No Google sign-in: subscriptions and history are local only (import via Takeout).
- Release APKs are signed with a debug key — fine for sideloading, not for the Play Store.

## Credits

Built on the excellent work of [NewPipe](https://github.com/TeamNewPipe/NewPipe) / NewPipeExtractor and [SponsorBlock](https://sponsor.ajay.app/).
