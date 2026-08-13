package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase20AppearanceTest {
    @Test
    fun newAppearanceDefaultsStaySafe() {
        val settings = KeyboardSettings()
        assertEquals(KeyboardVisualTheme.FOLLOW_APPEARANCE, settings.visualTheme)
        assertEquals(ColorPreset.THEME, settings.colorPreset)
        assertEquals(KeyDensity.NORMAL, settings.keyDensity)
        assertEquals(KeySpacing.NORMAL, settings.keySpacing)
        assertEquals(KeyCornerStyle.NORMAL, settings.keyCorner)
        assertTrue(settings.keyShadows)
        assertFalse(settings.keyBorders)
        assertTrue(settings.pressedHighlight)
        assertEquals(SoundVolume.MEDIUM, settings.soundVolume)
        assertEquals(HapticStrength.MEDIUM, settings.hapticStrength)
        assertEquals(KeyboardHeight.NORMAL, settings.height)
        assertFalse(settings.numberRow)
    }

    @Test
    fun appearanceSettingsSurviveRepositoryRecreation() {
        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setVisualTheme(KeyboardVisualTheme.PURPLE)
            setColorPreset(ColorPreset.INK)
            setKeyDensity(KeyDensity.COMFORTABLE)
            setKeySpacing(KeySpacing.COMPACT)
            setKeyCorner(KeyCornerStyle.ROUND)
            setKeyShadows(false)
            setKeyBorders(true)
            setPressedHighlight(false)
            setSoundVolume(SoundVolume.HIGH)
            setHapticStrength(HapticStrength.STRONG)
            setHeight(KeyboardHeight.LARGE)
            setNumberRow(true)
            setOneHanded(OneHandedAlignment.RIGHT)
        }

        val restored = KeyboardSettingsRepository(storage).load()
        assertEquals(KeyboardVisualTheme.PURPLE, restored.visualTheme)
        assertEquals(ColorPreset.INK, restored.colorPreset)
        assertEquals(KeyDensity.COMFORTABLE, restored.keyDensity)
        assertEquals(KeySpacing.COMPACT, restored.keySpacing)
        assertEquals(KeyCornerStyle.ROUND, restored.keyCorner)
        assertFalse(restored.keyShadows)
        assertTrue(restored.keyBorders)
        assertFalse(restored.pressedHighlight)
        assertEquals(SoundVolume.HIGH, restored.soundVolume)
        assertEquals(HapticStrength.STRONG, restored.hapticStrength)
        assertEquals(KeyboardHeight.LARGE, restored.height)
        assertTrue(restored.numberRow)
        assertEquals(OneHandedAlignment.RIGHT, restored.oneHanded)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        assertEquals(KeyboardVisualTheme.FOLLOW_APPEARANCE, KeyboardVisualTheme.fromStored("neon"))
        assertEquals(ColorPreset.THEME, ColorPreset.fromStored(null))
        assertEquals(KeyDensity.NORMAL, KeyDensity.fromStored("huge"))
        assertEquals(KeySpacing.NORMAL, KeySpacing.fromStored(""))
        assertEquals(KeyCornerStyle.NORMAL, KeyCornerStyle.fromStored(null))
        assertEquals(SoundVolume.MEDIUM, SoundVolume.fromStored("loud"))
        assertEquals(HapticStrength.MEDIUM, HapticStrength.fromStored("max"))
    }

    @Test
    fun everyThemeAndPresetKeepsReadableTokens() {
        val appearances = listOf(KeyboardAppearance.LIGHT, KeyboardAppearance.DARK)
        KeyboardVisualTheme.entries.forEach { theme ->
            ColorPreset.entries.forEach { preset ->
                appearances.forEach { appearance ->
                    val palette = AppearanceCatalog.resolve(theme, appearance, appearance == KeyboardAppearance.DARK, preset)
                    assertTrue("$theme $preset $appearance text/key", AppearanceCatalog.readable(palette.text, palette.key))
                    assertTrue("$theme $preset $appearance text/board", AppearanceCatalog.readable(palette.text, palette.background))
                    assertTrue("$theme $preset $appearance enter", AppearanceCatalog.readable(palette.enterText, palette.enterKey))
                    assertTrue("$theme $preset $appearance popup", AppearanceCatalog.readable(palette.popupText, palette.popupBackground))
                    assertEquals(palette.background, KeyboardThemeTokens.gutter(palette))
                    assertEquals(palette.key, KeyboardThemeTokens.letterKey(palette))
                    assertEquals(palette.specialKey, KeyboardThemeTokens.modifierKey(palette))
                    assertEquals(palette.enterKey, KeyboardTheme.fillColor(KeyVisualRole.ENTER, palette, active = false))
                }
            }
        }
    }

    @Test
    fun highContrastExceedsTheNormalMinimum() {
        val light = AppearanceCatalog.resolve(KeyboardVisualTheme.HIGH_CONTRAST, KeyboardAppearance.LIGHT, false)
        val dark = AppearanceCatalog.resolve(KeyboardVisualTheme.HIGH_CONTRAST, KeyboardAppearance.DARK, true)
        assertTrue(AppearanceCatalog.highContrast(light.text, light.key))
        assertTrue(AppearanceCatalog.highContrast(dark.text, dark.key))
        assertNotEquals(light.background, dark.background)
    }

    @Test
    fun unreadableColorOverlaysAreRejected() {
        val unreadable = AppearanceCatalog.lightDefault().copy(
            text = KeyboardTheme.WHITE,
            secondaryText = KeyboardTheme.WHITE,
            enterText = 0xFF1A73E8.toInt(),
            enterKey = 0xFF1A73E8.toInt(),
            popupText = KeyboardTheme.WHITE,
            popupBackground = KeyboardTheme.WHITE
        )
        assertFalse(AppearanceCatalog.readable(unreadable.text, unreadable.key))
        val fixed = AppearanceCatalog.sanitize(unreadable)
        assertTrue(AppearanceCatalog.readable(fixed.text, fixed.key))
        assertTrue(AppearanceCatalog.readable(fixed.enterText, fixed.enterKey))
        assertTrue(AppearanceCatalog.readable(fixed.popupText, fixed.popupBackground))
        assertTrue(AppearanceCatalog.readable(fixed.secondaryText, fixed.background))
    }

    @Test
    fun followAppearanceTracksSystemWhileNamedThemesStayFixed() {
        val followDark = AppearanceCatalog.resolve(
            KeyboardVisualTheme.FOLLOW_APPEARANCE,
            KeyboardAppearance.SYSTEM,
            systemIsDark = true
        )
        val followLight = AppearanceCatalog.resolve(
            KeyboardVisualTheme.FOLLOW_APPEARANCE,
            KeyboardAppearance.SYSTEM,
            systemIsDark = false
        )
        val forcedLight = AppearanceCatalog.resolve(
            KeyboardVisualTheme.DEFAULT_LIGHT,
            KeyboardAppearance.DARK,
            systemIsDark = true
        )
        assertEquals(AppearanceCatalog.DARK_BACKGROUND, followDark.background)
        assertEquals(AppearanceCatalog.LIGHT_BACKGROUND, followLight.background)
        assertEquals(AppearanceCatalog.LIGHT_BACKGROUND, forcedLight.background)
        assertEquals(AppearanceCatalog.LIGHT_ACCENT, forcedLight.accent)
    }

    @Test
    fun heightFormulasStayExactAndDensityIsBounded() {
        assertEquals(46, KeyboardUiMetrics.keyHeightDp(320, 568, landscape = false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, landscape = false))
        assertEquals(44, KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.SMALL))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.NORMAL))
        assertEquals(52, KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.LARGE))
        val compact = KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.NORMAL, KeyDensity.COMPACT)
        val comfortable = KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.NORMAL, KeyDensity.COMFORTABLE)
        assertEquals(46, compact)
        assertEquals(51, comfortable)
        assertTrue(compact < 48 && 48 < comfortable)
        assertTrue(KeyboardUiMetrics.keyHeightDp(640, 360, true, KeyboardHeight.SMALL, KeyDensity.COMPACT) >= 40)
        assertEquals(34, KeyboardUiMetrics.compactNumberRowHeightDp(KeyboardHeight.SMALL))
        assertEquals(38, KeyboardUiMetrics.compactNumberRowHeightDp(KeyboardHeight.LARGE))
    }

    @Test
    fun spacingAndCornersStayBounded() {
        assertEquals(1, KeyboardUiMetrics.keyGapDp(320, KeySpacing.COMPACT))
        assertEquals(1, KeyboardUiMetrics.keyGapDp(320, KeySpacing.NORMAL))
        assertEquals(2, KeyboardUiMetrics.keyGapDp(320, KeySpacing.COMFORTABLE))
        assertEquals(1, KeyboardUiMetrics.keyGapDp(360, KeySpacing.COMPACT))
        assertEquals(2, KeyboardUiMetrics.keyGapDp(360, KeySpacing.NORMAL))
        assertEquals(3, KeyboardUiMetrics.keyGapDp(360, KeySpacing.COMFORTABLE))
        assertEquals(3, KeyboardUiMetrics.cornerRadiusDp(KeyCornerStyle.TIGHT))
        assertEquals(KeyboardTheme.KEY_CORNER_RADIUS_DP, KeyboardUiMetrics.cornerRadiusDp(KeyCornerStyle.NORMAL))
        assertEquals(10, KeyboardUiMetrics.cornerRadiusDp(KeyCornerStyle.ROUND))
    }

    @Test
    fun numberRowStillAttachesToLetterLayouts() {
        val withRow = KeyboardLayouts.english(shifted = false, includeNumberRow = true)
        val withoutRow = KeyboardLayouts.english(shifted = false, includeNumberRow = false)
        val nepali = KeyboardLayouts.nepaliConsonants(includeNumberRow = true)
        assertEquals(withoutRow.size + 1, withRow.size)
        assertTrue(withRow.first().all { it.compact && it.label.first().isDigit() })
        assertTrue(nepali.first().all { it.compact })
        assertEquals(10, withRow.first().size)
    }

    @Test
    fun oneHandedShiftsVisiblyAndFallsBackOnNarrowScreens() {
        val left = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.LEFT)
        val center = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.CENTER)
        val right = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.RIGHT)
        assertEquals(0, left.startDp)
        assertTrue(left.endDp > 0)
        assertTrue(center.startDp > 0 && center.endDp > 0)
        assertEquals(0, right.endDp)
        assertTrue(right.startDp > 0)
        assertEquals(left.contentWidthDp, right.contentWidthDp)
        assertTrue(left.contentWidthDp < 412)
        val narrow = OneHandedLayoutPolicy.insets(320, OneHandedAlignment.RIGHT)
        assertEquals(0, narrow.startDp)
        assertEquals(0, narrow.endDp)
        assertEquals(320, narrow.contentWidthDp)
        assertEquals(
            KeyboardPresentationMode.ONE_HANDED,
            OneHandedLayoutPolicy.presentation(OneHandedAlignment.LEFT, KeyboardPresentationMode.FLOATING)
        )
        assertEquals(
            KeyboardPresentationMode.NORMAL,
            OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.FLOATING)
        )
    }

    @Test
    fun resetAppearanceLeavesTypingDataAndLayoutAlone() {
        val storage = seededStorage()
        KeyboardSettingsRepository(storage).resetAppearance()
        val restored = KeyboardSettingsRepository(storage).load()
        assertEquals(KeyboardVisualTheme.FOLLOW_APPEARANCE, restored.visualTheme)
        assertEquals(ColorPreset.THEME, restored.colorPreset)
        assertEquals(KeyboardHeight.NORMAL, restored.height)
        assertEquals(KeyDensity.NORMAL, restored.keyDensity)
        assertFalse(restored.numberRow)
        assertTrue(restored.keyShadows)
        assertEquals(OneHandedAlignment.RIGHT, restored.oneHanded)
        assertTrue(restored.keySound)
        assertEquals("hello\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
        assertEquals("1\tsaved clip", storage.getString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS))
    }

    @Test
    fun resetLayoutRestoresToolbarWithoutTouchingThemeOrWords() {
        val storage = seededStorage()
        KeyboardSettingsRepository(storage).resetLayout()
        val restored = KeyboardSettingsRepository(storage).load()
        assertEquals(OneHandedAlignment.OFF, restored.oneHanded)
        assertEquals(KeyboardPresentationMode.NORMAL, restored.presentationMode)
        assertEquals(KeyboardVisualTheme.BLUE, restored.visualTheme)
        assertEquals("hello\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
        assertEquals(ToolbarConfiguration.DEFAULT_ORDER, restored.let {
            KeyboardSettingsRepository(storage).toolbarConfiguration().visibleCollapsed(8)
        })
    }

    @Test
    fun resetAllSettingsKeepsLearnedWordsAndClipboard() {
        val storage = seededStorage()
        KeyboardSettingsRepository(storage).resetAllSettings()
        val restored = KeyboardSettingsRepository(storage).load()
        assertEquals(KeyboardSettings(), restored)
        assertEquals("hello\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
        assertEquals("नमस्ते\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_NEPALI))
        assertEquals("ghar\tघर", storage.getString(KeyboardPreferences.KEY_LEARNED_ROMAN))
        assertEquals("1\tsaved clip", storage.getString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS))
        assertEquals("😊", storage.getString(KeyboardPreferences.KEY_RECENT_EMOJIS))
        assertFalse(KeyboardPreferences.ALL_SETTING_KEYS.any { it in KeyboardPreferences.LOCAL_DATA_KEYS })
    }

    @Test
    fun soundAndHapticPoliciesStayLocalAndQuietWhenOff() {
        assertEquals(0.35f, TouchFeedbackPolicy.soundVolume(SoundVolume.LOW), 0.001f)
        assertEquals(0.7f, TouchFeedbackPolicy.soundVolume(SoundVolume.MEDIUM), 0.001f)
        assertEquals(1f, TouchFeedbackPolicy.soundVolume(SoundVolume.HIGH), 0.001f)
        assertEquals(12L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.KEY, HapticStrength.LIGHT))
        assertEquals(18L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.LONG_PRESS))
        assertEquals(28L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.LONG_PRESS, HapticStrength.STRONG))
        assertEquals(0L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.CHROME, HapticStrength.STRONG))
        assertFalse(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = false, FeedbackKind.KEY))
        assertFalse(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.NAVIGATION))
        assertTrue(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.LONG_PRESS))
        assertEquals(70, TouchFeedbackPolicy.vibrationAmplitude(HapticStrength.LIGHT))
        assertEquals(-1, TouchFeedbackPolicy.vibrationAmplitude(HapticStrength.MEDIUM))
        assertEquals(255, TouchFeedbackPolicy.vibrationAmplitude(HapticStrength.STRONG))
    }

    @Test
    fun spaceBarAndModifiersKeepAClearHierarchy() {
        assertEquals("English", KeyboardLanguage.ENGLISH.spaceLabel())
        assertEquals("नेपाली", KeyboardLanguage.NEPALI.spaceLabel())
        assertEquals("Roman", KeyboardLanguage.ROMAN.spaceLabel())
        assertEquals(KeyVisualRole.ENTER, KeyVisuals.role(KeySpec("↵", KeyAction.ENTER)))
        assertEquals(KeyVisualRole.MODIFIER, KeyVisuals.role(KeySpec("⌫", KeyAction.BACKSPACE)))
        assertEquals(KeyVisualRole.MODIFIER, KeyVisuals.role(KeySpec("⇧", KeyAction.SHIFT)))
        assertEquals(KeyVisualRole.MODIFIER, KeyVisuals.role(KeySpec("नेपाली", KeyAction.LANGUAGE)))
        assertEquals(KeyVisualRole.SPACE, KeyVisuals.role(KeySpec("English", KeyAction.SPACE, " ")))
        val palette = AppearanceCatalog.lightDefault()
        assertEquals(palette.enterKey, KeyboardTheme.fillColor(KeyVisualRole.ENTER, palette, false))
        assertEquals(palette.specialKey, KeyboardTheme.fillColor(KeyVisualRole.MODIFIER, palette, false))
        assertEquals(palette.key, KeyboardTheme.fillColor(KeyVisualRole.SPACE, palette, false))
        assertEquals(palette.accent, KeyboardTheme.fillColor(KeyVisualRole.MODIFIER, palette, true))
    }

    @Test
    fun previewPolicySkipsEmojiAndAccessibilityLabelsStayStable() {
        assertTrue(KeyInteractionPolicy.showsPreview(KeySpec("a")))
        assertTrue(KeyInteractionPolicy.showsPreview(KeySpec("क")))
        assertFalse(KeyInteractionPolicy.showsPreview(KeySpec("😊")))
        assertFalse(KeyVisuals.showsPreview(KeySpec("😊")))
        assertFalse(KeyInteractionPolicy.showsPreview(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", primary = true))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Shift", AccessibilityLabels.shift(false))
        assertEquals("Backspace", AccessibilityLabels.key(KeySpec("⌫", KeyAction.BACKSPACE)))
    }

    @Test
    fun constructorWithoutNewFieldsStillMatchesPersistedDefaults() {
        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setDefaultMode(DefaultKeyboardMode.NEPALI)
            setAppearance(KeyboardAppearance.DARK)
            setSuggestions(false)
            setLearnedWords(false)
            setNumberRow(true)
            setKeySound(true)
            setKeyVibration(true)
            setHeight(KeyboardHeight.SMALL)
        }
        assertEquals(
            KeyboardSettings(
                defaultMode = DefaultKeyboardMode.NEPALI,
                appearance = KeyboardAppearance.DARK,
                suggestions = false,
                learnedWords = false,
                numberRow = true,
                keySound = true,
                keyVibration = true,
                height = KeyboardHeight.SMALL
            ),
            KeyboardSettingsRepository(storage).load()
        )
    }

    private fun seededStorage(): FakeSettingsStorage = FakeSettingsStorage().apply {
        putString(KeyboardPreferences.KEY_VISUAL_THEME, KeyboardVisualTheme.BLUE.name)
        putString(KeyboardPreferences.KEY_COLOR_PRESET, ColorPreset.MIDNIGHT.name)
        putString(KeyboardPreferences.KEY_HEIGHT, KeyboardHeight.LARGE.name)
        putString(KeyboardPreferences.KEY_DENSITY, KeyDensity.COMFORTABLE.name)
        putBoolean(KeyboardPreferences.KEY_NUMBER_ROW, true)
        putBoolean(KeyboardPreferences.KEY_SHADOWS, false)
        putBoolean(KeyboardPreferences.KEY_SOUND, true)
        putString(KeyboardPreferences.KEY_ONE_HANDED, OneHandedAlignment.RIGHT.name)
        putString(KeyboardPreferences.KEY_PRESENTATION_MODE, KeyboardPresentationMode.FLOATING.name)
        putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
        putString(KeyboardPreferences.KEY_LEARNED_NEPALI, "नमस्ते\t1")
        putString(KeyboardPreferences.KEY_LEARNED_ROMAN, "ghar\tघर")
        putString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, "1\tsaved clip")
        putString(KeyboardPreferences.KEY_RECENT_EMOJIS, "😊")
    }

    private class FakeSettingsStorage : SettingsStorage {
        private val values = linkedMapOf<String, Any>()

        override fun contains(key: String): Boolean = key in values
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue
        override fun getString(key: String): String? = values[key] as? String
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }
}
