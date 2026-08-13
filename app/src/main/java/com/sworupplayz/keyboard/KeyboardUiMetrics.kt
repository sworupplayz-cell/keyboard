package com.sworupplayz.keyboard

/** Pure responsive dimensions used by the IME and layout tests. */
object KeyboardUiMetrics {
    const val ROOT_VERTICAL_PADDING_DP = 6
    const val ROOT_HORIZONTAL_PADDING_DP = 4
    const val NAVIGATION_HEIGHT_DP = 44
    const val SUGGESTION_HEIGHT_DP = 40
    const val TOOLBAR_HEIGHT_DP = 32
    const val EMOJI_CATEGORY_HEIGHT_DP = 44
    const val EMOJI_KEY_HEIGHT_DP = 48
    const val HANDWRITING_RESULT_HEIGHT_DP = 40

    fun keyHeightDp(
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int {
        val normal = when {
            landscape -> 42
            screenHeightDp < 480 -> 42
            screenWidthDp < 360 -> 46
            else -> 48
        }
        return adjust(normal, height, 4)
    }

    fun compactNumberRowHeightDp(height: KeyboardHeight = KeyboardHeight.NORMAL): Int = when (height) {
        KeyboardHeight.SMALL -> 34
        KeyboardHeight.NORMAL -> 36
        KeyboardHeight.LARGE -> 38
    }

    fun emojiKeyHeightDp(height: KeyboardHeight = KeyboardHeight.NORMAL): Int =
        adjust(EMOJI_KEY_HEIGHT_DP, height, 4)

    fun handwritingCanvasHeightDp(
        screenHeightDp: Int,
        landscape: Boolean,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int {
        val normal = when {
            landscape -> 104
            screenHeightDp < 600 -> 140
            else -> 160
        }
        return adjust(normal, height, 16)
    }

    fun keyMarginDp(screenWidthDp: Int): Int = if (screenWidthDp < 360) 1 else 2

    fun maxToolbarItems(screenWidthDp: Int): Int = when {
        screenWidthDp < 340 -> 4
        screenWidthDp < 400 -> 5
        else -> 6
    }

    fun toolbarHeightDp(
        screenWidthDp: Int,
        landscape: Boolean,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int {
        val normal = when {
            landscape -> 28
            screenWidthDp < 360 -> 28
            else -> TOOLBAR_HEIGHT_DP
        }
        return adjust(normal, height, 2)
    }

    fun estimatedStandardHeightDp(
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        rowCount: Int,
        hasSuggestion: Boolean,
        includeNavigation: Boolean = true,
        height: KeyboardHeight = KeyboardHeight.NORMAL,
        hasNumberRow: Boolean = false,
        hasToolbar: Boolean = false
    ): Int = ROOT_VERTICAL_PADDING_DP +
        (if (includeNavigation) NAVIGATION_HEIGHT_DP else 0) +
        (if (hasToolbar) toolbarHeightDp(screenWidthDp, landscape, height) else 0) +
        keyHeightDp(screenWidthDp, screenHeightDp, landscape, height) * rowCount +
        (if (hasSuggestion) SUGGESTION_HEIGHT_DP else 0) +
        (if (hasNumberRow) compactNumberRowHeightDp(height) else 0)

    fun equalKeyWidthDp(screenWidthDp: Int, keyCount: Int): Float {
        if (keyCount <= 0) return 0f
        val margins = keyMarginDp(screenWidthDp) * 2 * keyCount
        return (screenWidthDp - ROOT_HORIZONTAL_PADDING_DP - margins).coerceAtLeast(0).toFloat() / keyCount
    }

    private fun adjust(normal: Int, height: KeyboardHeight, amount: Int): Int = when (height) {
        KeyboardHeight.SMALL -> normal - amount
        KeyboardHeight.NORMAL -> normal
        KeyboardHeight.LARGE -> normal + amount
    }
}
