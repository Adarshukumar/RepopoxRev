# Frame with full uBlock Origin — experiment

Same Frame interface and features, with the genuine unmodified Firefox **uBlock Origin 1.75.4** replacing Lite in this build only. The live YouTube website is loaded by GeckoView; this does not modify or redistribute the official YouTube Android APK. No claim of guaranteed ad-free YouTube.

## Attempt result — not released

[CI run 42438354400](https://github.com/Adarshukumar/RepopoxRev/actions/runs/42438354400) built the app, passed 13 URL unit tests and APK/unchanged-extension verification, and installed full uBO in the emulator. However, the first enabled-filtering assertion failed: expected `probe:enabled:control=1;ad=0`, observed `probe:enabled:control=1;ad=1`. The bundled rule was `/ea/fl.js` from `assets/ublock/filters.min.txt`. Logs also reported a script timeout in upstream `js/cachestorage.js` during IndexedDB writes. The root cause is not established; this is not evidence that full uBO can never work in GeckoView.

The publication gate correctly stopped the release. The separate YouTube check was **not reached**. No claim of working/ad-free YouTube or verified full-uBO settings is made. The original Lite regression run [42438352059](https://github.com/Adarshukumar/RepopoxRev/actions/runs/42438352059) passed. Its existing release and installed data were not changed. The experiment is now manual-dispatch only.

## Intended installation if future checks pass
ARM64 Android 8+. Install `frame-full-ubo-experiment.apk`. It appears as **Frame uBO** alongside your existing Frame app; your Lite installation and its data are untouched. This separate app starts with its own settings/cookies: enter `https://m.youtube.com/` once. Debug-signed experimental build, not production signing.

## What is tested
Publication requires all 13 URL unit tests, byte-for-byte extension/APK verification, and the genuine extension's enabled-blocked → disabled-allowed → re-enabled-blocked network experiment, plus official settings navigation and native controls. The fixture rule is selected from the shipped default uBlock filter list, not injected.

YouTube navigation is attempted separately with a hard timeout and its actual outcome appended below. It does **not** establish video playback, sign-in, video-ad removal, or resistance to anti-adblock changes. Physical-device playback testing is still necessary.

## Reproduce / provenance
Run the `Frame full uBlock Origin experiment` workflow on the session branch. `FRAME_FULL_UBO=true` and `ci/prepare_full.py` select the experiment; normal builds remain Lite. The XPI is pinned to upstream SHA256 `e2df503cce7cf5069130618c349695f62bdddb9eb8c189156d027269ae4ae267` and checked against GitHub's release digest. The unmodified original XPI, Frame source and recursively populated upstream source accompany the release. Frame/uBO: GPL-3.0; GeckoView: MPL-2.0 and third-party notices. Existing Frame limitations still apply (see README).
