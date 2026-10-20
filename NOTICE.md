# Attribution and porting notice

This Android implementation was created as a platform translation of the interaction model and feature structure from [yichengchen/ATV-Bilibili-demo](https://github.com/yichengchen/ATV-Bilibili-demo), a tvOS client distributed under GNU GPL v2.

- Upstream project: `ATV-Bilibili-demo`
- Upstream copyright: its respective authors and contributors
- Android port changes: native Android canvas UI, D-pad focus navigation, Android WebView playback bridge, Android QR login, Android manifest and build configuration
- Port date: 2026-10-07

This derivative project is distributed under GNU GPL v2. See `LICENSE.md`.

## Third-party dependencies

- `com.google.zxing:core` — Apache License 2.0（二维码生成）。
- `androidx.media3:media3-exoplayer` / `media3-exoplayer-hls` / `media3-datasource` — Apache License 2.0（播放内核，自 v1.6.0 起）。
- AndroidX annotation/core 等传递依赖 — Apache License 2.0。

本项目整体仍按 GNU GPL v2 发布；上述依赖遵循各自许可证。
