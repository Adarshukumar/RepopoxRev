# Frame — a website in its own space

Native Android (Kotlin / Compose) + **Mozilla GeckoView 159.0.20261102144711** + **uBlock Origin Lite 2026.1103.1743**, the actual Firefox Manifest V3 extension. No Chromium, no WebView, no replacement blocker, no custom injected rules.

## UX
First launch asks for an HTTPS URL and explicitly discloses the extension's access to website content and network requests. The app saves the URL and waits for the extension to initialize before opening it. Later launches return directly to that site. No permanent address/tab/reload bar. A small corner control and Android Back at the root provide access to settings.

Controls include the official uBO Lite dashboard, extension enable/disable, change home URL, desktop user-agent mode, reload/home, app appearance, reduced startup motion, and browsing-data deletion. The dashboard—not native imitation toggles—controls uBO Lite filtering settings. Website cookies/storage are persistent. File inputs use the system document picker. Video fullscreen enters immersive mode.

## Build and source provenance

```sh
cd frame-android
python3 ci/prepare.py
gradle assembleDebug assembleDebugAndroidTest testDebugUnitTest
# On an emulator:
gradle connectedDebugAndroidTest
```

Requires internet, JDK 21, Gradle 9.3.1, AGP 9.1, SDK 37. Target SDK is 36. Separate ARM64 and x86_64 APKs keep the distributed phone APK smaller. ARM64 Android 8+ is the intended phone target.

`ci/prepare.py` downloads the original signed Firefox package from:
https://github.com/uBlockOrigin/uBOL-home/releases/tag/2026.1103.1743

Both the upstream GitHub asset digest and this pinned SHA256 must match:
`cca96aed8355bb59168c4af3da638a92dda18378d286af996fbf2013f45d7cc7`

The package is extracted **without modifying its files** and registered as a bundled GeckoView extension with `ensureBuiltIn`. GeckoView's built-in-extension path trusts the embedding APK; it is not the public add-on-store signature-verification path. The upstream signature is retained in the original XPI distributed for audit. The generated `build-manifest.json` identifies the extension, engine, hash and actual rule used in the blocking experiment.

Source:
- Frame source: this directory, GPL-3.0.
- uBO Lite source tag: `uBlockOrigin/uBOL-home@2026.1103.1743` (`e03161e379f8316b28a42df33c397d50716b2ab7`). Its uBlock submodule is `gorhill/uBlock@931ae981e3a4d4bfc3f339eed56b937a5024100f`. The release includes a recursively populated source archive and the original signed XPI with licenses.
- GeckoView: Mozilla, MPL-2.0. Unmodified Maven artifact `org.mozilla.geckoview:geckoview:159.0.20261102144711`. Java sources: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/159.0.20261102144711/geckoview-159.0.20261102144711-sources.jar . Firefox release source and MPL notices: https://archive.mozilla.org/pub/firefox/releases/159.0/source/ and https://www.mozilla.org/MPL/2.0/ . GeckoView itself carries third-party notices.

## Integration verification
The test fixture is a real loopback HTTP server inside the instrumented app. Preparation selects an unchanged enabled static blocking rule from the shipped extension (currently `/webtracking.min.js`, `ublock-filters` rule 2218). It adds no rule or extension code.

The test checks:
1. A normal script loads with the extension enabled.
2. The ad-pattern script does **not reach the server** with the extension enabled.
3. That same script reaches the server and executes with the extension disabled.
4. Re-enabling the extension blocks it again.
5. The genuine `moz-extension:` options dashboard loads and its localized title resolves; native screenshots are reviewed for the visible filtering controls. The fixed Pixel 6 test uses a screenshot-reviewed touch fallback if Gecko virtual HTML nodes are not exposed to UiAutomator.
6. Native settings and saved URL persistence work.
7. An optional `publicYouTubePageSmoke` test exists separately. Attempts stalled/crashed the CI emulator; it is excluded from deterministic release gating and is **not verified**. The preview must be checked on a physical phone before relying on YouTube playback, sign-in or ad behavior.

This is deliberately a controlled filtering test, not a claim that all YouTube advertising or every real-world site works. Screenshots and reports are retained by CI. Publication is gated on tests and APK verification.

## Known boundaries
- Preview/debug signing, not production signing or Play Store distribution. Future preview upgrades may require uninstalling, which loses local data.
- No promise of permanent YouTube ad blocking. Site changes, DRM, autoplay restrictions and embedded-engine login policies can affect behavior.
- Camera/microphone/geolocation permissions are not granted; no capture UI. Download management, password-manager integration, custom schemes except confirmed mail/phone handoff, and all browser settings are not implemented.
- App appearance styles native setup/controls; websites control their own appearance.
- The clear-data action signs out websites. Avoid it unless intended; extension preferences may also reset.
- Use app updates to update the pinned engine/extension. No claim of automatic security or filter-package updates beyond what the bundled extension itself supports.
- No analytics/backend. Requests still go to chosen sites. Network permission and website storage are necessary. Android backup is disabled.
