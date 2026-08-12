package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanNepaliConverterTest {
    private val converter: RomanNepaliConverter by lazy {
        RomanNepaliConverter.from(dictionaryFile().reader())
    }

    @Test
    fun commonRomanWordsConvertToNepaliUnicode() {
        val expected = mapOf(
            "ma" to "म",
            "malai" to "मलाई",
            "nepali" to "नेपाली",
            "ghar" to "घर",
            "ramro" to "राम्रो",
            "cha" to "छ"
        )

        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, converter.exactConversion(roman))
            assertEquals(nepali, converter.suggestions(roman).first())
        }
    }

    @Test
    fun suggestionsUsePrefixesAndAConservativeOfflineFallback() {
        assertTrue("मलाई" in converter.suggestions("mal"))
        assertEquals("घर", converter.transliterate("ghar"))
        assertTrue(converter.suggestions("xyz").isNotEmpty())
    }

    @Test
    fun spacesAndPunctuationFinishKnownWordsButPreserveUnknownWords() {
        val composer = RomanInputComposer(converter)
        assertEquals(RomanEdit.NoOp, composer.finishWord())
        "malai".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("मलाई "), composer.finishWord(" "))

        "ghar".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("घर,"), composer.finishWord(","))

        "unknown".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("unknown "), composer.finishWord(" "))
    }

    @Test
    fun backspaceEditsCompositionBeforeDeletingExternalText() {
        val composer = RomanInputComposer(converter)
        composer.type("m")
        composer.type("a")

        assertEquals(RomanEdit.SetComposing("m"), composer.backspace())
        assertEquals(RomanEdit.ClearComposing, composer.backspace())
        assertEquals(RomanEdit.DeletePrevious, composer.backspace())
    }

    @Test
    fun acceptingSuggestionCommitsItWithoutAddingUnexpectedText() {
        val composer = RomanInputComposer(converter)
        composer.type("m")
        composer.type("a")
        assertEquals(RomanEdit.Commit("म"), composer.acceptSuggestion("म"))
        assertEquals("", composer.currentWord)
    }

    @Test
    fun romanEditsReplaceCompositionInAnExternalFieldModel() {
        val composer = RomanInputComposer(converter)
        val field = ExternalFieldModel()

        "nepali".forEach { field.apply(composer.type(it.toString())) }
        field.apply(composer.finishWord(" "))
        "ghar".forEach { field.apply(composer.type(it.toString())) }
        field.apply(composer.finishWord("."))

        assertEquals("नेपाली घर.", field.text)
    }

    private fun dictionaryFile(): File = listOf(
        File("src/main/res/raw/roman_nepali_dictionary.tsv"),
        File("app/src/main/res/raw/roman_nepali_dictionary.tsv")
    ).first { it.isFile }

    private class ExternalFieldModel {
        private val value = StringBuilder()
        private var composingStart = -1
        val text: String get() = value.toString()

        fun apply(edit: RomanEdit) {
            when (edit) {
                is RomanEdit.SetComposing -> {
                    if (composingStart < 0) composingStart = value.length
                    value.replace(composingStart, value.length, edit.text)
                }
                is RomanEdit.Commit -> {
                    val start = if (composingStart < 0) value.length else composingStart
                    value.replace(start, value.length, edit.text)
                    composingStart = -1
                }
                RomanEdit.ClearComposing -> {
                    if (composingStart >= 0) value.delete(composingStart, value.length)
                    composingStart = -1
                }
                RomanEdit.DeletePrevious -> if (value.isNotEmpty()) value.deleteCharAt(value.lastIndex)
                RomanEdit.NoOp -> Unit
            }
        }
    }
}
