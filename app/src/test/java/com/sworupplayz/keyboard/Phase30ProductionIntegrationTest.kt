package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase30ProductionIntegrationTest {
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
    fun lifecycleResetsSessionWithoutASecondPredictionSystem() {
        assertTrue(ProductionIntegrationPolicy.shouldResetSession(restarting = false))
        assertFalse(ProductionIntegrationPolicy.shouldResetSession(restarting = true))
        assertTrue(ProductionIntegrationPolicy.shouldInvalidateSuggestionsOnHide())
        assertTrue(ProductionIntegrationPolicy.shouldInvalidateSuggestionsOnAccept())
        assertTrue(ProductionIntegrationPolicy.shouldResetRepeatLearningOnNewField(false))
        assertTrue(ProductionIntegrationPolicy.singlePredictionFacade())
        assertEquals(3, ProductionIntegrationPolicy.maxVisibleSuggestions())
        assertEquals(SuggestionEngine.MAX_VISIBLE, PredictionPipeline.MAX_VISIBLE)
        assertFalse(ProductionIntegrationPolicy.floatingImplemented())
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertFalse(ProductionIntegrationPolicy.allowsNetworkPrediction())
        val generation = TypingGeneration()
        val seen = generation.current()
        generation.bump()
        assertFalse(generation.isCurrent(seen))
        val cache = SuggestionQueryCache()
        val first = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, "how are you"
        )
        val second = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, null
        )
        assertTrue(first != second)
        cache.remember(first, listOf("hello"))
        cache.invalidate()
        assertNull(cache.hit(first))
    }

    @Test
    fun inputConnectionAndCursorReplacementStaySafe() {
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnFieldChange())
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnLanguageSwitch())
        assertFalse(InputConnectionPolicy.restoresComposingAfterPanelClose())
        assertTrue(CursorMovementPolicy.shouldInvalidateSuggestions(true, internal = false))
        assertFalse(CursorMovementPolicy.shouldInvalidateSuggestions(true, internal = true))
        assertEquals("hello world!", SuggestionAcceptanceUx.apply("hello wor", "ld!", "wor", "world"))
        assertEquals("I am going", SuggestionAcceptanceUx.apply("I am go", "", "go", "going"))
        assertNull(SuggestionSelectionPlan.create("wor", "world", "hello wor!"))
        assertEquals("can't", EditorContext.wordAtEnd("I can't"))
        assertEquals("mother-in-law", EditorContext.wordAtEnd("see mother-in-law"))
        assertEquals("", EditorContext.wordAtEnd("hello😊"))
        assertTrue(WordBoundaryPolicy.blocksSuggestionReplacement("user@example.com", "com"))
        assertFalse(InputConnectionCommitter.commit(null, "x"))
    }

    @Test
    fun predictionRomanAndMixedLanguageShareOneRanker() {
        val first = engine.suggest(SuggestionQuery("hel", SuggestionLanguage.ENGLISH)).visible()
        assertEquals(first, engine.suggest(SuggestionQuery("hel", SuggestionLanguage.ENGLISH)).visible())
        assertTrue(first.size <= 3)
        assertTrue(SuggestionBarState.gboardSlots(first)[1].primary)
        assertTrue("hello" in first)
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("hello" in engine.suggest(SuggestionQuery("helo", SuggestionLanguage.ENGLISH)).visible())
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("म Kathmandu जान्छु", converter.convertText("ma Kathmandu janchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("आज school जानु पर्छ", converter.convertText("aaja school jaanu parcha"))
        assertEquals("मलाई game खेल्न मनपर्छ", converter.convertText("malai game khelna manparcha"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("game", converter.bestConversion("game"))
        assertEquals("जान्छु", converter.bestConversion("jaanxu"))
        assertEquals("छ", converter.bestConversion("xa"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        assertFalse(LanguageSwitchPolicy.rewritesCommittedText())
    }

    @Test
    fun privacyFieldsSettingsAndLocalDataStayConsistent() {
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        val pin = EditorFieldPolicy.TYPE_CLASS_NUMBER or EditorFieldPolicy.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(EditorFieldPolicy.shouldLearn(password))
        assertFalse(EditorFieldPolicy.shouldSuggest(password))
        assertFalse(EditorFieldPolicy.shouldLearn(pin))
        assertTrue(EditorFieldPolicy.shouldLearn(EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        assertFalse(ClipboardPolicy.shouldStore("password=hunter2"))
        assertFalse(AdaptiveHitboxPolicy().record("https://evil", "w"))
        assertTrue(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.LOCAL_DATA_KEYS)
        assertFalse(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.ALL_SETTING_KEYS)
        assertFalse(KeyboardPreferences.ALL_SETTING_KEYS.any { it in KeyboardPreferences.LOCAL_DATA_KEYS })

        val storage = FakeSettingsStorage()
        storage.putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
        storage.putString(KeyboardPreferences.KEY_TOUCH_ADAPTATION, "q>w\t1")
        storage.putString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, "1\tnote")
        storage.putBoolean(KeyboardPreferences.KEY_SUGGESTIONS, false)
        KeyboardSettingsRepository(storage).clearLocalData()
        KeyboardPreferences.LOCAL_DATA_KEYS.forEach { assertFalse(storage.contains(it)) }
        assertFalse(KeyboardSettingsRepository(storage).load().suggestions)

        val reset = FakeSettingsStorage()
        KeyboardSettingsRepository(reset).apply {
            setVisualTheme(KeyboardVisualTheme.BLUE)
            setOneHanded(OneHandedAlignment.LEFT)
            setSuggestions(false)
        }
        reset.putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "keep\t1")
        KeyboardSettingsRepository(reset).resetAllSettings()
        val restored = KeyboardSettingsRepository(reset).load()
        assertEquals(KeyboardVisualTheme.FOLLOW_APPEARANCE, restored.visualTheme)
        assertEquals(OneHandedAlignment.OFF, restored.oneHanded)
        assertTrue(restored.suggestions)
        assertEquals("keep\t1", reset.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
        assertEquals(
            KeyboardPresentationMode.NORMAL,
            OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.FLOATING)
        )
    }

    @Test
    fun touchShiftPunctuationAndAccessibilityContractsHold() {
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertFalse(CoreTypingPolicy.allowsPredictionOnMove())
        assertFalse(CoreTypingPolicy.allowsKeyboardRebuildOnMove())
        val state = TouchRecognitionState()
        assertTrue(state.tryAcquire(1, "a", 10f, 10f, 1_000L))
        assertFalse(state.tryAcquire(2, "s", 40f, 10f, 1_010L))
        state.release(1)
        assertNull(state.ownerId)
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertFalse(ShiftPolicy.consumesOnPunctuation())
        assertFalse(ShiftPolicy.consumesOnSpace())
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.NEPALI))
        assertEquals(1, PunctuationSpacing.plan("hello ", ",").deleteBefore)
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertFalse(BackspaceRepeatPolicy.shouldStart(200))
        assertTrue(BackspaceRepeatPolicy.shouldStart(400))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Language, Nepali", AccessibilityLabels.languageControl(KeyboardLanguage.NEPALI))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Search", AccessibilityLabels.enter(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Space, English", AccessibilityLabels.space(KeyboardLanguage.ENGLISH))
        assertEquals(PanelNavigationResult.CLOSED_OVERLAY, PanelNavigation.consume(true, true, true))
        val first = english.suggestions("hel")
        val size = english.cacheSize()
        assertEquals(first, english.suggestions("hel"))
        assertEquals(size, english.cacheSize())
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
