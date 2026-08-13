package com.sworupplayz.keyboard

import java.util.Locale
import kotlin.math.min

/** Conservative offline typo helpers. Unknown text is never rewritten automatically. */
object TypoCorrector {
    private val LATIN_VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    val QWERTY_NEIGHBORS = mapOf(
        'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
        'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "ersfxc", 'f' to "rtdgcv",
        'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uihknm", 'k' to "iojlm",
        'l' to "opk", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
        'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
    )

    fun damerauDistance(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length
        val rows = first.length + 1
        val columns = second.length + 1
        val table = Array(rows) { IntArray(columns) }
        for (i in 0 until rows) table[i][0] = i
        for (j in 0 until columns) table[0][j] = j
        for (i in 1 until rows) {
            for (j in 1 until columns) {
                val cost = if (first[i - 1] == second[j - 1]) 0 else 1
                table[i][j] = min(
                    min(table[i - 1][j] + 1, table[i][j - 1] + 1),
                    table[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]) {
                    table[i][j] = min(table[i][j], table[i - 2][j - 2] + 1)
                }
            }
        }
        return table[first.length][second.length]
    }

    fun isNearbySubstitution(typed: String, candidate: String): Boolean {
        if (typed.length != candidate.length) return false
        var mismatch = -1
        typed.indices.forEach { index ->
            if (typed[index] != candidate[index]) {
                if (mismatch >= 0) return false
                mismatch = index
            }
        }
        if (mismatch < 0) return false
        return candidate[mismatch] in QWERTY_NEIGHBORS[typed[mismatch]].orEmpty()
    }

    fun isVowelDeletion(typed: String, candidate: String): Boolean {
        if (candidate.length != typed.length + 1) return false
        candidate.indices.forEach { index ->
            if (candidate[index] in LATIN_VOWELS && candidate.removeRange(index, index + 1) == typed) {
                return true
            }
        }
        return false
    }

    fun isAdjacentTransposition(typed: String, candidate: String): Boolean {
        if (typed.length != candidate.length || typed.length < 2) return false
        typed.indices.dropLast(1).forEach { index ->
            if (typed[index] != candidate[index]) {
                val swapped = typed.substring(0, index) + typed[index + 1] + typed[index] + typed.substring(index + 2)
                return swapped == candidate
            }
        }
        return false
    }

    fun isConservativeTypo(typed: String, candidate: String): Boolean {
        val left = typed.lowercase(Locale.ENGLISH)
        val right = candidate.lowercase(Locale.ENGLISH)
        if (left == right) return true
        if (isVowelDeletion(left, right) || isNearbySubstitution(left, right) || isAdjacentTransposition(left, right)) {
            return true
        }
        if (isExtraCharacter(left, right) && left.length >= 5) {
            return true
        }
        if (left.length >= 5 && damerauDistance(left, right) == 1 && isNearbyInsertion(left, right)) {
            return true
        }
        return collapseRepeats(left) == right
    }

    fun isExtraCharacter(typed: String, candidate: String): Boolean {
        val left = typed.lowercase(Locale.ENGLISH)
        val right = candidate.lowercase(Locale.ENGLISH)
        if (left.length != right.length + 1 || right.length < 3) return false
        left.indices.forEach { index ->
            if (left.removeRange(index, index + 1) == right) return true
        }
        return false
    }

