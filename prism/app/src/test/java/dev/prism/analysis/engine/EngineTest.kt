package dev.prism.analysis.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — engine tests

   These run on the JVM, not on a device: the engine imports nothing from
   android.*, which is what makes that possible. CI executes them, and that
   is the only real verification available for a project authored somewhere
   with no JDK and no emulator.

   Expected values are worked out by hand from the published formulas and
   written into the comments, so a failure points at whichever side of the
   arithmetic actually moved.
   ═══════════════════════════════════════════════════════════════════════ */

class SyllablesTest {

    /* Words of three letters or fewer are short-circuited to one syllable,
       which is right for the overwhelming majority and avoids the vowel-group
       logic mangling "one", "the" and "are". */
    @Test fun shortWordsAreMonosyllabic() {
        for (w in listOf("the", "cat", "sat", "on", "one", "are", "a", "I")) {
            assertEquals("$w should be 1 syllable", 1, Syllables.count(w))
        }
    }

    @Test fun countsVowelGroups() {
        assertEquals(3, Syllables.count("beautiful"))   // beau-ti-ful
        assertEquals(3, Syllables.count("computer"))    // com-pu-ter
        assertEquals(3, Syllables.count("elephant"))    // el-e-phant
        assertEquals(2, Syllables.count("apple"))       // ap-ple
        assertEquals(1, Syllables.count("strengths"))   // one vowel group
    }

    /* "queue" is four consecutive vowels and one syllable. Without the
       group logic it would count four. */
    @Test fun consecutiveVowelsCountOnce() {
        assertEquals(1, Syllables.count("queue"))
    }

    @Test fun silentTerminalEIsRemoved() {
        assertEquals(1, Syllables.count("make"))        // not ma-ke
        assertEquals(1, Syllables.count("hope"))
        assertEquals(2, Syllables.count("decide"))      // de-cide, not de-ci-de
    }

    /* "-le" after a consonant is its own syllable, and the silent-e rule must
       not strip it. This is the interaction most naive counters get wrong. */
    @Test fun leEndingKeepsItsSyllable() {
        assertEquals(2, Syllables.count("apple"))
        assertEquals(2, Syllables.count("table"))
        assertEquals(3, Syllables.count("syllable"))    // syl-la-ble
    }

    @Test fun edEndingIsSilentExceptAfterTOrD() {
        assertEquals(1, Syllables.count("walked"))      // one syllable
        assertEquals(2, Syllables.count("wanted"))      // wan-ted
        assertEquals(3, Syllables.count("decided"))     // de-ci-ded
        /* the vowel guard: "agreed" keeps both of its syllables */
        assertEquals(2, Syllables.count("agreed"))
    }

    @Test fun degenerateInputNeverThrowsOrReturnsZeroForAWord() {
        assertEquals(0, Syllables.count(""))
        assertEquals(0, Syllables.count("12345"))
        assertEquals(0, Syllables.count("..."))
        assertTrue(Syllables.count("rhythm") >= 1)
    }

    @Test fun isCaseInsensitive() {
        assertEquals(Syllables.count("beautiful"), Syllables.count("BEAUTIFUL"))
    }
}

class TokenizerTest {

    @Test fun splitsOnSentenceTerminators() {
        assertEquals(3, Tokenizer.sentences("One. Two. Three.").size)
        assertEquals(2, Tokenizer.sentences("Really? Yes!").size)
    }

    /* A title's full stop is not a sentence end. Getting this wrong inflates
       the sentence count and depresses every readability score at once. */
    @Test fun protectsTitles() {
        assertEquals(1, Tokenizer.sentences("Dr. Smith arrived.").size)
        assertEquals(1, Tokenizer.sentences("Prof. Ada Lovelace spoke.").size)
        assertEquals(2, Tokenizer.sentences("Mr. Jones left. Then it rained.").size)
    }

    /* The abbreviation substitution must not rewrite the author's casing. */
    @Test fun preservesOriginalCasingThroughAbbreviations() {
        val s = Tokenizer.sentences("Dr. Smith arrived.")
        assertEquals(1, s.size)
        assertTrue("expected 'Dr.' to survive, got: ${s[0]}", s[0].startsWith("Dr."))
    }

