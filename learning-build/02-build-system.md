# 02 — Build system

## Toolchain

| | |
|---|---|
| Gradle | 9.3.1 (wrapper, `validateDistributionUrl=true`) |
| Android Gradle Plugin | 9.0.0 |
| Kotlin | 2.3.10 |
| Compose | 1.10.2, compiler plugin via `libs.plugins.compose.compiler` |
| KSP | 2.3.5 (Room + Hilt) |
| JDK | 21 (required by CI, `temurin`) |
| NDK | 27.0.12077973, CMake 3.22.1, C++17 |
| minSdk / target / compile | 24 / 36 / 36 |
| Desugaring | enabled (`isCoreLibraryDesugaringEnabled = true`) |

`gradle.properties` is tuned hard: 6 GB heap for both Gradle and the Kotlin daemon,
parallel execution, configuration cache, build cache, and 180-second HTTP timeouts
specifically because NewPipeExtractor comes from JitPack and is slow to resolve.

## The variant matrix

Two flavor dimensions plus four build types = **60 buildable variants**.

```
abi      : universal, arm64, armeabi, x86, x86_64      (5)
variant  : foss, fdroid, gms                           (3)
buildType: debug, release, fast, profileable           (4)
                                                             = 60
```

**The `variant` dimension is about distribution policy, not features:**

- `foss` — default. Self-updating, no Google Play Services. `CAST_AVAILABLE = false`.
- `fdroid` — F-Droid policy compliant. Declares no sources of its own; `sourceSets{}`
  points it at `src/foss/kotlin` and `src/foss/res`. It strips `REQUEST_INSTALL_PACKAGES`
  because F-Droid forbids self-updating apps.
- `gms` — adds Google Cast. `media3-cast`, `mediarouter` and `play-services-cast-framework`
  are pulled in with `"gmsImplementation"(...)` so the other two flavors stay clean.

The `foss` and `gms` source sets both define `CastButton`, `CastOptionsProvider` and
`CastConnectionHandler` with the same package and class names. That is *correct*, not a
redeclaration: flavors in one dimension never compile together. The harness special-cases
this rather than reporting a false clash.

**The four build types are a study in build-speed versus measurement honesty:**

| type | debuggable | minify | profileable | purpose |
|---|---|---|---|---|
| `debug` | yes | no | — | fastest iteration; `.debug` appId suffix |
| `release` | no | R8 + resource shrink | — | what users get |
| `fast` | **no** | no | yes | release-grade ART (no JIT deopt) but no R8, so it builds as fast as debug |
| `profileable` | no | R8 | **yes** | byte-identical perf to release, but Perfetto can still attach |

The comments in `app/build.gradle.kts` explain the reasoning, and it is worth quoting
because it is a genuinely subtle point:

> `isDebuggable = false` is what makes release fast (ART will not JIT-deopt, R8 output runs
> as shipped), but it also locks out Studio's profiler and Perfetto's app-level tracks.
> `isProfileable` re-opens exactly those without turning debuggability back on, so frame
> timings measured here are the ones users actually get.

`profileable` also demonstrates a real Gradle technique: `matchingFallbacks += listOf("release")`,
because the `providers:*` library modules only publish `debug` and `release` variants and
would otherwise fail to resolve a `profileable` one.

## Signing — and a trap that was deliberately defused

```kotlin
val hasReleaseSigning = System.getenv("STORE_PASSWORD") != null &&
    System.getenv("KEY_ALIAS") != null &&
    System.getenv("KEY_PASSWORD") != null &&
    file("keystore/release.keystore").exists()
```

`release` signs with the real key only when all four conditions hold, and otherwise falls
back to the debug keystore. The comment says why:

> Release signing only works when the CI secrets are present. Locally they are not, which
> used to make `assembleRelease` unbuildable and left debug as the only variant anyone ever
> ran — and a debug Compose build is several times slower per frame than release, so all
> local performance impressions were being formed against the wrong binary.

Two keystores are referenced but absent from the repository: `app/keystore/release.keystore`
(guarded, so harmless) and `app/persistent-debug.keystore` (`.gitignore`d, and attached to a
`persistentDebug` signing config that **no build type uses** — inert today, a footgun if one
ever does). The harness distinguishes guarded from unconditional signing configs precisely so
it does not cry wolf about the first.

## Injected configuration

No credential is ever committed. All are read from `local.properties` or CI environment
variables and baked in via `buildConfigField`:

- `LASTFM_API_KEY` / `LASTFM_SECRET` — default to `""`
- `LIBREFM_API_KEY` / `LIBREFM_SECRET` — clever fallback: Libre.fm accepts any 32-char key
  and never validates the secret, so a fork or a secretless CI run derives a deterministic
  stand-in by MD5-hashing `"$applicationId.librefm.$name"`. The feature degrades instead of
  breaking.
- `DISCORD_CLIENT_ID` — also drives `manifestPlaceholders["discordRedirectScheme"]`, falling
  back to `mochy-discord-unset` so the OAuth redirect simply shows a friendly error rather
  than registering a broken deep link.
- `BUILD_TIME_MILLIS` — from `git log -1 --format=%ct`, i.e. the **commit** time, not wall
  clock, so it only changes when the code changes and does not break build caching.

## CI

`.github/workflows/build.yml` runs on every push to any branch (ignoring README, fastlane,
assets and `.github/**/*.md`) and on every PR. It sets up JDK 21, generates a throwaway
`~/.android/debug.keystore` with `keytool`, then runs:

```
./gradlew assembleUniversalGmsDebug --no-configuration-cache --console=plain --warning-mode summary
```

The harness confirms that task name is valid in the matrix, and that `--no-configuration-cache`
is a deliberate override of the `org.gradle.unsafe.configuration-cache=true` property.

Note what CI does *not* do: it never runs `./gradlew test`. The 177 unit tests are only run
when a contributor remembers to, which CONTRIBUTING.md asks for explicitly.

## `build.sh` — the one genuinely broken tool

A 350-line driver handling version bumping, build-attempt counters, multi-ABI assembly,
ADB install, and publishing to a GitHub `dev` tag. It is the documented entry point in
CONTRIBUTING.md (`./build.sh --debug`).

**It does not work on Linux.** Six lines use BSD/macOS-only `sed -i ''`:

```bash
sed -i '' "s/versionName = \".*\"/versionName = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" app/build.gradle.kts
```

GNU sed reads `''` as the *script* (empty), then treats the real expression and the target
file as *input filenames*. Under the script's own `set -e`, that aborts immediately. Since
line 145 runs on every build that does not pass `-c`, the documented happy path fails for
every Linux contributor and would fail on the CI runners too.

Portable fixes: `sed -i.bak … && rm -f *.bak`, `perl -pi -e`, or branch on `uname`.

## Reproducing a build

```bash
# what the sandbox could NOT do, and why:
./gradlew assembleArm64FossDebug
#   needs: JDK 21, Android SDK 36, NDK 27.0.12077973, CMake 3.22.1,
#          and network access to google() / mavenCentral() / jitpack.io / maven.aliyun.com
#   sandbox has: no JDK, no SDK, and an allowlist of 6 hosts (none of which are those)

# what it CAN do:
python3 learning-build/tools/verify.py Mochy-Music     # 17 static checks
./gradlew testDebugUnitTest                            # on a real dev machine
```
