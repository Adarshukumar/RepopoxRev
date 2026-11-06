# 08 — Key learnings

What is worth taking from this codebase, and what is worth avoiding.

---

## Patterns worth stealing

### 1. Execute the target's own JavaScript instead of reimplementing it

The app faces three moving targets: BotGuard PoToken attestation, a rotating signature cipher,
and the SABR `N`-transform. Each is solved the same way — run *YouTube's own* JavaScript in a
sandboxed WebView and bridge the result back to Kotlin.

```
utils/potoken/PoTokenWebView.kt      @JavascriptInterface bridge
utils/cipher/CipherWebView.kt        PlayerJsFetcher -> FunctionNameExtractor -> execute
utils/sabr/EjsNTransformSolver.kt    WebChromeClient console capture + suspendCancellableCoroutine
```

Reimplementing any of them would break on the next YouTube deploy. This way, only the *harness*
is maintained. The trade is honest: fragile in a different dimension (WebView availability,
timing, memory) and slow on cold start — which is exactly why `App.kt` schedules BotGuard
2.5 s after the launch intro rather than inside the playback resolver:

> Without a PoToken YouTube serves only a ~32 second preview window, and paying the engine's
> cold start inside the playback resolver stalls the first track instead.

**Lesson:** when a dependency is adversarial and changes without notice, don't reimplement it —
execute it, and move the cost off the critical path.

### 2. Degrade instead of breaking when a secret is missing

Libre.fm accepts any 32-character key and never validates the secret. Rather than disable the
feature for forks and secretless CI runs, the build derives a deterministic stand-in:

```kotlin
fun libreFmStandIn(name: String) = MessageDigest.getInstance("MD5")
    .digest("$applicationId.librefm.$name".toByteArray()).joinToString("") { "%02x".format(it) }
```

Same instinct in `DISCORD_CLIENT_ID`: when unset, `manifestPlaceholders["discordRedirectScheme"]`
becomes `mochy-discord-unset`, so the OAuth page shows a friendly explanation instead of
registering a deep link that can never return.

And `hasReleaseSigning` — release signing falls back to the debug keystore locally, with a
comment explaining the real cost of the old behaviour:

> a debug Compose build is several times slower per frame than release, so all local performance
> impressions were being formed against the wrong binary.

**Lesson:** a missing credential should cost a feature, not a build. And make the degraded path
*observable*, not silent.

### 3. Capability-limited extension points

`LookModule` lets a third party retheme the entire app, but it can only set keys in an explicit
allowlist:

> the only settings a look may change, so a module can restyle mochy but never touch accounts,
> network or playback

Anything else in the map is ignored. The three module trust tiers follow the same logic:
data-only modules (JSON, stream links, look values — nobody has to be trusted), sandboxed
JavaScript (internet only, no phone access), and native code (signature-checked against Mochy's
own key). The `ModuleHost` surface is four members: `storage`, `http`, `log`, `folder`.

**Lesson:** the safest plugin API is one where the *shape* of the interface enforces the policy,
so a malicious or buggy module has nothing to call.

### 4. `isProfileable` — a fourth build type for honest measurement

```
debug       debuggable, no R8            fast to build, unrepresentative perf
release     not debuggable, R8           real perf, profiler locked out
fast        not debuggable, no R8        release-grade ART, debug-grade build time
profileable not debuggable, R8, profileable   release perf WITH Perfetto attached
```

`fast` is the clever one: `isDebuggable = false` is what makes ART use the baseline profile and
skip JIT deopt, and dropping R8 keeps the build quick — so you get release-like runtime at
debug-like iteration speed. `profileable` adds `runtime-tracing` and
`tracing-perfetto-binary` so every composable is named in system traces:

> the profiling build names every composable in system traces, so a slow frame says what it was

Both use `matchingFallbacks` because the library modules only publish `debug`/`release`.

**Lesson:** if you are measuring performance in a debug Compose build, you are measuring
something that does not ship. Make the honest variant cheap to build.

### 5. Comments that explain why

CONTRIBUTING.md mandates it with good and bad examples, and the codebase follows through. The
comments that earn their place:

