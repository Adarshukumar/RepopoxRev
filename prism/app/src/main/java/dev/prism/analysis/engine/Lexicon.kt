package dev.prism.analysis.engine

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — lexicons

   Two word lists, both compiled into the app so it stays fully offline and
   needs no permission to fetch anything.

   The sentiment list is a compact valence dictionary: it is not a
   replacement for a trained model, and the UI says so. What it is good for
   is the shape of a piece of writing — whether it warms or cools as it goes
   — which is a comparative judgement and survives the crudeness of the
   instrument. Absolute scores from a few hundred words would be pretending.
   ═══════════════════════════════════════════════════════════════════════ */

object Stopwords {

    /* The standard English function-word list. Counting these matters because
       a high stopword ratio usually means padded prose, and because the
       frequency panel needs to know which of its top words carry content. */
    val ENGLISH: Set<String> = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before",
        "being", "below", "between", "both", "but", "by", "can", "cannot", "could",
        "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
        "down", "during", "each", "few", "for", "from", "further", "had", "hadn't",
        "has", "hasn't", "have", "haven't", "having", "he", "her", "here", "hers",
        "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is",
        "isn't", "it", "its", "itself", "just", "let", "me", "might", "more",
        "most", "mustn't", "my", "myself", "no", "nor", "not", "now", "of", "off",
        "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "should", "shouldn't", "so",
        "some", "such", "than", "that", "the", "their", "theirs", "them",
        "themselves", "then", "there", "these", "they", "this", "those", "through",
        "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "were",
        "weren't", "what", "when", "where", "which", "while", "who", "whom", "why",
        "will", "with", "won't", "would", "wouldn't", "you", "your", "yours",
        "yourself", "yourselves",
    )

    fun isStopword(w: String): Boolean = w.lowercase() in ENGLISH
}

object Sentiment {

    /* Weights run 1 (mild) to 3 (strong). Keeping them small integers makes
       the sum interpretable: valence is a weighted tally, not a probability. */
    val POSITIVE: Map<String, Int> = mapOf(
        "good" to 2, "great" to 3, "excellent" to 3, "wonderful" to 3, "fantastic" to 3,
        "brilliant" to 3, "superb" to 3, "outstanding" to 3, "amazing" to 3,
        "love" to 3, "loved" to 3, "loving" to 2, "adore" to 3, "cherish" to 2,
        "happy" to 3, "happiness" to 3, "glad" to 2, "joy" to 3, "joyful" to 3,
        "delight" to 3, "delighted" to 3, "pleased" to 2, "pleasure" to 2,
        "beautiful" to 3, "beauty" to 2, "lovely" to 2, "gorgeous" to 3,
        "kind" to 2, "kindness" to 2, "gentle" to 2, "warm" to 2, "warmth" to 2,
        "calm" to 2, "peace" to 3, "peaceful" to 3, "serene" to 2, "quiet" to 1,
        "hope" to 2, "hopeful" to 2, "optimistic" to 2, "confidence" to 2,
        "confident" to 2, "proud" to 2, "pride" to 2, "grateful" to 3,
        "gratitude" to 3, "thankful" to 2, "thanks" to 1, "blessed" to 2,
        "clear" to 2, "clarity" to 2, "sharp" to 2, "bright" to 2, "light" to 1,
        "strong" to 2, "strength" to 2, "brave" to 3, "courage" to 3,
        "win" to 2, "won" to 2, "success" to 3, "successful" to 3, "achieve" to 2,
        "achieved" to 2, "improve" to 2, "improved" to 2, "better" to 2,
        "best" to 3, "perfect" to 3, "correct" to 1, "true" to 2, "truth" to 2,
        "honest" to 2, "fair" to 2, "just" to 1, "free" to 2, "freedom" to 3,
        "safe" to 2, "safety" to 2, "secure" to 2, "comfort" to 2, "easy" to 2,
        "simple" to 1, "clean" to 2, "fresh" to 2, "alive" to 2, "grow" to 2,
        "grew" to 2, "growth" to 2, "learn" to 2, "learned" to 2, "wisdom" to 3,
        "wise" to 2, "understand" to 2, "understood" to 2, "insight" to 3,
        "elegant" to 3, "grace" to 2, "graceful" to 2, "harmony" to 3,
        "fun" to 2, "play" to 1, "smile" to 2, "smiled" to 2, "laugh" to 2,
        "laughed" to 2, "laughing" to 2, "enjoy" to 2, "enjoyed" to 2,
        "reward" to 2, "valuable" to 2, "worthy" to 2, "rich" to 2, "abundant" to 2,
        "swift" to 1, "fast" to 1, "smooth" to 2, "steady" to 2, "stable" to 2,
        "yes" to 1, "certainly" to 1, "indeed" to 1, "welcome" to 2,
        "admire" to 2, "respect" to 2, "trust" to 2, "trusted" to 2,
        "heal" to 2, "healed" to 2, "relief" to 2, "relieved" to 2,
        "triumph" to 3, "victory" to 3, "glory" to 2, "magnificent" to 3,
        "marvel" to 2, "marvellous" to 3, "splendid" to 3, "terrific" to 3,
        "nice" to 1, "fine" to 1, "ok" to 1, "okay" to 1, "well" to 1,
        "progress" to 2, "advance" to 2, "rise" to 1, "risen" to 2,
    )

