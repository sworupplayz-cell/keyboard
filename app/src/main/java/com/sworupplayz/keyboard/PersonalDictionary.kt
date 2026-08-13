package com.sworupplayz.keyboard

/**
 * Bounded on-device personal vocabulary. Learned words boost ranking but never
 * auto-replace typed text and never override built-in dictionaries entirely.
 */
object PersonalDictionary {
    const val MAX_WORDS = LearnedWordStore.DEFAULT_LIMIT
    const val MAX_RECENT = RecentWordStore.DEFAULT_LIMIT
    const val MAX_CONTEXT_PAIRS = ContextModel.DEFAULT_LIMIT
    const val MAX_PHRASES = PhrasePredictor.DEFAULT_LIMIT

    fun shouldAccept(word: String): Boolean =
        WordLearningPolicy.shouldLearnAccepted(word) && !ClipboardPolicy.looksSensitive(word)

    fun shouldLearnUnknown(word: String, known: (String) -> Boolean): Boolean =
        shouldAccept(word) && WordLearningPolicy.shouldLearnUnknown(word, known)

    fun shouldLearnRepeated(word: String, finishCount: Int, known: (String) -> Boolean): Boolean =
        shouldAccept(word) && WordLearningPolicy.shouldLearnRepeated(word, finishCount, known)

    fun rankingBoost(learnedRank: Int): Int = (8_000 - learnedRank * 20).coerceAtLeast(4_000)

    fun shouldLearnPhrase(previous: String, next: String): Boolean =
        isLearnablePhraseToken(previous) && isLearnablePhraseToken(next)

    fun isLearnablePhraseToken(word: String): Boolean {
        val clean = word.trim()
        if (clean.isEmpty() || '\n' in clean || '\t' in clean || clean.length > 48) return false
        if (ClipboardPolicy.looksSensitive(clean)) return false
        if (SpecialTokenPolicy.looksLikeUrl(clean) || SpecialTokenPolicy.looksLikeEmail(clean)) return false
        if (SpecialTokenPolicy.looksLikeMentionOrHashtag(clean) || SpecialTokenPolicy.looksLikeNumber(clean)) {
            return false
        }
        if (' ' in clean) return clean.split(' ').all { isLearnablePhraseToken(it) }
        if (shouldAccept(clean)) return true
        if (clean.length in 1..2 &&
            clean.any { it.isLetter() || it.code in 0x0900..0x097F } &&
            !clean.all { it == clean.first() }
        ) {
            return true
        }
        return false
    }
}
