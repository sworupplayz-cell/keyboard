package com.sworupplayz.keyboard

import java.util.Locale

/**
 * Bounded offline phrase / next-word model.
 * Stores a small seed of useful pairs and triples plus a limited number of
 * locally learned continuations. This is not a language model.
 */
class PhrasePredictor(
    initial: Map<Pair<String, String>, Int> = emptyMap(),
    private val seed: Map<String, List<String>> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val counts = linkedMapOf<Pair<String, String>, Int>()

    init {
        initial.forEach { (pair, score) ->
            if (isValid(pair.first) && isValid(pair.second)) {
                counts[normalize(pair.first) to pair.second.trim()] = score.coerceAtLeast(1)
            }
        }
        trimToLimit()
    }

    fun record(previous: String, next: String): Boolean {
        val key = normalize(previous) to next.trim()
        if (!isValid(key.first) || !isValid(key.second)) return false
        counts[key] = (counts.remove(key) ?: 0) + 1
        trimToLimit()
        return true
    }

    fun predict(
        previous: String?,
        previousTwo: String? = null,
        prefix: String = "",
        limit: Int = 3,
        previousThree: String? = null
    ): List<String> {
        if (limit <= 0) return emptyList()
        val normalizedPrefix = prefix.trim().lowercase(Locale.ENGLISH)
        val results = LinkedHashSet<String>()
        fun consider(key: String?) {
            val normalized = normalize(key.orEmpty())
            if (normalized.isEmpty()) return
            counts.entries
                .filter { it.key.first == normalized && matchesPrefix(it.key.second, normalizedPrefix) }
                .sortedByDescending { it.value }
                .forEach { results += it.key.second }
            seed[normalized].orEmpty()
                .filter { matchesPrefix(it, normalizedPrefix) }
                .forEach { results += it }
        }
        consider(previousThree)
        consider(previousTwo)
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
        const val DEFAULT_LIMIT = 240

        fun fromSerialized(
            value: String?,
            seed: Map<String, List<String>> = emptyMap(),
            limit: Int = DEFAULT_LIMIT
        ): PhrasePredictor {
            val entries = linkedMapOf<Pair<String, String>, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    entries[parts[0] to parts[1]] = parts[2].toIntOrNull() ?: 1
                }
            }
            return PhrasePredictor(entries, seed, limit)
        }

        val ENGLISH_PHRASES = mapOf(
            "good" to listOf("morning", "night", "luck", "job"),
            "thank" to listOf("you"),
            "thanks" to listOf("for", "you"),
            "how" to listOf("are", "to"),
            "how are" to listOf("you"),
            "how are you" to listOf("doing"),
            "i" to listOf("am", "have", "will", "can"),
            "i am" to listOf("fine", "going", "घर"),
            "see" to listOf("you"),
            "let" to listOf("me"),
            "going" to listOf("to"),
            "want" to listOf("to"),
            "nice" to listOf("to", "day")
        )

        val NEPALI_PHRASES = mapOf(
            "मलाई" to listOf("मन", "नेपाली", "मन पर्छ", "थाहा"),
            "तिमीलाई" to listOf("कस्तो छ", "मन पर्छ"),
            "तपाईंलाई" to listOf("कस्तो छ"),
            "म" to listOf("जान्छु", "घर"),
            "मेरो" to listOf("घर", "नाम"),
            "तिमी" to listOf("कहाँ", "कस्तो", "लाई"),
            "घर" to listOf("जान्छु", "घरमा"),
            "के" to listOf("छ", "गर्छौ")
        )

        val ROMAN_PHRASES = mapOf(
            "ma" to listOf("घर", "जान्छु", "school"),
            "म" to listOf("घर", "जान्छु", "school"),
            "ma school" to listOf("jaanchu", "जान्छु"),
            "म school" to listOf("jaanchu", "जान्छु"),
            "i am" to listOf("fine", "going", "घर"),
            "malai" to listOf("मन पर्छ"),
            "timi" to listOf("kaha", "kasto", "lai"),
            "timilai" to listOf("कस्तो छ"),
            "ghar" to listOf("jaanchu", "जान्छु"),
            "mero" to listOf("ghar", "घर")
        )

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun isValid(value: String): Boolean =
            value.isNotEmpty() && '\n' !in value && '\t' !in value && value.length <= 48

        private fun matchesPrefix(candidate: String, prefix: String): Boolean {
            if (prefix.isEmpty()) return true
            val normalized = candidate.trim().lowercase(Locale.ENGLISH)
            if (normalized.startsWith(prefix)) return true
            return candidate.any { it.code in 0x0900..0x097F } && prefix.all { it in 'a'..'z' }
        }
    }
}

object MixedSuggestionPolicy {
    fun nextWords(
        language: SuggestionLanguage,
        previous: String?,
        previousTwo: String?
    ): List<String> {
        val predictor = when (language) {
            SuggestionLanguage.ENGLISH -> PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES)
            SuggestionLanguage.NEPALI -> PhrasePredictor(seed = PhrasePredictor.NEPALI_PHRASES)
            SuggestionLanguage.ROMAN -> PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES)
        }
        return predictor.predict(previous, previousTwo, prefix = "", limit = 3)
    }
}