    @Test fun protectsDecimalNumbers() {
        assertEquals(1, Tokenizer.sentences("Pi is 3.14 approximately.").size)
        assertEquals(1, Tokenizer.sentences("It cost 1.000 exactly.").size)
    }

    /* "no" was deliberately left out of the abbreviation list: \bno\. also
       matches the end of an ordinary sentence, and swallowing that boundary
       is a worse error than mishandling "No. 4". */
    @Test fun doesNotTreatTheWordNoAsAnAbbreviation() {
        assertEquals(1, Tokenizer.sentences("I said no.").size)
        assertEquals(2, Tokenizer.sentences("I said no. Then I left.").size)
    }

    @Test fun treatsEllipsisAsOnePause() {
        assertEquals(1, Tokenizer.sentences("Wait... what?").size)
    }

    @Test fun wordsHandlesContractionsAndHyphens() {
        val w = Tokenizer.words("it's a well-known fact")
        assertEquals(listOf("it's", "a", "well-known", "fact"), w)
    }

    @Test fun countsOnlyLettersForColemanLiau() {
        /* punctuation must not inflate the letter count that Coleman-Liau is
           defined on */
        assertEquals(17, Tokenizer.letterCount("The cat sat on the mat."))
    }

    @Test fun emptyAndBlankInput() {
        assertTrue(Tokenizer.sentences("").isEmpty())
        assertTrue(Tokenizer.sentences("   ").isEmpty())
        assertTrue(Tokenizer.words("").isEmpty())
    }

    @Test fun paragraphsAreNonBlankLines() {
        assertEquals(3, Tokenizer.paragraphs("one\n\ntwo\nthree").size)
    }
}

class TextAnalyzerTest {

    /* "The cat sat on the mat." — 6 words, 1 sentence, 6 syllables, 17 letters.
       wps = 6, spw = 1, so:
         FRE = 206.835 - 1.015*6 - 84.6*1        = 116.145
         FKG = 0.39*6 + 11.8*1 - 15.59           =  -1.45
         Fog = 0.4*(6 + 100*(0/6))               =   2.40
         SMOG = 1.0430*sqrt(0*30) + 3.1291       =   3.129
         CL  = 0.0588*(17/6*100) - 0.296*(1/6*100) - 15.8 = -4.073
         ARI = 4.71*(17/6) + 0.5*6 - 21.43       =  -5.085
       Every one of these is exact enough to pin with a small delta. */
    @Test fun matchesHandComputedReadabilityForASimpleSentence() {
        val r = TextAnalyzer.analyze("The cat sat on the mat.")
        assertFalse(r.isEmpty)
        assertEquals(6, r.surface.words)
        assertEquals(1, r.surface.sentences)
        assertEquals(6, r.surface.syllables)
        assertEquals(17, r.surface.letters)

        assertEquals(116.145, r.readability.fleschReadingEase, 0.02)
        assertEquals(-1.45, r.readability.fleschKincaidGrade, 0.02)
        assertEquals(2.40, r.readability.gunningFog, 0.02)
        assertEquals(3.129, r.readability.smog, 0.02)
        assertEquals(-4.073, r.readability.colemanLiau, 0.02)
        assertEquals(-5.085, r.readability.ari, 0.02)
        assertEquals(0, r.readability.complexWords)
    }

    /* ARI counts letters, not every non-space character. The distinction only
       shows up once there is punctuation, so it needs its own test: a comma-
       heavy sentence would score higher than a plain one if this regressed. */
    @Test fun ariUsesLettersNotPunctuation() {
        val plain = TextAnalyzer.analyze("The cat sat on the mat")
        val punctuated = TextAnalyzer.analyze("The cat, sat, on, the, mat")
        assertEquals(plain.readability.ari, punctuated.readability.ari, 0.001)
    }

    @Test fun emptyInputReturnsTheEmptyReport() {
        assertTrue(TextAnalyzer.analyze("").isEmpty)
        assertTrue(TextAnalyzer.analyze("   \n\t ").isEmpty)
        assertTrue(TextAnalyzer.analyze("...?!").isEmpty)
    }

