package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

/** Small, offline Roman-to-Nepali dictionary with a conservative rule-based fallback. */
class RomanNepaliConverter private constructor(
    private val dictionary: Map<String, List<String>>
) {
    fun exactConversion(romanWord: String): String? =
        dictionary[normalize(romanWord)]?.firstOrNull()

    fun suggestions(romanWord: String, limit: Int = 3): List<String> {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty() || limit <= 0) return emptyList()

        val results = LinkedHashSet<String>()
        dictionary[normalized].orEmpty().forEach(results::add)

        dictionary.entries.asSequence()
            .filter { (roman, _) -> roman.startsWith(normalized) && roman != normalized }
            .sortedWith(compareBy({ it.key.length }, { it.key }))
            .flatMap { it.value.asSequence() }
            .forEach { value ->
                if (results.size < limit) results.add(value)
            }

        if (results.isEmpty()) {
            transliterate(normalized)?.let(results::add)
        }
        return results.take(limit)
    }

    /**
     * A deliberately small fallback for suggestions only. Unknown words are never
     * auto-converted, so users can always keep their original Roman spelling.
     */
    fun transliterate(romanWord: String): String? {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty() || normalized.any { !it.isLetter() }) return null

        val tokens = tokenize(normalized) ?: return null
        val output = StringBuilder()
        tokens.forEachIndexed { index, token ->
            when (token.type) {
                TokenType.CONSONANT -> {
                    output.append(CONSONANTS.getValue(token.value))
                    if (tokens.getOrNull(index + 1)?.type == TokenType.CONSONANT) {
                        output.append('्')
                    }
                }
                TokenType.VOWEL -> {
                    val followsConsonant = tokens.getOrNull(index - 1)?.type == TokenType.CONSONANT
                    output.append(
                        if (followsConsonant) VOWEL_SIGNS.getValue(token.value)
                        else INDEPENDENT_VOWELS.getValue(token.value)
                    )
                }
            }
        }
        return output.toString().takeIf { it.isNotEmpty() }
    }

    private fun tokenize(value: String): List<Token>? {
        val tokens = ArrayList<Token>(value.length)
        var position = 0
        while (position < value.length) {
            val consonant = CONSONANT_KEYS.firstOrNull { value.startsWith(it, position) }
            if (consonant != null) {
                tokens += Token(TokenType.CONSONANT, consonant)
                position += consonant.length
                continue
            }
            val vowel = VOWEL_KEYS.firstOrNull { value.startsWith(it, position) }
            if (vowel != null) {
                tokens += Token(TokenType.VOWEL, vowel)
                position += vowel.length
                continue
            }
            return null
        }
        return tokens
    }

    companion object {
        fun from(reader: Reader): RomanNepaliConverter {
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
            return RomanNepaliConverter(entries)
        }

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private val CONSONANTS = linkedMapOf(
            "ksh" to "क्ष", "chh" to "छ", "shr" to "श्र",
            "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ",
            "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ",
            "sh" to "श", "ng" to "ङ", "ny" to "ञ", "tr" to "त्र", "gy" to "ज्ञ",
            "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द",
            "n" to "न", "p" to "प", "b" to "ब", "m" to "म", "y" to "य",
            "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
            "h" to "ह", "f" to "फ", "q" to "क", "x" to "क्स", "z" to "ज"
        )
        private val INDEPENDENT_VOWELS = mapOf(
            "aa" to "आ", "ii" to "ई", "ee" to "ई", "uu" to "ऊ", "oo" to "ऊ",
            "ai" to "ऐ", "au" to "औ", "a" to "अ", "i" to "इ", "u" to "उ",
            "e" to "ए", "o" to "ओ"
        )
        private val VOWEL_SIGNS = mapOf(
            "aa" to "ा", "ii" to "ी", "ee" to "ी", "uu" to "ू", "oo" to "ू",
            "ai" to "ै", "au" to "ौ", "a" to "", "i" to "ि", "u" to "ु",
            "e" to "े", "o" to "ो"
        )
        private val CONSONANT_KEYS = CONSONANTS.keys.sortedByDescending(String::length)
        private val VOWEL_KEYS = INDEPENDENT_VOWELS.keys.sortedByDescending(String::length)
    }

    private data class Token(val type: TokenType, val value: String)
    private enum class TokenType { CONSONANT, VOWEL }
}

sealed class RomanEdit {
    data class SetComposing(val text: String) : RomanEdit()
    data class Commit(val text: String) : RomanEdit()
    data object ClearComposing : RomanEdit()
    data object DeletePrevious : RomanEdit()
    data object NoOp : RomanEdit()
}

/** Pure input state used by the IME and unit tests. */
class RomanInputComposer(private val converter: RomanNepaliConverter) {
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

    fun finishWord(boundary: String = ""): RomanEdit {
        if (currentWord.isEmpty()) {
            return if (boundary.isEmpty()) RomanEdit.NoOp else RomanEdit.Commit(boundary)
        }
        val completed = converter.exactConversion(currentWord) ?: currentWord
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
