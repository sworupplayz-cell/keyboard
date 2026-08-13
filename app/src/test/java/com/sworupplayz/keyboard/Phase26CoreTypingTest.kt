package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase26CoreTypingTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }

    @Test
    fun rapidTypingAcceptsAlternatingKeysAndRepeatedLettersAfterBounce() {
        assertTrue(CoreTypingPolicy.shouldAcceptKey("h", 0L, "e", 8L))
        assertTrue(CoreTypingPolicy.shouldAcceptKey("e", 8L, "l", 16L))
        assertFalse(CoreTypingPolicy.shouldAcceptKey("l", 16L, "l", 30L))
        assertTrue(CoreTypingPolicy.shouldAcceptKey("l", 16L, "l", 50L))
        assertTrue(CoreTypingPolicy.shouldAcceptKey("l", 50L, "o", 55L))
        assertTrue(CoreTypingPolicy.shouldAcceptKey("o", 55L, " ", 60L))
        assertTrue(CoreTypingPolicy.shouldAcceptKey(" ", 60L, ".", 70L))
        assertFalse(CoreTypingPolicy.allowsPredictionOnMove())
        assertFalse(CoreTypingPolicy.allowsKeyboardRebuildOnMove())
        assertFalse(CoreTypingPolicy.allowsVocabularyScanOnMove())
    }

    @Test
    fun delayedBackspaceTicksStopWhenGenerationChanges() {
        val generation = TypingGeneration()
        val seen = generation.current()
        assertTrue(generation.isCurrent(seen))
        assertFalse(BackspaceRepeatPolicy.shouldStart(200))
        assertTrue(BackspaceRepeatPolicy.shouldStart(400))
        assertEquals(1, BackspaceRepeatPolicy.deletesPerTick(9))
        generation.bump()
        assertFalse(generation.isCurrent(seen))
        assertTrue(generation.isCurrent(generation.current()))
    }

    @Test
    fun graphemeBackspaceRemovesMixedEnglishDevanagariAndEmoji() {
        assertEquals("hello", GraphemeBackspace.apply("hello!"))
        assertEquals("caf", GraphemeBackspace.apply("café"))
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("helloनमस्ते", GraphemeBackspace.apply("helloनमस्ते😊"))
        val afterNamaste = GraphemeBackspace.apply("helloनमस्ते")
        assertTrue(afterNamaste.startsWith("hello"))
        assertTrue(afterNamaste.length < "helloनमस्ते".length)
        assertEquals("flag ", GraphemeBackspace.apply("flag 🇳🇵"))
        assertEquals("skin ", GraphemeBackspace.apply("skin 👍🏻"))
        assertEquals("family ", GraphemeBackspace.apply("family 👨‍👩‍👧"))
        assertEquals("क्ष".length, GraphemeBackspace.codeUnitsToDelete("क्ष"))
        val state = DirectTypingState()
        assertTrue(state.append("क", DirectTypingLanguage.NEPALI))
        assertTrue(state.append("्", DirectTypingLanguage.NEPALI))
        assertTrue(state.append("ष", DirectTypingLanguage.NEPALI))
        assertEquals("क्ष", state.currentWord)
        assertTrue(state.backspace())
        assertEquals("", state.currentWord)
    }

    @Test
    fun wordBoundariesCoverContractionsHyphensAndProtectedTokens() {
        assertEquals("can't", EditorContext.wordAtEnd("I can't"))
        assertTrue(WordBoundaryPolicy.looksLikeContraction("can't"))
        assertEquals("mother-in-law", EditorContext.wordAtEnd("see mother-in-law"))
        assertTrue(WordBoundaryPolicy.looksLikeHyphenatedWord("mother-in-law"))
        assertEquals("janchu", EditorContext.wordAtEnd("ma Kathmandu janchu"))
        assertEquals("Kathmandu", EditorContext.previousWord("ma Kathmandu janchu"))
        assertEquals("जान्छु", EditorContext.wordAtEnd("म घर जान्छु"))
        assertEquals("", EditorContext.wordAtEnd("hello😊"))
        assertEquals("hello", EditorContext.wordAtEnd("hello"))
        assertEquals("com", EditorContext.wordAtEnd("test@example.com"))
        assertTrue(WordBoundaryPolicy.isProtectedSuggestionToken("test@example.com"))
        assertTrue(WordBoundaryPolicy.isProtectedSuggestionToken("https://example.com"))
        assertTrue(WordBoundaryPolicy.blocksSuggestionReplacement("visit https://example.com", "com"))
        assertEquals("123", EditorContext.wordAtEnd("room 123"))
        val contraction = DirectTypingState()
        "can't".forEach { assertTrue(contraction.append(it.toString(), DirectTypingLanguage.ENGLISH)) }
        assertEquals("can't", contraction.currentWord)
    }

    @Test
    fun spacesAndDoubleSpacePeriodStayConfigurableAndConservative() {
        assertTrue(SpacePolicy.shouldApplyDoubleSpace("hello ", 200, enabled = true))
        assertFalse(SpacePolicy.shouldApplyDoubleSpace("hello ", 200, enabled = false))
        assertEquals(". ", SpacePolicy.replacementForDoubleSpace(KeyboardLanguage.ENGLISH))
        assertEquals("। ", SpacePolicy.replacementForDoubleSpace(KeyboardLanguage.NEPALI))
        assertTrue(SpacePolicy.isRepeatedSpace("hello "))
        assertTrue(SpacePolicy.blocksSmartEdits("https://example.com "))
        assertFalse(DoubleSpacePolicy.canTrigger("https://example.com "))
        assertFalse(DoubleSpacePolicy.canTrigger("3.14 "))
        assertTrue(SpacePolicy.allowsSpaceAfterEmoji("hello😊"))
        assertTrue(SpacePolicy.allowsSpaceAfterNumber("room 12"))
        val afterDanda = SpacePolicy.planPunctuation("म घर जान्छु ", "।")
        assertEquals(1, afterDanda.deleteBefore)
        assertTrue(afterDanda.insertTrailingSpace)
    }

    @Test
    fun punctuationQuotesAndBracketsStayNatural() {
        val comma = SpacePolicy.planPunctuation("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        assertTrue(comma.insertTrailingSpace)
        val open = SpacePolicy.planPunctuation("hello", "(")
        assertTrue(open.insertLeadingSpace)
        val close = SpacePolicy.planPunctuation("hello(yes ", ")")
        assertEquals(1, close.deleteBefore)
        val quoteOpen = SpacePolicy.planPunctuation("say", "\"")
        assertTrue(quoteOpen.insertLeadingSpace)
        val quoteClose = SpacePolicy.planPunctuation("say \"hello ", "\"")
        assertEquals(1, quoteClose.deleteBefore)
        assertTrue(quoteClose.insertTrailingSpace)
        val bracket = SpacePolicy.planPunctuation("see", "[")
        assertTrue(bracket.insertLeadingSpace)
        val bang = SpacePolicy.planPunctuation("hello ", "!")
        assertTrue(bang.insertTrailingSpace)
        assertFalse(SpacePolicy.planPunctuation("https://example.com", ".").insertTrailingSpace)
        assertFalse(SpacePolicy.planPunctuation("3", ".").insertTrailingSpace)
    }

    @Test
    fun shiftIsOneShotThenCapsAndIgnoresSpacePunctuationAndBackspace() {
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1_000L, 0L, KeyboardLanguage.ENGLISH))
        assertEquals(
            ShiftLockState.CAPS_LOCK,
            ShiftPolicy.tap(ShiftLockState.ONE_SHOT, 1_200L, 1_000L, KeyboardLanguage.ENGLISH)
        )
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.afterNonLetter(ShiftLockState.ONE_SHOT))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertEquals(ShiftLockState.CAPS_LOCK, ShiftPolicy.afterLetter(ShiftLockState.CAPS_LOCK))
        assertFalse(ShiftPolicy.consumesOnSpace())
        assertFalse(ShiftPolicy.consumesOnBackspace())
        assertFalse(ShiftPolicy.consumesOnPunctuation())
        assertFalse(ShiftPolicy.consumesOneShot(","))
        assertFalse(ShiftPolicy.consumesOneShot(" "))
        assertTrue(ShiftPolicy.consumesOneShot("A"))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.NEPALI))
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.ROMAN))
        assertTrue(ShiftPolicy.refreshLabelsOnly(ShiftLockState.OFF, ShiftLockState.ONE_SHOT))
    }

    @Test
    fun enterFollowsImeActionsAndFallsBackToNewline() {
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_SEARCH))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_GO))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_SEND))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_NEXT))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_DONE))
        assertFalse(EnterActionPolicy.shouldInsertNewline(EnterActionPolicy.ACTION_SEARCH))
        assertTrue(EnterActionPolicy.shouldInsertNewline(EnterActionPolicy.ACTION_NONE))
        assertTrue(EnterActionPolicy.shouldInsertNewline(EnterActionPolicy.resolve(null)))
        assertEquals("Search", EnterActionPolicy.label(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Enter", EnterActionPolicy.spokenLabel(EnterActionPolicy.ACTION_UNSPECIFIED))
        assertFalse(
            EnterActionPolicy.shouldPerformAction(
                EnterActionPolicy.ACTION_SEARCH or EnterActionPolicy.IME_FLAG_NO_ENTER_ACTION
            )
        )
    }

    @Test
    fun suggestionReplacementIsCursorSafeAndDoesNotAppend() {
        assertEquals("I am going", replace("I am go", "go", "going"))
        assertEquals("I am going", replace("I am go", "go", "going", "ing"))
        assertEquals("say hello!", replace("say he", "he", "hello", "!"))
        assertNull(SuggestionSelectionPlan.create("he", "hello", "say he!"))
        assertNull(SuggestionSelectionPlan.create("com", "company", "test@example.com"))
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("the", "he"))
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
        assertTrue(CoreTypingPolicy.shouldAcceptSuggestion("going", 0L, "good", 10L))
        assertFalse(CoreTypingPolicy.shouldAcceptSuggestion("going", 100L, "going", 120L))
        assertFalse(CorrectionPolicy.shouldAutoReplace("go", "going"))
    }

    @Test
    fun languageSwitchAndRomanConversionDoNotRewriteCommittedText() {
        assertFalse(LanguageSwitchPolicy.rewritesCommittedText())
        assertTrue(LanguageSwitchPolicy.shouldCommitComposing(KeyboardLanguage.ROMAN))
        assertTrue(LanguageSwitchPolicy.preservesCursor())
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("म Kathmandu जान्छु", converter.convertText("ma Kathmandu janchu"))
        val composer = RomanInputComposer(converter)
        composer.type("m")
        composer.type("a")
        assertEquals("म", (composer.finishWord() as RomanEdit.Commit).text)
        assertEquals("म", converter.bestConversion("ma"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
    }

    @Test
    fun emojiNumberAndSymbolInsertionFinishComposingWithoutRewriting() {
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnPanelOpen())
        assertTrue(PanelTransitionPolicy.shouldFinishComposing(openingPanel = true))
        assertTrue(PanelTransitionPolicy.shouldResetShift())
        assertFalse(PanelTransitionPolicy.duplicatesSpacesOnInsert("hello", "😊"))
        assertTrue(PanelTransitionPolicy.duplicatesSpacesOnInsert("hello ", " "))
        val composer = RomanInputComposer(converter)
        composer.type("ma")
        val edit = PanelInsertionPolicy.romanEditBeforeInsert(composer)
        assertTrue(edit is RomanEdit.Commit)
        assertEquals("", composer.currentWord)
        assertTrue(InputConnectionCommitter.commit(null, "").not())
    }

    @Test
    fun targetAppChangesProtectPasswordsAndClearStaleState() {
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        val pin = EditorFieldPolicy.TYPE_CLASS_NUMBER or EditorFieldPolicy.TYPE_NUMBER_VARIATION_PASSWORD
        val email = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val noSuggest = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue(EditorFieldPolicy.isPasswordField(password))
        assertTrue(EditorFieldPolicy.isSensitiveField(pin))
        assertFalse(EditorFieldPolicy.shouldLearn(password))
        assertFalse(EditorFieldPolicy.shouldSuggest(password))
        assertFalse(EditorFieldPolicy.shouldSuggest(noSuggest))
        assertTrue(EditorFieldPolicy.shouldLearn(email))
        assertTrue(EditorFieldPolicy.isEmailField(email))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("hunter2password"))
        assertFalse(PersonalDictionary.shouldAccept("password: hunter2"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("4111111111111111"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://secret.example/token"))
        assertTrue(InputConnectionPolicy.shouldFinishComposingOnFieldChange())
    }

    @Test
    fun cursorMovementInvalidatesStaleStateWithoutRewritingText() {
        assertTrue(CursorMovementPolicy.shouldInvalidateComposing(true))
        assertFalse(CursorMovementPolicy.shouldInvalidateComposing(false))
        assertTrue(CursorMovementPolicy.shouldInvalidateSuggestions(true, internal = false))
        assertFalse(CursorMovementPolicy.shouldInvalidateSuggestions(true, internal = true))
        assertTrue(CursorMovementPolicy.preservesCommittedText())
        assertTrue(InputConnectionPolicy.shouldInvalidateOnCursorMove(true, true))
        assertFalse(InputConnectionPolicy.restoresComposingAfterPanelClose())
        assertTrue(RomanSelectionState.movedAwayFromComposition("ma", 2, 2, 4))
        assertFalse(RomanSelectionState.movedAwayFromComposition("ma", 4, 4, 4))
    }

    @Test
    fun accessibilityContractsAndDeterministicPoliciesStayIntact() {
        assertEquals("Space, English", AccessibilityLabels.space(KeyboardLanguage.ENGLISH))
        assertEquals("Space, Nepali", AccessibilityLabels.space(KeyboardLanguage.NEPALI))
        assertEquals("Space", AccessibilityLabels.key(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", primary = true))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Shift", AccessibilityLabels.shift(false))
        assertEquals("Search", AccessibilityLabels.enter(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("English", AccessibilityLabels.language(KeyboardLanguage.ENGLISH))
        assertEquals("Emoji 😊", AccessibilityLabels.emoji("😊"))
        assertEquals(firstReplace(), firstReplace())
        assertEquals(32L, CoreTypingPolicy.KEY_BOUNCE_MS)
        assertEquals(KeyTouchPolicy.KEY_BOUNCE_MS, CoreTypingPolicy.KEY_BOUNCE_MS)
        assertTrue(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.LOCAL_DATA_KEYS)
        assertFalse(KeyboardPreferences.KEY_TOUCH_ADAPTATION in KeyboardPreferences.ALL_SETTING_KEYS)
    }

    private fun firstReplace(): String = replace("I am go", "go", "going")

    private fun replace(
        field: String,
        typed: String,
        suggestion: String,
        after: String = ""
    ): String {
        val plan = SuggestionSelectionPlan.create(typed, suggestion, field, after)!!
        val keptAfter = if (after.length >= plan.deleteAfterCodeUnits) {
            after.drop(plan.deleteAfterCodeUnits)
        } else {
            ""
        }
        return field.substring(0, field.length - plan.deleteCodeUnits) + plan.replacement + keptAfter
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
