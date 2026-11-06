# 07 — Verification report

Clone verified: `../Mochy-Music/` at upstream commit
`8dd8f3b0620473ab4c26466f3b6b8b7d08aaab12` (2026-11-06, "Revise Mochy Music description in README").

## What could and could not be run

**Could not:** a real Gradle build. That needs JDK 21, Android SDK 36, NDK 27.0.12077973,
CMake 3.22.1, and network access to `google()`, `mavenCentral()`, `jitpack.io` and
`maven.aliyun.com`. The sandbox has no JDK, no Android SDK, and an egress allowlist of six
hosts — none of which are those. It also has 2 vCPU / 4 GB RAM, below what this build asks for
(`org.gradle.jvmargs=-Xmx6144M`).

Every one of those was checked rather than assumed:

```
dl.google.com          -> SSL_ERROR_SYSCALL (blocked)
repo.maven.apache.org  -> SSL_ERROR_SYSCALL (blocked)
services.gradle.org    -> SSL_ERROR_SYSCALL (blocked)
jitpack.io             -> SSL_ERROR_SYSCALL (blocked)
maven.aliyun.com       -> SSL_ERROR_SYSCALL (blocked)
java / javac / gradle  -> command not found
ANDROID_HOME           -> unset
```

**Could:** decide a great deal from the sources alone. `tools/verify.py` runs 17 checks over
1,036 Kotlin files, 763 XML files, 22 Gradle modules and 45 JSON files. It is not a lint
config — `tools/kotlin_lex.py` is a real Kotlin lexer and `tools/kotlin_index.py` builds a
symbol index of 27,872 declarations, so existence questions are answered from the code.

```bash
python3 learning-build/tools/verify.py Mochy-Music      # full report + JSON
python3 learning-build/tools/kotlin_lex.py              # lexer self-test (16 cases)
```

## Result

**28 PASS · 6 INFO · 4 WARN · 4 FAIL**

### PASS

| check | result |
|---|---|
| gradle-modules | all 22 `include()`d modules exist with a build script; every `project(":…")` reference is declared |
| version-catalog | all 68 library, 5 plugin and 2 version accessors resolve in `libs.versions.toml` |
| manifest | all activity/service/receiver/provider classes resolve; all `@resource` refs resolve; 2 authorities unique; exactly 1 of 50 launcher aliases enabled |
| resources | all 763 XML files well-formed; all 2,897 `@type/name` and `R.type.name` references resolve; no duplicates in default `values/`; all adaptive-icon layers resolve |
| kotlin | all 1,036 files lexically balanced (deepest nesting 19 levels); no duplicate declarations within any module+source-set; no BOM/tabs/CRLF/empty files |
| room | all 23 entities + 3 views exist; `AllMigrations` and the `MIGRATION_*` definitions agree exactly |
| tests | all 27 unit-test files resolve every internal import; 177 `@Test` methods |
| module-deps | all 6,085 internal cross-module imports are backed by a declared `project(":…")` dependency |
| ci | `assembleUniversalGmsDebug` is valid in the 60-variant matrix; no tabs in YAML |
| prereqs | proguard files, native build (`CMakeLists.txt` + both `.cpp` sources), executable `gradlew` + wrapper jar all present; every keystore a build type actually uses is guarded |
| buildconfig | all 13 `BuildConfig` fields read by Kotlin are defined by Gradle |
| json-docs | all 45 JSON files parse (10 config/asset, 35 Room schema exports) |
| secrets | no AWS/GitHub/Slack/Stripe keys, PEM private keys, bearer literals or password assignments in any of 1,859 source/config/script files |
| flavors | 2 dimensions × 4 build types = 60 variants; `fdroid` correctly reuses `src/foss` |
| app-icons | the manifest's 50 launcher aliases are exactly the set `AppIcons.kt` generates |
| navigation | 204 of 206 `navigate()` call sites resolve to a declared route |

### FAIL — four real defects

---

#### F1. Room migration graph is disconnected — upgrades from v≤34 silently delete the library

**Severity: critical (data loss).** `db/MusicDatabase.kt`, `importer/transfer/TransferImport.kt:259`

`MusicDatabaseVersion = 42` and `AllMigrations` holds 11 migrations:

```
1→2, 21→24, 22→24, 24→25, 35→36, 36→37, 37→38, 38→39, 39→40, 40→41, 41→42
```

Nothing starts at 2–20 or 25–34. **34 of 42 schema versions have no path to 42.** Only 35–42
work. The builder also specifies:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

so an unmatched upgrade does not error — it **drops every table**. Songs, playlists, artists,
albums, play counts, lyrics, history: gone, with no prompt and no log the user would see.