    /* The single most important property in the whole engine: NaN reaching a
       Compose layout produces a zero-size box that renders nothing and
       reports no error, so it has to be impossible rather than unlikely. */
    @Test fun neverProducesNonFiniteValues() {
        val hostile = listOf(
            "", " ", "a", "I", ".", "...", "123", "123 456",
            "The.", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "One two three four five six seven eight nine ten eleven twelve.",
            "\n\n\n", "— – -", "e.e. cummings wrote poetry",
            String(CharArray(2000) { 'x' }),
        )
        for (text in hostile) {
            val r = TextAnalyzer.analyze(text)
            assertNotNull(r)
            assertFinite("fleschReadingEase", r.readability.fleschReadingEase, text)
            assertFinite("fleschKincaidGrade", r.readability.fleschKincaidGrade, text)
            assertFinite("gunningFog", r.readability.gunningFog, text)
            assertFinite("smog", r.readability.smog, text)
            assertFinite("colemanLiau", r.readability.colemanLiau, text)
            assertFinite("ari", r.readability.ari, text)
            assertFinite("complexWordRatio", r.readability.complexWordRatio, text)
            assertFinite("typeTokenRatio", r.lexicon.typeTokenRatio, text)
            assertFinite("yulesK", r.lexicon.yulesK, text)
            assertFinite("herdansC", r.lexicon.herdansC, text)
            assertFinite("honoresR", r.lexicon.honoresR, text)
            assertFinite("meanWordLength", r.lexicon.meanWordLength, text)
            assertFinite("meanSyllablesPerWord", r.lexicon.meanSyllablesPerWord, text)
            assertFinite("richness01", r.lexicon.richness01, text)
            assertFinite("structure.mean", r.structure.mean, text)
            assertFinite("structure.median", r.structure.median, text)
            assertFinite("structure.stdev", r.structure.stdev, text)
            assertFinite("structure.rhythm01", r.structure.rhythm01, text)
            assertFinite("sentiment.valence", r.sentiment.valence, text)
            assertFinite("sentiment.volatility01", r.sentiment.volatility01, text)
            r.sentiment.arc.forEachIndexed { i, p ->
                assertFinite("arc[$i].t", p.t, text)
                assertFinite("arc[$i].valence", p.valence, text)
            }
            r.frequency.forEach { assertFinite("zipf[${it.word}]", it.zipfDeviation, text) }
        }
    }

    private fun assertFinite(field: String, value: Double, input: String) {
        assertTrue("$field was $value for input ${describe(input)}", value.isFinite())
    }

    private fun describe(s: String) =
        if (s.length > 40) "\"" + s.take(40) + "…\" (" + s.length + " chars)" else "\"$s\""

    @Test fun aSingleWordDoesNotDivideByZero() {
        val r = TextAnalyzer.analyze("Hello")
        assertFalse(r.isEmpty)
        assertEquals(1, r.surface.words)
        assertEquals(1, r.surface.sentences)
        assertTrue(r.readability.fleschReadingEase.isFinite())
    }

    /* Every word unique: TTR is 1.0 and hapax equals the vocabulary.
       Honore's R has a 1 - V1/V denominator that is exactly zero here, so
       this is also the division-by-zero case for that statistic. */
    @Test fun allUniqueWordsHitTheHonoreEdgeCase() {
        val r = TextAnalyzer.analyze("cat dog bird fish")
        assertEquals(4, r.lexicon.unique)
        assertEquals(4, r.lexicon.hapaxLegomena)
        assertEquals(1.0, r.lexicon.typeTokenRatio, 0.001)
        assertEquals(1.0, r.lexicon.herdansC, 0.001)   // ln(4)/ln(4)
        assertTrue(r.lexicon.honoresR.isFinite())
    }

    @Test fun repeatedWordsLowerTheTypeTokenRatio() {
        val r = TextAnalyzer.analyze("the the the")
        assertEquals(1, r.lexicon.unique)
        assertEquals(3, r.surface.words)
        assertEquals(0, r.lexicon.hapaxLegomena)
        /* delta 0.01, not 0.001: the engine rounds to two places, so 1/3 comes
           back as 0.33 and a tighter tolerance would fail on the rounding
           rather than on anything wrong with the statistic */
        assertEquals(1.0 / 3.0, r.lexicon.typeTokenRatio, 0.01)
    }

