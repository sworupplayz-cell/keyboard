package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

/** Small indexed prefix dictionary with conservative one-edit typo candidates. */
class LocalWordSuggester private constructor(words: List<String>) {
    private val vocabulary = words.map(String::trim).filter(String::isNotEmpty).distinct()
    private val prefixIndex = linkedMapOf<String, List<String>>()
    private val deletionIndex = linkedMapOf<String, List<String>>()
    private val substitutionIndex = linkedMapOf<String, List<String>>()

    init {
        val prefixBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        val deletionBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        val substitutionBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        vocabulary.forEach { word ->
            val normalized = normalize(word)
            for (length in 1..normalized.length) {
                prefixBuckets.getOrPut(normalized.substring(0, length)) { LinkedHashSet() }
                    .addBounded(word)
            }
            if (normalized.length >= MIN_TYPO_LENGTH) {
                val isLatinWord = normalized.all { it in 'a'..'z' }
                normalized.indices.forEach { index ->
                    if (!isLatinWord || normalized[index] in LATIN_VOWELS) {
                        deletionBuckets.getOrPut(normalized.removeRange(index, index + 1)) { LinkedHashSet() }
                            .addBounded(word)
                    }
                    substitutionBuckets.getOrPut(normalized.replaceRange(index, index + 1, "*")) { LinkedHashSet() }
                        .addBounded(word)
                }
            }
        }
        prefixBuckets.forEach { (key, values) -> prefixIndex[key] = values.toList() }
        deletionBuckets.forEach { (key, values) -> deletionIndex[key] = values.toList() }
        substitutionBuckets.forEach { (key, values) -> substitutionIndex[key] = values.toList() }
    }

    fun suggestions(
        input: String,
        learned: List<String> = emptyList(),
        limit: Int = 3
    ): List<String> {
        val normalized = normalize(input)
        if (normalized.isEmpty() || limit <= 0) return emptyList()

        val results = LinkedHashSet<String>()
        learned.filter { normalize(it).startsWith(normalized) }.forEach(results::add)
        prefixIndex[normalized].orEmpty().forEach(results::add)

        if (results.size < 2 && normalized.length >= MIN_TYPO_LENGTH) {
            val collapsed = collapseRepeatedLetters(normalized)
            if (collapsed != normalized) prefixIndex[collapsed].orEmpty().forEach(results::add)
            deletionIndex[normalized].orEmpty().forEach(results::add)
            normalized.indices.forEach { index ->
                substitutionIndex[normalized.replaceRange(index, index + 1, "*")]
                    .orEmpty()
                    .filter { candidate -> hasSingleNearbyKeySubstitution(normalized, normalize(candidate)) }
                    .forEach(results::add)
            }
        }

        val formatted = results.map { formatLikeInput(input, it) }.toMutableList()
        if (formatted.none { it == input }) {
            if (formatted.size >= limit) formatted.removeAt(limit - 1)
            formatted.add(input)
        }
        return formatted.distinct().take(limit)
    }

    companion object {
        fun fromWords(words: List<String>): LocalWordSuggester = LocalWordSuggester(words)

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun formatLikeInput(input: String, candidate: String): String =
            if (input.firstOrNull()?.isUpperCase() == true) {
                candidate.replaceFirstChar { it.uppercaseChar() }
            } else {
                candidate
            }

        private fun collapseRepeatedLetters(value: String): String {
            val output = StringBuilder(value.length)
            value.forEach { character -> if (output.lastOrNull() != character) output.append(character) }
            return output.toString()
        }

        private fun hasSingleNearbyKeySubstitution(first: String, second: String): Boolean {
            if (first.length != second.length) return false
            var mismatch = -1
            first.indices.forEach { index ->
                if (first[index] != second[index]) {
                    if (mismatch >= 0) return false
                    mismatch = index
                }
            }
            if (mismatch < 0) return false
            return second[mismatch] in QWERTY_NEIGHBORS[first[mismatch]].orEmpty()
        }

        private fun LinkedHashSet<String>.addBounded(value: String) {
            if (size < INDEX_CANDIDATE_LIMIT) add(value)
        }

        private const val LATIN_VOWELS = "aeiou"
        private val QWERTY_NEIGHBORS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
            'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "ersfxc", 'f' to "rtdgcv",
            'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uihknm", 'k' to "iojlm",
            'l' to "opk", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
            'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )
        private const val INDEX_CANDIDATE_LIMIT = 8
        private const val MIN_TYPO_LENGTH = 3
    }
}

