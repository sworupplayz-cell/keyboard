package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase29ProductionUxTest {
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
    fun suggestionBarStaysThreeStableSlots() {
        val first = engine.suggest(SuggestionQuery("hel", SuggestionLanguage.ENGLISH)).visible()
        assertEquals(first, engine.suggest(SuggestionQuery("hel", SuggestionLanguage.ENGLISH)).visible())
        assertTrue(first.size <= 3)
        val slots = SuggestionBarState.gboardSlots(first)
        assertEquals(3, slots.size)
        assertTrue(slots[1].primary)
        assertTrue(SuggestionBarStyle.hasStableSlots(first))
        assertFalse(SuggestionBarStyle.showsEmptyHint(first))
        assertTrue(SuggestionBarStyle.showsEmptyHint(emptyList()))
        assertTrue(SuggestionBarState.unchanged(first, first))
        assertEquals(KeyboardTheme.SUGGESTION_TEXT_SP + 1f, SuggestionBarStyle.textSizeSp(true, "hello"), 0.01f)
    }

    @Test
    fun suggestionAcceptanceIsCursorSafeAndKeepsPunctuation() {
        assertEquals("hello world", SuggestionAcceptanceUx.apply("hello wor", "ld", "wor", "world"))
        assertEquals("hello world!", SuggestionAcceptanceUx.apply("hello wor", "ld!", "wor", "world"))
        assertEquals("hello world,", SuggestionAcceptanceUx.apply("hello wor", "ld,", "wor", "world"))
        assertEquals("hello world😊", SuggestionAcceptanceUx.apply("hello wor", "ld😊", "wor", "world"))
        assertNull(SuggestionSelectionPlan.create("wor", "world", "hello wor!"))
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
        assertTrue(CoreTypingPolicy.shouldAcceptSuggestion("world", 0L, "word", 10L))
        assertFalse(CoreTypingPolicy.shouldAcceptSuggestion("world", 100L, "world", 120L))
    }

    @Test
    fun typosAreSuggestionsAndProperNounsStayTyped() {
        assertTrue(AutocorrectUxPolicy.neverAutoReplaces())
        assertTrue(AutocorrectUxPolicy.shouldOfferCorrection("helo", "hello"))
        assertTrue(AutocorrectUxPolicy.shouldOfferCorrection("teh", "the"))
        assertTrue(AutocorrectUxPolicy.shouldOfferCorrection("becuase", "because"))
        assertTrue(AutocorrectUxPolicy.shouldPreserveTyped("Sworup"))
        assertTrue(AutocorrectUxPolicy.shouldPreserveTyped("Kathmandu"))
        assertTrue(AutocorrectUxPolicy.shouldPreserveTyped("lol"))
        assertTrue(AutocorrectUxPolicy.shouldPreserveTyped("https://example.com"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("hello" in engine.suggest(SuggestionQuery("helo", SuggestionLanguage.ENGLISH)).visible())
    }

    @Test
    fun languageSwitchAndMixedTypingStayExplicit() {
        assertEquals(KeyboardLanguage.NEPALI, LanguageSwitcher.cycle(KeyboardLanguage.ENGLISH))
        assertEquals(KeyboardLanguage.ROMAN, LanguageSwitcher.cycle(KeyboardLanguage.NEPALI))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageSwitcher.cycle(KeyboardLanguage.ROMAN))
        assertEquals("Language, Nepali", AccessibilityLabels.languageControl(KeyboardLanguage.NEPALI))
        assertEquals("Language, English", AccessibilityLabels.languageControl(KeyboardLanguage.ENGLISH))
        assertEquals("English", AccessibilityLabels.language(KeyboardLanguage.ENGLISH))
        val picker = LanguagePickerState()
        picker.open(KeyboardLanguage.ROMAN)
        assertTrue(picker.items().first { it.option.language == KeyboardLanguage.ROMAN }.selected)
        assertTrue(LanguageSwitchPolicy.shouldCommitComposing(KeyboardLanguage.ROMAN))
        assertFalse(LanguageSwitchPolicy.rewritesCommittedText())
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
    }

    @Test
    fun touchBackspaceEnterAndPanelsKeepExistingContracts() {
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertTrue(TouchRecognition.shouldSlideCorrect(true, TouchGesture.SLIDE, "q", "w"))
        assertFalse(TouchRecognition.shouldSlideCorrect(false, TouchGesture.SLIDE, "q", "w"))
        val state = TouchRecognitionState()
        assertTrue(state.tryAcquire(1, "a", 10f, 10f, 1_000L))
        assertFalse(state.tryAcquire(2, "s", 40f, 10f, 1_010L))
        assertFalse(CoreTypingPolicy.allowsPredictionOnMove())
        assertFalse(BackspaceRepeatPolicy.shouldStart(200))
        assertTrue(BackspaceRepeatPolicy.shouldStart(400))
        val generation = TypingGeneration()
        val seen = generation.current()
        generation.bump()
        assertFalse(generation.isCurrent(seen))
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_SEARCH))
        assertTrue(EnterActionPolicy.shouldInsertNewline(EnterActionPolicy.resolve(null)))
        assertEquals(PanelNavigationResult.CLOSED_OVERLAY, PanelNavigation.consume(true, true, true))
        assertEquals(PanelNavigationResult.COLLAPSED_TOOLS, PanelNavigation.consume(false, true, true))
        assertEquals(PanelNavigationResult.CLOSED_PANEL, PanelNavigation.consume(false, false, true))
        assertTrue(PanelInsertionPolicy.shouldFinishComposing(true))
        assertTrue(ClipboardInsertion.prepareThenInsert(false, {}, { true }, "hello"))
        assertFalse(ClipboardPolicy.shouldStore("password=hunter2"))
    }

    @Test
    fun settingsAppearancePrivacyAndCachesStayHonest() {
        val storage = FakeSettingsStorage()
        val repository = KeyboardSettingsRepository(storage)
        repository.setSuggestions(false)
        repository.setTypoSuggestions(false)
        repository.setVisualTheme(KeyboardVisualTheme.BLUE)
        val restored = KeyboardSettingsRepository(storage).load()
        assertFalse(restored.suggestions)
        assertFalse(restored.typoSuggestions)
        assertEquals(KeyboardVisualTheme.BLUE, restored.visualTheme)
        assertEquals(
            KeyboardPresentationMode.NORMAL,
            OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.FLOATING)
        )
        assertFalse(EditorFieldPolicy.shouldSuggest(EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", true))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Search", AccessibilityLabels.enter(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Emoji 😊", AccessibilityLabels.emoji("😊"))
        assertEquals("Space, Nepali", AccessibilityLabels.space(KeyboardLanguage.NEPALI))
        val cache = SuggestionQueryCache()
        val key = cache.fingerprint(SuggestionLanguage.ENGLISH, "hel", "good", null, true, true)
        cache.remember(key, listOf("hello", "help"))
        assertEquals(listOf("hello", "help"), cache.hit(key))
        cache.invalidate()
        assertNull(cache.hit(key))
        val first = english.suggestions("hel")
        val size = english.cacheSize()
        assertEquals(first, english.suggestions("hel"))
        assertEquals(size, english.cacheSize())
        assertTrue(english.cacheSize() <= LocalWordSuggester.PREFIX_CACHE_LIMIT)
        assertEquals(1, PunctuationSpacing.plan("hello ", ",").deleteBefore)
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
    }

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
