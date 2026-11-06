# 15 — Build Verification Report

An honest report on building and verifying Mochy Music in this environment.

## 1. What was attempted

A full `./gradlew assembleUniversalGmsDebug` (the CI build) requires:
- **JDK 21** — not installed, and not downloadable here
- **Android SDK** (compileSdk 36, build-tools, platform-36) — not installed
- **NDK 27.0.12077973** — not installed
- **Gradle distribution** (via the `gradlew` wrapper) — downloads from `services.gradle.org`
- **Maven/Google dependencies** (`google()`, `mavenCentral()`, `jitpack.io`)

## 2. Network reality of this sandbox

Outbound internet is restricted to: `github.com`, `codeload.github.com`, `api.github.com`,
`registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`. Confirmed blocked (HTTP 000):

| Host | Status |
|---|---|
| `services.gradle.org` (Gradle wrapper) | ❌ blocked |
| `dl.google.com` (Android SDK / Google Maven) | ❌ blocked |
| `repo.maven.apache.org` / `repo1.maven.org` (Maven Central) | ❌ blocked |
| `jitpack.io` | ❌ blocked |
| `objects.githubusercontent.com` (GitHub release assets) | ❌ blocked |
| `api.adoptium.net` / `download.java.net` / `aka.ms` / `cdn.azul.com` (JDKs) | ❌ blocked |

Because **no JDK, no Android SDK, and no Gradle/Maven access** are available, a real
compile/build is **not possible** in this sandbox. This is an environment limitation, not
a code problem.

## 3. What was verified instead (static analysis)

Since a build wasn't possible, the app was verified by **deep static analysis**:

### ✅ Repository integrity
- Clean clone of `main` (commit `8dd8f3b`), 2,128 tracked files, ~79 MB.
- `git ls-files` + directory walk confirm the expected structure (`app/`, `providers/`,
  `modules/`, `gradle/`, `.github/`, `scripts/`, `assets/`, `fastlane/`).

### ✅ Build configuration coherence
- `settings.gradle.kts` includes all 16 `providers:*` + 5 `modules:*` + `:app` — all
  referenced modules exist on disk.
- `build.gradle.kts` (app) dependency list matches the included projects.
- `gradle/libs.versions.toml` defines every version referenced by the build files.
- `app/build.gradle.kts`: flavors/build-types/signing/`BuildConfig` fields all consistent;
  `externalNativeBuild` CMake path exists (`app/src/main/cpp/CMakeLists.txt`); Room schema
  location exists (`app/schemas/.../34.json`); Compose compiler config file exists.

### ✅ Source code coherence
- **834 Kotlin files** in `app/src/main/kotlin` scanned.
- **5,132 internal `com.mochyy.mochymusic.*` imports** checked. The only "unresolved"
  hits were **false positives** from a naive regex that didn't handle `suspend`/`inline`/
  `operator` modifiers and generated symbols (`R`, `BuildConfig`) — e.g. `rememberPreference`,
  `dataStore`, `resize`, `toMediaItem` were confirmed to exist as real declarations.
  **No genuinely missing classes** were found.
- Manifest (`AndroidManifest.xml`) permissions, activities, activity-aliases (icons),
  intent-filters (YouTube/share/audio), and `queries` are all well-formed.

### ✅ Resources
- `res/values/` has `strings.xml` (297 strings), `styles.xml` (themes), `colors.xml`,
  plus per-feature string files; **60+ locale `values-*` directories**.
- `res/layout/` has 4 widget layouts; `res/drawable*`, `res/mipmap*`, `res/font` present.
- `res/xml/` has backup rules, data extraction rules, network security config, shortcuts.

### ✅ Tests
- 20 unit-test files + module tests present, all pure-JVM, covering DSP/lyrics/import/skin.

### ✅ Build script & CI
- `build.sh` is syntactically sound (arg parsing, trap cleanup, sed version bumps,
  `gh release` publish flow).
- `.github/workflows/build.yml` correctly sets up JDK 21 + debug keystore + Gradle.

## 4. Verdict

| Aspect | Result |
|---|---|
| Full Gradle build | ⚠️ **Not possible** — no JDK/Android SDK/Gradle access in sandbox |
| Static coherence (config, imports, resources, manifest) | ✅ **Pass** — internally consistent |
| Architecture soundness | ✅ **Sound** — clean layering, isolated providers, tested core |
| Build reproducibility (on a dev machine / CI) | ✅ **Should work** — standard AGP 9 + JDK 21 setup, documented in CONTRIBUTING |

To build it yourself (needs a normal dev machine or the GitHub CI):
```bash
# 1. Install JDK 21 + Android Studio (or cmdline-tools + SDK 36 + NDK 27)
# 2. (Optional) create local.properties with LASTFM_* / LIBREFM_* / DISCORD_CLIENT_ID
./build.sh --debug          # fastest; or --release -a for all ABIs
./gradlew testDebugUnitTest # run the unit tests
```

The app is **architecturally sound and internally consistent**; the only thing this
sandbox could not do is execute the Android toolchain.
