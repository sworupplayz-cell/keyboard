package com.sworupplayz.keyboard

/**
 * Privacy, lifecycle, and suggestion-bridge rules for handwriting.
 * This is not a second prediction engine.
 */
object HandwritingPrivacyPolicy {
    fun allowsRecognition(inputType: Int): Boolean =
        !EditorFieldPolicy.isSensitiveField(inputType) &&
            !EditorFieldPolicy.isPasswordField(inputType)

    fun persistStrokes(): Boolean = false

    fun logsStrokes(): Boolean = false

    fun allowsNetwork(): Boolean = false

    fun shouldLearnCommitted(inputType: Int, text: String): Boolean =
        EditorFieldPolicy.shouldLearn(inputType) &&
            WordLearningPolicy.shouldLearnAccepted(text)
}

object HandwritingLifecyclePolicy {
    fun shouldCancelOnHide(): Boolean = true

    fun shouldCancelOnFieldChange(): Boolean = true

    fun shouldCancelOnPanelClose(): Boolean = true

    fun shouldCancelOnNewStroke(): Boolean = true

    fun shouldIgnoreStale(generation: Int, current: Int): Boolean = generation != current

    fun shouldRecognizeOnEveryMove(): Boolean = false
}

object HandwritingSuggestionBridge {
    fun suggestionLanguage(language: KeyboardLanguage): SuggestionLanguage = when (language) {
        KeyboardLanguage.ENGLISH -> SuggestionLanguage.ENGLISH
        KeyboardLanguage.NEPALI -> SuggestionLanguage.NEPALI
        KeyboardLanguage.ROMAN -> SuggestionLanguage.ROMAN
    }

    fun queryForCommitted(text: String, language: KeyboardLanguage): SuggestionQuery =
        SuggestionQuery(
            input = text,
            language = suggestionLanguage(language),
            includeEmoji = language != KeyboardLanguage.NEPALI,
            includeTypos = false
        )

    fun shouldAutoCommit(candidates: List<RecognitionCandidate>): Boolean = false
}

object HandwritingUiState {
    fun showsLanguage(language: KeyboardLanguage): String = when (language) {
        KeyboardLanguage.ENGLISH -> "English handwriting"
        KeyboardLanguage.NEPALI -> "Nepali handwriting"
        KeyboardLanguage.ROMAN -> "Roman handwriting"
    }

    fun statusMessage(status: HandwritingStatus): String = when (status) {
        HandwritingStatus.EMPTY -> "Write, then tap Confirm"
        HandwritingStatus.READY -> "Tap Confirm to check the writing"
        HandwritingStatus.RECOGNIZING -> "Recognizing handwriting"
        HandwritingStatus.RESULTS -> "Choose a recognition candidate"
        HandwritingStatus.NO_MATCH -> "No match. Clear and try again."
        HandwritingStatus.RECOGNIZER_UNAVAILABLE -> "Offline recognizer is not bundled"
        HandwritingStatus.BLOCKED -> "Handwriting recognition is off in this field"
    }
}
