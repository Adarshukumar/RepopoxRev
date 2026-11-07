package dev.prism.analysis.engine

import kotlin.math.sqrt

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — the analysis engine

   One pure function: String -> PrismReport. No Android imports, no state,
   no I/O, which is why CI can execute it on a JVM and assert the numbers.

   Every formula below is a published one and is written out longhand with
   its coefficients visible rather than folded into a helper, because the
   point of the app is that the reader can check the arithmetic.

   Guarding matters more than it looks. Short or empty input drives most of
   these through a division by zero, and the natural result — NaN — then
   propagates into the Compose layout as a zero-size box that renders
   nothing and reports no error. Every path here returns a finite number.
   ═══════════════════════════════════════════════════════════════════════ */

object TextAnalyzer {

    /** Sentences per histogram bucket. Fixed so the bars are comparable
     *  between two different texts. */
    private val BUCKETS = listOf(
        Triple("1–5", 1, 5),
        Triple("6–10", 6, 10),
        Triple("11–15", 11, 15),
        Triple("16–20", 16, 20),
        Triple("21–30", 21, 30),
        Triple("31+", 31, Int.MAX_VALUE),
    )

    private const val TOP_WORDS = 14
    private const val ARC_WINDOWS = 12

    fun analyze(raw: String): PrismReport {
        val text = raw.trim()
        if (text.isEmpty()) return PrismReport.EMPTY

        val sentences = Tokenizer.sentences(text)
        val words = Tokenizer.words(text)
        val lower = words.map { it.lowercase() }
        val n = words.size

        /* Nothing to analyse: punctuation only, or a single stray mark. The
           rest of this function divides by these counts throughout. */
        if (n == 0 || sentences.isEmpty()) return PrismReport.EMPTY

        val perSentence = sentences.map { Tokenizer.words(it) }
        /* kept index-aligned with `sentences` on purpose: longestIdx below is
           used to look a sentence up by position, and filtering here would
           shift every index after the first dropped element. Tokenizer already
           guarantees each sentence contains at least one word. */
        val sentenceWordCounts = perSentence.map { it.size }
        val s = sentenceWordCounts.size.coerceAtLeast(1)

        val syllableCounts = words.map { Syllables.count(it) }
        val totalSyllables = syllableCounts.sum()
        val letters = Tokenizer.letterCount(text)
        val chars = text.length
        val charsNoSpaces = text.count { !it.isWhitespace() }

        /* ── surface ─────────────────────────────────────────────────── */
        val surface = SurfaceCounts(
            characters = chars,
            charactersNoSpaces = charsNoSpaces,
            words = n,
            sentences = s,
            paragraphs = Tokenizer.paragraphs(text).size.coerceAtLeast(1),
            syllables = totalSyllables,
            letters = letters,
            readingSeconds = n / 238.0 * 60.0,
            speakingSeconds = n / 150.0 * 60.0,
        )

        /* ── readability ─────────────────────────────────────────────── */
        val wps = n.toDouble() / s                    // words per sentence
        val spw = totalSyllables.toDouble() / n       // syllables per word

        val complexWords = syllableCounts.count { it >= 3 }
        /* SMOG counts polysyllables specifically, and its original definition
           excludes proper nouns; without a tagger the honest thing is to
           count them all and say so in the UI. */
        val polysyllables = complexWords

        val fleschReadingEase = 206.835 - 1.015 * wps - 84.6 * spw
        val fleschKincaid = 0.39 * wps + 11.8 * spw - 15.59
        val gunningFog = 0.4 * (wps + 100.0 * (complexWords.toDouble() / n))
        /* SMOG is defined on 30 sentences; scaling by 30/s extrapolates from
           whatever the text actually contains */
        val smog = 1.0430 * sqrt(polysyllables * (30.0 / s)) + 3.1291
        val lettersPer100 = letters.toDouble() / n * 100.0
        val sentencesPer100 = s.toDouble() / n * 100.0
        val colemanLiau = 0.0588 * lettersPer100 - 0.296 * sentencesPer100 - 15.8
        /* ARI is defined on letters, not on every non-space character. Using
           charsNoSpaces silently adds punctuation to the numerator, which
           pushes the grade up in proportion to how heavily the author
           punctuates -- the exact opposite of what the index measures. */
        val ari = 4.71 * (letters.toDouble() / n) + 0.5 * wps - 21.43

        val readability = Readability(
            fleschReadingEase = round2(fleschReadingEase),
            fleschKincaidGrade = round2(fleschKincaid),
            gunningFog = round2(gunningFog),
            smog = round2(smog),
            colemanLiau = round2(colemanLiau),
            ari = round2(ari),
            complexWordRatio = round2(complexWords.toDouble() / n),
            complexWords = complexWords,
            polysyllables = polysyllables,
        )

        /* ── lexicon ─────────────────────────────────────────────────── */
        val freq = lower.groupingBy { it }.eachCount()
        val v = freq.size                          // distinct tokens
        val hapax = freq.values.count { it == 1 }  // occurring once
        val dis = freq.values.count { it == 2 }

        /* Yule's K. Builds the frequency spectrum V(i,N) — how many words
           occur exactly i times — then M = Σ i²·V(i,N). K is largely
           independent of text length, which TTR is not, and that is the
           whole reason to compute it. */
        val spectrum = freq.values.groupingBy { it }.eachCount()
        var m = 0.0
        for ((i, vi) in spectrum) m += i.toDouble() * i * vi
        val yulesK = if (n > 0) 1e4 * (m - n) / (n.toDouble() * n) else 0.0

        val herdansC = if (n > 1 && v > 1) safeLn(v.toDouble()) / safeLn(n.toDouble()) else 0.0

        /* Honoré's R blows up when every word is unique (V1 == V), which is
           exactly what a very short text does. Clamp rather than emit
           Infinity into the UI. */
        val honoresR = if (v > 1 && hapax < v) {
            100.0 * safeLn(n.toDouble()) / (1.0 - hapax.toDouble() / v)
        } else {
            0.0
        }

        val lexicon = LexiconStats(
            unique = v,
            hapaxLegomena = hapax,
            disLegomena = dis,
            typeTokenRatio = round2(v.toDouble() / n),
            yulesK = round2(yulesK.coerceIn(-1e6, 1e6)),
            herdansC = round2(herdansC.coerceIn(0.0, 1.0)),
            honoresR = round2(honoresR.coerceIn(-1e6, 1e6)),
            meanWordLength = round2(lower.sumOf { it.length }.toDouble() / n),
            meanSyllablesPerWord = round2(spw),
            /* Herdan's C is bounded 0..1 and reads as a dial directly; Yule's K
               is unbounded, so it is squashed for the single visual */
            richness01 = round2(herdansC.coerceIn(0.0, 1.0)),
        )

        /* ── structure ───────────────────────────────────────────────── */
        val lengthsD = sentenceWordCounts.map { it.toDouble() }
        val meanLen = mean(lengthsD)
        val sd = stdev(lengthsD)
        /* Coefficient of variation, capped: it is the honest measure of prose
           rhythm, because a long text naturally has a larger stdev than a
           short one at the same variability. */
        val rhythm = if (meanLen > 0) (sd / meanLen).coerceIn(0.0, 1.0) else 0.0

        val histogram = BUCKETS.map { (label, from, to) ->
            val openEnded = to == Int.MAX_VALUE
            HistogramBucket(
                label = label,
                from = from,
                to = if (openEnded) null else to,
                count = sentenceWordCounts.count { it >= from && (openEnded || it <= to) },
            )
        }

        val longestIdx = sentenceWordCounts.indices.maxByOrNull { sentenceWordCounts[it] } ?: 0
        val longestSentence = sentences.getOrNull(longestIdx)?.let {
            if (it.length > 160) it.take(157).trimEnd() + "…" else it
        } ?: ""

        val structure = StructureStats(
            lengths = sentenceWordCounts,
            mean = round2(meanLen),
            median = round2(median(lengthsD)),
            stdev = round2(sd),
            shortest = sentenceWordCounts.minOrNull() ?: 0,
            longest = sentenceWordCounts.maxOrNull() ?: 0,
            histogram = histogram,
            longestSentence = longestSentence,
            rhythm01 = round2(rhythm),
        )

        /* ── sentiment ───────────────────────────────────────────────── */
        val (posHits, negHits) = Sentiment.tally(lower)
        val valence = Sentiment.score(lower)

        /* The arc is the interesting output: windows of sentences, scored
           separately, so a text that starts bleak and ends warm shows as a
           rising line rather than averaging out to nothing. */
        val windowSize = ((perSentence.size + ARC_WINDOWS - 1) / ARC_WINDOWS).coerceAtLeast(1)
        val arc = mutableListOf<ArcPoint>()
        var idx = 0
        while (idx < perSentence.size) {
            val end = (idx + windowSize).coerceAtMost(perSentence.size)
            val windowWords = perSentence.subList(idx, end).flatten().map { it.lowercase() }
            val centre = (idx + end) / 2.0
            arc += ArcPoint(
                t = round2(centre / perSentence.size.coerceAtLeast(1)),
                valence = round2(Sentiment.score(windowWords)),
            )
            idx = end
        }

        val arcValues = arc.map { it.valence }
        val volatility = if (arcValues.size > 1) {
            ((arcValues.max() - arcValues.min()) / 2.0).coerceIn(0.0, 1.0)
        } else 0.0

        val sentiment = SentimentStats(
            valence = round2(valence),
            positiveHits = posHits,
            negativeHits = negHits,
            arc = arc,
            volatility01 = round2(volatility),
        )

        /* ── frequency ───────────────────────────────────────────────── */
        /* Zipf's law predicts frequency proportional to 1/rank. Comparing the
           observed share against that is a real signal: prose hugging the line
           is ordinary, and prose well above it at rank 1 is repetitive. */
        val ranked = freq.entries.sortedByDescending { it.value }
        val totalFreq = ranked.sumOf { it.value }.toDouble()
        val zipfWeights = ranked.indices.map { 1.0 / (it + 1) }
        val zipfTotal = zipfWeights.sum()
        val frequency = ranked.take(TOP_WORDS).mapIndexed { i, e ->
            val observed = if (totalFreq > 0) e.value / totalFreq else 0.0
            val expected = if (zipfTotal > 0) zipfWeights[i] / zipfTotal else 0.0
            WordFrequency(
                word = e.key,
                count = e.value,
                isStopword = Stopwords.isStopword(e.key),
                zipfDeviation = round2(observed - expected),
            )
        }

        return PrismReport(
            surface = surface,
            readability = readability,
            lexicon = lexicon,
            structure = structure,
            sentiment = sentiment,
            frequency = frequency,
            isEmpty = false,
        )
    }

    private fun round2(x: Double): Double {
        if (!x.isFinite()) return 0.0
        return Math.round(x * 100.0) / 100.0
    }
}
