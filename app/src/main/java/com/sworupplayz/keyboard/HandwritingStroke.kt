package com.sworupplayz.keyboard

/**
 * Stroke collector for the dual-language handwriting framework.
 * Reuses [InkPoint]/[InkStroke]; this is not a second ink format.
 */
class HandwritingStrokeCollector {
    private val finished = ArrayList<InkStroke>()
    private var current = ArrayList<InkPoint>()
    private var drawing = false

    val strokeCount: Int get() = finished.size
    val isEmpty: Boolean get() = finished.isEmpty() && current.isEmpty()

    fun beginStroke(point: InkPoint? = null) {
        drawing = true
        current = ArrayList()
        if (point != null) current += point
    }

    fun append(x: Float, y: Float, timeMillis: Long = 0L, pressure: Float = 1f) {
        if (!drawing) beginStroke()
        current += InkPoint(x, y, timeMillis, pressure)
    }

    fun endStroke(): InkStroke? {
        drawing = false
        if (current.size < 2) {
            current = ArrayList()
            return null
        }
        val stroke = InkStroke(current.toList())
        finished += stroke
        current = ArrayList()
        return stroke
    }

    fun addFinished(points: List<InkPoint>): Boolean {
        if (points.size < 2) return false
        finished += InkStroke(points.toList())
        return true
    }

    fun undo(): Boolean {
        current = ArrayList()
        drawing = false
        if (finished.isEmpty()) return false
        finished.removeAt(finished.lastIndex)
        return true
    }

    fun clear() {
        finished.clear()
        current = ArrayList()
        drawing = false
    }

    fun snapshot(): HandwritingInk {
        val strokes = ArrayList<InkStroke>(finished)
        if (current.size >= 2) strokes += InkStroke(current.toList())
        return HandwritingInk(strokes)
    }

    fun boundingBox(): InkBounds? = StrokeNormalizer.bounds(snapshot())
}
