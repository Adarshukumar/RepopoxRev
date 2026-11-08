# Frame YouTube 1.1 — full uBlock Origin device preview

## Download the APK, not a ZIP
**[Install frame-youtube-1.1.0-device-preview.apk](https://github.com/Adarshukumar/RepopoxRev/releases/download/frame-youtube-v1.1.0-device-preview/frame-youtube-1.1.0-device-preview.apk)** — ARM64 Android 8+.

Installs as **Frame uBO**, separately from your existing Frame Lite. The interface is unchanged; YouTube is prefilled during setup. Acknowledge the extension's website access and launch. First-install filtering verification can take up to two minutes. Separate cookies/settings; debug signing, not a production Play Store release.

**This is NOT a fully playback-validated or guaranteed ad-free YouTube release.** It runs the live YouTube website, not a patched official YouTube APK.

## What works in controlled testing
- Genuine, unmodified full **uBlock Origin 1.75.4**, not Lite or a replacement filter.
- Fixed first-install readiness: Frame waits for a real filtering capability check before initially opening your site.
- 13 URL unit tests, signature/APK checks, actual extension settings/native controls.
- Real server requests: **enabled → blocked; disabled → allowed; re-enabled → blocked**, with normal control scripts loading throughout. See `blocking-evidence.json`.
- YouTube homepage and a public watch-page document loaded in testing.

## What did NOT pass
The cloud-emulator playback test reached YouTube's **“Sign in to confirm you’re not a bot”** challenge. Observations recorded zero decoded video frames and zero media-time advance. Later emulator activity teardown also failed. This candidate preserves that failed result; it does not turn it into a passing playback test.

Therefore **YouTube playback and video-ad removal are not verified**. No observed ad while a video is blocked is NOT proof of ad removal. Physical-device testing is needed. Do not send passwords or account cookies to the developer. This build does not bypass YouTube's sign-in challenge.

## Attached proof
- `blocking-evidence.json`: measured controlled-request counts (not a YouTube ad experiment).
- `youtube-playback-evidence.json`: actual public-page observations, including the challenge text and zero playback progress.
- `youtube-result.txt`: failed playback-test result and log excerpt.
- `frame-screenshots.zip`: actual setup/blocking/settings emulator screenshots.
- SHA256, build manifest, original signed XPI, Frame source, and upstream uBlock source.

The read-only CI observer is activated only by instrumentation, not during normal app use. It does not block ads, alter media responses, skip ads or manipulate player state. Full uBO's upstream network filters and content scripts do the filtering. Camera/microphone, full download management and some browser features remain outside this preview's scope.

Frame/uBO GPL-3.0; GeckoView MPL-2.0 with third-party notices. No affiliation with YouTube, Mozilla or uBlock Origin.
