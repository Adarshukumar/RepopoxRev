# Frame 1.0.0 Preview — your website, without the browser toolbar

## Install the app — APK download

**[Download frame-1.0.0-preview.apk](https://github.com/Adarshukumar/RepopoxRev/releases/download/frame-v1.0.0/frame-1.0.0-preview.apk)** — 219 MB, ARM64, Android 8 or later.

Download this `.apk`, then open it with Android's package installer. If prompted, allow installation from the browser/file manager you used. **Do not download Source code (zip), frame-screenshots.zip, or Actions artifact ZIPs to install the app.** Do not rename a source ZIP to `.apk`.

This is the original **uBlock Origin Lite** preview, not the full-uBlock experiment. That experiment failed blocking verification and has not been released. No APK replacement is being claimed here.

The publication workflow passed APK structure/signature verification and the emulator integration test. Published APK SHA256: `4e9db4833f3124cf5075d94c6b2be14469a6be3eb1fe5fdb5d95393643c92df3`.

Native Android shell with Mozilla GeckoView and the **real, unmodified uBlock Origin Lite Firefox extension**. This is not Chromium and not a WebView filter imitation.

Enter an HTTPS URL once. Later launches open that website directly. The small corner control opens settings without permanent browser chrome. Android Back follows page history, closes extension settings, or opens app controls at the root.

Includes uBO Lite's real dashboard, extension enable/disable, saved-site editing, desktop-site mode, dark/light/system app appearance, and clearing browsing data. Website sessions persist. Full-screen video uses Android immersive mode. Basic JavaScript dialogs, selection fields and document upload prompts are supported.

## Install
Download `frame-1.0.0-preview.apk` for **ARM64 Android 8+**. This is a debug-signed evaluation build, not a production Play Store release. No account is required by Frame; websites may require their own accounts.

## Verification and limits
CI tests the genuine extension against a local HTTP fixture using an actual unmodified upstream blocking rule: normal requests load, the ad-pattern request is blocked when enabled, reaches the server when disabled, and is blocked again when re-enabled. The official extension dashboard must also load before publication. No fabricated blocking counter or replacement rules are used.

**YouTube compatibility is not verified.** An external YouTube smoke test stalled/crashed the CI emulator and is recorded separately, not counted as passing. Test on a physical phone before relying on playback or sign-in.

This proves the controlled filtering path, not that every ad on every site will disappear. YouTube changes frequently; DRM playback and some providers' embedded-browser sign-in restrictions remain outside Frame's control. Camera/microphone capture, download management and other full-browser features are not implemented in this preview.

## Privacy and sources
Frame has no analytics/backend. Website requests still go to their respective servers. The extension has website access needed to filter content; its settings and permissions are exposed. Acknowledge this during initial setup.

Frame and uBO Lite are GPL-3.0; GeckoView is MPL-2.0 with additional third-party licenses. The workflow publishes build provenance and corresponding source archives with this release. This project is not affiliated with Mozilla, YouTube, or uBlock Origin.
