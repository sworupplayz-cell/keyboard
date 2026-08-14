package com.sworupplayz.keyboard

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import kotlin.math.max
import kotlin.math.pow

/** Gboard-inspired visual tokens. Typing behavior does not depend on these values. */
enum class KeyVisualRole {
    LETTER,
    SPACE,
    MODIFIER,
    ENTER,
    TOOLBAR
}

data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val text: Int,
    val secondaryText: Int,
    val divider: Int,
    val accent: Int,
    val enterKey: Int,
    val enterText: Int,
    val popupBackground: Int,
    val popupText: Int,
    val shadow: Int
)

object KeyboardTheme {
    const val KEY_CORNER_RADIUS_DP = 8
    const val PREVIEW_CORNER_RADIUS_DP = 10
    const val KEY_HORIZONTAL_GAP_DP = 3
    const val KEY_VERTICAL_GAP_DP = 5
    const val COMPACT_HORIZONTAL_GAP_DP = 2
    const val PREVIEW_WIDTH_DP = 50
    const val PREVIEW_HEIGHT_DP = 58
    const val SHADOW_DP = 1
    const val SUGGESTION_TEXT_SP = 16f
    const val LETTER_TEXT_SP = 21f
    const val COMPACT_LETTER_TEXT_SP = 19f
    const val SPACE_TEXT_SP = 13f
    const val MODIFIER_TEXT_SP = 14f
    const val MINIMUM_CONTRAST = 4.5
    const val WHITE = -1
    const val PRESS_FADE_MS = 60
    const val RELEASE_FADE_MS = 80

    fun palette(dark: Boolean, color: (Int) -> Int): KeyboardPalette = if (dark) {
        KeyboardPalette(
            background = color(R.color.keyboard_dark_background),
            key = color(R.color.keyboard_dark_key),
            specialKey = color(R.color.keyboard_dark_special),
            text = color(R.color.keyboard_dark_text),
            secondaryText = color(R.color.keyboard_dark_hint),
            divider = color(R.color.keyboard_dark_divider),
            accent = color(R.color.accent_dark),
            enterKey = color(R.color.accent_dark),
            enterText = color(R.color.keyboard_dark_background),
            popupBackground = color(R.color.keyboard_dark_popup),
            popupText = color(R.color.keyboard_dark_text),
            shadow = color(R.color.keyboard_dark_shadow)
        )
    } else {
        KeyboardPalette(
            background = color(R.color.keyboard_light_background),
            key = color(R.color.keyboard_light_key),
            specialKey = color(R.color.keyboard_light_special),
            text = color(R.color.keyboard_light_text),
            secondaryText = color(R.color.keyboard_light_hint),
            divider = color(R.color.keyboard_light_divider),
            accent = color(R.color.accent),
            enterKey = color(R.color.accent),
            enterText = color(R.color.enter_text),
            popupBackground = color(R.color.keyboard_light_popup),
            popupText = color(R.color.keyboard_light_text),
            shadow = color(R.color.keyboard_light_shadow)
        )
    }

    fun fillColor(role: KeyVisualRole, palette: KeyboardPalette, active: Boolean): Int = when {
        active -> palette.accent
        role == KeyVisualRole.ENTER -> palette.enterKey
        role == KeyVisualRole.MODIFIER || role == KeyVisualRole.TOOLBAR -> palette.specialKey
        else -> palette.key
    }

    fun textColor(role: KeyVisualRole, palette: KeyboardPalette, active: Boolean): Int = when {
        active -> WHITE
        role == KeyVisualRole.ENTER -> palette.enterText
        else -> palette.text
    }

    fun pressedColor(color: Int): Int {
        val isDark = red(color) + green(color) + blue(color) < 384
        val factor = if (isDark) 1.16f else 0.92f
        return rgb(
            (red(color) * factor).toInt().coerceIn(0, 255),
            (green(color) * factor).toInt().coerceIn(0, 255),
            (blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    fun keyBackground(
        color: Int,
        radiusPx: Float,
        shadowColor: Int,
        shadowPx: Int,
        borderColor: Int? = null,
        pressedEnabled: Boolean = true
    ): StateListDrawable {
        return StateListDrawable().apply {
            setEnterFadeDuration(PRESS_FADE_MS)
            setExitFadeDuration(RELEASE_FADE_MS)
            val rest = surface(color, radiusPx, shadowColor, shadowPx, borderColor)
            val pressed = if (pressedEnabled) {
                surface(pressedColor(color), radiusPx, shadowColor, shadowPx, borderColor)
            } else {
                rest
            }
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), pressed)
            addState(IntArray(0), rest)
        }
    }

    fun flatKeyBackground(color: Int, radiusPx: Float): StateListDrawable {
        return StateListDrawable().apply {
            setEnterFadeDuration(PRESS_FADE_MS)
            setExitFadeDuration(RELEASE_FADE_MS)
            addState(
                intArrayOf(android.R.attr.state_pressed),
                roundedRect(pressedColor(color), radiusPx)
            )
            addState(
                intArrayOf(android.R.attr.state_focused),
                roundedRect(pressedColor(color), radiusPx)
            )
            addState(IntArray(0), roundedRect(color, radiusPx))
        }
    }

    fun roundedRect(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokeColor != null && strokePx > 0) {
                setStroke(strokePx, strokeColor)
            }
        }
    }

    fun contrastRatio(foreground: Int, background: Int): Double {
        val fg = relativeLuminance(foreground) + 0.05
        val bg = relativeLuminance(background) + 0.05
        return max(fg, bg) / max(minOf(fg, bg), 0.0001)
    }

    private fun surface(
        color: Int,
        radiusPx: Float,
        shadowColor: Int,
        shadowPx: Int,
        borderColor: Int?
    ): android.graphics.drawable.Drawable {
        val fill = roundedRect(color, radiusPx, borderColor, if (borderColor != null) 1 else 0)
        if (shadowPx <= 0) return fill
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(shadowColor)
        }
        return LayerDrawable(arrayOf(shadow, fill)).apply {
            setLayerInset(0, 0, shadowPx, 0, 0)
            setLayerInset(1, 0, 0, 0, shadowPx)
        }
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red(color)) +
            0.7152 * channel(green(color)) +
            0.0722 * channel(blue(color))
    }

    private fun red(color: Int): Int = (color ushr 16) and 0xFF
    private fun green(color: Int): Int = (color ushr 8) and 0xFF
    private fun blue(color: Int): Int = color and 0xFF
    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
