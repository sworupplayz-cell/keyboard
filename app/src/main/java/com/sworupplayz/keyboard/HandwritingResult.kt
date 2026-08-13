package com.sworupplayz.keyboard

data class HandwritingResult(
    val status: HandwritingStatus,
    val candidates: List<HandwritingCandidate> = emptyList(),
    val detectedLanguage: InkLanguage = InkLanguage.UNKNOWN
) {
    companion object {
        val UNAVAILABLE = HandwritingResult(HandwritingStatus.RECOGNIZER_UNAVAILABLE)
        val BLOCKED = HandwritingResult(HandwritingStatus.BLOCKED)
        val EMPTY = HandwritingResult(HandwritingStatus.EMPTY)
        val RECOGNIZING = HandwritingResult(HandwritingStatus.RECOGNIZING)

        fun Success(
            candidates: List<HandwritingCandidate>,
            language: InkLanguage = InkLanguage.ENGLISH
        ): HandwritingResult = HandwritingResult(
            status = if (candidates.isEmpty()) HandwritingStatus.NO_MATCH else HandwritingStatus.RESULTS,
            candidates = candidates,
            detectedLanguage = language
        )
    }

    fun suggestionTexts(): List<String> = HandwritingCandidateMerger.forSuggestionBar(candidates)
}
