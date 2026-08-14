package com.sworupplayz.keyboard

enum class KeyEdge {
    START,
    MIDDLE,
    END,
    ALONE
}

/**
 * Touch ownership, bounce suppression, and visual-versus-hitbox insets.
 * Visual key size stays the same; the view fills its cell so edge keys stay usable.
 */
object KeyTouchPolicy {
    const val KEY_BOUNCE_MS = TouchCalibration.DEBOUNCE_MS
    const val LONG_PRESS_MS = TouchCalibration.LONG_PRESS_MS
    const val TOUCH_SLOP_RATIO = TouchCalibration.MOVE_TOLERANCE_RATIO
    const val MIN_SLOP_PX = TouchCalibration.MIN_SLOP_PX

    fun bounceGuardMs(kind: FeedbackKind): Long = when (kind) {
        FeedbackKind.CHROME, FeedbackKind.NAVIGATION -> KeyInteractionPolicy.DOUBLE_TAP_GUARD_MS
        else -> KEY_BOUNCE_MS
    }

    fun shouldAcceptTap(previousToken: String, previousAt: Long, token: String, now: Long): Boolean {
        if (token.isEmpty()) return true
        if (token != previousToken) return true
        return now - previousAt >= KEY_BOUNCE_MS
    }

    fun staysOnKey(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        width: Int,
        height: Int
    ): Boolean {
        val slopX = (width * TOUCH_SLOP_RATIO).coerceAtLeast(MIN_SLOP_PX.toFloat())
        val slopY = (height * TOUCH_SLOP_RATIO).coerceAtLeast(MIN_SLOP_PX.toFloat())
        val dx = kotlin.math.abs(currentX - downX)
        val dy = kotlin.math.abs(currentY - downY)
        if (dx > slopX || dy > slopY) return false
        return currentX in -slopX..(width + slopX) && currentY in -slopY..(height + slopY)
    }

    fun horizontalInsets(gapPx: Int, edge: KeyEdge): Pair<Int, Int> {
        val gap = gapPx.coerceAtLeast(0)
        val outer = (gap / 2).coerceAtLeast(0)
        return when (edge) {
            KeyEdge.START -> 0 to gap
            KeyEdge.END -> gap to 0
            KeyEdge.ALONE -> outer to outer
            KeyEdge.MIDDLE -> gap to gap
        }
    }

    fun verticalInsets(gapPx: Int): Pair<Int, Int> {
        val gap = gapPx.coerceAtLeast(0)
        return gap to gap
    }

    fun commitsOnDown(action: KeyAction): Boolean = action == KeyAction.BACKSPACE

    fun ownsGestureUntilUp(action: KeyAction): Boolean =
        action == KeyAction.BACKSPACE || action == KeyAction.TEXT || action == KeyAction.SPACE

    fun shouldCancelLongPress(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        width: Int,
        height: Int
    ): Boolean = TouchRecognition.shouldCancelLongPress(downX, downY, currentX, currentY, width, height)

    fun classify(
        trajectory: TouchTrajectory,
        width: Int,
        height: Int
    ): TouchGesture = trajectory.classify(width, height)
}

/**
 * Held-backspace timing. One grapheme per tick; the interval only gets shorter.
 */
object BackspaceRepeatPolicy {
    const val INITIAL_DELAY_MS = 400L
    const val INITIAL_INTERVAL_MS = 85L
    const val FAST_INTERVAL_MS = 38L
    const val ACCELERATE_AFTER = 6

    fun shouldStart(heldMs: Long): Boolean = heldMs >= INITIAL_DELAY_MS

    fun intervalMs(repeatCount: Int): Long =
        if (repeatCount >= ACCELERATE_AFTER) FAST_INTERVAL_MS else INITIAL_INTERVAL_MS

    fun deletesPerTick(repeatCount: Int): Int {
        repeatCount
        return 1
    }

    fun nextRepeatCount(current: Int): Int = (current + 1).coerceAtMost(10_000)
}

/**
 * IME enter/action mapping. Values match [android.view.inputmethod.EditorInfo]
 * so tests do not need the Android SDK.
 */
object EnterActionPolicy {
    const val ACTION_UNSPECIFIED = 0
    const val ACTION_NONE = 1
    const val ACTION_GO = 2
    const val ACTION_SEARCH = 3
    const val ACTION_SEND = 4
    const val ACTION_NEXT = 5
    const val ACTION_DONE = 6
    const val ACTION_PREVIOUS = 7
    const val IME_MASK_ACTION = 0x000000ff
    const val IME_FLAG_NO_ENTER_ACTION = 0x40000000

    fun actionId(imeOptions: Int): Int = imeOptions and IME_MASK_ACTION

    fun shouldPerformAction(imeOptions: Int): Boolean {
        if (imeOptions and IME_FLAG_NO_ENTER_ACTION != 0) return false
        return actionId(imeOptions) in ACTION_GO..ACTION_PREVIOUS
    }

    fun label(imeOptions: Int): String = when (actionId(imeOptions)) {
        ACTION_GO -> "Go"
        ACTION_SEARCH -> "Search"
        ACTION_SEND -> "Send"
        ACTION_NEXT -> "Next"
        ACTION_DONE -> "Done"
        ACTION_PREVIOUS -> "Prev"
        else -> "↵"
    }

    fun spokenLabel(imeOptions: Int): String = when (actionId(imeOptions)) {
        ACTION_GO -> "Go"
        ACTION_SEARCH -> "Search"
        ACTION_SEND -> "Send"
        ACTION_NEXT -> "Next"
        ACTION_DONE -> "Done"
        ACTION_PREVIOUS -> "Previous"
        else -> "Enter"
    }

    fun shouldInsertNewline(imeOptions: Int): Boolean = !shouldPerformAction(imeOptions)

    fun resolve(imeOptions: Int?): Int = imeOptions ?: 0
}