```kotlin
// wait until the cover has loaded, otherwise the open spring jumps
// paused while the player covers home, so the bars stop eating frames
// aaudio is opened at runtime, so only the log and the loader are linked;
//   that keeps the library loadable on android 7
// 16 kb pages on android 15 phones need the segments aligned to them
// written last: without it the next start throws the half-made folder away instead of using it
// NetEase, QQ Music and KuGou near the end: they are Chinese services, and LyricsHelper only
//   believes them first-hand for Chinese songs
// MODULE kept last so the others keep their places   (ordinals are persisted)
```

Each records a constraint that is invisible in the code and would otherwise be rediscovered by
breaking it. Contrast the style it explicitly rejects — a javadoc block restating the signature.

### 6. Tolerant deserialisation of user-editable orderings

```kotlin
fun deserializeProviderOrder(orderString: String): List<String> {
    if (orderString.isBlank()) return getDefaultProviderOrder()
    val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
    val newModules = moduleProviders.keys.filter { it !in saved }
    return newModules + saved + getDefaultProviderOrder().filter { it !in saved && it !in newModules }
}
```

Three separate robustness properties in four lines: a persisted order survives providers being
*removed* (filtered out), *added* (appended in default order), and *re-enabled* (a newly enabled
module jumps to the front, because that is why the user enabled it). The same care appears in
`SourceModules.readOrder()` with its one-time `order_monochrome_first` promotion, and in
`reorderVisible()` keeping hidden modules' positions so toggling does not reshuffle.

### 7. Staging + marker for crash-safe file operations

`TransferImport` unpacks to a staging directory and writes `ReadyMarker` **last**:

> without it the next start throws the half-made folder away instead of using it

Combined with zip-slip defence (`if (!target.canonicalPath.startsWith(root)) continue`) and
`TransferRestore.applyPending(this)` called in `App.onCreate()` *before* `super.onCreate()` —
because that is where Hilt builds the database, so the restore must land first.

### 8. An explicit state machine for anything the user watches

```kotlin
sealed interface ProviderSwitchState {
    data object Idle
    data class Working(val providerId: String, val attempt: Int, val of: Int)
    data class Failed(val providerId: String)
    data class Done(val providerId: String)
}
```

`Working(attempt, of)` carries the progress denominator, so the UI can render "trying 2 of 4"
rather than an indeterminate spinner. And playback is *held*, not stopped, while a new source
resolves (`holdPlayback()` / `resumePlayback()`), so switching sources does not produce an
audible gap.

### 9. Distinguishing "doesn't have it" from "couldn't be asked"

```kotlin
sealed interface ResolveResult {
    data class Found(val stream: SourceStream)
    data class NotFound(val reason: String)      // the service doesn't have this song
    data class Unavailable(val reason: String)   // signed out, offline, region-blocked
}
```

The provider walk can make different decisions for each: `NotFound` means move on permanently,
`Unavailable` means move on but retry later. Collapsing these into one `null` is a common design
mistake that produces inexplicable "this song won't play" bugs.

### 10. Native code that still loads on old devices

```cmake
target_link_libraries(mochypass log dl)                          # AAudio dlopened, not linked
target_link_options(mochypass PRIVATE "-Wl,-z,max-page-size=16384")   # Android 15 16 KB pages
```

Linking only `log` and `dl` means one `.so` loads on Android 7, where AAudio does not exist.
The 16 KB alignment is forward-compatibility for Android 15 hardware. Both are one-line details
that are easy to miss and expensive to discover in production.

---

## Anti-patterns to avoid

### A. `fallbackToDestructiveMigration` on a database holding user data

The single worst line in the codebase:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

It converts every migration bug from a loud crash into **silent total data loss**. Combined
with the disconnected graph (F1), 34 of 42 schema versions upgrade by deleting the library.

The team was already bitten by this once and documented it:

> when that copy of the list fell behind this one, an upgrade found no path to the new version
> and `fallbackToDestructiveMigration` emptied the whole library

They fixed the symptom — one canonical `AllMigrations` — but not the cause. Nothing asserts the
graph is connected, and `exportSchema = false` disables the mechanism (`MigrationTestHelper`)
that normally would.

**Avoid:** destructive fallback on irreplaceable data. If a migration is missing, crash. A crash
is recoverable; a dropped library is not.

### B. Runtime-resolved contracts with no compile-time or test-time guard

Three separate contracts in this app resolve **at runtime by string**, and all three have
defects or near-misses:

