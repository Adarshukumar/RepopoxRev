package dev.prism.analysis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prism.analysis.engine.Interpretation
import dev.prism.analysis.engine.PrismReport
import dev.prism.analysis.ui.component.ArcChart
import dev.prism.analysis.ui.component.BodyText
import dev.prism.analysis.ui.component.FrequencyBars
import dev.prism.analysis.ui.component.GaugeRing
import dev.prism.analysis.ui.component.Hairline
import dev.prism.analysis.ui.component.HistogramChart
import dev.prism.analysis.ui.component.LabelText
import dev.prism.analysis.ui.component.MetricTile
import dev.prism.analysis.ui.component.ScaleBar
import dev.prism.analysis.ui.component.SentimentDial
import dev.prism.analysis.ui.component.count
import dev.prism.analysis.ui.component.duration
import dev.prism.analysis.ui.component.ellipsize
import dev.prism.analysis.ui.component.freLabel
import dev.prism.analysis.ui.component.gradeLabel
import dev.prism.analysis.ui.component.normalise
import dev.prism.analysis.ui.component.num
import dev.prism.analysis.ui.component.richnessLabel
import dev.prism.analysis.ui.component.valenceLabel
import dev.prism.analysis.ui.theme.PrismPalette

/* ═══════════════════════════════════════════════════════════════════════
   The six layers.

   Each one is a pure function of the report and the palette. None of them
   holds state, none of them animates on a timer, and none of them can show a
   value the engine did not compute — the only thing a layer decides is which
   part of the report to draw and against what published range.
   ═══════════════════════════════════════════════════════════════════════ */

/* ── 1. overview ──────────────────────────────────────────────────────── */

@Composable
fun OverviewLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    val reading = remember(report) { Interpretation.compose(report) }
    val s = report.surface
    val r = report.readability

    Column(modifier = modifier.fillMaxWidth()) {
        /* The headline gauge. Difficulty is a composite of three formulas, so
           it is the one number that summarises the whole reading. */
        GaugeRing(
            value01 = r.difficulty01.toFloat(),
            display = num(r.difficulty01 * 100.0, 0),
            label = "DIFFICULTY INDEX",
            sublabel = gradeLabel(r.fleschKincaidGrade).uppercase(),
            palette = palette,
            size = 148.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("WORDS", count(s.words), palette, Modifier.weight(1f))
            MetricTile("SENTENCES", count(s.sentences), palette, Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("CHARACTERS", count(s.characters), palette, Modifier.weight(1f))
            MetricTile("SYLLABLES", count(s.syllables), palette, Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile(
                "READ SILENTLY",
                duration(s.readingSeconds),
                palette,
                Modifier.weight(1f),
                hint = "at 238 wpm",
            )
            MetricTile(
                "READ ALOUD",
                duration(s.speakingSeconds),
                palette,
                Modifier.weight(1f),
                hint = "at 150 wpm",
            )
        }

        Spacer(Modifier.height(22.dp))
        Hairline(palette)
        Spacer(Modifier.height(18.dp))

        SectionHeaderLocal("THE READING", "what these numbers say about this text", palette)

        if (reading.isEmpty()) {
            BodyText(
                "Nothing to interpret yet. Every sentence below is derived from the computed " +
                    "statistics — there is no filler text, so an empty document produces an " +
                    "empty reading.",
                palette,
            )
        } else {
            reading.forEachIndexed { i, sentence ->
                Row(Modifier.fillMaxWidth().padding(bottom = 11.dp)) {
                    /* the marker is the sentence index, so the reading can be
                       referred to precisely and reads as a numbered finding */
                    Text(
                        text = (i + 1).toString().padStart(2, '0'),
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(top = 3.dp, end = 10.dp),
                    )
                    BodyText(sentence, palette, Modifier.weight(1f), size = 12.5.toInt())
                }
            }
        }
    }
}

/* ── 2. readability ───────────────────────────────────────────────────── */

@Composable
fun ReadabilityLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    val r = report.readability
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderLocal(
            "SIX FORMULAS",
            "each fitted on a different corpus, so their agreement matters as much as their values",
            palette,
        )

        ScaleBar(
            value01 = normalise(r.fleschReadingEase, 0.0, 100.0),
            leftLabel = "0 · VERY HARD",
            rightLabel = "100 · VERY EASY",
            title = "FLESCH READING EASE",
            display = num(r.fleschReadingEase, 1),
            note = freLabel(r.fleschReadingEase),
            palette = palette,
            delayIndex = 0,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(r.fleschKincaidGrade, -3.0, 20.0),
            leftLabel = "PRE-SCHOOL",
            rightLabel = "GRADUATE",
            title = "FLESCH-KINCAID GRADE",
            display = num(r.fleschKincaidGrade, 1),
            note = gradeLabel(r.fleschKincaidGrade) + " · US school year",
            palette = palette,
            delayIndex = 1,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(r.gunningFog, 0.0, 20.0),
            leftLabel = "0",
            rightLabel = "20 · YEARS OF EDUCATION",
            title = "GUNNING FOG",
            display = num(r.gunningFog, 1),
            note = "weighted on words of three or more syllables",
            palette = palette,
            delayIndex = 2,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(r.smog, 0.0, 20.0),
            leftLabel = "0",
            rightLabel = "20",
            title = "SMOG",
            display = num(r.smog, 1),
            note = "normalised to 30 sentences; below 1.04·√30 polysyllables it floors near 3.1",
            palette = palette,
            delayIndex = 3,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(r.colemanLiau, -6.0, 20.0),
            leftLabel = "−6",
            rightLabel = "20",
            title = "COLEMAN-LIAU",
            display = num(r.colemanLiau, 1),
            note = "derived from letters per word, not syllables — so it disagrees with the others on purpose",
            palette = palette,
            delayIndex = 4,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(r.ari, -6.0, 20.0),
            leftLabel = "−6",
            rightLabel = "20",
            title = "AUTOMATED READABILITY INDEX",
            display = num(r.ari, 1),
            note = "uses letters per word and words per sentence; ignores syllable counting entirely",
            palette = palette,
            delayIndex = 5,
        )

        Spacer(Modifier.height(24.dp))
        Hairline(palette)
        Spacer(Modifier.height(18.dp))

        SectionHeaderLocal("WORD COMPLEXITY", "what the syllable-based formulas are reacting to", palette)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile(
                "COMPLEX WORDS",
                count(r.complexWords),
                palette,
                Modifier.weight(1f),
                hint = "3+ syllables",
                accent = true,
            )
            MetricTile(
                "POLYSYLLABLES",
                count(r.polysyllables),
                palette,
                Modifier.weight(1f),
                hint = "3+ syllables, SMOG input",
            )
        }
        Spacer(Modifier.height(9.dp))
        ScaleBar(
            value01 = r.complexWordRatio.toFloat().coerceIn(0f, 1f),
            leftLabel = "0%",
            rightLabel = "100%",
            title = "COMPLEX WORD RATIO",
            display = num(r.complexWordRatio * 100.0, 1) + "%",
            note = "share of all words carrying three or more syllables",
            palette = palette,
            delayIndex = 6,
        )
    }
}

