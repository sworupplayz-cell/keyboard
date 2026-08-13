package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase22LanguageIntelligenceTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val nepaliWords by lazy {
        VocabularyLoader.mergeDistinct(
            VocabularyLoader.nepali(resource("nepali_vocabulary.txt").reader()),
            VocabularyLoader.nepaliFromRomanDictionary(resource("roman_nepali_dictionary.tsv").reader())
        )
    }
    private val englishEntries by lazy {
        VocabularyCatalog.english(resource("english_vocabulary.txt").reader())
    }
    private val english by lazy { LocalWordSuggester.fromWords(englishWords) }
    private val nepali by lazy { LocalWordSuggester.fromWords(nepaliWords) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }
    private val engine by lazy { SuggestionEngine(english, nepali, converter) }

    @Test
    fun vocabularyCatalogPreservesFrequencyOrderAndCategories() {
        assertTrue(englishEntries.size >= 2500)
        assertEquals("the", englishEntries.first().word)
        assertTrue(englishEntries.first().rank < englishEntries.first { it.word == "hello" }.rank)
        assertEquals(VocabularyCategory.SLANG, VocabularyCatalog.classify("lol", VocabularyLanguage.ENGLISH))
        assertEquals(VocabularyCategory.PLACE, VocabularyCatalog.classify("kathmandu", VocabularyLanguage.ENGLISH))
        assertTrue(VocabularyCatalog.shouldStayBehindCore(VocabularyCategory.SLANG))
        assertTrue(english.contains("playing"))
        assertTrue(english.contains("works"))
        assertTrue(nepali.contains("जाने") || "जाने" in nepaliWords)
        assertTrue(nepali.contains("मनपर्ने") || "मनपर्ने" in nepaliWords)
    }

    @Test
    fun prefixRankingStaysDeterministicAndDeduped() {
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.toString(), "hello" in hel)
        assertTrue(hel.toString(), "help" in hel)
        assertTrue(hel.toString(), "he'll" in hel)
        assertEquals(hel.distinct(), hel)
        assertTrue(hel.size <= 3)

        val goo = visible(englishQuery("goo"))
        assertTrue(goo.toString(), "good" in goo)
        assertTrue(goo.toString(), "going" in goo)

        val mal = visible(nepaliQuery("मला"))
        assertTrue(mal.toString(), "मलाई" in mal)

        val jaan = visible(romanQuery("jaan"))
        assertTrue(jaan.toString(), jaan.any { it in setOf("जान", "जान्छु", "जानु") })
        assertEquals(jaan, visible(romanQuery("jaan")))
    }

    @Test
    fun morphologyFamiliesDoNotRewriteText() {
        assertEquals(listOf("play", "plays", "played", "playing"), Morphology.englishRelatives("play"))
        assertTrue("went" in Morphology.englishRelatives("go"))
        assertTrue("घरमा" in Morphology.nepaliRelatives("घर"))
        assertTrue("जान्छु" in Morphology.nepaliRelatives("जानु"))
        assertTrue("मनपर्ने" in Morphology.nepaliRelatives("मन"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("play", "playing"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("घर", "घरमा"))
    }

    @Test
    fun romanNormalizationFindsEverydayVariants() {
        assertEquals("जान्छु", converter.bestConversion("jaanxu"))
        assertEquals("मलाई", converter.bestConversion("malay"))
        assertEquals("मलाई", converter.bestConversion("malaii"))
        assertEquals("धन्यवाद", converter.bestConversion("dhanyabaad"))
        assertEquals("कहाँ", converter.bestConversion("kata"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        assertTrue(RomanNepaliConverter.isNepali(converter.bestConversion("zorpa")))
        assertEquals("school", converter.bestConversion("school"))
    }

    @Test
    fun mixedLanguageKeepsTheSelectedModeAuthoritative() {
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("today म school जान्छु", converter.convertText("today ma school jaanchu"))
        assertEquals("मेरो phone good छ", converter.convertText("mero phone good cha"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        assertTrue(LanguageIntelligencePolicy.staysInSelectedMode(KeyboardLanguage.ROMAN, "hello"))
    }

    @Test
    fun contextUsesOneTwoAndThreeWords() {
        val afterGood = visible(englishQuery("", previous = "good"))
        assertTrue(afterGood.toString(), "morning" in afterGood)
        val howAre = visible(englishQuery("", previous = "are", previousTwo = "how are"))
        assertTrue(howAre.toString(), "you" in howAre)
        val three = EditorContext.from("how are you ")
        assertEquals("you", three.previousWord)
        assertEquals("are you", three.previousTwoWords)
        assertEquals("how are you", three.previousThreeWords)
        val malai = visible(nepaliQuery("", previous = "मलाई"))
        assertTrue(malai.toString(), malai.any { "मन" in it })
        val timi = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES).predict("timi")
        assertTrue(timi.toString(), timi.any { it in setOf("kaha", "kasto", "lai") })
    }

    @Test
    fun personalLearningStaysBoundedAndRejectsGarbage() {
        val learned = LearnedWordStore(limit = 2)
        assertTrue(learned.record("Zorplet"))
        assertEquals(1, learned.scoreOf("Zorplet"))
        learned.record("Zorplet")
        assertEquals(2, learned.scoreOf("Zorplet"))
        learned.record("localslang")
        learned.record("newnickname")
        assertEquals(2, learned.size())
        assertTrue(WordLearningPolicy.shouldLearnUnknown("Zorplet", english::contains))
        assertFalse(WordLearningPolicy.shouldLearnUnknown("hi", english::contains))
        assertFalse(WordLearningPolicy.isGarbage("Sworup"))
        assertTrue(WordLearningPolicy.isGarbage("https://example.com"))
        assertTrue(WordLearningPolicy.isGarbage("user@example.com"))
        assertTrue(WordLearningPolicy.isGarbage("aa"))
        assertFalse(PersonalDictionary.shouldLearnPhrase("aa", "hello"))
        assertTrue(PersonalDictionary.shouldLearnPhrase("good", "morning"))
    }

    @Test
    fun typosAreSuggestedNotAppliedAndRankingCapsDuplicates() {
        assertFalse(TypoCorrector.isConservativeTypo("helo", "hello"))
        assertTrue(CorrectionPolicy.shouldOfferTypo("helo", "hello"))
        assertTrue("hello" in visible(englishQuery("helo")))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("the" in TypoCorrector.commonCorrections("teh"))
        assertTrue("because" in TypoCorrector.commonCorrections("becuase"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("recieve", "receive"))
        val ranked = SuggestionRanker.rank(
            input = "he",
            prefixMatches = listOf("he", "hello", "help", "he"),
            typoMatches = listOf("hello"),
            learned = emptyList(),
            recent = emptyList(),
            frequencyOf = { listOf("he", "hello", "help").indexOf(it).takeIf { n -> n >= 0 } ?: 9 },
            limit = 3
        )
        assertEquals(ranked.distinct(), ranked)
        assertTrue(ranked.size <= 3)
        assertEquals("he", ranked.first())
    }

    @Test
    fun emojiStaysHighConfidenceAndPrefixCacheIsStable() {
        EmojiCatalog.resetToBuiltin()
        assertEquals("❤", EmojiSuggestionPolicy.suggest("love"))
        assertEquals("🎉", EmojiSuggestionPolicy.suggest("congratulations"))
        assertEquals("🙏", EmojiSuggestionPolicy.suggest("thanks"))
        assertEquals(null, EmojiSuggestionPolicy.suggest("he"))
        val first = english.suggestions("hel")
        val second = english.suggestions("hel")
        assertEquals(first, second)
        assertTrue(first.size <= 3)
    }

    private fun visible(query: SuggestionQuery): List<String> = engine.suggest(query).visible()

    private fun englishQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(input, SuggestionLanguage.ENGLISH, previous, previousTwo)

    private fun nepaliQuery(input: String, previous: String? = null) =
        SuggestionQuery(input, SuggestionLanguage.NEPALI, previous)

    private fun romanQuery(input: String) = SuggestionQuery(input, SuggestionLanguage.ROMAN)

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
