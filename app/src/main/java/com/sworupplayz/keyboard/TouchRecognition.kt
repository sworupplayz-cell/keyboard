package com.sworupplayz.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Centralized touch calibration. Values are ratios or milliseconds so they
 * scale across phone widths, one-handed gutters, and keyboard heights.
 */
object TouchCalibration {
    /** Grow a logical hitbox toward neighbors without changing the drawn key. */
    const val EDGE_EXPANSION_RATIO = 0.12f

    /** Extra outer expansion on the first/last key of a row. */
    const val OUTER_EDGE_EXPANSION_RATIO = 0.18f

    /** Inner region that must stay locked to the key under the finger. */
    const val CENTER_LOCK_RATIO = 0.28f

    /** Same family as [KeyTouchPolicy.TOUCH_SLOP_RATIO]: accidental drift. */
    const val MOVE_TOLERANCE_RATIO = 0.42f
    const val MIN_SLOP_PX = 24

    /** Existing long-press timeout. Slightly-long taps stay below this. */
    const val LONG_PRESS_MS = 420L

    /** Travel needed before a boundary press can become a neighbor slide. */
    const val SLIDE_THRESHOLD_RATIO = 0.35f
    const val SLIDE_MIN_PX = 18

    /** Fast flicks toward a neighbor may slide even with a shorter path. */
    const val VELOCITY_SLIDE_PX_PER_MS = 0.45f

    /** Existing letter-key bounce window. */
    const val DEBOUNCE_MS = 32L

    const val NARROW_SCREEN_DP = 360
    const val NARROW_EXPANSION_BOOST = 0.04f
    const val ONE_HANDED_EXPANSION_BOOST = 0.03f

    const val MAX_ADAPTIVE_PAIRS = 48
    const val ADAPTIVE_WEIGHT = 0.08f
}

enum class TouchGesture {
    TAP,
    DRIFT,
    SLIDE,
    LONG_PRESS,
    CANCEL
}

enum class TouchDirection {
    NONE,
    LEFT,
    RIGHT,
    UP,
    DOWN
}

data class TouchRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun distanceToCenter(x: Float, y: Float): Float = hypot(x - centerX, y - centerY)
}

data class TouchCandidate(
    val id: String,
    val rect: TouchRect,
    val edge: KeyEdge = KeyEdge.MIDDLE,
    val row: Int = 0
)

/**
 * Row-aware geometry. Logical boxes are larger than the drawn key, but a
 * centered press still wins over a neighbor's expanded edge.
 */
object TouchGeometryPolicy {
    fun expansion(
        width: Float,
        edge: KeyEdge,
        screenWidthDp: Int,
        oneHanded: Boolean
    ): Pair<Float, Float> {
        var ratio = TouchCalibration.EDGE_EXPANSION_RATIO
        if (screenWidthDp < TouchCalibration.NARROW_SCREEN_DP) {
            ratio += TouchCalibration.NARROW_EXPANSION_BOOST
        }
        if (oneHanded) ratio += TouchCalibration.ONE_HANDED_EXPANSION_BOOST
        val inner = width * ratio
        val outer = width * max(ratio, TouchCalibration.OUTER_EDGE_EXPANSION_RATIO)
        return when (edge) {
            KeyEdge.START -> outer to inner
            KeyEdge.END -> inner to outer
            KeyEdge.ALONE -> outer to outer
            KeyEdge.MIDDLE -> inner to inner
        }
    }

    fun expanded(rect: TouchRect, edge: KeyEdge, screenWidthDp: Int, oneHanded: Boolean): TouchRect {
        val (leftGrow, rightGrow) = expansion(rect.width, edge, screenWidthDp, oneHanded)
        val vertical = rect.height * TouchCalibration.EDGE_EXPANSION_RATIO
        return TouchRect(
            left = rect.left - leftGrow,
            top = rect.top - vertical,
            right = rect.right + rightGrow,
            bottom = rect.bottom + vertical
        )
    }

    fun isCenterHit(rect: TouchRect, x: Float, y: Float): Boolean {
        val insetX = rect.width * TouchCalibration.CENTER_LOCK_RATIO
        val insetY = rect.height * TouchCalibration.CENTER_LOCK_RATIO
        return x in (rect.left + insetX)..(rect.right - insetX) &&
            y in (rect.top + insetY)..(rect.bottom - insetY)
    }

    fun isEdgeHit(rect: TouchRect, x: Float, y: Float): Boolean =
        rect.contains(x, y) && !isCenterHit(rect, x, y)