/* ── 3. lexicon ───────────────────────────────────────────────────────── */

@Composable
fun LexiconLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    val lex = report.lexicon
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderLocal(
            "VOCABULARY RICHNESS",
            "four statistics that measure variety differently, because no single one is length-independent",
            palette,
        )

        GaugeRing(
            value01 = lex.richness01.toFloat(),
            display = num(lex.richness01 * 100.0, 0),
            label = "RICHNESS",
            sublabel = richnessLabel(lex.herdansC).uppercase(),
            palette = palette,
            size = 132.dp,
            delayIndex = 0,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(22.dp))

        ScaleBar(
            value01 = lex.typeTokenRatio.toFloat().coerceIn(0f, 1f),
            leftLabel = "0 · ALL REPEATED",
            rightLabel = "1 · ALL UNIQUE",
            title = "TYPE-TOKEN RATIO",
            display = num(lex.typeTokenRatio, 3),
            note = "distinct words ÷ total words. Falls mechanically as a text grows, which is why the three below exist.",
            palette = palette,
            delayIndex = 1,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(lex.yulesK, 0.0, 400.0),
            leftLabel = "0",
            rightLabel = "400+",
            title = "YULE'S K",
            display = num(lex.yulesK, 1),
            note = "built from the whole frequency spectrum, so it barely moves with text length",
            palette = palette,
            delayIndex = 2,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = lex.herdansC.toFloat().coerceIn(0f, 1f),
            leftLabel = "0",
            rightLabel = "1",
            title = "HERDAN'S C",
            display = num(lex.herdansC, 3),
            note = "ln(V) ÷ ln(N). This is the value that sets the accent saturation.",
            palette = palette,
            delayIndex = 3,
        )
        Spacer(Modifier.height(18.dp))

        ScaleBar(
            value01 = normalise(lex.honoresR, 0.0, 200.0),
            leftLabel = "0",
            rightLabel = "200+",
            title = "HONORÉ'S R",
            display = num(lex.honoresR, 1),
            note = "rewards texts that keep introducing new words; undefined when every word is unique, so it reports 0 there",
            palette = palette,
            delayIndex = 4,
        )

        Spacer(Modifier.height(24.dp))
        Hairline(palette)
        Spacer(Modifier.height(18.dp))

        SectionHeaderLocal("THE SPECTRUM", "how often words recur", palette)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("UNIQUE", count(lex.unique), palette, Modifier.weight(1f), hint = "distinct words")
            MetricTile(
                "HAPAX",
                count(lex.hapaxLegomena),
                palette,
                Modifier.weight(1f),
                hint = "seen exactly once",
                accent = true,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile(
                "DIS LEGOMENA",
                count(lex.disLegomena),
                palette,
                Modifier.weight(1f),
                hint = "seen exactly twice",
            )
            MetricTile(
                "MEAN LENGTH",
                num(lex.meanWordLength, 2),
                palette,
                Modifier.weight(1f),
                hint = "letters per word",
            )
        }
        Spacer(Modifier.height(9.dp))
        MetricTile(
            "MEAN SYLLABLES",
            num(lex.meanSyllablesPerWord, 2),
            palette,
            Modifier.fillMaxWidth(),
            hint = "the single largest driver of every syllable-based readability formula",
        )
    }
}

