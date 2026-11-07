# PRISM

A deep text-analysis instrument for Android, written in **Kotlin** with **Jetpack Compose**.

Paste or share any text and PRISM resolves it into six layers of measurement, then says what
those measurements mean. Everything is computed on the device. The app declares **no permissions
at all** — not even `INTERNET` — so there is no channel through which a document could leave it.

```
prism/
├── app/src/main/java/dev/prism/analysis/
│   ├── engine/      pure Kotlin, no android.* import, JVM-tested
│   │   ├── Tokenizer.kt      syllable counting + sentence/word segmentation
│   │   ├── Lexicon.kt        stopwords and the weighted sentiment lexicon
│   │   ├── Metrics.kt        the result model
│   │   ├── TextAnalyzer.kt   String → PrismReport
│   │   └── Interpretation.kt PrismReport → prose
│   ├── ui/
│   │   ├── theme/PrismTheme.kt      palette derived from the analysed text
│   │   ├── component/AnalysisCore.kt the morphing core
│   │   ├── component/Instruments.kt  gauges, charts, histogram, dial
│   │   ├── component/Controls.kt     tabs, editor, buttons
│   │   ├── component/Format.kt       locale-independent number formatting
│   │   └── screen/Layers.kt          the six layers
│   ├── vm/PrismViewModel.kt
│   └── MainActivity.kt
├── app/src/test/…/EngineTest.kt     49 tests
└── ci/                              static audit + APK verifier
```

---

## What it measures

Every figure comes from a published formula. Nothing is estimated, smoothed, or invented to look
plausible, and where a formula's assumptions do not hold the app says so rather than hiding it.

### 1 · Overview
Surface counts (characters, letters, words, sentences, paragraphs, syllables), reading time at
238 wpm silent and 150 wpm aloud, a composite difficulty index, and a **prose reading** derived
from the statistics.

### 2 · Readability
| Formula | Definition |
|---|---|
| Flesch Reading Ease | `206.835 − 1.015·(words/sentences) − 84.6·(syllables/words)` |
| Flesch-Kincaid Grade | `0.39·(words/sentences) + 11.8·(syllables/words) − 15.59` |
| Gunning Fog | `0.4·[(words/sentences) + 100·(complex/words)]`, complex = 3+ syllables |
| SMOG | `1.0430·√(polysyllables·(30/sentences)) + 3.1291` |
| Coleman-Liau | `0.0588·L − 0.296·S − 15.8`, L = letters/100 words, S = sentences/100 words |
| ARI | `4.71·(letters/words) + 0.5·(words/sentences) − 21.43` |

Coleman-Liau and ARI deliberately ignore syllable counting and work from letters instead. That is
why they disagree with the other four, and the disagreement is reported rather than averaged away:
the five grade-level formulas are fitted on different corpora, and their spread is itself a
finding. When it exceeds four grades the reading says the input is probably not continuous prose.

### 3 · Lexicon
Vocabulary richness measured four ways, because no single statistic is independent of text length.

| Statistic | Definition |
|---|---|
| Type-token ratio | `V/N` — falls mechanically as a text grows |
| Yule's K | `10⁴·(M − N)/N²`, `M = Σ i²·V(i,N)` — barely moves with length |
| Herdan's C | `ln(V)/ln(N)` |
| Honoré's R | `100·ln(N)/(1 − V₁/V)` — undefined when every word is unique, so it reports 0 |

Plus hapax legomena (words appearing once), dis legomena (twice), and mean word length and
syllables per word.

### 4 · Structure
Sentence-length distribution as a histogram over six buckets (`1–5, 6–10, 11–15, 16–20, 21–30,
31+`), mean, median, sample standard deviation, range, and a rhythm score — the coefficient of
variation, `σ/mean`. The longest sentence is quoted in full, because it is the hardest single thing
a reader has to hold.

### 5 · Sentiment
A weighted lexicon tally: `(positive − negative) / Σ weights`, bounded −1..+1. Roughly 120 positive
and 150 negative entries, each weighted 1–3. This is not a model and does not pretend to be; every
hit is a listed word with a listed weight. The **emotional arc** splits the text into twelve
windows and scores each, and **volatility** is half the distance between the warmest and coldest
window.

### 6 · Frequency
Rank-ordered word frequency with each word's deviation from the share **Zipf's law** predicts for
its rank — observed share minus `(1/rank)/Σ(1/rank)`. Content words are drawn in the accent colour
and stopwords dimmed, using the same distinction the engine computes.

---

## Animation

The rule throughout is that **nothing animates on a timer**. Every moving value is a function of
something the engine computed, which is what separates an instrument from a screensaver.

The central **analysis core** is the clearest case. Its radial profile *is* the document's
emotional arc, unrolled into a closed loop and resampled to 72 points: warm stretches bulge
outward, cold stretches contract.

| Motion | Bound to |
|---|---|
| radial profile | the emotional arc, resampled and looped |
| rotation rate | `rhythm01` — varied prose turns faster than flat prose |
| pulse depth and rate | `difficulty01` — dense text throbs harder |
| hue | `valence` — cold blue-violet at −1, teal at 0, warm amber at +1 |
| saturation | Herdan's C — repetitive vocabulary looks washed out |
| lobe reach | `volatility01` — a text that swings reaches further out |