| contract | resolution | status |
|---|---|---|
| navigation routes | `navigate("string")` | **2 of 206 broken** (F2) |
| manifest components | `android:name=".Class"` | **1 broken** (F3) |
| launcher aliases | `setComponentEnabledSetting(alias)` | 50/50 correct — verified |

None of these produce a compile error. `navigate("mood_and_genres")` and
`<activity android:name=".ui.screens.CrashActivity">` both build, pass CI, and ship. They fail
only when a user taps.

The icon contract happens to be correct, and it is worth noting *why*: `AppIcons.kt` generates
alias names programmatically from two enums, and the manifest was written to match. That is
still fragile — nothing enforces it — but the structure makes drift less likely than 50
hand-written strings on both sides.

**Avoid:** string-keyed runtime dispatch without a test that enumerates every key. Both broken
contracts here would be caught by a dozen lines of unit test, and neither needs an emulator:
assert every `navigate()` literal has a registered route; assert every manifest class resolves.
That is precisely what `tools/verify.py` does statically.

### C. A build entry point that only works on the maintainer's machine

CONTRIBUTING.md tells new contributors, twice, to run `./build.sh --debug`. On Linux — and on
the project's own `ubuntu-latest` CI — BSD `sed -i ''` makes that abort under `set -e` (F4).
CI never exercises `build.sh`; it calls `./gradlew` directly, so the breakage is invisible to
the pipeline and only surfaces for humans.

**Avoid:** letting your documented onboarding path diverge from your CI path. If CI does not run
the script contributors are told to run, the script will rot.

### D. `.gitignore`-ing documentation the code depends on

`.gitignore` contains `docs/`, while 20 source files and `modules/catalog.json` cite
`docs/notes/modules.md`, `docs/notes/audio-output.md`, `docs/popup-sheet.skill.md` and four
others. `modules/catalog.json`'s `"about"` field says:

> docs/notes/modules.md says what that link has to answer

That is the module system's only specification, and a fresh clone does not have it. The same
`.gitignore` also excludes `CLAUDE.md`, `AGENTS.md`, `plan.md` and `.claude/` — understandable
for scratch files, but `docs/` is referenced by shipped code.

**Avoid:** ignoring any path that a committed file points at. Either commit the docs or remove
the references.

### E. A 4,980-line service

`MusicService.kt` is the largest file in the repo and holds the player, the session, the library,
the DSP wiring, the provider walk, the retry policy, the scrobbling, the widget updates, the
call watcher, the sleep timer and the audio-device routing. It works, and its concurrency is
thoughtful (`ConcurrentHashMap` for shared state, `WeakHashMap<Player, …>` so discarded players
cannot leak their processors) — but it is where every playback bug will land, and no single
change to it is small.

Partial extraction has already begun (`ProviderSwitch`, `CrossmixDirector`, `SleepTimer`,
`CallWatcher`, `TransportFadingPlayer` are separate collaborators reached through small `Host`
interfaces), which is the right direction.

### F. Letting a schema export freeze

`exportSchema = false` while `ksp { arg("room.schemaLocation", …) }` is still configured. The
argument is now dead, `app/schemas` stops at v34 while the DB is at v42, and the standard
migration test becomes impossible to write. Two schema directories also survive the package
rename (`com.music.vivi.db.InternalDatabase` with 34 exports, `com.mochyy.mochymusic.db.InternalDatabase`
with one), so the history is split across two namespaces.

---

## What the verification actually established

Reading a codebase tells you what it *intends*. Checking it tells you what it *does*. Across
1,036 Kotlin files and 763 XML files, the app is in notably good structural shape:

- every one of 6,085 cross-module imports is backed by a declared Gradle dependency
- all 2,897 resource references resolve; all 763 XML files are well-formed
- all 22 Gradle modules exist; all 75 version-catalog accessors resolve
- all 23 Room entities and 3 views exist; the migration list matches its definitions exactly
- all 177 unit-test imports resolve
- no leaked credentials, no duplicate resources, no duplicate declarations
- the 50-way launcher-icon contract between manifest and code is exactly satisfied

and yet four real defects survived, **all in categories a compiler cannot see**: a graph that is
disconnected, two strings that resolve at runtime, one manifest entry pointing at nothing, and
one shell script that assumes BSD userland.

That is the general lesson. The failures are not in the code's *structure* — they are in its
*contracts*, and contracts that are checked only at runtime are checked only by users.
