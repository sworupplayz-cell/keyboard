package com.sworupplayz.keyboard

/**
 * Cancellable recognition session. KeyboardService stays the orchestrator.
 * A second finger, a new stroke, hide, or field change bumps generation so a
 * stale result cannot be committed.
 */
class HandwritingJobController {
    private var generation = 0

    fun current(): Int = generation

    fun bump(): Int {
        generation += 1
        return generation
    }

    fun isCurrent(seen: Int): Boolean = seen == generation

    fun cancel(): Int = bump()
}

object HandwritingCandidatePolicy {
    fun limit(requested: Int = HandwritingModelSpec.MAX_CANDIDATES): Int =
        requested.coerceIn(1, HandwritingModelSpec.MAX_CANDIDATES)

    fun keep(candidates: List<RecognitionCandidate>, language: KeyboardLanguage): List<RecognitionCandidate> {
        val seen = LinkedHashSet<String>()
        val kept = ArrayList<RecognitionCandidate>()
        candidates.forEach { candidate ->
            val text = HandwritingUnicode.normalize(candidate.text)
            if (text.isEmpty()) return@forEach
            if (candidate.confidence < HandwritingModelSpec.MIN_CONFIDENCE) return@forEach
            if (!HandwritingUnicode.allowedForLanguage(text, language)) return@forEach
            if (!seen.add(text)) return@forEach
            kept += candidate.copy(text = text)
            if (kept.size == limit()) return kept
        }
        return kept
    }
}

/**
 * Runs preprocessing, then the model boundary. Without a bundled model this
 * always reports unavailable. It never invents Devanagari from stroke counts.
 */
class OfflineHandwritingRecognizer(
    private val loader: HandwritingModelLoader,
    private val fallback: NepaliHandwritingRecognizer = UnavailableNepaliHandwritingRecognizer
) : NepaliHandwritingRecognizer {
    override val isAvailable: Boolean = loader.isAvailable && fallback.isAvailable

    override fun recognize(ink: HandwritingInk): List<String> {
        val prepared = HandwritingPreprocessor.prepare(ink)
        if (prepared.empty) return emptyList()
        val handle = loader.load(KeyboardLanguage.NEPALI)
        val inferred = BundledHandwritingInterpreter.infer(handle, prepared)
        if (inferred.candidates.isNotEmpty()) {
            return inferred.candidates.map { it.text }
        }
        if (!fallback.isAvailable) return emptyList()
        return fallback.recognize(prepared.ink)
    }

    fun recognizePrepared(
        ink: HandwritingInk,
        language: KeyboardLanguage,
        generation: Int
    ): RecognitionResult {
        val prepared = HandwritingPreprocessor.prepare(ink)
        if (prepared.empty) {
            return RecognitionResult(emptyList(), HandwritingStatus.NO_MATCH, generation)
        }
        val handle = loader.load(language)
        if (handle == null && !fallback.isAvailable) {
            return RecognitionResult(
                emptyList(),
                HandwritingStatus.RECOGNIZER_UNAVAILABLE,
                generation
            )
        }
        val inferred = BundledHandwritingInterpreter.infer(handle, prepared)
        val ranked = HandwritingCandidatePolicy.keep(inferred.candidates, language)
        if (ranked.isNotEmpty()) {
            return RecognitionResult(ranked, HandwritingStatus.RESULTS, generation)
        }
        if (fallback.isAvailable) {
            val raw = fallback.recognize(prepared.ink).map { RecognitionCandidate(it, 1f) }
            val kept = HandwritingCandidatePolicy.keep(raw, language)
            val status = if (kept.isEmpty()) HandwritingStatus.NO_MATCH else HandwritingStatus.RESULTS
            return RecognitionResult(kept, status, generation)
        }
        return RecognitionResult(emptyList(), HandwritingStatus.RECOGNIZER_UNAVAILABLE, generation)
    }
}
