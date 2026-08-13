package com.sworupplayz.keyboard

import android.view.inputmethod.InputConnection
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase31ImeReliabilityTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }

    @Test
    fun lifecycleSeparatesFieldSessionAndPersistentState() {
        assertEquals(ImeSessionScope.FIELD, ImeSessionState.scopeForComposing())
        assertEquals(ImeSessionScope.FIELD, ImeSessionState.scopeForSuggestions())
        assertEquals(ImeSessionScope.FIELD, ImeSessionState.scopeForEditorInfo())
        assertEquals(ImeSessionScope.INPUT_SESSION, ImeSessionState.scopeForTouchPointers())
        assertEquals(ImeSessionScope.INPUT_SESSION, ImeSessionState.scopeForBackspaceRepeat())
        assertEquals(ImeSessionScope.PERSISTENT, ImeSessionState.scopeForLearnedWords())
        assertEquals(ImeSessionScope.PERSISTENT, ImeSessionState.scopeForSettings())
        assertTrue(ImeSessionState.shouldReset(ImeSessionScope.FIELD, ImeLifecycleEvent.FIELD_CHANGE))
        assertFalse(ImeSessionState.shouldReset(ImeSessionScope.PERSISTENT, ImeLifecycleEvent.FIELD_CHANGE))
        assertFalse(ImeSessionState.shouldReset(ImeSessionScope.PERSISTENT, ImeLifecycleEvent.HIDE))
        assertTrue(ImeSessionState.shouldReset(ImeSessionScope.INPUT_SESSION, ImeLifecycleEvent.HIDE))
        assertTrue(ImeLifecyclePolicy.shouldApplyFieldPolicyOnStartInput())
        assertTrue(ImeLifecyclePolicy.shouldResetFieldState(restarting = false))
        assertFalse(ImeLifecyclePolicy.shouldResetFieldState(restarting = true))
        assertTrue(ImeLifecyclePolicy.shouldResetTransientStateOnHide())
        assertTrue(ImeLifecyclePolicy.shouldFinishComposingOnHide())
        assertFalse(ImeLifecyclePolicy.shouldConvertRomanOnHide())
        assertTrue(ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(false))
        assertFalse(ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(true))
        assertTrue(ImeLifecyclePolicy.shouldPreserveSettingsOnFieldChange())
        assertTrue(ImeLifecyclePolicy.shouldPreserveLearningOnFieldChange())
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnHide())
        val generation = TypingGeneration()
        val seen = generation.current()
        generation.bump()
        assertFalse(generation.isCurrent(seen))
    }

    @Test
    fun inputConnectionIsNullSafeAndDoesNotRewriteUnrelatedText() {
        assertFalse(InputConnectionCommitter.commit(null, "x"))
        assertFalse(InputConnectionCommitter.commitRaw(null, "x"))
        assertFalse(InputConnectionCommitter.finishComposing(null))
        assertFalse(InputConnectionCommitter.setComposing(null, "ma"))
        assertFalse(InputConnectionCommitter.deleteSurrounding(null, 1, 0))
        assertEquals("", InputConnectionCommitter.textBeforeCursor(null, 80))
        assertEquals("", InputConnectionCommitter.textAfterCursor(null, 24))
        assertNull(InputConnectionCommitter.selectedText(null))
        assertFalse(InputConnectionCommitter.performEditorAction(null, EnterActionPolicy.ACTION_SEARCH))
        assertFalse(InputConnectionCommitter.setSelection(null, 0, 1))

        val broken = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, _, _ -> throw IllegalStateException("dead editor") } as InputConnection
        assertFalse(InputConnectionCommitter.commit(broken, "hello"))
        assertFalse(InputConnectionCommitter.finishComposing(broken))
        assertEquals("", InputConnectionCommitter.textBeforeCursor(broken, 16))

        assertEquals("hello world", SuggestionAcceptanceUx.apply("hello wor", "ld", "wor", "world"))
        assertEquals("hello world!", SuggestionAcceptanceUx.apply("hello wor", "ld!", "wor", "world"))
        assertEquals("hello, world", SuggestionAcceptanceUx.apply("hello, wor", "", "wor", "world"))
        assertEquals("hello 😊 world", SuggestionAcceptanceUx.apply("hello 😊 wor", "", "wor", "world"))
        assertEquals("म घर जान्छु", SuggestionAcceptanceUx.apply("म घर जा", "", "जा", "जान्छु"))
        assertEquals("ma school जान्छु", SuggestionAcceptanceUx.apply("ma school jaan", "", "jaan", "जान्छु"))
        assertEquals("I am घर", SuggestionAcceptanceUx.apply("I am ghar", "", "ghar", "घर"))
        assertNull(SuggestionSelectionPlan.create("wor", "world", "hello wor!"))
        assertTrue(WordBoundaryPolicy.blocksSuggestionReplacement("user@example.com", "com"))
        assertTrue(CursorMovementPolicy.preservesCommittedText())
    }

    @Test
    fun editorTypesProtectPasswordsUrlsEmailsAndNumericFields() {
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        val pin = EditorFieldPolicy.TYPE_CLASS_NUMBER or EditorFieldPolicy.TYPE_NUMBER_VARIATION_PASSWORD
        val email = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val url = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_URI
        val number = EditorFieldPolicy.TYPE_CLASS_NUMBER
        val phone = EditorFieldPolicy.TYPE_CLASS_PHONE
        val search = EnterActionPolicy.ACTION_SEARCH
        val multiline = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_FLAG_MULTI_LINE
        assertFalse(EditorFieldPolicy.shouldSuggest(password))
        assertFalse(EditorFieldPolicy.shouldLearn(password))
        assertFalse(EditorFieldPolicy.shouldSuggest(pin))
        assertFalse(EditorFieldPolicy.shouldLearn(pin))
        assertFalse(EditorFieldPolicy.shouldSuggest(number))
        assertFalse(EditorFieldPolicy.shouldSuggest(phone))
        assertFalse(EditorFieldPolicy.shouldLearn(number))
        assertTrue(EditorFieldPolicy.shouldLearn(email))
        assertTrue(EditorFieldPolicy.isEmailField(email))
        assertTrue(EditorFieldPolicy.isUriField(url))
        assertTrue(EditorFieldPolicy.prefersNumberPad(number))
        assertTrue(EditorFieldPolicy.prefersNumberPad(phone))
        assertFalse(EditorFieldPolicy.prefersNumberPad(email))
        assertTrue(ImeLifecyclePolicy.shouldOpenNumberPadOnNewField(number, restarting = false))
        assertFalse(ImeLifecyclePolicy.shouldOpenNumberPadOnNewField(number, restarting = true))
        assertFalse(EditorFieldPolicy.allowsSmartPunctuation(email))
        assertFalse(EditorFieldPolicy.allowsSmartPunctuation(url))
        assertFalse(EditorFieldPolicy.allowsDoubleSpacePeriod(password))
        assertTrue(EditorFieldPolicy.allowsSmartPunctuation(EditorFieldPolicy.TYPE_CLASS_TEXT))
        assertTrue(EditorFieldPolicy.isMultilineField(multiline))
        assertTrue(EnterActionPolicy.shouldPerformAction(search))
        assertTrue(EnterActionPolicy.shouldInsertNewline(EnterActionPolicy.IME_FLAG_NO_ENTER_ACTION))
        assertEquals("password", EditorFieldPolicy.kind(password))
        assertEquals("email", EditorFieldPolicy.kind(email))
        assertEquals("uri", EditorFieldPolicy.kind(url))
        assertEquals("number", EditorFieldPolicy.kind(number))
        assertFalse(PunctuationSpacing.plan("https", ":").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("http", ":").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("user@example", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("https://example.com/path", "?").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
        assertEquals(0, PunctuationSpacing.plan("user@example.com", "@").deleteBefore)
        assertEquals("https", SpecialTokenPolicy.tokenAtEnd("open https"))
        assertTrue(SpecialTokenPolicy.looksLikeUrlPrefix("https"))
        assertTrue(SpecialTokenPolicy.looksLikeEmailInProgress("user@example"))
        assertTrue(SpecialTokenPolicy.isProtectedContext("visit https://ex.com/a?q=1&x=2#top"))
    }

    @Test
    fun composingAndRomanConversionDoNotRewriteCommittedWords() {
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnFieldChange())
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnLanguageSwitch())
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnHide())
        assertFalse(InputConnectionPolicy.restoresComposingAfterPanelClose())
        assertFalse(LanguageSwitchPolicy.rewritesCommittedText())
        assertFalse(ImeLifecyclePolicy.shouldConvertRomanOnHide())
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        val composer = RomanInputComposer(converter)
        composer.type("m")
        composer.type("a")
        assertEquals("ma", composer.currentWord)
        val finished = composer.finishWord()
        assertTrue(finished is RomanEdit.Commit)
        assertEquals("म", (finished as RomanEdit.Commit).text)
        assertEquals("", composer.currentWord)
        composer.type("ghar")
        assertEquals("घर", (composer.finishWord() as RomanEdit.Commit).text)
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
    }

    @Test
    fun touchAndBackspaceStopWithImeLifecycle() {
        assertTrue(ImeTouchLifecycle.shouldResetOnHide())
        assertTrue(ImeTouchLifecycle.shouldResetOnDestroy())
        assertTrue(ImeTouchLifecycle.shouldResetOnPanelOpen())
        assertFalse(ImeTouchLifecycle.shouldDeliverEventAfterHide(false))
        assertTrue(ImeTouchLifecycle.shouldDeliverEventAfterHide(true))
        assertFalse(ImeTouchLifecycle.secondFingerSteals())
        assertFalse(ImeTouchLifecycle.allowsRebuildOnMove())
        assertFalse(ImeTouchLifecycle.allowsPredictionOnMove())
        val state = TouchRecognitionState()
        assertTrue(state.tryAcquire(1, "a", 10f, 10f, 1_000L))
        assertFalse(state.tryAcquire(2, "s", 40f, 10f, 1_010L))
        state.reset()
        assertNull(state.ownerId)
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertTrue(KeyTouchPolicy.commitsOnDown(KeyAction.BACKSPACE))
        assertFalse(KeyTouchPolicy.commitsOnDown(KeyAction.TEXT))
        assertFalse(BackspaceRepeatPolicy.shouldStart(200))
        assertTrue(BackspaceRepeatPolicy.shouldStart(400))
        assertEquals(1, BackspaceRepeatPolicy.deletesPerTick(9))
        assertTrue(ImeLifecyclePolicy.shouldStopBackspaceOnHide())
        assertTrue(ImeLifecyclePolicy.shouldStopBackspaceOnFieldChange())
        assertTrue(ImeLifecyclePolicy.shouldStopBackspaceOnDestroy())
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("flag ", GraphemeBackspace.apply("flag 🇳🇵"))
        assertEquals("skin ", GraphemeBackspace.apply("skin 👍🏻"))
        assertEquals("family ", GraphemeBackspace.apply("family 👨‍👩‍👧"))
        assertTrue(WordBoundaryPolicy.isEmojiCodePoint("😊".codePointAt(0)))
        assertTrue(SpacePolicy.allowsSpaceAfterEmoji("hello😊"))
    }

    @Test
    fun shiftLanguageAndSuggestionCacheStaySynchronized() {
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1_000L, 0L, KeyboardLanguage.ENGLISH))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertFalse(ShiftPolicy.consumesOnSpace())
        assertFalse(ShiftPolicy.consumesOnPunctuation())
        assertFalse(ShiftPolicy.consumesOnBackspace())
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.NEPALI))
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.ROMAN))
        assertTrue(LanguageSwitchPolicy.resetShiftOnSwitch())
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        val cache = SuggestionQueryCache()
        val first = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, "how are you"
        )
        val second = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, "why are you"
        )
        assertNotEquals(first, second)
        val emailKind = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, "how are you", "email"
        )
        assertNotEquals(first, emailKind)
        val selected = cache.fingerprint(
            SuggestionLanguage.ENGLISH, "hel", "are", "how are", true, true, "how are you", "", false, true
        )
        assertNotEquals(first, selected)
        cache.remember(first, listOf("hello"))
        assertEquals(listOf("hello"), cache.hit(first))
        cache.invalidate()
        assertNull(cache.hit(first))
        assertTrue(ImeLifecyclePolicy.shouldInvalidateSuggestionsOnHide())
        assertTrue(ImeLifecyclePolicy.shouldInvalidateSuggestionsOnFieldChange(false))
        assertFalse(ImeLifecyclePolicy.shouldInvalidateSuggestionsOnFieldChange(true))
        assertTrue(ImeLifecyclePolicy.shouldInvalidateSuggestionsOnSelectionChange(false))
        assertFalse(ImeLifecyclePolicy.shouldInvalidateSuggestionsOnSelectionChange(true))
    }

    @Test
    fun panelsSettingsScreensAndPrivacyContractsHold() {
        assertEquals(PanelNavigationResult.CLOSED_OVERLAY, PanelNavigation.consume(true, true, true))
        assertEquals(PanelNavigationResult.COLLAPSED_TOOLS, PanelNavigation.consume(false, true, true))
        assertEquals(PanelNavigationResult.CLOSED_PANEL, PanelNavigation.consume(false, false, true))
        assertFalse(PanelNavigation.isTemporaryPanel(letters = true, vowels = false))
        assertTrue(PanelNavigation.isTemporaryPanel(letters = false, vowels = false))
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertFalse(ProductionIntegrationPolicy.floatingImplemented())
        assertEquals(0, OneHandedLayoutPolicy.insets(320, OneHandedAlignment.LEFT).startDp)
        assertEquals(46, KeyboardUiMetrics.keyHeightDp(320, 568, false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, false))
        assertEquals(42, KeyboardUiMetrics.keyHeightDp(640, 360, true))
        assertTrue(KeyboardUiMetrics.cappedLetterTextSp(18f, 2.0f) < 18f)
        assertEquals(7, KeyboardUiMetrics.emojiColumns(320))
        assertTrue(KeyboardUiMetrics.equalKeyWidthDp(320, 10) > 0f)
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", true))
        assertEquals("Language, Nepali", AccessibilityLabels.languageControl(KeyboardLanguage.NEPALI))
        assertEquals("Language, Roman Nepali", AccessibilityLabels.languageControl(KeyboardLanguage.ROMAN))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Shift", AccessibilityLabels.shift(false))
        assertEquals("Backspace", AccessibilityLabels.key(KeySpec("⌫", KeyAction.BACKSPACE)))
        assertEquals("Search", AccessibilityLabels.enter(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Space, English", AccessibilityLabels.space(KeyboardLanguage.ENGLISH))
        assertEquals(250, ImeMemoryPolicy.learnedWordLimit())
        assertEquals(60, ImeMemoryPolicy.recentWordLimit())
        assertEquals(48, ImeMemoryPolicy.adaptivePairLimit())
        assertEquals(3, ImeMemoryPolicy.maxVisibleSuggestions())
        assertEquals(1, ImeMemoryPolicy.suggestionCacheSlots())
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(ClipboardPolicy.shouldStore("password=hunter2"))
        assertFalse(AdaptiveHitboxPolicy().record("https://evil", "w"))
        assertTrue(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.LOCAL_DATA_KEYS)
        assertFalse(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.ALL_SETTING_KEYS)
        val storage = FakeSettingsStorage()
        storage.putString(KeyboardPreferences.KEY_LEARNED_ENGLISH, "hello\t1")
        KeyboardSettingsRepository(storage).apply {
            setVisualTheme(KeyboardVisualTheme.BLUE)
            setSuggestions(false)
        }
        KeyboardSettingsRepository(storage).resetAllSettings()
        val restored = KeyboardSettingsRepository(storage).load()
        assertEquals(KeyboardVisualTheme.FOLLOW_APPEARANCE, restored.visualTheme)
        assertTrue(restored.suggestions)
        assertEquals("hello\t1", storage.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        assertFalse(ProductionIntegrationPolicy.allowsNetworkPrediction())
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
