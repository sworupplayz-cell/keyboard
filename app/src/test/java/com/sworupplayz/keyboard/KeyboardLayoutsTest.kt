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
    }

    private fun textOutputs(rows: List<List<KeySpec>>): Set<String> = rows.flatten()
        .filter { it.action == KeyAction.TEXT }
        .map { it.output }
        .toSet()
}
