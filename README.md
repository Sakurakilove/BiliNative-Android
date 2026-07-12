# Bili Mobile Android Phase 4

Native Android client built with Kotlin, Jetpack Compose Material 3, OkHttp, Media3, Coil, and ZXing.

## Run

1. Open this directory in Android Studio with JDK 17.
2. Install Android SDK 35 when prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

GitHub Actions uses Gradle 8.9 to run `lintDebug assembleDebug` and publishes the debug APK as an artifact.

## Phase 4 behavior

- Compact edge-to-edge home with a stable popular recommendation baseline, correctly decoded region rankings, dense image-first cards, loading skeletons, retained content on refresh failures, and three state-retaining tabs. 推荐 and 热门 remain separately labeled even when both use the reliable popular feed.
- Search is an integrated top-level destination. Logged-in dynamic video posts use the heterogeneous Polymer feed endpoint and safely ignore unsupported card types.
- Detail requests progressive HTML5 playback first through 720P, falls back once to merged DASH video/audio (or back to progressive), then requires an explicit retry after another source error. It has a compact custom controller, retry/replay, quality and speed controls, full-window landscape playback using the same player, saved position, timed XML danmaku, related videos, and independently loaded comments/replies.
- Like, watch-later, and favorite status requests are isolated and unavailable actions stay disabled with retry feedback. Watch-later membership uses the endpoint's complete returned list while the profile keeps a 12-item preview. Favorite membership is tracked per folder; add shows all folders, remove shows only folders containing the video, and status is reloaded after writes. Authenticated writes submit `bili_jct` with Origin/Referer headers.
- Profile shows encrypted QR login, identity, compact account shortcuts, thumbnail previews for history/watch later, favorite-folder summaries, version/cache information, disclaimer, and local sign-out.
- Search signs requests from runtime WBI navigation keys. No app credentials, official keys, secrets, trademarks, or bundled copyrighted assets are included.

## Caveats

- This is an unofficial client and is not affiliated with Bilibili. It depends on undocumented public web APIs whose schemas and anti-bot requirements can change.
- Playback availability and quality depend on account rights, region, and the server response. DRM streams are unsupported.
- The recommendation baseline intentionally uses the public popular endpoint because the anonymous personalization endpoint is currently unstable. Home categories are cached independently in memory and retain their own content on refresh failure; account sections have no durable offline database cache.
- Picture-in-picture, uploader space browsing, gesture brightness/volume, cache clearing, and write operations for comments/danmaku are intentionally deferred rather than represented by nonfunctional UI.
- Cleartext traffic is disabled. Session cookies are encrypted at rest and removed on sign-out.