    /* "lost" appears in the negative lexicon, and it is also a word that could
       reasonably be read either way. This pins the weighting arithmetic:
       three positives weighing 3+3+3 against nothing is (3-0)/9. */
    @Test fun sentimentScoresPositiveAndNegativeText() {
        val pos = TextAnalyzer.analyze("Happy joy love.")
        assertEquals(3, pos.sentiment.positiveHits)
        assertEquals(0, pos.sentiment.negativeHits)
        assertEquals(1.0 / 3.0, pos.sentiment.valence, 0.02)

        val neg = TextAnalyzer.analyze("Bad terrible hate.")
        assertEquals(0, neg.sentiment.positiveHits)
        assertEquals(3, neg.sentiment.negativeHits)
        assertTrue("expected negative valence, got ${neg.sentiment.valence}",
                   neg.sentiment.valence < 0.0)

        val neutral = TextAnalyzer.analyze("The table is near the window.")
        assertEquals(0.0, neutral.sentiment.valence, 0.001)
    }

    @Test fun histogramCountsSumToTheSentenceCount() {
        val text = "Short one. A somewhat longer second sentence here. " +
                   "Third. This fourth sentence is deliberately much longer than " +
                   "any of the others so that it lands in a different bucket."
        val r = TextAnalyzer.analyze(text)
        assertEquals(r.surface.sentences, r.structure.histogram.sumOf { it.count })
        assertEquals(r.surface.sentences, r.structure.lengths.size)
    }

    @Test fun theOpenEndedBucketReportsNoUpperBound() {
        val r = TextAnalyzer.analyze("One two three.")
        val last = r.structure.histogram.last()
        assertEquals("31+", last.label)
        assertEquals(null, last.to)
    }

    /* longestSentence is looked up by position, so the index it is found with
       has to belong to the same list it indexes into. */
    @Test fun longestSentenceIsActuallyTheLongest() {
        val r = TextAnalyzer.analyze("Tiny. A considerably longer sentence follows here. Mid one.")
        assertTrue(r.structure.longestSentence.contains("considerably"))
        assertEquals(6, r.structure.longest)
        assertEquals(1, r.structure.shortest)
    }

    @Test fun frequencyIsSortedDescendingAndFlagsStopwords() {
        val r = TextAnalyzer.analyze(
            "the cat and the dog and the bird sat with the cat"
        )
        val counts = r.frequency.map { it.count }
        assertEquals(counts.sortedDescending(), counts)
        assertEquals("the", r.frequency.first().word)
        assertTrue(r.frequency.first().isStopword)
        val cat = r.frequency.firstOrNull { it.word == "cat" }
        assertNotNull(cat)
        assertFalse(cat!!.isStopword)
    }

    @Test fun frequencyListIsBounded() {
        val many = (1..500).joinToString(" ") { "word$it" }
        assertTrue(TextAnalyzer.analyze(many).frequency.size <= 14)
    }

    /* A longer, ordinary piece of prose should land in a plausible adult band.
       Asserting a range rather than an exact number, because the point is that
       the formulas agree with each other, not that any one is precise. */
    @Test fun ordinaryProseLandsInAPlausibleGradeBand() {
        val text = """
            The ocean covers most of the planet, and yet we have mapped only a
            fraction of its floor. Most of the water below a thousand metres has
            never seen sunlight, and the animals that live there make their own.
            Pressure rises by about one atmosphere for every ten metres of depth,
            which is why a vessel that survives the descent has to be built with
            unusual care. Temperature falls steadily through the upper layers and
            then levels out, staying only a few degrees above freezing.
        """.trimIndent()
        val r = TextAnalyzer.analyze(text)
        assertFalse(r.isEmpty)
        assertTrue("words: ${r.surface.words}", r.surface.words > 60)
        assertTrue("sentences: ${r.surface.sentences}", r.surface.sentences >= 4)
        assertTrue("FKG: ${r.readability.fleschKincaidGrade}",
                   r.readability.fleschKincaidGrade in 5.0..20.0)
        assertTrue("FRE: ${r.readability.fleschReadingEase}",
                   r.readability.fleschReadingEase in 10.0..80.0)
        assertTrue("TTR: ${r.lexicon.typeTokenRatio}",
                   r.lexicon.typeTokenRatio in 0.3..0.95)
        assertTrue("valence: ${r.sentiment.valence}",
                   r.sentiment.valence in -1.0..1.0)
    }

