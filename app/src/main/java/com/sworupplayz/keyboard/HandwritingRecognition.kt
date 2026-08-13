package com.sworupplayz.keyboard

data class InkPoint(
    val x: Float,
    val y: Float,
    val timeMillis: Long = 0L,
    val pressure: Float = 1f
)
data class InkStroke(val points: List<InkPoint>)
data class HandwritingInk(val strokes: List<InkStroke>)

enum class HandwritingStatus {
    EMPTY,
    READY,
    RECOGNIZING,
    RESULTS,
    NO_MATCH,
    RECOGNIZER_UNAVAILABLE,
    BLOCKED
}

data class HandwritingRecognition(
    val candidates: List<String>,
    val status: HandwritingStatus
)

/** Contract for a future small, bundled, offline Nepali handwriting recognizer. */
interface NepaliHandwritingRecognizer {
    val isAvailable: Boolean
    fun recognize(ink: HandwritingInk): List<String>
}

/**
 * Android does not provide a public Nepali handwriting recognizer. Keeping this
 * implementation dependency-free avoids a runtime model download or a large APK.
 */
object UnavailableNepaliHandwritingRecognizer : NepaliHandwritingRecognizer {
    override val isAvailable: Boolean = false
    override fun recognize(ink: HandwritingInk): List<String> = emptyList()
}

/** Lightweight handwriting state shared by the IME UI and unit tests. */
class HandwritingInputState(
    private val recognizer: NepaliHandwritingRecognizer
) {
    private val strokes = ArrayList<InkStroke>()
    var candidates: List<String> = emptyList()
        private set
    var status: HandwritingStatus = HandwritingStatus.EMPTY
        private set

    val strokeCount: Int get() = strokes.size

    fun snapshot(): HandwritingInk = HandwritingInk(strokes.map { InkStroke(it.points.toList()) })

    fun addStroke(points: List<InkPoint>): Boolean {
        if (points.size < 2) return false
        strokes += InkStroke(points.toList())
        candidates = emptyList()
        status = HandwritingStatus.READY
        return true
    }

    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        candidates = emptyList()
        status = if (strokes.isEmpty()) HandwritingStatus.EMPTY else HandwritingStatus.READY
        return true
    }

    fun clear() {
        strokes.clear()
        candidates = emptyList()
        status = HandwritingStatus.EMPTY
    }

    fun cancel() = clear()

    fun recognize(language: KeyboardLanguage = KeyboardLanguage.NEPALI): HandwritingRecognition {
        if (strokes.isEmpty()) {
            status = HandwritingStatus.EMPTY
            candidates = emptyList()
            return HandwritingRecognition(candidates, status)
        }
        if (!recognizer.isAvailable) {
            status = HandwritingStatus.RECOGNIZER_UNAVAILABLE
            candidates = emptyList()
            return HandwritingRecognition(candidates, status)
        }

        val prepared = HandwritingPreprocessor.prepare(snapshot())
        if (prepared.empty) {
            status = HandwritingStatus.NO_MATCH
            candidates = emptyList()
            return HandwritingRecognition(candidates, status)
        }
        candidates = recognizer.recognize(prepared.ink)
            .asSequence()
            .map(HandwritingUnicode::normalize)
            .filter { candidate -> HandwritingUnicode.allowedForLanguage(candidate, language) }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
        status = if (candidates.isEmpty()) HandwritingStatus.NO_MATCH else HandwritingStatus.RESULTS
        return HandwritingRecognition(candidates, status)
    }

    fun blockSensitiveField() {
        candidates = emptyList()
        status = HandwritingStatus.BLOCKED
    }

    fun confirm(candidateIndex: Int = 0): String? {
        val result = candidates.getOrNull(candidateIndex) ?: return null
        clear()
        return result
    }

    companion object {
        private const val MAX_CANDIDATES = 3
    }
}
