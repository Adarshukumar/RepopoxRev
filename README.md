# RepopoxRev

## New: Offscript social frontend

See [`offscript/`](offscript/README.md) for the responsive social-media UI. Run `cd offscript && npm ci && npm run dev`. This is a standalone frontend demo, not a PRISM update or Android wrapper.

## PRISM — the app in this repository

**[PRISM](prism/)** is a deep text-analysis instrument for Android, written in Kotlin with Jetpack
Compose. Paste or share any text and it resolves it into six layers of measurement — surface counts,
six published readability formulas, four lexical-richness statistics, sentence-length structure,
weighted sentiment with an emotional arc, and word frequency scored against Zipf's law — then says
in prose what those numbers mean.

It declares **no permissions at all**, not even `INTERNET`, so analysis staying on the device is a
property of the build rather than a promise in a privacy policy.

**Download the APK from the [latest release](../../releases).** CI builds it, runs 49 unit tests
against the engine's arithmetic first, and verifies the artifact before publishing.

See [`prism/README.md`](prism/README.md) for the formulas, the animation bindings, and how the
project is verified.

---

## Repository layout

| Path | What it is |
|---|---|
| [`prism/`](prism/) | **PRISM** — the Kotlin/Compose text-analysis app. This is the deliverable. |
| [`Mochy-Music/`](Mochy-Music/) | A vendored copy of the upstream Mochy-Music project, unmodified. GPL-3.0; see the attribution note below. |
| [`Mochy-Music-VENDORED.md`](Mochy-Music-VENDORED.md) | Licence attribution and the exact upstream commit the vendored copy was taken from. |
| [`learning-build/`](learning-build/) | Notes from reading that codebase — architecture, the DSP chain, the persistence layer — plus a static verification harness and its results. |
| [`abyssal/`](abyssal/) | An earlier web experiment. Superseded by PRISM and kept only for reference. |
| [`.github/workflows/build-prism.yml`](.github/workflows/build-prism.yml) | CI: configures, compiles, runs the unit tests, verifies the APK, and publishes releases from tags. |

### Why a music player is vendored here

PRISM is built against AGP 9.0.0, Kotlin 2.3.10, Compose 1.10.2 and Gradle 9.3.1. Those versions
were taken from Mochy-Music because it builds that exact combination in CI, and a configuration
observed to work is worth more than a fashionable one — particularly for a project written
somewhere with no JDK, no Android SDK and no route to the Maven repositories, where nothing can be
compiled locally to check.

AGP 9 has two consequences that are easy to get wrong from pre-9 experience, and both are visible
in the vendored build files: the `org.jetbrains.kotlin.android` plugin must not be applied, because
AGP 9 has built-in Kotlin support and fails configuration if it is; and Kotlin is configured inside
`android { kotlin { … } }` rather than through a top-level `kotlin { }` extension.

The vendored copy is also used as a reference for API existence. `prism/ci/static_audit.py`
cross-checks every `androidx.*` import in PRISM against the imports that appear across those 1,036
Kotlin files, and flags any that appear in neither as unverified.

No code is shared between the two projects. What PRISM borrows is an approach: binding every
animation to a real state value rather than to a clock.

### Licence

Mochy-Music is GPL-3.0 and remains the property of its authors; the vendored copy is unmodified and
attributed in [`Mochy-Music-VENDORED.md`](Mochy-Music-VENDORED.md). PRISM is original work.
