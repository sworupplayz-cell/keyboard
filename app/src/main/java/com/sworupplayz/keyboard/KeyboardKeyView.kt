package com.sworupplayz.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Flat Gboard-style key. Behavior is attached by the IME, not this view. */
class KeyboardKeyView(context: Context) : TextView(context) {
    init {
        isAllCaps = false
        includeFontPadding = false
        gravity = Gravity.CENTER
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    fun bind(
        key: KeySpec,
        palette: KeyboardPalette,
        active: Boolean,
        compactScreen: Boolean,
        horizontalGapPx: Int,
        verticalGapPx: Int,
        radiusPx: Float,
        shadowPx: Int,
        borderColor: Int? = null,
        pressedEnabled: Boolean = true,
        edge: KeyEdge = KeyEdge.MIDDLE,
        fontScale: Float = 1f
    ) {
        val role = KeyVisuals.role(key)
        text = key.label
        contentDescription = AccessibilityLabels.key(key)
        setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            KeyboardUiMetrics.cappedLetterTextSp(
                KeyVisuals.letterTextSizeSp(key.label, compactScreen, role),
                fontScale
            )
        )
        setTypeface(
            Typeface.SANS_SERIF,
            if (role == KeyVisualRole.LETTER || role == KeyVisualRole.SPACE) Typeface.NORMAL else Typeface.BOLD
        )
        setTextColor(KeyboardTheme.textColor(role, palette, active))
        val fill = KeyboardTheme.keyBackground(
            KeyboardTheme.fillColor(role, palette, active),
            radiusPx,
            palette.shadow,
            shadowPx,
            borderColor,
            pressedEnabled
        )
        val horizontal = KeyTouchPolicy.horizontalInsets(horizontalGapPx, edge)
        val vertical = KeyTouchPolicy.verticalInsets(verticalGapPx)
        background = InsetDrawable(fill, horizontal.first, vertical.first, horizontal.second, vertical.second)
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, key.width)
    }
}
