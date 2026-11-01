# MediaGrab for Android

Prototype. Share a link from any app to MediaGrab and the download starts straight away,
with no window to confirm. Files go to `Download/Media/<source>`.

- yt-dlp and ffmpeg come from `youtubedl-android`; yt-dlp updates itself once a day.
- Instagram `/p/` and X posts use gallery-dl, so posts keep all their photos and videos. Bundled by
  `tools/build-gallerydl-zip.sh`.
- Profiles and playlists are refused. Instagram posts need a login inside the app.

Build: `./gradlew :app:assembleDebug`. Add `-Pabi=x86_64` for an emulator.
