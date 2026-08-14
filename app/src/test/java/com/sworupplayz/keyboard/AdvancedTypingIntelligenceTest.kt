package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTypingIntelligenceTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val english by lazy { LocalWordSuggester.fromWords(englishWords) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }

    @Test
    fun contextBoostsTheLikelyNextWordWithoutExceedingThreeSuggestions() {
        val model = ContextModel(seed = ContextModel.ENGLISH_SEED)
        assertTrue("morning" in model.predictions("good", "m"))
        val suggestions = english.suggestions(
            input = "m",
            learned = emptyList(),
            limit = 3,
            recent = emptyList(),
            contextPredictions = model.predictions("good", "m")
        )
        assertTrue("morning" in suggestions)
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun learnedContextPairsAreBoundedAndSurviveSerialization() {
        val model = ContextModel(limit = 2)
        model.record("see", "you")
        model.record("thank", "you")
        model.record("let", "go")
        val restored = ContextModel.fromSerialized(model.serialize(), limit = 2)
        assertTrue(restored.predictions("let").isNotEmpty())
        assertTrue(restored.serialize().lines().filter { it.isNotBlank() }.size <= 2)
    }

    @Test
    fun capitalizationStartsASentenceAndFollowsPunctuation() {
        assertTrue(CapitalizationPolicy.shouldCapitalize(""))
        assertTrue(CapitalizationPolicy.shouldCapitalize("Hello. "))
        assertTrue(CapitalizationPolicy.shouldCapitalize("ठीक छ।"))
        assertFalse(CapitalizationPolicy.shouldCapitalize("Hello "))
        assertEquals("H", CapitalizationPolicy.applyIncomingLetter("h", ""))
        assertEquals("h", CapitalizationPolicy.applyIncomingLetter("h", "say "))
        assertEquals("Hello", CapitalizationPolicy.applyToWord("hello", "Yes! "))
    }

    @Test
    fun punctuationSpacingAttachesMarksAndSeparatesTheNextSentence() {
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        assertEquals(",", comma.text)

        val nextSentence = PunctuationSpacing.plan("done.", "t")
        assertTrue(nextSentence.insertLeadingSpace)
        assertFalse(PunctuationSpacing.plan("done. ", "t").insertLeadingSpace)
    }

    @Test
    fun editorContextReadsPreviousAndCurrentWordsAroundTheCursor() {
        val inside = EditorContext.from("see you tom", "tom")
        assertEquals("tom", inside.currentWord)
        assertEquals("you", inside.previousWord)

        val afterSpace = EditorContext.from("see you ")
        assertEquals("", afterSpace.currentWord)
        assertEquals("you", afterSpace.previousWord)
        assertEquals("घर", EditorContext.wordAtEnd("मेरो घर"))
    }

    @Test
    fun typoCorrectionUsesEditDistanceButStaysConservative() {
        assertTrue(TypoCorrector.isAdjacentTransposition("ehllo", "hello"))
        assertTrue(TypoCorrector.isVowelDeletion("hllo", "hello"))
        assertTrue(TypoCorrector.isNearbySubstitution("goof", "good"))
        assertFalse(TypoCorrector.isConservativeTypo("helo", "hello"))
        assertFalse(TypoCorrector.isConservativeTypo("xood", "good"))
        assertEquals(1, TypoCorrector.damerauDistance("ehllo", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("hello" in english.suggestions("ehllo") || "hello" in english.suggestions("hllo"))
    }

    @Test
    fun mixedLanguageKeepsEnglishAfterEnglishAndStillConvertsNepali() {
        assertTrue(
            MixedLanguagePolicy.keepAsEnglish("man", "office", setOf("man", "office"), hasNepaliEntry = true)
        )
        assertFalse(
            MixedLanguagePolicy.keepAsEnglish("man", "mero", setOf("man", "office"), hasNepaliEntry = true)
        )
        assertEquals("office", converter.bestConversion("office", previousWord = "the"))
        assertEquals("म", converter.bestConversion("ma", previousWord = null))
        assertEquals("school", converter.bestConversion("school", previousWord = "the"))
    }

    @Test
    fun romanSuggestionsStayCompactAndKeepTheTypedFallback() {
        val suggestions = converter.suggestions("cha")
        assertEquals("छ", suggestions.first())
        assertTrue("cha" in suggestions)
        assertTrue(suggestions.size <= 3)

        val unknown = converter.suggestions("zorpa")
        assertTrue(RomanNepaliConverter.isNepali(unknown.first()))
        assertTrue("zorpa" in unknown)
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
    }

    @Test
    fun namesAndSlangAreTaughtLocallyInsteadOfBeingRequiredInTheDictionary() {
        assertTrue(WordLearningPolicy.shouldLearnUnknown("Zorplet", english::contains))
        assertTrue(WordLearningPolicy.shouldLearnUnknown("idkxyz", english::contains))
        assertFalse(WordLearningPolicy.shouldLearnUnknown("hello", english::contains))

        val learned = LearnedWordStore()
        learned.record("Zorplet")
        val suggestions = english.suggestions("Zo", learned.suggestions("Zo"))
        assertEquals("Zorplet", suggestions.first())
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun suggestionReplacementIsCursorSafeAndNeverTouchesUnrelatedText() {
        assertTrue(SuggestionSelectionPlan.matchesCurrentWord("say he", "he"))
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("the", "he"))
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("other", "he"))

        val plan = SuggestionSelectionPlan.create("he", "hello", "say he")
        assertEquals("hello", plan?.replacement)
        assertEquals(2, plan?.deleteCodeUnits)
        assertNull(SuggestionSelectionPlan.create("he", "hello", "the"))
    }

    @Test
    fun backspaceAndPunctuationKeepComposingStateConsistent() {
        val composer = RomanInputComposer(converter)
        "hello".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.SetComposing("hell"), composer.backspace())
        composer.reset()

        val state = DirectTypingState()
        "good".forEach { assertTrue(state.append(it.toString(), DirectTypingLanguage.ENGLISH)) }
        assertTrue(state.backspace())
        assertEquals("goo", state.currentWord)
        state.replaceWith("good")
        assertEquals("good", state.currentWord)
        assertFalse(state.append(".", DirectTypingLanguage.ENGLISH))
        assertEquals("", state.currentWord)

        assertFalse(RomanSelectionState.movedAwayFromComposition("good", 4, 4, 4))
        assertTrue(RomanSelectionState.movedAwayFromComposition("good", 1, 1, 4))
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
