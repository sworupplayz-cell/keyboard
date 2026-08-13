package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

/**
 * Offline Romanized-Nepali facade: vocabulary, spelling variants, ranking,
 * and a dictionary-free phonetic fallback for unknown words.
 */
class RomanNepaliConverter private constructor(
    private val dictionary: Map<String, List<String>>,
    private val keepEnglish: Set<String>
) {
    private val prefixSuggestions: Map<String, List<String>> = buildPrefixSuggestions(dictionary)
    private val typoSuggestions: RomanTypoSuggestions = buildTypoSuggestions(dictionary)
    private val phoneticCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean =
            size > PHONETIC_CACHE_LIMIT
    }

    fun exactConversion(romanWord: String): String? =
        dictionaryCandidates(normalize(romanWord)).firstOrNull()

    fun bestConversion(
        romanWord: String,
        learned: String? = null,
        previousWord: String? = null
    ): String {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty()) return romanWord
        if (isNepali(learned)) return learned.orEmpty()
        if (shouldPreserveToken(normalized)) return romanWord
        val exact = dictionaryCandidates(normalized)
        if (MixedLanguagePolicy.keepAsEnglish(normalized, previousWord, keepEnglish, exact.isNotEmpty(), romanWord)) {
            return romanWord
        }
        exact.firstOrNull()?.let { return it }
        if (normalized in keepEnglish) return romanWord
        return phoneticCandidates(normalized).firstOrNull() ?: romanWord
    }

    fun suggestions(
        romanWord: String,
        learned: String? = null,
        limit: Int = 3,
        includeRoman: Boolean = true,
        recent: List<String> = emptyList(),
        previousWord: String? = null,
        contextPredictions: List<String> = emptyList()
    ): List<String> {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty() || limit <= 0) return emptyList()

        val exactCandidates = dictionaryCandidates(normalized)
        val prefixMatches = prefixSuggestions[normalized].orEmpty()
        val typoMatches = ArrayList<String>()
        if (exactCandidates.isEmpty() && normalized.length >= 3 && prefixMatches.size < 2) {
            typoSuggestions.deletions[normalized].orEmpty().forEach(typoMatches::add)
            normalized.indices.forEach { index ->
                typoSuggestions.substitutions[substitutionLookupKey(normalized, index)]
                    .orEmpty()
                    .forEach(typoMatches::add)
            }
        }
        val keepEnglishNow = MixedLanguagePolicy.keepAsEnglish(
            normalized,
            previousWord,
            keepEnglish,
            exactCandidates.isNotEmpty(),
            romanWord
        )
        val phonetic = if (keepEnglishNow) emptyList() else phoneticCandidates(normalized)
        val contextNepali = if (keepEnglishNow) {
            emptyList()
        } else {
            contextPredictions.map { prediction ->
                exactConversion(prediction) ?: phoneticCandidates(normalize(prediction)).firstOrNull() ?: prediction
            }
        }
        val ranked = SuggestionRanker.rank(
            input = romanWord,
            prefixMatches = if (keepEnglishNow) emptyList() else exactCandidates + prefixMatches,
            typoMatches = if (keepEnglishNow) emptyList() else typoMatches + phonetic,
            learned = listOfNotNull(learned?.takeIf { isNepali(it) }),
            recent = recent,
            frequencyOf = { word ->
                val index = (exactCandidates + prefixMatches).indexOf(word)
                if (index >= 0) index else Int.MAX_VALUE
            },
            limit = if (includeRoman) (limit - 1).coerceAtLeast(1) else limit,
            contextMatches = contextNepali
        )
        if (!includeRoman) return ranked.take(limit)
        if (keepEnglishNow || (normalized in keepEnglish && exactCandidates.isEmpty())) {
            return (listOf(romanWord) + ranked).distinct().take(limit)
        }
        if (limit == 1) return ranked.take(1)
        return (ranked + romanWord).distinct().take(limit)
    }

    /** Returns the best rule-generated form even when the word is absent from the vocabulary. */
    fun transliterate(romanWord: String): String? =
        phoneticCandidates(normalize(romanWord)).firstOrNull()

    fun transliterationCandidates(romanWord: String): List<String> =
        phoneticCandidates(normalize(romanWord))

    fun convertText(
        text: String,
        learnedLookup: (String) -> String? = { null },
        previousWord: String? = null
    ): String = RomanTextProcessor.convert(text, previousWord) { word, previous ->
        bestConversion(word, learnedLookup(word), previous)
    }

    private fun phoneticCandidates(normalized: String): List<String> {
        if (normalized.isEmpty() || normalized.any { !it.isLetter() }) return emptyList()
        synchronized(phoneticCache) {
            phoneticCache[normalized]?.let { return it }
            val generated = RomanPhoneticEngine.candidates(normalized)
            phoneticCache[normalized] = generated
            return generated
        }
    }

    private fun dictionaryCandidates(normalized: String): List<String> {
        val results = LinkedHashSet<String>()
        RomanSpellingNormalizer.lookupForms(normalized).forEach { form ->
            dictionary[form].orEmpty().forEach(results::add)
        }
        return results.toList()
    }

    companion object {
        fun from(
            reader: Reader,
            keepEnglish: Set<String> = PREFERRED_ENGLISH_WORDS
        ): RomanNepaliConverter {
            val entries = linkedMapOf<String, List<String>>()
            reader.buffered().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEach
                    val parts = trimmed.split('\t', limit = 2)
                    if (parts.size != 2) return@forEach
                    val key = normalize(parts[0])
                    val values = parts[1].split('|').map(String::trim).filter(String::isNotEmpty)
                    if (key.isNotEmpty() && values.isNotEmpty()) entries[key] = values
                }
            }
            val english = LinkedHashSet<String>()
            PREFERRED_ENGLISH_WORDS.forEach(english::add)
            keepEnglish.map { normalize(it) }.filter(String::isNotEmpty).forEach(english::add)
            return RomanNepaliConverter(entries, english)
        }

        fun isNepali(value: String?): Boolean = !value.isNullOrBlank() && value.all { character ->
            character == ' ' || character.code in DEVANAGARI_RANGE
        }

        private fun normalize(value: String): String = RomanSpellingNormalizer.normalize(value)

        private fun shouldPreserveToken(normalized: String): Boolean {
            if (normalized.startsWith("http") || normalized.startsWith("www")) return true
            return normalized.any { !it.isLetter() }
        }

        private fun substitutionLookupKey(
            value: String,
            index: Int,
            typedCharacter: Char = value[index]
        ): String = value.replaceRange(index, index + 1, "*") + "|" + typedCharacter

        private fun buildTypoSuggestions(dictionary: Map<String, List<String>>): RomanTypoSuggestions {
            val deletions = linkedMapOf<String, LinkedHashSet<String>>()
            val substitutions = linkedMapOf<String, LinkedHashSet<String>>()
            dictionary.forEach { (roman, values) ->
                if (roman.length < 3) return@forEach
                roman.indices.forEach { index ->
                    if (roman[index] in VOWEL_CHARACTERS) {
                        val deletionBucket = deletions.getOrPut(roman.removeRange(index, index + 1)) {
                            LinkedHashSet()
                        }
                        values.forEach { value ->
                            if (deletionBucket.size < MAX_PREFIX_CANDIDATES) deletionBucket.add(value)
                        }
                    }
                    QWERTY_NEIGHBORS[roman[index]].orEmpty().forEach { nearbyKey ->
                        val substitutionBucket = substitutions.getOrPut(
                            substitutionLookupKey(roman, index, nearbyKey)
                        ) { LinkedHashSet() }
                        values.forEach { value ->
                            if (substitutionBucket.size < MAX_PREFIX_CANDIDATES) substitutionBucket.add(value)
                        }
                    }
                }
            }
            return RomanTypoSuggestions(
                deletions.mapValues { it.value.toList() },
                substitutions.mapValues { it.value.toList() }
            )
        }

        private fun buildPrefixSuggestions(dictionary: Map<String, List<String>>): Map<String, List<String>> {
            val prefixes = linkedMapOf<String, LinkedHashSet<String>>()
            dictionary.forEach { (roman, values) ->
                for (length in 1..roman.length) {
                    val bucket = prefixes.getOrPut(roman.substring(0, length)) { LinkedHashSet() }
                    if (bucket.size < MAX_PREFIX_CANDIDATES) {
                        values.forEach { if (bucket.size < MAX_PREFIX_CANDIDATES) bucket.add(it) }
                    }
                }
            }
            return prefixes.mapValues { (_, values) -> values.toList() }
        }

        private val QWERTY_NEIGHBORS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
            'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "ersfxc", 'f' to "rtdgcv",
            'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uihknm", 'k' to "iojlm",
            'l' to "opk", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
            'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )
        private val VOWEL_CHARACTERS = setOf('a', 'e', 'i', 'o', 'u')
        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private const val MAX_PREFIX_CANDIDATES = 8
        private const val PHONETIC_CACHE_LIMIT = 256
        private val PREFERRED_ENGLISH_WORDS = setOf(
            "unknown", "school", "college", "class", "office", "job", "meeting", "homework",
            "teacher", "student", "exam", "project", "mobile", "phone", "computer", "laptop",
            "internet", "online", "email", "message", "chat", "video", "photo", "bus", "car",
            "bike", "taxi", "train", "plane", "ok", "okay", "hello", "hi", "bye", "thanks",
            "please", "sorry", "yes", "no", "the", "to", "of", "and", "a", "in", "is", "it",
            "you", "that", "for", "on", "with", "as", "at", "this", "but", "from", "or", "an",
            "be", "are", "was", "were", "have", "has", "had", "not", "we", "they", "my", "your",
            "can", "will", "just", "about", "like", "so", "what", "when", "who", "how", "all",
            "good", "new", "time", "day", "work", "home", "friend", "family", "food", "water",
            "app", "google", "facebook", "youtube", "instagram", "whatsapp", "wifi", "file", "man",
            "i", "am", "awesome", "today", "because"
        )
    }

    private data class RomanTypoSuggestions(
        val deletions: Map<String, List<String>>,
        val substitutions: Map<String, List<String>>
    )
}