    fun resolve(
        candidates: List<TouchCandidate>,
        x: Float,
        y: Float,
        screenWidthDp: Int = 360,
        oneHanded: Boolean = false,
        adaptive: AdaptiveHitboxPolicy = AdaptiveHitboxPolicy(),
        preferredId: String? = null
    ): String? {
        if (candidates.isEmpty()) return null
        preferredId?.let { preferred ->
            candidates.firstOrNull { it.id == preferred }?.let { owner ->
                if (isCenterHit(owner.rect, x, y)) return preferred
            }
        }
        val hits = ArrayList<Pair<TouchCandidate, Float>>(candidates.size)
        candidates.forEach { candidate ->
            val grown = expanded(candidate.rect, candidate.edge, screenWidthDp, oneHanded)
            if (!grown.contains(x, y) && !candidate.rect.contains(x, y)) return@forEach
            var score = candidate.rect.distanceToCenter(x, y)
            if (isCenterHit(candidate.rect, x, y)) score -= candidate.rect.width
            preferredId?.let { from ->
                score -= adaptive.weight(from, candidate.id) * candidate.rect.width
            }
            hits += candidate to score
        }
        if (hits.isEmpty()) return preferredId
        return hits.minByOrNull { it.second }?.first?.id
    }
}

/**
 * Path between ACTION_DOWN and ACTION_UP. Allocation-free after construction.
 */
class TouchTrajectory {
    var pointerId: Int = -1
        private set
    var startX: Float = 0f
        private set
    var startY: Float = 0f
        private set
    var currentX: Float = 0f
        private set
    var currentY: Float = 0f
        private set
    var startAt: Long = 0L
        private set
    var currentAt: Long = 0L
        private set
    var samples: Int = 0
        private set

    fun start(x: Float, y: Float, now: Long, pointerId: Int = 0) {
        this.pointerId = pointerId
        startX = x
        startY = y
        currentX = x
        currentY = y
        startAt = now
        currentAt = now
        samples = 1
    }

    fun move(x: Float, y: Float, now: Long) {
        currentX = x
        currentY = y
        currentAt = now
        samples += 1
    }

    fun distance(): Float = hypot(currentX - startX, currentY - startY)

    fun durationMs(): Long = (currentAt - startAt).coerceAtLeast(0L)

    fun velocity(): Float {
        val elapsed = durationMs().coerceAtLeast(1L)
        return distance() / elapsed.toFloat()
    }

    fun direction(): TouchDirection {
        val dx = currentX - startX
        val dy = currentY - startY
        if (abs(dx) < 1f && abs(dy) < 1f) return TouchDirection.NONE
        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0f) TouchDirection.RIGHT else TouchDirection.LEFT
        } else {
            if (dy >= 0f) TouchDirection.DOWN else TouchDirection.UP
        }
    }

    fun classify(width: Int, height: Int): TouchGesture {
        val slopX = (width * TouchCalibration.MOVE_TOLERANCE_RATIO)
            .coerceAtLeast(TouchCalibration.MIN_SLOP_PX.toFloat())
        val slopY = (height * TouchCalibration.MOVE_TOLERANCE_RATIO)
            .coerceAtLeast(TouchCalibration.MIN_SLOP_PX.toFloat())
        val dx = abs(currentX - startX)
        val dy = abs(currentY - startY)
        if (dx > slopX * 2.4f || dy > slopY * 2.4f) return TouchGesture.CANCEL
        val slideNeed = (width * TouchCalibration.SLIDE_THRESHOLD_RATIO)
            .coerceAtLeast(TouchCalibration.SLIDE_MIN_PX.toFloat())
        if (durationMs() >= TouchCalibration.LONG_PRESS_MS && dx <= slopX && dy <= slopY) {
            return TouchGesture.LONG_PRESS
        }
        if (distance() >= slideNeed || velocity() >= TouchCalibration.VELOCITY_SLIDE_PX_PER_MS) {
            return TouchGesture.SLIDE
        }
        if (dx > slopX * 0.35f || dy > slopY * 0.35f) return TouchGesture.DRIFT
        return TouchGesture.TAP
    }

    fun reset() {
        pointerId = -1
        samples = 0
        startAt = 0L
        currentAt = 0L
    }
}

/**
 * Bounded neighbor-choice weights. Stores only short key ids, never typed text.
 */
