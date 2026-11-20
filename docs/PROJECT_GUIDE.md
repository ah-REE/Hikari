# Hikari project guide

Build and test instructions, architecture, and data-handling details for [Hikari](../README.md).

## Build and test

Requirements: **JDK 21**, **Android SDK 36**, and an **Android 8.0 / API 26 or newer** device. Configure `ANDROID_HOME`, or set `sdk.dir` in the ignored `local.properties` file. Open the repository root in Android Studio.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

These commands build and install a debug APK. Gradle downloads dependencies from the configured repositories, including JitPack for NewPipeExtractor. The GitHub workflow is manual-only and runs source checks, builds, tests, and lint without uploading APKs.

Tests that contact live upstream services are opt-in:

```sh
HIKARI_LIVE_TEST=1 ./gradlew :app:testDebugUnitTest --rerun-tasks
```

Live tests require network access and depend on upstream service availability.

## Architecture

| Area | Entry points | Responsibility |
|---|---|---|
| Android host | `MainActivity.kt` | Shared links, system bars, PiP actions and aspect ratio |
| Navigation | `HikariApp.kt`, `HikariNavigation.kt` | Root pager, child pages, stable player host |
| Discovery | `HikariViewModel.kt`, `NativeSearch.kt`, `ChannelScreen.kt` | Queries, results history, pagination and channels |
| Player UI | `WatchScreen.kt`, `PlaybackTimeline.kt` | Controls, metadata, sheets, segment markers |
| Embedded playback | `EmbedPlayback.kt` | YouTube iframe, lifecycle and control bridge |
| Native playback | `WatchViewModel.kt`, `PlaybackService.kt`, `Extractor.kt` | Media3 session, extracted streams, captions and audio |
| Collections | `Library.kt`, `VideoRepository.kt`, `PlayerPreferences.kt` | Local data, codecs, preferences |
| Downloads | `Downloads.kt`, `ParallelDownloader.kt`, `DownloadRanges.kt` | Persistent queue, cache and checked parallel transfers |
| Shared UI | `HikariDesign.kt`, `HikariTheme.kt`, `VideoLayouts.kt` | Theme, typography, components, images and state visuals |

Kotlin entry points are under `app/src/main/java/com/hikari/app/`. The native playback pipeline uses NewPipeExtractor to access YouTube/InnerTube data.

## Feature details

- **Home:** search-first. The optional time-fit finder returns up to three videos within a selected **5, 10, 15, or 30 minute per-video limit**. It is off by default. Repeated submissions vary selections where enough results are available. Recent queries are in Search, saved videos in Library, and creator uploads in Following.
- **Channels:** videos, Shorts, live uploads, playlists, podcasts, and channel information where available upstream. There is no dedicated Shorts navigation tab.
- **Collections:** resume watching, Watch later, playlists, local likes, and watch history. Likes and follows do not act on a YouTube account. Comments are read-only.
- **Playback:** embedded YouTube playback first, native extraction fallback, mini-player, fullscreen, PiP, playback speed, configurable seeking, and quality/audio selection where available. Embedded quality remains in YouTube's own controls; the native quality selector is not shown for embeds.
- **Background audio:** off by default. Enable **Settings → Background playback** to use native playback for newly opened videos, including Shorts, and continue audio with the screen off or in other apps using media-notification/lock-screen controls. Enabling it takes priority over the player preference and disables automatic PiP; explicit PiP remains available. With it off, native playback pauses when the activity is no longer visible, but visible PiP keeps playing. Hidden embeds pause regardless of this setting. Changing the engine applies to the next video; disabling background audio immediately prevents hidden native playback.
- **Captions:** extracted tracks can be attached to native playback; selection is retained during quality/audio changes for the current video. Availability depends on upstream tracks/endpoints. The embedded player also exposes YouTube's caption menu; its tracks can differ from extracted ones.
- **Geometry:** video dimensions drive layout and PiP geometry. Android limits ordinary PiP to supported aspect ratios; unusually wide/tall media remains fitted within those limits.
- **Downloads:** a persistent queue, quality/audio-language selection, pause/resume/removal, and app-private Media3 caching. Range-capable files use six workers with validated byte ranges; unsupported transfers use Media3's regular downloader.
- **Optional services:** SponsorBlock can mark and skip sponsor segments. Community data may be absent, unavailable, or inaccurate. Dislike estimates come from an independent service, not official YouTube counts. Displayed like counts use upstream metrics when available.
- **Appearance:** light/dark palettes, animated transitions, loading placeholders, large-text support, and reduced-motion settings.

Account-only, age/region-restricted, or otherwise blocked videos may fail. Extraction, captions, and embedding can change independently. Download URLs can expire and may need resolving again.

## Data and network

History, collections, recent searches, and preferences are stored in app-private preferences. Downloads use an app-private Media3 cache. Android automatic backup is disabled. User-selected JSON backups include library data, viewing history, and settings.

Hikari contacts YouTube/Google video and image hosts for discovery, embeds, streaming, thumbnails, and captions. Embedded playback uses WebView networking and storage, including cookies. Hikari has no account backend or app analytics SDK.

When enabled, SponsorBlock receives the video ID through requests to `sponsor.ajay.app`; dislike estimates similarly contact `returnyoutubedislikeapi.com`. These recipients also see the network address. Choosing native-preferred playback can still use embeds for Shorts when background playback is off. Metadata and optional services can still make network requests while a downloaded video is playing.

Clear watch history or collections in Settings; manage downloaded files in Downloads. Clearing the library does not delete downloads or settings. Clearing Android app storage or uninstalling removes private app data; export a backup first if needed.

## Backup and migration

Settings supports bounded JSON import/export with library merging. To move from the old web/Capacitor application, export its JSON and import it through the native app's Settings. Native code does not automatically read the old WebView's IndexedDB, and legacy offline files are not migrated. Keep the original export and installation until the imported data has been checked.

## Contributing

Run relevant tests and lint when changing code, and preserve existing data formats. Bug reports should include reproduction steps, Android/WebView versions, and logs with personal data and credentials removed.

Run the repository checks with Python 3.10+:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check_publication.py --history
```

The checks flag generated artifacts and common credential patterns in local files and Git history. Use `python3 scripts/check_publication.py --staged` to check the staged snapshot.

## License

Hikari is licensed under [GPL-3.0-or-later](../LICENSE.md). Dependency licenses and attribution are listed in the [third-party notices](../THIRD_PARTY_NOTICES.md).
