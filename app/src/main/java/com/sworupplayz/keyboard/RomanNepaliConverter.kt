package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

/** Offline vocabulary plus reusable phonetic Roman-to-Devanagari rules. */
class RomanNepaliConverter private constructor(
    private val dictionary: Map<String, List<String>>
) {
    private val prefixSuggestions: Map<String, List<String>> = buildPrefixSuggestions(dictionary)
    private val typoSuggestions: RomanTypoSuggestions = buildTypoSuggestions(dictionary)

    fun exactConversion(romanWord: String): String? =
        dictionaryCandidates(normalize(romanWord)).firstOrNull()

    fun bestConversion(romanWord: String, learned: String? = null): String {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty()) return romanWord
        if (isNepali(learned)) return learned.orEmpty()
        dictionaryCandidates(normalized).firstOrNull()?.let { return it }
        if (normalized in PREFERRED_ENGLISH_WORDS) return romanWord
        return transliterate(normalized) ?: romanWord
    }

    fun suggestions(
        romanWord: String,
        learned: String? = null,
        limit: Int = 3,
        includeRoman: Boolean = true
    ): List<String> {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty() || limit <= 0) return emptyList()

        val candidates = LinkedHashSet<String>()
        if (isNepali(learned)) candidates.add(learned.orEmpty())
        val exactCandidates = dictionaryCandidates(normalized)
        exactCandidates.forEach(candidates::add)
        prefixSuggestions[normalized].orEmpty().forEach(candidates::add)
        if (exactCandidates.isEmpty() && normalized.length >= 3 && candidates.size < 2) {
            typoSuggestions.deletions[normalized].orEmpty().forEach(candidates::add)
            normalized.indices.forEach { index ->
                typoSuggestions.substitutions[substitutionLookupKey(normalized, index)]
                    .orEmpty()
                    .forEach(candidates::add)
            }
        }
        transliterationCandidates(normalized).forEach(candidates::add)

        val romanFirst = normalized in PREFERRED_ENGLISH_WORDS
        if (!includeRoman) return candidates.take(limit)
        if (romanFirst) return (listOf(romanWord) + candidates).distinct().take(limit)
        if (limit == 1) return candidates.take(1)
        return (candidates.take(limit - 1) + romanWord).distinct().take(limit)
    }

    /** Returns the best rule-generated form even when the word is absent from the vocabulary. */
    fun transliterate(romanWord: String): String? =
        transliterationCandidates(normalize(romanWord)).firstOrNull()

    fun transliterationCandidates(romanWord: String): List<String> {
        val normalized = normalize(romanWord)
        if (normalized.isEmpty() || normalized.any { !it.isLetter() }) return emptyList()

        val results = LinkedHashSet<String>()
        transliterateInternal(normalized)?.let(results::add)
        if ("ch" in normalized && "chh" !in normalized) {
            transliterateInternal(normalized, aspirateCh = true)?.let(results::add)
        }
        if ('v' in normalized || 'w' in normalized) {
            transliterateInternal(normalized, labialAsBa = true)?.let(results::add)
        }
        if ("sh" in normalized) {
            transliterateInternal(normalized, retroflexSha = true)?.let(results::add)
        }
        return results.toList()
    }

    private fun transliterateInternal(
        value: String,
        aspirateCh: Boolean = false,
        labialAsBa: Boolean = false,
        retroflexSha: Boolean = false
    ): String? {
        val tokens = tokenize(value) ?: return null
        val output = StringBuilder()
        tokens.forEachIndexed { index, token ->
            when (token.type) {
                TokenType.CONSONANT -> {
                    val consonant = when {
                        token.value == "ch" && aspirateCh -> "छ"
                        (token.value == "v" || token.value == "w") && labialAsBa -> "ब"
                        token.value == "sh" && retroflexSha -> "ष"
                        else -> CONSONANTS.getValue(token.value)
                    }
                    output.append(consonant)
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
        return output.toString().takeIf(String::isNotEmpty)
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

    private fun dictionaryCandidates(normalized: String): List<String> {
        val results = LinkedHashSet<String>()
        lookupForms(normalized).forEach { form -> dictionary[form].orEmpty().forEach(results::add) }
        return results.toList()
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

        fun isNepali(value: String?): Boolean = !value.isNullOrBlank() && value.all { character ->
            character == ' ' || character.code in DEVANAGARI_RANGE
        }

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun lookupForms(value: String): List<String> {
            val forms = LinkedHashSet<String>()
            forms += value
            forms += collapseRepeatedVowels(value)
            forms += collapseRepeatedLetters(value)
            if (value.startsWith("aa")) forms += value.drop(1)
            if (value.endsWith("ey")) {
                forms += value.dropLast(1)
                forms += value.dropLast(2) + "ai"
            }
            if ("chh" in value) forms += value.replace("chh", "ch")
            else if ("ch" in value) forms += value.replace("ch", "chh")
            if ("bh" in value) forms += value.replace("bh", "v")
            if ('v' in value) forms += value.replace("v", "bh")
            if ('w' in value) {
                forms += value.replace('w', 'v')
                forms += value.replace("w", "bh")
            }
            if (value.endsWith('y')) forms += value.dropLast(1) + "i"
            return forms.toList()
        }

        private fun collapseRepeatedVowels(value: String): String {
            val output = StringBuilder(value.length)
            value.forEach { character ->
                if (character !in VOWEL_CHARACTERS || output.lastOrNull() != character) output.append(character)
            }
            return output.toString()
        }

        private fun collapseRepeatedLetters(value: String): String {
            val output = StringBuilder(value.length)
            value.forEach { character -> if (output.lastOrNull() != character) output.append(character) }
            return output.toString()
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
                    if (bucket.size < MAX_PREFIX_CANDIDATES) values.forEach { if (bucket.size < MAX_PREFIX_CANDIDATES) bucket.add(it) }
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
        private val CONSONANTS = linkedMapOf(
            "nchh" to "न्छ", "nch" to "न्छ", "rchh" to "र्छ", "rch" to "र्छ",
            "ksh" to "क्ष", "chh" to "छ", "shr" to "श्र", "gn" to "ज्ञ",
            "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ",
            "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ",
            "sh" to "श", "ng" to "ङ", "ny" to "ञ", "tr" to "त्र", "gy" to "ज्ञ",
            "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द",
            "n" to "न", "p" to "प", "b" to "ब", "m" to "म", "y" to "य",
            "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
            "h" to "ह", "f" to "फ", "q" to "क", "c" to "क", "x" to "क्स", "z" to "ज"
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
        private val VOWEL_CHARACTERS = setOf('a', 'e', 'i', 'o', 'u')
        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private const val MAX_PREFIX_CANDIDATES = 5
        private val PREFERRED_ENGLISH_WORDS = setOf(
            "unknown", "school", "college", "class", "office", "job", "meeting", "homework",
            "mobile", "phone", "computer", "laptop", "internet", "online", "email", "message",
            "chat", "video", "photo", "bus", "car", "bike", "taxi", "ok", "hello", "thanks"
        )
    }

    private data class RomanTypoSuggestions(
        val deletions: Map<String, List<String>>,
        val substitutions: Map<String, List<String>>
    )

    private data class Token(val type: TokenType, val value: String)
    private enum class TokenType { CONSONANT, VOWEL }
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
        const val DEFAULT_LIMIT = 100

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

    fun finishWord(boundary: String = ""): RomanEdit {
        if (currentWord.isEmpty()) {
            return if (boundary.isEmpty()) RomanEdit.NoOp else RomanEdit.Commit(boundary)
        }
        val completed = converter.bestConversion(currentWord, learnedLookup(currentWord))
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
