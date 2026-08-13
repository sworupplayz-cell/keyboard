package com.sworupplayz.keyboard

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

object StrokeNormalizer {
    fun pathLength(stroke: InkStroke): Float {
        if (stroke.points.size < 2) return 0f
        var length = 0f
        for (index in 1 until stroke.points.size) {
            val previous = stroke.points[index - 1]
            val current = stroke.points[index]
            length += hypot(current.x - previous.x, current.y - previous.y)
        }
        return length
    }

    fun filterNoise(
        ink: HandwritingInk,
        minPoints: Int = 2,
        minLength: Float = HandwritingPreprocessor.MIN_PATH_LENGTH
    ): HandwritingInk {
        val kept = ink.strokes.mapNotNull { stroke ->
            val points = stroke.points.filter { point ->
                point.x.isFinite() && point.y.isFinite()
            }
            if (points.size < minPoints) return@mapNotNull null
            val cleaned = InkStroke(points)
            if (pathLength(cleaned) < minLength) null else cleaned
        }
        return HandwritingInk(kept)
    }

    fun resample(stroke: InkStroke, targetPoints: Int = HandwritingPreprocessor.TARGET_POINTS): InkStroke {
        val count = targetPoints.coerceAtLeast(2)
        if (stroke.points.size < 2) return stroke
        val total = pathLength(stroke)
        if (total <= 0f) {
            return InkStroke(List(count) { stroke.points.first() })
        }
        val output = ArrayList<InkPoint>(count)
        output += stroke.points.first()
        for (step in 1 until count - 1) {
            output += pointAt(stroke, total * step / (count - 1).toFloat())
        }
        output += stroke.points.last()
        return InkStroke(output)
    }

    fun translateToOrigin(ink: HandwritingInk): HandwritingInk {
        val bounds = bounds(ink) ?: return ink
        return HandwritingInk(
            ink.strokes.map { stroke ->
                InkStroke(stroke.points.map { point ->
                    InkPoint(point.x - bounds.left, point.y - bounds.top, point.timeMillis)
                })
            }
        )
    }

    fun scaleToUnitBox(
        ink: HandwritingInk,
        padding: Float = HandwritingPreprocessor.BOX_PADDING
    ): HandwritingInk {
        val bounds = bounds(ink) ?: return ink
        val width = max(bounds.width, 0.0001f)
        val height = max(bounds.height, 0.0001f)
        val scale = (1f - 2f * padding) / max(width, height)
        val offsetX = padding + ((1f - 2f * padding) - width * scale) / 2f
        val offsetY = padding + ((1f - 2f * padding) - height * scale) / 2f
        return HandwritingInk(
            ink.strokes.map { stroke ->
                InkStroke(stroke.points.map { point ->
                    InkPoint(
                        x = (point.x - bounds.left) * scale + offsetX,
                        y = (point.y - bounds.top) * scale + offsetY,
                        timeMillis = point.timeMillis
                    )
                })
            }
        )
    }

    fun normalize(ink: HandwritingInk): HandwritingInk {
        val filtered = filterNoise(ink)
        if (filtered.strokes.isEmpty()) return filtered
        val resampled = HandwritingInk(filtered.strokes.map { resample(it) })
        return scaleToUnitBox(translateToOrigin(resampled))
    }

    fun bounds(ink: HandwritingInk): InkBounds? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var found = false
        ink.strokes.forEach { stroke ->
            stroke.points.forEach { point ->
                found = true
                left = min(left, point.x)
                top = min(top, point.y)
                right = max(right, point.x)
                bottom = max(bottom, point.y)
            }
        }
        if (!found) return null
        return InkBounds(left, top, right, bottom)
    }

    private fun pointAt(stroke: InkStroke, distance: Float): InkPoint {
        var remaining = distance.coerceAtLeast(0f)
        for (index in 1 until stroke.points.size) {
            val start = stroke.points[index - 1]
            val end = stroke.points[index]
            val segment = hypot(end.x - start.x, end.y - start.y)
            if (segment <= 0f) continue
            if (remaining <= segment) {
                val t = remaining / segment
                return InkPoint(
                    x = start.x + (end.x - start.x) * t,
                    y = start.y + (end.y - start.y) * t,
                    timeMillis = start.timeMillis +
                        ((end.timeMillis - start.timeMillis) * t).toLong()
                )
            }
            remaining -= segment
        }
        return stroke.points.last()
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

/** Rasterizes normalized ink to a reusable grayscale grid for an image model. */
object InkRasterizer {
    fun rasterize(
        ink: HandwritingInk,
        width: Int = HandwritingPreprocessor.RASTER_SIZE,
        height: Int = HandwritingPreprocessor.RASTER_SIZE
    ): FloatArray {
        val columns = width.coerceAtLeast(1)
        val rows = height.coerceAtLeast(1)
        val pixels = FloatArray(columns * rows)
        ink.strokes.forEach { stroke ->
            if (stroke.points.size < 2) {
                stamp(pixels, columns, rows, stroke.points.firstOrNull() ?: return@forEach)
                return@forEach
            }
            for (index in 1 until stroke.points.size) {
                drawLine(pixels, columns, rows, stroke.points[index - 1], stroke.points[index])
            }
        }
        return pixels
    }

    fun occupiedCount(pixels: FloatArray): Int = pixels.count { it > 0f }

    private fun drawLine(
        pixels: FloatArray,
        columns: Int,
        rows: Int,
        start: InkPoint,
        end: InkPoint
    ) {
        val x0 = start.x * (columns - 1)
        val y0 = start.y * (rows - 1)
        val x1 = end.x * (columns - 1)
        val y1 = end.y * (rows - 1)
        val steps = max(1, max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).toInt())
        for (step in 0..steps) {
            val t = step / steps.toFloat()
            stamp(
                pixels,
                columns,
                rows,
                InkPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            )
        }
    }

    private fun stamp(pixels: FloatArray, columns: Int, rows: Int, point: InkPoint) {
        val cx = point.x.toInt()
        val cy = point.y.toInt()
        for (dy in -1..1) {
            for (dx in -1..1) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until columns || y !in 0 until rows) continue
                val index = y * columns + x
                val weight = if (dx == 0 && dy == 0) 1f else 0.45f
                pixels[index] = max(pixels[index], weight)
            }
        }
    }
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
