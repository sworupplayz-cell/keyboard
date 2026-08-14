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

    fun record(previous: String, next: String, previousTwo: String?): Boolean {
        val first = record(previous, next)
        val second = if (!previousTwo.isNullOrBlank()) record(previousTwo, next) else false
        return first || second
    }

    fun strength(previous: String, next: String): Int {
        val key = normalize(previous) to normalize(next)
        return counts[key] ?: 0
    }

    fun size(): Int = counts.size

    fun predictions(
        previous: String,
        prefix: String = "",
        limit: Int = 3,
        previousTwo: String? = null,
        previousThree: String? = null
    ): List<String> {
        if (limit <= 0) return emptyList()
        val normalizedPrefix = prefix.trim().lowercase(Locale.ENGLISH)
        val results = LinkedHashSet<String>()
        fun consider(key: String) {
            val prev = normalize(key)
            if (prev.isEmpty()) return
            counts.entries
                .filter { it.key.first == prev && it.key.second.startsWith(normalizedPrefix) }
                .sortedByDescending { it.value }
                .forEach { results += it.key.second }
            seed[prev].orEmpty()
                .filter { it.startsWith(normalizedPrefix) || normalizedPrefix.isEmpty() }
                .forEach { results += it }
        }
        consider(previousThree.orEmpty())
        consider(previousTwo.orEmpty())
        consider(previous)
        return results.take(limit)
    }

    fun serialize(): String = counts.entries.joinToString("\n") { (pair, score) ->
        "${pair.first}\t${pair.second}\t$score"
    }

    private fun trimToLimit() {
        while (counts.size > limit) counts.remove(counts.keys.first())
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
            "good morning" to listOf("everyone", "guys"),
            "thank" to listOf("you"),
            "thanks" to listOf("for", "you"),
            "thank you" to listOf("for", "very"),
            "how" to listOf("are", "to"),
            "how are" to listOf("you"),
            "how are you" to listOf("doing"),
            "i" to listOf("am", "have", "will", "want", "can"),
            "i am" to listOf("going", "here", "fine"),
            "i am going" to listOf("to", "home"),
            "we" to listOf("are", "have", "will"),
            "going" to listOf("to"),
            "want" to listOf("to"),
            "and" to listOf("the", "i"),
            "of" to listOf("the"),
            "in" to listOf("the"),
            "to" to listOf("the", "be", "do", "go"),
            "see" to listOf("you"),
            "let" to listOf("me"),
            "see you" to listOf("soon", "tomorrow", "later")
        )

        val NEPALI_SEED = mapOf(
            "म" to listOf("लाई", "पनि", "जान्छु", "घर", "स्कुल", "कलेज"),
            "मलाई" to listOf("मन", "नेपाली", "थाहा"),
            "मलाई मन" to listOf("पर्छ"),
            "तिमीलाई" to listOf("कस्तो छ"),
            "मेरो" to listOf("घर", "नाम"),
            "तिमी" to listOf("लाई", "कहाँ", "कस्तो", "कहिले"),
            "तिमी कहाँ" to listOf("छौ", "जान्छौ"),
            "म घर" to listOf("जान्छु"),
            "म स्कुल" to listOf("जान्छु"),
            "के" to listOf("छ", "गर्छौ"),
            "आज" to listOf("काम"),
            "घर" to listOf("मा", "को", "बाट"),
            "जान" to listOf("जान्छु", "जान्छ", "जानु")
        )

        val ROMAN_SEED = mapOf(
            "ma" to listOf("ghar", "jaanchu", "school", "college"),
            "ma school" to listOf("jaanchu", "gaye", "janchu"),
            "ma ghar" to listOf("jaanchu", "janchu"),
            "ma college" to listOf("jaanchu", "janchu"),
            "aaja school" to listOf("jaanu", "parcha"),
            "malai game" to listOf("khelna", "manparcha"),
            "i am going" to listOf("ghar"),
            "ma kathmandu" to listOf("jaanchu", "janchu"),
            "mero" to listOf("ghar", "naam", "phone"),
            "timi" to listOf("kaha", "lai", "kasto"),
            "malai" to listOf("man", "manparcha"),
            "ke" to listOf("cha", "garchau"),
            "aaja" to listOf("kaam"),
            "ghar" to listOf("jaanchu"),
            "i am" to listOf("fine", "going", "ghar"),
            "nepali" to listOf("manparcha")
        )

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun isValid(value: String): Boolean =
            value.isNotEmpty() && '\n' !in value && '\t' !in value && value.length <= 48
    }
}