    val NEGATIVE: Map<String, Int> = mapOf(
        "bad" to 2, "poor" to 2, "terrible" to 3, "awful" to 3, "horrible" to 3,
        "dreadful" to 3, "abysmal" to 3, "lousy" to 2, "inferior" to 2,
        "hate" to 3, "hated" to 3, "hatred" to 3, "loathe" to 3, "despise" to 3,
        "sad" to 2, "sadness" to 2, "unhappy" to 3, "sorrow" to 3, "grief" to 3,
        "grieve" to 3, "mourn" to 3, "miserable" to 3, "misery" to 3,
        "angry" to 3, "anger" to 3, "rage" to 3, "furious" to 3, "fury" to 3,
        "annoyed" to 2, "irritated" to 2, "frustrated" to 2, "frustrating" to 2,
        "afraid" to 2, "fear" to 3, "feared" to 2, "frightened" to 3,
        "terrified" to 3, "panic" to 3, "anxious" to 2, "anxiety" to 2,
        "worried" to 2, "worry" to 2, "nervous" to 2, "dread" to 3,
        "pain" to 3, "painful" to 3, "hurt" to 3, "hurting" to 2, "wound" to 2,
        "suffer" to 3, "suffering" to 3, "suffered" to 3, "agony" to 3,
        "sick" to 2, "illness" to 2, "disease" to 2, "ache" to 2, "nausea" to 2,
        "die" to 3, "died" to 3, "death" to 3, "dead" to 3, "dying" to 3,
        "kill" to 3, "killed" to 3, "murder" to 3, "destroy" to 3,
        "destroyed" to 3, "destruction" to 3, "ruin" to 3, "ruined" to 3,
        "fail" to 3, "failed" to 3, "failure" to 3, "lose" to 2, "lost" to 2,
        "loss" to 3, "defeat" to 3, "defeated" to 3,
        "wrong" to 2, "false" to 2, "lie" to 3, "lied" to 3, "lying" to 2,
        "dishonest" to 3, "deceive" to 3, "deceived" to 3, "betray" to 3,
        "betrayed" to 3, "betrayal" to 3, "cheat" to 3, "steal" to 3,
        "stole" to 3, "theft" to 3, "corrupt" to 3, "corruption" to 3,
        "guilty" to 3, "guilt" to 2, "shame" to 3, "ashamed" to 3,
        "ugly" to 2, "hideous" to 3, "grotesque" to 2, "dull" to 2, "boring" to 2,
        "bored" to 2, "tedious" to 2, "tiresome" to 2,
        "hard" to 1, "difficult" to 2, "difficulty" to 2, "struggle" to 2,
        "struggled" to 2, "struggling" to 2, "effort" to 1, "problem" to 2,
        "problems" to 2, "trouble" to 2, "troubled" to 2, "crisis" to 3,
        "disaster" to 3, "catastrophe" to 3, "emergency" to 2, "danger" to 3,
        "dangerous" to 3, "risk" to 1, "risky" to 2, "threat" to 3,
        "threaten" to 3, "threatened" to 3, "attack" to 3, "attacked" to 3,
        "violence" to 3, "violent" to 3, "cruel" to 3, "cruelty" to 3,
        "harsh" to 2, "severe" to 2, "brutal" to 3, "savage" to 3,
        "weak" to 2, "weakness" to 2, "fragile" to 2, "broken" to 3,
        "break" to 2, "broke" to 2, "damage" to 3, "damaged" to 3,
        "dirty" to 2, "filthy" to 3, "rotten" to 3, "decay" to 2, "decaying" to 2,
        "dark" to 1, "darkness" to 2, "gloom" to 2, "gloomy" to 2,
        "cold" to 1, "bleak" to 3, "barren" to 2, "empty" to 1, "void" to 2,
        "lonely" to 3, "loneliness" to 3, "alone" to 1, "isolated" to 2,
        "abandoned" to 3, "reject" to 3, "rejected" to 3, "refuse" to 2,
        "refused" to 2, "deny" to 2, "denied" to 2, "ignore" to 2,
        "ignored" to 2, "neglect" to 3, "neglected" to 3,
        "confused" to 2, "confusion" to 2, "unclear" to 2, "vague" to 1,
        "lost" to 2, "stuck" to 2, "trapped" to 3, "helpless" to 3,
        "hopeless" to 3, "despair" to 3, "desperate" to 3, "hopelessness" to 3,
        "worse" to 2, "worst" to 3, "never" to 1, "no" to 1, "not" to 1,
        "nothing" to 1, "nobody" to 2, "none" to 1, "cannot" to 1, "can't" to 1,
        "slow" to 1, "sluggish" to 2, "stall" to 2, "stalled" to 2,
        "cost" to 1, "costly" to 2, "expensive" to 2, "debt" to 2, "poverty" to 3,
        "tired" to 2, "exhausted" to 3, "weary" to 2, "fatigue" to 2,
        "stress" to 2, "stressed" to 2, "pressure" to 1, "burden" to 2,
        "regret" to 3, "sorry" to 1, "apologise" to 1, "apologize" to 1,
        "blame" to 2, "blamed" to 2, "fault" to 2, "error" to 2, "mistake" to 2,
        "mistakes" to 2, "flaw" to 2, "flawed" to 2, "defect" to 2,
        "decline" to 2, "declined" to 2, "fall" to 1, "fell" to 1, "drop" to 1,
    )

    /** Weighted tally in −1..+1. Zero when the text has no charged words at
     *  all, which the UI must distinguish from "neutral but opinionated". */
    fun score(words: List<String>): Double {
        if (words.isEmpty()) return 0.0
        var pos = 0
        var neg = 0
        var weight = 0
        for (w in words) {
            val p = POSITIVE[w]
            val n = NEGATIVE[w]
            if (p != null) { pos++; weight += p }
            else if (n != null) { neg++; weight += n }
        }
        if (weight == 0) return 0.0
        return ((pos - neg).toDouble() / weight).coerceIn(-1.0, 1.0)
    }

    fun tally(words: List<String>): Pair<Int, Int> {
        var pos = 0
        var neg = 0
        for (w in words) {
            if (POSITIVE.containsKey(w)) pos++
            else if (NEGATIVE.containsKey(w)) neg++
        }
        return pos to neg
    }
}
