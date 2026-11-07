# offscript — a little more life

A native Kotlin + Jetpack Compose social-app preview, not a WebView wrapper.

- Animated Android launch handoff, swipeable welcome, screen transitions and spring feedback.
- Native lazy-scrolling photo feed, discovery filters, local search, people profiles.
- Photo import through the system picker, local post publishing and deletion.
- Likes, saves, following, comments, profile editing, local conversations.
- System/light/dark appearance and a reduced-motion option.
- Local persistence across restarts; no backend, account or network permission.

## Install
Download **offscript-1.0.0-preview.apk**. Open it on Android 8.0 or later and allow installation from your browser/file manager when prompted.

This is a **debug-signed preview APK**, not a Play Store production release. Keep PRISM installed if desired: Offscript uses its own application ID.

## Demo boundaries
All starter identities and activity are fictional. Messages are saved on this device, not delivered. No server or signup is required. Imported photos stay in private app storage. Uninstalling or clearing app data deletes your local changes. Reference photography is for design demonstration; source pages are documented in the repository and app About screen.

## Checks
The publishing workflow compiles both APKs, runs the pure Kotlin domain tests, checks APK structure/signature-block presence, and runs native interaction tests on an Android 15 emulator before publishing. Test reports and screenshots are attached to the workflow artifact. This does not replace physical-device testing or production signing.