class LearnedWordStore(
    initial: Map<String, Int> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val scores = linkedMapOf<String, Int>()

    init {
        initial.forEach { (word, score) -> if (isValid(word)) scores[word] = score.coerceAtLeast(1) }
        trimToLimit()
    }

    fun record(word: String): Boolean {
        val clean = word.trim()
        if (!isValid(clean)) return false
        val score = (scores.remove(clean) ?: 0) + 1
        scores[clean] = score
        trimToLimit()
        return true
    }

    fun suggestions(prefix: String, limit: Int = 6): List<String> {
        val normalized = prefix.lowercase(Locale.ENGLISH)
        return scores.entries.toList().asReversed()
            .filter { it.key.lowercase(Locale.ENGLISH).startsWith(normalized) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun serialize(): String = scores.entries.joinToString("\n") { (word, score) -> "$word\t$score" }

    private fun trimToLimit() {
        while (scores.size > limit) scores.remove(scores.keys.first())
    }

    private fun isValid(value: String): Boolean = value.isNotEmpty() &&
        '\n' !in value && '\t' !in value && value.length <= MAX_WORD_LENGTH

    companion object {
        const val DEFAULT_LIMIT = 100
        private const val MAX_WORD_LENGTH = 48

        fun fromSerialized(value: String?): LearnedWordStore {
            val entries = linkedMapOf<String, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) entries[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
            return LearnedWordStore(entries)
        }
    }
}

enum class DirectTypingLanguage { ENGLISH, NEPALI }

class DirectTypingState {
    var currentWord: String = ""
        private set

    fun append(text: String, language: DirectTypingLanguage): Boolean {
        val isWordText = when (language) {
            DirectTypingLanguage.ENGLISH -> text.all { it in 'A'..'Z' || it in 'a'..'z' }
            DirectTypingLanguage.NEPALI -> isNepaliWordText(text)
        }
        if (!isWordText) {
            clear()
            return false
        }
        currentWord += text
        return true
    }

    fun backspace(): Boolean {
        if (currentWord.isEmpty()) return false
        val lastCodePointStart = currentWord.offsetByCodePoints(currentWord.length, -1)
        currentWord = currentWord.substring(0, lastCodePointStart)
        return true
    }

    fun replaceWith(word: String) {
        currentWord = word
    }

    fun clear() {
        currentWord = ""
    }

    fun codePointCount(): Int = currentWord.codePointCount(0, currentWord.length)

    private fun isNepaliWordText(text: String): Boolean {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            if (codePoint !in DEVANAGARI_RANGE ||
                codePoint in NEPALI_BOUNDARIES ||
                codePoint in NEPALI_DIGITS
            ) return false
            offset += Character.charCount(codePoint)
        }
        return text.isNotEmpty()
    }

    companion object {
        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private val NEPALI_DIGITS = 0x0966..0x096F
        private val NEPALI_BOUNDARIES = setOf(0x0964, 0x0965, 0x0970)
    }
}

data class SuggestionReplacement(
    val deleteCodePoints: Int,
    val deleteCodeUnits: Int,
    val replacement: String,
    val changesText: Boolean
)

object SuggestionSelectionPlan {
    fun create(currentWord: String, suggestion: String, textBeforeCursor: String): SuggestionReplacement? {
        if (currentWord.isEmpty() || textBeforeCursor != currentWord) return null
        return SuggestionReplacement(
            deleteCodePoints = currentWord.codePointCount(0, currentWord.length),
            deleteCodeUnits = currentWord.length,
            replacement = suggestion,
            changesText = currentWord != suggestion
        )
    }
}

object VocabularyLoader {
    fun english(reader: Reader): List<String> = reader.buffered().useLines { lines ->
        lines.map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .toList()
    }

    fun nepaliFromRomanDictionary(reader: Reader): List<String> {
        val words = LinkedHashSet<String>()
        reader.buffered().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEach
                val value = trimmed.split('\t', limit = 2).getOrNull(1) ?: return@forEach
                value.split('|').map(String::trim)
                    .filter { it.isNotEmpty() && ' ' !in it }
                    .forEach(words::add)
            }
        }
        return words.toList()
    }
}