This is not a corner case about ancient installs. The predecessor package `com.music.vivi`
shipped schema **v34** (`app/schemas/com.music.vivi.db.InternalDatabase/` holds `1.json … 34.json`;
the renamed `com.mochyy.mochymusic.db.InternalDatabase/` holds only `34.json`), and migrations
resume at `35_36`. So the version the fork inherited is precisely the version with no path.

There is a second, user-facing consequence. `SetupWizardScreen.kt` offers *"previous data: a
whole export from the old app or another phone"* → `TransferViewModel.importFrom` →
`TransferImporter`, which guards with:

```kotlin
if (!hasMigrationPath(manifest.databaseVersion, MusicDatabaseVersion)) {
    throw TransferException("This export is too old for Mochy to read. " +
                            "Update your old app, export again and import that.")
}
```

`hasMigrationPath(34, 42)` returns **false**. The onboarding flow built to bring users over
from the old app rejects the old app's own last schema version, and the error message advises
something impossible — v34 *is* the latest the predecessor had.

The team already knows this failure mode. The comment above `AllMigrations` says:

> The app opens it through the Hilt module; when that copy of the list fell behind this one, an
> upgrade found no path to the new version and `fallbackToDestructiveMigration` emptied the
> whole library. Add new migrations here and nowhere else.

They fixed the *symptom* (two divergent lists) rather than the *cause* (nothing asserts the
graph is connected). `exportSchema = false` compounds it: no schema JSON is written even though
`ksp { arg("room.schemaLocation", …) }` is still configured, so `app/schemas` froze at v34 and
Room's `MigrationTestHelper` — the usual mechanism that catches this — cannot be used.

*Fix:* add the missing migrations (at minimum `34→35`, ideally `2→21` and `25→35`), and add a
unit test that walks `hasMigrationPath(v, MusicDatabaseVersion)` for every `v` in
`1 until MusicDatabaseVersion`. Consider removing `fallbackToDestructiveMigration` from release
builds so a future gap crashes in testing instead of eating a library in production.

---

#### F2. Two navigation routes are declared nowhere — tapping them throws

**Severity: high (user-visible crash).** `ui/screens/HomeScreen.kt:1679`, `ui/screens/ExploreScreen.kt:322`

```kotlin
navController.navigate("mood_and_genres")   // HomeScreen.kt:1679
navController.navigate("new_release")       // ExploreScreen.kt:322
```

Neither string is registered by `composable(`, `coverSourceComposable(` or
`navigationComposable(`, and neither appears as a `route =` constant or `val …Route` anywhere in
the app. Navigation-Compose resolves routes at runtime, so both compile cleanly and throw
`IllegalArgumentException` on tap.

Both are trivially reachable: "Mood and Genres" from a Home shelf, "New release albums" from
the Explore screen.

The surrounding work is all finished, which suggests the screens were wired and then lost.
`MoodAndGenresViewModel` exists and is already used by `SearchScreen`; `NewReleaseViewModel`
exists; InnerTube exposes `YouTube.moodAndGenres()` and `YouTube.newReleaseAlbums()`; and
`R.string.mood_and_genres` / `R.string.new_release_albums` are translated into all 83 locales.
Only the route registration is missing.

*Fix:* register both in `NavigationBuilder.kt`, or repoint the calls at the existing
`SectionDetailScreen` / search routes if that was the intent.

---

#### F3. `CrashActivity` is declared in the manifest but has no implementation

**Severity: medium (dead safety net).** `app/src/main/AndroidManifest.xml:74`

```xml
<activity
    android:name=".ui.screens.CrashActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.mochy"
    android:excludeFromRecents="true"
    android:process=":crash" />
```

`com.mochyy.mochymusic.ui.screens.CrashActivity` exists nowhere in the sources — a
whole-repository search for the string `CrashActivity` returns **this one manifest line and
nothing else**. There is no `ui/screens/CrashActivity.kt`, and `ui/screens/` contains only
`AlbumScreen`, `BrowseScreen`, `ComingSoonScreen`, `ExploreScreen`, `HistoryScreen`,
`HomeScreen`, `NavigationBuilder`, `NetworkReload`, `Screens`, `SectionDetailScreen`,
`YouTubeBrowseScreen`.

AGP does not resolve component classes at build time, so this **builds and passes CI**. It
fails only at runtime: anything that starts it gets `ActivityNotFoundException`.

The `android:process=":crash"` attribute is the tell — a separate process so the crash screen
survives the dying main process. It was clearly intended to be a real crash reporter. What
ships instead is `utils/CrashHandler.kt`, which writes `files/last_crash.txt`, logs to
`Log.e("MochyCrash", …)` and then kills the process:

