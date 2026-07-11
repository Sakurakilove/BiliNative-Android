# Bili Mobile Android MVP

Native Android client built with Kotlin, Jetpack Compose Material 3, OkHttp, Media3, Coil, and ZXing.

## Run

1. Open this directory in Android Studio (JDK 17).
2. Let Android Studio sync the project and install Android SDK 35 if prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

Android Studio 可直接同步并运行工程。GitHub Actions 使用固定的 Gradle 8.9 执行 `lintDebug assembleDebug`，成功后可在 workflow artifacts 下载 Debug APK。

## Behavior and caveats

- Search obtains public runtime WBI image keys from the navigation endpoint and signs each request locally. No app credentials or secrets are embedded.
- QR login uses Bilibili's web QR flow. Response cookies are encrypted at rest and sent only when their OkHttp domain/path rules match. Signing out clears them locally.
- Playback requests the progressive HTML5 `durl` format. Some videos require authentication, are region restricted, or only expose DASH/DRM streams and therefore cannot play in this MVP.
- The app depends on undocumented public Bilibili web APIs. Endpoint schemas and anti-bot requirements may change without notice.
- Cleartext traffic is disabled. All configured API, image, QR, and media entry-point URLs use HTTPS.
- This is an unofficial client and is not affiliated with Bilibili.
