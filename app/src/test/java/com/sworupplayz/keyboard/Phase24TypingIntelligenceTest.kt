package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase24TypingIntelligenceTest {
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
    private val engine by lazy { SuggestionEngine(english, nepali, converter) }

    @Test
    fun englishPrefixCompletionStaysUseful() {
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.toString(), "hello" in hel && "help" in hel && "he'll" in hel)
        val goo = visible(englishQuery("goo"))
        assertTrue(goo.toString(), "good" in goo && "going" in goo)
        val tha = visible(englishQuery("tha"))
        assertTrue(tha.toString(), "that" in tha)
        assertTrue("morning" in visible(englishQuery("morn")))
        val nep = visible(englishQuery("nep"))
        assertTrue(nep.toString(), nep.any { it.equals("Nepal", ignoreCase = true) })
        assertTrue(nep.toString(), nep.any { it.equals("Nepali", ignoreCase = true) })
        assertTrue(hel.size <= 3)
    }

    @Test
    fun nextWordPredictionUsesCompactPhrases() {
        assertTrue("morning" in visible(englishQuery("", previous = "good")))
        val afterGoodMorning = visible(
            englishQuery("", previous = "morning", previousTwo = "good morning")
        )
        assertTrue(afterGoodMorning.toString(), afterGoodMorning.any { it in setOf("everyone", "guys") })
        assertTrue("you" in visible(englishQuery("", previous = "are", previousTwo = "how are")))
        val afterIAm = visible(englishQuery("", previous = "am", previousTwo = "i am"))
        assertTrue(afterIAm.toString(), afterIAm.any { it in setOf("going", "here", "fine") })
        val afterThanks = visible(englishQuery("", previous = "you", previousTwo = "thank you"))
        assertTrue(afterThanks.toString(), afterThanks.any { it in setOf("for", "very") })
        val afterSeeYou = visible(englishQuery("", previous = "you", previousTwo = "see you"))
        assertTrue(afterSeeYou.toString(), afterSeeYou.any { it in setOf("soon", "tomorrow", "later") })
    }

    @Test
    fun contractionsAndTyposAreSuggestionsOnly() {
        assertTrue("can't" in visible(englishQuery("cant")))
        assertTrue("don't" in ContractionExpander.expand("dont"))
        assertTrue("I'm" in ContractionExpander.expand("im"))
        assertTrue("hello" in visible(englishQuery("helo")))
        assertTrue("the" in visible(englishQuery("teh")))
        assertTrue("receive" in TypoCorrector.commonCorrections("recieve"))
        assertTrue("address" in TypoCorrector.commonCorrections("adress"))
        assertTrue("because" in TypoCorrector.commonCorrections("becuase"))
        assertTrue("nepali" in visible(englishQuery("nepai")).map { it.lowercase() })
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("cant", "can't"))
    }

    @Test
    fun personalizationBoostsRepeatedWordsWithoutErasingCoreVocabulary() {
        val store = LearnedWordStore()
        repeat(4) { store.record("help") }
        store.record("hello")
        val adapted = visible(
            SuggestionQuery(
                input = "hel",
                language = SuggestionLanguage.ENGLISH,
                learned = store.suggestions("hel"),
                personalFrequency = store.frequencyMap("hel")
            )
        )
        assertEquals("help", adapted.first())
        assertTrue("hello" in adapted || "he'll" in adapted)
        assertTrue(english.contains("hello"))
        assertTrue(store.decayedScore("help") >= store.decayedScore("hello"))
    }

    @Test
    fun nepaliPrefixesContextAndMorphologyStayCompact() {
        assertTrue("मलाई" in visible(nepaliQuery("मला")))
        val tap = visible(nepaliQuery("तप"))
        assertTrue(tap.toString(), tap.any { it.startsWith("तपाई") })
        val jaan = visible(nepaliQuery("जान"))
        assertTrue(jaan.toString(), jaan.any { it in setOf("जान", "जान्छु", "जानु") })
        assertTrue(visible(nepaliQuery("", previous = "मलाई")).any { "मन" in it })
        assertTrue("पर्छ" in visible(nepaliQuery("", previous = "मन", previousTwo = "मलाई मन")))
        assertTrue("घरमा" in Morphology.nepaliRelatives("घर"))
    }

    @Test
    fun romanNormalizationAndPhoneticFallbackStayInPlace() {
        assertEquals("मलाई", converter.bestConversion("malai"))
        assertEquals("मलाई", converter.bestConversion("malaai"))
        assertEquals("मलाई", converter.bestConversion("malaii"))
        assertEquals("जान्छु", converter.bestConversion("jaanchu"))
        assertEquals("जान्छु", converter.bestConversion("janchu"))
        assertEquals("जान्छु", converter.bestConversion("jaanxu"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छौ", converter.bestConversion("chau"))
        assertEquals("हुन्छ", converter.bestConversion("huncha"))
        assertEquals("हुन्छ", converter.bestConversion("hunxa"))
        assertEquals("नेपाली", converter.bestConversion("nepali"))
        assertEquals("नेपाली", converter.bestConversion("nepaali"))
        val unknown = converter.suggestions("zorpa")
        assertTrue(RomanNepaliConverter.isNepali(unknown.first()))
        assertTrue("zorpa" in unknown)
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        val ghar = visible(romanQuery("ghar"))
        assertTrue("घर" in ghar)
        val jaan = visible(romanQuery("jaan"))
        assertTrue(jaan.any { it in setOf("जान", "जान्छु", "जानु") })
    }

    @Test
    fun mixedLanguageKeepsEnglishWordsAndProperNouns() {
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("म college जान्छु", converter.convertText("ma college jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("म Kathmandu जान्छु", converter.convertText("ma Kathmandu janchu"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("college", converter.bestConversion("college"))
        assertEquals(
            KeyboardLanguage.ENGLISH,
            LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar")
        )
        val afterMaSchool = visible(romanQuery("", previous = "school", previousTwo = "ma school"))
        assertTrue(afterMaSchool.toString(), afterMaSchool.any { it == "जान्छु" || it == "jaanchu" || it == "gaye" })
    }

    @Test
    fun privacyRejectsSecretsAndCustomWordsStayBehindCore() {
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        assertFalse(LearnedWordStore().record("https://example.com"))
        assertTrue(PersonalDictionary.looksLegitimateCustomWord("Sworup"))
        assertTrue(PersonalDictionary.looksLikePersonalName("Sworup"))
        assertFalse(PersonalDictionary.looksLikePersonalName("https://example.com"))
        assertFalse(PersonalDictionary.looksLikePersonalName("qz"))
        assertEquals(VocabularyCategory.NAME, VocabularyCatalog.classify("sharma", VocabularyLanguage.ENGLISH))
        assertTrue(VocabularyCatalog.shouldStayBehindCore(VocabularyCategory.NAME))
    }

    @Test
    fun rankingIsDeterministicDiverseAndCappedAtThree() {
        val first = visible(englishQuery("hel"))
        assertEquals(first, visible(englishQuery("hel")))
        assertEquals(first.distinct(), first)
        assertTrue(first.size <= 3)
        val collapsed = PredictionPipeline.diversify(listOf("hello", "hellos", "hello's", "help"))
        assertTrue(collapsed.toString(), collapsed.none { it == "hellos" || it == "hello's" })
        assertTrue("hello" in collapsed)
        assertTrue(CandidateIdentity.sameConcept("hello", "hellos"))
        assertFalse(CandidateIdentity.sameConcept("hello", "help"))
    }

    @Test
    fun suggestionReplacementTouchesOnlyTheCurrentWord() {
        val plan = SuggestionSelectionPlan.create("he", "hello", "say he")
        assertEquals("hello", plan?.replacement)
        assertEquals(2, plan?.deleteCodeUnits)
        assertTrue(plan?.changesText == true)
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("the", "he"))
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
        assertEquals("HELLO", SuggestionSelectionPlan.preserveCapitalization("HE", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("he", "hello"))
    }

    @Test
    fun capitalizationAndPunctuationStayConservative() {
        assertTrue(CapitalizationPolicy.shouldCapitalize(""))
        assertTrue(CapitalizationPolicy.shouldCapitalize("Hello. "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("Hello "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("see https://example.com"))
        assertEquals("Hello", CapitalizationPolicy.applyToWord("hello", "Yes! "))
        assertEquals("hello", CapitalizationPolicy.applyToWord("hello", "say "))
        assertFalse(ShiftPolicy.allowsAutoCapitalization(ShiftLockState.OFF, KeyboardLanguage.ROMAN))
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        val danda = PunctuationSpacing.plan("ठीक छ ", "।")
        assertEquals(1, danda.deleteBefore)
        assertTrue(danda.insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("see https://example.com", ".").insertTrailingSpace)
    }

    @Test
    fun lowConfidenceAndProtectedInputStayClean() {
        assertEquals(listOf("https://example.com"), visible(englishQuery("https://example.com")))
        assertEquals(listOf("user@example.com"), visible(englishQuery("user@example.com")))
        assertEquals(listOf("#nepali"), visible(englishQuery("#nepali")))
        assertTrue(visible(englishQuery("")).isEmpty())
        assertTrue(SuggestionBarState.display(listOf("aa"), "aa").isEmpty())
        assertEquals("Space English", AccessibilityLabels.space(KeyboardLanguage.ENGLISH))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", true))
    }

    private fun visible(query: SuggestionQuery): List<String> = engine.suggest(query).visible()

    private fun englishQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(input, SuggestionLanguage.ENGLISH, previous, previousTwo)

    private fun nepaliQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(input, SuggestionLanguage.NEPALI, previous, previousTwo)

    private fun romanQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(input, SuggestionLanguage.ROMAN, previous, previousTwo)

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
