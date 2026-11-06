# 03 — Playback engine

The playback package is the heart of the app and by far its densest code:
`playback/MusicService.kt` alone is **4,980 lines**, the largest file in the repository.

## MusicService

```kotlin
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
```

A Media3 `MediaLibraryService` — so it is simultaneously the playback engine, the media
session (lock-screen controls, Android Auto, Bluetooth AVRCP, the two home-screen widgets)
and the media *library* that Android's system UI browses. Dependencies arrive by Hilt field
injection: `MusicDatabase`, `LyricsHelper`, `SyncUtils`, `MediaLibrarySessionCallback`,
`EqualizerService`.

It is declared in the manifest with `foregroundServiceType="mediaPlayback"` and `exported=true`
(the latter with `tools:ignore="ExportedService"` — required so other apps and the system can
bind to the media session).

### State it carries

The service is effectively a large mutable state machine. Its fields read like a map of every
hard-won lesson about streaming audio:

```kotlin
private val resolvedStreamSources = ConcurrentHashMap<String, String>()   // mediaId -> url
private val failedSources         = ConcurrentHashMap<String, MutableSet<String>>()
private val failedSourcesAt       = ConcurrentHashMap<String, Long>()     // for back-off
private val moduleStreamHeaders   = ConcurrentHashMap<String, Map<String, String>>()
private val forcedRoutes          = ConcurrentHashMap<String, ForcedRoute>()
private val decoderNotices        = ConcurrentHashMap<String, DecoderNotice>()
private val songUrlCache          = HashMap<String, Pair<String, Long>>() // url + expiry
private val bypassCacheForQualityChange = mutableSetOf<String>()
private val usedInfiniteSeeds     = HashSet<String>()   // radio seeds already consumed
private val failedInfiniteSeeds   = HashSet<String>()
private val MAX_RETRY_PER_SONG    = 3
private val RETRY_DELAY_MS        = 1000L
private val STREAM_RETRY_INTERVAL_MS = 5000L
```

Concurrency is handled by `ConcurrentHashMap` rather than locks, and per-player state lives in
`WeakHashMap<Player, …>` so a discarded player cannot leak its processors:

```kotlin
private val playerInspectors  = java.util.WeakHashMap<Player, StreamInspector>()
private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()
private val playerReplayGain  = HashMap<Player, ReplayGainTags>()
private val playerEqProcessors = HashMap<Player, CustomEqualizerAudioProcessor>()
private val playerCrossmixProcessors = HashMap<Player, CrossmixTransitionAudioProcessor>()
private val playerSpectra     = java.util.WeakHashMap<Player, AudioSpectrum>()
```

### Failure handling

There is a whole vocabulary of recovery paths, each with its own method:
`waitOnNetworkError()`, `triggerRetry()`, `skipAfterFailure()`, `failStream()`,
`clearStreamFailure()`, `onUndecodableAudio()`, `noteMissingDecoder()`, `isDecoderError()`.

Two deserve attention:

- **Decoder detection.** When a stream's codec has no decoder on the device, the service does
  not just fail — it records *which* source produced it (`noteMissingDecoder(mediaId, codec,
  sampleRate, from)`), announces it to the UI, and moves to the next provider. This is why a
  module can advertise `sampleRateHz` in its `SourceStream`: the app skips a stream the phone
  physically cannot decode.
- **Rate limiting.** `YouTubeRateLimitedException` exists as its own type, and the comment
  explains the policy: *"YouTube answered 429: it's refusing this network for a while. Nothing
  else is asked — every client goes to the same host, and more requests only keep the limit in
  place."* Retrying harder would make it worse, so the app deliberately stops.

## The DSP chain

`createExoPlayer()` assembles a Media3 `AudioProcessor` pipeline. Order matters, because these
are applied to the PCM buffer in sequence:

```
ExoPlayer
  └── CustomEqualizerAudioProcessor     parametric EQ (biquad cascade)
  └── fxProcessors = [
        SpatialAudioProcessor,          virtual surround
        ReverbAudioProcessor,           room reverb
        LoudnessAudioProcessor,         loudness normalisation
      ]
  └── SilenceDetectorAudioProcessor     -> handleLongSilenceDetected()
  └── CrossmixTransitionAudioProcessor  gapless crossfade between tracks
  └── HapticAudioProcessor              -> amplitude + isBassKick -> vibration
  └── SincResampler                     sample-rate conversion
  └── StreamPcmDecoder
  └── PrecisionSink / PassRouterSink    output routing
```

Supporting DSP lives in `eq/audio/`: `BiquadFilter`, `PeakLimiter`, `ReplayGain`,
`VolumeCurve`, `AudioTap`, `AudioSpectrum`. `eq/autoeq/` implements AutoEq — it reads the
AutoEq headphone database (`AutoEqRepository`), fits a correction curve (`AutoEqFitter`), and
there are **real unit tests** for it (`AutoEqTest`, `AudioLabDspTest`) with fixture data under
`app/src/test/resources/autoeq/` including a genuine Sony WH-1000XM5 parametric EQ file.

