package com.sworupplayz.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Settings-only mini keyboard. It never commits text or talks to the IME. */
class KeyboardPreviewView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    fun bind(settings: KeyboardSettings, systemIsDark: Boolean, language: KeyboardLanguage = KeyboardLanguage.ENGLISH) {
        val palette = AppearanceCatalog.resolve(
            settings.visualTheme,
            settings.appearance,
            systemIsDark,
            settings.colorPreset
        )
        val style = ThemeStyle(settings.keyShadows, settings.keyBorders, settings.pressedHighlight)
        val radius = dp(KeyboardUiMetrics.cornerRadiusDp(settings.keyCorner)).toFloat()
        val shadow = if (style.shadows) dp(KeyboardTheme.SHADOW_DP) else 0
        val border = if (style.borders) palette.divider else null
        val rowHeight = dp(
            (36 + when (settings.height) {
                KeyboardHeight.SMALL -> -3
                KeyboardHeight.NORMAL -> 0
                KeyboardHeight.LARGE -> 4
            } + KeyboardUiMetrics.densityDelta(settings.keyDensity)).coerceAtLeast(32)
        )
        val gap = dp(KeyboardUiMetrics.keyGapDp(360, settings.keySpacing))
        val insets = OneHandedLayoutPolicy.insets(412, settings.oneHanded)
        setPadding(dp(8 + insets.startDp / 6), dp(8), dp(8 + insets.endDp / 6), dp(8))
        removeAllViews()
        setBackgroundColor(KeyboardThemeTokens.gutter(palette))
        addRow(rowHeight, gap) {
            addPreviewKey("hello", palette, style, radius, shadow, border, 1f, primary = true)
            addPreviewKey("help", palette, style, radius, shadow, border, 1f, primary = false)
            addPreviewKey("he'll", palette, style, radius, shadow, border, 1f, primary = false)
        }
        if (settings.numberRow) {
            val numberHeight = dp(
                (KeyboardUiMetrics.compactNumberRowHeightDp(settings.height) +
                    KeyboardUiMetrics.densityDelta(settings.keyDensity)).coerceAtLeast(28)
            )
            addRow(numberHeight, gap) {
                "1234567890".forEach { addPreviewKey(it.toString(), palette, style, radius, shadow, border, 1f) }
            }
        }
        addRow(rowHeight, gap) {
            "qwer".forEach { addPreviewKey(it.toString(), palette, style, radius, shadow, border, 1f) }
        }
        addRow(rowHeight, gap) {
            addPreviewKey("⇧", palette, style, radius, shadow, border, 1.2f, modifier = true)
            addPreviewKey("a", palette, style, radius, shadow, border, 1f)
            addPreviewKey("s", palette, style, radius, shadow, border, 1f)
            addPreviewKey("⌫", palette, style, radius, shadow, border, 1.2f, modifier = true)
        }
        addRow(rowHeight, gap) {
            addPreviewKey(language.spaceLabel(), palette, style, radius, shadow, border, 2.4f, space = true)
            addPreviewKey("↵", palette, style, radius, shadow, border, 1.1f, enter = true)
        }
    }

    private fun addRow(height: Int, gap: Int, populate: LinearLayout.() -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, height))
        row.populate()
        for (index in 0 until row.childCount) {
            val params = row.getChildAt(index).layoutParams as LayoutParams
            params.setMargins(gap, gap, gap, gap)
            row.getChildAt(index).layoutParams = params
        }
    }

    private fun LinearLayout.addPreviewKey(
        label: String,
        palette: KeyboardPalette,
        style: ThemeStyle,
        radius: Float,
        shadow: Int,
        border: Int?,
        weight: Float,
        primary: Boolean = false,
        modifier: Boolean = false,
        space: Boolean = false,
        enter: Boolean = false
    ) {
        addView(TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            isAllCaps = false
            setTypeface(Typeface.SANS_SERIF, if (primary || modifier || enter) Typeface.BOLD else Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (space) 12f else 14f)
            val fill = when {
                enter -> palette.enterKey
                modifier -> palette.specialKey
                else -> if (primary) palette.background else palette.key
            }
            setTextColor(if (enter) palette.enterText else if (primary) KeyboardThemeTokens.suggestionPrimary(palette) else palette.text)
            background = KeyboardTheme.keyBackground(
                fill,
                radius,
                palette.shadow,
                shadow,
                border,
                style.pressedHighlight
            )
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
