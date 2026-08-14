package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyVisualsTest {
    @Test
    fun rolesMatchGboardChromeWithoutChangingActions() {
        assertEquals(KeyVisualRole.LETTER, KeyVisuals.role(KeySpec("a")))
        assertEquals(KeyVisualRole.SPACE, KeyVisuals.role(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals(KeyVisualRole.ENTER, KeyVisuals.role(KeySpec("↵", KeyAction.ENTER)))
        assertEquals(KeyVisualRole.MODIFIER, KeyVisuals.role(KeySpec("⇧", KeyAction.SHIFT)))
        assertEquals(KeyVisualRole.TOOLBAR, KeyVisuals.role(KeySpec("EN", KeyAction.MODE_ENGLISH)))
        assertEquals(KeyVisualRole.TOOLBAR, KeyVisuals.role(KeySpec("⚙", KeyAction.SETTINGS)))
    }

    @Test
    fun previewIsOnlyForShortTextKeys() {
        assertTrue(KeyVisuals.showsPreview(KeySpec("a")))
        assertTrue(KeyVisuals.showsPreview(KeySpec("क")))
        assertFalse(KeyVisuals.showsPreview(KeySpec("English", KeyAction.SPACE, " ")))
        assertFalse(KeyVisuals.showsPreview(KeySpec("⇧", KeyAction.SHIFT)))
        assertFalse(KeyVisuals.showsPreview(KeySpec("?123", KeyAction.NUMBERS)))
    }

    @Test
    fun latinLongPressOffersAccentedFormsAndKeepsCase() {
        assertTrue("é" in KeyVisuals.alternates("e"))
        assertTrue("É" in KeyVisuals.alternates("E"))
        assertTrue("ñ" in KeyVisuals.alternates("n"))
        assertTrue("…" in KeyVisuals.alternates("."))
        assertTrue(KeyVisuals.alternates("q").isEmpty())
    }

    @Test
    fun nepaliLongPressOffersRelatedFormsWithoutReplacingTapOutput() {
        assertTrue("ख" in KeyVisuals.alternates("क"))
        assertTrue("॥" in KeyVisuals.alternates("।"))
        assertEquals("क", KeySpec("क").output)
    }

    @Test
    fun emojiCategoryIconsStayLocalAndTappableLabelsRemain() {
        assertEquals("😊", KeyVisuals.emojiCategoryIcon(EmojiCategory.SMILEYS))
        assertEquals("Recent", EmojiCategory.RECENT.title)
        assertEquals(EmojiCategory.entries.size, EmojiCategory.entries.map(KeyVisuals::emojiCategoryIcon).distinct().size)
    }

    @Test
    fun letterTextStaysLargerThanModifierText() {
        val letter = KeyVisuals.letterTextSizeSp("a", compactScreen = false, KeyVisualRole.LETTER)
        val modifier = KeyVisuals.letterTextSizeSp("?123", compactScreen = false, KeyVisualRole.MODIFIER)
        val space = KeyVisuals.letterTextSizeSp("English", compactScreen = false, KeyVisualRole.SPACE)
        assertTrue(letter > modifier)
        assertTrue(letter > space)
    }
}