class LearnedRomanWords(
    initial: Map<String, String> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val mappings = linkedMapOf<String, String>()

    init {
        initial.forEach { (roman, nepali) -> learn(roman, nepali) }
    }

    fun lookup(roman: String): String? = mappings[normalize(roman)]

    fun learn(roman: String, nepali: String): Boolean {
        val key = normalize(roman)
        if (key.isEmpty() || key.any { !it.isLetter() } || !RomanNepaliConverter.isNepali(nepali)) return false
        mappings.remove(key)
        mappings[key] = nepali
        while (mappings.size > limit) mappings.remove(mappings.keys.first())
        return true
    }

    fun serialize(): String = mappings.entries.joinToString("\n") { (roman, nepali) -> "$roman\t$nepali" }

    fun values(): Map<String, String> = mappings.toMap()

    companion object {
        const val DEFAULT_LIMIT = 250

        fun fromSerialized(value: String?): LearnedRomanWords {
            val entries = linkedMapOf<String, String>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) entries[parts[0]] = parts[1]
            }
            return LearnedRomanWords(entries)
        }

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)
    }
}

object RomanSelectionState {
    fun movedAwayFromComposition(
        currentWord: String,
        newSelectionStart: Int,
        newSelectionEnd: Int,
        composingEnd: Int
    ): Boolean = currentWord.isNotEmpty() &&
        (newSelectionStart != composingEnd || newSelectionEnd != composingEnd)
}