    /* Difficulty drives the UI's accent colour and the core's pulse rate, so it
       has to be bounded and it has to move in the right direction. */
    @Test fun difficultyRisesWithComplexity() {
        val simple = TextAnalyzer.analyze("The cat sat on the mat.")
        val dense = TextAnalyzer.analyze(
            "Notwithstanding the aforementioned epistemological complications, " +
            "the phenomenological investigation necessitates a comprehensive " +
            "reconsideration of presuppositions."
        )
        assertTrue(simple.readability.difficulty01 in 0.0..1.0)
        assertTrue(dense.readability.difficulty01 in 0.0..1.0)
        assertTrue(
            "dense (${dense.readability.difficulty01}) should exceed simple " +
            "(${simple.readability.difficulty01})",
            dense.readability.difficulty01 > simple.readability.difficulty01
        )
    }

    @Test fun emotionalArcCoversTheWholeText() {
        val text = (1..30).joinToString(" ") {
            if (it < 10) "Grief and sorrow followed the loss."
            else "Joy and hope returned with the morning."
        }
        val r = TextAnalyzer.analyze(text)
        assertTrue("arc points: ${r.sentiment.arc.size}", r.sentiment.arc.size > 1)
        assertTrue(r.sentiment.arc.first().t <= r.sentiment.arc.last().t)
        assertTrue(r.sentiment.arc.all { it.t in 0.0..1.0 })
        /* starts bleak, ends warm: the arc should rise */
        val first = r.sentiment.arc.take(2).map { it.valence }.average()
        val last = r.sentiment.arc.takeLast(2).map { it.valence }.average()
        assertTrue("expected a rising arc, got $first -> $last", last > first)
    }

    @Test fun readingTimeIsProportionalToLength() {
        val shortText = TextAnalyzer.analyze("One two three.")
        val long = TextAnalyzer.analyze((1..200).joinToString(" ") { "word" })
        assertTrue(long.surface.readingSeconds > shortText.surface.readingSeconds)
        /* 200 words at 238 wpm is about 50 seconds */
        assertEquals(200 / 238.0 * 60.0, long.surface.readingSeconds, 0.5)
    }

    @Test fun analysisIsDeterministic() {
        val text = "Some prose to analyse twice. It has two sentences."
        assertEquals(TextAnalyzer.analyze(text), TextAnalyzer.analyze(text))
    }

    @Test fun lexiconCountsEveryWordWithoutDoubleCounting() {
        val r = TextAnalyzer.analyze("apple banana apple cherry banana apple")
        assertEquals(6, r.surface.words)
        assertEquals(3, r.lexicon.unique)
        assertEquals(1, r.lexicon.hapaxLegomena)   // only cherry
        assertEquals(1, r.lexicon.disLegomena)     // only banana
    }
}

/* ═══════════════════════════════════════════════════════════════════════
   Interpretation is prose, so it is the easiest part of the engine to let
   rot silently: a formula could change and the sentences describing it would
   keep reading plausibly while saying something false. These tests pin the
   claims that are checkable against the numbers.
   ═══════════════════════════════════════════════════════════════════════ */
class InterpretationTest {

    private val prose = TextAnalyzer.analyze(
        """The morning was quiet and the road ran long beneath a pale sky. He walked
        without hurrying, because hurrying would not have made the distance any
        shorter, and because the air was cool enough to make walking pleasant.
        By the time the village appeared below him the sun had cleared the ridge
        and the fields had turned from grey to a thin, uncertain gold."""
    )

    @Test fun emptyReportProducesNoReading() {
        assertTrue(Interpretation.compose(PrismReport.EMPTY).isEmpty())
    }

    @Test fun nonEmptyReportAlwaysProducesAReading() {
        val out = Interpretation.compose(prose)
        assertTrue("expected several sentences, got " + out.size, out.size >= 3)
        out.forEach { assertTrue(it.isNotBlank()) }
    }

    /** The scale sentence is the one claim that must quote exact counts, so
     *  assert the numbers appear in it rather than just that it exists. */
    @Test fun scaleSentenceQuotesTheRealCounts() {
        val first = Interpretation.compose(prose).first()
        assertTrue(first.contains(prose.surface.words.toString() + " words"))
        assertTrue(first.contains(prose.surface.sentences.toString() + " sentences"))
    }

