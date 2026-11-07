# Frame 1.0.0 — your website, without the browser toolbar

Native Android shell with Mozilla GeckoView and the **real, unmodified uBlock Origin Lite Firefox extension**. This is not Chromium and not a WebView filter imitation.

Enter an HTTPS URL once. Later launches open that website directly. The small corner control opens settings without permanent browser chrome. Android Back follows page history, closes extension settings, or opens app controls at the root.

Includes uBO Lite's real dashboard, extension enable/disable, saved-site editing, desktop-site mode, dark/light/system app appearance, and clearing browsing data. Website sessions persist. Full-screen video uses Android immersive mode. Basic JavaScript dialogs, selection fields and document upload prompts are supported.

## Install
Download `frame-1.0.0-preview.apk` for **ARM64 Android 8+**. This is a debug-signed evaluation build, not a production Play Store release. No account is required by Frame; websites may require their own accounts.

## Verification and limits
CI tests the genuine extension against a local HTTP fixture using an actual unmodified upstream blocking rule: normal requests load, the ad-pattern request is blocked when enabled, reaches the server when disabled, and is blocked again when re-enabled. The official extension dashboard must also load before publication. No fabricated blocking counter or replacement rules are used.

This proves the tested filtering path, not that every ad on every site will disappear. YouTube changes frequently; DRM playback and some providers' embedded-browser sign-in restrictions remain outside Frame's control. Camera/microphone capture, download management and other full-browser features are not implemented in this preview.

## Privacy and sources
Frame has no analytics/backend. Website requests still go to their respective servers. The extension has website access needed to filter content; its settings and permissions are exposed. Acknowledge this during initial setup.

Frame and uBO Lite are GPL-3.0; GeckoView is MPL-2.0 with additional third-party licenses. The workflow publishes build provenance and corresponding source archives with this release. This project is not affiliated with Mozilla, YouTube, or uBlock Origin.
