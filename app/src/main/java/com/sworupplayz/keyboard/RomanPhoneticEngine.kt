package com.sworupplayz.keyboard

/**
 * Dictionary-free Roman → Devanagari engine.
 *
 * Known vocabulary can rank above these forms, but unknown and made-up words
 * still receive a reasonable conversion. Rules cover vowels, consonants,
 * aspiration, clusters, matras, nasals, virama, and common Nepali endings.
 */
object RomanPhoneticEngine {
    private const val VIRAMA = '्'
    private const val ANUSVARA = 'ं'
    private const val CANDRABINDU = 'ँ'
    private val DEVANAGARI_MATRAS = setOf('ा', 'ि', 'ी', 'ु', 'ू', 'ृ', 'े', 'ै', 'ो', 'ौ', 'ं', 'ँ', 'ः')

    fun transliterate(romanWord: String): String? = candidates(romanWord, limit = 1).firstOrNull()

    fun candidates(romanWord: String, limit: Int = 4): List<String> {
        val forms = RomanSpellingNormalizer.phoneticForms(romanWord)
        if (forms.isEmpty() || limit <= 0) return emptyList()

        val scored = linkedMapOf<String, Int>()
        forms.forEach { form ->
            val defaultForm = render(form)
            consider(scored, defaultForm, likelihood(form, default = true))
            if ("ch" in form && "chh" !in form) {
                consider(scored, render(form, aspirateCh = true), likelihood(form, aspirateCh = true))
            }
            if ('v' in form || 'w' in form) {
                consider(scored, render(form, labialAsBa = true), likelihood(form, labialAsBa = true))
            }
            if ("sh" in form) {
                consider(scored, render(form, retroflexSha = true), likelihood(form, retroflexSha = true))
            }
            if ("ri" in form || "rhi" in form) {
                consider(scored, render(form, vocalicRi = true), likelihood(form, vocalicRi = true))
            }
            consider(scored, render(form, nasalMark = NasalMark.ANUSVARA), likelihood(form, anusvara = true))
            if (!endsWithChaFamilyGlyph(defaultForm)) {
                consider(scored, renderWithCommonEnding(form), likelihood(form, ending = true))
            }
        }
        return scored.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .distinct()
            .take(limit)
    }

    private fun consider(scored: MutableMap<String, Int>, value: String?, score: Int) {
        if (value.isNullOrEmpty()) return
        val existing = scored[value]
        if (existing == null || score > existing) scored[value] = score
    }

    private fun likelihood(
        form: String,
        default: Boolean = false,
        aspirateCh: Boolean = false,
        labialAsBa: Boolean = false,
        retroflexSha: Boolean = false,
        vocalicRi: Boolean = false,
        anusvara: Boolean = false,
        ending: Boolean = false
    ): Int {
        var score = 80
        if (default) score += 25
        if (ending && hasCommonEnding(form)) score += 30
        if (aspirateCh && hasChaFamilyEnding(form)) score += 20
        else if (aspirateCh) score -= 15
        if (labialAsBa) score -= 20
        if (retroflexSha) score -= 25
        if (vocalicRi) score -= 20
        if (anusvara) score -= 10
        return score
    }

    private fun renderWithCommonEnding(value: String): String? {
        val ending = COMMON_ENDINGS.firstOrNull { (suffix, _) ->
            value.length > suffix.length + 1 && value.endsWith(suffix)
        } ?: return null
        val stem = render(value.dropLast(ending.first.length)) ?: return null
        return stem + ending.second
    }

    private fun hasCommonEnding(value: String): Boolean =
        COMMON_ENDINGS.any { value.length > it.first.length + 1 && value.endsWith(it.first) }

    private fun hasChaFamilyEnding(value: String): Boolean =
        CHA_FAMILY_ENDINGS.any { value.endsWith(it) }

    private fun endsWithChaFamilyGlyph(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        val lastLetter = value.lastOrNull { it !in DEVANAGARI_MATRAS && it != VIRAMA }
        return lastLetter == 'छ'
    }

