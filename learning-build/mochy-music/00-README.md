# Mochy Music — Deep-Dive Learning Build

> A complete, structured study of the **Mochy Music** Android app
> (`github.com/realidkroo/Mochy-Music`), cloned and analysed line-by-line.
>
> This folder is the "learning build" — a self-contained set of notes that explains
> the entire app: what it is, how it is built, how every subsystem works, and the key
> engineering ideas worth learning from it.

---

## What is Mochy Music?

Mochy Music is a **free, open-source (GPL-3.0) Android music player** that streams from
YouTube Music (and many other sources), downloads, manages a local library, syncs
lyrics, scrobbles to Last.fm/Libre.fm, shows Discord presence, and does all of it with
a sleek, customisable Jetpack Compose UI. It is **not** affiliated with YouTube/Google/
Spotify/Apple. It is still in beta.

- **Package:** `com.mochyy.mochymusic`
- **Version studied:** `8.3.2 build#1226` (versionCode 73)
- **Min / Target SDK:** 24 (Android 7.0) / 36 (Android 16) — officially Android 8.0+, Android 7 unofficial
- **Language:** Kotlin 2.3.10, 100% Jetpack Compose UI (no XML views except widgets)
- **Size of codebase studied:** ~**230,700 lines of Kotlin** across **1,036 files**
  (870 in `app/`, 151 in `providers/`, 15 in `modules/`, plus tests)

---

## Why this app is worth studying

Mochy is a **tiny team's answer to a huge problem**: how to build a premium-feeling
music player that talks to YouTube Music's private InnerTube API *without* a backend,
while staying open-source, privacy-respecting, and F-Droid-buildable. It bundles an
entire reverse-engineered YouTube Music client, a multi-source lyrics engine, a
Widevine/DASH/HLS playback pipeline, an audio DSP lab, a plugin/module system, and a
Discord RPC — all in one app.

---

## Folder guide

| File | What it covers |
|---|---|
| [`01-architecture.md`](01-architecture.md) | High-level architecture, layering, module map, data flow |
| [`02-build-system.md`](02-build-system.md) | Gradle, flavors, build types, variants, `build.sh`, CI, signing |
| [`03-core-lifecycle.md`](03-core-lifecycle.md) | `App`, `MainActivity`, DI, settings/DataStore, PoToken boot |
| [`04-playback-engine.md`](04-playback-engine.md) | `MusicService`, `PlayerConnection`, ExoPlayer, audio pass, crossfade |
| [`05-navigation-ui.md`](05-navigation-ui.md) | Nav graph, screens, theme, player styles, components |
| [`06-providers.md`](06-providers.md) | InnerTube (YouTube Music), lyrics providers, Last.fm, etc. |
| [`07-module-system.md`](07-module-system.md) | The plugin system: looks, web modules, zip modules, code modules |
| [`08-database.md`](08-database.md) | Room schema, entities, DAOs, library/import/sync data |
| [`09-audio-lab-eq.md`](09-audio-lab-eq.md) | Equalizer, Audio Lab DSP, AutoEq, reverb, crossmix |
| [`10-lyrics.md`](10-lyrics.md) | Multi-provider lyrics, auto-sync, caption alignment, translation |
| [`11-import-sync.md`](11-import-sync.md) | Spotify/Apple/YouTube playlist import, transfer, resync |
| [`12-integrations.md`](12-integrations.md) | Scrobble, Discord RPC, Shazam recognition, widgets |
| [`13-testing.md`](13-testing.md) | Unit tests, what they cover, how to run them |
| [`14-key-learning-points.md`](14-key-learning-points.md) | The big takeaways & engineering patterns |
| [`15-build-verification-report.md`](15-build-verification-report.md) | Honest report on building/verifying the app |

---

## The 60-second summary

- **One `app` module** + **16 `providers:*` modules** + **5 `modules:*` modules**.
- **UI:** Jetpack Compose + Material 3, dark-only, dynamic color from album art,
  shared-element cover transitions, 10 player "styles".
- **Playback:** Media3/ExoPlayer with a custom audio-pass pipeline (USB DAC, Hi-Res,
  crossfade, silence-skip, sample-rate conversion, Audio Lab effects).
- **Sources:** InnerTube (YouTube Music) is the default; modules add Apple Music,
  JioSaavn, Monochrome, and arbitrary web services.
- **Extensibility:** a signed-plugin "module system" (looks = restyle, web = songs/lyrics,
  code = runs in-process) so new sources ship without an app update.
- **Privacy:** no account needed to listen; logins open the *real* service page in-app;
  tokens stay on-device; F-Droid/FOSS builds have no Google services.

---

*Generated from a full clone + static analysis of the repository at commit `main`
(latest commit `8dd8f3b`, "Revise Mochy Music description in README").*
