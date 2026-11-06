# 05 — Navigation & UI

The UI is **100% Jetpack Compose** (Material 3), dark-only, and the largest subsystem
(~141k LOC in `ui/`). It is built around a single `NavHost` with shared-element
transitions and a customisable player.

## 1. Top-level navigation

`Screens.kt` defines the four main tabs (`Screens.MainScreens`):

| Tab | Route | Icon |
|---|---|---|
| Home | `home` | home / home_filled |
| Search | `search_input` | search / search_filled |
| Library | `library` | lib / lib_fill |
| Settings | `settings` | nav_mochy_outline / nav_mochy_filled |

Plus `NavigationTab { HOME, SEARCH }`, `DarkMode { AUTO, ON, OFF }`,
`LyricsPosition { LEFT, CENTER, RIGHT }`, `OptionStats`.

## 2. `navigationBuilder` (`NavigationBuilder.kt`, ~900 LOC)

A custom `NavGraphBuilder` extension that registers every route with **motion-aware
transitions**. A local wrapper `composable(...)` wraps navigation's `composable` so every
page answers **Settings → Appearance → Animation** (off = swap in place). Key helpers:

- `coverSourceComposable` — a screen an album/playlist can open *from* (Library pages,
  Search, artist, Home section pages) hands its transition scope to its covers, so the
  cover grows out of the tapped card and the source sinks back — the same clock as Home.
- `libraryExitTo` / `coverReturnFrom` / `libraryReturnFrom` — the recede/return springs.
- `HomeRecedeSpring` (open spring going out) + `HomeReturnEase` (close ease coming back).

Routes include: `home`, `section_detail/{sectionId}`, `album/{browseId}`,
`artist/{artistId}` + `artist_items`, `local_playlist/`, `online_playlist/`,
`auto_playlist/`, `cache_playlist/`, `top_playlist/`, `search/{query}`, `genre/`,
`online_search_result`, `settings` + all sub-settings, `player`, `queue`, `lyrics`,
`studio`, `bedside`, etc.

## 3. Screens

| Screen | Role |
|---|---|
| `HomeScreen` (~1900 LOC) | the main feed — hero carousel, section cards, "Listen now"/"Hop in"/"Mochyy" styles |
| `BrowseScreen`, `YouTubeBrowseScreen` | YouTube Music browse |
| `ExploreScreen` | explore (charts, moods, genres) |
| `SearchScreen` + `search/suggestions` | search + suggestions |
| `Library*` screens | library home, albums, artists, imported, playlists |
| `AlbumScreen`, `ArtistScreen` + sub-screens | detail pages |
| `PlaylistScreen` variants | local/online/auto/cache/top playlists |
| `HistoryScreen`, `RecapScreen` | history + monthly recap |
| `ProfileScreen` | user profile + stats |
| `Settings` + sub-screens | behaviour, language, proxy, crossmix, experimental, devspace, storage, import/sync, integrations, modules |
| `AudioLab`, `Equalizer`, `AudioQuality` | audio settings |
| `Setup`/`onboarding` | first-run |
| `Update` | update prompt/screen |
| `Donate` | donation prompt |

## 4. The player UI (`ui/player/`)

The now-playing experience is a bottom sheet (`BottomSheetPlayer.kt`, ~1.6k LOC) with
**10 player styles** (`ui/player/styles/`):

