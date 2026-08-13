package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase27LanguageIntelligenceTest {
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
    fun englishPrefixesContractionsAndMorphologyStayUseful() {
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.toString(), "hello" in hel && "help" in hel && "he'll" in hel)
        assertTrue("morning" in visible(englishQuery("morn")))
        val nep = visible(englishQuery("nep"))
        assertTrue(nep.toString(), nep.any { it.equals("Nepal", ignoreCase = true) })
        assertTrue(nep.toString(), nep.any { it.equals("Nepali", ignoreCase = true) })
        assertTrue("can't" in ContractionExpander.expand("cant"))
        assertTrue("I'll" in ContractionExpander.expand("ill"))
        assertTrue("I'd" in ContractionExpander.expand("id"))
        assertTrue("you'll" in ContractionExpander.expand("youll"))
        assertTrue("you've" in ContractionExpander.expand("youve"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("cant", "can't"))
        assertEquals(listOf("play", "plays", "played", "playing"), Morphology.englishRelatives("play"))
        assertTrue("walked" in Morphology.englishRelatives("walk"))
        assertTrue("studying" in Morphology.englishRelatives("study"))
        assertTrue("went" in Morphology.englishRelatives("go"))
        assertTrue("ran" in Morphology.englishRelatives("run"))
        assertTrue(english.contains("weird") || "weird" in englishWords)
        assertTrue(english.contains("guys") || "guys" in englishWords)
        assertTrue(hel.size <= 3)
    }

    @Test
    fun englishTyposAreSuggestedNeverApplied() {
        assertTrue("hello" in visible(englishQuery("helo")))
        assertTrue("the" in visible(englishQuery("teh")))
        assertTrue("receive" in TypoCorrector.commonCorrections("recieve"))
        assertTrue("address" in TypoCorrector.commonCorrections("adress"))
        assertTrue("because" in TypoCorrector.commonCorrections("becuase"))
        assertTrue("definitely" in TypoCorrector.commonCorrections("definately"))
        assertTrue("separate" in TypoCorrector.commonCorrections("seperate"))
        assertTrue("weird" in TypoCorrector.commonCorrections("wierd"))
        assertTrue("necessary" in TypoCorrector.commonCorrections("neccessary"))
        assertFalse(TypoCorrector.isConservativeTypo("helo", "hello"))
        assertTrue(CorrectionPolicy.shouldOfferTypo("helo", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertFalse(CorrectionPolicy.shouldOfferTypo("qz", "hello"))
    }

    @Test
    fun nepaliVocabularyMorphologyAndContextStayEveryday() {
        assertTrue(nepali.contains("म") || "म" in nepaliWords)
        assertTrue(nepali.contains("तिमी") || "तिमी" in nepaliWords)
        assertTrue(nepali.contains("तपाईं") || "तपाईं" in nepaliWords)
        assertTrue(nepali.contains("स्कुल") || "स्कुल" in nepaliWords)
        assertTrue(nepali.contains("घरहरू") || "घरहरू" in nepaliWords)
        assertTrue("घरमा" in Morphology.nepaliRelatives("घर"))
        assertTrue("घरहरू" in Morphology.nepaliRelatives("घर"))
        assertTrue("मान्छेलाई" in Morphology.nepaliRelatives("मान्छे"))
        assertTrue("जान्छु" in Morphology.nepaliRelatives("जानु"))
        assertTrue("जाँदै" in Morphology.nepaliRelatives("जानु"))
        assertTrue("खाँदै" in Morphology.nepaliRelatives("खानु"))
        assertTrue("गरे" in Morphology.nepaliRelatives("गर्नु"))
        assertTrue("मनपरेको" in Morphology.nepaliRelatives("मन"))
        assertTrue(visible(nepaliQuery("मला")).contains("मलाई"))
        assertTrue(visible(nepaliQuery("", previous = "मलाई")).any { "मन" in it })
        assertTrue("पर्छ" in visible(nepaliQuery("", previous = "मन", previousTwo = "मलाई मन")))
        assertFalse(CorrectionPolicy.shouldAutoReplace("घर", "घरमा"))
    }

    @Test
    fun romanAlternateSpellingsNormalizeToOneConcept() {
        assertEquals("म", converter.bestConversion("ma"))
        assertEquals("म", converter.bestConversion("mah"))
        assertEquals("मलाई", converter.bestConversion("malai"))
        assertEquals("मलाई", converter.bestConversion("malay"))
        assertEquals("मलाई", converter.bestConversion("malaii"))
        assertEquals("मलाई", converter.bestConversion("malaai"))
        assertEquals("राम्रो", converter.bestConversion("ramro"))
        assertEquals("राम्रो", converter.bestConversion("ramroo"))
        assertEquals("नेपाली", converter.bestConversion("nepali"))
        assertEquals("नेपाली", converter.bestConversion("nepaali"))
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("छ", converter.bestConversion("xa"))
        assertEquals("छु", converter.bestConversion("chu"))
        assertEquals("छु", converter.bestConversion("xu"))
        assertEquals("जान्छु", converter.bestConversion("janchu"))
        assertEquals("जान्छु", converter.bestConversion("jaanchu"))
        assertEquals("जान्छु", converter.bestConversion("jaanxu"))
        assertEquals("हुन्छ", converter.bestConversion("huncha"))
        assertEquals("हुन्छ", converter.bestConversion("hunxa"))
        assertTrue(CandidateIdentity.sameConcept("ramro", "ramrooo"))
        assertTrue(CandidateIdentity.sameConcept("nepali", "nepaliii"))
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        assertTrue(RomanNepaliConverter.isNepali(converter.bestConversion("zorpa")))
    }

    @Test
    fun mixedLanguageKeepsEnglishTokensAndProperNouns() {
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("म college जान्छु", converter.convertText("ma college janchu"))
        assertEquals("म Kathmandu जान्छु", converter.convertText("ma Kathmandu janchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("I am going घर", converter.convertText("I am going ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("आज school जानु पर्छ", converter.convertText("aaja school jaanu parcha"))
        assertEquals("मलाई game खेल्न मनपर्छ", converter.convertText("malai game khelna manparcha"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("college", converter.bestConversion("college"))
        assertEquals("computer", converter.bestConversion("computer"))
        assertEquals("internet", converter.bestConversion("internet"))
        assertEquals("game", converter.bestConversion("game"))
        assertEquals("घर", converter.bestConversion("ghar"))
        assertEquals("मलाई", converter.bestConversion("malai"))
        assertEquals("कहाँ", converter.bestConversion("kaha"))
        assertEquals(
            KeyboardLanguage.ENGLISH,
            LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar")
        )
        assertTrue(LanguageIntelligencePolicy.staysInSelectedMode(KeyboardLanguage.ROMAN, "hello"))
    }

    @Test
    fun nextWordSeedsStayCompactAndConversational() {
        assertTrue("morning" in visible(englishQuery("", previous = "good")))
        assertTrue("you" in visible(englishQuery("", previous = "are", previousTwo = "how are")))
        val afterI = PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES).predict("i")
        assertTrue(afterI.toString(), afterI.any { it in setOf("am", "will", "want", "have") })
        val afterIAm = visible(englishQuery("", previous = "am", previousTwo = "i am"))
        assertTrue(afterIAm.toString(), afterIAm.any { it in setOf("going", "here", "fine") })
        assertTrue(visible(nepaliQuery("", previous = "मलाई")).any { "मन" in it })
        assertTrue("पर्छ" in visible(nepaliQuery("", previous = "मन", previousTwo = "मलाई मन")))
        val afterMa = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES).predict("ma")
        assertTrue(afterMa.toString(), afterMa.any { it in setOf("घर", "school", "college", "ghar") })
        assertTrue(
            visible(romanQuery("", previous = "school", previousTwo = "ma school"))
                .any { it in setOf("जान्छु", "jaanchu", "gaye") }
        )
        assertNull(PredictionPipeline.effectivePrevious("?"))
        assertEquals("hello", PredictionPipeline.effectivePrevious("hello"))
    }

    @Test
    fun personalizationAndPrivacyStayBounded() {
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
        assertTrue(store.decayedScore("help") >= store.decayedScore("hello"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("4111111111111111"))
        assertFalse(LearnedWordStore().record("https://example.com"))
        assertTrue(PersonalDictionary.looksLikePersonalName("Sworup"))
        assertFalse(PersonalDictionary.looksLikePersonalName("qz"))
        assertEquals(listOf("https://example.com"), visible(englishQuery("https://example.com")))
        assertEquals(listOf("user@example.com"), visible(englishQuery("user@example.com")))
    }

    @Test
    fun emojiCapitalizationAndAbbreviationsStayConservative() {
        EmojiCatalog.resetToBuiltin()
        assertEquals("😊", EmojiSuggestionPolicy.suggest("happy"))
        assertEquals("❤", EmojiSuggestionPolicy.suggest("love"))
        assertEquals("😢", EmojiSuggestionPolicy.suggest("sad"))
        assertEquals("😂", EmojiSuggestionPolicy.suggest("laugh"))
        assertEquals("🔥", EmojiSuggestionPolicy.suggest("fire"))
        assertEquals("⚽", EmojiSuggestionPolicy.suggest("football"))
        assertEquals("🎂", EmojiSuggestionPolicy.suggest("birthday"))
        assertNull(EmojiSuggestionPolicy.suggest("h"))
        assertNull(EmojiSuggestionPolicy.suggest("he"))
        assertNull(EmojiSuggestionPolicy.suggest("ha"))
        assertTrue(CapitalizationPolicy.shouldCapitalize(""))
        assertTrue(CapitalizationPolicy.shouldCapitalize("Hello. "))
        assertTrue(CapitalizationPolicy.shouldCapitalize("ready? "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("Hello "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("see https://example.com"))
        assertFalse(CapitalizationPolicy.shouldCapitalize("mail user@example.com"))
        assertFalse(ShiftPolicy.allowsAutoCapitalization(ShiftLockState.OFF, KeyboardLanguage.ROMAN))
        assertFalse(ShiftPolicy.allowsAutoCapitalization(ShiftLockState.OFF, KeyboardLanguage.NEPALI))
        assertTrue(ChatAbbreviation.isKnown("lol"))
        assertTrue(ChatAbbreviation.isKnown("idk"))
        assertTrue("lol" in ChatAbbreviation.matches("lo"))
        assertEquals(VocabularyCategory.SLANG, VocabularyCatalog.classify("lol", VocabularyLanguage.ENGLISH))
        assertTrue(VocabularyCatalog.shouldStayBehindCore(VocabularyCategory.SLANG))
        assertEquals(VocabularyCategory.PLACE, VocabularyCatalog.classify("Kathmandu", VocabularyLanguage.ENGLISH))
    }

    @Test
    fun rankingIsDeterministicDiverseAndCached() {
        val first = visible(englishQuery("hel"))
        assertEquals(first, visible(englishQuery("hel")))
        assertEquals(first.distinct(), first)
        assertTrue(first.size <= 3)
        val collapsed = PredictionPipeline.diversify(listOf("hello", "hellos", "hello's", "help"))
        assertTrue(collapsed.toString(), collapsed.none { it == "hellos" || it == "hello's" })
        assertTrue("hello" in collapsed)
        assertTrue("help" in collapsed)
        val cacheFirst = english.suggestions("hel")
        val size = english.cacheSize()
        assertEquals(cacheFirst, english.suggestions("hel"))
        assertEquals(size, english.cacheSize())
        assertTrue(english.cacheSize() <= LocalWordSuggester.PREFIX_CACHE_LIMIT)
        assertFalse(CorrectionPolicy.shouldAutoReplace("play", "playing"))
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
