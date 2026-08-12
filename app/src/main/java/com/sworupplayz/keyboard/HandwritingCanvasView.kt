package com.sworupplayz.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Small dependency-free drawing surface. Recognition is never run during MOVE events. */
class HandwritingCanvasView(context: Context) : View(context) {
    var onStrokeFinished: ((List<InkPoint>) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4f * resources.displayMetrics.density
    }
    private val strokes = ArrayList<DrawnStroke>()
    private var currentStroke: DrawnStroke? = null
    private val minimumMoveSquared = (1.5f * resources.displayMetrics.density).let { it * it }

    fun setInkColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun clearInk() {
        strokes.clear()
        currentStroke = null
        invalidate()
    }

    fun undoStroke(): Boolean {
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { canvas.drawPath(it.path, paint) }
        currentStroke?.let { canvas.drawPath(it.path, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentStroke = DrawnStroke(
                    Path().apply { moveTo(x, y) },
                    arrayListOf(InkPoint(x, y, event.eventTime))
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = currentStroke ?: return false
                appendPointIfMoved(stroke, x, y, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val stroke = currentStroke ?: return false
                appendPointIfMoved(stroke, x, y, event.eventTime)
                currentStroke = null
                parent?.requestDisallowInterceptTouchEvent(false)
                if (stroke.points.size >= 2) {
                    strokes += stroke
                    onStrokeFinished?.invoke(normalize(stroke.points))
                }
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun appendPointIfMoved(stroke: DrawnStroke, x: Float, y: Float, timeMillis: Long) {
        val last = stroke.points.last()
        val dx = x - last.x
        val dy = y - last.y
        if (dx * dx + dy * dy < minimumMoveSquared) return
        stroke.path.lineTo(x, y)
        stroke.points += InkPoint(x, y, timeMillis)
    }

    private fun normalize(points: List<InkPoint>): List<InkPoint> {
        val safeWidth = max(width, 1).toFloat()
        val safeHeight = max(height, 1).toFloat()
        return points.map { point ->
            InkPoint(point.x / safeWidth, point.y / safeHeight, point.timeMillis)
        }
    }

    private data class DrawnStroke(
        val path: Path,
        val points: ArrayList<InkPoint>
    )
}
