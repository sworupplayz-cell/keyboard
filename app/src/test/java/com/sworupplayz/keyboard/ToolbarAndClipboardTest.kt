package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarAndClipboardTest {
    @Test
    fun collapsedToolbarShowsOnlyAFewTools() {
        val toolbar = ToolbarController()
        val labels = toolbar.items(KeyboardLanguage.ENGLISH).map { it.label }
        assertEquals(listOf("😊", "📋", "⚙", "⋯"), labels)
        assertFalse(toolbar.isExpanded)
        assertTrue(toolbar.items(KeyboardLanguage.ENGLISH).size <= 4)
    }

    @Test
    fun expandedToolbarExposesExistingModesWithoutDuplicatingCollapsedEmoji() {
        val toolbar = ToolbarController()
        toolbar.expand()
        val actions = toolbar.items(KeyboardLanguage.NEPALI).map { it.action }
        assertTrue(ToolbarAction.NUMBERS in actions)
        assertTrue(ToolbarAction.SYMBOLS in actions)
        assertTrue(ToolbarAction.HANDWRITING in actions)
        assertTrue(ToolbarAction.MODE_ENGLISH in actions)
        assertTrue(ToolbarAction.MODE_NEPALI in actions)
        assertTrue(ToolbarAction.MODE_ROMAN in actions)
        assertTrue(ToolbarAction.CLIPBOARD in actions)
        assertTrue(ToolbarAction.SETTINGS in actions)
        assertFalse(ToolbarAction.EMOJI in actions)
        assertTrue(toolbar.items(KeyboardLanguage.NEPALI).first { it.action == ToolbarAction.MODE_NEPALI }.selected)
        assertFalse(toolbar.items(KeyboardLanguage.NEPALI).first { it.action == ToolbarAction.MODE_ENGLISH }.selected)
    }

    @Test
    fun moreTogglesAndBackClosesExpandedToolsFirst() {
        val toolbar = ToolbarController()
        toolbar.toggleMore()
        assertTrue(toolbar.isExpanded)
        assertEquals(ToolbarBackResult.COLLAPSED_TOOLS, toolbar.consumeBack(panelOpen = true))
        assertFalse(toolbar.isExpanded)
        assertEquals(ToolbarBackResult.CLOSED_PANEL, toolbar.consumeBack(panelOpen = true))
        assertEquals(ToolbarBackResult.NONE, toolbar.consumeBack(panelOpen = false))
    }

    @Test
    fun toolbarActionsRouteToExistingFeatures() {
        assertEquals(KeyAction.EMOJI, ToolbarController.keyAction(ToolbarAction.EMOJI))
        assertEquals(KeyAction.NUMBERS, ToolbarController.keyAction(ToolbarAction.NUMBERS))
        assertEquals(KeyAction.SYMBOLS, ToolbarController.keyAction(ToolbarAction.SYMBOLS))
        assertEquals(KeyAction.HANDWRITING, ToolbarController.keyAction(ToolbarAction.HANDWRITING))
        assertEquals(KeyAction.SETTINGS, ToolbarController.keyAction(ToolbarAction.SETTINGS))
        assertEquals(KeyAction.MODE_ROMAN, ToolbarController.keyAction(ToolbarAction.MODE_ROMAN))
        assertTrue(ToolbarController().routesToExistingPanel(ToolbarAction.EMOJI))
        assertTrue(ToolbarController().routesToExistingPanel(ToolbarAction.NUMBERS))
        assertFalse(ToolbarController().routesToExistingPanel(ToolbarAction.MORE))
    }

    @Test
    fun clipboardHistoryIsBoundedAndSupportsDeleteAndClear() {
        val store = ClipboardRepository(limit = 2)
        assertTrue(store.record("Hello everyone"))
        assertTrue(store.record("नमस्ते साथीहरू"))
        assertTrue(store.record("ma ghar jaanchu"))
        assertEquals(2, store.values().size)
        assertEquals("ma ghar jaanchu", store.values().first().text)
        assertFalse(store.values().any { it.text == "Hello everyone" })

        val keep = store.values().first().id
        assertTrue(store.delete(keep))
        assertEquals(1, store.values().size)
        store.clear()
        assertTrue(store.isEmpty())
    }

    @Test
    fun clipboardKeepsUnicodeNepaliAndEmojiAndSurvivesSerialization() {
        val store = ClipboardRepository()
        assertTrue(store.record("नमस्ते\nसाथै 😊"))
        assertTrue(store.record("I am घर"))
        val restored = ClipboardRepository.fromSerialized(store.serialize())
        assertEquals(listOf("I am घर", "नमस्ते\nसाथै 😊"), restored.values().map { it.text })
    }

    @Test
    fun clipboardFiltersPasswordsTokensAndHugeOrBinaryText() {
        val store = ClipboardRepository()
        assertFalse(store.record("password=hunter2"))
        assertFalse(store.record("Authorization: Bearer abcdefghijklmnop"))
        assertFalse(ClipboardPolicy.looksSensitive("hello"))
        assertTrue(ClipboardPolicy.looksSensitive("api_key=abcd1234"))
        assertTrue(ClipboardPolicy.looksSensitive("aaaaaaaa.bbbbbbbb.cccccccc"))
        assertFalse(store.record("x".repeat(ClipboardPolicy.MAX_TEXT_LENGTH + 1)))
        assertFalse(store.record("secret\u0000token"))
        assertTrue(store.record("Hello everyone"))
        assertEquals(1, store.values().size)
    }

    @Test
    fun clipboardInsertFinishesComposingThenPastesRawText() {
        var finished = false
        var inserted = ""
        assertTrue(
            ClipboardInsertion.prepareThenInsert(
                hasComposingText = true,
                finishComposing = { finished = true },
                insert = { text -> inserted = text; true },
                text = "ma ghar jaanchu"
            )
        )
        assertTrue(finished)
        assertEquals("ma ghar jaanchu", inserted)
        assertFalse(
            ClipboardInsertion.prepareThenInsert(
                hasComposingText = false,
                finishComposing = { finished = false },
                insert = { false },
                text = ""
            )
        )
    }

    @Test
    fun toolbarAndClipboardSettingsDefaultOnAndPersist() {
        val settings = KeyboardSettingsRepository(FakeSettingsStorage()).load()
        assertTrue(settings.toolbar)
        assertTrue(settings.clipboardHistory)

        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setToolbar(false)
            setClipboardHistory(false)
        }
        val restored = KeyboardSettingsRepository(storage).load()
        assertFalse(restored.toolbar)
        assertFalse(restored.clipboardHistory)

        storage.putString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, "1\thello")
        KeyboardSettingsRepository(storage).clearClipboardHistory()
        assertFalse(storage.contains(KeyboardPreferences.KEY_CLIPBOARD_ITEMS))
    }

    @Test
    fun lightAndDarkPalettesStayAvailableForToolbarChrome() {
        val light = KeyboardTheme.palette(dark = false) { if (it == 0) 0xFFFFFFFF.toInt() else it }
        val dark = KeyboardTheme.palette(dark = true) { if (it == 0) 0xFF000000.toInt() else it }
        assertTrue(KeyboardTheme.contrastRatio(light.text, light.background) >= 1.0)
        assertTrue(KeyboardTheme.contrastRatio(dark.text, dark.background) >= 1.0)
        assertTrue(KeyboardAppearance.LIGHT.isDark(systemIsDark = true).not())
        assertTrue(KeyboardAppearance.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun toolbarHeightStaysCompactOnSmallAndLandscapeScreens() {
        assertEquals(28, KeyboardUiMetrics.toolbarHeightDp(320, landscape = false))
        assertEquals(32, KeyboardUiMetrics.toolbarHeightDp(412, landscape = false))
        assertEquals(28, KeyboardUiMetrics.toolbarHeightDp(640, landscape = true))
        assertTrue(
            KeyboardUiMetrics.estimatedStandardHeightDp(
                320,
                568,
                landscape = false,
                rowCount = 4,
                hasSuggestion = true,
                includeNavigation = true,
                hasToolbar = true
            ) <= 320
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
