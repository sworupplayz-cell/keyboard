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
    }

    fun suggestionTexts(): List<String> = HandwritingCandidateMerger.forSuggestionBar(candidates)
}