```kotlin
runCatching { java.io.File(applicationContext.filesDir, LAST_CRASH_FILE).writeText(crashLog) }
android.os.Process.killProcess(android.os.Process.myPid())
exitProcess(1)
```

Its companion object still declares `const val EXTRA_CRASH_LOG = "crash_log"` — an intent extra
with no Activity to receive it. So the user-visible behaviour is: the app dies with no
explanation, and the log sits on disk until someone runs
`adb shell run-as <package> cat files/last_crash.txt`.

Note the CONTRIBUTING guidance asks bug reporters for `adb logcat` output; this is the
machinery that was meant to make that easier, left half-built.

*Fix:* either implement `CrashActivity` to read `EXTRA_CRASH_LOG` and launch it from
`CrashHandler` before killing the process, or delete the manifest entry and the orphaned extra.

---

#### F4. `build.sh` is macOS-only — the documented build path fails on Linux and on CI

**Severity: medium (contributor onboarding).** `build.sh` lines 138, 145, 153, 154, 155, 156

Six uses of BSD `sed -i ''`:

```bash
sed -i '' "s/versionName = \".*\"/versionName = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" app/build.gradle.kts
```

GNU sed parses `-i` (no argument), then takes `''` as the *script* — an empty program — and
treats the real expression and the target file as *input filenames*. It prints the file
unchanged and exits non-zero on the missing file. Under the script's own `set -e` (line 2),
that aborts the build immediately.

Line 145 is unconditional: it runs on every invocation that does not pass `-c`. So
`./build.sh --debug` — the exact command CONTRIBUTING.md tells new contributors to run, twice —
fails on Linux, on WSL, and on the project's own `ubuntu-latest` CI runners. Only macOS works.

The rest of the script is careful and portable, which makes this look like an oversight from a
macOS-only maintainer workflow rather than a design choice.

*Fix:* `sed -i.bak "…" file && rm -f file.bak`, or `perl -pi -e`, or branch on `uname`.

---

### WARN — four, all minor

**W1. `exportSchema = false` with a live schema location.** Covered under F1. 35 schema exports
exist but stop at v34 while the DB is at v42; the `ksp` argument is dead configuration.

**W2. Nine orphan translation keys.** `values-in/player_bedside_strings.xml` declares
`bedside_clock_caption`, `bedside_face_analog`, `bedside_face_analog_caption`,
`bedside_face_big`, `bedside_face_big_caption`, `bedside_face_cover`,
`bedside_face_cover_caption`, `bedside_face_stacked`, `bedside_face_stacked_caption` — none of
which exist in default `values/`.

This would be a `Resources$NotFoundException` crash **if anything referenced them**. Nothing
does: no `R.string.<key>` in Kotlin and no `@string/<key>` in any layout. They are dead
translations of a bedside-clock feature whose strings were later renamed or removed — Android
lint's `ExtraTranslation` warning, not a defect. The harness downgrades this from FAIL to WARN
precisely because it checks whether the keys are referenced.

**W3. Seven dangling `docs/*.md` references.** Code comments cite
`docs/notes/modules.md`, `docs/notes/audio-output.md`, `docs/notes/cast.md`,
`docs/notes/performance.md`, `docs/notes/settings.md`, `docs/notes/update.md` and
`docs/popup-sheet.skill.md`. `.gitignore` contains `docs/`, so upstream keeps them locally and
every fresh clone inherits broken pointers — including `modules/catalog.json`, whose `"about"`
field directs readers to `docs/notes/modules.md` to learn what a module link must answer. That
is the module system's only specification, and it is not in the repository.

**W4. Fourteen files where the package differs from the directory path.** Mostly the
`com.music.vivi` → `com.mochyy.mochymusic` rename: `providers/canvas/src/main/kotlin/com/music/vivi/canvas/*.kt`
declare `package com.mochyy.mochymusic.canvas`. Legal Kotlin, and the harness verifies all
6,085 cross-module imports still resolve, so nothing is broken — but navigation and IDE search
by path will mislead. One unrelated case: `providers/innertube/.../models/PlaylistDeleteBody.kt`
declares `package com.music.innertube.models.body` while sitting in `models/`.

### INFO — six observations

- **17 permissions** declared, all justified in comments (media playback, foreground services,
  Bluetooth, `READ_MEDIA_AUDIO` for local library scan, `MODIFY_AUDIO_SETTINGS` for USB DAC,
  `RECORD_AUDIO` for music recognition marked `required="false"`, `REQUEST_INSTALL_PACKAGES`
  for self-update — stripped in the `fdroid` flavor).
