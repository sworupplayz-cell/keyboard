package com.sworupplayz.keyboard

import java.util.Locale

/**
 * One deterministic offline prediction pipeline.
 *
 * SuggestionEngine is still the only public facade. These types describe the
 * stages that already feed [SuggestionRanker]; they are not a second engine.
 *
 * InputContext → generate → normalize → filter → score → diversify → top 3
 */
enum class CandidateSource {
    EXACT,
    PREFIX,
    NORMALIZED_PREFIX,
    LEARNED,
    RECENT,
    UNIGRAM,
    BIGRAM,
    TRIGRAM,
    PHRASE,
    MORPHOLOGY,
    TYPO,
    PHONETIC,
    EMOJI,
    NAME,
    PLACE,
    ABBREVIATION,
    SLANG,
    CONTRACTION,
    TYPED
}

data class PredictionCandidate(
    val word: String,
    val source: CandidateSource,
    val scoreHint: Int = 0
)

data class InputContext(
    val input: String,
    val language: SuggestionLanguage,
    val previousWord: String? = null,
    val previousTwoWords: String? = null,
    val previousThreeWords: String? = null,
    val learned: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val contextPredictions: List<String> = emptyList(),
    val personalFrequency: Map<String, Int> = emptyMap(),
    val includeEmoji: Boolean = true,
    val includeTypos: Boolean = true
) {
    companion object {
        fun from(query: SuggestionQuery): InputContext = InputContext(
            input = query.input.trim(),
            language = query.language,
            previousWord = query.previousWord,
            previousTwoWords = query.previousTwoWords,
            previousThreeWords = query.previousThreeWords,
            learned = query.learned,
            recent = query.recent,
            contextPredictions = query.contextPredictions,
            personalFrequency = query.personalFrequency,
            includeEmoji = query.includeEmoji,
            includeTypos = query.includeTypos
        )
    }
}

/** Shared score bands. A weak signal cannot outrank a stronger one. */
object PredictionScores {
    const val EXACT = 20_000
    const val LEARNED = 8_000
    const val LEARNED_CAP = 8_400
    const val TRIGRAM = 5_200
    const val TRIGRAM_FLOOR = 4_800
    const val RECENT = 3_000
    const val RECENT_FLOOR = 2_400
    const val BIGRAM = 2_700
    const val BIGRAM_FLOOR = 2_300
    const val CONTEXT = 2_200
    const val CONTEXT_FLOOR = 1_600
    const val CONTRACTION = 1_450
    const val PREFIX = 1_000
    const val PREFIX_FREQ_CAP = 900
    const val CATEGORY_PENALTY = 80
    const val MORPHOLOGY = 380
    const val MORPHOLOGY_FLOOR = 200
    const val COMMON_TYPO = 180
    const val TYPO = 120
    const val TYPO_FLOOR = 40
    const val PHONETIC = 90
    const val TYPED = 10
    const val PERSONAL_USAGE_CAP = 40
}

/**
 * Collapses playful Roman lengthening and case so ramro / ramroo / ramrooo
 * occupy one suggestion slot.
 */
object CandidateIdentity {
    fun canonical(word: String): String {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any { it.code in DEVANAGARI_RANGE }) return trimmed
        if (trimmed.any { !it.isLetter() && it != '\'' }) return trimmed
        val lower = trimmed.lowercase(Locale.ENGLISH)
        return RomanSpellingNormalizer.collapseRepeatedLetters(
            RomanSpellingNormalizer.collapseRepeatedVowels(lower)
        )
    }

    /** Collapses hello / hellos / hello's without merging hello / help / he'll. */
    fun familyKey(word: String): String {
        val canon = canonical(word)
        if (canon.length <= 4) return canon
        if (canon.endsWith("'s")) return canon.dropLast(2)
        if (canon.endsWith('s') && !canon.endsWith("ss") && !canon.endsWith("us") && !canon.endsWith("is")) {
            return canon.dropLast(1)
        }
        return canon
    }

    fun sameConcept(first: String, second: String): Boolean {
        val left = familyKey(first)
        val right = familyKey(second)
        return left.isNotEmpty() && left == right
    }

    private val DEVANAGARI_RANGE = 0x0900..0x097F
}

/** Harmless chat abbreviations. Never auto-applied. */
object ChatAbbreviation {
    private val KNOWN = setOf(
        "lol", "omg", "btw", "idk", "brb", "tbh", "ok", "okay", "pls", "plz",
        "imo", "imho", "smh", "fyi", "nvm", "thx", "ty", "lmk", "hmu", "afaik",
        "idc", "ikr", "np", "tyvm", "rn", "gonna", "wanna", "gotta"
    )

