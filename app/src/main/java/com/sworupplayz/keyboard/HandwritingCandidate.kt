package com.sworupplayz.keyboard

data class HandwritingCandidate(
    val text: String,
    val confidence: Float,
    val language: InkLanguage
)

object HandwritingCandidateMerger {
    const val INTERNAL_LIMIT = 5
    const val SUGGESTION_LIMIT = 3

    fun merge(candidates: List<HandwritingCandidate>): List<HandwritingCandidate> {
        val seen = LinkedHashSet<String>()
        val kept = ArrayList<HandwritingCandidate>()
        candidates.sortedByDescending { it.confidence }.forEach { candidate ->
            val text = HandwritingUnicode.normalize(candidate.text)
            if (text.isEmpty()) return@forEach
            if (candidate.confidence < HandwritingModelSpec.MIN_CONFIDENCE) return@forEach
            if (!seen.add(text)) return@forEach
            kept += candidate.copy(text = text)
            if (kept.size == INTERNAL_LIMIT) return kept
        }
        return kept
    }

    fun forSuggestionBar(candidates: List<HandwritingCandidate>): List<String> =
        merge(candidates).take(SUGGESTION_LIMIT).map { it.text }
}