    @Test fun everySentenceIsTerminated() {
        Interpretation.compose(prose).forEach {
            assertTrue("unterminated: $it", it.trimEnd().endsWith("."))
        }
    }

    /** A non-finite value reaching a sentence would render as "NaN" and read
     *  as a defect to the user, so it is treated as one here. */
    @Test fun neverEmitsNonFiniteText() {
        val hostile = listOf(
            "", " ", "\n\n\n", "a", ".", "...", "!!!", "0 0 0 0",
            "Supercalifragilisticexpialidocious.",
            (1..40).joinToString(" ") { "aa" },
            "\u0000\u0001 binary",
        )
        hostile.forEach { text ->
            val out = Interpretation.compose(TextAnalyzer.analyze(text))
            out.forEach { sentence ->
                assertFalse("NaN leaked: $sentence", sentence.contains("NaN"))
                assertFalse("Infinity leaked: $sentence", sentence.contains("Infinity"))
            }
        }
    }

    /** Below 30 words and under three sentences every statistic is unstable,
     *  and the reading is required to say so rather than present the numbers
     *  as though they carried the usual weight. */
    @Test fun shortTextDrawsACaveat() {
        val out = Interpretation.compose(TextAnalyzer.analyze("The cat sat on the mat."))
        assertTrue(out.any { it.startsWith("Read with care:") })
    }

    /** The converse matters as much: a caveat on ordinary prose would train
     *  the reader to ignore it, which is worse than no caveat at all. */
    @Test fun ordinaryProseDrawsNoCaveat() {
        val out = Interpretation.compose(prose)
        assertFalse(out.any { it.startsWith("Read with care:") })
    }

    /** The five grade formulas are fitted on different corpora. When they
     *  agree the reading must say they agree, and when they diverge it must
     *  quote the spread — smoothing the disagreement away would be the single
     *  most misleading thing this feature could do. */
    @Test fun agreementSentenceIsAlwaysPresent() {
        val out = Interpretation.compose(prose)
        assertTrue(out.any { it.contains("grade-level formulas") })
    }

    @Test fun disagreementIsReportedAsARange() {
        /* Jargon-dense input with long sentences drives Fog and SMOG well
           above Flesch-Kincaid, which is the case worth surfacing. */
        val dense = TextAnalyzer.analyze(
            """The aforementioned methodological presuppositions necessitate a comprehensive
            reconceptualisation of the epistemological foundations underpinning contemporary
            interpretive frameworks, notwithstanding the considerable heterogeneity observed
            across the participant cohorts subjected to preliminary investigation."""
        )
        val scores = listOf(
            dense.readability.fleschKincaidGrade,
            dense.readability.gunningFog,
            dense.readability.smog,
            dense.readability.colemanLiau,
            dense.readability.ari,
        )
        val spread = scores.max() - scores.min()
        val out = Interpretation.compose(dense)
        val sentence = out.firstOrNull { it.contains("grade-level formulas") }
        assertNotNull(sentence)
        if (spread >= 1.5) {
            assertTrue("spread $spread should be quoted: $sentence", sentence!!.contains("span") || sentence.contains("disagree"))
        } else {
            assertTrue(sentence!!.contains("agree closely"))
        }
    }

    @Test fun toneSentenceNamesTheLexiconHits() {
        val warm = TextAnalyzer.analyze("Joy and hope and love filled the happy morning.")
        val out = Interpretation.compose(warm)
        assertTrue(out.any { it.contains("positive and") && it.contains("negative") })
    }

    /** A document with no sentiment-bearing words must not be described as
     *  neutral by omission — the honest statement is that the lexicon found
     *  nothing, which is a fact about the lexicon as much as the text. */
    @Test fun noSentimentWordsIsSaidExplicitly() {
        val flat = TextAnalyzer.analyze(
            "The table is near the window. The window is near the table. Both are brown."
        )
        assertEquals(0, flat.sentiment.positiveHits)
        assertEquals(0, flat.sentiment.negativeHits)
        val out = Interpretation.compose(flat)
        assertTrue(out.any { it.contains("No sentiment-bearing words") })
    }

    @Test fun vocabularySentenceQuotesHerdansC() {
        val out = Interpretation.compose(prose)
        assertTrue(out.any { it.contains("Herdan's C") })
    }
}
