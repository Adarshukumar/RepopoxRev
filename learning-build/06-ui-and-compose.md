# 06 — UI and Compose

The UI is 100% Jetpack Compose — no fragments, no XML layouts (the 763 XML files are
resources: values, drawables, mipmaps, widget layouts, manifests). Material 3 `1.5.0-alpha18`
with the `adaptive` suites `1.3.0-alpha09` for large screens.

`ui/component/` alone holds **85 files**; `ui/player/` holds 41 more plus 11 subpackages.

## Navigation

`ui/screens/NavigationBuilder.kt` (900 lines) declares **69 routes** through three helpers:

| helper | count | note |
|---|---|---|
| `composable(…)` | 50 | navigation-compose's own |
| `coverSourceComposable(…)` | 19 | app wrapper — shares a cover-art transition source |
| `navigationComposable(…)` | 1 | app wrapper for nested graphs |

A private `NavGraphBuilder.composable(…)` overload funnels every page through one place:

> Every page of this graph goes through here rather than straight to navigation's
> `composable()`, so …

That is the right instinct — one choke point for shared enter/exit transitions and arguments.

`Screens.kt` is a `sealed class` for the four bottom-bar tabs (Home, Search, Library,
Settings), each carrying `titleId`, `iconIdInactive`, `iconIdActive` and `route`. Deep links
are handled in `MainActivity.handleDeepLinkIntent()` against the manifest's YouTube/`youtu.be`/
`vnd.youtube` intent filters, plus `SEND` for shared text and audio files.

### The route table is the crash surface

Navigation-Compose resolves routes **at runtime**. `navigate("typo")` compiles fine and throws
`IllegalArgumentException` the instant the user taps. With 206 navigate() call sites against
69 declared routes, this is the single most likely source of a production crash that no
compiler will catch.

The harness checks all 206 call sites. **204 resolve. Two do not:**

```
navigate("mood_and_genres")   HomeScreen.kt:1679
navigate("new_release")       ExploreScreen.kt:322
```

Neither string is registered by any of the three helpers, nor declared as a `route =` constant
anywhere in the app. Both are user-reachable: "Mood and Genres" from a Home shelf, "New release
albums" from the Explore screen. Tapping either throws.

Both destinations have their data layer already built — `MoodAndGenresViewModel` (used by
`SearchScreen`), `NewReleaseViewModel`, `YouTube.moodAndGenres()` and
`YouTube.newReleaseAlbums()` in InnerTube, and translated strings `R.string.mood_and_genres` /
`R.string.new_release_albums` in all 83 locales. So these look like screens that were wired up
and then lost, or never finished — the plumbing is complete and only the route registration is
missing.

`"history"`, by contrast, is fine: it is registered via `coverSourceComposable("history")`. A
checker that only looked for `composable(` would have reported it as missing — which is exactly
the false positive the first version of the harness produced.

Also verified: `Screens.Home.route` and friends are used as identifiers, and
`AppNavigation.kt` carries a parent-route map (`"developer_space" to "settings"`) used for
back-stack and highlight behaviour.

## Player styles — eleven of them

`PlayerStyle` in `constants/PreferenceKeys.kt`:

```kotlin
FLAGSHIP        // frosted cover fade, moving cover colours, the current design
CLASSIC         // the flagship before its layout refresh, kept as a variant
TURNTABLE       // a record player whose tonearm IS the seek bar
VIDEO           // the song's YouTube video full-screen, rotated, lyrics in a pane
IPOD            // an iPod Classic, click wheel and all, browsing the library inside the player
POSTER
TIMELINE
LYRICS          // "Lyrics stage"
BEDSIDE         // a big quiet clock for the nightstand, stays up over the lock screen
BEDSIDE_TILES   // two tiles side by side, each scrolled independently
MODULE          // a look module's own player, drawn from its layout
```

With derived behaviour rather than scattered `if`s:

```kotlin
val editable: Boolean get() = this == FLAGSHIP || this == CLASSIC || this == TURNTABLE ||
                              this == POSTER || this == IPOD || isBedside
val isBedside: Boolean get() = this == BEDSIDE || this == BEDSIDE_TILES
val startsSideways: Boolean get() = this == BEDSIDE_TILES   // "the way a phone stands on a nightstand"
```

`MODULE` is kept last in the enum *"so the others keep their places"* — the enum's ordinal is
persisted, so appending is a compatibility decision, not an arbitrary one. And it is only
offered while a look module is enabled.

Implementation is split by style: `ui/player/flagship/`, `ipod/`, `bedside/`, `video/`,
`studio/`, plus `styles/` (`AlternatePlayerStyles.kt`, `StyleKit.kt`, `ExpressiveText.kt` at
1,212 lines, `ModulePlayerStyle.kt`, `TurnedSheets.kt`). `ui/player/customize/` backs the
"customize it yourself" answer in the FAQ.

## The heavy visual components

These are the files where the effort went, and they are large for a reason — each is a
bespoke animation system rather than an off-the-shelf widget:

