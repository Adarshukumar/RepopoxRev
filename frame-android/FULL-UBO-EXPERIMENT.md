# Frame YouTube Preview — full uBlock Origin

Same Frame interface and features, with genuine unmodified Firefox **uBlock Origin 1.75.4** replacing Lite in this separate build. YouTube is prefilled during setup; acknowledge website-access permission and launch. This uses the live YouTube website, **not a modified official YouTube APK**, and does not promise permanently ad-free YouTube.

## Install (when publication checks pass)
**[Download the Android APK](https://github.com/Adarshukumar/RepopoxRev/releases/download/frame-youtube-v1.1.0/frame-youtube-1.1.0-preview.apk)** — ARM64 Android 8+.

Open `frame-youtube-1.1.0-preview.apk`, not a source/screenshot ZIP. It appears as **Frame uBO** alongside your existing Frame app; Lite and its data are untouched. This separate app has its own cookies/settings. Debug-signed preview, not production signing.

## What changed / how blocking works
The original experiment failed its initial blocking check. Upstream `start.js/onVersionReady` deliberately unsuspends requests during first installation, before all filter lists are ready. Frame now waits for an actual blocking capability check on a fresh full-uBO profile before opening the chosen website. It fails visibly rather than silently opening the site without verified initial filtering. First initialization can take up to two minutes. Later deliberate user filter-list changes are respected; this initial check is not a per-site guarantee.

Full uBO performs filtering through its real upstream network engine and content filters/scriptlets. No Frame ad-hiding imitation, custom YouTube bypass or modified upstream code is substituted. Changing YouTube behavior can still break filtering.

[Readiness-fix validation run 42490474587](https://github.com/Adarshukumar/RepopoxRev/actions/runs/42490474587) passed the controlled enabled/disabled/re-enabled blocking checks, official settings/native controls, and YouTube homepage navigation. That earlier navigation check alone did not prove playback or video-ad removal.

## Attached evidence and its limits
Publication now also requires a real public YouTube video to decode frames and advance its media time at least 10 seconds. The separate read-only test observer has no blocking or player-manipulation functionality. Its assets live in the debug APK but it is installed/activated only by instrumentation, never during normal app use.

- `blocking-evidence.json`: actual loopback request counts with full uBO enabled, disabled, and re-enabled, using an unchanged default upstream rule. This proves that filtering works, **not specifically that YouTube video ads were removed**.
- `youtube-playback-evidence.json`: observed playback time, decoded frames and detected ad markers for the public test video. **Seeing no ad is not proof that an ad was removed.** Ad delivery varies by session, region and account.
- `frame-screenshots.zip`: actual emulator screenshots, not design mockups.
- `youtube-result.txt`: actual test outcome/log summary.

13 URL unit tests and byte-for-byte extension/APK signature checks are also required. Full-uBO settings and native controls must pass their integration test. No guarantee of every video, login provider, DRM or permanent ad blocking; existing Frame feature limitations still apply.

## Source and reproducibility
Run the `Frame full uBlock Origin experiment` workflow. `FRAME_FULL_UBO=true` and `ci/prepare_full.py` select full uBO; ordinary builds remain Lite. Signed upstream XPI SHA256: `e2df503cce7cf5069130618c349695f62bdddb9eb8c189156d027269ae4ae267`, verified against GitHub's release digest. The original XPI, Frame source and recursively populated upstream source accompany publication. Frame/uBO: GPL-3.0; GeckoView: MPL-2.0 plus third-party notices. Not affiliated with YouTube, Mozilla or uBlock Origin.