sealed class RomanEdit {
    data class SetComposing(val text: String) : RomanEdit()
    data class Commit(val text: String) : RomanEdit()
    data object ClearComposing : RomanEdit()
    data object DeletePrevious : RomanEdit()
    data object NoOp : RomanEdit()
}

/** Pure composing state used by the IME and unit tests. */
class RomanInputComposer(
    private val converter: RomanNepaliConverter,
    private val learnedLookup: (String) -> String? = { null }
) {
    var currentWord: String = ""
        private set

    fun type(text: String): RomanEdit {
        currentWord += text
        return RomanEdit.SetComposing(currentWord)
    }

    fun backspace(): RomanEdit {
        if (currentWord.isEmpty()) return RomanEdit.DeletePrevious
        currentWord = currentWord.dropLast(1)
        return if (currentWord.isEmpty()) RomanEdit.ClearComposing
        else RomanEdit.SetComposing(currentWord)
    }

    fun finishWord(boundary: String = "", previousWord: String? = null): RomanEdit {
        if (currentWord.isEmpty()) {
            return if (boundary.isEmpty()) RomanEdit.NoOp else RomanEdit.Commit(boundary)
        }
        val completed = converter.bestConversion(currentWord, learnedLookup(currentWord), previousWord)
        currentWord = ""
        return RomanEdit.Commit(completed + boundary)
    }

    fun acceptSuggestion(suggestion: String): RomanEdit {
        currentWord = ""
        return RomanEdit.Commit(suggestion)
    }

    fun reset() {
        currentWord = ""
    }
}