- **`PoTokenWebView.kt:303` embeds a YouTube web-client API key** (`AIza…`). This is *not* a
  leak and should not be "fixed": it is the public key YouTube's own browser player ships,
  quota-scoped to that client id, and every InnerTube implementation carries the same
  constant. It cannot be a build-time secret because the app must work with no server and no
  user-supplied key. Classified INFO with that reasoning rather than reported as a credential.
- **Real credentials are correctly externalised** — Last.fm, Libre.fm and Discord all come from
  `local.properties` or CI secrets and default to empty, with a clever MD5-derived stand-in for
  Libre.fm so the feature degrades instead of breaking.
- **`CAST_AVAILABLE`** is defined but never read (the `gms` flavor gates Cast by source set
  instead of by flag).
- **Three `[libraries]` catalog entries are unreferenced**: `compose-material-icons-core`,
  `compose-material-icons-extended` (the app declares
  `androidx.compose.material:material-icons-extended:1.7.8` as a direct coordinate instead,
  bypassing the catalog), and `json`.
- **`modules/catalog.json` ships empty** (`"modules": []`), so Settings → Modules → "Find more
  sources" has nothing to offer.

## How the harness was itself validated

Worth recording, because a verification tool that lies is worse than none. The first run
reported 14 FAILs. Most were the *checker's* bugs, and each was fixed by finding a real
construct that broke it:

| false positive | root cause | fix |
|---|---|---|
| "unbalanced brackets" in 286 files | the lexer popped a code frame on *any* `}`, so every function body ended a template | per-frame brace depth, with `-1` as a file-scope sentinel |
| …still 10 files | `"${if (b) "y" else "z"}"` — a string inside a template was read as ending the outer string | a `${` frame now **replaces** its string frame and carries a resume token |
| …still 4 files | `` fun `the artist's own copy`() `` — the apostrophe opened a char literal | a backtick-identifier state |
| …still 2 files | `Regex("""content="([^"]+)"""")` — a raw string whose content ends in a quote | count the whole run of quotes: 3 close, extras were content |
| `${…}` left brackets unmatched | the mask blanked the `{` of `${` but kept the `}` | keep `{` — it is a real code bracket |
| "14 missing types" (`ReverbParams`, `ImportSessionSong`, …) | resolved by filename; Kotlin puts many types per file | a real symbol index over 27,872 declarations |
| "3 duplicate classes" (`CastButton` …) | `foss/` and `gms/` are mutually exclusive flavors | scope duplicates to module+source-set, exempt one-per-flavor |
| "23 phantom flavors" | scanned the whole build script for `create("x")`, catching signing configs and build types | bound the scan to the `productFlavors { }` block |
| `R.string.ok` missing ×15 | `\bR\.` matched inside `android.R.string.ok` | `(?<![\w.])R\.` negative lookbehind |
| `R.style.Theme_App_Starting` missing | R field names turn `.` into `_` | normalise `Theme_App_Starting` → `Theme.App.Starting` |
| `Theme.AppCompat.Light.NoActionBar` missing | ships inside the appcompat AAR | allowlist library-provided resource families |
| "release keystore missing → cannot build" | ignored the `hasReleaseSigning` guard | distinguish guarded from unconditional signing configs |
| "72 include()d modules missing" | the masked build script had blanked the string literals | `strip_line_comments()` keeps strings, drops comments |
| `navigate("appearance_settings?highlight=x")` | it appears only in a *comment* explaining why that form would fail | strip comments before matching call sites |
| `"history"` reported missing | registered via the wrapper `coverSourceComposable(` | match all three registration helpers |
| `"developer_space"` reported missing | declared with optional args, `?person={person}` | accept the query-free prefix |

`tools/kotlin_lex.py` now carries a 16-case self-test covering each construct above, so these
classes of false positive stay fixed. The four remaining FAILs were each re-confirmed by hand
against the source — full-repository greps for `CrashActivity`, `mood_and_genres`,
`new_release`, `MIGRATION_`, and a line-by-line read of `build.sh` and the signing block.

## Reproducing

```bash
cd /home/user/RepopoxRev
python3 learning-build/tools/verify.py Mochy-Music
# exit code 1 (FAILs present); machine-readable copy in learning-build/reports/verify-results.json

# confirm F1 by hand
grep -n "val MIGRATION_" Mochy-Music/app/src/main/kotlin/com/mochyy/mochymusic/db/MusicDatabase.kt
# confirm F2
grep -rn "mood_and_genres\|new_release" Mochy-Music --include=*.kt | grep -v values-
# confirm F3
grep -rn "CrashActivity" Mochy-Music
# confirm F4
grep -n "sed -i ''" Mochy-Music/build.sh
```
