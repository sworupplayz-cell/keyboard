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
        shouldAccept(previous) && shouldAccept(next)
}