/* ── 4. structure ─────────────────────────────────────────────────────── */

@Composable
fun StructureLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    val st = report.structure
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderLocal(
            "SENTENCE LENGTH",
            "distribution of words per sentence — the shape of the prose rhythm",
            palette,
        )

        HistogramChart(st.histogram, palette)

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("MEAN", num(st.mean, 1), palette, Modifier.weight(1f), hint = "words per sentence")
            MetricTile("MEDIAN", num(st.median, 1), palette, Modifier.weight(1f), hint = "the middle sentence")
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("STDEV", num(st.stdev, 2), palette, Modifier.weight(1f), hint = "sample σ, n−1")
            MetricTile("RANGE", "${st.shortest}–${st.longest}", palette, Modifier.weight(1f), hint = "shortest to longest")
        }

        Spacer(Modifier.height(20.dp))

        ScaleBar(
            value01 = st.rhythm01.toFloat(),
            leftLabel = "EVEN",
            rightLabel = "WILD",
            title = "RHYTHM VARIATION",
            display = num(st.rhythm01 * 100.0, 0) + "%",
            note = "coefficient of variation, σ ÷ mean. Drives how fast the analysis core turns.",
            palette = palette,
            delayIndex = 1,
        )

        if (st.longestSentence.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Hairline(palette)
            Spacer(Modifier.height(18.dp))
            SectionHeaderLocal(
                "LONGEST SENTENCE",
                "${st.longest} words — the hardest single thing a reader has to hold",
                palette,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.panel)
                    .padding(14.dp)
            ) {
                Text(
                    text = "\u201C" + ellipsize(st.longestSentence, 320) + "\u201D",
                    color = palette.textMid,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

/* ── 5. sentiment ─────────────────────────────────────────────────────── */

@Composable
fun SentimentLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    val sent = report.sentiment
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderLocal(
            "TONE",
            "a weighted lexicon tally, not a model — every hit is a listed word with a listed weight",
            palette,
        )

        SentimentDial(sent.valence, palette, Modifier.fillMaxWidth(), delayIndex = 0)

        Spacer(Modifier.height(6.dp))
        Text(
            text = valenceLabel(sent.valence).uppercase(),
            color = palette.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile(
                "POSITIVE HITS",
                count(sent.positiveHits),
                palette,
                Modifier.weight(1f),
                hint = "weighted lexicon matches",
            )
            MetricTile(
                "NEGATIVE HITS",
                count(sent.negativeHits),
                palette,
                Modifier.weight(1f),
                hint = "weighted lexicon matches",
            )
        }

        Spacer(Modifier.height(20.dp))

        ScaleBar(
            value01 = sent.volatility01.toFloat(),
            leftLabel = "STEADY",
            rightLabel = "SWINGING",
            title = "EMOTIONAL VOLATILITY",
            display = num(sent.volatility01 * 100.0, 0) + "%",
            note = "half the distance between the warmest and coldest window. Sets how far the core's lobes reach.",
            palette = palette,
            delayIndex = 1,
        )

        Spacer(Modifier.height(24.dp))
        Hairline(palette)
        Spacer(Modifier.height(18.dp))

        SectionHeaderLocal(
            "EMOTIONAL ARC",
            "valence across ${sent.arc.size} windows from the first word to the last",
            palette,
        )
        ArcChart(sent.arc, palette)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabelText("START", palette)
            LabelText("MIDDLE", palette)
            LabelText("END", palette)
        }
    }
}

/* ── 6. frequency ─────────────────────────────────────────────────────── */

@Composable
fun FrequencyLayer(report: PrismReport, palette: PrismPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderLocal(
            "WORD FREQUENCY",
            "rank-ordered, with each word's deviation from the share Zipf's law predicts for its rank",
            palette,
        )

        if (report.frequency.isEmpty()) {
            BodyText("No words to rank.", palette)
            return
        }

        FrequencyBars(report.frequency, palette)

        Spacer(Modifier.height(20.dp))
        Hairline(palette)
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.panel)
                .padding(14.dp)
        ) {
            Column {
                LabelText("READING THE MARKERS", palette, emphasized = true)
                Spacer(Modifier.height(6.dp))
                BodyText(
                    "Zipf's law says the nth most common word should appear about 1/n as often as " +
                        "the most common one. The marker beside each word shows how far this text " +
                        "departs from that: to the right means the word is over-used for its rank, " +
                        "to the left means under-used. Content words in the accent colour sitting " +
                        "far right usually indicates a text that keeps returning to one subject.",
                    palette,
                    size = 11.5.toInt(),
                )
            }
        }
    }
}

/* ── shared ───────────────────────────────────────────────────────────── */

@Composable
private fun SectionHeaderLocal(title: String, note: String, palette: PrismPalette) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        LabelText(title, palette, emphasized = true)
        Spacer(Modifier.height(4.dp))
        BodyText(note, palette, size = 11)
    }
}