class AdaptiveHitboxPolicy(
    initial: Map<String, Int> = emptyMap(),
    private val limit: Int = TouchCalibration.MAX_ADAPTIVE_PAIRS
) {
    private val counts = linkedMapOf<String, Int>()

    init {
        initial.forEach { (pair, score) ->
            if (isSafePair(pair)) counts[pair] = score.coerceAtLeast(1)
        }
        trim()
    }

    fun record(fromId: String, toId: String): Boolean {
        if (fromId == toId || !isSafeKeyId(fromId) || !isSafeKeyId(toId)) return false
        val key = "$fromId>$toId"
        counts[key] = (counts.remove(key) ?: 0) + 1
        trim()
        return true
    }

    fun weight(fromId: String, toId: String): Float {
        val count = counts["$fromId>$toId"] ?: return 0f
        return (count * 0.02f).coerceAtMost(TouchCalibration.ADAPTIVE_WEIGHT)
    }

    fun size(): Int = counts.size

    fun serialize(): String = counts.entries.joinToString("\n") { (pair, score) -> "$pair\t$score" }

    private fun trim() {
        while (counts.size > limit) counts.remove(counts.keys.first())
    }

    companion object {
        fun fromSerialized(value: String?): AdaptiveHitboxPolicy {
            val entries = linkedMapOf<String, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2 && isSafePair(parts[0])) {
                    entries[parts[0]] = parts[1].toIntOrNull() ?: 1
                }
            }
            return AdaptiveHitboxPolicy(entries)
        }

        fun isSafeKeyId(id: String): Boolean {
            val clean = id.trim()
            if (clean.isEmpty() || clean.length > 8) return false
            if ('\n' in clean || '\t' in clean || ' ' in clean) return false
            if (SpecialTokenPolicy.looksLikeUrl(clean) || SpecialTokenPolicy.looksLikeEmail(clean)) return false
            if (SpecialTokenPolicy.looksLikeNumber(clean)) return false
            return clean.all { it.isLetterOrDigit() || it == '>' || it == '_' || it.code in 0x0900..0x097F }
        }

        private fun isSafePair(value: String): Boolean {
            val parts = value.split('>')
            return parts.size == 2 && isSafeKeyId(parts[0]) && isSafeKeyId(parts[1])
        }
    }
}

/**
 * One active pointer at a time for letter/space/backspace. A second finger
 * cannot steal the first gesture.
 */
class TouchRecognitionState {
    val trajectory = TouchTrajectory()
    var ownerId: String? = null
        private set
    var resolvedId: String? = null

    fun tryAcquire(pointerId: Int, ownerId: String, x: Float, y: Float, now: Long): Boolean {
        if (trajectory.pointerId >= 0 && trajectory.pointerId != pointerId) return false
        this.ownerId = ownerId
        resolvedId = ownerId
        trajectory.start(x, y, now, pointerId)
        return true
    }

    fun isOwner(pointerId: Int): Boolean =
        trajectory.pointerId == pointerId && ownerId != null

    fun release(pointerId: Int) {
        if (trajectory.pointerId == pointerId) reset()
    }

    fun reset() {
        ownerId = null
        resolvedId = null
        trajectory.reset()
    }
}

object TouchRecognition {
    fun keyId(key: KeySpec): String {
        val raw = key.output.ifEmpty { key.label }.trim()
        return if (AdaptiveHitboxPolicy.isSafeKeyId(raw)) raw else key.action.name.take(8)
    }

    fun shouldKeepCurrentKey(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        width: Int,
        height: Int
    ): Boolean = KeyTouchPolicy.staysOnKey(downX, downY, currentX, currentY, width, height)

    fun shouldCancelLongPress(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        width: Int,
        height: Int
    ): Boolean = !KeyTouchPolicy.staysOnKey(downX, downY, currentX, currentY, width, height)

    fun shouldOpenAlternates(trajectory: TouchTrajectory, stillOnKey: Boolean, now: Long = trajectory.currentAt): Boolean {
        if (!stillOnKey) return false
        val held = (now - trajectory.startAt).coerceAtLeast(0L)
        if (held < TouchCalibration.LONG_PRESS_MS) return false
        return trajectory.classify(64, 64) != TouchGesture.SLIDE
    }

    fun shouldSlideCorrect(
        startedOnEdge: Boolean,
        gesture: TouchGesture,
        fromId: String,
        toId: String?
    ): Boolean {
        if (!startedOnEdge || toId == null || toId == fromId) return false
        return gesture == TouchGesture.SLIDE
    }
}