`FLAGSHIP`, `CLASSIC`, `TURNTABLE`, `VIDEO`, `IPOD`, `POSTER`, `TIMELINE`, `LYRICS`,
`BEDSIDE`, `BEDSIDE_TILES`, and `MODULE` (a module's own player).

Each style directory (`flagship/`, `ipod/`, `bedside/`, `studio/`, `video/`, `customize/`,
`share/`, `models/`, `menu/`, `info/`) implements its layout.

Key player components:
- `PlayerControls`, `PlayerProgressSlider`, `PlayerVolumeBar`, `PlayerHandle`
- `MorphingCover`, `MorphingSongInfo`, `SongCarousel`, `SongInfoRow`
- `CanvasArtworkPlayer` + `CanvasArtworkUtils` (animated Apple-Music-style canvases)
- `CoverReflection`, `SeamBlur`, `BackdropBlurTransformation`
- `LyricsScreen`, `MiniLyricsTicker`, `LyricsGlowLine`, `LyricsStagger`
- `TurntableControls` (the turntable style)
- `VideoLetterbox`, `YouTubeVideoBackground`, `YouTubeVideoLookup`
- `LarpMode`, `LikeBurst`, `MixingNote`, `PlaybackClock`
- `StreamFailureUi`, `StreamFormatBadge`
- `PlayerUiState` — the UI state model

**Performance rule (from `CLAUDE.md`/comments):** per-frame work (playback position,
animations) goes in `drawBehind`/`graphicsLayer`, *never* in composition; loops pause
while hidden (`LocalAppContentObscured`, `rememberStyleShowing`) and use `damageBoundary()`.

## 5. Shared transitions & glass

- `SharedElementTransition.kt`, `ContainerTransform.kt`, `SharedTransitionLayout` —
  cover-to-detail shared elements.
- `GlassKit.kt`, `FrostedGlass.kt`, `ProgressiveBlur.kt`, `BlurEffectCache.kt`,
  `OnlineBlur.kt` — the glass/blur system (blur strength, progressive blur, element blur).
- `SmoothCornerShape.kt` — custom rounded corners.

## 6. Theme (`ui/theme/`)

- **Dark-only** (`ForceDarkTheme = true`): the light palette was never finished, so it's
  pinned to one switch rather than decided per call site.
- `MochyTheme(darkTheme, pureBlack, content)` builds a `ColorScheme`:
  - base dark scheme (black background, grey primaries)
  - **dynamic color from album art**: `CoverThemeColor.kt` extracts a palette from the
    playing cover via `materialkolor` (`PaletteStyle`, `Score.score`) and builds a
    dynamic `ColorScheme`. `LocalArtworkColor` exposes the accent to M3 surfaces.
  - `pureBlack` toggle → pure `#000` backgrounds.
- `Type.kt` — typography, including `LookFont` (a module's own font).
- `PlayerColorExtractor.kt`, `PlayerSliderColors.kt`, `ArtworkPalette.kt`.
- `ColorSaver` — a `Saver<Color, Int>` so colors survive process death.

## 7. Components (`ui/component/`)

A large library of reusable composables: `HomeCards`, `HeroCarousel`, `PlaylistCoverCarousel`,
`AlbumGradient`, `LikedCover`, `ArtistListCard`, `Items`, `GridMenu`, `SearchBar`,
`SongSwipeBox`, `SwipeActions`, `BigSeekBar`, `WavySlider`, `SquigglySlider`, `VolumeSlider`,
`OdometerText`, `Press3D`, `DeviceTilt`, `HeroCarousel`, `RevealOnAppear`, `PageWipe`,
`PlayingIndicator`, `ShimmerTheme` (shimmer loading), `Preference` (settings rows),
`SettingsGroup`, `IntegrationCard`, `UpdaterComponents`, `LyricsV2`, `MetroLyrics`,
`MochyLyrics`, etc.

## 8. Composition locals (the UI's service locator)

`MainActivity` provides `LocalDatabase`, `LocalPlayerConnection`, `LocalDownloadUtil`,
`LocalSyncUtils`, `LocalGlassState`, `LocalAppContentObscured`, `LocalHideAppChrome`,
`LocalPlayerAwareWindowInsets`, `LocalArtworkColor`, `LocalDetailAccentColor`,
`LocalCropAlbumArt`, `LocalGridItemSize`, `LocalSwipeToSong`, `LocalIsPlayerExpanded`,
`LocalNavAnimatedContentScope` — so deep components reach services without prop-drilling.

## 9. Widgets (`widget/`, `res/layout/widget_*.xml`)

4 home-screen widgets (compact square, compact wide, music player, turntable) with
`widget_colors.xml`. `AppWidgetProvider`-based; updated via the media session.

## 10. Localisation

- `i18n/` — `AppLanguage` (custom per-app language, persists across config changes),
  `LanguageResourcesTest` verifies every locale has the same keys.
- `res/values-*` — **60+ locales** (en, hi, zh-CN, ja, ko, ar, …).
- `crowdin.yml` — translation management via Crowdin.
- Icons: `scripts/download_material_icons.py`, `scripts/compose_svg_drawable.py`.
