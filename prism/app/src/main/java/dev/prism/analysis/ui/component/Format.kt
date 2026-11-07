package dev.prism.analysis.ui.component

import kotlin.math.abs
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
   Formatting.

   Written by hand rather than via String.format so the output does not
   depend on the device locale: a decimal separator that changes from "." to
   "," under a European locale would break the tabular alignment every one of
   these readouts relies on, and would make the numbers in the UI disagree
   with the numbers in the tests.
   ═══════════════════════════════════════════════════════════════════════ */

/** Fixed-decimal formatting with an explicit sign only when asked. */
fun num(value: Double, dp: Int = 2, signed: Boolean = false): String {
    if (value.isNaN()) return "—"
    if (value.isInfinite()) return if (value > 0) "∞" else "−∞"
    val factor = pow10(dp)
    val scaled = (value * factor).roundToInt()
    val negative = scaled < 0
    val sign = when {
        negative -> "−"
        signed && value > 0 -> "+"
        else -> ""
    }
    return if (dp == 0) {
        sign + group(abs(scaled).toString())
    } else {
        val intPart = (abs(scaled) / pow10(dp)).toString()
        val fracPart = (abs(scaled) % pow10(dp)).toString().padStart(dp, '0')
        sign + group(intPart) + "." + fracPart
    }
}

private fun pow10(dp: Int): Int {
    var p = 1
    repeat(dp) { p *= 10 }
    return p
}

/** Insert thin grouping separators. Locale-independent on purpose. */
fun group(digits: String): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    var count = 0
    for (i in digits.indices.reversed()) {
        sb.append(digits[i])
        count++
        if (count % 3 == 0 && i > 0) sb.append('\u2009') // thin space
    }
    return sb.reverse().toString()
}

fun count(value: Int): String = group(value.toString())

/** Seconds → "1m 23s" / "4.2s" / "1h 05m". */
fun duration(seconds: Double): String {
    if (seconds.isNaN() || seconds <= 0) return "0s"
    val total = seconds.roundToInt()
    return when {
        total < 60 -> "${total}s"
        total < 3600 -> {
            val m = total / 60
            val s = total % 60
            if (s == 0) "${m}m" else "${m}m ${s.toString().padStart(2, '0')}s"
        }
        else -> {
            val h = total / 3600
            val m = (total % 3600) / 60
            if (m == 0) "${h}h" else "${h}h ${m.toString().padStart(2, '0')}m"
        }
    }
}

/** A grade number → the band a reader would recognise. The formulas emit US
 *  school grades and can go negative on very simple input, which is a real
 *  result rather than an error, so it needs a label rather than a clamp. */
fun gradeLabel(grade: Double): String = when {
    grade.isNaN() -> "—"
    grade < 0.0 -> "pre-school"
    grade < 6.0 -> "grade " + (grade + 1).toInt().coerceAtLeast(1)
    grade < 7.0 -> "grade 6"
    grade < 12.0 -> "grade " + grade.roundToInt().coerceIn(7, 12)
    grade < 13.0 -> "grade 12"
    grade < 16.0 -> "undergrad"
    grade < 18.0 -> "postgrad"
    else -> "specialist"
}

/** Flesch Reading Ease → band. The published ranges, not invented ones. */
fun freLabel(fre: Double): String = when {
    fre.isNaN() -> "—"
    fre >= 90 -> "very easy"
    fre >= 80 -> "easy"
    fre >= 70 -> "fairly easy"
    fre >= 60 -> "plain"
    fre >= 50 -> "fairly difficult"
    fre >= 30 -> "difficult"
    else -> "very difficult"
}

/** Sentiment valence → word. */
fun valenceLabel(v: Double): String = when {
    v.isNaN() -> "neutral"
    v > 0.45 -> "very warm"
    v > 0.15 -> "warm"
    v > 0.05 -> "mildly warm"
    v >= -0.05 -> "neutral"
    v >= -0.15 -> "mildly cold"
    v >= -0.45 -> "cold"
    else -> "very cold"
}

/** Lexical richness → word, keyed on Herdan's C. */
fun richnessLabel(c: Double): String = when {
    c.isNaN() -> "—"
    c >= 0.72 -> "very varied"
    c >= 0.62 -> "varied"
    c >= 0.52 -> "moderate"
    c >= 0.40 -> "repetitive"
    else -> "very repetitive"
}

/** Truncate a sentence for display without breaking mid-word. */
fun ellipsize(text: String, max: Int): String {
    val flat = text.replace('\n', ' ').trim()
    if (flat.length <= max) return flat
    val cut = flat.substring(0, max)
    val lastSpace = cut.lastIndexOf(' ')
    val head = if (lastSpace > max * 0.6) cut.substring(0, lastSpace) else cut
    return head.trimEnd() + "…"
}
