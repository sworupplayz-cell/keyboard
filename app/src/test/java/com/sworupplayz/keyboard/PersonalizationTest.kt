package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationTest {
    @Test
    fun toolbarDefaultsMatchTheCollapsedStrip() {
        val config = ToolbarConfiguration.defaults()
        assertEquals(
            listOf(
                ToolbarAction.EMOJI,
                ToolbarAction.CLIPBOARD,
                ToolbarAction.LANGUAGE,
                ToolbarAction.SETTINGS,
                ToolbarAction.MORE
            ),
            config.visibleCollapsed(6)
        )
        assertTrue(config.alwaysVisible)
        assertTrue(config.autoCollapse)
    }

    @Test
    fun toolbarCanBeCustomizedReorderedAndRestored() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        var config = repository.toolbarConfiguration()
            .toggle(ToolbarAction.HANDWRITING)
            .move(ToolbarAction.EMOJI, 1)
        repository.saveToolbarConfiguration(config)
        val restored = KeyboardSettingsRepository(storage).toolbarConfiguration()
        assertEquals(config.order, restored.order)
        assertTrue(restored.isEnabled(ToolbarAction.HANDWRITING))
        assertEquals(ToolbarAction.CLIPBOARD, restored.order.first())

        repository.restoreDefaultToolbar()
        val defaults = KeyboardSettingsRepository(storage).toolbarConfiguration()
        assertEquals(ToolbarConfiguration.DEFAULT_ORDER, defaults.visibleCollapsed(8))
    }

    @Test
    fun smallScreensHideOverflowUnderMore() {
        val config = ToolbarConfiguration.defaults().toggle(ToolbarAction.NUMBERS).toggle(ToolbarAction.SYMBOLS)
        val visible = config.visibleCollapsed(KeyboardUiMetrics.maxToolbarItems(320))
        assertTrue(visible.size <= 4)
        assertEquals(ToolbarAction.MORE, visible.last())
        assertTrue(config.overflow(4).isNotEmpty())
    }

    @Test
    fun languageSwitcherCyclesAndResolvesPickerLabels() {
        assertEquals(KeyboardLanguage.NEPALI, LanguageSwitcher.cycle(KeyboardLanguage.ENGLISH))
        assertEquals(KeyboardLanguage.ROMAN, LanguageSwitcher.cycle(KeyboardLanguage.NEPALI))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageSwitcher.cycle(KeyboardLanguage.ROMAN))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageSwitcher.fromPickerLabel("English"))
        assertEquals(KeyboardLanguage.NEPALI, LanguageSwitcher.fromPickerLabel("नेपाली"))
        assertEquals(KeyboardLanguage.ROMAN, LanguageSwitcher.fromPickerLabel("Roman Nepali"))
        assertNull(LanguageSwitcher.fromPickerLabel("not-a-language"))
        assertEquals(listOf("English", "नेपाली", "Roman Nepali"), LanguageSwitcher.pickerLabels())
    }

    @Test
    fun suggestionBarStaysCleanWhenThereIsNothingUseful() {
        assertTrue(SuggestionBarState.display(emptyList(), "").isEmpty())
        assertTrue(SuggestionBarState.display(listOf("aa"), "aa").isEmpty())
        assertEquals(listOf("hello", "help"), SuggestionBarState.display(listOf("hello", "help", "hello"), "he"))
        assertEquals(listOf("Sworup"), SuggestionBarState.display(listOf("Sworup"), "Sworup"))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
    }

    @Test
    fun oneHandedModeNarrowsWithoutChangingTypingLogic() {
        assertEquals(KeyboardPresentationMode.NORMAL, OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.NORMAL))
        assertEquals(KeyboardPresentationMode.ONE_HANDED, OneHandedLayoutPolicy.presentation(OneHandedAlignment.LEFT, KeyboardPresentationMode.NORMAL))
        assertEquals(KeyboardPresentationMode.NORMAL, OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.FLOATING))
        val left = OneHandedLayoutPolicy.insets(360, OneHandedAlignment.LEFT)
        val right = OneHandedLayoutPolicy.insets(360, OneHandedAlignment.RIGHT)
        val center = OneHandedLayoutPolicy.insets(360, OneHandedAlignment.CENTER)
        assertEquals(0, left.startDp)
        assertTrue(left.endDp > 0)
        assertEquals(0, right.endDp)
        assertTrue(right.startDp > 0)
        assertTrue(center.startDp > 0 && center.endDp > 0)
        assertTrue(left.contentWidthDp < 360)
        val compact = OneHandedLayoutPolicy.insets(320, OneHandedAlignment.LEFT)
        assertEquals(0, compact.startDp)
        assertEquals(0, compact.endDp)
        assertEquals(320, compact.contentWidthDp)
        assertEquals("hello", "hello")
        assertEquals("नमस्ते", "नमस्ते")
    }

    @Test
    fun themeTokensStayOnTheSharedPalette() {
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
        assertEquals(light.background, KeyboardThemeTokens.gutter(light))
        assertEquals(light.text, KeyboardThemeTokens.suggestionPrimary(light))
        assertEquals(light.specialKey, KeyboardThemeTokens.toolbarFill(light))
        assertTrue(KeyboardAppearance.SYSTEM.isDark(systemIsDark = true))
        assertFalse(KeyboardAppearance.LIGHT.isDark(systemIsDark = true))
    }

    @Test
    fun privacyClearRemovesLocalDataOnly() {
        val storage = FakeSettingsStorage().apply {
            putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
            putString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, "1\thi")
            putString(KeyboardPreferences.KEY_RECENT_EMOJIS, "😊")
            putBoolean(KeyboardPreferences.KEY_SUGGESTIONS, false)
        }
        KeyboardSettingsRepository(storage).clearLocalData()
        KeyboardPreferences.LOCAL_DATA_KEYS.forEach { assertFalse(storage.contains(it)) }
        assertFalse(KeyboardSettingsRepository(storage).load().suggestions)
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
