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
    const val EMOJI_VISIBLE_ROWS = 4

    fun keyHeightDp(
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        height: KeyboardHeight = KeyboardHeight.NORMAL,
        density: KeyDensity = KeyDensity.NORMAL
    ): Int {
        val normal = when {
            landscape -> 42
            screenHeightDp < 480 -> 42
            screenWidthDp < 360 -> 46
            else -> 48
        }
        return (adjust(normal, height, 4) + densityDelta(density)).coerceAtLeast(40)
    }

    fun keyGapDp(
        screenWidthDp: Int,
        spacing: KeySpacing = KeySpacing.NORMAL
    ): Int {
        val base = if (screenWidthDp < 360) 1 else 2
        return when (spacing) {
            KeySpacing.COMPACT -> (base - 1).coerceAtLeast(1)
            KeySpacing.NORMAL -> base
            KeySpacing.COMFORTABLE -> base + 1
        }
    }

    fun cornerRadiusDp(style: KeyCornerStyle = KeyCornerStyle.NORMAL): Int = when (style) {
        KeyCornerStyle.TIGHT -> 3
        KeyCornerStyle.NORMAL -> KeyboardTheme.KEY_CORNER_RADIUS_DP
        KeyCornerStyle.ROUND -> 10
    }

    fun densityDelta(density: KeyDensity): Int = when (density) {
        KeyDensity.COMPACT -> -2
        KeyDensity.NORMAL -> 0
        KeyDensity.COMFORTABLE -> 3
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

    fun cappedLetterTextSp(baseSp: Float, fontScale: Float): Float {
        val scale = if (fontScale <= 0f) 1f else fontScale
        val cap = 1.15f
        return if (scale <= cap) baseSp else baseSp * cap / scale
    }

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

    fun suggestionHeightDp(
        landscape: Boolean = false,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int = adjust(if (landscape) 36 else SUGGESTION_HEIGHT_DP, height, 2)

    fun navigationHeightDp(
        landscape: Boolean = false,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int = adjust(if (landscape) 40 else NAVIGATION_HEIGHT_DP, height, 2)

    fun clipboardPanelHeightDp(
        screenHeightDp: Int,
        landscape: Boolean,
        height: KeyboardHeight = KeyboardHeight.NORMAL
    ): Int {
        val normal = when {
            landscape -> 112
            screenHeightDp < 600 -> 140
            else -> 160
        }
        return adjust(normal, height, 12)
    }

    fun emojiColumns(screenWidthDp: Int): Int = if (screenWidthDp < 340) 7 else 8

    fun emojiCategoryWidthDp(screenWidthDp: Int): Int =
        if (screenWidthDp < 360) AccessibilityLabels.MIN_TOUCH_DP else 40

    fun previewWidthDp(screenWidthDp: Int, landscape: Boolean): Int = when {
        landscape -> 44
        screenWidthDp < 360 -> 46
        else -> KeyboardTheme.PREVIEW_WIDTH_DP
    }

    fun previewHeightDp(screenWidthDp: Int, landscape: Boolean): Int = when {
        landscape -> 50
        screenWidthDp < 360 -> 54
        else -> KeyboardTheme.PREVIEW_HEIGHT_DP
    }

    fun contentWidthDp(screenWidthDp: Int, alignment: OneHandedAlignment): Int =
        OneHandedLayoutPolicy.insets(screenWidthDp, alignment).contentWidthDp

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
        (if (includeNavigation) navigationHeightDp(landscape, height) else 0) +
        (if (hasToolbar) toolbarHeightDp(screenWidthDp, landscape, height) else 0) +
        keyHeightDp(screenWidthDp, screenHeightDp, landscape, height) * rowCount +
        (if (hasSuggestion) suggestionHeightDp(landscape, height) else 0) +
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
