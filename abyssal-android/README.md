# ABYSSAL for Android

The [ABYSSAL](../abyssal) descent, packaged as an installable APK.

This is a **wrapper, not a rewrite** — a single fullscreen `WebView` pointed at the
web app, which CI copies in from `../abyssal` at build time. Rewriting a
canvas-and-Web-Audio piece natively would mean two implementations of the same
descent, the same physics and the same 28 specimens, and they would drift apart. Here
there is one.

```
app/src/main/java/dev/abyssal/app/MainActivity.java   the whole Android side, ~200 lines
app/src/main/assets/abyssal/                          copied in by CI; not committed
```

---

## Get the APK

The APK is built by GitHub Actions and attached to the release:

**https://github.com/Adarshukumar/RepopoxRev/releases/tag/v1.0.0-abyssal**

Download `abyssal-android-1.0.0-debug.apk` and install it. You will need to allow
installation from unknown sources; it is signed with the standard Android debug key.

It is also available as a workflow artifact (`abyssal-android-apk`) on any run of the
**Build APK** workflow.

| | |
| --- | --- |
| Package | `dev.abyssal.app` |
| Version | `1.0.0` (versionCode 1) |
| Min Android | 8.0 (API 26) |
| Target | 14 (API 34) |
| Permissions | **none** |
| Dependencies | **none** — framework only |

---

## No permissions, and that is a property not a promise

The manifest declares **no `<uses-permission>` element at all** — in particular no
`INTERNET`. The installed APK has no mechanism by which it could make a network
request. It is offline because it cannot be otherwise, not because it says it will be.

There is also no analytics, no advertising ID, no storage access and no location. The
water, the light, the animals and the sound are all generated at runtime from data
compiled into the APK.

`android:usesCleartextTraffic="false"` is set as well, and the WebView runs with
`allowFileAccess`, `allowFileAccessFromFileURLs` and
`allowUniversalAccessFromFileURLs` all **false**. `file:///android_asset/` stays
reachable regardless — Android keeps assets and resources available to a WebView even
with file-system access off — so the app loses nothing by closing those.

---

## Three things the wrapper has to get right

A default `WebView` gets all three of these wrong, and each failure is quiet.

**1. DOM storage, or your settings vanish.** `setDomStorageEnabled(true)` is what makes
`localStorage` work. The web app persists four toggles and your last depth there. It
wraps those calls in `try/catch`, so if storage is unavailable it degrades to "no
persistence" rather than a blank screen — but persistence is the intended behaviour, so
it is switched on.

**2. `configChanges`, or rotating throws away the descent.** Without
`android:configChanges` covering `orientation|screenSize|screenLayout|…`, Android
destroys and recreates the Activity on rotation, which destroys the WebView, which
loses the depth you were at. The page is already responsive, so handling the change in
the manifest means it simply relayouts.

**3. Back must ask the page, not guess it.** Pressing back should close the specimen
panel before it exits the app. Rather than duplicating that state in Java — where it
would slowly diverge from the JS — `onBackPressed` runs a small script against the page
that consults `window.Sheet.isOpen()` and the help panel, closes whichever is open, and
reports back whether the press was consumed. Only an unconsumed press finishes the
Activity. `evaluateJavascript` is asynchronous, so the decision arrives in the callback
and `finish()` is called from there.

Two smaller ones worth naming:

- **The window background matches the canvas at depth** (`#01080F`), so there is no
  white flash in the moment before the WebView paints its first frame.
- **`setTextZoom(100)`** pins the page's text scale. The HUD is laid out in `px` and
  `clamp()`; letting the system font scale inflate it would push the gauges off the
  rail. This affects only the WebView, not the rest of the device.

`FLAG_KEEP_SCREEN_ON` is set because the guided descent (`D`) takes about 35 seconds
and the screen should not sleep partway down it. The WebView is paused in `onPause`,
which stops its timers and `requestAnimationFrame` so a backgrounded app is not burning
battery on a canvas nobody can see.

---

## Launcher icon

