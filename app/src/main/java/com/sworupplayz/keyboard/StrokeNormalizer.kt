package com.sworupplayz.keyboard

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Shared stroke math for Phase 32 (32-point) and the Phase 33 dual framework.
 * This is not a recognizer.
 */
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

    fun smooth(stroke: InkStroke): InkStroke {
        if (stroke.points.size < 3) return stroke
        val output = ArrayList<InkPoint>(stroke.points.size)
        output += stroke.points.first()
        for (index in 1 until stroke.points.lastIndex) {
            val previous = stroke.points[index - 1]
            val current = stroke.points[index]
            val next = stroke.points[index + 1]
            output += InkPoint(
                x = (previous.x + current.x + next.x) / 3f,
                y = (previous.y + current.y + next.y) / 3f,
                timeMillis = current.timeMillis,
                pressure = current.pressure
            )
        }
        output += stroke.points.last()
        return InkStroke(output)
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
                    InkPoint(point.x - bounds.left, point.y - bounds.top, point.timeMillis, point.pressure)
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
                        timeMillis = point.timeMillis,
                        pressure = point.pressure
                    )
                })
            }
        )
    }

    fun normalize(ink: HandwritingInk): HandwritingInk {
        val filtered = filterNoise(ink)
        if (filtered.strokes.isEmpty()) return filtered
        val resampled = HandwritingInk(filtered.strokes.map { resample(smooth(it)) })
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
                        ((end.timeMillis - start.timeMillis) * t).toLong(),
                    pressure = start.pressure + (end.pressure - start.pressure) * t
                )
            }
            remaining -= segment
        }
        return stroke.points.last()
    }
}
