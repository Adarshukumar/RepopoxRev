# 01 — App overview

## What Mochy Music is

An unofficial, GPL-3.0 Android music player. It streams from YouTube Music through the
private **InnerTube** API, supplements that with alternative audio sources (Apple Music,
Monochrome FLAC, JioSaavn) through a plugin system, and pulls lyrics from twelve different
services. It needs no account to work; signing in only adds library sync and Discord status.

It is a fork-lineage descendant of ViMusic/InnerTune-style players — the leftover
`com.music.vivi` package directories and the `com.music.vivi.db` Room schema history are the
fossil record of that rename.

## Scale

| | |
|---|---|
| Kotlin files | 1,036 |
| Kotlin lines | 230,722 |
| Distinct declarations indexed | 27,872 |
| XML files | 763 |
| Resource references resolved | 2,897 |
| Gradle modules | 22 (`:app` + 16 providers + 5 source modules) |
| Buildable variants | 60 (5 ABI × 3 distribution × 4 build types) |
| String resources | 1,946 default keys across 83 locales |
| Native C++ | 2 files, ~950 lines (`usb_pass.cpp`, `exclusive_pass.cpp`) |
| Unit tests | 27 files, 177 `@Test` methods |
| Version | `8.3.2 build#1226`, versionCode 73, minSdk 24, target/compileSdk 36 |

## The shape of the repository

```
Mochy-Music/
├── app/                      the Android application (208k of the 231k lines)
│   ├── src/main/kotlin/com/mochyy/mochymusic/
│   ├── src/main/cpp/         audio pass: direct USB DAC + AAudio exclusive output
│   ├── src/main/res/         83 locale folders, 50 launcher icon variants, fonts
│   ├── src/foss/             self-updating build, no Google Play Services
│   ├── src/gms/              Google Cast build
│   ├── src/fdroid/           reuses src/foss via sourceSets{}, strips install permission
│   └── schemas/              Room schema history (stops at v34 — see 04-data-layer.md)
├── providers/                16 network modules, each a thin typed API client
│   ├── innertube/            YouTube Music's private API — 8.3k lines, the big one
│   ├── kugou/ lrclib/ musixmatch/ genius/ …   lyrics sources
│   ├── lastfm/               scrobbling
│   ├── shazamkit/            audio fingerprint recognition
│   └── canvas/ applecanvas/ artistvideo/      cover art and video backdrops
├── modules/                  the plugin system
│   ├── api/                  MochyModule: the contract plugins compile against
│   ├── bridge/               loads modules that ship their own code
│   ├── applemusic/ monochrome/ jiosaavn/     built-in audio source modules
├── gradle/libs.versions.toml version catalog: 43 versions, 71 libraries, 5 plugins
├── build.sh                  the maintainer's build/publish driver (macOS-only — see 07)
└── .github/workflows/        build.yml, build_pr.yml, stale.yml
```

## The layering

The dependency direction is strict and the harness verifies it (6,085 cross-module imports,
all backed by a declared `project(":…")` dependency):

```
        ┌─────────────────────────────────────────────┐
        │  app  — Compose UI, ViewModels, MusicService │
        └───────┬─────────────────────┬───────────────┘
                │                     │
      ┌─────────▼──────────┐   ┌──────▼───────────┐
      │  providers:*       │   │  modules:*       │
      │  typed API clients │   │  plugin system   │
      │  (no Android UI)   │   │  api/bridge/built-in
      └─────────┬──────────┘   └──────┬───────────┘
                │                     │
                └──────────┬──────────┘
                           │
                  modules:api — the only shared contract,
                  deliberately free of Android imports so a
                  module app can load it at runtime
```

`providers:*` know how to talk to a service and nothing else. They return typed models.
The app owns all state, all persistence and all UI. `modules:api` is the seam that lets
third parties add audio sources, lyrics and whole visual themes without an app update.

## The interesting subsystems

Each gets its own document:

| Subsystem | Where | Doc |
|---|---|---|
| Playback engine, DSP chain, bit-perfect USB audio | `playback/`, `eq/`, `cpp/` | [03](03-playback-engine.md) |
| Room database, 23 entities, migration graph | `db/` | [04](04-data-layer.md) |
| InnerTube, PoToken/BotGuard, signature deciphering, lyrics | `providers/`, `utils/` | [05](05-network-and-providers.md) |
| Compose navigation, 11 player styles, blur/theming | `ui/` | [06](06-ui-and-compose.md) |
| 60 build variants, 4 build types, CI | `app/build.gradle.kts`, `.github/` | [02](02-build-system.md) |

## What makes this codebase worth studying

Three things stand out, and they are the reason the docs go deep rather than broad:

**1. It solves genuinely hard problems client-side.** There is no server. To play a song it
must obtain a PoToken by running Google's BotGuard attestation inside a hidden WebView,
decipher YouTube's rotating signature cipher by fetching and analysing `player.js`, and
solve an `N`-transform for SABR streams — all on the phone, all documented in
[05-network-and-providers.md](05-network-and-providers.md).

**2. The audio path is written twice.** Once in Kotlin as a chain of Media3
`AudioProcessor`s (EQ, spatial, reverb, loudness, silence detection, crossfade, haptics),
and once in C++17 as a direct USB DAC driver and an AAudio exclusive-mode output that
bypasses Android's mixer entirely for bit-perfect playback. See
[03-playback-engine.md](03-playback-engine.md).

**3. The comments explain *why*, not *what*.** The CONTRIBUTING file mandates it and shows
good and bad examples. The result is a codebase where the reasoning behind non-obvious
decisions survives — which is precisely what makes it teachable.
