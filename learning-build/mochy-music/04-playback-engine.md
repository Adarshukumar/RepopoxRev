# 04 — Playback Engine

The playback core is the most complex subsystem (~14.7k LOC in `playback/`). It is built
on **Media3 / ExoPlayer** and extended with a custom audio pipeline.

## 1. The two main classes

### `MusicService` (`playback/MusicService.kt`, ~5k LOC)
A `MediaLibraryService` (foreground service) that **owns** the `ExoPlayer` and the
`MediaSession`. It is the single place playback actually happens. Responsibilities:

- Build & configure `ExoPlayer` (renderers, audio sink, download cache, DNS-over-HTTPS).
- Own the `MediaSession` (foreground notification, media buttons, auto-widget).
- Resolve stream URLs lazily (delegated to `SourceModules`/`YouTube`).
- Run the **audio pass** pipeline (USB DAC, Hi-Res, crossfade, silence-skip, SRC, Audio Lab).
- Handle: repeat/shuffle, queue windows, auto-skip-on-error, crossfade, sleep timer,
  audio focus, media-session commands, downloads, scrobbling hooks, playback stats.
- Expose `isPlayerReady` (a `StateFlow<Boolean>`) so `PlayerConnection` can wait for init.

### `PlayerConnection` (`playback/PlayerConnection.kt`, ~805 LOC)
The **app-side handle** to the service. Constructed by `MainActivity` after binding.
It is a `Player.Listener` that mirrors ExoPlayer state into Compose-friendly flows:

- `player: ExoPlayer` (safe accessor that waits for readiness)
- `playbackState`, `playWhenReady`, `isPlaying` (`StateFlow`)
- `playQueue(queue)`, `togglePlayPause()`, `seekTo()`, `next()`, `previous()`, …
- Combines `playbackState × playWhenReady × transport.playIntent` so a fade shows the
  *pressed* state immediately, not when the player catches up.
- Owns a `SupervisorJob` scope so `dispose()` stops its pollers/flows.

## 2. Queues (`playback/queues/`)

`Queue` is a sealed interface. Implementations:

| Queue | Source |
|---|---|
| `ListQueue` | a plain list of songs |
| `YouTubeQueue` | a YouTube `WatchEndpoint` (videoId + playlistId) — supports `next()` continuation |
| `YouTubePlaylistQueue` | YouTube playlist |
| `YouTubeAlbumRadio` | album radio |
| `LocalAlbumRadio` | local album radio |
| `EmptyQueue` | nothing |

Each queue provides `getQueueWindows()`, `getCurrentQueueIndex()`, `isAutoplay()`, etc.

## 3. Stream resolution (the lazy-URL trick)

For YouTube songs the stream URL isn't known at queue time. `DynamicResolvingMediaSource`
wraps the `MediaItem` so that **at playback time** it asks `SourceModules` to resolve the
URL. This is essential because:
- YouTube URLs expire (and need a fresh PoToken).
- The player can request a different quality mid-stream.

`SourceModules` walks the **provider order** (modules → YouTube → …) calling each
`SourceModule.resolve(track)` until one returns a `SourceStream`. If all are exhausted,
it reports `Exhausted`.

## 4. The audio pass pipeline (`playback/audio/`)

A chain of Media3 `AudioProcessor`s sits between the decoder and the `AudioSink`.

| Processor | Job |
|---|---|
| `SincResampler` | high-quality sinc sample-rate conversion (Hi-Res output) |
| `StreamPcmDecoder` | decode opaque PCM streams |
| `SilenceDetectorAudioProcessor` | skip silence |
| `HapticAudioProcessor` | haptic feedback synced to audio |
| `CrossmixTransitionAudioProcessor` | crossfade between tracks |
| `CrossmixClockSink` | crossmix clock |
| `CustomEqualizerAudioProcessor` | the 10-band equalizer + presets |
| `LoudnessAudioProcessor` | loudness normalisation / loudness enhancer |
| `PeakLimiter` | prevent clipping |
| `ReverbAudioProcessor` | reverb (Audio Lab) |
| `SpatialAudioProcessor` | spatial/Atmos-style processing |
| `VolumeCurve` | custom volume curve |

### Native half (`app/src/main/cpp`)
`CMakeLists.txt` builds a tiny `mochypass` shared library (no third-party code):
- `usb_pass.cpp` — direct USB DAC driver (ask the DAC to run at the song's rate / bit-perfect, Android 14+)
- `exclusive_pass.cpp` — AAudio exclusive output (Hi-Res / bit-perfect)

Linked against `log` + `dl` only, so it loads on Android 7. Pages aligned to 16 KB for
Android 15. This is the "audio pass" — bypassing Android's mixing for bit-perfect output.

## 5. Audio quality & output modes

From `MusicService` imports & constants:
- `AudioQualityKey`, `HiResOutputKey`, `HiFiResamplerKey`, `PreferUsbDacKey`, `UsbPassthroughKey`
- `AudioPassMode` / `AudioPassFixedVolumeKey` / `AudioPassRestoreKey` — the USB/exclusive pass modes
- `AudioNormalizationKey` — loudness normalisation
- `BluetoothCodecs.kt` — LDAC/aptX/etc. codec negotiation

## 6. Crossfade & transport fade

- `CrossfadeEnabledKey`, `CrossfadeDurationKey`, `CrossfadeGaplessKey` — gapless crossfade.
- `TransportFadeEnabledKey`, `TransportFadeSecondsKey`, `TransportEffect` — a *fade on
  play/pause/seek* (the "transport fade") with min/max seconds; `TransportTransitions.kt`
  implements it, `TransportFadingPlayer.kt` wraps the player.

## 7. Crossmix (`crossmix/`)

Crossmix is Mochy's "mix with another app" / crossfade-between-sources feature.
`CrossmixDirector.kt` orchestrates it; `CrossmixAnalysisRepository.kt` stores analyses.
Tested by `CrossmixTest.kt` + `TransportMovesTest.kt`.

## 8. Sleep timer, call watcher, downloads

- `SleepTimer.kt` — stop playback after N minutes / at end of song.
- `CallWatcher.kt` — pause on incoming call.
- `ExoDownloadService.kt` + `DownloadUtil.kt` — background downloads (module-aware download order).
- `CachedSongs.kt` — offline cache management.

## 9. YouTube playback reporting

`YouTubePlayReporter.kt` reports watchtime/playback to YouTube so the session stays healthy
(`YouTube.reportWatchtime`, `YouTube.registerPlayback`). `CallWatcher` watches calls.

## 10. MediaSession & notification

`MediaLibrarySessionCallback.kt` wires media-session commands (play/pause/next/prev/like/
repeat/shuffle/radio) to the player. The notification is built by Media3's
`DefaultMediaNotificationProvider` + a custom channel ("updates").

## 11. Stream facts & inspection

`StreamFacts.kt` / `StreamInspector.kt` — inspect the resolved stream (codec, sample rate,
bitrate) for the format badge and the "Audio Quality" screen.
