package com.sworupplayz.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Character balloon shown above a pressed letter, overlapping the keyboard chrome. */
class KeyPreviewView(context: Context) : TextView(context) {
    init {
        isAllCaps = false
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL)
        visibility = GONE
        minWidth = dp(KeyboardTheme.PREVIEW_WIDTH_DP)
        minHeight = dp(KeyboardTheme.PREVIEW_HEIGHT_DP)
        elevation = 8f * resources.displayMetrics.density
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(palette: KeyboardPalette, radiusPx: Float) {
        setTextColor(palette.popupText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        background = KeyboardTheme.roundedRect(palette.popupBackground, radiusPx)
    }

    fun showAbove(anchor: View, host: View, label: String) {
        text = label
        visibility = VISIBLE
        measureAndPlace(this, anchor, host, extraLiftPx = dp(4))
    }

    fun dismiss() {
        visibility = GONE
    }
}

/** Compact long-press row of alternate characters. */
class AlternateChooserView(context: Context) : LinearLayout(context) {
    var onPick: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        visibility = GONE
        elevation = 10f * resources.displayMetrics.density
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun showAbove(
        anchor: View,
        host: View,
        options: List<String>,
        palette: KeyboardPalette,
        radiusPx: Float
    ) {
        removeAllViews()
        setPadding(dp(6), dp(4), dp(6), dp(4))
        background = KeyboardTheme.roundedRect(palette.popupBackground, radiusPx)
        options.forEach { option ->
            addView(TextView(context).apply {
                text = option
                contentDescription = option
                isAllCaps = false
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(palette.popupText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = KeyboardTheme.keyBackground(
                    palette.key,
                    radiusPx,
                    palette.shadow,
                    dp(1)
                )
                setOnClickListener {
                    onPick?.invoke(option)
                    dismiss()
                }
            })
        }
        visibility = VISIBLE
        measureAndPlace(this, anchor, host, extraLiftPx = dp(6))
    }

    fun dismiss() {
        removeAllViews()
        visibility = GONE
    }
}

private fun measureAndPlace(overlay: View, anchor: View, host: View, extraLiftPx: Int) {
    val maxWidth = host.width.coerceAtLeast(1)
    val maxHeight = host.height.coerceAtLeast(1)
    overlay.measure(
        View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
    )
    overlay.layout(0, 0, overlay.measuredWidth, overlay.measuredHeight)
    val anchorLoc = IntArray(2)
    val hostLoc = IntArray(2)
    anchor.getLocationInWindow(anchorLoc)
    host.getLocationInWindow(hostLoc)
    val x = (anchorLoc[0] - hostLoc[0] + (anchor.width - overlay.measuredWidth) / 2)
        .coerceIn(0, (host.width - overlay.measuredWidth).coerceAtLeast(0))
    val y = (anchorLoc[1] - hostLoc[1] - overlay.measuredHeight - extraLiftPx)
        .coerceAtLeast(0)
    overlay.translationX = x.toFloat()
    overlay.translationY = y.toFloat()
}

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