    fun isKnown(word: String): Boolean = word.trim().lowercase(Locale.ENGLISH) in KNOWN

    fun matches(prefix: String): List<String> {
        val key = prefix.trim().lowercase(Locale.ENGLISH)
        if (key.length < 2) return emptyList()
        return KNOWN.filter { it.startsWith(key) }.take(3)
    }
}

/** Typed contractions become suggestion candidates only. */
object ContractionExpander {
    private val FORMS = mapOf(
        "cant" to "can't",
        "dont" to "don't",
        "im" to "I'm",
        "ive" to "I've",
        "ill" to "I'll",
        "youre" to "you're",
        "theyre" to "they're",
        "weve" to "we've",
        "theyve" to "they've",
        "wont" to "won't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "thats" to "that's",
        "whats" to "what's",
        "lets" to "let's",
        "hes" to "he's",
        "shes" to "she's",
        "theres" to "there's",
        "heres" to "here's",
        "aint" to "ain't",
        "havent" to "haven't",
        "hasnt" to "hasn't"
    )

    fun expand(input: String): List<String> {
        val key = input.trim().lowercase(Locale.ENGLISH)
        if (key.isEmpty()) return emptyList()
        val exact = FORMS[key]
        if (exact != null) return listOf(exact)
        return FORMS.entries
            .filter { it.key.startsWith(key) && key.length >= 2 }
            .map { it.value }
            .take(2)
    }

    fun isContraction(word: String): Boolean {
        val key = word.trim()
        return key.contains('\'') && FORMS.containsValue(key)
    }
}

object PredictionPipeline {
    const val MAX_VISIBLE = SuggestionEngine.MAX_VISIBLE
    const val MAX_GENERATED = 16

    fun contractions(input: String): List<String> = ContractionExpander.expand(input)

    fun extras(input: String): List<String> = CommonCompletions.extras(input)

    fun abbreviations(input: String): List<String> = ChatAbbreviation.matches(input)

    fun morphology(
        input: String,
        language: SuggestionLanguage,
        known: (String) -> Boolean = { true }
    ): List<String> = when (language) {
        SuggestionLanguage.ENGLISH -> Morphology.englishRelatives(input).filter(known)
        SuggestionLanguage.NEPALI -> Morphology.nepaliRelatives(input).filter(known)
        SuggestionLanguage.ROMAN -> Morphology.nepaliRelatives(input)
    }

    fun normalize(candidates: List<String>): List<String> {
        val result = ArrayList<String>(candidates.size)
        candidates.forEach { word ->
            val clean = word.trim()
            if (clean.isNotEmpty()) result += clean
        }
        return result
    }

    fun filter(candidates: List<String>): List<String> {
        val result = ArrayList<String>(candidates.size)
        val seen = HashSet<String>()
        candidates.forEach { word ->
            val clean = word.trim()
            if (clean.isEmpty() || isBlocked(clean)) return@forEach
            val key = clean.lowercase(Locale.ENGLISH)
            if (key in seen) return@forEach
            seen += key
            result += clean
        }
        return result
    }

    fun diversify(words: List<String>, limit: Int = MAX_VISIBLE): List<String> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<String>(limit)
        words.forEach { word ->
            val key = CandidateIdentity.familyKey(word)
            if (key.isEmpty() || key in seen) return@forEach
            seen += key
            result += word
            if (result.size >= limit) return result
        }
        return result
    }

    fun isBlocked(word: String): Boolean {
        val clean = word.trim()
        if (clean.isEmpty()) return true
        return SpecialTokenPolicy.looksLikeUrl(clean) ||
            SpecialTokenPolicy.looksLikeEmail(clean) ||
            SpecialTokenPolicy.looksLikeMentionOrHashtag(clean) ||
            ClipboardPolicy.looksSensitive(clean)
    }

    fun isLowConfidenceInput(input: String): Boolean {
        val clean = input.trim()
        if (clean.isEmpty()) return false
        if (SpecialTokenPolicy.looksLikeUrl(clean) || SpecialTokenPolicy.looksLikeEmail(clean)) return true
        if (SpecialTokenPolicy.looksLikeMentionOrHashtag(clean) || SpecialTokenPolicy.looksLikeNumber(clean)) {
            return true
        }
        return ClipboardPolicy.looksSensitive(clean)
    }
}
