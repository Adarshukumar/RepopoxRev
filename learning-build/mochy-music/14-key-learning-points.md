# 14 — Key Learning Points

The big engineering ideas worth taking away from studying Mochy Music.

## 1. A tiny team can build a premium music player by speaking a private API directly

Mochy has **no backend**. It talks to YouTube Music's private InnerTube API from the device,
solves YouTube's PoToken/BotGuard challenge **on-device** (WebView + a JS solver), and uses
the device's own Widevine CDM for DASH. This is why it's open-source and free — there's
nothing to pay for.

**Lesson:** Reverse-engineering a private API + on-device token solving removes the
infrastructure cost entirely. The hard part is keeping up with API changes, not scaling.

## 2. Isolate the "content backend" from the app

Every external service is a separate Gradle module (`providers/*`) exposing a stable object
(`YouTube`, `KuGou`, `Genius`, …). The app never touches HTTP directly for content.

**Lesson:** This makes the app testable, lets you swap a provider, and keeps protocol
changes from rippling into the UI. The module boundary is the unit of encapsulation.

## 3. The "audio pass" is the performance core

The custom audio pipeline (Media3 `AudioProcessor` chain + native C++ USB/AAudio pass)
is where bit-perfect/Hi-Res output, crossfade, EQ, and Audio Lab all live. Per-frame work
goes in `drawBehind`/`graphicsLayer`, never composition.

**Lesson:** For an audio app, the audio pipeline *is* the product. Keeping it on the audio
render thread and doing per-frame UI work in draw phases (not composition) is what keeps
it smooth.

## 4. A plugin system with a real trust boundary

The module system is elegantly split by **what runs**:
- Looks / web modules / links / zips = **descriptions** (JSON + stream URLs + look values) —
  nobody has to be trusted; nothing runs in-process.
- Code modules / module apps = **code** — only loaded if signed with Mochy's key + same
  API version.

**Lesson:** Design your extension system so the *common* case is safe by construction
(data, not code), and only the rare case needs a trust check. Whitelist what a "look" may
change so it can restyle but never reach accounts/network/playback.

## 5. `profileable` build type = measure what users actually get

A debug Compose build is several× slower per frame than release. Mochy adds a `profileable`
build type that's **release-identical** (R8 on, `isDebuggable=false`) but `isProfileable=true`
so Perfetto/Studio can attach.

**Lesson:** If you care about performance, your *measurement* build must match your *shipping*
build. Otherwise every perf impression is against the wrong binary.

## 6. Many app identities from one codebase

Flavor (`foss`/`fdroid`/`gms`) × build-type (`debug`/`release`/`profileable`/`fast`) ×
ABI (`universal`/`arm64`/`armeabi`/`x86`/`x86_64`) → dozens of APKs, each with its own
`BuildConfig` switches (`CAST_AVAILABLE`, `ARCHITECTURE`, `IS_NIGHTLY`).

**Lesson:** Build-time feature flags (flavors + `BuildConfig`) let one codebase serve
F-Droid, FOSS, and Play variants without runtime feature-check spaghetti. The F-Droid build
even reuses the `foss` source set.

## 7. Settings as typed DataStore keys, reacted to via flows

Hundreds of typed preference keys; `App` reads them at boot and launches flows that react
to changes (proxy change → rotate guest session; content country change → `YouTube.locale`).

**Lesson:** Typed keys + reactive flows beat scattered `getSharedPreferences` calls.
A single `observeSettingsChanges()` is the app's settings wiring.

## 8. Composition locals as a service locator

`LocalDatabase`, `LocalPlayerConnection`, `LocalDownloadUtil`, `LocalArtworkColor`, … let
deep UI components reach services without prop-drilling or global singletons in every
composable.

**Lesson:** Composition locals are the Compose-idiomatic service locator — perfect for
ambient services the whole tree needs.

## 9. Shared-element cover transitions, done right

Covers grow out of the tapped card; the source screen recedes on the *same clock* (a spring
going out, an ease coming back). A tiny threshold stops the spring snapping the last 1%.

**Lesson:** Shared-element transitions are what make a UI feel premium. The trick is
synchronising the source's exit with the target's enter using one animation clock.

## 10. Dark-only, dynamic color from the album

The light palette was never finished, so it's pinned to one switch (`ForceDarkTheme = true`)
rather than decided per call site. Color comes from the album art via `materialkolor`.

**Lesson:** Commit early to a visual direction (dark-only) rather than half-finishing two
themes. Derive accent color from content, not the wallpaper.

## 11. Test the pure logic, verify the rest on device

DSP math, lyrics sync, import matching, skin parsing are unit-tested (pure Kotlin). UI,
network, audio pass are verified on real hardware with before/after screenshots.

**Lesson:** Put the testable core in plain Kotlin (no Android), test it on the JVM, and
reserve device testing for what genuinely needs a device.

## 12. Privacy as a design constraint

No account to listen; sign-in opens the real page; only session cookies stored; FOSS/F-Droid
builds have no Google; code modules must be signed.

**Lesson:** Build the privacy model *into* the architecture (in-app real-page sign-in,
on-device tokens, signed code) rather than bolting it on.

## 13. Resilience details everywhere

- `TransferRestore.applyPending` before `super.onCreate` — restore a crashed import.
- PoToken pre-warm waits for the intro — don't compete with first frames.
- Local artwork repair waits for the intro — don't share the disk.
- One-shot lyrics wipe on a version bump — refetch fresh, keep user edits.
- `getPlayerSafe()` — graceful handling if the player isn't ready yet.

**Lesson:** The difference between "works on the happy path" and "feels solid" is dozens of
small ordering/resilience decisions. Read the comments — they explain *why*.

## 14. Commit messages & culture

Short, human, sometimes playful ("son 😭🥀", "stop the ai slop issue report pls"). The
CONTRIBUTING guide is light but clear: one fix per PR, match surrounding code, check older
devices, don't trade features for speed, test on a real device.

**Lesson:** A small, healthy OSS project can have a light process and still ship a
141k-LOC UI. Scale the process to the team, not the codebase.
