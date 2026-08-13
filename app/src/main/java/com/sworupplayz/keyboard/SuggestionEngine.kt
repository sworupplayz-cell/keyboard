package com.sworupplayz.keyboard

import java.util.Locale

enum class SuggestionLanguage { ENGLISH, NEPALI, ROMAN }

data class SuggestionQuery(
    val input: String,
    val language: SuggestionLanguage,
    val previousWord: String? = null,
    val previousTwoWords: String? = null,
    val learned: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val contextPredictions: List<String> = emptyList(),
    val learnedRoman: String? = null,
    val includeEmoji: Boolean = true
)

data class SuggestionResult(
    val words: List<String>,
    val emoji: String? = null
) {
    fun visible(limit: Int = SuggestionEngine.MAX_VISIBLE): List<String> {
        val items = ArrayList<String>(limit)
        words.forEach { word ->
            if (word.isNotEmpty() && word !in items) items += word
        }
        val glyph = emoji
        if (glyph != null && glyph !in items) {
            if (items.size < limit) {
                items += glyph
            } else if (items.size == limit && items.last().equals(words.lastOrNull(), ignoreCase = true)) {
                items[items.lastIndex] = glyph
            }
        }
        return items.take(limit)
    }
}

/**
 * Gboard-style offline suggestion facade. Combines prefix dictionaries, phrase
 * prediction, conservative typos, Roman conversion, mixed-language context,
 * local learning, and an optional high-confidence emoji candidate.
 */
class SuggestionEngine(
    private val english: LocalWordSuggester,
    private val nepali: LocalWordSuggester,
    private val roman: RomanNepaliConverter,
    private val englishPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES),
    private val nepaliPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.NEPALI_PHRASES),
    private val romanPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES)
) {
    fun suggest(query: SuggestionQuery, limit: Int = MAX_VISIBLE): SuggestionResult {
        val capped = limit.coerceAtMost(MAX_VISIBLE)
        if (capped <= 0) return SuggestionResult(emptyList())
        val input = query.input.trim()
        if (input.isNotEmpty() && isProtectedToken(input)) {
            return SuggestionResult(listOf(input).take(capped))
        }

        val phrases = (phrasesFor(query, input) + query.contextPredictions).distinct()
        val words = when (query.language) {
            SuggestionLanguage.ENGLISH -> englishWords(query, input, phrases, capped)
            SuggestionLanguage.NEPALI -> nepaliWords(query, input, phrases, capped)
            SuggestionLanguage.ROMAN -> romanWords(query, input, phrases, capped)
        }
        val emoji = if (query.includeEmoji) EmojiSuggestionPolicy.suggest(input) else null
        return SuggestionResult(words, emoji)
    }

    fun recordPhrase(language: SuggestionLanguage, previous: String, next: String): Boolean =
        predictor(language).record(previous, next)

    private fun englishWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) return phrases.take(limit)
        val ranked = english.suggestions(
            input = input,
            learned = query.learned,
            limit = limit,
            recent = query.recent,
            contextPredictions = phrases + CommonCompletions.extras(input)
        )
        val merged = LinkedHashSet<String>()
        CommonCompletions.extras(input).forEach { extra ->
            if (english.contains(extra) || extra.contains('\'')) merged += extra
        }
        ranked.forEach { merged += it }
        val compact = merged.take(limit)
        val onlyTypedFallback = compact.isEmpty() ||
            (compact.size == 1 && compact.first().equals(input, ignoreCase = true))
        if (!onlyTypedFallback) return compact
        val extras = TypoCorrector.missingLetterCandidates(input, english::contains)
            .filter { candidate -> ranked.none { it.equals(candidate, ignoreCase = true) } }
        if (extras.isEmpty()) return ranked
        return SuggestionRanker.rank(
            input = input,
            prefixMatches = ranked,
            typoMatches = extras,
            learned = query.learned,
            recent = query.recent,
            frequencyOf = english::rankOf,
            limit = limit,
            contextMatches = phrases
        )
    }

    private fun nepaliWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) return phrases.take(limit)
        return nepali.suggestions(
            input = input,
            learned = query.learned,
            limit = limit,
            recent = query.recent,
            contextPredictions = phrases
        )
    }

    private fun romanWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) {
            return phrases.map { prediction ->
                roman.exactConversion(prediction) ?: prediction
            }.distinct().take(limit)
        }
        val converted = roman.suggestions(
            romanWord = input,
            learned = query.learnedRoman,
            limit = limit,
            recent = query.recent,
            previousWord = query.previousWord,
            contextPredictions = phrases
        )
        val nepaliOnly = roman.suggestions(
            romanWord = input,
            learned = query.learnedRoman,
            limit = limit,
            includeRoman = false,
            recent = query.recent,
            previousWord = query.previousWord,
            contextPredictions = phrases
        )
        val merged = LinkedHashSet<String>()
        nepaliOnly.forEach { merged += it }
        converted.forEach { merged += it }
        return merged.take(limit)
    }

    private fun phrasesFor(query: SuggestionQuery, input: String): List<String> =
        predictor(query.language).predict(
            previous = query.previousWord,
            previousTwo = query.previousTwoWords,
            prefix = input,
            limit = MAX_VISIBLE
        )

    private fun predictor(language: SuggestionLanguage): PhrasePredictor = when (language) {
        SuggestionLanguage.ENGLISH -> englishPhrases
        SuggestionLanguage.NEPALI -> nepaliPhrases
        SuggestionLanguage.ROMAN -> romanPhrases
    }

    private fun isProtectedToken(value: String): Boolean =
        SpecialTokenPolicy.looksLikeUrl(value) ||
            SpecialTokenPolicy.looksLikeEmail(value) ||
            SpecialTokenPolicy.looksLikeMentionOrHashtag(value) ||
            SpecialTokenPolicy.looksLikeNumber(value)

    companion object {
        const val MAX_VISIBLE = 3
    }
}

/** Tiny offline completions for a few everyday stems that are not strict prefixes. */
object CommonCompletions {
    private val EXTRAS = mapOf(
        "goo" to listOf("good", "going", "Google"),
        "hel" to listOf("hello", "help", "he'll"),
        "tha" to listOf("that", "thanks", "than")
    )

    fun extras(prefix: String): List<String> = EXTRAS[prefix.trim().lowercase(Locale.ENGLISH)].orEmpty()
}

class RepeatFinishStore(
    initial: Map<String, Int> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val counts = linkedMapOf<String, Int>()

    init {
        initial.forEach { (word, score) ->
            val clean = word.trim()
            if (clean.isNotEmpty()) counts[normalize(clean)] = score.coerceAtLeast(1)
        }
        trim()
    }

    fun record(word: String): Int {
        val key = normalize(word)
        if (key.isEmpty()) return 0
        val next = (counts.remove(key) ?: 0) + 1
        counts[key] = next
        trim()
        return next
    }

    fun count(word: String): Int = counts[normalize(word)] ?: 0

    fun serialize(): String = counts.entries.joinToString("\n") { (word, score) -> "$word\t$score" }

    private fun trim() {
        while (counts.size > limit) counts.remove(counts.keys.first())
    }

    companion object {
        const val DEFAULT_LIMIT = 80

        fun fromSerialized(value: String?): RepeatFinishStore {
            val entries = linkedMapOf<String, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) entries[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
            return RepeatFinishStore(entries)
        }

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)
    }
}
