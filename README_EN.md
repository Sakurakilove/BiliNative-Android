# BiliNative · Unofficial Bilibili Android Client

A **third-party**, non-official Bilibili Android client built with Kotlin and Jetpack Compose (Material 3). It is fully native, has **no private backend**, and keeps all account data encrypted on-device while talking directly to Bilibili's public web APIs.

> ⚠️ This project is for personal learning, research, and technical exchange only. It is in no way affiliated with or endorsed by Bilibili. See the [Legal Disclaimer](#legal-disclaimer) below.

## Design Principles

- **Lightweight**: No private backend, no redundant background services; small install and runtime footprint.
- **Power-efficient**: No Push, no always-on background polling; requests are on-demand to save battery.
- **Minimal**: Native Material 3 UI with clear hierarchy and direct interactions, free of decorative clutter.

---

## Features

- **Video playback**: Media3 (ExoPlayer) streaming with quality switching, scrubbing, and frame-synced danmaku rendering.
- **Danmaku**: Remote danmaku (official XML hosts with automatic GZIP/DEFLATE decompression and multi-host fallback) plus local danmaku; persisted master toggle, opacity, size, speed, area, and mode filters.
- **Live**: Media3 HLS (AVC) playback, quality selection, polling danmaku (labeled "recent"), and logged-in sending with optimistic local echo plus server confirmation.
- **Login**: Default QR-code login; experimental +86 SMS login (**currently unavailable**: an in-app Geetest WebView was planned; phone number and code are never persisted).
- **Home feed**: Edge-to-edge compact recommendation feed backed by the public popular endpoint, with genuine pull-to-refresh paging.
- **Dynamics**: Post-login Polymer heterogeneous feed with pull-to-refresh and safe ignoring of unsupported card types.
- **Search**: Top-level destination with trending hot search, local search history (clearable), and runtime WBI request signing.
- **Uploader space**: View a UP's profile and video archive via WBI-signed endpoints.
- **Interaction**: Like, coin (with explicit irreversible-cost warning), favorite (per-folder membership tracking), and watch-later, with isolated status and retry feedback.
- **Watch history**: Progress synced to Bilibili via heartbeat reporting.
- **Privacy card**: Explains direct device-to-Bilibili traffic, locally encrypted cookies/positions, and credential removal on logout.
- **Material 3 theme**: Unified typography/shape tokens, light & dark themes, flattened hierarchy, and 48dp touch targets.

---

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose · Material 3 |
| Networking | OkHttp (global interceptor, WBI signing, encrypted local CookieJar) |
| Playback | Media3 (ExoPlayer) · HLS |
| Images | Coil |
| QR | ZXing |
| Min / Target | minSdk 26 / targetSdk 35 |
| Version | 0.2.0 (versionCode 2) |

---

## Features Not Planned

For compliance, privacy, and the "lightweight, power-saving, minimal" positioning, the following are **explicitly out of scope**:

- **Live gifting / tips**: No virtual currency, recharge, or gift flows.
- **Reverse-engineering the official client**: No cracking or reuse of the official app's private protocols, keys, or signing logic.
- **Charging (UP sponsorship)**: No paid sponsorship interfaces.
- **Push notifications**: No Push service, to avoid background residency and extra battery drain.

## Planned Features (actively iterated)

- **Short-video feed**: Bilibili's home separates long and short videos (uniform cover size). Plan to add an immersive vertical swipe-to-watch short-video screen that plays continuously as you scroll down.
- **Follow**: Try to add one-tap follow / unfollow for UPs and a following feed entry.
- **UP space dynamics**: Complete the dynamics view and pagination inside an uploader's profile space.
- **UI rebuild**: Continuously refine the UI into a more polished, minimal design.
- **Premium content playback**: Attempt to support Bilibili Premium (大会员) exclusive content.
- **Anime & movie playback**: Attempt to support bangumi (番剧) and movie playback.

---

## Build & Run

1. Open this directory in Android Studio (JDK 17).
2. Install Android SDK 35 when prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

Alternatively, **GitHub Actions** builds automatically: the workflow runs `lintDebug assembleDebug` with Gradle 8.9 and publishes the debug APK as an artifact.

---

## Privacy & Security

- No private server is bundled; all traffic goes **directly from device to Bilibili**.
- Login credentials (cookies) are stored on-device using `EncryptedSharedPreferences`; session cookies stay memory-only and are removed on logout.
- Cleartext traffic is disabled.
- No official keys, secrets, trademarks, or bundled copyrighted assets are included.
- Search requests are signed at runtime with WBI navigation keys; SMS phone numbers/codes are never persisted.

---

## Known Limitations

- Depends on undocumented public web APIs whose schemas and anti-bot rules may change at any time.
- The recommendation baseline intentionally uses the public popular endpoint (the anonymous personalization endpoint is currently unstable).
- Live chat is polling rather than real-time WebSocket; badges, gifts, Super Chat, room moderation, and non-AVC/HLS fallbacks are deferred.
- Picture-in-picture, brightness/volume gestures, cache clearing, nested reply posting, and password login are not yet implemented.
- The experimental SMS login may stop working; the app never collects a password, auto-solves, or bypasses captcha.

---

## Legal Disclaimer

1. **Non-official**: This is an unofficial third-party client developed for learning purposes. It has **no affiliation, partnership, authorization, or sponsorship** with Bilibili or its affiliates.
2. **Use restriction**: This project is for **personal learning, technical research, and non-commercial personal use only**. Commercial use, large-scale distribution, monetization, or any violation of Bilibili's Terms of Service is strictly prohibited.
3. **APIs and data**: The app retrieves data through Bilibili's public web APIs (some undocumented). Availability, format, and access restrictions are solely determined by Bilibili and may break at any time due to anti-bot or business changes. Users assume all related risks.
4. **Data and privacy**: The app never stores or uploads your account information to any server other than your own device. All credentials are encrypted locally and cleared on logout. The developer cannot and does not intend to access any of your personal data.
5. **Intellectual property**: Bilibili and related trademarks, logos, videos, thumbnails, and danmaku are the property of their respective owners. This project bundles or redistributes no copyrighted resources; the app icon is an original vector approximation.
6. **Disclaimer of liability**: All consequences arising from the use of this software (including but not limited to account risk, regional restrictions, API failure, and data loss) are borne solely by the user. The developer is not liable for any direct or indirect damages.
7. **Takedown**: If a rights holder raises an objection, the developer will cooperate by taking down or disabling the relevant functionality.

---

## License

Released under the [MIT License](./LICENSE).
