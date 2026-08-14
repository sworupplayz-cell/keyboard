package com.sworupplayz.keyboard

/**
 * Stroke preprocessing for a future bundled offline recognizer.
 * This is not a recognizer: it never maps stroke counts to letters.
 */
data class PreparedHandwriting(
    val ink: HandwritingInk,
    val raster: FloatArray,
    val rasterWidth: Int,
    val rasterHeight: Int,
    val empty: Boolean
)

object HandwritingPreprocessor {
    const val TARGET_POINTS = 32
    const val RASTER_SIZE = 32
    const val MIN_PATH_LENGTH = 0.02f
    const val BOX_PADDING = 0.08f

    fun prepare(ink: HandwritingInk): PreparedHandwriting {
        val cleaned = StrokeNormalizer.normalize(ink)
        val raster = InkRasterizer.rasterize(cleaned, RASTER_SIZE, RASTER_SIZE)
        return PreparedHandwriting(
            ink = cleaned,
            raster = raster,
            rasterWidth = RASTER_SIZE,
            rasterHeight = RASTER_SIZE,
            empty = cleaned.strokes.isEmpty()
        )
    }
}

data class InkBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

object HandwritingUnicode {
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFC)

    fun looksDevanagari(text: String): Boolean =
        text.any { it.code in 0x0900..0x097F }

    fun looksLatin(text: String): Boolean =
        text.any { it in 'A'..'Z' || it in 'a'..'z' }

    fun allowedForLanguage(text: String, language: KeyboardLanguage): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return false
        return when (language) {
            KeyboardLanguage.NEPALI ->
                clean.all { it.isWhitespace() || it.code in 0x0900..0x097F }
            KeyboardLanguage.ENGLISH ->
                clean.all { it.isWhitespace() || it.code < 128 }
            KeyboardLanguage.ROMAN ->
                clean.all {
                    it.isWhitespace() || it.code < 128 || it.code in 0x0900..0x097F
                }
        }
    }
}