`SincResampler` has its own test (`SincResamplerTest`) — windowed-sinc resampling is exactly
the kind of DSP that silently produces aliasing if it is wrong, so testing it is well judged.

## Crossmix

`crossmix/` plus `CrossmixDirector`, `CrossmixTransitionAudioProcessor` and
`CrossmixClockSink` implement gapless crossfading. This is subtle work: two players must run
with a shared clock so the fade is sample-accurate, which is what `CrossmixClockSink` is for.
`CrossmixTest` and `TransportMovesTest` cover the transport logic.

The roadmap notes *"Crossfade is not working with reverb"* as a known open issue — consistent
with the difficulty of running a crossfade through a stateful reverb processor.

## Audio pass — bit-perfect output, written twice

`playback/audio/pass/` is the most unusual subsystem in the app. Five modes, each with a
minimum SDK:

```kotlin
enum class AudioPassMode(val minSdk: Int) {
    SONG_RATE(34),      // let Android's own DAC mixer run at the song's rate
    BIT_PERFECT(34),    // Android 14+ bit-perfect path
    DIRECT_USB(24),     // Mochy's own USB audio driver, bypassing Android entirely
    EXCLUSIVE_USB(27),  // AAudio exclusive mode on a USB DAC
    EXCLUSIVE_DAC(27),  // AAudio exclusive mode on wired headphones

    val exclusive: Boolean get() = this != SONG_RATE
}
```

The `DIRECT_USB` path is implemented in **C++17** (`app/src/main/cpp/usb_pass.cpp`, 19 KB, plus
`exclusive_pass.cpp`, 13.6 KB) — the app parses USB audio class descriptors itself
(`UsbAudioDescriptors.kt`), claims the interface, and writes PCM straight to the DAC
(`UsbDacAccess.kt`, `UsbDacVolume.kt`). That is why `MODIFY_AUDIO_SETTINGS` is requested and
why the manifest comment reads *"lets the player ask a usb dac to run at the song's own rate,
or bit-perfect, on android 14+"*.

The `CMakeLists.txt` is a lesson in shipping native code to old devices:

```cmake
target_compile_options(mochypass PRIVATE -O2 -Wall -fvisibility=hidden)
# aaudio is opened at runtime, so only the log and the loader are linked;
# that keeps the library loadable on android 7
target_link_libraries(mochypass log dl)
# 16 kb pages on android 15 phones need the segments aligned to them
target_link_options(mochypass PRIVATE "-Wl,-z,max-page-size=16384")
```

AAudio is `dlopen`ed rather than linked, so the same `.so` loads on Android 7 where AAudio does
not exist. And the 16 KB page alignment is for Android 15 devices — a forward-compatibility
detail most projects miss.

`PassBlockers.kt` deserves a mention: it enumerates *why* pass-through is unavailable (another
app holds the device, the format is unsupported, permissions), so the UI can explain rather
than silently fall back.

## Queues

`playback/queues/` defines a `Queue` interface with a `Status` data class and six
implementations: `ListQueue`, `EmptyQueue`, `YouTubeQueue`, `YouTubePlaylistQueue`,
`YouTubeAlbumRadio`, `LocalAlbumRadio`. Radio queues are *infinite* — hence
`usedInfiniteSeeds` / `failedInfiniteSeeds` in the service, which stop the same seed being
re-requested forever when YouTube returns an empty automix.

## Provider switching

`ProviderSwitch.kt` implements the "play this song from a different source" feature with an
explicit state machine:

```kotlin
sealed interface ProviderSwitchState {
    data object Idle
    data class Working(val providerId: String, val attempt: Int, val of: Int)
    data class Failed(val providerId: String)
    data class Done(val providerId: String)
}
```

Its `Host` interface is the seam back into the service — `currentMediaId()`, `holdPlayback()`,
`resumePlayback()`, `resolve(providerId, mediaId)`, `switchTo(mediaId, route, play)`. Playback
is *held* rather than stopped while a new source resolves, so switching does not produce a gap.

`SourceModules.kt` maintains the user-reorderable provider order (`provider_order` in
SharedPreferences), and includes a migration of its own: a one-time
`order_monochrome_first` flag that promotes Monochrome to the top for orders saved before it
existed. Note `reorderVisible()` — hidden (disabled) modules keep their position, so toggling a
module off and on does not reshuffle the user's list.

## Other services

- `ExoDownloadService` — Media3 download manager, `foregroundServiceType="dataSync"`, with a
  `RESTART` intent filter so interrupted downloads resume after reboot (`RECEIVE_BOOT_COMPLETED`).
- `CallWatcher` — ducks or pauses on incoming calls.
- `SleepTimer`, `YouTubePlayReporter` (reports watch time back to YouTube so play counts
  register), `StreamInspector` / `StreamFacts` (diagnostics), `CachedSongs`, `TransportFadingPlayer`,
  `TransportTransitions`.
