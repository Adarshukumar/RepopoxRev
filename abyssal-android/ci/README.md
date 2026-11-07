# The APK build workflow, parked here on purpose

`build-apk.yml` in this directory is the GitHub Actions workflow that compiles the APK
and attaches it to the release. **It is not in `.github/workflows/`, and that is not an
oversight.**

Creating or updating a file under `.github/workflows/` requires a `workflows`
permission that the token available while this was being built does not have. Both
routes were tried and both were refused:

| Route | Result |
| --- | --- |
| `git push` with the workflow included | `remote rejected ... refusing to allow a GitHub App to create or update workflow .github/workflows/build-apk.yml without workflows permission` |
| `PUT /repos/.../contents/.github/workflows/build-apk.yml` | `403 Resource not accessible by integration` |

The rest of this commit pushed normally. Only the workflow path is blocked.

## To enable it

Grant the GitHub App the **Workflows** permission (read and write), then:

```bash
mkdir -p .github/workflows
cp abyssal-android/ci/build-apk.yml .github/workflows/build-apk.yml
git add .github/workflows/build-apk.yml
git commit -m "Enable the ABYSSAL APK build"
git push
```

The workflow triggers on a push touching `abyssal/`, `abyssal-android/` or itself, so
that push builds the APK immediately. It can also be run by hand from the *Actions* tab
(`workflow_dispatch`).

It installs JDK 17 and Gradle 8.7, copies the web app into `assets/`, builds
`assembleDebug`, verifies the produced APK is a valid zip containing
`AndroidManifest.xml`, `classes.dex` and the web app, then attaches it to the
`v1.0.0-abyssal` release. It needs `permissions: contents: write`, which the file
already declares.

## Or build it without CI

Nothing about the APK requires GitHub Actions; CI is only where a JDK and an Android
SDK happen to be available. With [Android Studio](https://developer.android.com/studio)
or a local JDK 17 plus Android SDK platform 34:

```bash
mkdir -p abyssal-android/app/src/main/assets/abyssal
cp -r abyssal/index.html abyssal/css abyssal/js abyssal-android/app/src/main/assets/abyssal/

cd abyssal-android
gradle assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

There is no `gradle/wrapper` directory — the wrapper JAR is a binary that could not be
fetched while authoring. Add one for local convenience with
`gradle wrapper --gradle-version 8.7`, or just use Android Studio, which supplies its
own Gradle.

Keep the `cp` step. The web app is deliberately not duplicated into the Android tree in
git: one copy of the descent and its physics, not two that can drift apart.
