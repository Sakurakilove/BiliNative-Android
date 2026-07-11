# Bili Mobile Android Phase 2

Native Android client built with Kotlin, Jetpack Compose Material 3, OkHttp, Media3, Coil, and ZXing.

## Run

1. Open this directory in Android Studio (JDK 17).
2. Let Android Studio sync the project and install Android SDK 35 if prompted.
3. Run the `app` configuration on an API 26+ device or emulator.

Android Studio 可直接同步并运行工程。GitHub Actions 使用固定的 Gradle 8.9 执行 `lintDebug assembleDebug`，成功后可在 workflow artifacts 下载 Debug APK。

## Behavior and caveats

- 默认界面为简体中文，支持自适应双列首页、深色模式、圆角卡片和边到边布局。
- 详情页包含分集播放、完整统计与简介、UP 主信息、主评论分页和相关推荐。
- 登录后独立加载观看历史、稍后再看和收藏夹预览；任一接口失败不会影响其他分区。
- Coil 使用独立且不携带登录 Cookie 的图片客户端、内存/磁盘缓存、尺寸约束和淡入效果。
- 播放器支持原生 Media3 控件、0.75x 至 2x 倍速，以及按账号权限重新请求 360P/480P/720P 渐进式地址。

- Search obtains public runtime WBI image keys from the navigation endpoint and signs each request locally. No app credentials or secrets are embedded.
- QR login uses Bilibili's web QR flow. Response cookies are encrypted at rest and sent only when their OkHttp domain/path rules match. Signing out clears them locally.
- Playback requests the progressive HTML5 `durl` format. Some videos require authentication, are region restricted, or only expose DASH/DRM streams and therefore cannot play. Fullscreen orientation locking and DASH quality switching are not implemented yet.
- 评论为只读主评论；回复展开、互动写操作、历史/收藏的完整列表页和服务端退出尚未实现。退出始终会清除本地加密 Cookie。
- The app depends on undocumented public Bilibili web APIs. Endpoint schemas and anti-bot requirements may change without notice.
- Cleartext traffic is disabled. All configured API, image, QR, and media entry-point URLs use HTTPS.
- This is an unofficial client and is not affiliated with Bilibili.
