package com.sworupplayz.keyboard

import kotlin.math.abs
import kotlin.math.hypot

enum class InkLanguage {
    ENGLISH,
    DEVANAGARI,
    UNKNOWN
}

data class InkGeometryFeatures(
    val headlineScore: Float,
    val verticalDensity: Float,
    val loopRatio: Float,
    val aspectRatio: Float
)

/**
 * Script guess from stroke geometry only. Never runs OCR or invents letters.
 */
object HandwritingLanguageDetector {
    fun detect(ink: HandwritingInk): InkLanguage {
        val features = features(ink) ?: return InkLanguage.UNKNOWN
        if (features.headlineScore >= 0.32f && features.verticalDensity >= 0.12f) {
            return InkLanguage.DEVANAGARI
        }
        if (features.loopRatio >= 0.22f && features.headlineScore < 0.18f) {
            return InkLanguage.ENGLISH
        }
        return InkLanguage.UNKNOWN
    }

    fun features(ink: HandwritingInk): InkGeometryFeatures? {
        val bounds = StrokeNormalizer.bounds(ink) ?: return null
        val height = bounds.height.coerceAtLeast(0.0001f)
        val width = bounds.width.coerceAtLeast(0.0001f)
        var total = 0f
        var headline = 0f
        var vertical = 0f
        var turns = 0
        var segments = 0
        ink.strokes.forEach { stroke ->
            for (index in 1 until stroke.points.size) {
                val start = stroke.points[index - 1]
                val end = stroke.points[index]
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = hypot(dx, dy)
                if (length <= 0f) continue
                total += length
                segments += 1
                val midY = ((start.y + end.y) / 2f - bounds.top) / height
                if (abs(dx) >= 2f * abs(dy) && midY <= 0.32f) headline += length
                if (abs(dy) >= 2f * abs(dx)) vertical += length
                if (index >= 2) {
                    val previous = stroke.points[index - 2]
                    val pdx = start.x - previous.x
                    val pdy = start.y - previous.y
                    val cross = pdx * dy - pdy * dx
                    val dot = pdx * dx + pdy * dy
                    if (abs(cross) > abs(dot) * 0.35f) turns += 1
                }
            }
        }
        if (total <= 0f) return null
        return InkGeometryFeatures(
            headlineScore = headline / total,
            verticalDensity = vertical / total,
            loopRatio = if (segments == 0) 0f else turns.toFloat() / segments,
            aspectRatio = width / height
        )
    }
}
