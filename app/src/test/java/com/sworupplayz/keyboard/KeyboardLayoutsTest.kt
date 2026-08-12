package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {
    @Test
    fun englishLayoutProvidesLowercaseAndShiftedLetters() {
        val lowercase = textOutputs(KeyboardLayouts.english(shifted = false))
        val uppercase = textOutputs(KeyboardLayouts.english(shifted = true))

        assertTrue("q" in lowercase)
        assertTrue("z" in lowercase)
        assertTrue("Q" in uppercase)
        assertTrue("Z" in uppercase)
        assertTrue(KeyboardLayouts.english(false).flatten().any { it.action == KeyAction.BACKSPACE })
        assertTrue(KeyboardLayouts.english(false).flatten().any { it.action == KeyAction.ENTER })
    }

    @Test
    fun nepaliLayoutsContainCommonUnicodeCharactersAndSigns() {
        val outputs = textOutputs(KeyboardLayouts.nepaliConsonants()) +
            textOutputs(KeyboardLayouts.nepaliVowels())

        listOf("क", "ख", "ग", "त", "न", "प", "म", "र", "ल", "स", "ह", "अ", "आ", "इ", "उ", "ए", "ओ", "ा", "ि", "ी", "ु", "ू", "े", "ो", "ं", "ँ", "्", "।").forEach {
            assertTrue("Missing Nepali key: $it", it in outputs)
        }
    }

    @Test
    fun symbolLayoutContainsDigitsAndReturnsToTheCurrentLanguage() {
        val symbols = KeyboardLayouts.symbols(KeyboardLanguage.ENGLISH).flatten()
        assertEquals(
            (0..9).map { it.toString() }.toSet(),
            symbols.map { it.output }.filter { it.length == 1 && it[0].isDigit() }.toSet()
        )
        assertTrue(symbols.any { it.action == KeyAction.LETTERS && it.label == "ABC" })
        assertTrue(KeyboardLayouts.symbols(KeyboardLanguage.NEPALI).flatten().any {
            it.action == KeyAction.LETTERS && it.label == "कखग"
        })
        assertTrue(KeyboardLayouts.symbols(KeyboardLanguage.ROMAN).flatten().any {
            it.action == KeyAction.LETTERS && it.label == "Roman"
        })
    }

    @Test
    fun languageModeCyclesThroughEnglishNepaliAndRoman() {
        assertEquals(KeyboardLanguage.NEPALI, KeyboardLanguage.ENGLISH.next())
        assertEquals(KeyboardLanguage.ROMAN, KeyboardLanguage.NEPALI.next())
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.ROMAN.next())
        assertTrue(KeyboardLayouts.english(false, KeyboardLanguage.ROMAN).flatten().any {
            it.action == KeyAction.LANGUAGE && it.label == "EN"
        })
    }

    @Test
    fun handwritingModeIsReachableAndCanReturnToEveryTypingMode() {
        listOf(
            KeyboardLayouts.english(false),
            KeyboardLayouts.nepaliConsonants(),
            KeyboardLayouts.nepaliVowels(),
            KeyboardLayouts.symbols(KeyboardLanguage.ROMAN)
        ).forEach { layout ->
            assertTrue(layout.flatten().any { it.action == KeyAction.HANDWRITING })
        }

        val controls = KeyboardLayouts.handwritingControls().flatten()
        assertTrue(controls.any { it.action == KeyAction.HANDWRITING_CLEAR })
        assertTrue(controls.any { it.action == KeyAction.HANDWRITING_UNDO })
        assertTrue(controls.any { it.action == KeyAction.HANDWRITING_CONFIRM })
        assertTrue(controls.any { it.action == KeyAction.HANDWRITING_CANCEL })
        assertTrue(controls.any { it.action == KeyAction.MODE_ENGLISH })
        assertTrue(controls.any { it.action == KeyAction.MODE_NEPALI })
        assertTrue(controls.any { it.action == KeyAction.MODE_ROMAN })
    }

    private fun textOutputs(rows: List<List<KeySpec>>): Set<String> = rows.flatten()
        .filter { it.action == KeyAction.TEXT }
        .map { it.output }
        .toSet()
}