| file | lines | what it is |
|---|---|---|
| `LyricsGlowLine.kt` | 2,384 | per-line lyric rendering with glow, word timing |
| `MorphingCover.kt` | 2,055 | the cover art morphing between player states |
| `HeroCarousel.kt` | 2,014 | shared-element hero transitions across a carousel |
| `BottomSheetPlayer.kt` | 2,078 | the player sheet itself |
| `ui/component/BottomSheet.kt` | 1,194 | a hand-written bottom sheet, not Material's |
| `Items.kt` | 1,534 | song/album/artist row and card primitives |
| `PopupSheet.kt` | 770 | the app's sheet system (documented in the absent `docs/popup-sheet.skill.md`) |
| `MorphingSongInfo.kt`, `LikeBurst.kt`, `CoverReflection.kt`, `SeamBlur.kt` | — | individual effects |

## Blur, and its cost

**92 files** in `ui/` reference blur or haze. `BackdropBlurTransformation`, `SeamBlur`,
`blurQuality` (LOW/HIGH) and `blurStrengthPx` (10–50) are user settings, and the README's FAQ
is blunt about the performance trade:

> **Q** — App too laggy, how to fix?
> **A** — Disabling blur will helps

The roadmap records *"bottom bar should not using haze for blur, theres too much artifact"* as
done. `enableProgressiveBlur` and `elementBlur` are separately toggleable. All four are in the
`LookSetting` allowlist, so a theme module can restate them — but nothing else.

`MainActivity.followLookSettings()` and `highRefreshModeId()` handle display refresh voting
(`ui/utils/HighRefreshVote.kt`), and `PlayerSystemBarsSync(state: BottomSheetState)` keeps
system bar appearance in step with the sheet — a detail that is easy to get visibly wrong.

## Theming

`ui/theme/` does something more interesting than a static palette:

- `ArtworkPalette.kt` + `PlayerColorExtractor.kt` — extract colours from the playing cover
  (AndroidX `palette-ktx`) and drive the whole theme from them.
- `CoverThemeColor.kt` — the `dynamicTheme` toggle.
- `Font.kt`, `Type.kt` — custom fonts; `res/font/` ships `google_sans_flex.ttf` (4.4 MB) and
  `sans_flex.ttf` (3.8 MB), plus 12 TTFs total. Those two files are 8.2 MB of the 39 MB app
  module, which is a real APK-size consideration.
- `materialKolor` 4.1.1 generates the tonal palette from a single seed colour, which is what
  makes cover-derived theming tractable.
- `bbh_bartle.kt` — a novelty theme.

Because themes derive from artwork, `ui/player/styles/StyleKit.kt` and `PlayerSliderColors.kt`
centralise colour decisions so eleven player styles do not each reinvent them.

## Launcher icons — a 50-way contract

The manifest declares **50 `activity-alias` entries**, exactly one `enabled="true"`
(`.IconDefault`). `utils/AppIcons.kt` generates the same set at runtime:

```kotlin
enum class AppIconFamily  { Mark, Outline, Anime }                    // 3
enum class AppIconColor   { Red, Orange, Yellow, Green, Teal, Sky,
                            Blue, Indigo, Purple, Pink, Graphite, Cream }  // 12
val presets = listOf(Default, MarkWhite, MarkBlack, … )               // 14
private val customs = AppIconFamily.entries.flatMap { family ->
    AppIconColor.entries.map { color ->
        AppIcon(name = family.name + color.name,
                alias = "Icon${family.name}${color.name}", …)
    }
}
```

14 presets + (3 × 12) customs = **50**, and the harness confirms the manifest's 50 aliases are
*exactly* that set — no alias the code cannot produce, no code entry the manifest lacks.

That matters because switching icons calls `PackageManager.setComponentEnabledSetting()` on the
alias by name; an alias that is not in the manifest throws `IllegalArgumentException`. It is
the same class of runtime-only contract as the navigation graph, and it is equally worth
checking mechanically.

Two nice details: `AppIconColor.pale` selects darker outline art so the icon stays legible on
light backgrounds (`artOn(color)`), and `AppIcons.launchedFrom(intent)` reads the alias back
out of the launching `Intent` so the app knows which icon the user tapped.

`res/mipmap-*` supplies five densities plus `anydpi-v26` adaptive icons; the harness verifies
every adaptive-icon `<foreground>`/`<background>` layer resolves.

## Widgets and other surfaces

Two Glance-free AppWidget receivers with their own action vocabularies: `MusicWidgetReceiver`
(`PLAY_PAUSE`, `LIKE`, `UPDATE_WIDGET`) and `TurntableWidgetReceiver`. `MusicService` carries
`WidgetProgressIntervalMs = 1_000L` — *"how often a placed home screen widget's progress is
redrawn while playing"* — which is a battery trade made explicit in a constant.

Also: Android TV / leanback support (`LEANBACK_LAUNCHER` category, `tv_banner`,
`automotive_app_desc.xml` for Android Auto), a `DensityScaler` content provider for DPI
override, `UCropActivity` for profile-image cropping, and `CrashActivity` — which is declared
but does not exist (see [07](07-verification-report.md)).
