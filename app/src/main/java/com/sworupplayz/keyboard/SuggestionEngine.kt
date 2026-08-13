package com.sworupplayz.keyboard

import java.util.Locale

enum class SuggestionLanguage { ENGLISH, NEPALI, ROMAN }

data class SuggestionQuery(
    val input: String,
    val language: SuggestionLanguage,
    val previousWord: String? = null,
    val previousTwoWords: String? = null,
    val previousThreeWords: String? = null,
    val learned: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val contextPredictions: List<String> = emptyList(),
    val learnedRoman: String? = null,
    val includeEmoji: Boolean = true,
    val includeTypos: Boolean = true,
    val personalFrequency: Map<String, Int> = emptyMap()
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
    englishPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES),
    nepaliPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.NEPALI_PHRASES),
    romanPhrases: PhrasePredictor = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES)
) {
    private var englishPhrases = englishPhrases
    private var nepaliPhrases = nepaliPhrases
    private var romanPhrases = romanPhrases

    fun suggest(query: SuggestionQuery, limit: Int = MAX_VISIBLE): SuggestionResult {
        val capped = limit.coerceAtMost(MAX_VISIBLE)
        if (capped <= 0) return SuggestionResult(emptyList())
        val input = query.input.trim()
        if (input.isNotEmpty() && (isProtectedToken(input) || PredictionPipeline.isLowConfidenceInput(input))) {
            return SuggestionResult(listOf(input).take(capped))
        }

        val phrases = PredictionPipeline.filter(
            phrasesFor(query, input) + query.contextPredictions
        )
        val trigrams = phrasesForKey(query, query.previousThreeWords, input)
        val bigrams = (
            phrasesForKey(query, query.previousTwoWords, input) +
                phrasesForKey(query, query.previousWord, input)
            ).distinct()
        val words = when (query.language) {
            SuggestionLanguage.ENGLISH -> englishWords(query, input, phrases, bigrams, trigrams, capped)
            SuggestionLanguage.NEPALI -> nepaliWords(query, input, phrases, bigrams, trigrams, capped)
            SuggestionLanguage.ROMAN -> romanWords(query, input, phrases, bigrams, trigrams, capped)
        }
        val emoji = if (query.includeEmoji) {
            EmojiSuggestionPolicy.suggest(input, query.previousWord, query.previousTwoWords)
        } else {
            null
        }
        return SuggestionResult(PredictionPipeline.diversify(words, capped), emoji)
    }

    fun recordPhrase(
        language: SuggestionLanguage,
        previous: String,
        next: String,
        previousTwo: String? = null
    ): Boolean {
        if (!PersonalDictionary.shouldLearnPhrase(previous, next)) return false
        return predictor(language).record(previous, next, previousTwo)
    }

    fun serializePhrases(language: SuggestionLanguage): String = predictor(language).serialize()

    fun restorePhrases(language: SuggestionLanguage, serialized: String?) {
        val seed = when (language) {
            SuggestionLanguage.ENGLISH -> PhrasePredictor.ENGLISH_PHRASES
            SuggestionLanguage.NEPALI -> PhrasePredictor.NEPALI_PHRASES
            SuggestionLanguage.ROMAN -> PhrasePredictor.ROMAN_PHRASES
        }
        val loaded = PhrasePredictor.fromSerialized(serialized, seed)
        when (language) {
            SuggestionLanguage.ENGLISH -> englishPhrases = loaded
            SuggestionLanguage.NEPALI -> nepaliPhrases = loaded
            SuggestionLanguage.ROMAN -> romanPhrases = loaded
        }
    }

    private fun englishWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        bigrams: List<String>,
        trigrams: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) return PredictionPipeline.diversify(phrases, limit)
        val extras = PredictionPipeline.extras(input)
        val contractions = PredictionPipeline.contractions(input)
        val ranked = english.suggestions(
            input = input,
            learned = query.learned,
            limit = limit,
            recent = query.recent,
            contextPredictions = phrases + extras
        )
        val prefixMatches = PredictionPipeline.filter(ranked + extras + contractions)
        val onlyTypedFallback = prefixMatches.isEmpty() ||
            (prefixMatches.size == 1 && prefixMatches.first().equals(input, ignoreCase = true))
        val typos = if (query.includeTypos) typoCandidates(input, prefixMatches) else emptyList()
        if (!onlyTypedFallback && typos.isEmpty() && contractions.isEmpty() && extras.isEmpty()) {
            return SuggestionRanker.rank(
                input = input,
                prefixMatches = prefixMatches,
                typoMatches = emptyList(),
                learned = query.learned,
                recent = query.recent,
                frequencyOf = english::rankOf,
                limit = limit,
                contextMatches = phrases,
                morphologyMatches = PredictionPipeline.morphology(input, SuggestionLanguage.ENGLISH, english::contains),
                personalFrequencyOf = { word -> query.personalFrequency.scoreOf(word) },
                trigramMatches = trigrams,
                bigramMatches = bigrams,
                contractionMatches = contractions,
                categoryOf = { VocabularyCatalog.classify(it, VocabularyLanguage.ENGLISH) }
            )
        }
        return SuggestionRanker.rank(
            input = input,
            prefixMatches = if (onlyTypedFallback) ranked else prefixMatches,
            typoMatches = typos,
            learned = query.learned,
            recent = query.recent,
            frequencyOf = english::rankOf,
            limit = limit,
            contextMatches = phrases + extras,
            morphologyMatches = PredictionPipeline.morphology(input, SuggestionLanguage.ENGLISH, english::contains),
            personalFrequencyOf = { word -> query.personalFrequency.scoreOf(word) },
            trigramMatches = trigrams,
            bigramMatches = bigrams,
            contractionMatches = contractions,
            categoryOf = { VocabularyCatalog.classify(it, VocabularyLanguage.ENGLISH) }
        )
    }

    private fun nepaliWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        bigrams: List<String>,
        trigrams: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) return PredictionPipeline.diversify(phrases, limit)
        return SuggestionRanker.rank(
            input = input,
            prefixMatches = nepali.suggestions(
                input = input,
                learned = query.learned,
                limit = limit,
                recent = query.recent,
                contextPredictions = phrases
            ),
            typoMatches = emptyList(),
            learned = query.learned,
            recent = query.recent,
            frequencyOf = nepali::rankOf,
            limit = limit,
            contextMatches = phrases,
            morphologyMatches = PredictionPipeline.morphology(input, SuggestionLanguage.NEPALI, nepali::contains),
            personalFrequencyOf = { word -> query.personalFrequency.scoreOf(word) },
            trigramMatches = trigrams,
            bigramMatches = bigrams,
            categoryOf = { VocabularyCatalog.classify(it, VocabularyLanguage.NEPALI) }
        )
    }

    private fun romanWords(
        query: SuggestionQuery,
        input: String,
        phrases: List<String>,
        bigrams: List<String>,
        trigrams: List<String>,
        limit: Int
    ): List<String> {
        if (input.isEmpty()) {
            return PredictionPipeline.diversify(
                phrases.map { prediction -> roman.exactConversion(prediction) ?: prediction },
                limit
            )
        }
        val converted = roman.suggestions(
            romanWord = input,
            learned = query.learnedRoman,
            limit = limit,
            recent = query.recent,
            previousWord = query.previousWord,
            contextPredictions = phrases + bigrams + trigrams
        )
        val nepaliOnly = roman.suggestions(
            romanWord = input,
            learned = query.learnedRoman,
            limit = limit,
            includeRoman = false,
            recent = query.recent,
            previousWord = query.previousWord,
            contextPredictions = phrases + bigrams + trigrams
        )
        val stem = roman.exactConversion(input) ?: nepaliOnly.firstOrNull()
        val relatives = if (stem != null && RomanNepaliConverter.isNepali(stem)) {
            Morphology.nepaliRelatives(stem).filter { relative ->
                relative.startsWith(stem) || stem.startsWith(relative.take(2))
            }
        } else {
            emptyList()
        }
        val merged = LinkedHashSet<String>()
        nepaliOnly.forEach { merged += it }
        relatives.forEach { merged += it }
        converted.forEach { merged += it }
        return PredictionPipeline.diversify(merged.toList(), limit)
    }

    private fun phrasesFor(query: SuggestionQuery, input: String): List<String> =
        predictor(query.language).predict(
            previous = PredictionPipeline.effectivePrevious(query.previousWord),
            previousTwo = query.previousTwoWords,
            prefix = input,
            limit = MAX_VISIBLE,
            previousThree = query.previousThreeWords
        )

    private fun phrasesForKey(query: SuggestionQuery, key: String?, input: String): List<String> {
        if (key.isNullOrBlank()) return emptyList()
        return predictor(query.language).predict(
            previous = key,
            prefix = input,
            limit = MAX_VISIBLE
        )
    }

    private fun typoCandidates(input: String, compact: List<String>): List<String> =
        (
            TypoCorrector.missingLetterCandidates(input, english::contains) +
                TypoCorrector.extraCandidates(input, english::contains) +
                TypoCorrector.commonCorrections(input, english::contains) +
                TypoCorrector.commonCorrections(input) { true }
            ).distinct()
            .filter { candidate ->
                compact.none { it.equals(candidate, ignoreCase = true) } &&
                    (CorrectionPolicy.shouldOfferTypo(input, candidate) ||
                        TypoCorrector.commonCorrections(input).any { it.equals(candidate, ignoreCase = true) })
            }

    private fun Map<String, Int>.scoreOf(word: String): Int {
        this[word]?.let { return it }
        val lower = word.lowercase()
        entries.forEach { (key, value) ->
            if (key.equals(lower, ignoreCase = true)) return value
        }
        return 0
    }

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