    private fun render(
        value: String,
        aspirateCh: Boolean = false,
        labialAsBa: Boolean = false,
        retroflexSha: Boolean = false,
        vocalicRi: Boolean = false,
        nasalMark: NasalMark = NasalMark.NONE
    ): String? {
        val tokens = tokenize(value, vocalicRi) ?: return null
        val output = StringBuilder(value.length * 2)
        tokens.forEachIndexed { index, token ->
            when (token.type) {
                TokenType.CONSONANT -> {
                    output.append(consonantGlyph(token.value, aspirateCh, labialAsBa, retroflexSha))
                    val next = tokens.getOrNull(index + 1)
                    if (next?.type == TokenType.CONSONANT) {
                        when {
                            alreadyHasVirama(token.value, aspirateCh) -> Unit
                            nasalMark == NasalMark.ANUSVARA && token.value == "n" && next.value in ANUSVARA_FOLLOWERS -> {
                                if (output.lastOrNull() == 'न') {
                                    output.deleteCharAt(output.lastIndex)
                                    output.append(ANUSVARA)
                                }
                            }
                            shouldJoinWithVirama(token.value, next.value) -> output.append(VIRAMA)
                        }
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
        return applyFinalChaPreference(value, output.toString()).takeIf(String::isNotEmpty)
    }

    private fun applyFinalChaPreference(roman: String, devanagari: String): String {
        if (roman.length <= 3 || !hasChaFamilyEnding(roman)) return devanagari
        val lastLetterIndex = devanagari.indexOfLast { it !in DEVANAGARI_MATRAS && it != VIRAMA }
        if (lastLetterIndex < 0 || devanagari[lastLetterIndex] != 'च') return devanagari
        return devanagari.substring(0, lastLetterIndex) + 'छ' + devanagari.substring(lastLetterIndex + 1)
    }

    private fun consonantGlyph(
        key: String,
        aspirateCh: Boolean,
        labialAsBa: Boolean,
        retroflexSha: Boolean
    ): String = when {
        key == "ch" && aspirateCh -> "छ"
        (key == "v" || key == "w") && labialAsBa -> "ब"
        key == "sh" && retroflexSha -> "ष"
        else -> CONSONANTS.getValue(key)
    }

    private fun alreadyHasVirama(key: String, aspirateCh: Boolean): Boolean {
        val glyph = if (key == "ch" && aspirateCh) "छ" else CONSONANTS[key]
        return glyph?.any { it == VIRAMA } == true
    }

    private fun shouldJoinWithVirama(left: String, right: String): Boolean {
        if (right in GLIDE_SECONDS) return true
        if (left == "n") return right in NASAL_CLUSTER_SECONDS
        if (left == "m") return right in LABIAL_CLUSTER_SECONDS || right in GLIDE_SECONDS
        if (left == "s" || left == "sh") return right in SIBILANT_CLUSTER_SECONDS
        return true
    }

    private fun tokenize(value: String, vocalicRi: Boolean): List<Token>? {
        val tokens = ArrayList<Token>(value.length)
        var position = 0
        while (position < value.length) {
            val token = nextToken(value, position, vocalicRi) ?: return null
            tokens += token
            position += token.value.length
        }
        return tokens
    }

    private fun nextToken(value: String, position: Int, vocalicRi: Boolean): Token? {
        val consonant = CONSONANT_KEYS.firstOrNull { value.startsWith(it, position) }
        val vowel = VOWEL_KEYS.firstOrNull { value.startsWith(it, position) }
        val preferVocalicRi = vowel != null &&
            (vowel == "ri" || vowel == "rhi") &&
            (position == 0 || vocalicRi)
        if (preferVocalicRi) return Token(TokenType.VOWEL, vowel!!)
        if (consonant != null) return Token(TokenType.CONSONANT, consonant)
        if (vowel != null) return Token(TokenType.VOWEL, vowel)
        return null
    }

    private val CONSONANTS = linkedMapOf(
        "nchh" to "न्छ",
        "nch" to "न्छ",
        "rchh" to "र्छ",
        "rch" to "र्छ",
        "ksh" to "क्ष",
        "chh" to "छ",
        "shr" to "श्र",
        "gn" to "ज्ञ",
        "tth" to "ठ",
        "ddh" to "ढ",
        "kh" to "ख",
        "gh" to "घ",
        "ch" to "च",
        "jh" to "झ",
        "th" to "थ",
        "dh" to "ध",
        "ph" to "फ",
        "bh" to "भ",
        "sh" to "श",
        "ng" to "ङ",
        "ny" to "ञ",
        "tr" to "त्र",
        "tt" to "ट",
        "dd" to "ड",
        "gy" to "ज्ञ",
        "k" to "क",
        "g" to "ग",
        "j" to "ज",
        "t" to "त",
        "d" to "द",
        "n" to "न",
        "p" to "प",
        "b" to "ब",
        "m" to "म",
        "y" to "य",
        "r" to "र",
        "l" to "ल",
        "v" to "व",
        "w" to "व",
        "s" to "स",
        "h" to "ह",
        "f" to "फ",
        "q" to "क",
        "c" to "क",
        "x" to "क्स",
        "z" to "ज"
    )

    private val INDEPENDENT_VOWELS = mapOf(
        "aa" to "आ",
        "ii" to "ई",
        "ee" to "ई",
        "uu" to "ऊ",
        "oo" to "ऊ",
        "ai" to "ऐ",
        "au" to "औ",
        "ri" to "ऋ",
        "rhi" to "ऋ",
        "a" to "अ",
        "i" to "इ",
        "u" to "उ",
        "e" to "ए",
        "o" to "ओ"
    )

    private val VOWEL_SIGNS = mapOf(
        "aa" to "ा",
        "ii" to "ी",
        "ee" to "ी",
        "uu" to "ू",
        "oo" to "ू",
        "ai" to "ै",
        "au" to "ौ",
        "ri" to "ृ",
        "rhi" to "ृ",
        "a" to "",
        "i" to "ि",
        "u" to "ु",
        "e" to "े",
        "o" to "ो"
    )

    private val CONSONANT_KEYS = CONSONANTS.keys.sortedByDescending(String::length)
    private val VOWEL_KEYS = INDEPENDENT_VOWELS.keys.sortedByDescending(String::length)

    private val GLIDE_SECONDS = setOf("r", "y", "l", "v", "w")
    private val NASAL_CLUSTER_SECONDS = setOf(
        "ch", "chh", "t", "th", "d", "dh", "j", "jh", "k", "kh", "g", "gh", "tt", "dd", "tth", "ddh"
    )
    private val LABIAL_CLUSTER_SECONDS = setOf("p", "ph", "b", "bh", "m")
    private val SIBILANT_CLUSTER_SECONDS = setOf("t", "th", "k", "kh", "p", "ph", "n", "m", "tt")
    private val ANUSVARA_FOLLOWERS = setOf("k", "kh", "g", "gh", "ch", "chh", "j", "jh")

    private val COMMON_ENDINGS = listOf(
        "chhu" to "छु",
        "chhau" to "छौ",
        "chhan" to "छन्",
        "chha" to "छ",
        "chu" to "छु",
        "chau" to "छौ",
        "chan" to "छन्",
        "cha" to "छ"
    )
    private val CHA_FAMILY_ENDINGS = listOf("chhu", "chhau", "chhan", "chha", "chu", "chau", "chan", "cha")

    private data class Token(val type: TokenType, val value: String)
    private enum class TokenType { CONSONANT, VOWEL }
    private enum class NasalMark { NONE, ANUSVARA }

    /** Exposed for tests that want to assert chandrabindu remains available as a mark. */
    internal val nasalMarks: Pair<Char, Char> = ANUSVARA to CANDRABINDU
}
