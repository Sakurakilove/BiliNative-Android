# Bili Mobile Android Phase 3

Native Android client built with Kotlin, Jetpack Compose Material 3, OkHttp, Media3, Coil, and ZXing.

## Run

1. Open this directory in Android Studio with JDK 17.
2. Install Android SDK 35 when prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

GitHub Actions uses Gradle 8.9 to run `lintDebug assembleDebug` and publishes the debug APK as an artifact.

## Phase 3 behavior

- Compact edge-to-edge home with real popular/region ranking channels, dense image-first cards, dynamic colors, and three state-retaining tabs.
- Search is an integrated top-level destination. Logged-in dynamic video posts use the heterogeneous Polymer feed endpoint and safely ignore unsupported card types.
- Detail includes fragmented MP4 video/audio merging, multi-segment progressive fallback, quality and speed controls, full-window landscape playback, saved position per video/page, timed XML danmaku, related videos, and independently loaded comment replies.
- Like/unlike, watch-later add/remove, and favorite add/remove are explicit user actions. Favorites are only changed when a folder named `默认收藏夹` is available. These actions require an authenticated encrypted cookie jar and submit `bili_jct` as CSRF with Origin/Referer headers. Coin spending and comment/danmaku posting are not implemented.
- Profile shows encrypted QR login, identity, visible history, favorite-folder summaries, watch later, version/cache information, disclaimer, and local sign-out.
- Search signs requests from runtime WBI navigation keys. No app credentials, official keys, secrets, trademarks, or bundled copyrighted assets are included.

## Caveats

- This is an unofficial client and is not affiliated with Bilibili. It depends on undocumented public web APIs whose schemas and anti-bot requirements can change.
- Playback availability and quality depend on account rights, region, and the server response. DRM streams are unsupported.
- Picture-in-picture, uploader space browsing, gesture brightness/volume, cache clearing, and write operations for comments/danmaku are intentionally deferred rather than represented by nonfunctional UI.
- Cleartext traffic is disabled. Session cookies are encrypted at rest and removed on sign-out.
