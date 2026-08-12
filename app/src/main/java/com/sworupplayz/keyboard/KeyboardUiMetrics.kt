package com.sworupplayz.keyboard

/** Pure responsive dimensions used by the IME and layout tests. */
object KeyboardUiMetrics {
    const val ROOT_VERTICAL_PADDING_DP = 6
    const val ROOT_HORIZONTAL_PADDING_DP = 4
    const val NAVIGATION_HEIGHT_DP = 44
    const val SUGGESTION_HEIGHT_DP = 40
    const val EMOJI_CATEGORY_HEIGHT_DP = 44
    const val EMOJI_KEY_HEIGHT_DP = 48
    const val HANDWRITING_RESULT_HEIGHT_DP = 40

    fun keyHeightDp(screenWidthDp: Int, screenHeightDp: Int, landscape: Boolean): Int = when {
        landscape -> 42
        screenHeightDp < 480 -> 42
        screenWidthDp < 360 -> 46
        else -> 48
    }

    fun handwritingCanvasHeightDp(screenHeightDp: Int, landscape: Boolean): Int = when {
        landscape -> 104
        screenHeightDp < 600 -> 140
        else -> 160
    }

    fun keyMarginDp(screenWidthDp: Int): Int = if (screenWidthDp < 360) 1 else 2

    fun estimatedStandardHeightDp(
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        rowCount: Int,
        hasSuggestion: Boolean
    ): Int = ROOT_VERTICAL_PADDING_DP + NAVIGATION_HEIGHT_DP +
        keyHeightDp(screenWidthDp, screenHeightDp, landscape) * rowCount +
        if (hasSuggestion) SUGGESTION_HEIGHT_DP else 0

    fun equalKeyWidthDp(screenWidthDp: Int, keyCount: Int): Float {
        if (keyCount <= 0) return 0f
        val margins = keyMarginDp(screenWidthDp) * 2 * keyCount
        return (screenWidthDp - ROOT_HORIZONTAL_PADDING_DP - margins).coerceAtLeast(0).toFloat() / keyCount
    }
}
