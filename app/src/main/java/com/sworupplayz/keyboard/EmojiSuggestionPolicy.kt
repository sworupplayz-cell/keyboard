package com.sworupplayz.keyboard

import java.util.Locale

/**
 * High-confidence emoji candidates for the suggestion strip.
 * Kept separate from word ranking so emoji never flood everyday completions.
 */
object EmojiSuggestionPolicy {
    private val EXACT_KEYWORDS = mapOf(
        "heart" to "❤",
        "love" to "❤",
        "happy" to "😊",
        "sad" to "😢",
        "cry" to "😢",
        "laugh" to "😂",
        "lol" to "😂",
        "haha" to "😂",
        "fire" to "🔥",
        "hot" to "🔥",
        "football" to "⚽",
        "soccer" to "⚽",
        "birthday" to "🎂",
        "cake" to "🎂",
        "congratulations" to "🎉",
        "congrats" to "🎉",
        "thanks" to "🙏",
        "thank" to "🙏",
        "sorry" to "😔",
        "party" to "🎉",
        "food" to "🍔",
        "music" to "🎵",
        "sleep" to "😴",
        "school" to "📚"
    )

    fun suggest(word: String, previousWord: String? = null): String? {
        exact(word)?.let { return it }
        if (word.trim().isNotEmpty()) return null
        return exact(previousWord.orEmpty())
    }

    private fun exact(word: String): String? {
        val key = word.trim().lowercase(Locale.ENGLISH)
        if (key.length < 3) return null
        EXACT_KEYWORDS[key]?.let { return it }
        if (key.length < 4) return null
        val hits = EmojiCatalog.search(key, limit = 2)
        if (hits.size != 1) return null
        return hits.first()
    }
}
