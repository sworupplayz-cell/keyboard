package com.sworupplayz.keyboard

/**
 * Named colors for every keyboard surface. Callers should use these instead of
 * hard-coded ARGB values so light/dark/system stay consistent.
 */
object KeyboardThemeTokens {
    fun gutter(palette: KeyboardPalette): Int = palette.background
    fun suggestionBackground(palette: KeyboardPalette): Int = palette.background
    fun suggestionPrimary(palette: KeyboardPalette): Int = palette.text
    fun suggestionSecondary(palette: KeyboardPalette): Int = palette.secondaryText
    fun suggestionDivider(palette: KeyboardPalette): Int = palette.divider
    fun suggestionPressed(palette: KeyboardPalette): Int = KeyboardTheme.pressedColor(palette.background)
    fun toolbarFill(palette: KeyboardPalette): Int = palette.specialKey
    fun toolbarSelected(palette: KeyboardPalette): Int = palette.accent
    fun overlayFill(palette: KeyboardPalette): Int = palette.popupBackground
    fun overlayText(palette: KeyboardPalette): Int = palette.popupText
    fun previewFill(palette: KeyboardPalette): Int = palette.popupBackground
    fun previewText(palette: KeyboardPalette): Int = palette.popupText
    fun letterKey(palette: KeyboardPalette): Int = palette.key
    fun modifierKey(palette: KeyboardPalette): Int = palette.specialKey
    fun selectedLabel(palette: KeyboardPalette): Int = KeyboardTheme.WHITE
    fun pressed(color: Int): Int = KeyboardTheme.pressedColor(color)
}
