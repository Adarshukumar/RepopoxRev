# Offscript for Android

A **native Kotlin / Jetpack Compose** implementation of the Offscript design, independent of the web prototype and PRISM. Application ID: `dev.offscript.social`. Minimum Android version: 8.0 (API 26).

## Try it
The GitHub release `offscript-v1.0.0` is the intended distribution point. CI publishes the debug-signed APK only after build, unit checks, APK structure checks and Android emulator interaction tests succeed. See [release notes](RELEASE.md) for installation and demo boundaries. Do not confuse the earlier PRISM APK with this app.

## Scope
- Three-page swipeable onboarding, Android splash fade/scale, screen transitions and reactive spring feedback.
- Native LazyColumn feed with keyed items, category/Following filters, Explore search and people discovery.
- Photo detail viewer, profiles, follow/unfollow, likes, saves, comments.
- Composer: optional private photo import through the system document picker, sample image selection, caption/location/category. Imported photos are decoded off the main thread at a bounded resolution and copied into app-private storage.
- Own-post deletion, editable profile, local demo inbox, Android text-share sheet.
- Persistent theme, profile, posts, likes, bookmarks, follows, comments and messages. Serialized writes run on a dedicated ordered worker, not the UI thread.
- Light / dark / system colors, reduced motion and Android animator-scale preference support.

**No backend**, as requested for this iteration. No network permission, signup, tracking, device-media permission, live activity or delivered messages. Demo status is disclosed in onboarding, inbox, composer, notifications and About. Sample imagery is reference material with provenance documented in [`../offscript/README.md`](../offscript/README.md). Starter names/captions/locations are illustrative.

## Build
Use JDK 21, Gradle 9.3.1, Android SDK 36. AGP 9 has built-in Kotlin: do not add `org.jetbrains.kotlin.android`.

```sh
cd offscript-android
gradle assembleDebug testDebugUnitTest
# On a connected emulator/device:
gradle connectedDebugAndroidTest
python3 ci/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow installs the toolchain. No Gradle wrapper JAR is bundled. Demo photos are resized to at most 1400px and stored in drawable-nodpi to avoid Android density upscaling large photographs.

## Verification
`SocialActionsTest` checks the pure Kotlin state transitions, constraints and filtering. `AppTest` launches the native activity on an Android 15 emulator, traverses primary screens, publishes a post, changes appearance, checks state serialization and captures screenshots. The workflow retains APKs, logs, reports and screenshots. Emulator screenshots reflect the native app, not HTML mockups.

## Boundaries / follow-up
- Debug signing is for evaluation, not production. Future CI runs may use different debug keys; uninstalling an older preview may be required and loses local data.
- No background photo cleanup yet: removing a draft/post does not remove its imported private image file. Clearing app data removes all of it.
- File import preserves the decoded pixels but does not currently apply EXIF orientation normalization; some camera images may appear rotated.
- This preview uses a small JSON-backed local store; a production feed should use Room and a server-backed repository, authenticated accounts and moderated media storage.
- Physical device, large-library stress and full accessibility audits remain separate from the automated emulator smoke tests.
