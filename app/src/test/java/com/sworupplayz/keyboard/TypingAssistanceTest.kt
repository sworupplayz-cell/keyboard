package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingAssistanceTest {
    private val english by lazy {
        LocalWordSuggester.fromWords(VocabularyLoader.english(resourceFile("english_vocabulary.txt").reader()))
    }
    private val nepali by lazy {
        LocalWordSuggester.fromWords(
            VocabularyLoader.nepaliFromRomanDictionary(resourceFile("roman_nepali_dictionary.tsv").reader())
        )
    }

    @Test
    fun commonEnglishPrefixesReturnUsefulLocalSuggestions() {
        val he = english.suggestions("he")
        assertTrue("he" in he)
        assertTrue("hello" in he)
        assertTrue(he.size <= 3)

        assertTrue("how" in english.suggestions("how"))
        assertTrue("what" in english.suggestions("wha"))
        assertTrue("where" in english.suggestions("wher"))
        assertTrue("good" in english.suggestions("goo"))
        assertTrue("you" in english.suggestions("you"))
        assertTrue("are" in english.suggestions("are"))
    }

    @Test
    fun unknownEnglishWordRemainsAvailableAndIsNeverForced() {
        val suggestions = english.suggestions("qzorp")
        assertEquals(listOf("qzorp"), suggestions)

        val state = DirectTypingState()
        "qzorp".forEach { state.append(it.toString(), DirectTypingLanguage.ENGLISH) }
        assertEquals("qzorp", state.currentWord)
        state.clear()
        assertEquals("", state.currentWord)
    }

    @Test
    fun conservativeTypoIndexesOfferButDoNotApplyCorrections() {
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("hello" in english.suggestions("hllo"))
        assertTrue("good" in english.suggestions("goof"))
        assertFalse("good" in english.suggestions("xood"))
        assertTrue("goof" in english.suggestions("goof"))
    }

    @Test
    fun nepaliPrefixSuggestionsPrioritizeCommonVocabulary() {
        val suggestions = nepali.suggestions("म")
        assertEquals("म", suggestions.first())
        assertTrue("मैले" in suggestions || "मलाई" in suggestions)
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun directTypingStateHandlesNepaliMarksBackspaceAndBoundaries() {
        val state = DirectTypingState()
        assertTrue(state.append("क", DirectTypingLanguage.NEPALI))
        assertTrue(state.append("ी", DirectTypingLanguage.NEPALI))
        assertEquals("की", state.currentWord)
        assertTrue(state.backspace())
        assertEquals("क", state.currentWord)
        assertFalse(state.append("।", DirectTypingLanguage.NEPALI))
        assertEquals("", state.currentWord)
        assertFalse(state.append("१", DirectTypingLanguage.NEPALI))
    }

    @Test
    fun explicitlySelectedUnusualWordsBecomeHighPrioritySuggestions() {
        val learned = LearnedWordStore()
        assertTrue(learned.record("Zorplet"))
        assertTrue(learned.record("Zorplet"))
        assertTrue(learned.record("Zora"))
        val restored = LearnedWordStore.fromSerialized(learned.serialize())

        val suggestions = english.suggestions("Zo", restored.suggestions("Zo"))
        assertEquals("Zorplet", suggestions.first())
        assertTrue("Zora" in suggestions)
    }

    @Test
    fun learnedWordStoreRemainsBounded() {
        val learned = LearnedWordStore(limit = 2)
        learned.record("oneoffname")
        learned.record("localslang")
        learned.record("newnickname")

        assertTrue(learned.suggestions("one").isEmpty())
        assertEquals(2, LearnedWordStore.fromSerialized(learned.serialize()).suggestions("").size)
    }

    @Test
    fun suggestionReplacementOnlyTouchesTheVerifiedCurrentWord() {
        val plan = SuggestionSelectionPlan.create("he", "hello", "he")
        assertEquals(2, plan?.deleteCodePoints)
        assertEquals("hello", plan?.replacement)
        assertEquals(true, plan?.changesText)

        assertNull(SuggestionSelectionPlan.create("he", "hello", "other"))
        assertEquals(
            false,
            SuggestionSelectionPlan.create("unusual", "unusual", "unusual")?.changesText
        )
    }

    @Test
    fun acceptingSuggestionPreservesEarlierExternalText() {
        val field = StringBuilder("say he")
        val plan = SuggestionSelectionPlan.create("he", "hello", "he")!!
        field.delete(field.length - plan.deleteCodeUnits, field.length)
        field.append(plan.replacement)

        assertEquals("say hello", field.toString())
    }

    private fun resourceFile(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
