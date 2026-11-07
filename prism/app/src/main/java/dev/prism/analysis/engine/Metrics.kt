package dev.prism.analysis.engine

import kotlin.math.ln
import kotlin.math.sqrt

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — the analysis result model

   Every field here is something the UI actually draws, and every value is
   produced by a published formula rather than invented to look plausible.
   Nothing in this file imports android.*, which is what lets the engine be
   tested on the JVM in CI from an environment that cannot run a device.
   ═══════════════════════════════════════════════════════════════════════ */

data class SurfaceCounts(
    val characters: Int,
    val charactersNoSpaces: Int,
    val words: Int,
    val sentences: Int,
    val paragraphs: Int,
    val syllables: Int,
    val letters: Int,
    /** silent reading at ~238 wpm, the commonly cited adult average */
    val readingSeconds: Double,
    /** reading aloud at ~150 wpm */
    val speakingSeconds: Double,
)

data class Readability(
    /** 0–100 in theory; higher is easier. Can exceed the range on odd input. */
    val fleschReadingEase: Double,
    /** US school grade */
    val fleschKincaidGrade: Double,
    /** years of education needed; weighted on 3+-syllable words */
    val gunningFog: Double,
    /** Simple Measure of Gobbledygook; 30-sentence normalisation */
    val smog: Double,
    /** derived from character counts rather than syllables */
    val colemanLiau: Double,
    /** Automated Readability Index */
    val ari: Double,
    /** words with three or more syllables, as a fraction of all words */
    val complexWordRatio: Double,
    val complexWords: Int,
    val polysyllables: Int,
) {
    /** 0..1, where 1 is the hardest text these six scores describe.
     *  The UI uses it to drive the core's pulse and the accent's warmth. */
    val difficulty01: Double
        get() {
            val fre = (fleschReadingEase / 100.0).coerceIn(0.0, 1.0)
            val grade = (fleschKincaidGrade / 18.0).coerceIn(0.0, 1.0)
            val fog = (gunningFog / 20.0).coerceIn(0.0, 1.0)
            return (((1.0 - fre) + grade + fog) / 3.0).coerceIn(0.0, 1.0)
        }
}

data class LexiconStats(
    /** distinct tokens */
    val unique: Int,
    /** tokens occurring exactly once */
    val hapaxLegomena: Int,
    /** tokens occurring exactly twice */
    val disLegomena: Int,
    val typeTokenRatio: Double,
    /** Yule's K: vocabulary richness, largely independent of text length */
    val yulesK: Double,
    /** Herdan's C: log(V)/log(N) */
    val herdansC: Double,
    /** Honoré's R: rewards texts that keep introducing new words */
    val honoresR: Double,
    val meanWordLength: Double,
    val meanSyllablesPerWord: Double,
    /** 0..1 normalised richness, for the UI's single dial */
    val richness01: Double,
)

data class SentenceLength(
    val words: Int,
    val syllables: Int,
    val text: String,
)

data class StructureStats(
    val lengths: List<Int>,
    val mean: Double,
    val median: Double,
    val stdev: Double,
    val shortest: Int,
    val longest: Int,
    /** histogram buckets of sentence word-counts, for the animated bars */
    val histogram: List<HistogramBucket>,
    val longestSentence: String,
    /** 0..1: how much the prose rhythm varies. Flat writing scores low. */
    val rhythm01: Double,
)

data class HistogramBucket(
    val label: String,
    val from: Int,
    /** inclusive upper bound; null means open-ended, i.e. the "31+" bucket */
    val to: Int?,
    val count: Int,
)

data class SentimentStats(
    /** −1..+1 across the whole text */
    val valence: Double,
    val positiveHits: Int,
    val negativeHits: Int,
    /** valence per window across the text, for the emotional arc */
    val arc: List<ArcPoint>,
    /** how far the text swings, 0..1 */
    val volatility01: Double,
)

data class ArcPoint(
    /** 0..1 position through the text */
    val t: Double,
    val valence: Double,
)

data class WordFrequency(
    val word: String,
    val count: Int,
    val isStopword: Boolean,
    /** observed share versus the share Zipf's law predicts for this rank */
    val zipfDeviation: Double,
)

data class PrismReport(
    val surface: SurfaceCounts,
    val readability: Readability,
    val lexicon: LexiconStats,
    val structure: StructureStats,
    val sentiment: SentimentStats,
    val frequency: List<WordFrequency>,
    /** true when there is nothing to analyse; every UI path checks this */
    val isEmpty: Boolean,
) {
    companion object {
        /** A report for empty input. Returned rather than throwing, because an
         *  editor that starts empty is the normal state, not an error. */
        val EMPTY = PrismReport(
            surface = SurfaceCounts(0, 0, 0, 0, 0, 0, 0, 0.0, 0.0),
            readability = Readability(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0),
            lexicon = LexiconStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            structure = StructureStats(emptyList(), 0.0, 0.0, 0.0, 0, 0, emptyList(), "", 0.0),
            sentiment = SentimentStats(0.0, 0, 0, emptyList(), 0.0),
            frequency = emptyList(),
            isEmpty = true,
        )
    }
}

/* ── small math helpers shared by the formulas ────────────────────────── */

internal fun mean(xs: List<Double>): Double =
    if (xs.isEmpty()) 0.0 else xs.sum() / xs.size

internal fun median(xs: List<Double>): Double {
    if (xs.isEmpty()) return 0.0
    val s = xs.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2.0 else s[mid].toDouble()
}

internal fun stdev(xs: List<Double>): Double {
    if (xs.size < 2) return 0.0
    val m = mean(xs)
    return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
}

/** natural log guarded against 0 and negatives, which the richness statistics
 *  reach constantly on short input */
internal fun safeLn(x: Double): Double = if (x <= 0.0) 0.0 else ln(x)
