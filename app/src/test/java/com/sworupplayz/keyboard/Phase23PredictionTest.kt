package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase23PredictionTest {
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
    fun personalFrequencyAdaptsHelRankingWithoutDroppingUsefulNeighbors() {
        val helpStore = LearnedWordStore()
        repeat(4) { helpStore.record("help") }
        helpStore.record("hello")
        val helpFirst = visible(
            SuggestionQuery(
                input = "hel",
                language = SuggestionLanguage.ENGLISH,
                learned = helpStore.suggestions("hel"),
                personalFrequency = helpStore.frequencyMap("hel")
            )
        )
        assertEquals("help", helpFirst.first())
        assertTrue(helpFirst.toString(), "hello" in helpFirst)
        assertTrue(helpFirst.size <= 3)

        val helloStore = LearnedWordStore()
        repeat(4) { helloStore.record("hello") }
        helloStore.record("help")
        val helloFirst = visible(
            SuggestionQuery(
                input = "hel",
                language = SuggestionLanguage.ENGLISH,
                learned = helloStore.suggestions("hel"),
                personalFrequency = helloStore.frequencyMap("hel")
            )
        )
        assertEquals("hello", helloFirst.first())
        assertTrue("help" in helloFirst || "he'll" in helloFirst)
    }

    @Test
    fun recencyDecaysOlderLearnedWordsWithoutDeletingThem() {
        val learned = LearnedWordStore()
        learned.record("oldslangword")
        repeat(3) { learned.record("newslangword") }
        assertTrue(learned.scoreOf("oldslangword") > 0)
        assertTrue(learned.decayedScore("newslangword") >= learned.decayedScore("oldslangword"))
        assertTrue(learned.recencyRank("newslangword") < learned.recencyRank("oldslangword"))
        assertEquals(2, learned.size())
    }

    @Test
    fun acceptedSuggestionLearningStrengthensBigrams() {
        val phrases = PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES)
        assertTrue(phrases.record("good", "morning"))
        assertTrue(phrases.record("good", "morning"))
        assertTrue("morning" in phrases.predict("good"))
        assertTrue(phrases.strength("good", "morning") >= 2)
        assertTrue(PersonalDictionary.shouldLearnPhrase("good", "morning"))
        assertTrue(PersonalDictionary.shouldLearnPhrase("ma", "ghar"))
        assertTrue(PersonalDictionary.shouldLearnPhrase("how are", "you"))
    }

    @Test
    fun bigramAndTrigramSeedsMatchConversationalExamples() {
        val englishPhrases = PhrasePredictor(seed = PhrasePredictor.ENGLISH_PHRASES)
        assertTrue("morning" in englishPhrases.predict("good"))
        assertTrue("everyone" in englishPhrases.predict("morning", previousTwo = "good morning") ||
            "guys" in englishPhrases.predict("morning", previousTwo = "good morning"))
        assertTrue("are" in englishPhrases.predict("how"))
        assertTrue("you" in englishPhrases.predict("are", previousTwo = "how are"))
        assertTrue("am" in englishPhrases.predict("i"))
        val afterIAm = englishPhrases.predict("am", previousTwo = "i am")
        assertTrue(afterIAm.toString(), afterIAm.any { it in setOf("going", "here", "fine") })

        val nepaliPhrases = PhrasePredictor(seed = PhrasePredictor.NEPALI_PHRASES)
        assertTrue(nepaliPhrases.predict("मलाई").any { "मन" in it })
        assertTrue("पर्छ" in nepaliPhrases.predict("मन", previousTwo = "मलाई मन"))
        assertTrue(nepaliPhrases.predict("तिमी").any { it in setOf("कहाँ", "कस्तो", "कहिले") })
        assertTrue(nepaliPhrases.predict("कहाँ", previousTwo = "तिमी कहाँ").any { it in setOf("छौ", "जान्छौ") })

        val romanPhrases = PhrasePredictor(seed = PhrasePredictor.ROMAN_PHRASES)
        assertTrue(romanPhrases.predict("ma").any { it in setOf("घर", "जान्छु", "school") })
        assertTrue(romanPhrases.predict("ghar", previousTwo = "ma ghar").any { it in setOf("jaanchu", "janchu") })
        assertTrue(romanPhrases.predict("school", previousTwo = "ma school").any { it in setOf("jaanchu", "gaye", "janchu") })
    }

    @Test
    fun englishPrefixesContractionsAndTyposStaySuggestionsOnly() {
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.toString(), "hello" in hel && "help" in hel && "he'll" in hel)
        val goo = visible(englishQuery("goo"))
        assertTrue(goo.toString(), "good" in goo && "going" in goo)
        val tha = visible(englishQuery("tha"))
        assertTrue(tha.toString(), "that" in tha)

        assertTrue("can't" in ContractionExpander.expand("cant"))
        assertTrue("don't" in ContractionExpander.expand("dont"))
        assertTrue("I'm" in ContractionExpander.expand("im"))
        assertTrue("I've" in ContractionExpander.expand("ive"))
        assertTrue("I'll" in ContractionExpander.expand("ill"))
        assertTrue("you're" in ContractionExpander.expand("youre"))
        assertTrue("can't" in visible(englishQuery("cant")))
        assertFalse(CorrectionPolicy.shouldAutoReplace("cant", "can't"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("im", "I'm"))

        assertTrue("hello" in visible(englishQuery("helo")))
        assertTrue("the" in visible(englishQuery("teh")))
        assertTrue("nepali" in visible(englishQuery("nepai")).map { it.lowercase() })
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse(TypoCorrector.isConservativeTypo("helo", "hello"))
    }

    @Test
    fun nepaliAndRomanPrefixesCoverRequestedStems() {
        val mal = visible(nepaliQuery("मला"))
        assertTrue(mal.toString(), "मलाई" in mal)
        val jaan = visible(romanQuery("jaan"))
        assertTrue(jaan.toString(), jaan.any { it in setOf("जान", "जान्छु", "जानु") })
        val ghar = visible(romanQuery("ghar"))
        assertTrue("घर" in ghar)
        assertTrue(ghar.any { it in setOf("घरमा", "घरको", "ghar") })
        val malai = visible(romanQuery("mal"))
        assertTrue("मलाई" in malai)
        assertEquals("मलाई", converter.bestConversion("malay"))
        assertEquals("मलाई", converter.bestConversion("malaii"))
        assertEquals("जान्छु", converter.bestConversion("jaanxu"))
        assertEquals("जान्छु", converter.bestConversion("janchu"))
        assertEquals("कहाँ", converter.bestConversion("kata"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertTrue(CandidateIdentity.sameConcept("ramro", "ramroo"))
        assertTrue(CandidateIdentity.sameConcept("ramroo", "ramrooo"))
        val diverse = PredictionPipeline.diversify(listOf("ramro", "ramroo", "ramrooo", "ramro xa"))
        assertEquals(1, diverse.count { CandidateIdentity.sameConcept(it, "ramro") })
    }

    @Test
    fun mixedLanguageKeepsTheSelectedModeAndEnglishWords() {
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("today म school जान्छु", converter.convertText("today ma school jaanchu"))
        assertEquals("मेरो phone good छ", converter.convertText("mero phone good cha"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        val afterMaSchool = visible(romanQuery("", previous = "school", previousTwo = "ma school"))
        assertTrue(afterMaSchool.toString(), afterMaSchool.any { it == "जान्छु" || it == "jaanchu" || it == "gaye" })
    }

    @Test
    fun namesPlacesAndAbbreviationsStayBehindCorePrefixes() {
        assertEquals(VocabularyCategory.NAME, VocabularyCatalog.classify("ram", VocabularyLanguage.ENGLISH))
        assertEquals(VocabularyCategory.PLACE, VocabularyCatalog.classify("Kathmandu", VocabularyLanguage.ENGLISH))
        assertEquals(VocabularyCategory.SLANG, VocabularyCatalog.classify("lol", VocabularyLanguage.ENGLISH))
        assertTrue(VocabularyCatalog.shouldStayBehindCore(VocabularyCategory.NAME))
        assertTrue(ChatAbbreviation.isKnown("btw"))
        assertTrue(ChatAbbreviation.isKnown("idk"))
        assertTrue("lol" in ChatAbbreviation.matches("lo"))
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.none { it.equals("helicopter", ignoreCase = true) } || hel.first() in setOf("hello", "help", "he'll"))
        assertTrue(english.contains("I've") || "I've" in englishWords)
        assertTrue(english.contains("I'll") || "I'll" in englishWords)
        assertTrue(nepali.contains("मलाईको") || "मलाईको" in nepaliWords)
    }

    @Test
    fun emojiStaysOneHighConfidenceCandidate() {
        EmojiCatalog.resetToBuiltin()
        assertEquals("😊", EmojiSuggestionPolicy.suggest("happy"))
        assertEquals("❤", EmojiSuggestionPolicy.suggest("love"))
        assertEquals("🔥", EmojiSuggestionPolicy.suggest("fire"))
        assertEquals("⚽", EmojiSuggestionPolicy.suggest("football"))
        assertEquals("🎂", EmojiSuggestionPolicy.suggest("birthday"))
        assertEquals("🎉", EmojiSuggestionPolicy.suggest("party"))
        assertEquals("😢", EmojiSuggestionPolicy.suggest("sad"))
        assertEquals("😊", EmojiSuggestionPolicy.suggest("", "happy"))
        assertEquals(null, EmojiSuggestionPolicy.suggest("he"))
        assertEquals(null, EmojiSuggestionPolicy.suggest("ha"))
        val happy = engine.suggest(englishQuery("happy"))
        val glyphs = listOfNotNull(happy.emoji) + happy.visible().filter { EmojiCatalog.contains(it) }
        assertTrue(glyphs.size <= 1)
    }

    @Test
    fun rankingIsDeterministicDedupedAndCappedAtThree() {
        val first = visible(englishQuery("hel"))
        val second = visible(englishQuery("hel"))
        assertEquals(first, second)
        assertEquals(first.distinct(), first)
        assertTrue(first.size <= 3)
        val ranked = SuggestionRanker.rank(
            input = "he",
            prefixMatches = listOf("he", "hello", "help", "he"),
            typoMatches = listOf("hello"),
            learned = emptyList(),
            recent = emptyList(),
            frequencyOf = { listOf("he", "hello", "help").indexOf(it).takeIf { n -> n >= 0 } ?: 9 },
            limit = 3
        )
        assertEquals("he", ranked.first())
        assertEquals(ranked.distinct(), ranked)
        assertTrue(ranked.size <= 3)
    }

    @Test
    fun privacyRejectsSecretsAndLearningStaysBounded() {
        assertTrue(WordLearningPolicy.isGarbage("https://example.com"))
        assertTrue(WordLearningPolicy.isGarbage("user@example.com"))
        assertTrue(WordLearningPolicy.isGarbage("aa"))
        assertTrue(WordLearningPolicy.isGarbage("4111111111111111"))
        assertTrue(WordLearningPolicy.isGarbage("api_key=abcd1234"))
        assertFalse(WordLearningPolicy.isGarbage("Sworup"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        val learned = LearnedWordStore(limit = 2)
        assertTrue(learned.record("Zorplet"))
        learned.record("localslang")
        learned.record("newnickname")
        assertEquals(2, learned.size())
        val context = ContextModel(limit = 2)
        context.record("see", "you")
        context.record("thank", "you")
        context.record("let", "go")
        assertTrue(context.size() <= 2)
        val phrases = PhrasePredictor(limit = 2)
        phrases.record("good", "morning")
        phrases.record("thank", "you")
        phrases.record("see", "you")
        assertTrue(phrases.serialize().lines().filter { it.isNotBlank() }.size <= 2)
    }

    @Test
    fun prefixCacheIsStableAndDoesNotRescanOnRepeatQueries() {
        val first = english.suggestions("hel")
        val sizeAfterFirst = english.cacheSize()
        val second = english.suggestions("hel")
        assertEquals(first, second)
        assertEquals(sizeAfterFirst, english.cacheSize())
        assertTrue(english.cacheSize() <= LocalWordSuggester.PREFIX_CACHE_LIMIT)
        assertTrue(first.size <= 3)
    }

    @Test
    fun contextLimitsDoNotInventAggressivePhrases() {
        assertTrue(visible(englishQuery("")).isEmpty())
        val afterUnknown = visible(englishQuery("", previous = "qzorpxyz"))
        assertTrue(afterUnknown.isEmpty() || afterUnknown.size <= 3)
        assertFalse(CorrectionPolicy.shouldAutoReplace("play", "playing"))
        assertTrue("playing" in Morphology.englishRelatives("play"))
        assertTrue("घरमा" in Morphology.nepaliRelatives("घर"))
        assertTrue("मलाईको" in Morphology.nepaliRelatives("मलाई"))
    }

    private fun visible(query: SuggestionQuery): List<String> = engine.suggest(query).visible()

    private fun englishQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(input, SuggestionLanguage.ENGLISH, previous, previousTwo)

    private fun nepaliQuery(input: String, previous: String? = null) =
        SuggestionQuery(input, SuggestionLanguage.NEPALI, previous)

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
