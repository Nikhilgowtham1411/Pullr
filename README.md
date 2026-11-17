# Pullr

Pullr is an Android media downloader built with Kotlin and Jetpack Compose. The bundled source includes the on-device yt-dlp and FFmpeg engine alongside the existing X/Twitter download flow.

## Build the APK

The GitHub Actions workflow **Build Pullr APK** reconstructs the Android project from the files in `source-parts/`, then runs:

```bash
./gradlew assembleRelease
```

Open the Actions tab, select the latest successful **Build Pullr APK** run, and download the **Pullr-apk** artifact.

## Project source

The uploaded Android project is stored as binary archive pieces in `source-parts/`. The workflow concatenates those pieces and extracts the original source tree before building.

Requires Android 10 (API 29) or newer. Only download media you own or have permission to save, and comply with each site's terms.
