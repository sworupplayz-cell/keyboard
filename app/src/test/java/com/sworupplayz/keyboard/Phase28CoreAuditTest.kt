package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase28CoreAuditTest {
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
    fun onePipelineCapsSuggestionsAndStaysDeterministic() {
        assertEquals(3, SuggestionEngine.MAX_VISIBLE)
        assertEquals(3, PredictionPipeline.MAX_VISIBLE)
        val first = visible(englishQuery("hel"))
        assertEquals(first, visible(englishQuery("hel")))
        assertEquals(first.distinct(), first)
        assertTrue(first.size <= 3)
        assertTrue(first.toString(), "hello" in first && "help" in first && "he'll" in first)
        val slots = SuggestionBarState.gboardSlots(first)
        assertEquals(3, slots.size)
        assertTrue(slots[1].primary)
        assertTrue(visible(englishQuery("")).isEmpty())
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertEquals(
            MixedSuggestionPolicy.nextWords(SuggestionLanguage.ENGLISH, "good", null).take(3),
            PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES).predict("good")
        )
    }

    @Test
    fun prefixPersonalFrequencyAndRecencyShareOneRanker() {
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
        assertTrue(adapted.toString(), "hello" in adapted || "he'll" in adapted)
        assertTrue(store.decayedScore("help") >= store.decayedScore("hello"))
        assertTrue(store.recencyRank("hello") < store.recencyRank("help") || store.scoreOf("help") > store.scoreOf("hello"))
        assertTrue("morning" in visible(englishQuery("morn")))
        assertTrue(CandidateIdentity.sameConcept("hello", "hellos"))
        assertFalse(CandidateIdentity.sameConcept("hello", "help"))
        assertFalse(CandidateIdentity.sameConcept("go", "going"))
    }

    @Test
    fun nextWordContextCoversEnglishNepaliRomanAndMixed() {
        assertTrue("morning" in visible(englishQuery("", previous = "good")))
        assertTrue("you" in visible(englishQuery("", previous = "are", previousTwo = "how are")))
        val afterMorning = visible(englishQuery("", previous = "morning", previousTwo = "good morning"))
        assertTrue(afterMorning.toString(), afterMorning.any { it in setOf("everyone", "guys") })
        assertTrue("you" in PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES).predict("see you"))
        val afterIAm = visible(englishQuery("", previous = "am", previousTwo = "i am"))
        assertTrue(afterIAm.toString(), afterIAm.any { it in setOf("going", "here", "fine") })

        assertTrue(visible(nepaliQuery("", previous = "मलाई")).any { "मन" in it || it == "नेपाली" })
        assertTrue("पर्छ" in visible(nepaliQuery("", previous = "मन", previousTwo = "मलाई मन")))
        assertTrue(
            visible(nepaliQuery("", previous = "कहाँ", previousTwo = "तिमी कहाँ"))
                .any { it in setOf("छौ", "जान्छौ") }
        )
        val nepaliPhrases = PhrasePredictor(seed = PhrasePredictor.NEPALI_PHRASES)
        assertTrue("जान्छु" in nepaliPhrases.predict("घर", previousTwo = "म घर"))
        assertTrue("जान्छु" in nepaliPhrases.predict("स्कुल", previousTwo = "म स्कुल"))

        val romanPhrases = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES)
        assertTrue(romanPhrases.predict("ghar", previousTwo = "ma ghar").any { it in setOf("jaanchu", "janchu") })
        assertTrue(romanPhrases.predict("school", previousTwo = "ma school").any { it in setOf("jaanchu", "gaye", "janchu") })
        assertTrue(romanPhrases.predict("malai").any { it in setOf("मन", "manparcha", "nepali") })
        assertTrue("thik xa" in romanPhrases.predict("xa", previousTwo = "k xa"))
        assertNull(PredictionPipeline.effectivePrevious("?"))
    }

    @Test
    fun englishTyposNepaliMorphologyAndRomanVariantsStaySuggestions() {
        assertTrue("hello" in visible(englishQuery("helo")))
        assertTrue("the" in TypoCorrector.commonCorrections("teh"))
        assertTrue("receive" in TypoCorrector.commonCorrections("recieve"))
        assertTrue("address" in TypoCorrector.commonCorrections("adress"))
        assertTrue("because" in TypoCorrector.commonCorrections("becuase"))
        assertTrue("weird" in TypoCorrector.commonCorrections("wierd"))
        assertTrue("necessary" in TypoCorrector.commonCorrections("neccessary"))
        assertTrue("separate" in TypoCorrector.commonCorrections("seperate"))
        assertFalse(TypoCorrector.isConservativeTypo("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))

        assertTrue("घरमा" in Morphology.nepaliRelatives("घर"))
        assertTrue("घरको" in Morphology.nepaliRelatives("घर"))
        assertTrue("जान्छु" in Morphology.nepaliRelatives("जानु"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("घर", "घरमा"))

        assertEquals("म", converter.bestConversion("ma"))
        assertEquals("मलाई", converter.bestConversion("malai"))
        assertEquals("मलाई", converter.bestConversion("malaai"))
        assertEquals("घर", converter.bestConversion("ghar"))
        assertEquals("जान्छु", converter.bestConversion("jaanchu"))
        assertEquals("जान्छु", converter.bestConversion("janchu"))
        assertEquals("जान्छौ", converter.bestConversion("janchhau"))
        assertEquals("राम्रो", converter.bestConversion("ramro"))
        assertEquals("छ", converter.bestConversion("xa"))
        assertEquals("छु", converter.bestConversion("xu"))
        assertEquals("धन्यवाद", converter.bestConversion("dhanyabaad"))
        assertEquals("ठीक छ", converter.convertText("thik xa"))
        assertEquals("के छ", converter.convertText("ke xa"))
        assertEquals("के छ", converter.bestConversion("kxa"))
        val ghar = visible(romanQuery("ghar"))
        assertTrue("घर" in ghar)
        assertTrue(ghar.any { it in setOf("घरमा", "घरको", "ghar") })
    }

    @Test
    fun mixedLanguageAndProperNounsStayWhereTheUserPutThem() {
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("म Kathmandu जान्छु", converter.convertText("ma Kathmandu janchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("आज school जानु पर्छ", converter.convertText("aaja school jaanu parcha"))
        assertEquals("मलाई game खेल्न मनपर्छ", converter.convertText("malai game khelna manparcha"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("college", converter.bestConversion("college"))
        assertEquals("computer", converter.bestConversion("computer"))
        assertEquals("internet", converter.bestConversion("internet"))
        assertEquals("game", converter.bestConversion("game"))
        assertEquals(
            KeyboardLanguage.ENGLISH,
            LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar")
        )
        assertTrue(LanguageIntelligencePolicy.staysInSelectedMode(KeyboardLanguage.ROMAN, "hello"))
        assertEquals(VocabularyCategory.PLACE, VocabularyCatalog.classify("Kathmandu", VocabularyLanguage.ENGLISH))
    }

    @Test
    fun learningIsBoundedAndRejectsSensitiveTokens() {
        val learned = LearnedWordStore(limit = 2)
        assertTrue(learned.record("Zorplet"))
        learned.record("localslang")
        learned.record("newnickname")
        assertEquals(2, learned.size())
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("4111111111111111"))
        assertFalse(LearnedWordStore().record("https://example.com"))
        assertEquals(listOf("https://example.com"), visible(englishQuery("https://example.com")))
        assertEquals(listOf("user@example.com"), visible(englishQuery("user@example.com")))
        assertTrue(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.LOCAL_DATA_KEYS)
        assertFalse(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.ALL_SETTING_KEYS)
        assertFalse(EditorFieldPolicy.shouldLearn(EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(EditorFieldPolicy.shouldSuggest(EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD))
    }

    @Test
    fun touchContractsAndCoreTypingStillHold() {
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertTrue(TouchRecognition.shouldKeepCurrentKey(20f, 20f, 24f, 22f, 80, 80))
        val mid = TouchCandidate("w", TouchRect(80f, 0f, 160f, 80f), KeyEdge.MIDDLE, 0)
        val row = listOf(
            TouchCandidate("q", TouchRect(0f, 0f, 80f, 80f), KeyEdge.START, 0),
            mid,
            TouchCandidate("e", TouchRect(160f, 0f, 240f, 80f), KeyEdge.END, 0)
        )
        assertEquals("w", TouchGeometryPolicy.resolve(row, 120f, 40f))
        assertTrue(TouchGeometryPolicy.isCenterHit(mid.rect, 120f, 40f))
        assertTrue(TouchRecognition.shouldSlideCorrect(true, TouchGesture.SLIDE, "q", "w"))
        assertFalse(TouchRecognition.shouldSlideCorrect(false, TouchGesture.SLIDE, "q", "w"))
        val path = TouchTrajectory()
        path.start(20f, 20f, 1_000L)
        path.move(22f, 21f, 1_430L)
        assertTrue(TouchRecognition.shouldOpenAlternates(path, stillOnKey = true, now = 1_430L))
        val state = TouchRecognitionState()
        assertTrue(state.tryAcquire(1, "a", 10f, 10f, 1_000L))
        assertFalse(state.tryAcquire(2, "s", 40f, 10f, 1_010L))
        assertEquals(32L, KeyTouchPolicy.KEY_BOUNCE_MS)
        assertFalse(CoreTypingPolicy.allowsPredictionOnMove())
        assertFalse(CoreTypingPolicy.allowsKeyboardRebuildOnMove())
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("I am going", replace("I am go", "go", "going"))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertFalse(ShiftPolicy.consumesOnPunctuation())
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        val cacheFirst = english.suggestions("hel")
        val size = english.cacheSize()
        assertEquals(cacheFirst, english.suggestions("hel"))
        assertEquals(size, english.cacheSize())
        assertTrue(english.cacheSize() <= LocalWordSuggester.PREFIX_CACHE_LIMIT)
    }

    private fun replace(field: String, typed: String, suggestion: String): String {
        val plan = SuggestionSelectionPlan.create(typed, suggestion, field)!!
        return field.substring(0, field.length - plan.deleteCodeUnits) + plan.replacement
    }

    private fun visible(query: SuggestionQuery): List<String> = engine.suggest(query).visible()

    private fun englishQuery(input: String, previous: String? = null, previousTwo: String? = null) =
        SuggestionQuery(input, SuggestionLanguage.ENGLISH, previous, previousTwo)

    private fun nepaliQuery(input: String, previous: String? = null, previousTwo: String? = null) =
        SuggestionQuery(input, SuggestionLanguage.NEPALI, previous, previousTwo)

    private fun romanQuery(input: String) = SuggestionQuery(input, SuggestionLanguage.ROMAN)

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
