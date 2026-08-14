package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase21CoreQualityTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val english by lazy { LocalWordSuggester.fromWords(englishWords) }
    private val nepali by lazy {
        LocalWordSuggester.fromWords(
            VocabularyLoader.mergeDistinct(
                VocabularyLoader.nepali(resource("nepali_vocabulary.txt").reader()),
                VocabularyLoader.nepaliFromRomanDictionary(resource("roman_nepali_dictionary.tsv").reader())
            )
        )
    }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }
    private val engine by lazy { SuggestionEngine(english, nepali, converter) }

    @Test
    fun graphemeBackspaceRemovesOneClusterAtATime() {
        assertEquals("hello", GraphemeBackspace.apply("hello!"))
        assertEquals("caf", GraphemeBackspace.apply("café"))
        assertTrue(GraphemeBackspace.apply("नमस्ते").length < "नमस्ते".length)
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("flag ", GraphemeBackspace.apply("flag 🇳🇵"))
        assertEquals("skin ", GraphemeBackspace.apply("skin 👍🏻"))
        assertEquals("family ", GraphemeBackspace.apply("family 👨‍👩‍👧"))
        val state = DirectTypingState()
        assertTrue(state.append("क", DirectTypingLanguage.NEPALI))
        assertTrue(state.append("ी", DirectTypingLanguage.NEPALI))
        assertEquals("की", state.currentWord)
        assertTrue(state.backspace())
        assertEquals("क", state.currentWord)
    }

    @Test
    fun backspaceRepeatStartsAfterADelayThenAcceleratesOneGraphemeAtATime() {
        assertFalse(BackspaceRepeatPolicy.shouldStart(200))
        assertTrue(BackspaceRepeatPolicy.shouldStart(400))
        assertEquals(1, BackspaceRepeatPolicy.deletesPerTick(0))
        assertEquals(1, BackspaceRepeatPolicy.deletesPerTick(20))
        assertEquals(BackspaceRepeatPolicy.INITIAL_INTERVAL_MS, BackspaceRepeatPolicy.intervalMs(0))
        assertEquals(BackspaceRepeatPolicy.FAST_INTERVAL_MS, BackspaceRepeatPolicy.intervalMs(6))
        assertTrue(BackspaceRepeatPolicy.intervalMs(8) < BackspaceRepeatPolicy.intervalMs(1))
        assertEquals(3, BackspaceRepeatPolicy.nextRepeatCount(2))
    }

    @Test
    fun shiftIsOneShotThenCapsAndIgnoresPunctuation() {
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1_000L, 0L, KeyboardLanguage.ENGLISH))
        assertEquals(
            ShiftLockState.CAPS_LOCK,
            ShiftPolicy.tap(ShiftLockState.ONE_SHOT, 1_200L, 1_000L, KeyboardLanguage.ENGLISH)
        )
        assertEquals(ShiftLockState.OFF, ShiftPolicy.afterLetter(ShiftLockState.ONE_SHOT))
        assertEquals(ShiftLockState.CAPS_LOCK, ShiftPolicy.afterLetter(ShiftLockState.CAPS_LOCK))
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.afterNonLetter(ShiftLockState.ONE_SHOT))
        assertTrue(ShiftPolicy.consumesOneShot("A"))
        assertFalse(ShiftPolicy.consumesOneShot(","))
        assertFalse(ShiftPolicy.consumesOneShot(" "))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.NEPALI))
        assertEquals(ShiftLockState.ONE_SHOT, ShiftPolicy.tap(ShiftLockState.OFF, 1L, 0L, KeyboardLanguage.ROMAN))
        assertEquals(ShiftLockState.OFF, ShiftPolicy.tap(ShiftLockState.ONE_SHOT, 50L, 1L, KeyboardLanguage.ROMAN))
    }

    @Test
    fun enterActionFollowsTheTargetApp() {
        assertEquals("↵", EnterActionPolicy.label(EnterActionPolicy.ACTION_NONE))
        assertEquals("Search", EnterActionPolicy.label(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Go", EnterActionPolicy.label(EnterActionPolicy.ACTION_GO))
        assertEquals("Send", EnterActionPolicy.label(EnterActionPolicy.ACTION_SEND))
        assertEquals("Next", EnterActionPolicy.label(EnterActionPolicy.ACTION_NEXT))
        assertEquals("Done", EnterActionPolicy.label(EnterActionPolicy.ACTION_DONE))
        assertTrue(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_SEARCH))
        assertFalse(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_NONE))
        assertFalse(EnterActionPolicy.shouldPerformAction(EnterActionPolicy.ACTION_SEARCH or EnterActionPolicy.IME_FLAG_NO_ENTER_ACTION))
        assertEquals("Search", AccessibilityLabels.enter(EnterActionPolicy.ACTION_SEARCH))
        assertEquals("Enter", AccessibilityLabels.key(KeySpec("↵", KeyAction.ENTER)))
    }

    @Test
    fun suggestionRankingKeepsThreeSlotsAndNeverAutoReplaces() {
        val hel = engine.suggest(SuggestionQuery("hel", SuggestionLanguage.ENGLISH)).visible()
        assertTrue(hel.toString(), "hello" in hel)
        assertEquals(3, SuggestionBarState.gboardSlots(hel).size)
        assertTrue(SuggestionBarState.gboardSlots(listOf("hello", "help", "he'll"))[1].primary)
        assertEquals(listOf("hello", "help"), SuggestionBarState.display(listOf("hello", "help", "hello"), "he"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))
        assertTrue("hello" in engine.suggest(SuggestionQuery("helo", SuggestionLanguage.ENGLISH)).visible())
        assertNull(SuggestionSelectionPlan.create("he", "hello", "say he!"))
        assertEquals("say hello", replace("say he", "he", "hello"))
    }

    @Test
    fun typoSystemCoversNearbyMissingExtraAndCommonMistakes() {
        assertTrue(TypoCorrector.isNearbySubstitution("goof", "good"))
        assertTrue(TypoCorrector.isAdjacentTransposition("teh", "the"))
        assertTrue(TypoCorrector.isMissingLetter("helo", "hello"))
        assertTrue(TypoCorrector.isExtraCharacter("helllo", "hello"))
        assertTrue(TypoCorrector.isConservativeTypo("definately", "definitely") ||
            "definitely" in TypoCorrector.commonCorrections("definately"))
        assertTrue("the" in TypoCorrector.commonCorrections("teh"))
        assertTrue("receive" in TypoCorrector.commonCorrections("recieve"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("definately", "definitely"))
        assertTrue(CorrectionPolicy.shouldOfferTypo("helo", "hello"))
        assertFalse(CorrectionPolicy.shouldOfferTypo("qz", "hello"))
    }

    @Test
    fun romanNepaliKeepsVariantsAndMixedEnglish() {
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("आज", converter.bestConversion("aja"))
        assertEquals("आज", converter.bestConversion("aaja"))
        assertTrue("ee" in RomanSpellingNormalizer.lookupForms("ee") || "i" in RomanSpellingNormalizer.lookupForms("ee"))
        assertTrue(RomanSpellingNormalizer.lookupForms("phul").any { "ful" in it || "phul" in it })
        assertTrue(RomanSpellingNormalizer.lookupForms("shanti").any { it.contains("santi") || it == "shanti" })
        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("today म school जान्छु", converter.convertText("today ma school jaanchu"))
        assertEquals("मेरो phone good छ", converter.convertText("mero phone good cha"))
        assertEquals("school", converter.bestConversion("school"))
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        assertEquals(KeyboardLanguage.ENGLISH, LanguageIntelligencePolicy.keepManualMode(KeyboardLanguage.ENGLISH, "ghar"))
        assertTrue(LanguageIntelligencePolicy.staysInSelectedMode(KeyboardLanguage.ROMAN, "hello"))
    }

    @Test
    fun punctuationStaysSafeAroundUrlsEmailsAndDecimals() {
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        assertTrue(comma.insertTrailingSpace)
        assertTrue(PunctuationSpacing.plan("hello ", "?").insertTrailingSpace)
        assertTrue(PunctuationSpacing.plan("hello ", "!").insertTrailingSpace)
        assertTrue(PunctuationSpacing.plan("hello ", ":").insertTrailingSpace)
        assertTrue(PunctuationSpacing.plan("hello ", ";").insertTrailingSpace)
        assertTrue(PunctuationSpacing.plan("hello ", "%").insertTrailingSpace)
        val open = PunctuationSpacing.plan("hello", "(")
        assertTrue(open.insertLeadingSpace)
        val close = PunctuationSpacing.plan("hello(yes ", ")")
        assertEquals(1, close.deleteBefore)
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("user@example.com", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("3", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("#nepali", "!").insertTrailingSpace)
    }

    @Test
    fun touchPolicyPrefersOwnedGesturesAndUsableEdgeKeys() {
        assertTrue(KeyTouchPolicy.shouldAcceptTap("a", 0L, "a", 40L))
        assertFalse(KeyTouchPolicy.shouldAcceptTap("a", 100L, "a", 120L))
        assertTrue(KeyTouchPolicy.shouldAcceptTap("a", 100L, "b", 110L))
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertEquals(0, KeyTouchPolicy.horizontalInsets(4, KeyEdge.START).first)
        assertEquals(0, KeyTouchPolicy.horizontalInsets(4, KeyEdge.END).second)
        assertTrue(KeyTouchPolicy.commitsOnDown(KeyAction.BACKSPACE))
        assertFalse(KeyTouchPolicy.commitsOnDown(KeyAction.TEXT))
        assertEquals(32L, KeyTouchPolicy.bounceGuardMs(FeedbackKind.KEY))
        assertEquals(KeyInteractionPolicy.DOUBLE_TAP_GUARD_MS, KeyTouchPolicy.bounceGuardMs(FeedbackKind.CHROME))
    }

    @Test
    fun narrowScreensAndFontScaleDoNotBreakExistingHeights() {
        assertEquals(46, KeyboardUiMetrics.keyHeightDp(320, 568, landscape = false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, landscape = false))
        assertEquals(42, KeyboardUiMetrics.keyHeightDp(640, 360, landscape = true))
        assertEquals(0, OneHandedLayoutPolicy.insets(320, OneHandedAlignment.LEFT).endDp)
        assertEquals(21f, KeyboardUiMetrics.cappedLetterTextSp(21f, 1f), 0.01f)
        assertTrue(KeyboardUiMetrics.cappedLetterTextSp(21f, 1.6f) < 21f)
        assertTrue(KeyboardUiMetrics.equalKeyWidthDp(320, 10) >= 29f)
    }

    @Test
    fun accessibilityLabelsCoverShiftLanguageSuggestionsAndClipboard() {
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        assertEquals("Primary suggestion hello", AccessibilityLabels.suggestion("hello", primary = true))
        assertEquals("Caps lock on", AccessibilityLabels.shift(true))
        assertEquals("Shift", AccessibilityLabels.shift(false))
        assertEquals("English", AccessibilityLabels.language(KeyboardLanguage.ENGLISH))
        assertEquals("Space, Nepali", AccessibilityLabels.space(KeyboardLanguage.NEPALI))
        assertEquals("Alternates for e", AccessibilityLabels.longPress("e"))
        assertEquals("Backspace", AccessibilityLabels.key(KeySpec("⌫", KeyAction.BACKSPACE)))
        assertEquals("Space", AccessibilityLabels.key(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals("Clipboard", AccessibilityLabels.key(KeySpec("📋", KeyAction.CLIPBOARD)))
    }

    private fun replace(field: String, typed: String, suggestion: String): String {
        val plan = SuggestionSelectionPlan.create(typed, suggestion, field)!!
        return field.substring(0, field.length - plan.deleteCodeUnits) + plan.replacement
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
