package com.sworupplayz.keyboard

enum class KeyboardVisualTheme {
    FOLLOW_APPEARANCE,
    DEFAULT_LIGHT,
    DEFAULT_DARK,
    BLUE,
    GREEN,
    PURPLE,
    HIGH_CONTRAST;

    companion object {
        fun fromStored(value: String?): KeyboardVisualTheme =
            entries.firstOrNull { it.name == value } ?: FOLLOW_APPEARANCE
    }
}

enum class KeyDensity {
    COMPACT,
    NORMAL,
    COMFORTABLE;

    companion object {
        fun fromStored(value: String?): KeyDensity =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class KeySpacing {
    COMPACT,
    NORMAL,
    COMFORTABLE;

    companion object {
        fun fromStored(value: String?): KeySpacing =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class KeyCornerStyle {
    TIGHT,
    NORMAL,
    ROUND;

    companion object {
        fun fromStored(value: String?): KeyCornerStyle =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class SoundVolume {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromStored(value: String?): SoundVolume =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class HapticStrength {
    LIGHT,
    MEDIUM,
    STRONG;

    companion object {
        fun fromStored(value: String?): HapticStrength =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class ColorPreset {
    THEME,
    PAPER,
    INK,
    MIDNIGHT;

    companion object {
        fun fromStored(value: String?): ColorPreset =
            entries.firstOrNull { it.name == value } ?: THEME
    }
}

data class ThemeStyle(
    val shadows: Boolean = true,
    val borders: Boolean = false,
    val pressedHighlight: Boolean = true
)

/**
 * Central palettes and safe color overlays. Values are ARGB so tests do not
 * need Android resources. KeyboardService still uses the same [KeyboardPalette].
 */
object AppearanceCatalog {
    const val LIGHT_BACKGROUND = 0xFFE8EAED.toInt()
    const val LIGHT_KEY = 0xFFFFFFFF.toInt()
    const val LIGHT_SPECIAL = 0xFFD3D7DE.toInt()
    const val LIGHT_TEXT = 0xFF202124.toInt()
    const val LIGHT_HINT = 0xFF5F6368.toInt()
    const val LIGHT_DIVIDER = 0xFFDADCE0.toInt()
    const val LIGHT_ACCENT = 0xFF1A73E8.toInt()
    const val LIGHT_SHADOW = 0x33000000
    const val DARK_BACKGROUND = 0xFF1F1F1F.toInt()
    const val DARK_KEY = 0xFF3C4043.toInt()
    const val DARK_SPECIAL = 0xFF2D2E31.toInt()
    const val DARK_TEXT = 0xFFE8EAED.toInt()
    const val DARK_HINT = 0xFF9AA0A6.toInt()
    const val DARK_DIVIDER = 0xFF3C4043.toInt()
    const val DARK_ACCENT = 0xFF8AB4F8.toInt()
    const val DARK_SHADOW = 0x66000000

    fun resolve(
        visual: KeyboardVisualTheme,
        appearance: KeyboardAppearance,
        systemIsDark: Boolean,
        preset: ColorPreset = ColorPreset.THEME
    ): KeyboardPalette {
        val dark = appearance.isDark(systemIsDark)
        val base = when (visual) {
            KeyboardVisualTheme.FOLLOW_APPEARANCE -> if (dark) darkDefault() else lightDefault()
            KeyboardVisualTheme.DEFAULT_LIGHT -> lightDefault()
            KeyboardVisualTheme.DEFAULT_DARK -> darkDefault()
            KeyboardVisualTheme.BLUE -> if (dark) blueDark() else blueLight()
            KeyboardVisualTheme.GREEN -> if (dark) greenDark() else greenLight()
            KeyboardVisualTheme.PURPLE -> if (dark) purpleDark() else purpleLight()
            KeyboardVisualTheme.HIGH_CONTRAST -> if (dark) highContrastDark() else highContrastLight()
        }
        return applyPreset(base, preset)
    }

    fun applyPreset(base: KeyboardPalette, preset: ColorPreset): KeyboardPalette = when (preset) {
        ColorPreset.THEME -> base
        ColorPreset.PAPER -> sanitize(
            base.copy(
                background = LIGHT_BACKGROUND,
                key = LIGHT_KEY,
                specialKey = LIGHT_SPECIAL,
                text = LIGHT_TEXT,
                secondaryText = LIGHT_HINT,
                divider = LIGHT_DIVIDER,
                popupBackground = LIGHT_KEY,
                popupText = LIGHT_TEXT
            )
        )
        ColorPreset.INK -> sanitize(
            base.copy(
                background = 0xFF121212.toInt(),
                key = 0xFF1E1E1E.toInt(),
                specialKey = 0xFF2A2A2A.toInt(),
                text = 0xFFF1F3F4.toInt(),
                secondaryText = 0xFFBDC1C6.toInt(),
                divider = 0xFF3C4043.toInt(),
                popupBackground = 0xFF2A2A2A.toInt(),
                popupText = 0xFFF1F3F4.toInt()
            )
        )
        ColorPreset.MIDNIGHT -> sanitize(
            base.copy(
                background = 0xFF000000.toInt(),
                key = 0xFF111111.toInt(),
                specialKey = 0xFF1A1A1A.toInt(),
                text = KeyboardTheme.WHITE,
                secondaryText = 0xFFE8EAED.toInt(),
                divider = 0xFF5F6368.toInt(),
                popupBackground = 0xFF1A1A1A.toInt(),
                popupText = KeyboardTheme.WHITE
            )
        )
    }

    fun sanitize(palette: KeyboardPalette): KeyboardPalette {
        val textOnKey = if (readable(palette.text, palette.key)) palette.text else contrastingText(palette.key)
        val textOnBoard = if (readable(palette.text, palette.background)) palette.text else contrastingText(palette.background)
        val enterText = if (readable(palette.enterText, palette.enterKey)) {
            palette.enterText
        } else {
            contrastingText(palette.enterKey)
        }
        return palette.copy(
            text = textOnKey,
            secondaryText = if (readable(palette.secondaryText, palette.background)) {
                palette.secondaryText
            } else {
                textOnBoard
            },
            enterText = enterText,
            popupText = if (readable(palette.popupText, palette.popupBackground)) {
                palette.popupText
            } else {
                contrastingText(palette.popupBackground)
            }
        )
    }

    fun readable(foreground: Int, background: Int): Boolean =
        KeyboardTheme.contrastRatio(foreground, background) >= KeyboardTheme.MINIMUM_CONTRAST

    fun highContrast(foreground: Int, background: Int): Boolean =
        KeyboardTheme.contrastRatio(foreground, background) >= 7.0

    private fun contrastingText(background: Int): Int {
        val light = KeyboardTheme.contrastRatio(KeyboardTheme.WHITE, background)
        val dark = KeyboardTheme.contrastRatio(LIGHT_TEXT, background)
        return if (light >= dark) KeyboardTheme.WHITE else LIGHT_TEXT
    }

    fun lightDefault(): KeyboardPalette = KeyboardPalette(
        background = LIGHT_BACKGROUND,
        key = LIGHT_KEY,
        specialKey = LIGHT_SPECIAL,
        text = LIGHT_TEXT,
        secondaryText = LIGHT_HINT,
        divider = LIGHT_DIVIDER,
        accent = LIGHT_ACCENT,
        enterKey = LIGHT_ACCENT,
        enterText = KeyboardTheme.WHITE,
        popupBackground = LIGHT_KEY,
        popupText = LIGHT_TEXT,
        shadow = LIGHT_SHADOW
    )

    fun darkDefault(): KeyboardPalette = KeyboardPalette(
        background = DARK_BACKGROUND,
        key = DARK_KEY,
        specialKey = DARK_SPECIAL,
        text = DARK_TEXT,
        secondaryText = DARK_HINT,
        divider = DARK_DIVIDER,
        accent = DARK_ACCENT,
        enterKey = DARK_ACCENT,
        enterText = DARK_BACKGROUND,
        popupBackground = DARK_KEY,
        popupText = DARK_TEXT,
        shadow = DARK_SHADOW
    )

    private fun blueLight(): KeyboardPalette = lightDefault().copy(
        background = 0xFFD7E3FC.toInt(),
        specialKey = 0xFFB6CCF8.toInt(),
        accent = 0xFF1A73E8.toInt(),
        enterKey = 0xFF1A73E8.toInt()
    )

    private fun blueDark(): KeyboardPalette = darkDefault().copy(
        background = 0xFF0B1C33.toInt(),
        key = 0xFF16324F.toInt(),
        specialKey = 0xFF10263D.toInt(),
        accent = 0xFF8AB4F8.toInt(),
        enterKey = 0xFF8AB4F8.toInt(),
        enterText = 0xFF0B1C33.toInt()
    )

    private fun greenLight(): KeyboardPalette = lightDefault().copy(
        background = 0xFFD9EDE0.toInt(),
        specialKey = 0xFFB7DCC4.toInt(),
        accent = 0xFF188038.toInt(),
        enterKey = 0xFF188038.toInt()
    )

    private fun greenDark(): KeyboardPalette = darkDefault().copy(
        background = 0xFF0F2418.toInt(),
        key = 0xFF1A3A27.toInt(),
        specialKey = 0xFF142C1E.toInt(),
        accent = 0xFF81C995.toInt(),
        enterKey = 0xFF81C995.toInt(),
        enterText = 0xFF0F2418.toInt()
    )

    private fun purpleLight(): KeyboardPalette = lightDefault().copy(
        background = 0xFFE6DFF5.toInt(),
        specialKey = 0xFFD0C4EC.toInt(),
        accent = 0xFF7627BB.toInt(),
        enterKey = 0xFF7627BB.toInt()
    )

    private fun purpleDark(): KeyboardPalette = darkDefault().copy(
        background = 0xFF1B1028.toInt(),
        key = 0xFF2C1B40.toInt(),
        specialKey = 0xFF221433.toInt(),
        accent = 0xFFD7AEFB.toInt(),
        enterKey = 0xFFD7AEFB.toInt(),
        enterText = 0xFF1B1028.toInt()
    )

    private fun highContrastLight(): KeyboardPalette = KeyboardPalette(
        background = KeyboardTheme.WHITE,
        key = KeyboardTheme.WHITE,
        specialKey = 0xFFE8EAED.toInt(),
        text = 0xFF000000.toInt(),
        secondaryText = 0xFF202124.toInt(),
        divider = 0xFF000000.toInt(),
        accent = 0xFF000000.toInt(),
        enterKey = 0xFF000000.toInt(),
        enterText = KeyboardTheme.WHITE,
        popupBackground = KeyboardTheme.WHITE,
        popupText = 0xFF000000.toInt(),
        shadow = 0x66000000
    )

    private fun highContrastDark(): KeyboardPalette = KeyboardPalette(
        background = 0xFF000000.toInt(),
        key = 0xFF000000.toInt(),
        specialKey = 0xFF111111.toInt(),
        text = KeyboardTheme.WHITE,
        secondaryText = 0xFFE8EAED.toInt(),
        divider = KeyboardTheme.WHITE,
        accent = 0xFFFFD600.toInt(),
        enterKey = 0xFFFFD600.toInt(),
        enterText = 0xFF000000.toInt(),
        popupBackground = 0xFF000000.toInt(),
        popupText = KeyboardTheme.WHITE,
        shadow = 0x00000000
    )
}
