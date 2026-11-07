package dev.prism.analysis.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — interpretation

   Numbers on a dashboard are not analysis. This turns a report into plain
   sentences that a reader can act on, and it is deliberately conservative:
   every claim is computed from the report, nothing is templated filler, and
   where the formulas disagree the disagreement is the headline rather than
   something smoothed away.

   It lives in the engine package, not the UI, for one reason — it is pure
   Kotlin with no Android import, so CI can assert on its output the same way
   it asserts on the arithmetic. A sentence that says "grade 11" when the
   numbers say 14 is a bug, and it should fail a test.
   ═══════════════════════════════════════════════════════════════════════ */

object Interpretation {

    /**
     * @return three to six sentences, most significant first. Never empty for
     *   a non-empty report; empty for an empty one.
     */
    fun compose(report: PrismReport): List<String> {
        if (report.isEmpty) return emptyList()
        val out = ArrayList<String>(6)

        out += scale(report)
        agreement(report)?.let { out += it }
        out += vocabulary(report)
        rhythm(report)?.let { out += it }
        tone(report)?.let { out += it }
        caution(report)?.let { out += it }

        return out
    }

    /* ── length and grade ─────────────────────────────────────────────── */

    private fun scale(r: PrismReport): String {
        val w = r.surface.words
        val s = r.surface.sentences
        val grade = r.readability.fleschKincaidGrade
        val words = plural(w, "word")
        val sentences = plural(s, "sentence")
        val level = when {
            grade < 6 -> "primary school"
            grade < 9 -> "middle school"
            grade < 13 -> "secondary school"
            grade < 16 -> "undergraduate"
            grade < 18 -> "postgraduate"
            else -> "specialist"
        }
        val secs = r.surface.readingSeconds.roundToInt()
        val time = when {
            secs < 60 -> "about $secs seconds"
            secs < 3600 -> "about ${(secs / 60f).roundToInt()} minutes"
            else -> "over an hour"
        }
        return "$words in $sentences. It reads at roughly $level level and takes $time to read silently."
    }

    /**
     * The six readability formulas are fitted on different corpora and can
     * disagree by several grades. Reporting only their mean hides that, so
     * the spread is stated whenever it is large enough to change the answer.
     */
    private fun agreement(r: PrismReport): String? {
        val scores = listOf(
            r.readability.fleschKincaidGrade,
            r.readability.gunningFog,
            r.readability.smog,
            r.readability.colemanLiau,
            r.readability.ari,
        ).filter { it.isFinite() }
        if (scores.size < 2) return null
        val lo = scores.min()
        val hi = scores.max()
        val spread = hi - lo
        val spreadStr = format(spread, 1)
        return when {
            spread < 1.5 ->
                "The five grade-level formulas agree closely, within $spreadStr of a grade."
            spread < 4.0 ->
                "The grade-level formulas span $spreadStr grades (${format(lo, 1)} to ${format(hi, 1)}), " +
                    "so treat the level as a range rather than a number."
            else ->
                "The grade-level formulas disagree sharply — ${format(lo, 1)} to ${format(hi, 1)}, a spread of " +
                    "$spreadStr grades. That usually means unusual input: very long sentences, heavy " +
                    "jargon, or text that is not continuous prose."
        }
    }

    /* ── vocabulary ───────────────────────────────────────────────────── */

    private fun vocabulary(r: PrismReport): String {
        val lex = r.lexicon
        val c = lex.herdansC
        val band = when {
            c >= 0.72 -> "very varied"
            c >= 0.62 -> "varied"
            c >= 0.52 -> "moderately varied"
            c >= 0.40 -> "repetitive"
            else -> "highly repetitive"
        }
        val hapax = lex.hapaxLegomena
        val total = r.surface.words
        val pct = if (total > 0) (hapax * 100.0 / total).roundToInt() else 0
        val once = if (hapax > 0) {
            " $hapax words (${pct}%) appear exactly once."
        } else {
            " No word appears only once, which is unusual outside very short or formulaic text."
        }
        val avgLen = format(lex.meanWordLength, 1)
        return "Vocabulary is $band (Herdan's C ${format(c, 2)}), averaging $avgLen letters per word.$once"
    }

