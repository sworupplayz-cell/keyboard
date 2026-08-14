package com.sworupplayz.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
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
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(palette: KeyboardPalette, radiusPx: Float) {
        setTextColor(KeyboardThemeTokens.previewText(palette))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        background = KeyboardTheme.roundedRect(KeyboardThemeTokens.previewFill(palette), radiusPx)
    }

    fun showAbove(
        anchor: View,
        host: View,
        label: String,
        textSizeSp: Float = 22f,
        widthDp: Int = KeyboardTheme.PREVIEW_WIDTH_DP,
        heightDp: Int = KeyboardTheme.PREVIEW_HEIGHT_DP
    ) {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        minWidth = dp(widthDp)
        minHeight = dp(heightDp)
        visibility = VISIBLE
        measureAndPlace(this, anchor, host, extraLiftPx = dp(4))
    }

    fun dismiss() {
        visibility = GONE
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/** Compact long-press row of alternate characters or language options. */
class AlternateChooserView(context: Context) : HorizontalScrollView(context) {
    var onPick: ((String) -> Unit)? = null
    private val row = LinearLayout(context)

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        visibility = GONE
        elevation = 10f * resources.displayMetrics.density
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        addView(
            row,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun isShowing(): Boolean = visibility == VISIBLE

    fun showAbove(
        anchor: View,
        host: View,
        options: List<String>,
        palette: KeyboardPalette,
        radiusPx: Float,
        selected: String? = null
    ) {
        row.removeAllViews()
        setPadding(dp(6), dp(4), dp(6), dp(4))
        background = KeyboardTheme.roundedRect(KeyboardThemeTokens.overlayFill(palette), radiusPx)
        options.forEach { option ->
            val isSelected = selected != null && option == selected
            val language = LanguageSwitcher.fromPickerLabel(option)
            row.addView(TextView(context).apply {
                text = option
                contentDescription = when {
                    language != null -> AccessibilityLabels.languageOption(
                        LanguageSwitcher.options().first { it.language == language },
                        isSelected
                    )
                    isSelected -> "$option, selected"
                    else -> option
                }
                isAllCaps = false
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                minimumHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                setTextColor(
                    if (isSelected) KeyboardThemeTokens.selectedLabel(palette)
                    else KeyboardThemeTokens.overlayText(palette)
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = KeyboardTheme.keyBackground(
                    if (isSelected) KeyboardThemeTokens.toolbarSelected(palette) else palette.key,
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
        row.removeAllViews()
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
