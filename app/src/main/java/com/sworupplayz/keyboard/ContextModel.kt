package com.sworupplayz.keyboard

import java.util.Locale

/**
 * Bounded local bigram model. Built-in pairs give immediate context, and the user
 * can teach more pairs by typing. Storage cannot grow past [limit] pairs.
 */
class ContextModel(
    initial: Map<Pair<String, String>, Int> = emptyMap(),
    private val seed: Map<String, List<String>> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val counts = linkedMapOf<Pair<String, String>, Int>()

    init {
        initial.forEach { (pair, score) ->
            if (isValid(pair.first) && isValid(pair.second)) {
                counts[normalize(pair.first) to normalize(pair.second)] = score.coerceAtLeast(1)
            }
        }
        trimToLimit()
    }

    fun record(previous: String, next: String): Boolean {
        val key = normalize(previous) to normalize(next)
        if (!isValid(key.first) || !isValid(key.second)) return false
        counts[key] = (counts.remove(key) ?: 0) + 1
        trimToLimit()
        return true
    }

    fun predictions(previous: String, prefix: String = "", limit: Int = 3): List<String> {
        val prev = normalize(previous)
        if (prev.isEmpty() || limit <= 0) return emptyList()
        val normalizedPrefix = prefix.trim().lowercase(Locale.ENGLISH)
        val learned = counts.entries
            .filter { it.key.first == prev && it.key.second.startsWith(normalizedPrefix) }
            .sortedByDescending { it.value }
            .map { it.key.second }
        val seeded = seed[prev].orEmpty().filter { it.startsWith(normalizedPrefix) }
        return (learned + seeded).distinct().take(limit)
    }

    fun serialize(): String = counts.entries.joinToString("\n") { (pair, score) ->
        "${pair.first}\t${pair.second}\t$score"
    }

    companion object {
        const val DEFAULT_LIMIT = 400

        fun fromSerialized(
            value: String?,
            seed: Map<String, List<String>> = emptyMap(),
            limit: Int = DEFAULT_LIMIT
        ): ContextModel {
            val entries = linkedMapOf<Pair<String, String>, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    entries[parts[0] to parts[1]] = parts[2].toIntOrNull() ?: 1
                }
            }
            return ContextModel(entries, seed, limit)
        }

        val ENGLISH_SEED = mapOf(
            "the" to listOf("first", "same", "other", "world", "time"),
            "good" to listOf("morning", "night", "luck", "job"),
            "thank" to listOf("you"),
            "thanks" to listOf("for"),
            "how" to listOf("are", "to"),
            "i" to listOf("am", "have", "will", "can"),
            "we" to listOf("are", "have", "will"),
            "going" to listOf("to"),
            "want" to listOf("to"),
            "and" to listOf("the", "i"),
            "of" to listOf("the"),
            "in" to listOf("the"),
            "to" to listOf("the", "be", "do", "go"),
            "see" to listOf("you"),
            "let" to listOf("me")
        )

        val NEPALI_SEED = mapOf(
            "म" to listOf("लाई", "पनि"),
            "मेरो" to listOf("घर", "नाम"),
            "तिमी" to listOf("लाई"),
            "के" to listOf("छ", "गर्छौ"),
            "आज" to listOf("काम"),
            "घर" to listOf("जान्छु")
        )

        val ROMAN_SEED = mapOf(
            "ma" to listOf("pani", "ni"),
            "mero" to listOf("ghar", "naam"),
            "timi" to listOf("lai"),
            "ke" to listOf("cha", "garchau"),
            "aaja" to listOf("kaam"),
            "ghar" to listOf("jaanchu")
        )

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun isValid(value: String): Boolean =
            value.isNotEmpty() && '\n' !in value && '\t' !in value && value.length <= 48
    }
}
