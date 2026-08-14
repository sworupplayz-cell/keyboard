package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSettingsTest {
    @Test
    fun defaultsAreSimpleAndSafe() {
        val settings = KeyboardSettingsRepository(FakeSettingsStorage()).load()

        assertEquals(DefaultKeyboardMode.ENGLISH, settings.defaultMode)
        assertEquals(KeyboardAppearance.SYSTEM, settings.appearance)
        assertEquals(KeyboardHeight.NORMAL, settings.height)
        assertTrue(settings.suggestions)
        assertTrue(settings.learnedWords)
        assertFalse(settings.numberRow)
        assertFalse(settings.keySound)
        assertFalse(settings.keyVibration)
        assertTrue(settings.smartPunctuation)
        assertTrue(settings.doubleSpacePeriod)
        assertTrue(settings.autoCapitalization)
        assertTrue(settings.emojiRecents)
        assertTrue(settings.toolbar)
        assertTrue(settings.clipboardHistory)
    }

    @Test
    fun defaultModePersistsAndPolicyKeepsSubtypeFallback() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        assertNull(repository.savedDefaultMode())
        assertEquals(KeyboardLanguage.NEPALI, KeyboardModePolicy.initialLanguage(null, "ne_NP"))

        repository.setDefaultMode(DefaultKeyboardMode.ROMAN)
        val recreated = KeyboardSettingsRepository(storage)
        assertEquals(DefaultKeyboardMode.ROMAN, recreated.savedDefaultMode())
        assertEquals(
            KeyboardLanguage.ROMAN,
            KeyboardModePolicy.initialLanguage(recreated.savedDefaultMode(), "en_US")
        )
    }

    @Test
    fun suggestionsAndLearnedWordsSettingsPersistIndependently() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        repository.setSuggestions(false)
        repository.setLearnedWords(false)

        val recreated = KeyboardSettingsRepository(storage).load()
        assertFalse(recreated.suggestions)
        assertFalse(recreated.learnedWords)
    }

    @Test
    fun disablingLearningDoesNotDeleteExistingMappings() {
        val storage = FakeSettingsStorage().apply {
            putString(KeyboardPreferences.KEY_LEARNED_ROMAN, "zorp\tजोर्प")
        }
        KeyboardSettingsRepository(storage).setLearnedWords(false)

        assertEquals("zorp\tजोर्प", storage.getString(KeyboardPreferences.KEY_LEARNED_ROMAN))
    }

    @Test
    fun feedbackSettingsPersist() {
        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setKeySound(true)
            setKeyVibration(true)
        }

        val recreated = KeyboardSettingsRepository(storage).load()
        assertTrue(recreated.keySound)
        assertTrue(recreated.keyVibration)
    }

    @Test
    fun numberRowAndKeyboardHeightPersist() {
        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setNumberRow(true)
            setHeight(KeyboardHeight.LARGE)
        }

        val recreated = KeyboardSettingsRepository(storage).load()
        assertTrue(recreated.numberRow)
        assertEquals(KeyboardHeight.LARGE, recreated.height)
    }

    @Test
    fun appearanceSupportsSystemLightDarkAndLegacyMigration() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        KeyboardAppearance.entries.forEach { appearance ->
            repository.setAppearance(appearance)
            assertEquals(appearance, KeyboardSettingsRepository(storage).load().appearance)
        }
        assertTrue(KeyboardAppearance.SYSTEM.isDark(systemIsDark = true))
        assertFalse(KeyboardAppearance.SYSTEM.isDark(systemIsDark = false))
        assertFalse(KeyboardAppearance.LIGHT.isDark(systemIsDark = true))
        assertTrue(KeyboardAppearance.DARK.isDark(systemIsDark = false))

        val legacy = FakeSettingsStorage().apply {
            putBoolean(KeyboardPreferences.KEY_DARK_LEGACY, true)
        }
        assertEquals(KeyboardAppearance.DARK, KeyboardSettingsRepository(legacy).load().appearance)
    }

    @Test
    fun clearLearnedWordsDeletesOnlyLearnedMappings() {
        val storage = FakeSettingsStorage().apply {
            putString(KeyboardPreferences.KEY_LEARNED_ROMAN, "roman")
            putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "english")
            putString(KeyboardPreferences.KEY_LEARNED_NEPALI, "nepali")
            putString(KeyboardPreferences.KEY_RECENT_EMOJIS, "😊")
            putBoolean(KeyboardPreferences.KEY_SUGGESTIONS, false)
        }
        KeyboardSettingsRepository(storage).clearLearnedWords()

        KeyboardPreferences.LEARNED_WORD_KEYS.forEach { assertFalse(storage.contains(it)) }
        assertEquals("😊", storage.getString(KeyboardPreferences.KEY_RECENT_EMOJIS))
        assertFalse(KeyboardSettingsRepository(storage).load().suggestions)
    }

    @Test
    fun clearRecentEmojiRemovesOnlyPanelHistory() {
        val storage = FakeSettingsStorage().apply {
            putString(KeyboardPreferences.KEY_RECENT_EMOJIS, "😊")
            putString(KeyboardPreferences.KEY_EMOJI_USAGE, "😊\t2")
            putString(KeyboardPreferences.KEY_RECENT_SYMBOLS, "+")
            putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
        }
        KeyboardSettingsRepository(storage).clearRecentEmojiAndSymbols()
        assertFalse(storage.contains(KeyboardPreferences.KEY_RECENT_EMOJIS))
        assertFalse(storage.contains(KeyboardPreferences.KEY_EMOJI_USAGE))
        assertFalse(storage.contains(KeyboardPreferences.KEY_RECENT_SYMBOLS))
        assertEquals("hello\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
    }

    @Test
    fun everySettingSurvivesRepositoryRecreation() {
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