    fun extraCandidates(input: String, known: (String) -> Boolean): List<String> {
        val normalized = input.lowercase(Locale.ENGLISH)
        if (normalized.length < 3) return emptyList()
        val results = LinkedHashSet<String>()
        normalized.indices.dropLast(1).forEach { index ->
            val swapped = normalized.substring(0, index) +
                normalized[index + 1] +
                normalized[index] +
                normalized.substring(index + 2)
            if (swapped != normalized && known(swapped)) results += swapped
        }
        if (normalized.length >= 5) {
            normalized.indices.forEach { index ->
                QWERTY_NEIGHBORS[normalized[index]].orEmpty().forEach { neighbor ->
                    val inserted = normalized.substring(0, index) + neighbor + normalized.substring(index)
                    if (known(inserted)) results += inserted
                }
            }
        }
        if (normalized.length >= 4) {
            normalized.indices.forEach { index ->
                val dropped = normalized.removeRange(index, index + 1)
                if (known(dropped)) results += dropped
            }
        }
        COMMON_PHONETIC[normalized]?.forEach { if (known(it)) results += it }
        commonCorrections(normalized, known).forEach { results += it }
        return results.toList()
    }

    fun commonCorrections(input: String, known: (String) -> Boolean = { true }): List<String> {
        val normalized = input.lowercase(Locale.ENGLISH)
        return COMMON_CORRECTIONS[normalized].orEmpty().filter { known(it) || it != normalized }
    }

    fun isMissingLetter(typed: String, candidate: String): Boolean {
        val left = typed.lowercase(Locale.ENGLISH)
        val right = candidate.lowercase(Locale.ENGLISH)
        if (right.length != left.length + 1) return false
        right.indices.forEach { index ->
            if (right.removeRange(index, index + 1) == left) return true
        }
        return false
    }

    fun missingLetterCandidates(input: String, known: (String) -> Boolean, limit: Int = 3): List<String> {
        val normalized = input.lowercase(Locale.ENGLISH)
        if (normalized.length !in 3..12) return emptyList()
        val results = LinkedHashSet<String>()
        normalized.indices.forEach { index ->
            val doubled = normalized.substring(0, index + 1) + normalized[index] + normalized.substring(index + 1)
            if (known(doubled)) results += doubled
        }
        LATIN_VOWELS.forEach { vowel ->
            for (index in 0..normalized.length) {
                val inserted = normalized.substring(0, index) + vowel + normalized.substring(index)
                if (known(inserted)) results += inserted
            }
        }
        return results.take(limit)
    }

    private fun isNearbyInsertion(typed: String, candidate: String): Boolean {
        if (candidate.length != typed.length + 1) return false
        candidate.indices.forEach { index ->
            if (candidate.removeRange(index, index + 1) == typed) {
                val inserted = candidate[index]
                val left = candidate.getOrNull(index - 1)
                val right = candidate.getOrNull(index + 1)
                if (left != null && inserted in QWERTY_NEIGHBORS[left].orEmpty()) return true
                if (right != null && inserted in QWERTY_NEIGHBORS[right].orEmpty()) return true
            }
        }
        return false
    }

    private fun collapseRepeats(value: String): String {
        val output = StringBuilder(value.length)
        value.forEach { character -> if (output.lastOrNull() != character) output.append(character) }
        return output.toString()
    }

    private val COMMON_PHONETIC = mapOf(
        "teh" to listOf("the"),
        "taht" to listOf("that"),
        "adn" to listOf("and"),
        "whihc" to listOf("which"),
        "thier" to listOf("their"),
        "recieve" to listOf("receive"),
        "recieved" to listOf("received"),
        "seperate" to listOf("separate"),
        "occured" to listOf("occurred"),
        "definately" to listOf("definitely"),
        "tommorrow" to listOf("tomorrow"),
        "untill" to listOf("until"),
        "wich" to listOf("which"),
        "becuase" to listOf("because"),
        "nepai" to listOf("nepali"),
        "neplai" to listOf("nepali")
    )

    private val COMMON_CORRECTIONS = mapOf(
        "teh" to listOf("the"),
        "taht" to listOf("that"),
        "adn" to listOf("and"),
        "recieve" to listOf("receive"),
        "becuase" to listOf("because"),
        "definately" to listOf("definitely"),
        "seperate" to listOf("separate"),
        "occured" to listOf("occurred"),
        "nepai" to listOf("nepali"),
        "neplai" to listOf("nepali")
    )
}
