package dev.prism.analysis.engine

import kotlin.math.max

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — syllable estimation

   Five of the six readability formulas divide by syllables per word, so
   this heuristic quietly governs most of what the app reports. Getting it
   exactly right needs a pronouncing dictionary; getting it *systematically*
   right needs only a few rules, and the classic vowel-group count with
   silent-e handling lands within a few percent on ordinary prose.

   Known misses, stated rather than hidden: words whose only vowel is a
   syllabic consonant ("rhythm" → 1 here, 2 in fact), and loanwords that
   keep a sounded terminal e ("recipe"). Both are rare enough not to move a
   grade-level estimate, and every test in TextAnalyzerTest pins the cases
   that do matter.
   ═══════════════════════════════════════════════════════════════════════ */

object Syllables {

    private const val VOWELS = "aeiouy"

    fun count(word: String): Int {
        val w = word.lowercase().filter(Char::isLetter)
        if (w.isEmpty()) return 0
        /* short words are overwhelmingly monosyllabic, and running the group
           logic on them only introduces errors ("one", "the", "are") */
        if (w.length <= 3) return 1

        var groups = 0
        var prevVowel = false
        for (c in w) {
            val isVowel = c in VOWELS
            if (isVowel && !prevVowel) groups++
            prevVowel = isVowel
        }
        if (groups == 0) return 1

        /* "-le" after a consonant carries its own syllable: ap-ple, ta-ble,
           syl-la-ble. That has to be known before the silent-e rule, or the
           rule strips a syllable that is genuinely pronounced. */
        val leIsSyllabic = w.endsWith("le") && w.length >= 3 && w[w.length - 3] !in VOWELS

        /* a terminal 'e' is usually silent: make, hope, decide */
        if (w.endsWith("e") && !leIsSyllabic && groups > 1) groups--

        /* "-ed" is usually silent too (walked), but not after t or d, where it
           has to be pronounced to keep the two consonants apart (wanted,
           decided). The vowel check stops "agreed" losing a syllable it keeps. */
        if (w.endsWith("ed") && groups > 1 && w.length >= 3) {
            val before = w[w.length - 3]
            if (before !in VOWELS && before != 't' && before != 'd') groups--
        }

        return max(1, groups)
    }

    fun countAll(words: List<String>): Int = words.sumOf { count(it) }
}

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — tokenising

   Sentence splitting is the fiddly half. A naive split on [.!?] shreds
   "Dr. Smith" and "3.14" into three sentences each, which inflates the
   sentence count and depresses every readability score at once. So the
   common cases are protected first, the split happens on placeholders, and
   the placeholders are put back.
   ═══════════════════════════════════════════════════════════════════════ */

object Tokenizer {

    private val WORD = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")

    /* Titles and abbreviations that take a full stop without ending a
       sentence. Deliberately short: a long list starts swallowing real
       sentence boundaries, and these are the ones that actually appear. */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "al",
        "inc", "ltd", "co", "corp", "dept", "univ", "assn", "bros", "ph",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec", "fig", "pp", "vol", "chap", "approx",
    )
    /* "no" is conspicuously absent. It belongs in the usual abbreviation lists
       (No. 4), but \bno\. also matches the end of an ordinary sentence --
       "I said no." -- and swallowing that boundary silently deflates every
       readability score in the app at once. Losing "No." is the cheaper error. */

    fun words(text: String): List<String> =
        WORD.findAll(text).map { it.value }.toList()

    /** Lowercased words, which is what every frequency and lexicon statistic
     *  needs. Kept separate from [words] so syllable counting can see the
     *  original casing where it matters. */
    fun lowerWords(text: String): List<String> =
        WORD.findAll(text).map { it.value.lowercase() }.toList()

    fun sentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        var work = text

        /* decimal and thousands separators: 3.14, 1.000, 12,345.67 */
        work = work.replace(Regex("(?<=\\d)[.,](?=\\d)"), "\u0001")

        /* abbreviations, matched case-insensitively and only when followed by
           a stop, so "Dr." is protected but "dr" in ordinary text is not */
        for (a in ABBREVIATIONS) {
            /* replace with the matched text itself, not the list entry: the list
               is lowercase and "Dr." must not come back out as "dr." */
            work = Regex("(?i)\\b$a\\.(?=\\s|$)").replace(work) { mr ->
                mr.value.dropLast(1) + "\u0002"
            }
        }

        /* initials: "E. B. White" */
        work = work.replace(Regex("\\b\\p{Lu}\\.(?=\\s|$)"), "\u0003")

        /* ellipsis is one pause, not three sentence ends */
        work = work.replace(Regex("\\.{2,}"), "\u0004")

        val parts = work
            .split(Regex("(?<=[.!?])\\s+"))
            .map { restore(it) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && WORD.containsMatchIn(it) }

        return parts.ifEmpty { listOf(text.trim()).filter { it.isNotEmpty() } }
    }

    private fun restore(s: String): String = s
        .replace('\u0001', '.')
        .replace('\u0002', '.')
        .replace('\u0003', '.')
        .replace('\u0004', '…')

    /** Non-blank lines, collapsed — a paragraph is whatever the author broke. */
    fun paragraphs(text: String): List<String> =
        text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Only letters count toward Coleman-Liau, which is defined on letters
     *  rather than characters, so punctuation must not inflate it. */
    fun letterCount(text: String): Int = text.count { it.isLetter() }
}
