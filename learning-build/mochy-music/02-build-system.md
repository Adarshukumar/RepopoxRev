# 02 — Build System

## 1. Gradle & toolchain

- **AGP:** 9.0.0 · **Kotlin:** 2.3.10 · **KSP:** 2.3.5 · **Compose:** 1.10.2 · **Media3:** 1.7.1
- **JDK 21** (`jvmToolchain(21)`, `sourceCompatibility = VERSION_21`)
- **compileSdk 36**, **NDK 27.0.12077973**, **minSdk 24**
- Version catalog: `gradle/libs.versions.toml`
- Compose compiler tuning: `compose_compiler_config.conf` (stability config), source-info
  stripped for release/profileable/fast builds, opt-in `-PcomposeMetrics` for reports.

## 2. Product flavors (two dimensions)

```
flavorDimensions += listOf("abi", "variant")
```

**`variant` dimension** (the "flavour" people mean):
| Flavor | What it is | `CAST_AVAILABLE` |
|---|---|---|
| `foss` *(default)* | self-updating, no Google Play Services | false |
| `fdroid` | F-Droid-policy compliant (strips `REQUEST_INSTALL_PACKAGES`) | false |
| `gms` | Google Cast support (media3-cast, mediarouter, play-services-cast) | true |

`fdroid` also reuses `src/foss/kotlin` as an extra source set
(`sourceSets.fdroid.java.directories.add("src/foss/kotlin")`).

**`abi` dimension:** `universal`, `arm64`, `armeabi`, `x86`, `x86_64` — each sets
`BuildConfig.ARCHITECTURE` and an `abiFilters` list. The `universal` APK bundles all four
ABI folders.

## 3. Build types

| Build type | Purpose |
|---|---|
| `debug` | fast iteration, `applicationIdSuffix = ".debug"`, debug keystore |
| `release` | R8 minify + shrink resources, release keystore (or debug fallback locally) |
| `profileable` | **identical to release** (`isDebuggable=false`, R8 on) but `isProfileable=true` so Perfetto/Studio profiler attaches — for measuring *real* frame performance |
| `fast` | like debug but **not debuggable** → ART uses baseline profiles, no R8 → builds fast, runs fast |

**Smart detail:** `profileable` exists because a debug Compose build is several× slower
per frame than release — so measuring on debug gives wrong perf numbers. `profileable`
gives release-identical performance *with* the profiler attachable.

## 4. The variant matrix

`flavor × buildType` × `abi` → e.g. `assembleUniversalGmsDebug`, `assembleArm64FossRelease`,
`assembleUniversalFdroidProfileable`. The CI builds `assembleUniversalGmsDebug`.

## 5. `build.sh` — the friendly build wrapper

A bash script (`build.sh`) that wraps Gradle for day-to-day dev. Options:

```
./build.sh [--debug|--release] [-fd|--fdroid] [-i] [-I] [-a] [-cc]
           [-c <version>] [-g "date,ver,attempt"] [--commit "changelog"] [-k]
```

- **`--debug`** (default) / **`--release`**
- **`-fd`** → F-Droid flavor; **`-i`** → ADB install + auto-launch; **`-I`** → skip compile, install existing APK
- **`-a`** → build **all** ABIs; **`-cc`** → arm64 + universal only; default = universal only
- **`-c <ver>`** → bump `versionName`; **`-g "date,ver,attempt"`** → scoped custom build that
  backs up & restores `build.gradle.kts` + beta-notice + `.build_number` (trap-based cleanup)
- **`--commit "notes"`** → bump build number, rename APKs, `gh release edit/create` on the
  `dev` tag, upload APKs
- **`-k`** → kill Gradle daemons

It maintains `.build_number` (incremented each build) and injects `mochy build #N <variant>`
into the beta notice screen via `sed`.

## 6. Signing

- `persistentDebug` → `persistent-debug.keystore` (stable debug identity, used by `fast`)
- `debug` → `~/.android/debug.keystore` (`androiddebugkey`)
- `release` → only when env secrets `STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` +
  `keystore/release.keystore` exist (CI). **Locally it falls back to the debug keystore**
  so `assembleRelease` is always runnable — a deliberate choice so devs measure release-quality
  builds.

## 7. BuildConfig fields (generated)

| Field | Source |
|---|---|
| `LASTFM_API_KEY` / `LASTFM_SECRET` | `local.properties` or CI secrets (never committed) |
| `LIBREFM_API_KEY` / `LIBREFM_SECRET` | same, or a deterministic MD5 stand-in so Libre.fm still works |
| `DISCORD_CLIENT_ID` | same; sets `manifestPlaceholders["discordRedirectScheme"]` |
| `IS_NIGHTLY` | `-Pnightly=true` |
| `BUILD_TIME_MILLIS` | `git log -1 --format=%ct` (commit time, not wall clock) |
| `CAST_AVAILABLE` | flavor |
| `ARCHITECTURE` | ABI flavor |

## 8. CI (`.github/workflows/build.yml`)

On push/PR to any branch (ignoring README/fastlane/assets/.github docs):
1. `actions/setup-java@v5` → Temurin JDK 21
2. `gradle/actions/setup-gradle@v4` (cache disabled)
3. `chmod +x gradlew`
4. Generate `~/.android/debug.keystore` via `keytool`
5. `./gradlew assembleUniversalGmsDebug --no-configuration-cache`
6. Upload the APK artifact

(`build_pr.yml` is the PR variant.) Secrets injected: LASTFM, LIBREFM, DISCORD.

## 9. Protobuf, KSP, Room

- **Protobuf** (lite) for message serialization (YouTube internal protocols).
- **Room** via KSP: `ksp(libs.room.compiler)`, schema export to `app/schemas/`
  (`room.schemaLocation`), migrations via `app/schemas/.../InternalDatabase/34.json`.
- **Hilt** via KSP: `ksp(libs.hilt.compiler)`.

## 10. ProGuard / R8

`proguard-android-optimize.txt` + `proguard-rules.pro` (+ `proguard-profileable.pro`).
Keep rules preserve `com.mochyy.mochymusic.module.bridge.shared` (the only classes that
cross the module class-loader boundary) and the `mochy-js-modules` bridge.
