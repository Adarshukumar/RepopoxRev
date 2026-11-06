# 01 — Architecture

## 1. Module map

The project is a **multi-module Gradle build** (`settings.gradle.kts`). Three groups:

```
mochy/                              ← rootProject.name = "mochy"
├── app/                            ← the application (com.mochyy.mochymusic)
├── providers/                      ← self-contained "content backends"
│   ├── innertube/     YouTube Music (the big one, ~8.3k LOC)
│   ├── kugou/         KuGou music
│   ├── lrclib/        LRCLIB lyrics
│   ├── lastfm/        Last.fm + Libre.fm scrobble/metadata
│   ├── betterlyrics/  Apple Music-style synced lyrics (TTML)
│   ├── simpmusic/     SimpMusic lyrics
│   ├── youlyplus/     YouLyPlus lyrics
│   ├── canvas/        Apple Music / Monochrome animated canvases
│   ├── applecanvas/   Apple Music artist backgrounds
│   ├── artistvideo/   artist video canvases
│   ├── shazamkit/     Shazam music-recognition signatures
│   ├── paxsenixlyrics/ paxsenix lyrics
│   ├── directlyrics/  Deezer/NetEase/QQ Music lyrics
│   ├── musixmatch/    MusixMatch lyrics
│   ├── genius/        Genius lyrics
│   └── wikipedia/     artist Wikipedia info
└── modules/                        ← the plugin/module system
    ├── api/          the pure-Kotlin contract modules implement (MODULE_API_VERSION=3)
    ├── bridge/       cross-classloader bridge (ModuleSide, Wire, shared)
    ├── applemusic/   built-in Apple Music source module
    ├── monochrome/   built-in Monochrome (FLAC) source module
    └── jiosaavn/     built-in JioSaavn source module
```

Each `providers:*` and `modules:*` is an **Android library** (`com.android.library`)
that `:app` depends on via `implementation(project(":providers:innertube"))` etc.

**Key design idea:** providers are plain Kotlin libraries with no UI. They expose a
stable object (e.g. `com.music.innertube.YouTube`, `com.music.kugou.KuGou`) that the
app calls. This keeps the network/protocol logic isolated and unit-testable, and lets
the app swap behaviour without touching UI code.

## 2. Layering

```
┌─────────────────────────────────────────────────────────────┐
│  UI layer  (ui/, ui/screens, ui/player, ui/component, theme) │  Compose screens, VMs
├─────────────────────────────────────────────────────────────┤
│  ViewModels (viewmodels/)                                    │  state + flows
├─────────────────────────────────────────────────────────────┤
│  Domain / Playback (playback/, playback/audio, playback/queues)│  ExoPlayer, audio pass
├─────────────────────────────────────────────────────────────┤
│  Data (db/, importer/, lyrics/, scrobble/, discord/)         │  Room, importers, providers
├─────────────────────────────────────────────────────────────┤
│  Core (core/, core/updater) + utils + constants + i18n       │  app lifecycle, prefs
├─────────────────────────────────────────────────────────────┤
│  Providers (providers/*) + Modules (modules/*)               │  external services
└─────────────────────────────────────────────────────────────┘
```

- **Dependency injection:** Hilt (`@HiltAndroidApp`, `@Module`, `@Inject`).
  `di/` holds `AppModule`, `NetworkModule`, `Qualifiers`, and several `*EntryPoint`
  interfaces (`AudioLabEntryPoint`, `DiscordEntryPoint`, `LyricsHelperEntryPoint`,
  `ScrobblerEntryPoint`, `PlaylistResyncEntryPoint`) — entry points let the app reach
  singletons defined in library modules without circular deps.
- **State:** almost everything is exposed as Kotlin `Flow` / `StateFlow` collected by
  Compose `collectAsState`. Settings live in **DataStore Preferences** (`utils/DataStore.kt`),
  not SharedPreferences.
- **No XML views in the main UI** — Compose only. XML layouts exist only for home-screen
  widgets (`res/layout/widget_*.xml`).

## 3. The singletons that glue it together

| Singleton | Where | Role |
|---|---|---|
| `App` (Application) | `App.kt` | boots DI, DB, settings, PoToken, caches, image loader |
| `MusicService` | `playback/MusicService.kt` (~5k LOC) | the foreground MediaSession + ExoPlayer host |
| `PlayerConnection` | `playback/PlayerConnection.kt` | the app-side handle to the service; exposes `player`, `isPlaying`, `playQueue`… |
| `MusicDatabase` | `db/MusicDatabase.kt` | Room DB (single instance) |
| `ModuleRegistry` | `modules/ModuleRegistry.kt` | loads/enables all modules |
| `SourceModules` | `modules/SourceModules.kt` | walks provider order to resolve a stream |
| `LyricsProviderRegistry` | `lyrics/LyricsProviderRegistry.kt` | ordered lyrics sources |
| `YouTube` (object) | `providers/innertube/.../YouTube.kt` | the YouTube Music API facade |

## 4. How a song actually plays (data flow)

1. User taps a song in a screen → the screen calls `playerConnection.playQueue(queue)`.
2. `PlayerConnection` builds `MediaItem`s and calls `service.player.setMediaItems(...)`.
3. `MusicService` (a `MediaLibraryService`) owns the `ExoPlayer`, the `MediaSession`,
   the download service, and the audio-pass pipeline.
4. For a YouTube song, the **stream URL** is resolved lazily: `SourceModules` walks the
   provider order (modules → YouTube → …) and calls `YouTube.player(...)` (InnerTube)
   to get a `PlayerResponse`, then extracts the streaming URL. This is wrapped in a
   `DynamicResolvingMediaSource` so the URL can refresh (PoToken, expiry) at playback time.
5. ExoPlayer fetches the stream through `OkHttpDataSource` (proxy-aware), decodes it,
   and sends PCM through the **audio pass** pipeline → `AudioSink`.
6. `PlayerConnection` observes playback state and updates the UI (mini-player, queue,
   scrobble timer, Discord "now playing").

## 5. Build variants → many app identities

Because of flavor × build-type × ABI, one codebase produces many APKs (see `02-build-system.md`).
The app reads `BuildConfig.CAST_AVAILABLE`, `BuildConfig.ARCHITECTURE`, `BuildConfig.IS_NIGHTLY`,
`BuildConfig.BUILD_TIME_MILLIS`, etc., to adapt behaviour per build.

## 6. Threading model

- **Main thread:** Compose composition, `Dispatchers.Main` UI updates.
- **IO:** network, DB, file ops via `Dispatchers.IO` in `applicationScope`.
- **Player:** ExoPlayer runs its own threads; `PlayerConnection` uses a `SupervisorJob`
  scope and `flow.stateIn` to mirror state into Compose.
- **Audio pass:** native C++ (`app/src/main/cpp`) + Media3 `AudioProcessor`s run on the
  audio render thread; per-frame work is done in `drawBehind`/`graphicsLayer` (not composition)
  to keep frames cheap.

## 7. Privacy & security model (from README + code)

- No account required to search/play/download/lyrics.
- Sign-in opens the **real** service page (accounts.google.com, open.spotify.com, …) in an
  in-app browser; only the returned **session cookie/token** is stored (DataStore), never
  the password. `App.forgetAccount()` clears DataStore keys + `YouTube.*` + WebView cookies.
- FOSS/F-Droid builds: no Google Play Services, no Cast, no proprietary bits.
- Modules that run code must be **signed with Mochy's own key** and built for the same
  `MODULE_API_VERSION` — a trust boundary.
- Network: a custom `network_security_config.xml` + optional per-app proxy (`AppProxy`).
