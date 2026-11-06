# Mochy Music — Learning Build

A deep, verified study of **[realidkroo/Mochy-Music](https://github.com/realidkroo/Mochy-Music)**,
an open-source Android music player written in Kotlin + Jetpack Compose.

This folder is the *learning artefact*. The cloned app itself lives one level up, in
[`../Mochy-Music/`](../Mochy-Music/), at upstream commit
`8dd8f3b0620473ab4c26466f3b6b8b7d08aaab12` ("Revise Mochy Music description in README",
2026-11-06).

```
learning-build/
├── README.md                      <- you are here
├── 01-app-overview.md             what the app is, and its shape
├── 02-build-system.md             Gradle, flavors, 60 variants, CI, build.sh
├── 03-playback-engine.md          MusicService, ExoPlayer, the DSP chain, audio pass
├── 04-data-layer.md               Room, 23 entities, the migration graph
├── 05-network-and-providers.md    InnerTube, 12 lyrics sources, the module system
├── 06-ui-and-compose.md           navigation graph, 11 player styles, theming
├── 07-verification-report.md      what was checked, what passed, what is broken
├── 08-key-learnings.md            the distilled lessons
├── reports/
│   └── verify-results.json        machine-readable output of the harness
└── tools/
    ├── verify.py                  the verification harness (17 checks)
    ├── kotlin_lex.py              a Kotlin lexer (nestable comments, nested templates)
    └── kotlin_index.py            a symbol index over 27,872 declarations
```

---

## Why there is a verification harness at all

The brief was to check whether the whole app actually works. That cannot be answered by
reading alone, and it cannot be answered by building either: a real `./gradlew assemble…`
needs JDK 21, the Android SDK + NDK + CMake, and network access to `google()`,
`mavenCentral()`, `jitpack.io` and `maven.aliyun.com`. The analysis sandbox has none of
those, and only 2 vCPU / 4 GB RAM.

So rather than guess, `tools/verify.py` runs **every check that can be decided from the
sources alone** and reports each one as PASS / FAIL / WARN with file-and-line evidence.
It is a real checker: it lexes Kotlin properly (nestable block comments, nested string
templates, raw strings, backtick identifiers) and indexes all 27,872 declarations, so
"does this type exist?" is answered from the code rather than from file names.

```bash
python3 learning-build/tools/verify.py Mochy-Music
python3 learning-build/tools/kotlin_lex.py     # the lexer's own self-test
```

### Result

| | |
|---|---|
| **PASS** | 28 |
| **INFO** | 6 |
| **WARN** | 4 |
| **FAIL** | 4 |

1,036 Kotlin files · 230,722 lines · 763 XML files · 2,897 resource references ·
22 Gradle modules · 60 buildable variants · 69 declared routes · 206 navigate() calls ·
177 unit-test methods · 83 locales.

The four failures are real and are written up in
[`07-verification-report.md`](07-verification-report.md). In one line each:

1. **Room migration gap** — no upgrade path to schema v42 from any version ≤ 34, so an
   upgrade falls through to `fallbackToDestructiveMigration(dropAllTables = true)` and
   deletes the user's entire library. The predecessor app shipped v34.
2. **Two dead navigation routes** — `navigate("mood_and_genres")` and `navigate("new_release")`
   have no registered destination; tapping them throws at runtime.
3. **`CrashActivity` does not exist** — the manifest declares it, no source file does.
4. **`build.sh` is macOS-only** — `sed -i ''` on six lines aborts the documented
   `./build.sh --debug` path on Linux and on the project's own CI runners.

### A note on how these were confirmed

Every FAIL above was re-checked by hand against the source before being written down, and
the harness itself was debugged until its false positives were gone. Early runs reported
"unbalanced brackets" in 286 files and "missing types" that all existed — both were bugs in
the *checker*, not the app. `kotlin_lex.py` now carries a self-test suite covering the exact
constructs that broke it, so those classes of false positive stay fixed.
