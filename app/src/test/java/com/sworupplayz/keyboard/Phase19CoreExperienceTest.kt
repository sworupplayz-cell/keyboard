package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase19CoreExperienceTest {
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
    fun nextWordPredictionUsesOneAndTwoWordContext() {
        val afterGood = visible(englishQuery("", previous = "good"))
        assertTrue(afterGood.toString(), "morning" in afterGood)
        assertTrue(afterGood.toString(), afterGood.any { it in setOf("luck", "night", "job") })
        assertTrue(afterGood.size <= 3)

        val howAre = visible(englishQuery("", previous = "are", previousTwo = "how are"))
        assertTrue(howAre.toString(), "you" in howAre)

        val iAm = visible(englishQuery("", previous = "am", previousTwo = "i am"))
        assertTrue(iAm.toString(), iAm.any { it in setOf("fine", "going", "घर") })

        val malai = visible(nepaliQuery("", previous = "मलाई"))
        assertTrue(malai.toString(), malai.any { it == "मन" || "मन" in it })
        assertTrue(malai.toString(), malai.any { it == "नेपाली" || "मन" in it })
    }

    @Test
    fun suggestionBarPutsThePrimaryCandidateInTheCenter() {
        val slots = SuggestionBarState.gboardSlots(listOf("hello", "help", "he'll"))
        assertEquals("help", slots[0].text)
        assertEquals("hello", slots[1].text)
        assertEquals("he'll", slots[2].text)
        assertTrue(slots[1].primary)
        assertFalse(slots[0].primary)
        assertEquals("hello", SuggestionBarState.gboardSlots(listOf("hello")).first { it.primary }.text)
        assertEquals(3, SuggestionBarState.gboardSlots(listOf("hello")).size)
    }

    @Test
    fun emojiSuggestionsStayHighConfidenceAndDoNotFlood() {
        EmojiCatalog.resetToBuiltin()
        assertEquals("❤", EmojiSuggestionPolicy.suggest("heart"))
        assertEquals("❤", EmojiSuggestionPolicy.suggest("love"))
        assertEquals("😊", EmojiSuggestionPolicy.suggest("happy"))
        assertEquals("😢", EmojiSuggestionPolicy.suggest("sad"))
        assertEquals("😂", EmojiSuggestionPolicy.suggest("laugh"))
        assertEquals("🔥", EmojiSuggestionPolicy.suggest("fire"))
        assertEquals("⚽", EmojiSuggestionPolicy.suggest("football"))
        assertEquals("🎂", EmojiSuggestionPolicy.suggest("birthday"))
        assertNull(EmojiSuggestionPolicy.suggest("he"))
        val heart = engine.suggest(englishQuery("heart")).visible()
        assertTrue(heart.size <= 3)
        assertTrue(heart.any { it == "heart" || it.contains("heart", ignoreCase = true) } || "❤" in heart)
    }

    @Test
    fun personalDictionaryRejectsGarbageAndStaysBounded() {
        assertFalse(PersonalDictionary.shouldAccept("aa"))
        assertFalse(PersonalDictionary.shouldAccept("https://example.com"))
        assertFalse(PersonalDictionary.shouldAccept("user@example.com"))
        assertFalse(PersonalDictionary.shouldAccept("password=hunter2"))
        assertFalse(PersonalDictionary.shouldAccept("Authorization: Bearer abcdefghijklmnop"))
        assertFalse(PersonalDictionary.shouldAccept("aaaaaaaaaaaaaaaa1234"))
        assertTrue(PersonalDictionary.shouldAccept("Sworup"))
        assertTrue(PersonalDictionary.shouldLearnRepeated("Zorplet", 2, english::contains))
        assertFalse(PersonalDictionary.shouldLearnRepeated("Zorplet", 1, english::contains))
        assertEquals(250, PersonalDictionary.MAX_WORDS)
        assertEquals(60, PersonalDictionary.MAX_RECENT)
        assertEquals(400, PersonalDictionary.MAX_CONTEXT_PAIRS)
    }

    @Test
    fun conservativeTyposAreOfferedButNeverApplied() {
        assertTrue("hello" in visible(englishQuery("helo")))
        assertTrue("the" in visible(englishQuery("teh")))
        assertTrue("receive" in visible(englishQuery("recieve")) || "receive" in TypoCorrector.commonCorrections("recieve"))
        assertTrue("nepali" in TypoCorrector.commonCorrections("nepai") { true })
        assertFalse("hello" in english.suggestions("helo"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("recieve", "receive"))
    }

    @Test
    fun romanNepaliExpandsEverydayVariantsAndKeepsMixedEnglish() {
        assertEquals("म", converter.bestConversion("ma"))
        assertEquals("मलाई", converter.bestConversion("malai"))
        assertEquals("मेरो", converter.bestConversion("mero"))
        assertEquals("तिमी", converter.bestConversion("timi"))
        assertTrue(converter.bestConversion("tapai").startsWith("तपाई"))
        assertEquals("कहाँ", converter.bestConversion("kaha"))
        assertEquals("कस्तो", converter.bestConversion("kasto"))
        assertEquals("किन", converter.bestConversion("kina"))
        assertEquals("घर", converter.bestConversion("ghar"))
        assertEquals("जान्छु", converter.bestConversion("jaanchu"))
        assertEquals("जान्छु", converter.bestConversion("janchu"))
        assertEquals("गर्छु", converter.bestConversion("garchu"))
        assertEquals("गर्दै", converter.bestConversion("gardai"))
        assertEquals("खान्छु", converter.bestConversion("khanchu"))
        assertEquals("बस्छु", converter.bestConversion("baschu"))
        assertEquals("आउँछु", converter.bestConversion("auchu"))
        assertEquals("आउँछु", converter.bestConversion("aaunchu"))
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("छौ", converter.bestConversion("chau"))
        assertEquals("नेपाली", converter.bestConversion("nepali"))
        assertEquals("राम्रो", converter.bestConversion("ramro"))
        assertEquals("नराम्रो", converter.bestConversion("naramro"))
        assertEquals("मनपर्छ", converter.bestConversion("manparcha"))
        assertEquals("मनपर्छ", converter.bestConversion("manparchha"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("college", converter.bestConversion("college"))
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        val ma = visible(romanQuery("ma"))
        assertTrue(ma.toString(), "म" in ma)
        assertTrue(ma.toString(), "मा" in ma || "मलाई" in ma)
    }

    @Test
    fun languageModesStayWhereTheUserPutThem() {
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        assertEquals(KeyboardLanguage.NEPALI, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.NEPALI, "hello"))
        assertTrue(LanguageIntelligencePolicy.prefersEnglish(KeyboardLanguage.ENGLISH))
        assertTrue(LanguageIntelligencePolicy.prefersNepali(KeyboardLanguage.NEPALI))
        assertTrue(LanguageIntelligencePolicy.prefersRomanConversion(KeyboardLanguage.ROMAN))
        assertTrue(LanguageIntelligencePolicy.allowMixedEnglish(KeyboardLanguage.ROMAN))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
    }

    @Test
    fun punctuationQuotesAndEmojiStayConservative() {
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        assertEquals(",", comma.text)
        assertTrue(comma.insertTrailingSpace)

        val danda = PunctuationSpacing.plan("ठिक छ ", "।")
        assertEquals(1, danda.deleteBefore)
        assertTrue(danda.insertTrailingSpace)

        val open = PunctuationSpacing.plan("hello", "(")
        assertTrue(open.insertLeadingSpace)
        assertFalse(open.insertTrailingSpace)

        val close = PunctuationSpacing.plan("hello(yes ", ")")
        assertEquals(1, close.deleteBefore)

        val quoteOpen = PunctuationSpacing.plan("say", "\"")
        assertTrue(quoteOpen.insertLeadingSpace)

        val afterEmoji = PunctuationSpacing.plan("hello 😊 ", "!")
        assertEquals(1, afterEmoji.deleteBefore)
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
    }

    @Test
    fun shiftUsesOneShotAndCapsLockOnlyInEnglish() {
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1_000L, 0L, KeyboardLanguage.ENGLISH))
        assertEquals(
            ShiftLockState.CAPS_LOCK,
            ShiftPolicy.tap(ShiftLockState.ONE_SHOT, 1_200L, 1_000L, KeyboardLanguage.ENGLISH)
        )
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.CAPS_LOCK, 2_000L, 1_200L, KeyboardLanguage.ENGLISH))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertEquals(ShiftLockState.CAPS_LOCK, ShiftPolicy.afterLetter(ShiftLockState.CAPS_LOCK))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.NEPALI))
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.ROMAN))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.ONE_SHOT, 50L, 1L, KeyboardLanguage.ROMAN))
        assertEquals("⇪", ShiftPolicy.label(ShiftLockState.CAPS_LOCK))
        assertTrue(ShiftPolicy.allowsAutoCapitalization(ShiftLockState.OFF, KeyboardLanguage.ENGLISH))
        assertFalse(ShiftPolicy.allowsAutoCapitalization(ShiftLockState.OFF, KeyboardLanguage.NEPALI))
        val capsLayout = KeyboardLayouts.english(shifted = true, capsLock = true).flatten()
        assertTrue(capsLayout.any { it.action == KeyAction.SHIFT && it.label == "⇪" })
    }

    @Test
    fun suggestionReplacementStaysOnTheCurrentToken() {
        assertEquals("say hello", replace("say he", "he", "hello"))
        assertEquals("मलाई", replace("मला", "मला", "मलाई"))
        assertNull(SuggestionSelectionPlan.create("he", "hello", "say he!"))
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("the", "he"))
        assertTrue(SuggestionSelectionPlan.matchesCurrentWord("hi 😊", "😊"))
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
    }

    @Test
    fun settingsSectionsAndLearningPersist() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        repository.setSuggestions(false)
        repository.setTypoSuggestions(false)
        repository.setLearnedWords(false)
        repository.setEmojiRecents(false)
        val restored = KeyboardSettingsRepository(storage).load()
        assertFalse(restored.suggestions)
        assertFalse(restored.typoSuggestions)
        assertFalse(restored.learnedWords)
        assertFalse(restored.emojiRecents)
        storage.putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
        repository.clearLearnedWords()
        assertFalse(storage.contains(KeyboardPreferences.KEY_LEARNED_ENGLISH))
    }

    @Test
    fun accessibilityAndStorageLimitsStayDocumented() {
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", primary = true))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Shift", AccessibilityLabels.shift(false))
        assertTrue(LearnedWordStore.DEFAULT_LIMIT <= PersonalDictionary.MAX_WORDS)
        assertTrue(RecentWordStore.DEFAULT_LIMIT <= 80)
        assertTrue(ContextModel.DEFAULT_LIMIT <= 400)
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

    private fun romanQuery(input: String, previous: String? = null, previousTwo: String? = null) =
        SuggestionQuery(input, SuggestionLanguage.ROMAN, previous, previousTwo)

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }

    private class FakeSettingsStorage : SettingsStorage {
        private val values = linkedMapOf<String, Any>()
        override fun contains(key: String): Boolean = key in values
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue
        override fun getString(key: String): String? = values[key] as? String
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }
}
