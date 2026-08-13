package com.sworupplayz.keyboard

/**
 * Suggestion-bar UX helpers. They do not rank candidates and are not a
 * second prediction engine.
 */
object SuggestionBarStyle {
    fun textSizeSp(primary: Boolean, text: String): Float = when {
        primary && text.length <= 10 -> KeyboardTheme.SUGGESTION_TEXT_SP + 1f
        text.length > 10 -> 13f
        else -> KeyboardTheme.SUGGESTION_TEXT_SP
    }

    fun showsEmptyHint(suggestions: List<String>): Boolean = suggestions.isEmpty()

    fun hasStableSlots(suggestions: List<String>): Boolean =
        SuggestionBarState.gboardSlots(suggestions).size == 3
}

/** Skip identical suggestion work when the editor context has not changed. */
class SuggestionQueryCache {
    private var key: String = ""
    private var words: List<String> = emptyList()

    fun fingerprint(
        language: SuggestionLanguage,
        input: String,
        previous: String?,
        previousTwo: String?,
        includeTypos: Boolean,
        includeEmoji: Boolean,
        previousThree: String? = null
    ): String = listOf(
        language.name,
        input,
        previous.orEmpty(),
        previousTwo.orEmpty(),
        previousThree.orEmpty(),
        if (includeTypos) "t" else "-",
        if (includeEmoji) "e" else "-"
    ).joinToString("\u0001")

    fun hit(fingerprint: String): List<String>? = words.takeIf { fingerprint == key }

    fun remember(fingerprint: String, suggestions: List<String>) {
        key = fingerprint
        words = suggestions
    }

    fun invalidate() {
        key = ""
        words = emptyList()
    }
}

/**
 * Autocorrect is suggestion-only. Unknown names, slang, and protected tokens
 * stay typed unless the user taps a candidate.
 */
object AutocorrectUxPolicy {
    fun shouldPreserveTyped(typed: String): Boolean {
        val clean = typed.trim()
        if (clean.isEmpty()) return true
        if (SpecialTokenPolicy.looksLikeUrl(clean) || SpecialTokenPolicy.looksLikeEmail(clean)) return true
        if (SpecialTokenPolicy.looksLikeMentionOrHashtag(clean)) return true
        if (ClipboardPolicy.looksSensitive(clean)) return true
        if (ChatAbbreviation.isKnown(clean)) return true
        if (CapitalizationPolicy.isProperNoun(clean)) return true
        return VocabularyCatalog.classify(clean, VocabularyLanguage.ENGLISH) == VocabularyCategory.SLANG
    }

    fun shouldOfferCorrection(typed: String, candidate: String): Boolean {
        if (typed.equals(candidate, ignoreCase = true)) return false
        if (shouldPreserveTyped(typed) && !CorrectionPolicy.shouldOfferTypo(typed, candidate)) return false
        if (shouldPreserveTyped(typed) && CapitalizationPolicy.isProperNoun(typed)) return false
        return CorrectionPolicy.shouldOfferTypo(typed, candidate)
    }

    fun neverAutoReplaces(): Boolean = !CorrectionPolicy.shouldAutoReplace("helo", "hello")
}

/** Cursor-safe acceptance helpers used by tests and KeyboardService. */
object SuggestionAcceptanceUx {
    fun apply(fieldBefore: String, fieldAfter: String, typed: String, suggestion: String): String? {
        val plan = SuggestionSelectionPlan.create(typed, suggestion, fieldBefore, fieldAfter) ?: return null
        val keptAfter = if (fieldAfter.length >= plan.deleteAfterCodeUnits) {
            fieldAfter.drop(plan.deleteAfterCodeUnits)
        } else {
            ""
        }
        return fieldBefore.substring(0, fieldBefore.length - plan.deleteCodeUnits) + plan.replacement + keptAfter
    }

    fun preservesTrailing(fieldAfter: String, deleteAfter: Int): Boolean =
        fieldAfter.drop(deleteAfter).isNotEmpty() || fieldAfter.isEmpty()
}
