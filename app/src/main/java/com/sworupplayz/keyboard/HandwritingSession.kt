package com.sworupplayz.keyboard

/**
 * Dual-language handwriting session. Resets strokes, raster, and temporary
 * candidates. Never clears learned words or clipboard.
 */
class HandwritingSession(
    private val models: HandwritingModelManager = HandwritingModelManager()
) {
    val collector = HandwritingStrokeCollector()
    var lastResult: HandwritingResult = HandwritingResult.EMPTY
        private set
    var lastRaster: FloatArray? = null
        private set

    fun addStroke(points: List<InkPoint>): Boolean {
        lastResult = HandwritingResult.EMPTY
        lastRaster = null
        return collector.addFinished(points)
    }

    fun undo(): Boolean {
        lastResult = HandwritingResult.EMPTY
        lastRaster = null
        return collector.undo()
    }

    fun clear() {
        collector.clear()
        lastResult = HandwritingResult.EMPTY
        lastRaster = null
    }

    fun reset() = clear()

    fun recognize(inputType: Int): HandwritingResult {
        if (!HandwritingPrivacyPolicy.allowsRecognition(inputType)) {
            lastResult = HandwritingResult.BLOCKED
            lastRaster = null
            return lastResult
        }
        if (collector.isEmpty) {
            lastResult = HandwritingResult.EMPTY
            lastRaster = null
            return lastResult
        }
        val normalized = StrokeNormalizer.normalize(collector.snapshot())
        lastRaster = InkRasterizer.rasterizeFramework(normalized).copyOf()
        val language = HandwritingLanguageDetector.detect(normalized)
        lastResult = models.recognize(normalized, language).copy(detectedLanguage = language)
        return lastResult
    }
}
