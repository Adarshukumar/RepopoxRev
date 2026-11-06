# 13 — Testing

## 1. What's tested

20 unit-test files in `app/src/test/` (~2,550 LOC), all **pure JVM** (no Android instrumentation
needed for these). They cover the *logic* that's easy to get wrong: DSP math, lyrics sync,
import matching, skin parsing, resampler.

| Test | Covers |
|---|---|
| `crossmix/CrossmixTest.kt` (600 lines) | the crossmix transport state machine |
| `crossmix/TransportMovesTest.kt` | crossfade/transport moves |
| `eq/AudioLabDspTest.kt` | the Audio Lab DSP processors (EQ, loudness, reverb, …) |
| `eq/AutoEqTest.kt` | autoeq preset parsing/application |
| `i18n/AppLanguageTest.kt`, `LanguageResourcesTest.kt` | app language + every locale has same keys |
| `importer/CsvImportTest.kt`, `DateMsTest.kt`, `PlaylistSourceRecordTest.kt`, `TrackMatcherTest.kt` | CSV parsing, dates, playlist provenance, track matching |
| `lyrics/AudioMatcherTest.kt`, `LyricsUtilsTest.kt`, `CaptionAlignerTest.kt`, `LyricsAutoSyncTest.kt` | lyrics fingerprint, LRC parsing, caption alignment, auto-sync |
| `modules/skin/SkinParserTest.kt` | module skin/manifest parsing (clamps, defaults) |
| `playback/audio/SincResamplerTest.kt` | the sinc sample-rate converter |
| `ui/player/VideoLetterboxTest.kt`, `video/VideoDashTest.kt` | video letterbox math, DASH video |
| `ui/screens/settings/importsync/ChromeUserAgentTest.kt` | Chrome user-agent parsing |
| `ui/screens/setup/onboarding/FrameProbeRecorderTest.kt` | onboarding frame probe |

Plus module tests:
- `providers/canvas/.../MonochromeApiCanvasTest.kt`
- `modules/bridge/BridgeTest.kt` + `FakeModule.kt` (the classloader bridge)
- `modules/monochrome/.../MonochromeModuleTest.kt`

## 2. How to run

```bash
./gradlew testDebugUnitTest              # app unit tests
./gradlew :modules:<name>:test           # a module's tests
./gradlew :app:compileArm64FossDebugKotlin   # compile check
```

CI runs `assembleUniversalGmsDebug` (build, not test, in the workflow shown).

## 3. Testing philosophy (visible from the tests)

- Tests target **pure logic** (DSP, parsing, matching, sync math) — the parts where a
  regression is silent and costly.
- The app deliberately keeps DSP/parsing in **plain Kotlin** (no Android) so it's testable
  on the JVM — same reason `modules/api` is plain Kotlin.
- `SkinParserTest` enforces the "clamp every number, give every field a default" rule so a
  typo'd manifest can't break the player.
- `LanguageResourcesTest` enforces that no locale is missing keys.

## 4. What's *not* tested (and why it matters)

- UI/Compose rendering — verified manually on device (the CONTRIBUTING guide stresses
  real-device testing + before/after screenshots for visual changes).
- Network/protocol parsing (InnerTube) — hard to test deterministically; relies on
  manual + the live API.
- The audio pass / native code — verified on real hardware (USB DAC, Hi-Res).
- Module loading across classloaders — `BridgeTest` covers the bridge, but full module-app
  loading needs a signed APK.
