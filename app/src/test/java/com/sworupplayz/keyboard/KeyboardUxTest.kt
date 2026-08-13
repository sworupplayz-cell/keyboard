package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardUxTest {
    @Test
    fun letterAndDevanagariKeysPreviewTheInsertedCharacter() {
        assertTrue(KeyInteractionPolicy.showsPreview(KeySpec("a")))
        assertTrue(KeyInteractionPolicy.showsPreview(KeySpec("क")))
        assertTrue(KeyInteractionPolicy.showsPreview(KeySpec("ा")))
        assertEquals("?", KeyInteractionPolicy.previewLabel(KeySpec("?", output = "?")))
        assertEquals("।", KeyInteractionPolicy.previewLabel(KeySpec("।")))
        assertFalse(KeyInteractionPolicy.showsPreview(KeySpec("English", KeyAction.SPACE, " ")))
        assertFalse(KeyInteractionPolicy.showsPreview(KeySpec("⇧", KeyAction.SHIFT)))
        assertFalse(KeyInteractionPolicy.showsPreview(KeySpec("😊")))
        assertTrue(KeyInteractionPolicy.looksLikeEmoji("😊"))
        assertFalse(KeyInteractionPolicy.looksLikeEmoji("क"))
    }

    @Test
    fun pressedFillStaysDistinctAndFadeIsFast() {
        val white = KeyboardTheme.WHITE
        val dark = 0xFF3C4043.toInt()
        assertNotEquals(white, KeyboardTheme.pressedColor(white))
        assertNotEquals(dark, KeyboardTheme.pressedColor(dark))
        assertEquals(KeyboardTheme.pressedColor(white), KeyboardThemeTokens.pressed(white))
        assertTrue(KeyboardTheme.PRESS_FADE_MS <= 80)
        assertTrue(KeyboardTheme.RELEASE_FADE_MS <= 90)
        assertEquals(KeyInteractionPolicy.PRESS_FADE_MS, KeyboardTheme.PRESS_FADE_MS)
    }

    @Test
    fun longPressOnlyOffersExistingAlternates() {
        assertTrue(KeyInteractionPolicy.allowsLongPressAlternates(KeySpec("e")))
        assertTrue(KeyInteractionPolicy.allowsLongPressAlternates(KeySpec("क")))
        assertTrue(KeyInteractionPolicy.allowsLongPressAlternates(KeySpec(".")))
        assertFalse(KeyInteractionPolicy.allowsLongPressAlternates(KeySpec("q")))
        assertFalse(KeyInteractionPolicy.allowsLongPressAlternates(KeySpec("⇧", KeyAction.SHIFT)))
        assertTrue("é" in KeyVisuals.alternates("e"))
        assertTrue("ख" in KeyVisuals.alternates("क"))
    }

    @Test
    fun backClosesOverlayThenMoreThenPanel() {
        assertEquals(
            PanelNavigationResult.CLOSED_OVERLAY,
            PanelNavigation.consume(overlayOpen = true, toolbarExpanded = true, panelOpen = true)
        )
        assertEquals(
            PanelNavigationResult.COLLAPSED_TOOLS,
            PanelNavigation.consume(overlayOpen = false, toolbarExpanded = true, panelOpen = true)
        )
        assertEquals(
            PanelNavigationResult.CLOSED_PANEL,
            PanelNavigation.consume(overlayOpen = false, toolbarExpanded = false, panelOpen = true)
        )
        assertEquals(
            PanelNavigationResult.NONE,
            PanelNavigation.consume(overlayOpen = false, toolbarExpanded = false, panelOpen = false)
        )
        assertFalse(PanelNavigation.isTemporaryPanel(letters = true, vowels = false))
        assertFalse(PanelNavigation.isTemporaryPanel(letters = false, vowels = true))
        assertTrue(PanelNavigation.isTemporaryPanel(letters = false, vowels = false))
    }

    @Test
    fun toolbarBackContractIsUnchangedWhenMoreIsExpanded() {
        val toolbar = ToolbarController()
        toolbar.expand()
        assertEquals(ToolbarBackResult.COLLAPSED_TOOLS, toolbar.consumeBack(panelOpen = true))
        assertFalse(toolbar.isExpanded)
        assertEquals(ToolbarBackResult.CLOSED_PANEL, toolbar.consumeBack(panelOpen = true))
        assertEquals(ToolbarBackResult.NONE, toolbar.consumeBack(panelOpen = false))
    }

    @Test
    fun languagePickerMarksTheCurrentModeAndDismissesOnSelect() {
        val picker = LanguagePickerState()
        assertFalse(picker.isOpen)
        picker.open(KeyboardLanguage.NEPALI)
        assertTrue(picker.isOpen)
        val items = picker.items()
        assertEquals(listOf("English", "नेपाली", "Roman Nepali"), items.map { it.option.label })
        assertTrue(items.first { it.option.language == KeyboardLanguage.NEPALI }.selected)
        assertFalse(items.first { it.option.language == KeyboardLanguage.ENGLISH }.selected)
        assertEquals(KeyboardLanguage.ROMAN, picker.select("Roman Nepali"))
        assertFalse(picker.isOpen)
        assertNull(picker.select("not-a-language"))
        assertEquals("ने", LanguageSwitcher.toolbarLabel(KeyboardLanguage.NEPALI))
    }

    @Test
    fun doubleActivationGuardIgnoresTheSameControl() {
        val guard = ActivationGuard(intervalMs = 200)
        assertTrue(guard.allow("emoji", 1_000L))
        assertFalse(guard.allow("emoji", 1_100L))
        assertTrue(guard.allow("clipboard", 1_120L))
        assertTrue(guard.allow("emoji", 1_250L))
        guard.reset()
        assertTrue(guard.allow("emoji", 1_260L))
    }

    @Test
    fun oneHandedMetricsAlignToolbarSuggestionsAndPanels() {
        assertEquals(0, OneHandedLayoutPolicy.insets(320, OneHandedAlignment.LEFT).endDp)
        val left = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.LEFT)
        val right = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.RIGHT)
        assertEquals(0, left.startDp)
        assertTrue(left.endDp > 0)
        assertEquals(left.endDp, right.startDp)
        assertEquals(left.contentWidthDp, right.contentWidthDp)
        assertEquals(left.contentWidthDp, KeyboardUiMetrics.contentWidthDp(412, OneHandedAlignment.LEFT))
        assertTrue(left.contentWidthDp < 412)
        assertEquals(
            KeyboardPresentationMode.NORMAL,
            OneHandedLayoutPolicy.presentation(OneHandedAlignment.OFF, KeyboardPresentationMode.FLOATING)
        )
    }

    @Test
    fun heightSettingScalesEverySurfaceWithoutTinyKeys() {
        val small = KeyboardHeight.SMALL
        val large = KeyboardHeight.LARGE
        assertTrue(KeyboardUiMetrics.keyHeightDp(360, 800, false, small) >= 40)
        assertTrue(
            KeyboardUiMetrics.keyHeightDp(360, 800, false, small) <
                KeyboardUiMetrics.keyHeightDp(360, 800, false, large)
        )
        assertTrue(
            KeyboardUiMetrics.suggestionHeightDp(false, small) <
                KeyboardUiMetrics.suggestionHeightDp(false, large)
        )
        assertTrue(
            KeyboardUiMetrics.clipboardPanelHeightDp(800, false, small) <
                KeyboardUiMetrics.clipboardPanelHeightDp(800, false, large)
        )
        assertTrue(
            KeyboardUiMetrics.emojiKeyHeightDp(small) < KeyboardUiMetrics.emojiKeyHeightDp(large)
        )
        assertEquals(40, KeyboardUiMetrics.suggestionHeightDp())
        assertEquals(44, KeyboardUiMetrics.navigationHeightDp())
    }

    @Test
    fun responsiveWidthsStayUsableFromCompactToLandscape() {
        assertTrue(KeyboardUiMetrics.equalKeyWidthDp(320, 10) >= 29f)
        assertTrue(KeyboardUiMetrics.previewWidthDp(320, false) <= 50)
        assertTrue(KeyboardUiMetrics.previewHeightDp(640, true) <= 54)
        assertEquals(7, KeyboardUiMetrics.emojiColumns(320))
        assertEquals(8, KeyboardUiMetrics.emojiColumns(412))
        assertEquals(4, KeyboardUiMetrics.maxToolbarItems(320))
        assertTrue(KeyboardUiMetrics.emojiCategoryWidthDp(320) >= AccessibilityLabels.MIN_TOUCH_DP)
        assertTrue(
            KeyboardUiMetrics.estimatedStandardHeightDp(
                320, 568, landscape = false, rowCount = 4, hasSuggestion = true, hasToolbar = true
            ) <= 320
        )
    }

    @Test
    fun suggestionBarStaysStableAtThreeSlots() {
        assertEquals(listOf("hello", "help", "he'll"), SuggestionBarState.display(listOf("hello", "help", "he'll", "held"), "he"))
        assertTrue(SuggestionBarState.unchanged(listOf("hello"), listOf("hello")))
        assertFalse(SuggestionBarState.unchanged(listOf("hello"), listOf("help")))
        val cells = SuggestionBarState.cells(listOf("hello"), 3)
        assertEquals(3, cells.size)
        assertEquals("hello", cells[0])
        assertNull(cells[1])
        assertNull(cells[2])
        assertTrue(SuggestionBarState.display(emptyList(), "").isEmpty())
    }

    @Test
    fun themeTokensStayOnTheSharedPalette() {
        val light = KeyboardPalette(
            background = 0xFFE8EAED.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFD3D7DE.toInt(),
            text = 0xFF202124.toInt(),
            secondaryText = 0xFF5F6368.toInt(),
            divider = 0xFFDADCE0.toInt(),
            accent = 0xFF1A73E8.toInt(),
            enterKey = 0xFF1A73E8.toInt(),
            enterText = KeyboardTheme.WHITE,
            popupBackground = KeyboardTheme.WHITE,
            popupText = 0xFF202124.toInt(),
            shadow = 0x33000000
        )
        assertEquals(light.background, KeyboardThemeTokens.suggestionBackground(light))
        assertEquals(light.popupBackground, KeyboardThemeTokens.previewFill(light))
        assertEquals(light.popupText, KeyboardThemeTokens.previewText(light))
        assertEquals(KeyboardTheme.WHITE, KeyboardThemeTokens.selectedLabel(light))
        assertEquals(light.accent, KeyboardThemeTokens.toolbarSelected(light))
        assertNotEquals(light.background, KeyboardThemeTokens.suggestionPressed(light))
    }

    @Test
    fun accessibilityLabelsCoverKeysSuggestionsAndLanguages() {
        assertEquals("Backspace", AccessibilityLabels.key(KeySpec("⌫", KeyAction.BACKSPACE)))
        assertEquals("Enter", AccessibilityLabels.key(KeySpec("↵", KeyAction.ENTER)))
        assertEquals("Shift", AccessibilityLabels.key(KeySpec("⇧", KeyAction.SHIFT)))
        assertEquals("Space", AccessibilityLabels.key(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals("Suggestion hello", AccessibilityLabels.suggestion("hello"))
        val nepali = LanguageSwitcher.options().first { it.language == KeyboardLanguage.NEPALI }
        assertEquals("Nepali", AccessibilityLabels.languageOption(nepali))
        assertEquals("Nepali, selected", AccessibilityLabels.languageOption(nepali, selected = true))
        assertEquals(40, AccessibilityLabels.MIN_TOUCH_DP)
        assertEquals("Emoji", AccessibilityLabels.panel("Emoji"))
    }

    @Test
    fun soundAndVibrationStayOffWhenDisabledAndSkipChrome() {
        assertFalse(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = false, FeedbackKind.KEY))
        assertFalse(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = false, FeedbackKind.KEY))
        assertTrue(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = true, FeedbackKind.KEY))
        assertFalse(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = true, FeedbackKind.CHROME))
        assertFalse(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = true, FeedbackKind.NAVIGATION))
        assertFalse(TouchFeedbackPolicy.shouldPlaySound(soundEnabled = true, FeedbackKind.LONG_PRESS))
        assertTrue(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.KEY))
        assertTrue(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.LONG_PRESS))
        assertFalse(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.CHROME))
        assertFalse(TouchFeedbackPolicy.shouldVibrate(vibrationEnabled = true, FeedbackKind.NAVIGATION))
        assertEquals(KeyClickSound.DELETE, TouchFeedbackPolicy.soundFor(KeyAction.BACKSPACE))
        assertEquals(KeyClickSound.NONE, TouchFeedbackPolicy.soundFor(KeyAction.SETTINGS))
        assertEquals(18L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.LONG_PRESS))
        assertEquals(0L, TouchFeedbackPolicy.vibrationDurationMs(FeedbackKind.CHROME))
    }

    @Test
    fun graphemeBackspaceStillRemovesClustersAndEmoji() {
        assertEquals("hello", GraphemeBackspace.apply("hello!"))
        assertEquals("caf", GraphemeBackspace.apply("café"))
        assertTrue(GraphemeBackspace.apply("नमस्ते").length < "नमस्ते".length)
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("flag ", GraphemeBackspace.apply("flag 🇳🇵"))
        assertEquals("skin ", GraphemeBackspace.apply("skin 👍🏻"))
        assertEquals("family ", GraphemeBackspace.apply("family 👨‍👩‍👧"))
    }

    @Test
    fun existingTypingContractsStayIntact() {
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertEquals(1, PunctuationSpacing.plan("hello ", ",").deleteBefore)
        assertEquals(". ", DoubleSpacePolicy.replacement(KeyboardLanguage.ENGLISH))
        assertEquals("। ", DoubleSpacePolicy.replacement(KeyboardLanguage.NEPALI))
        assertEquals(KeyboardLanguage.NEPALI, LanguageSwitcher.cycle(KeyboardLanguage.ENGLISH))
        assertEquals("EN", LanguageSwitcher.toolbarLabel(KeyboardLanguage.ENGLISH))
        assertEquals(listOf("😊", "📋", "EN", "⚙", "⋯"), ToolbarController().items(KeyboardLanguage.ENGLISH).map { it.label })
    }
}
