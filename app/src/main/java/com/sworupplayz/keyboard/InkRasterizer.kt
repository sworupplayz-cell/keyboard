package com.sworupplayz.keyboard

import kotlin.math.max

/**
 * Rasterizes normalized ink to a reusable grayscale grid.
 * Phase 32 keeps the 32×32 path. The dual framework uses a reused 224×224 buffer.
 */
object InkRasterizer {
    const val FRAMEWORK_SIZE = 224

    private val frameworkBuffer = FloatArray(FRAMEWORK_SIZE * FRAMEWORK_SIZE)

    fun rasterize(
        ink: HandwritingInk,
        width: Int = HandwritingPreprocessor.RASTER_SIZE,
        height: Int = HandwritingPreprocessor.RASTER_SIZE
    ): FloatArray {
        val columns = width.coerceAtLeast(1)
        val rows = height.coerceAtLeast(1)
        val pixels = FloatArray(columns * rows)
        draw(ink, pixels, columns, rows)
        return pixels
    }

    fun rasterizeFramework(ink: HandwritingInk): FloatArray {
        java.util.Arrays.fill(frameworkBuffer, 0f)
        draw(ink, frameworkBuffer, FRAMEWORK_SIZE, FRAMEWORK_SIZE)
        return frameworkBuffer
    }

    fun occupiedCount(pixels: FloatArray): Int = pixels.count { it > 0f }

    fun occupiedBounds(pixels: FloatArray, width: Int, height: Int): InkBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] <= 0f) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x > right) right = x
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        return InkBounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private fun draw(ink: HandwritingInk, pixels: FloatArray, columns: Int, rows: Int) {
        ink.strokes.forEach { stroke ->
            if (stroke.points.size < 2) {
                stamp(pixels, columns, rows, stroke.points.firstOrNull() ?: return@forEach)
                return@forEach
            }
            for (index in 1 until stroke.points.size) {
                drawLine(pixels, columns, rows, stroke.points[index - 1], stroke.points[index])
            }
        }
    }

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
