# Pullr

Android app (Kotlin + Jetpack Compose) that saves videos, photos, GIFs and audio from a link.
Paste a link from **YouTube, Instagram, Facebook, X (Twitter), TikTok, Reddit, Vimeo** and 1000+ other sites,
pick a quality, and it lands in your gallery. Works on any phone, no account needed.

> Pullr used to be "X Saver". It keeps the same app id and signing key, so installing the new APK
> over the old one is an update, not a second app.

## What it does

- **Paste a link, or Share to Pullr** from any app. Two share targets:
  - **Pullr · Quick save**: no screen, you stay in the other app. It looks the link up and downloads in the background.
  - **Pullr**: opens the app so you can pick the quality first.
- **Real qualities and real sizes**: every saved link lists the qualities that exist (for example
  `1080p · 52 MB`, `720p · 33 MB`) before you download. "Best" means the highest quality up to 1080p, so you don't
  accidentally pull a 4K file.
- **Audio only (MP3)** for any video that has sound.
- **Multi-item posts**: Instagram carousels and X posts with several videos or photos show every item.
- **Saved links**, **downloads history** with live progress, Open, Cancel and Retry, and a **notification panel**
  (copy a link, tap *Paste & save*) that works without opening the app.
- **Fast and resumable X downloads**: 3 files at once, each split over 4 to 8 parallel connections, resuming from the
  exact byte after a network drop.
- Files go to `Movies/Pullr`, `Music/Pullr` and `Pictures/Pullr` through MediaStore, so no storage permission is needed.

Requires Android 10 or newer.

## How it works

| Link | How it is read | How it is downloaded |
|------|----------------|----------------------|
| X / Twitter | public endpoints: `api.fxtwitter.com`, then `api.vxtwitter.com`, then X's embed endpoint | built-in parallel downloader |
| Everything else | on-device **yt-dlp** (via [youtubedl-android](https://github.com/yausername/youtubedl-android)) | yt-dlp, with **FFmpeg** merging video and audio into one MP4 |

The engine (Python + yt-dlp + FFmpeg) is bundled inside the app, so there is no server. Websites change their pages
often, so the app refreshes yt-dlp once a day, and there is an **Update engine** button if a site stops working.

Only public videos can be saved. Private, age-restricted or login-only posts fail with a clear message.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `Pullr.apk`.
2. Open the file and allow "install unknown apps" for your browser if Android asks.
3. Updates install over the old version (same signing key, higher version code on every build).

The APK is large (roughly 80 to 100 MB) because it carries Python and FFmpeg for two CPU types (arm64 and 32-bit arm).

## Build

Every push to `main` runs `.github/workflows/build.yml`, which builds a signed release APK and publishes it as a
GitHub release. Locally: open the folder in Android Studio, or run `./gradlew assembleRelease`.

The signing key (`pullr.keystore`) is committed on purpose so builds are updatable. It is meant for sideloading a
personal app. Use a private key of your own before publishing anywhere else.

## Privacy

No accounts, no analytics, no ads, no server of its own. Saved links and the downloads history live only in the app's
private storage. Network traffic is only: the site you paste a link from (and its CDN), the public lookup services for
X links (they see the post link and your IP), and GitHub (to fetch yt-dlp updates).

## Responsible use

For personal use only. Download only content you own, that is licensed for it, or that you otherwise have the right to
keep, and respect creators' rights and each site's terms of service.
