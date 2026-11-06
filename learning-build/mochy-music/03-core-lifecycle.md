# 03 — Core Application Lifecycle

## 1. `App.kt` (Application)

`class App : Application(), SingletonImageLoader.Factory` annotated `@HiltAndroidApp`.

### `onCreate()` order (matters — see comments in code)

```kotlin
override fun onCreate() {
    TransferRestore.applyPending(this)   // before super: restores a crashed transfer
    super.onCreate()                      // Hilt builds DB + player caches here
    AppLanguage.initialize(this)          // per-app language (custom locale)
    CrashHandler.install(this)            // thread-uncaught-exception → CrashActivity
    CipherDeobfuscator.initialize(this)   // de-obfuscates obfuscated strings/keys
    Timber.plant(DebugTree())             // only in debug / fast
    PreferencesCache.start(...)           // in-memory cache of DataStore settings
    NotRecommended.init(this)             // flag for unsupported devices
    // ── background coroutines (applicationScope, Dispatchers.IO) ──
    musicDatabase.query { purgeTimestampArtists() }
    one-shot lyrics-cache wipe (LyricsCacheWipeGeneration) — clears fetched lyrics once on 7.1.0
    initializeSettings() + observeSettingsChanges()
    preWarmPoToken()                       // after intro + 2.5s: boots BotGuard WebView
    LocalArtwork.repair(...)               // after intro: restore local covers from song files
}
```

**Why the order matters (the code comments explain it):**
- `TransferRestore.applyPending` runs *before* `super.onCreate()` because that's where
  Hilt constructs the DB and the player caches — pending transfers must be applied first.
- PoToken pre-warm waits for a **session id** (`YouTube.visitorData`/`dataSyncId`), then
  waits for the **launch intro** (`LaunchGate.awaitIntro()`) + 2.5s so the WebView doesn't
  compete with the first frames. VISIONOS streams need no token, so it's best-effort.
- Local artwork repair waits for the intro so it doesn't share the disk with first frames.

### `onConfigurationChanged`
Refreshes app resources when the system locale changes (`AppLanguage.refreshApplicationResources`).

### `newImageLoader` (Coil 3)
Builds a custom `ImageLoader`:
- SVG decoder
- `crossfade(300)` (covers ease in)
- hardware acceleration on API 28+
- memory cache = 30% of app memory
- disk cache size from `MaxImageCacheSizeKey` (default 512 MB), `0` disables disk cache

### `forgetAccount(context)` (companion)
Full logout: clears DataStore keys (cookie, visitorData, dataSyncId, account fields),
nulls `YouTube.cookie/visitorData/dataSyncId`, and clears the WebView `CookieManager`
on the main thread.

## 2. Settings & DataStore

- Settings live in **DataStore Preferences** (`utils/DataStore.kt`):
  `val Context.dataStore by preferencesDataStore(name = "settings")`.
- `PreferenceKeys.kt` defines **hundreds** of typed keys (`booleanPreferencesKey`,
  `stringPreferencesKey`, …) covering theme, player, audio, queue, lyrics, import, etc.
- `App.initializeSettings()` reads them once at boot; `observeSettingsChanges()` launches
  flows that react to changes:
  - `OfflineMode` → `OfflineMode.set(it)`
  - `VisitorDataKey` → `YouTube.visitorData` (persist new visitor data)
  - `DataSyncIdKey` → `YouTube.dataSyncId`
  - `InnerTubeCookieKey` → `YouTube.cookie` (clears account on parse failure)
  - content country/language → `YouTube.locale` (`gl`/`hl`)
  - proxy config → `AppProxy.apply(config)` + rotates guest session if a new egress is set
  - `IpVersionKey` → `YouTube.ipVersion`
- `PreferencesCache` keeps an in-memory snapshot so the UI doesn't hit DataStore every frame.

## 3. `MainActivity.kt` (~1712 lines)

The single activity hosts the whole Compose UI.

### Setup
- `installSplashScreen()` (`Theme.App.Starting`)
- `setContent { MochyTheme { AppNavigation(...) } }`
- Binds `MusicService` via `bindService`, gets a `PlayerConnection`
- Handles deep links: YouTube URLs (`youtube.com`, `youtu.be`, `vnd.youtube`), share intents
  (`SEND` text/audio), `MEDIA_PLAY_FROM_SEARCH`, NFC — parses the URI and navigates
  (`album/`, `artist/`, `online_playlist/`, `search/`, or queues a `YouTubeQueue`).

### Composition locals (the app's "context" for the UI tree)
`LocalDatabase`, `LocalPlayerConnection`, `LocalGlassState`, `LocalAppContentObscured`,
`LocalHideAppChrome`, `LocalPlayerAwareWindowInsets`, `LocalDownloadUtil`, `LocalSyncUtils`,
`LocalCropAlbumArt`, `LocalGridItemSize`, `LocalSwipeToSong`, `LocalIsPlayerExpanded`,
`LocalArtworkColor`, `LocalDetailAccentColor`, `LocalPlayerAwareWindowInsets`…

These are how deep UI components reach services without threading params everywhere.

### Window behaviour
- `setSystemBarAppearance` (dark/light bars), cutout drawing (`drawUnderCutout`),
  player nav-bar behaviour (gesture vs buttons — `playerNavBarBehavior()`).
- `SubSettingsRoutes` set: routes that keep the top bar (Audio Lab, equalizer, settings…).
- `HomeUpdatePrompt`: shows an update snackbar on Home if a newer release exists
  (snooze window = 24h).

## 4. Crash handling

`CrashHandler.install(this)` installs a thread-uncaught-exception handler that routes to
`CrashActivity` (a separate `:crash` process, `excludeFromRecents=true`) so the user gets
a friendly crash screen instead of a system dialog.

## 5. `CipherDeobfuscator`

De-obfuscates obfuscated strings/keys at runtime (some YouTube signature/cipher constants
are stored obfuscated to slow down reverse-engineering). Initialized in `App.onCreate`.

## 6. `BotDetectionMitigator` & `AppProxy`

- `AppProxy.apply(config)`: per-app HTTP proxy (SOCKS/HTTP) applied to the InnerTube client.
  Changing the egress rotates the guest session (the old one may be what YouTube flagged).
- `BotDetectionMitigator.rotateGuestSession()`: rotates the guest `visitorData` when the
  proxy changes and no cookie is set.

## 7. Launch gate

`LaunchGate.awaitIntro()` lets the splash/intro animation finish before heavy background
work (PoToken boot, artwork repair) starts, so the first frames stay smooth.
