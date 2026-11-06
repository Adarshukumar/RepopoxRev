# 07 — The Module System

Mochy's killer feature: **extensibility without an app update**. A *module* adds songs,
lyrics, or a new look — and one switch turns it on/off. This is documented exhaustively in
`modules/README.md`; here is the engineering summary.

## 1. The contract (`modules/api/`)

`MochyModule.kt` is **plain Kotlin — no Android, no Media3, no HTTP library** — so a
module can be built as its own APK and loaded at runtime. It has `MODULE_API_VERSION = 3`.

Core types:
- `ModuleInfo(id, name, version, description, apiVersion, sourceUrl, author)`
- `ModuleFeature { AudioSource, Lyrics, Look }` — what a module brings
- `ModuleHost` — what Mochy gives a module: `storage` (key-value), `http(request)`
  (blocking HTTP through Mochy's client, so proxy/DNS apply), `log`, `folder` (files)
- `TrackQuery` — the song Mochy wants played
- `SourceStream` — a playable stream (url, format Progressive/Hls/Dash, headers, DRM,
  codec, sampleRateHz, matched track)
- `ResolveResult { Found, NotFound, Unavailable }`
- `SourceModule` (resolve/diagnose), `LyricsModule` (lyrics), `LookModule` (look map)
- `StreamDrm.Widevine(license, initData)` — Widevine CDM
- `SignInSpec` / `AccountState` — how a module's account signs in (Mochy opens the real
  page in-app and passes headers to `onSignInHeader`)
- `LookSetting` — the **only** settings a look may change (playerStyle, homeStyle,
  playlistStyle, thumbnailCornerRadius, lyricsMotionStyle, blur*, pureBlack, accentColor,
  dynamicTheme, playerBackground, flagshipBackground, ipodTheme, ipodWheel)

**Versioning rule:** bump `MODULE_API_VERSION` when you rename/remove/change-type anything
a module calls, or add a method to an interface (even with a default body). Adding an
optional field with a default does *not* bump it.

## 2. The four kinds of module

| Kind | What it adds | Runs code? | Trust |
|---|---|---|---|
| **Look** (zip or link) | restyle Mochy (settings, own player, font, pictures, pages) | **No** — it's a description Mochy draws | Anyone |
| **Web module** (link) | songs/lyrics from an HTTP service | **No** — only JSON/stream links | Anyone |
| **Zip with `url`** | a web module as one file | **No** | Anyone |
| **Script** (.js) | songs via `searchTracks`+`getTrackStreamUrl` (JS Modules) | Sandboxed JS, no phone access | Anyone |
| **Zip with its own code** | code runs inside Mochy | **Yes** | **Mochy's key only** |
| **Built-in module** | code shipped in the app | Yes | Mochy |
| **Module app** | an APK that runs inside Mochy | Yes | **Mochy's key + same API version** |

**The safety line:** a look can *never* reach accounts, network, or playback — only the
whitelisted `LookSetting`s. Web modules/links/zips only hand back JSON/streams/look values,
so nobody has to be trusted with the app. Code that runs inside Mochy must be signed with
Mochy's key (debug zip on release Mochy is rejected).

## 3. How Mochy loads them (`ModuleRegistry.kt`)

`ModuleRegistry.load()` assembles every module:
- **Built-in** (`applemusic`, `monochrome`, `jiosaavn`) — from `modules/*` Gradle projects.
- **Module apps** — installed APKs with a `<service>` answering the
  `com.mochyy.mochymusic.SOURCE_MODULE` intent + `<meta-data
  android:name="com.mochyy.mochymusic.module.entry">` pointing at the entry class.
- **Web modules** — from a link (`WebModuleManifest` + `WebModule`).
- **Zip modules** — from a file (`LocalModule`), optionally bringing code (`CodeModules`).

Each gets a `ModuleEntry(module, origin, enabled, picks, account)`. The registry exposes
`StateFlow<List<ModuleEntry>>` and toggles enable/disable.

## 4. The bridge (`modules/bridge/`)

For code that runs in a **separate class loader** (module apps / code zips),
`BridgedModule` + `ModuleSide` + `Wire` + `shared/Shared.kt` cross the class-loader
boundary. **Only** `com.mochyy.mochymusic.module.bridge.shared` crosses — it's kept by name
in Mochy's R8 rules. `BridgeTest.kt` + `FakeModule.kt` test the bridge.

## 5. Looks & skins (`modules/skin/`)

- `ModuleSkin.kt` — the skin model (`SkinBlock`, `SkinBackground`, `PageBlock`, player blocks)
- `SkinParser.kt` — parses a manifest's `player`/`font`/`pages` into a skin (clamps numbers,
  gives defaults so a typo can't break the player)
- `SkinFiles.kt` — unpacks a zip's pictures/fonts
- `ActiveSkin.kt` — the look that's on; applies settings, gives them back on disable
- `ModuleLooks.kt` — applying/restoring look settings (the "user always wins" rule)

A look with `player` brings a **module player style** (`ui/player/styles/ModulePlayerStyle.kt`)
shown first in the player's style selector while the look is on.

## 6. Web modules (`modules/web/`)

`WebModule` implements `SourceModule, LyricsModule, LookModule, SkinSource`. It reads a
`manifest.json` (from a link or zip), exposes choices (quality, mix), and resolves streams
by calling the service's HTTP endpoints. `WebModuleManifest` parses the manifest with
defaults for every field so old manifests keep working.

## 7. The catalog

`modules/catalog.json` lists modules people can find under Settings → Modules → Find more
sources. `ModuleCatalog.kt` reads it.

## 8. The JS Modules module

A built-in module (`mochy-js-modules`, outside this repo) runs sandboxed JavaScript
(`searchTracks` + `getTrackStreamUrl`) with **no access to the phone or app** — only to the
internet through Mochy. Scripts are added as `.js` in a zip or a link.

## 9. How a module's stream plays

When a module is on and first in the provider order, `SourceModules` calls
`module.resolve(track)` → `ResolveResult.Found(SourceStream)`. Mochy then plays the stream
*itself* through ExoPlayer, so every Audio Lab effect still applies. The module only
provides the URL — it never touches the audio pipeline.