Pure XML vectors — **no PNG mipmaps anywhere in the project**. `minSdk 26` is what makes
that possible: adaptive icons arrive at 26, so there is no older device that needs a
legacy bitmap.

The background gradient uses the same colour stops `js/data.js` feeds the canvas
(`#0E6D97` where the sunlight zone gives out, `#03253F` at the base of the twilight
zone, `#00050A` in the abyss), so the icon and the app agree about what the ocean looks
like rather than the icon being a separate guess.

The foreground is a descent in three marks that stay legible at 48 dp: the surface
swell, the line of the drop breaking into dashes as the light runs out, and the
anglerfish lure at the bottom in the same `#FFCF6B` the canvas lights it with. All of it
sits inside the central 72 dp safe zone, so no launcher mask can crop it. A third
`monochrome` layer supports Android 13+ themed icons.

---

## Building it yourself

### On GitHub Actions (what actually produces the release APK)

`.github/workflows/build-apk.yml` runs on any push touching `abyssal/`,
`abyssal-android/` or the workflow itself, and can be run by hand from the *Actions*
tab. It installs JDK 17 and Gradle 8.7, copies the web app into assets, builds,
verifies, and attaches the APK to the release.

### Locally

You need JDK 17 and the Android SDK (platform 34).

```bash
# put the web app where the build expects it
mkdir -p abyssal-android/app/src/main/assets/abyssal
cp -r abyssal/index.html abyssal/css abyssal/js abyssal-android/app/src/main/assets/abyssal/

cd abyssal-android
gradle assembleDebug          # or ./gradlew, if you generate a wrapper
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**There is no `gradle/wrapper` directory**, deliberately. The wrapper JAR is a binary
that could not be fetched from the environment this project was authored in, so CI
installs Gradle via `gradle/actions/setup-gradle` and calls `gradle` directly. To add a
wrapper for local convenience:

```bash
gradle wrapper --gradle-version 8.7
```

---

## Why this cannot be built where it was written

Worth recording, because it explains the shape of everything above. The environment has
no `java`, no `javac`, no `gradle`, no `sdkmanager`, no `aapt2`, no `apksigner` and no
`keytool`; `ANDROID_HOME` and `JAVA_HOME` are unset and `/usr/lib/jvm` does not exist.
And the hosts an Android build needs are all unreachable:

| Host | Result |
| --- | --- |
| `dl.google.com` | blocked |
| `maven.google.com` | blocked |
| `repo.maven.apache.org` | blocked |
| `services.gradle.org` | blocked |
| `plugins.gradle.org` | blocked |

So a local build is not "hard", it is impossible: there is no compiler and no way to
fetch one. Everything here is written to be compiled by a runner that has both.

The same wall is why the wrapper is deliberately boring. **No AndroidX, no Kotlin
plugin, no third-party library, no binary assets.** Each dependency this build does not
declare is one fewer thing that can fail to resolve on a cold runner in a build nobody
could test locally. `android.app.Activity` and `android.webkit.WebView` are enough, so
that is all it uses.

---

## Verification

The workflow does not trust `assembleDebug` to have done the right thing. After the
build it opens the APK as a zip and asserts that:

- the archive is not corrupt;
- `AndroidManifest.xml` and `classes.dex` are present;
- `assets/abyssal/index.html`, `assets/abyssal/js/app.js` and
  `assets/abyssal/css/abyssal.css` are present.

That last check matters. A build that silently dropped the assets still succeeds — it
just installs an app that shows a blank screen. The copy step checks the same nine files
on the way in, and fails the build if `dist/` (the release zip) or `tools/` (the jsdom
smoke test) leaked into the assets.

The web app underneath it has its own test, `abyssal/tools/smoke.js`, which runs it in a
real DOM: **399 checks, 0 failures, 0 runtime errors**. See
[abyssal/README.md](../abyssal/README.md).

What is *not* verified: nothing here has been run on a device or an emulator, because
none is available in this environment. The APK is compiled and structurally checked by
CI, not exercised.

---

## Licence

MIT, matching the web app. The specimen data and physics models are summaries of
published marine biology; the prose and the code are original.