A **perfect circle is therefore a meaningful reading**: it is a document with no emotional range,
and the shape says so without being told to.

The same discipline governs the widgets. In every gauge one animated float drives both the arc and
the digits beneath it, so a gauge can never show `0.62` while its arc sits at `0.4`. The emotional
arc draws itself left to right from one reveal float that clips the stroke and the fill together,
so the shaded area never runs ahead of the line above it. Histogram bars and frequency rows are
staggered by index. Layer switches cross-fade rather than snap.

The **accent colour is derived, not configured**. Hue interpolates `valence` from 232° to 40°,
saturation follows Herdan's C from 0.42 to 0.86, and lightness falls as difficulty rises. Two
different documents look like two different instruments. This is the same move as theming an
interface from its content rather than decorating it — except that here the derivation is
arithmetic anyone can check.

---

## Offline by construction

`AndroidManifest.xml` contains no `<uses-permission>` element. Without `android.permission.INTERNET`
the process cannot open a socket, so there is no exfiltration path to disable at runtime and no
cleartext-traffic setting to get wrong. Privacy here is a property of the build that a reviewer can
verify by reading one file, not a promise in a policy.

Text can be shared *in* from any other app through an `ACTION_SEND` / `text/plain` filter.

---

## Building

```bash
cd prism
gradle testDebugUnitTest    # engine arithmetic, on the JVM, no emulator
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

The engine imports nothing from `android.*`, so its tests run on a plain JVM. That matters more
than it sounds: it means the metrics are verifiable without a device, an emulator, or an Android
SDK, and `ci/static_audit.py` enforces the boundary by failing if an engine file ever acquires an
`android.*` import.

**Toolchain.** AGP 9.0.0, Kotlin 2.3.10, Compose 1.10.2, Material3 1.5.0-alpha18, Gradle 9.3.1,
JDK 21, `compileSdk`/`targetSdk` 36, `minSdk` 26.

Two consequences of AGP 9 are worth recording, because both are easy to get wrong from pre-9
experience:

- The `org.jetbrains.kotlin.android` plugin must **not** be applied. AGP 9 has built-in Kotlin
  support and fails configuration outright if the standalone plugin is present.
- Kotlin is configured inside the `android { kotlin { … } }` block, not through a top-level
  `kotlin { }` extension, since with no standalone Kotlin plugin applied there is nothing to
  provide one.

`minSdk` 26 keeps the launcher icon pure XML — adaptive icons arrive at API 26, so no legacy PNG
mipmaps are needed and the repository carries no binary assets at all.

---

## Verification

CI runs in `.github/workflows/build-prism.yml`, split into separate configure / compile / test /
verify steps so that which one fails is itself the diagnosis.

**49 unit tests** pin the arithmetic against hand-computed values:

| Suite | Tests | Covers |
|---|---|---|
| `SyllablesTest` | 8 | the syllable counter, including silent-e, `-ed`, and `-le` endings |
| `TokenizerTest` | 10 | word and sentence segmentation, abbreviations, contractions |
| `TextAnalyzerTest` | 19 | every formula, against values computed by hand |
| `InterpretationTest` | 12 | the generated prose, against the numbers it claims to describe |

Reference values for `"The cat sat on the mat."` — 6 words, 1 sentence, 6 syllables, 17 letters —
are FRE `116.145`, Flesch-Kincaid `−1.45`, Fog `2.40`, SMOG `3.129`, Coleman-Liau `−4.073`, ARI
`−5.085`, complex words `0`. Negative grade scores are a real result on very simple input, not an
error, and are labelled rather than clamped.

A sweep over fifteen hostile inputs — empty, whitespace-only, punctuation-only, binary, a single
42-character word — asserts the engine never produces `NaN` or an infinite value, and that the
interpretation never leaks one into a sentence.

`InterpretationTest` exists because prose is the easiest part of this to let rot silently: a
formula could change and the sentences describing it would keep reading plausibly while saying
something false. So the reading is asserted against the counts it quotes, required to draw a
caveat on short input, and required *not* to draw one on ordinary prose — a caveat that fires on
everything trains the reader to ignore it.

Two further scripts run outside the app:

- **`ci/static_audit.py`** — checks delimiter balance in every Kotlin file, resolves every import
  of PRISM's own symbols against a declaration index, and cross-checks each `androidx.*` import
  against the vendored Mochy-Music tree, which builds the same library versions. An import that
  appears in neither is flagged as unverified rather than assumed correct.
- **`ci/verify_apk.py`** — opens the built APK and confirms it is a valid archive containing a
  manifest, dex and resource table; that it is signed, by locating the APK Signing Block and
  reading its scheme IDs rather than looking for v1 files in `META-INF`; and that every native
  library sits under a valid ABI directory.

---

## Provenance

The toolchain versions are copied from the vendored [Mochy-Music](../Mochy-Music-VENDORED.md)
project in this repository, because that project builds these exact artifacts together in CI and
represents a known-good pairing. Copying a configuration that has been observed to work beats
picking a fashionable one, particularly when the project cannot be compiled locally to check.

PRISM's own code shares nothing with it. The animation *approach* — binding every transition to a
real state value rather than to a clock — is the thing borrowed.
