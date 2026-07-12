# Bili Mobile Android Phase 5

Native Android client built with Kotlin, Jetpack Compose Material 3, OkHttp, Media3, Coil, and ZXing.

## Run

1. Open this directory in Android Studio with JDK 17.
2. Install Android SDK 35 when prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

GitHub Actions uses Gradle 8.9 to run `lintDebug assembleDebug` and publishes the debug APK as an artifact.

## Phase 5 behavior

- Compact edge-to-edge home with a stable popular recommendation baseline, correctly decoded region rankings, dense image-first cards, loading skeletons, retained content on refresh failures, and three state-retaining tabs. 推荐 and 热门 remain separately labeled even when both use the reliable popular feed.
- Search is an integrated top-level destination. Logged-in dynamic video posts use the heterogeneous Polymer feed endpoint and safely ignore unsupported card types.
- Detail requests progressive HTML5 playback first through 720P, falls back once to merged DASH video/audio (or back to progressive), then requires an explicit retry after another source error. The refined overlay has compact playback/seek controls, full-window landscape playback using the same player, clamped saved positions, and clean view detachment.
- Danmaku loads the comment XML host first and falls back to the API XML endpoint with the same safe parser. Loading, empty, count, and retry states are visible; logged-in users can explicitly send ordinary scrolling danmaku. Comments support explicit top-level posting with retained text on failure.
- Like, coin, watch-later, and favorite status requests are isolated and unavailable actions stay disabled with retry feedback. Coin confirmation clearly identifies irreversible real cost. Favorite membership remains tracked per folder and status reloads after writes.
- Profile history, watch-later, favorite folders, and guarded paged folder contents are independent destinations whose primitive route fields survive rotation. QR remains the default reliable login and uses its creation time for expiry guidance.
- Experimental mainland (+86, internal `cid=1`) SMS login is available behind an explicit entry. It uses an ephemeral host-restricted in-app Geetest v3 WebView; Geetest resources are third-party network requests. Only unsupported captcha/risk-control failures direct users to QR, while correctable failures preserve SMS context. Phone numbers and codes are never persisted.
- A visible privacy card explains direct device-to-Bilibili traffic, local encrypted cookies and playback positions, credential removal on logout, and the unofficial undocumented-API limitation.
- Search signs requests from runtime WBI navigation keys. No app credentials, official keys, secrets, trademarks, or bundled copyrighted assets are included.

## Caveats

- This is an unofficial client and is not affiliated with Bilibili. It depends on undocumented public web APIs whose schemas and anti-bot requirements can change.
- Playback availability and quality depend on account rights, region, and the server response. DRM streams are unsupported.
- The recommendation baseline intentionally uses the public popular endpoint because the anonymous personalization endpoint is currently unstable. Home categories are cached independently in memory and retain their own content on refresh failure; account sections have no durable offline database cache.
- Picture-in-picture, uploader space browsing, gesture brightness/volume, cache clearing, nested reply posting, and password login are intentionally deferred. The experimental undocumented SMS API may stop working; the app never collects a password, auto-solves, or bypasses Geetest.
- Video images use Coil's system-managed memory/disk cache. Captcha WebViews are destroyed and their cache/history are cleared on disposal on a best-effort basis; they do not receive cookie values through JavaScript.
- Cleartext traffic is disabled. Persistent cookies are encrypted at rest; session cookies remain memory-only according to cookie semantics. Both are removed on sign-out.