    /* ── sentence rhythm ──────────────────────────────────────────────── */

    private fun rhythm(r: PrismReport): String? {
        val st = r.structure
        if (st.lengths.size < 2) return null
        val mean = format(st.mean, 1)
        val longest = st.longest
        val shortest = st.shortest
        val cv = st.rhythm01
        val verdict = when {
            cv < 0.20 -> "The rhythm is very even — nearly every sentence is the same length, which reads as flat or mechanical."
            cv < 0.40 -> "Sentence length varies a little."
            cv < 0.65 -> "Sentence length varies considerably, which usually helps readability."
            else -> "Sentence length swings widely; the mix of very short and very long sentences is doing a lot of the work."
        }
        val sd = if (st.stdev > 0) " (σ ${format(st.stdev, 1)})" else ""
        return "Sentences average $mean words$sd, from $shortest to $longest. $verdict"
    }

    /* ── tone ─────────────────────────────────────────────────────────── */

    private fun tone(r: PrismReport): String? {
        val sent = r.sentiment
        if (sent.positiveHits == 0 && sent.negativeHits == 0) {
            return "No sentiment-bearing words were found, so the tone reads as neutral or the lexicon does not cover this domain."
        }
        val v = sent.valence
        val band = when {
            v > 0.45 -> "strongly warm"
            v > 0.15 -> "warm"
            v > 0.05 -> "mildly warm"
            v >= -0.05 -> "neutral"
            v >= -0.15 -> "mildly cold"
            v >= -0.45 -> "cold"
            else -> "strongly cold"
        }
        val hits = "${sent.positiveHits} positive and ${sent.negativeHits} negative"
        val swing = when {
            sent.volatility01 < 0.15 -> " and holds that tone steadily throughout."
            sent.volatility01 < 0.40 -> ", with some movement through the text."
            else -> ", and shifts substantially from start to end."
        }
        return "Tone is $band ($hits lexicon hits)$swing"
    }

    /* ── honest caveats ───────────────────────────────────────────────── */

    /**
     * Every formula here was fitted on continuous English prose. Saying so
     * when the input clearly is not prose is more useful than letting a
     * confident-looking number stand unqualified.
     */
    private fun caution(r: PrismReport): String? {
        val reasons = ArrayList<String>(3)
        if (r.surface.words < 30) reasons += "the text is very short, so every statistic is unstable"
        if (r.surface.sentences < 3) reasons += "there are fewer than three sentences, so sentence-based formulas are extrapolating"
        if (r.readability.gunningFog > 17 && r.readability.fleschKincaidGrade < 10) {
            reasons += "the formulas disagree in a way that suggests list-like or telegraphic input"
        }
        if (r.lexicon.meanWordLength > 9.0) reasons += "words are unusually long on average, which inflates grade scores"
        if (reasons.isEmpty()) return null
        return "Read with care: " + reasons.joinToString("; ") + "."
    }

    /* ── helpers ──────────────────────────────────────────────────────── */

    private fun plural(n: Int, noun: String): String =
        "$n $noun" + if (n == 1) "" else "s"

    /** Fixed-decimal formatting independent of device locale. */
    private fun format(v: Double, dp: Int): String {
        if (!v.isFinite()) return "—"
        var p = 1
        repeat(dp) { p *= 10 }
        val scaled = (v * p).roundToInt()
        val neg = scaled < 0
        val a = abs(scaled)
        val intPart = (a / p).toString()
        return if (dp == 0) {
            (if (neg) "−" else "") + intPart
        } else {
            val frac = (a % p).toString().padStart(dp, '0')
            (if (neg) "−" else "") + intPart + "." + frac
        }
    }
}
