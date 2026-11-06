# 09 — Audio Lab & Equalizer

Mochy ships a full **audio DSP lab** (`eq/`, ~3.2k LOC) plus a **crossmix** engine
(`crossmix/`, ~2.3k LOC). Effects run in the audio pass pipeline (see `04-playback-engine.md`).

## 1. Equalizer (`eq/EqualizerService.kt`)

- A **10-band** parametric equalizer.
- `CustomEqualizerAudioProcessor` (in `eq/audio/`) implements Media3's `AudioProcessor`.
- Presets + a custom curve, persisted (`AudioLabStore`).
- Applied in the audio pass so it works for *every* source (YouTube, modules, local).

## 2. `eq/audio/` — the DSP processors

| Processor | Job |
|---|---|
| `BiquadFilter` | the building block (peaking/low-shelf/high-shelf/low-pass/high-pass) |
| `CustomEqualizerAudioProcessor` | the 10-band EQ |
| `LoudnessAudioProcessor` | loudness normalisation + Loudness Enhancer |
| `PeakLimiter` | prevent clipping |
| `ReverbAudioProcessor` | reverb (Audio Lab) |
| `SpatialAudioProcessor` | spatial / Atmos-style |
| `VolumeCurve` | custom volume curve |
| `AudioTap` | tap point for analysis/visualisation |
| `ReplayGain` | ReplayGain tag-based gain |

## 3. AutoEq (`eq/autoeq/`)

`AutoEq` applies **autoeq** presets (compensation curves from the autoeq project).
- `eq/data/` holds the preset data.
- `AutoEqTest.kt` tests the preset parsing/application.
- The Audio Quality screen lets users pick a target (headphone model) and Mochy applies
  the matching curve.

## 4. Audio Lab (`AudioLabViewModel`, `AudioLabEntryPoint`)

The "Audio Lab" is the umbrella for all audio effects: EQ, reverb, spatial, loudness,
crossmix, Hi-Res output, USB pass. It's exposed via a Hilt `AudioLabEntryPoint` so the
service (a library concern) can reach the app's settings without a circular dep.

## 5. Crossmix (`crossmix/`)

Crossmix = Mochy's **crossfade-between-sources / mix-with-other-apps** feature.

- `CrossmixDirector.kt` — orchestrates the crossfade between two streams.
- `CrossmixAnalysisRepository.kt` — stores analyses (so it can match/blend).
- `crossmix/CrossmixTest.kt` + `TransportMovesTest.kt` — unit tests for the transport
  state machine (how it moves between tracks).
- Settings: `CrossmixModeKey`, `CrossmixTestToolsKey` (experimental live status +
  "Test on this song").

## 6. Settings keys (from `constants/`)

A huge set of audio preference keys: `AudioQualityKey`, `HiResOutputKey`,
`HiFiResamplerKey`, `PreferUsbDacKey`, `UsbPassthroughKey`, `AudioPassModeKey`,
`AudioPassFixedVolumeKey`, `AudioPassRestoreKey`, `AudioNormalizationKey`,
`CrossfadeEnabledKey`, `CrossfadeDurationKey`, `CrossfadeGaplessKey`,
`TransportFadeEnabledKey`, `TransportFadeSecondsKey`, `SkipSilenceKey`,
`SkipSilenceInstantKey`, `PauseOnMute`, `SeekExtraSeconds`, `ReplayGain`, …

## 7. The audio pass (native half)

See `04-playback-engine.md` §4 — `app/src/main/cpp` builds `mochypass` (USB DAC direct
driver + AAudio exclusive output) for bit-perfect / Hi-Res output.

## 8. Testing

`AudioLabDspTest.kt` (268 lines) tests the DSP math; `AutoEqTest.kt` tests preset parsing.
These run on the JVM (pure Kotlin DSP), so they're fast and don't need Android.
