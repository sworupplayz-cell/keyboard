package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAndPanelTest {
    @Test
    fun emojiCatalogUsesLocalUnicodeAndSelectionCanBeInserted() {
        val smileys = EmojiCatalog.emojis(EmojiCategory.SMILEYS)
        assertTrue("😀" in smileys)
        assertTrue(smileys.all(String::isNotBlank))

        val externalField = StringBuilder("hello ")
        val selected = RecentEmojiList().record("😊")
        externalField.append(selected)

        assertEquals("hello 😊", externalField.toString())
    }

    @Test
    fun recentEmojiListIsLocalOrderedUniqueAndBounded() {
        val recent = RecentEmojiList(limit = 3)
        recent.record("😀")
        recent.record("😊")
        recent.record("🐶")
        recent.record("😀")
        recent.record("🍎")

        assertEquals(listOf("🍎", "😀", "🐶"), recent.values())
        assertFalse("not-an-emoji" in recent.values())
    }

    @Test
    fun numberLayoutContainsEveryAsciiDigit() {
        val outputs = KeyboardLayouts.numbers(KeyboardLanguage.ENGLISH).flatten()
            .filter { it.action == KeyAction.TEXT }
            .map { it.output }
            .toSet()

        assertEquals((0..9).map { it.toString() }.toSet(), outputs.filter { it.singleOrNull()?.isDigit() == true }.toSet())
        assertTrue(KeyboardLayouts.numbers(KeyboardLanguage.ENGLISH).flatten().any {
            it.action == KeyAction.LETTERS
        })
    }

    @Test
    fun symbolLayoutContainsRequestedCommonSymbols() {
        val outputs = KeyboardLayouts.symbols(KeyboardLanguage.ENGLISH).flatten()
            .filter { it.action == KeyAction.TEXT }
            .map { it.output }
            .toSet()
        val required = setOf(
            ".", ",", "?", "!", "'", "\"", "@", "#", "\$", "%", "&", "*",
            "(", ")", "-", "+", "=", "/", ":", ";", "_"
        )

        assertTrue(outputs.containsAll(required))
    }

    @Test
    fun navigationExposesEveryPrimaryLayout() {
        val actions = KeyboardLayouts.navigationControls().map { it.action }.toSet()

        assertTrue(KeyAction.MODE_ENGLISH in actions)
        assertTrue(KeyAction.MODE_NEPALI in actions)
        assertTrue(KeyAction.MODE_ROMAN in actions)
        assertTrue(KeyAction.NUMBERS in actions)
        assertTrue(KeyAction.EMOJI in actions)
        assertTrue(KeyAction.HANDWRITING in actions)
        assertTrue(KeyboardLayouts.emojiControls().flatten().any {
            it.action == KeyAction.RETURN_TO_PREVIOUS
        })
    }

    @Test
    fun previousLayoutStackReturnsNestedPanelsInOrder() {
        val history = PreviousLayoutStack<String>()
        history.remember("English")
        history.remember("Numbers")

        assertEquals("Numbers", history.previousOr("Fallback"))
        assertEquals("English", history.previousOr("Fallback"))
        assertEquals("Fallback", history.previousOr("Fallback"))
        assertEquals(0, history.size)
    }
}
