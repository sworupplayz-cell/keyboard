package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeTest {
    @Test
    fun lightLetterKeysKeepReadableContrast() {
        val text = 0xFF202124.toInt()
        val key = 0xFFFFFFFF.toInt()
        val background = 0xFFE8EAED.toInt()
        assertTrue(KeyboardTheme.contrastRatio(text, key) >= KeyboardTheme.MINIMUM_CONTRAST)
        assertTrue(KeyboardTheme.contrastRatio(text, background) >= 7.0)
    }

    @Test
    fun darkLetterKeysKeepReadableContrast() {
        val text = 0xFFE8EAED.toInt()
        val key = 0xFF3C4043.toInt()
        assertTrue(KeyboardTheme.contrastRatio(text, key) >= KeyboardTheme.MINIMUM_CONTRAST)
    }

    @Test
    fun enterAndActiveKeysUseAccentFillWithContrastingLabel() {
        val light = KeyboardPalette(
            background = 0xFFE8EAED.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFD3D7DE.toInt(),
            text = 0xFF202124.toInt(),
            secondaryText = 0xFF5F6368.toInt(),
            divider = 0xFFDADCE0.toInt(),
            accent = 0xFF1A73E8.toInt(),
            enterKey = 0xFF1A73E8.toInt(),
            enterText = KeyboardTheme.WHITE,
            popupBackground = KeyboardTheme.WHITE,
            popupText = 0xFF202124.toInt(),
            shadow = 0x33000000
        )
        assertEquals(light.enterKey, KeyboardTheme.fillColor(KeyVisualRole.ENTER, light, active = false))
        assertEquals(light.enterText, KeyboardTheme.textColor(KeyVisualRole.ENTER, light, active = false))
        assertEquals(KeyboardTheme.WHITE, KeyboardTheme.textColor(KeyVisualRole.LETTER, light, active = true))
        assertTrue(KeyboardTheme.contrastRatio(light.enterText, light.enterKey) >= KeyboardTheme.MINIMUM_CONTRAST)
    }

    @Test
    fun pressedColorStaysDistinctFromTheRestingFill() {
        val white = KeyboardTheme.WHITE
        val dark = 0xFF3C4043.toInt()
        assertTrue(KeyboardTheme.pressedColor(white) != white)
        assertTrue(KeyboardTheme.pressedColor(dark) != dark)
    }
}
