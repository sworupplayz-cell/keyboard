package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingIntelligenceTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val nepaliWords by lazy {
        VocabularyLoader.mergeDistinct(
            VocabularyLoader.nepali(resource("nepali_vocabulary.txt").reader()),
            VocabularyLoader.nepaliFromRomanDictionary(resource("roman_nepali_dictionary.tsv").reader())
        )
    }
    private val english by lazy { LocalWordSuggester.fromWords(englishWords) }
    private val nepali by lazy { LocalWordSuggester.fromWords(nepaliWords) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }

    @Test
    fun englishVocabularyCoversEverydayWordsAndStaysOffline() {
        assertTrue(englishWords.size >= 2500)
        listOf("the", "hello", "school", "because", "computer", "please", "tomorrow", "family").forEach {
            assertTrue("missing English word: $it", english.contains(it))
        }
        assertTrue("lol" in englishWords || english.contains("ok"))
    }

    @Test
    fun nepaliVocabularyCoversEverydayDevanagari() {
        assertTrue(nepaliWords.size >= 800)
        listOf("म", "मेरो", "घर", "आज", "काम", "नेपाल", "धन्यवाद", "खाना").forEach { word ->
            assertTrue("missing Nepali word: $word", nepali.contains(word) || word in nepaliWords)
        }
        assertTrue(nepali.contains("म"))
        assertTrue(nepali.contains("मैले") || nepali.contains("मलाई"))
    }

    @Test
    fun romanizedNepaliKeepsCommonWordsAndAlternateSpellings() {
        val expected = mapOf(
            "ma" to "म",
            "mero" to "मेरो",
            "malai" to "मलाई",
            "ramro" to "राम्रो",
            "aaja" to "आज",
            "aja" to "आज",
            "bholi" to "भोलि",
            "cha" to "छ",
            "chha" to "छ",
            "maile" to "मैले",
            "mailey" to "मैले"
        )
        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, converter.bestConversion(roman))
        }
    }

    @Test
    fun unknownRomanWordsStillConvertPhonetically() {
        val generated = converter.bestConversion("zorpa")
        assertNotEquals("zorpa", generated)
        assertTrue(RomanNepaliConverter.isNepali(generated))
        assertEquals("म", converter.transliterate("ma"))
        assertTrue(RomanNepaliConverter.isNepali(converter.transliterate("bholi")))
    }

    @Test
    fun mixedEnglishAndNepaliSentenceStaysNatural() {
        val composer = RomanInputComposer(converter)
        val output = StringBuilder()
        output.append(typeWord(composer, "ma", " "))
        output.append(typeWord(composer, "school", " "))
        output.append(typeWord(composer, "jaanchu", ""))
        assertEquals("म school जान्छु", output.toString())

        val longer = StringBuilder()
        val second = RomanInputComposer(converter)
        longer.append(typeWord(second, "ma", " "))
        longer.append(typeWord(second, "office", " "))
        longer.append(typeWord(second, "jana", " "))
        longer.append(typeWord(second, "man", ""))
        assertTrue(longer.toString().startsWith("म office"))
        assertFalse(longer.toString().contains("स्कूल"))
    }

    @Test
    fun rankingPrefersExactFrequencyLearnedAndRecentWords() {
        val ranked = SuggestionRanker.rank(
            input = "he",
            prefixMatches = listOf("he", "hello", "help", "her"),
            typoMatches = emptyList(),
            learned = listOf("helix"),
            recent = listOf("headset"),
            frequencyOf = { word -> listOf("he", "hello", "help", "her").indexOf(word).takeIf { it >= 0 } ?: 99 },
            limit = 3
        )
        assertEquals("he", ranked.first())
        assertTrue("helix" in ranked || "hello" in ranked)
        assertTrue(ranked.size <= 3)

        val learnedFirst = english.suggestions("Zo", listOf("Zorplet", "Zora"), 3)
        assertEquals("Zorplet", learnedFirst.first())
    }

    @Test
    fun typoCorrectionStaysConservative() {
        assertTrue("hello" in english.suggestions("hllo"))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("good" in english.suggestions("goof"))
        assertFalse("good" in english.suggestions("xood"))
        assertTrue("goof" in english.suggestions("goof"))
    }

    @Test
    fun learnedAndRecentStoresAreBoundedAndSerializable() {
        val learned = LearnedWordStore(limit = 2)
        learned.record("alpha")
        learned.record("bravo")
        learned.record("charlie")
        assertTrue(learned.suggestions("alpha").isEmpty())
        assertEquals(2, LearnedWordStore.fromSerialized(learned.serialize()).suggestions("").size)

        val recent = RecentWordStore(limit = 2)
        recent.record("today")
        recent.record("tonight")
        recent.record("tomorrow")
        assertEquals(listOf("tomorrow", "tonight"), recent.values())
        assertEquals("tomorrow", RecentWordStore.fromSerialized(recent.serialize()).matches("to").first())
        assertTrue(WordLearningPolicy.shouldLearnUnknown("Zorplet", english::contains))
        assertFalse(WordLearningPolicy.shouldLearnUnknown("hello", english::contains))
        assertFalse(WordLearningPolicy.shouldLearnUnknown("hi", english::contains))
    }

    @Test
    fun backspacePunctuationAndCursorMovementKeepEditingSafe() {
        val composer = RomanInputComposer(converter)
        composer.type("m")
        composer.type("a")
        assertEquals(RomanEdit.SetComposing("m"), composer.backspace())
        assertEquals(RomanEdit.ClearComposing, composer.backspace())
        assertEquals(RomanEdit.DeletePrevious, composer.backspace())

        "malai".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.Commit("मलाई!"), composer.finishWord("!"))

        val state = DirectTypingState()
        "hello".forEach { assertTrue(state.append(it.toString(), DirectTypingLanguage.ENGLISH)) }
        assertTrue(state.backspace())
        assertEquals("hell", state.currentWord)
        assertFalse(state.append(".", DirectTypingLanguage.ENGLISH))
        assertEquals("", state.currentWord)

        assertFalse(RomanSelectionState.movedAwayFromComposition("ghar", 4, 4, 4))
        assertTrue(RomanSelectionState.movedAwayFromComposition("ghar", 1, 1, 4))
        assertFalse(RomanSelectionState.movedAwayFromComposition("", 1, 1, 4))
    }

    private fun typeWord(composer: RomanInputComposer, word: String, boundary: String): String {
        word.forEach { composer.type(it.toString()) }
        return (composer.finishWord(boundary) as RomanEdit.Commit).text
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
