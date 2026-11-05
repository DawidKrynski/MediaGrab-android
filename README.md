# MediaGrab for Android

Share a link to MediaGrab and the download starts straight away, with no window to confirm.
Files go to `Download/Media/<site>`.

- YouTube, TikTok, Instagram reels and other sites supported by yt-dlp.
- Instagram posts and carousels keep every photo and video (sign in to Instagram inside the app).
- X posts keep all their photos, videos and GIFs.
- Profiles and playlists are refused.

## How it works
yt-dlp and FFmpeg come from [youtubedl-android](https://github.com/JunkFood02/youtubedl-android);
yt-dlp updates itself once a day. Instagram posts and X posts use [gallery-dl](https://github.com/mikf/gallery-dl),
kept as sources in `third_party/python` (refresh with `tools/update-gallery-dl.sh`) and zipped at build time. The Instagram session is kept in the app's private storage
and used only for Instagram links.

## Build
`./gradlew :app:assembleRelease` (arm64-v8a by default; `-Pabi=x86_64` for an emulator).
Release signing reads a properties file outside the repository; without it the release APK is unsigned.

## License
GPL-3.0-only. See [LICENSE](LICENSE).
