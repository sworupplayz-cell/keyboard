package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedRomanNepaliTest {
    private val converter: RomanNepaliConverter by lazy {
        RomanNepaliConverter.from(dictionaryFile().reader())
    }

    @Test
    fun knownWordsStillUseNaturalVocabularyResults() {
        val expected = mapOf(
            "ma" to "म",
            "malai" to "मलाई",
            "nepali" to "नेपाली",
            "ghar" to "घर",
            "ramro" to "राम्रो",
            "cha" to "छ"
        )
        expected.forEach { (roman, nepali) -> assertEquals(nepali, converter.bestConversion(roman)) }
    }

    @Test
    fun commonAlternateSpellingsResolveThroughReusableLookupForms() {
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("भनेको", converter.bestConversion("vaneko"))
        assertEquals("भनेको", converter.bestConversion("bhaneko"))
        assertEquals("आज", converter.bestConversion("aja"))
        assertEquals("आज", converter.bestConversion("aaja"))
        assertEquals("मैले", converter.bestConversion("maile"))
        assertEquals("मैले", converter.bestConversion("mailey"))
        assertEquals("तिमी", converter.bestConversion("timii"))
        assertEquals("सन्तोष", converter.bestConversion("santooosh"))
    }

    @Test
    fun madeUpWordGetsPhoneticDevanagariInsteadOfBeingRejected() {
        val generated = converter.bestConversion("zorpa")

        assertNotEquals("zorpa", generated)
        assertTrue(RomanNepaliConverter.isNepali(generated))
        assertEquals(generated, converter.suggestions("zorpa").first())
        assertTrue("zorpa" in converter.suggestions("zorpa"))
    }

    @Test
    fun mixedNepaliAndEnglishCanBeComposedNaturally() {
        val composer = RomanInputComposer(converter)
        val output = StringBuilder()

        output.append(typeWord(composer, "ma", " "))
        output.append(typeWord(composer, "school", " "))
        output.append(typeWord(composer, "jaanchu", ""))

        assertEquals("म school जान्छु", output.toString())
    }

    @Test
    fun suggestionsAreCompactUsefulAndKeepRomanAsAnOption() {
        val candidates = converter.suggestions("cha")

        assertEquals("छ", candidates.first())
        assertTrue("च" in candidates)
        assertTrue("cha" in candidates)
        assertTrue(candidates.size <= 3)
    }

    @Test
    fun learnedMappingBecomesFirstSuggestionAndSurvivesSerialization() {
        val learned = LearnedRomanWords()
        assertTrue(learned.learn("zorpaa", "जोर्पा"))
        val restored = LearnedRomanWords.fromSerialized(learned.serialize())

        assertEquals("जोर्पा", restored.lookup("zorpaa"))
        assertEquals("जोर्पा", converter.suggestions("zorpaa", restored.lookup("zorpaa")).first())

        val composer = RomanInputComposer(converter, restored::lookup)
        "zorpaa".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("जोर्पा "), composer.finishWord(" "))
    }

    @Test
    fun editingBoundariesConvertUnknownWordsWithoutBreakingComposition() {
        val composer = RomanInputComposer(converter)
        composer.type("z")
        composer.type("o")
        composer.type("r")
        assertEquals(RomanEdit.SetComposing("zo"), composer.backspace())
        composer.type("r")
        composer.type("p")
        composer.type("a")

        val punctuation = composer.finishWord("!") as RomanEdit.Commit
        assertTrue(RomanNepaliConverter.isNepali(punctuation.text.dropLast(1)))
        assertTrue(punctuation.text.endsWith("!"))

        "ghar".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("घर\n"), composer.finishWord("\n"))
    }

    @Test
    fun cursorMovementPolicyKeepsOrFinishesCompositionSafely() {
        assertEquals(false, RomanSelectionState.movedAwayFromComposition("ghar", 4, 4, 4))
        assertEquals(true, RomanSelectionState.movedAwayFromComposition("ghar", 2, 2, 4))
        assertEquals(false, RomanSelectionState.movedAwayFromComposition("", 2, 2, 4))
    }

    @Test
    fun typingCanContinueAndBeEditedAfterSuggestionSelection() {
        val composer = RomanInputComposer(converter)
        "malai".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("मलाई"), composer.acceptSuggestion("मलाई"))

        "ghar".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.SetComposing("gha"), composer.backspace())
        composer.type("r")
        assertEquals(RomanEdit.Commit("घर "), composer.finishWord(" "))
    }

    @Test
    fun romanModeCanReachAndReturnFromEveryTemporaryPanel() {
        val actions = KeyboardLayouts.navigationControls().map { it.action }.toSet()
        assertTrue(KeyAction.MODE_ROMAN in actions)
        assertTrue(KeyAction.MODE_NEPALI in actions)
        assertTrue(KeyAction.HANDWRITING in actions)
        assertTrue(KeyAction.NUMBERS in actions)
        assertTrue(KeyAction.EMOJI in actions)
        assertTrue(KeyboardLayouts.numbers(KeyboardLanguage.ROMAN).flatten().any {
            it.action == KeyAction.SYMBOLS
        })

        val history = PreviousLayoutStack<Pair<KeyboardLanguage, String>>()
        history.remember(KeyboardLanguage.ROMAN to "letters")
        assertEquals(
            KeyboardLanguage.ROMAN to "letters",
            history.previousOr(KeyboardLanguage.ENGLISH to "letters")
        )
    }

    private fun typeWord(composer: RomanInputComposer, word: String, boundary: String): String {
        word.forEach { composer.type(it.toString()) }
        return (composer.finishWord(boundary) as RomanEdit.Commit).text
    }

    private fun dictionaryFile(): File = listOf(
        File("src/main/res/raw/roman_nepali_dictionary.tsv"),
        File("app/src/main/res/raw/roman_nepali_dictionary.tsv")
    ).first { it.isFile }
}
