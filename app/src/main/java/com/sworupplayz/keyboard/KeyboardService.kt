package com.sworupplayz.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.ClipboardManager
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class KeyboardService : InputMethodService() {
    private enum class LayoutMode {
        LETTERS,
        VOWELS,
        NUMBERS,
        SYMBOLS,
        EMOJI,
        CLIPBOARD,
        HANDWRITING
    }

    private lateinit var overlayHost: FrameLayout
    private lateinit var keyboardRoot: LinearLayout
    private var previewView: KeyPreviewView? = null
    private var alternateChooser: AlternateChooserView? = null
    private var suggestionRow: LinearLayout? = null
    private var handwritingResultRow: LinearLayout? = null
    private var handwritingCanvas: HandwritingCanvasView? = null
    private val handwritingState = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
    private val handwritingJobs = HandwritingJobController()
    private val handwritingSession = HandwritingSession()
    private val modeHistory = PreviousLayoutStack<ModeSnapshot>()
    private var emojiCategory = EmojiCategory.RECENT
    private var emojiQuery = ""
    private var digitScript = DigitScript.LATIN
    private var symbolGroup = SymbolGroup.COMMON
    private var emojiUsage = EmojiUsageStore()
    private var recentSymbols = RecentSymbolStore()
    private val emojiDataset = lazy {
        resources.openRawResource(R.raw.emoji_catalog).bufferedReader().use { EmojiCatalog.load(it) }
        true
    }
    private val recentEmojis: RecentEmojiList by lazy {
        ensureEmojiDataset()
        RecentEmojiList.fromSerialized(
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .getString(KeyboardPreferences.KEY_RECENT_EMOJIS, null)
        )
    }
    private var learnedRomanWords = LearnedRomanWords()
    private var learnedEnglishWords = LearnedWordStore()
    private var learnedNepaliWords = LearnedWordStore()
    private var recentEnglishWords = RecentWordStore()
    private var recentNepaliWords = RecentWordStore()
    private var recentRomanWords = RecentWordStore()
    private var englishContext = ContextModel(seed = ContextModel.ENGLISH_SEED)
    private var nepaliContext = ContextModel(seed = ContextModel.NEPALI_SEED)
    private var romanContext = ContextModel(seed = ContextModel.ROMAN_SEED)
    private var lastCommittedWord: String = ""
    private var lastTwoCommittedWords: String? = null
    private val englishVocabulary: List<String> by lazy {
        resources.openRawResource(R.raw.english_vocabulary).bufferedReader().use {
            VocabularyLoader.english(it)
        }
    }
    private val englishSuggester: LocalWordSuggester by lazy {
        LocalWordSuggester.fromWords(englishVocabulary)
    }
    private val nepaliSuggester: LocalWordSuggester by lazy {
        val dedicated = resources.openRawResource(R.raw.nepali_vocabulary).bufferedReader().use {
            VocabularyLoader.nepali(it)
        }
        val fromRoman = resources.openRawResource(R.raw.roman_nepali_dictionary).bufferedReader().use {
            VocabularyLoader.nepaliFromRomanDictionary(it)
        }
        LocalWordSuggester.fromWords(VocabularyLoader.mergeDistinct(dedicated, fromRoman))
    }
    private val directTypingState = DirectTypingState()
    private val romanConverter: RomanNepaliConverter by lazy {
        resources.openRawResource(R.raw.roman_nepali_dictionary).bufferedReader().use {
            RomanNepaliConverter.from(it, englishVocabulary.toSet())
        }
    }
    private val romanComposerDelegate = lazy {
        RomanInputComposer(romanConverter) { word ->
            if (useLearning) learnedRomanWords.lookup(word) else null
        }
    }
    private val romanComposer: RomanInputComposer get() = romanComposerDelegate.value
    private val suggestionEngineDelegate = lazy {
        val preferences = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
        SuggestionEngine(
            english = englishSuggester,
            nepali = nepaliSuggester,
            roman = romanConverter,
            englishPhrases = PhrasePredictor.fromSerialized(
                preferences.getString(KeyboardPreferences.KEY_PHRASE_ENGLISH, null),
                PhrasePredictor.ENGLISH_PHRASES
            ),
            nepaliPhrases = PhrasePredictor.fromSerialized(
                preferences.getString(KeyboardPreferences.KEY_PHRASE_NEPALI, null),
                PhrasePredictor.NEPALI_PHRASES
            ),
            romanPhrases = PhrasePredictor.fromSerialized(
                preferences.getString(KeyboardPreferences.KEY_PHRASE_ROMAN, null),
                PhrasePredictor.ROMAN_PHRASES
            )
        )
    }
    private val suggestionEngine: SuggestionEngine get() = suggestionEngineDelegate.value
    private var repeatFinishes = RepeatFinishStore()
    private var language = KeyboardLanguage.ENGLISH
    private var layoutMode = LayoutMode.LETTERS
    private var shiftState = ShiftLockState.OFF
    private var lastShiftTapAt = 0L
    private var useSound = false
    private var useVibration = false
    private var useDarkAppearance = false
    private var useSuggestions = true
    private var useLearning = true
    private var useSmartPunctuation = true
    private var useDoubleSpacePeriod = true
    private var useAutoCapitalization = true
    private var useEmojiRecents = true
    private var useToolbar = true
    private var useClipboardHistory = true
    private var useLanguageButton = true
    private var useTypoSuggestions = true
    private var oneHanded = OneHandedAlignment.OFF
    private var presentationMode = KeyboardPresentationMode.NORMAL
    private val toolbar = ToolbarController()
    private val languagePicker = LanguagePickerState()
    private val activationGuard = ActivationGuard()
    private val keyBounce = ActivationGuard(KeyTouchPolicy.KEY_BOUNCE_MS)
    private val suggestionBounce = ActivationGuard(CoreTypingPolicy.SUGGESTION_BOUNCE_MS)
    private val typingGeneration = TypingGeneration()
    private val touchState = TouchRecognitionState()
    private var adaptiveHitboxes = AdaptiveHitboxPolicy()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeat: Runnable? = null
    private var fieldAllowsLearning = true
    private var fieldAllowsSuggestions = true
    private var inputViewActive = false
    private var currentInputType = 0
    private var hasSelection = false
    private val boundTypingKeys = ArrayList<BoundTypingKey>()
    private var lastSuggestionWords: List<String> = emptyList()
    private val suggestionQueryCache = SuggestionQueryCache()
    private var overlayDismissView: View? = null
    private var clipboardHistory = ClipboardRepository()
    private var lastSpaceUptime = 0L
    private var showNumberRow = false
    private var keyboardHeight = KeyboardHeight.NORMAL
    private var visualTheme = KeyboardVisualTheme.FOLLOW_APPEARANCE
    private var colorPreset = ColorPreset.THEME
    private var keyDensity = KeyDensity.NORMAL
    private var keySpacing = KeySpacing.NORMAL
    private var keyCorner = KeyCornerStyle.NORMAL
    private var themeStyle = ThemeStyle()
    private var soundVolume = SoundVolume.MEDIUM
    private var hapticStrength = HapticStrength.MEDIUM
    private var cachedPalette: KeyboardPalette? = null
    private var defaultMode: DefaultKeyboardMode? = null
    private var internalSelectionChange = false
    private var keyHeightDp = 48
    private var suggestionSettingsVisible = false

    override fun onCreateInputView(): View {
        readPreferences()
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        updateInitialLanguage(inputMethodManager.currentInputMethodSubtype)
        keyHeightDp = preferredKeyHeight()
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(KeyboardUiMetrics.ROOT_HORIZONTAL_PADDING_DP),
                dp(KeyboardUiMetrics.ROOT_VERTICAL_PADDING_DP / 2),
                dp(KeyboardUiMetrics.ROOT_HORIZONTAL_PADDING_DP),
                dp(KeyboardUiMetrics.ROOT_VERTICAL_PADDING_DP / 2)
            )
        }
        overlayHost = FrameLayout(this)
        overlayHost.addView(
            keyboardRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        previewView = KeyPreviewView(this).also { overlayHost.addView(it) }
        alternateChooser = AlternateChooserView(this).also { overlay ->
            overlay.onPick = { text -> insertAlternate(text) }
            overlayHost.addView(overlay)
        }
        applyWindowAppearance()
        renderKeyboard()
        return overlayHost
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (ImeLifecyclePolicy.shouldApplyFieldPolicyOnStartInput()) {
            applyEditorField(attribute)
        }
        if (ImeLifecyclePolicy.shouldResetFieldState(restarting)) {
            typingGeneration.bump()
            stopBackspaceRepeat()
            finishComposingSpanOnly()
            directTypingState.clear()
            lastCommittedWord = ""
            lastTwoCommittedWords = null
            lastSuggestionWords = emptyList()
            suggestionQueryCache.invalidate()
            hasSelection = false
            if (ProductionIntegrationPolicy.shouldResetRepeatLearningOnNewField(restarting)) {
                repeatFinishes = RepeatFinishStore()
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputViewActive = true
        readPreferences()
        keyHeightDp = preferredKeyHeight()
        applyEditorField(info)
        typingGeneration.bump()
        if (ProductionIntegrationPolicy.shouldResetSession(restarting)) {
            defaultMode?.let { language = it.toKeyboardLanguage() }
            layoutMode = if (ImeLifecyclePolicy.shouldOpenNumberPadOnNewField(currentInputType, restarting)) {
                LayoutMode.NUMBERS
            } else {
                LayoutMode.LETTERS
            }
            lastCommittedWord = ""
            lastTwoCommittedWords = null
            if (ProductionIntegrationPolicy.shouldResetRepeatLearningOnNewField(restarting)) {
                repeatFinishes = RepeatFinishStore()
            }
        }
        if (InputConnectionPolicy.shouldFinishComposingOnFieldChange()) {
            finishComposingSpanOnly()
        }
        directTypingState.clear()
        internalSelectionChange = false
        hasSelection = false
        resetHandwriting()
        modeHistory.clear()
        toolbar.reset()
        languagePicker.dismiss()
        activationGuard.reset()
        keyBounce.reset()
        suggestionBounce.reset()
        stopBackspaceRepeat()
        lastSuggestionWords = emptyList()
        if (ImeLifecyclePolicy.shouldInvalidateSuggestionsOnFieldChange(restarting)) {
            suggestionQueryCache.invalidate()
        }
        captureClipboard()
        resetShift()
        if (::keyboardRoot.isInitialized) {
            applyWindowAppearance()
            renderKeyboard()
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            InputConnectionCommitter.finishComposing(currentInputConnection)
            romanComposer.reset()
        }
        directTypingState.clear()
        internalSelectionChange = false
        resetHandwriting()
        modeHistory.clear()
        updateLanguageFromSubtype(newSubtype)
        resetShift()
        layoutMode = LayoutMode.LETTERS
        if (::keyboardRoot.isInitialized) renderKeyboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewActive = false
        releaseTransientInput(finishComposing = ImeLifecyclePolicy.shouldFinishComposingOnHide())
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        inputViewActive = false
        releaseTransientInput(finishComposing = true)
        if (ProductionIntegrationPolicy.shouldResetGuardsOnHide()) {
            activationGuard.reset()
            keyBounce.reset()
            suggestionBounce.reset()
        }
        resetHandwriting()
        modeHistory.clear()
        toolbar.reset()
        suggestionRow = null
        super.onFinishInput()
    }

    override fun onDestroy() {
        inputViewActive = false
        handwritingJobs.cancel()
        if (ImeLifecyclePolicy.shouldStopBackspaceOnDestroy()) {
            typingGeneration.bump()
            stopBackspaceRepeat()
        }
        if (ImeLifecyclePolicy.shouldResetTouchOnDestroy()) {
            hideOverlays()
            touchState.reset()
        }
        handwritingSession.unload()
        suggestionQueryCache.invalidate()
        super.onDestroy()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        val cursorMoved = oldSelStart != newSelStart || oldSelEnd != newSelEnd
        hasSelection = newSelStart != newSelEnd
        if (romanComposerDelegate.isInitialized() &&
            CursorMovementPolicy.shouldInvalidateComposing(
                RomanSelectionState.movedAwayFromComposition(
                    romanComposer.currentWord,
                    newSelStart,
                    newSelEnd,
                    candidatesEnd
                )
            )
        ) {
            typingGeneration.bump()
            stopBackspaceRepeat()
            romanComposer.reset()
            InputConnectionCommitter.finishComposing(currentInputConnection)
            lastSuggestionWords = emptyList()
            suggestionQueryCache.invalidate()
            updateSuggestionRow()
        }
        if (internalSelectionChange) {
            internalSelectionChange = false
            return
        }
        if (language != KeyboardLanguage.ROMAN &&
            ImeLifecyclePolicy.shouldInvalidateSuggestionsOnSelectionChange(internal = false) &&
            CursorMovementPolicy.shouldInvalidateSuggestions(cursorMoved, internal = false)
        ) {
            typingGeneration.bump()
            stopBackspaceRepeat()
            directTypingState.clear()
            lastSuggestionWords = emptyList()
            suggestionQueryCache.invalidate()
            lastCommittedWord = ""
            lastTwoCommittedWords = null
            updateSuggestionRow()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ImeLifecyclePolicy.shouldResetTouchOnConfiguration()) {
            typingGeneration.bump()
            stopBackspaceRepeat()
            hideOverlays()
            touchState.reset()
        }
        readPreferences()
        keyHeightDp = preferredKeyHeight()
        if (::keyboardRoot.isInitialized) {
            applyWindowAppearance()
            renderKeyboard()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            val panelOpen = PanelNavigation.isTemporaryPanel(
                letters = layoutMode == LayoutMode.LETTERS,
                vowels = layoutMode == LayoutMode.VOWELS
            )
            when (PanelNavigation.consume(isOverlayOpen(), toolbar.isExpanded, panelOpen)) {
                PanelNavigationResult.CLOSED_OVERLAY -> {
                    hideOverlays()
                    return true
                }
                PanelNavigationResult.COLLAPSED_TOOLS -> {
                    toolbar.collapse()
                    renderKeyboard()
                    return true
                }
                PanelNavigationResult.CLOSED_PANEL -> {
                    returnToPreviousLayout()
                    return true
                }
                PanelNavigationResult.NONE -> Unit
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun applyEditorField(info: EditorInfo?) {
        currentInputType = info?.inputType ?: 0
        fieldAllowsLearning = EditorFieldPolicy.shouldLearn(currentInputType)
        fieldAllowsSuggestions = EditorFieldPolicy.shouldSuggest(currentInputType)
    }

    private fun releaseTransientInput(finishComposing: Boolean) {
        typingGeneration.bump()
        if (ImeLifecyclePolicy.shouldStopBackspaceOnHide()) stopBackspaceRepeat()
        if (ImeLifecyclePolicy.shouldInvalidateSuggestionsOnHide() ||
            ProductionIntegrationPolicy.shouldInvalidateSuggestionsOnHide()
        ) {
            suggestionQueryCache.invalidate()
            lastSuggestionWords = emptyList()
        }
        if (finishComposing) finishComposingSpanOnly()
        directTypingState.clear()
        internalSelectionChange = false
        if (ImeLifecyclePolicy.shouldResetTouchOnHide() || ImeTouchLifecycle.shouldResetOnHide()) {
            hideOverlays()
            touchState.reset()
        }
    }

    private fun finishComposingSpanOnly() {
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            InputConnectionCommitter.finishComposing(currentInputConnection)
            romanComposer.reset()
        } else {
            InputConnectionCommitter.finishComposing(currentInputConnection)
        }
    }

    private fun updateInitialLanguage(subtype: InputMethodSubtype?) {
        language = KeyboardModePolicy.initialLanguage(defaultMode, subtype?.locale.orEmpty())
    }

    private fun updateLanguageFromSubtype(subtype: InputMethodSubtype?) {
        language = KeyboardModePolicy.initialLanguage(null, subtype?.locale.orEmpty())
    }

    private fun readPreferences() {
        val preferences = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
        val repository = KeyboardSettingsRepository(SharedPreferencesSettingsStorage(preferences))
        val settings = repository.load()
        defaultMode = repository.savedDefaultMode()
        useSound = settings.keySound
        useVibration = settings.keyVibration
        useDarkAppearance = settings.appearance.isDark(systemUsesDarkTheme())
        useSuggestions = settings.suggestions
        useLearning = settings.learnedWords
        useSmartPunctuation = settings.smartPunctuation
        useDoubleSpacePeriod = settings.doubleSpacePeriod
        useAutoCapitalization = settings.autoCapitalization
        useEmojiRecents = settings.emojiRecents
        useToolbar = settings.toolbar
        useClipboardHistory = settings.clipboardHistory
        useLanguageButton = settings.languageButton
        useTypoSuggestions = settings.typoSuggestions
        oneHanded = settings.oneHanded
        presentationMode = settings.presentationMode
        toolbar.configuration = applyLanguageButton(repository.toolbarConfiguration())
        showNumberRow = settings.numberRow
        keyboardHeight = settings.height
        visualTheme = settings.visualTheme
        colorPreset = settings.colorPreset
        keyDensity = settings.keyDensity
        keySpacing = settings.keySpacing
        keyCorner = settings.keyCorner
        themeStyle = ThemeStyle(settings.keyShadows, settings.keyBorders, settings.pressedHighlight)
        soundVolume = settings.soundVolume
        hapticStrength = settings.hapticStrength
        cachedPalette = AppearanceCatalog.resolve(
            visualTheme,
            settings.appearance,
            systemUsesDarkTheme(),
            colorPreset
        )
        clipboardHistory = ClipboardRepository.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, null)
        )
        learnedRomanWords = LearnedRomanWords.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_LEARNED_ROMAN, null)
        )
        learnedEnglishWords = LearnedWordStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_LEARNED_ENGLISH, null)
        )
        learnedNepaliWords = LearnedWordStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_LEARNED_NEPALI, null)
        )
        recentEnglishWords = RecentWordStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_RECENT_ENGLISH, null)
        )
        recentNepaliWords = RecentWordStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_RECENT_NEPALI, null)
        )
        recentRomanWords = RecentWordStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_RECENT_ROMAN, null)
        )
        englishContext = ContextModel.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_CONTEXT_ENGLISH, null),
            ContextModel.ENGLISH_SEED
        )
        nepaliContext = ContextModel.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_CONTEXT_NEPALI, null),
            ContextModel.NEPALI_SEED
        )
        romanContext = ContextModel.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_CONTEXT_ROMAN, null),
            ContextModel.ROMAN_SEED
        )
        if (suggestionEngineDelegate.isInitialized()) {
            suggestionEngine.restorePhrases(
                SuggestionLanguage.ENGLISH,
                preferences.getString(KeyboardPreferences.KEY_PHRASE_ENGLISH, null)
            )
            suggestionEngine.restorePhrases(
                SuggestionLanguage.NEPALI,
                preferences.getString(KeyboardPreferences.KEY_PHRASE_NEPALI, null)
            )
            suggestionEngine.restorePhrases(
                SuggestionLanguage.ROMAN,
                preferences.getString(KeyboardPreferences.KEY_PHRASE_ROMAN, null)
            )
        }
        emojiUsage = EmojiUsageStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_EMOJI_USAGE, null)
        )
        recentSymbols = RecentSymbolStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_RECENT_SYMBOLS, null)
        )
        adaptiveHitboxes = AdaptiveHitboxPolicy.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_TOUCH_ADAPTATION, null)
        )
    }

    private fun ensureEmojiDataset() {
        emojiDataset.value
    }

    private fun renderKeyboard() {
        stopBackspaceRepeat()
        hideOverlays()
        touchState.reset()
        boundTypingKeys.clear()
        val colors = keyboardColors()
        applyPresentationPadding(colors)
        keyboardRoot.removeAllViews()
        suggestionRow = null
        lastSuggestionWords = emptyList()
        suggestionQueryCache.invalidate()
        suggestionSettingsVisible = false
        handwritingResultRow = null
        handwritingCanvas = null
        previewView?.bind(colors, dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat())

        if (layoutMode == LayoutMode.HANDWRITING) {
            renderHandwriting(colors)
            return
        }
        if (layoutMode == LayoutMode.EMOJI) {
            renderEmojiPanel(colors)
            return
        }
        if (layoutMode == LayoutMode.CLIPBOARD) {
            renderClipboardPanel(colors)
            return
        }

        val supportsSuggestions = layoutMode == LayoutMode.LETTERS ||
            (language == KeyboardLanguage.NEPALI && layoutMode == LayoutMode.VOWELS)
        val useCompactNepaliSuggestions = useSuggestions && supportsSuggestions &&
            language == KeyboardLanguage.NEPALI
        if (useToolbar && toolbar.configuration.alwaysVisible && supportsSuggestions) {
            addToolbarRow(colors)
        }
        if (useSuggestions && fieldAllowsSuggestions && supportsSuggestions) {
            addSuggestionRow(colors, includeSettings = useCompactNepaliSuggestions)
        }
        if (!useCompactNepaliSuggestions) addNavigationRow(colors)

        val rows = when {
            layoutMode == LayoutMode.NUMBERS -> KeyboardLayouts.numbers(language, digitScript)
            layoutMode == LayoutMode.SYMBOLS -> KeyboardLayouts.symbols(language, symbolGroup, recentSymbols.values())
            language == KeyboardLanguage.ENGLISH || language == KeyboardLanguage.ROMAN ->
                KeyboardLayouts.english(
                    ShiftPolicy.lettersUppercase(shiftState),
                    language,
                    showNumberRow,
                    ShiftPolicy.isCapsLock(shiftState)
                )
            layoutMode == LayoutMode.VOWELS -> KeyboardLayouts.nepaliVowels(showNumberRow)
            else -> KeyboardLayouts.nepaliConsonants(showNumberRow)
        }
        addKeyRows(rows, colors)
    }

    private fun addKeyRows(rows: List<List<KeySpec>>, colors: KeyboardPalette) {
        rows.forEach { keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val rowHeight = if (keys.isNotEmpty() && keys.all { it.compact }) {
                (KeyboardUiMetrics.compactNumberRowHeightDp(keyboardHeight) + KeyboardUiMetrics.densityDelta(keyDensity)).coerceAtLeast(30)
            } else {
                keyHeightDp
            }
            keyboardRoot.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(rowHeight))
            )
            keys.forEachIndexed { index, key ->
                val edge = when {
                    keys.size == 1 -> KeyEdge.ALONE
                    index == 0 -> KeyEdge.START
                    index == keys.lastIndex -> KeyEdge.END
                    else -> KeyEdge.MIDDLE
                }
                row.addView(createKeyButton(key, colors, edge))
            }
        }
    }

    private fun addToolbarRow(colors: KeyboardPalette) {
        val current = when (layoutMode) {
            LayoutMode.EMOJI -> ToolbarAction.EMOJI
            LayoutMode.CLIPBOARD -> ToolbarAction.CLIPBOARD
            LayoutMode.NUMBERS -> ToolbarAction.NUMBERS
            LayoutMode.SYMBOLS -> ToolbarAction.SYMBOLS
            LayoutMode.HANDWRITING -> ToolbarAction.HANDWRITING
            else -> null
        }
        val items = toolbar.items(language, current, resources.configuration.screenWidthDp)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(KeyboardUiMetrics.toolbarHeightDp(
                    resources.configuration.screenWidthDp,
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                    keyboardHeight
                ))
            )
        )
        items.forEach { item ->
            row.addView(chromeLabel(item.label, item.description, colors, item.selected).apply {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (resources.configuration.screenWidthDp < 360 || items.size > 6) 11f else 13f
                )
                minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                contentDescription = AccessibilityLabels.toolbar(item)
                setOnClickListener {
                    if (consumeChooserTap()) return@setOnClickListener
                    if (!activationGuard.allow(item.action.name, SystemClock.uptimeMillis())) return@setOnClickListener
                    giveFeedback(ToolbarController.keyAction(item.action), FeedbackKind.CHROME)
                    handleToolbarAction(item.action)
                }
                if (item.action == ToolbarAction.LANGUAGE) {
                    setOnLongClickListener { view ->
                        showLanguagePicker(view)
                        true
                    }
                }
            })
        }
    }

    private fun handleToolbarAction(action: ToolbarAction) {
        when (action) {
            ToolbarAction.MORE -> {
                toolbar.expand()
                renderKeyboard()
            }
            ToolbarAction.COLLAPSE -> {
                toolbar.collapse()
                renderKeyboard()
            }
            ToolbarAction.CLIPBOARD -> {
                if (layoutMode == LayoutMode.CLIPBOARD) return
                openClipboard()
            }
            ToolbarAction.EMOJI -> {
                if (layoutMode == LayoutMode.EMOJI) return
                toolbar.collapse()
                openPanel(LayoutMode.EMOJI)
            }
            ToolbarAction.NUMBERS -> {
                if (layoutMode == LayoutMode.NUMBERS) return
                toolbar.collapse()
                openPanel(LayoutMode.NUMBERS)
            }
            ToolbarAction.SYMBOLS -> {
                if (layoutMode == LayoutMode.SYMBOLS) return
                toolbar.collapse()
                openPanel(LayoutMode.SYMBOLS)
            }
            ToolbarAction.HANDWRITING -> {
                if (layoutMode == LayoutMode.HANDWRITING) return
                toolbar.collapse()
                openHandwriting()
            }
            ToolbarAction.SETTINGS -> openSettings()
            ToolbarAction.LANGUAGE -> {
                toolbar.maybeAutoCollapse()
                switchTypingMode(LanguageSwitcher.cycle(language))
            }
            ToolbarAction.MODE_ENGLISH -> {
                toolbar.collapse()
                switchTypingMode(KeyboardLanguage.ENGLISH)
            }
            ToolbarAction.MODE_NEPALI -> {
                toolbar.collapse()
                switchTypingMode(KeyboardLanguage.NEPALI)
            }
            ToolbarAction.MODE_ROMAN -> {
                toolbar.collapse()
                switchTypingMode(KeyboardLanguage.ROMAN)
            }
        }
    }

    private fun addNavigationRow(colors: KeyboardPalette) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(navigationRowHeightDp())
            )
        )
        val numberLabel = if (layoutMode == LayoutMode.SYMBOLS) "#+=" else "123"
        KeyboardLayouts.navigationControls(numberLabel).forEach { key ->
            row.addView(createKeyButton(key, colors))
        }
        row.addView(createKeyButton(KeySpec("⚙", KeyAction.SETTINGS), colors))
    }

    private fun renderEmojiPanel(colors: KeyboardPalette) {
        ensureEmojiDataset()
        val categories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        EmojiCategory.entries.forEach { category ->
            val selected = category == emojiCategory
            categories.addView(TextView(this).apply {
                text = KeyVisuals.emojiCategoryIcon(category)
                contentDescription = category.title
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (selected) KeyboardThemeTokens.selectedLabel(colors) else colors.text)
                background = KeyboardTheme.keyBackground(
                    if (selected) KeyboardThemeTokens.toolbarSelected(colors) else KeyboardThemeTokens.toolbarFill(colors),
                    dp(KeyboardUiMetrics.cornerRadiusDp(keyCorner)).toFloat(),
                    colors.shadow,
                    if (themeStyle.shadows) dp(KeyboardTheme.SHADOW_DP) else 0,
                    if (themeStyle.borders) colors.divider else null,
                    themeStyle.pressedHighlight
                )
                layoutParams = LinearLayout.LayoutParams(
                    dp(KeyboardUiMetrics.emojiCategoryWidthDp(resources.configuration.screenWidthDp)),
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    val margin = preferredKeyMargin()
                    setMargins(margin, dp(2), margin, dp(2))
                }
                setOnClickListener {
                    giveFeedback(KeyAction.EMOJI, FeedbackKind.NAVIGATION)
                    emojiCategory = category
                    if (category != EmojiCategory.SEARCH) emojiQuery = ""
                    renderKeyboard()
                }
            })
        }
        keyboardRoot.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(categories)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KeyboardUiMetrics.EMOJI_CATEGORY_HEIGHT_DP))
        )

        if (emojiCategory == EmojiCategory.SEARCH) {
            keyboardRoot.addView(TextView(this).apply {
                text = emojiQuery.ifEmpty { getString(R.string.emoji_search_hint) }
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                textSize = 14f
                setTextColor(if (emojiQuery.isEmpty()) colors.secondaryText else colors.text)
                background = KeyboardTheme.roundedRect(
                    colors.key,
                    dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat()
                )
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply {
                setMargins(dp(4), dp(2), dp(4), dp(2))
            })
        }

        val emojis = emojiGlyphsForCurrentCategory()
        if (emojis.isEmpty()) {
            val message = when {
                emojiCategory == EmojiCategory.SEARCH && emojiQuery.isNotEmpty() -> R.string.emoji_search_empty
                emojiCategory == EmojiCategory.SEARCH -> R.string.emoji_search_hint
                else -> R.string.emoji_no_recent
            }
            keyboardRoot.addView(TextView(this).apply {
                text = getString(message)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.secondaryText)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)))
            if (emojiCategory == EmojiCategory.SEARCH && emojiQuery.isEmpty()) {
                addEmojiKeywordChips(colors)
            }
        } else {
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val columns = KeyboardUiMetrics.emojiColumns(resources.configuration.screenWidthDp)
            emojis.chunked(columns).forEach { emojiRow ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                grid.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(KeyboardUiMetrics.emojiKeyHeightDp(keyboardHeight))
                    )
                )
                emojiRow.forEach { emoji -> row.addView(createEmojiButton(emoji, colors)) }
                repeat(columns - emojiRow.size) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            keyboardRoot.addView(
                ScrollView(this).apply {
                    isVerticalScrollBarEnabled = false
                    addView(grid)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(KeyboardUiMetrics.emojiKeyHeightDp(keyboardHeight) * EMOJI_VISIBLE_ROWS)
                )
            )
        }
        if (emojiCategory == EmojiCategory.SEARCH) {
            addKeyRows(KeyboardLayouts.emojiSearchLetters(), colors)
        }
        addKeyRows(KeyboardLayouts.emojiControls(), colors)
    }

    private fun renderClipboardPanel(colors: KeyboardPalette) {
        captureClipboard()
        keyboardRoot.addView(TextView(this).apply {
            text = getString(R.string.clipboard_title)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors.text)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))

        val entries = if (useClipboardHistory) clipboardHistory.values() else emptyList()
        if (entries.isEmpty()) {
            keyboardRoot.addView(TextView(this).apply {
                text = getString(
                    if (useClipboardHistory) R.string.clipboard_empty else R.string.clipboard_disabled
                )
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.secondaryText)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)))
        } else {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            entries.forEach { entry ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = KeyboardTheme.roundedRect(
                        colors.key,
                        dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat()
                    )
                }
                row.minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                row.addView(TextView(this).apply {
                    text = ClipboardPolicy.preview(entry.text)
                    contentDescription = getString(R.string.clipboard_item_description)
                    setTextColor(colors.text)
                    textSize = 14f
                    isClickable = true
                    isFocusable = true
                    minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                    setOnClickListener {
                        giveFeedback(KeyAction.CLIPBOARD)
                        insertClipboardText(entry.text)
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "✕"
                    contentDescription = getString(R.string.clipboard_delete)
                    gravity = Gravity.CENTER
                    setTextColor(colors.secondaryText)
                    setPadding(dp(10), 0, dp(4), 0)
                    isClickable = true
                    isFocusable = true
                    minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                    minWidth = dp(AccessibilityLabels.MIN_TOUCH_DP)
                    setOnClickListener {
                        giveFeedback(KeyAction.CLIPBOARD, FeedbackKind.CHROME)
                        clipboardHistory.delete(entry.id)
                        persistClipboard()
                        renderKeyboard()
                    }
                })
                list.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(6), dp(3), dp(6), dp(3)) })
            }
            keyboardRoot.addView(
                ScrollView(this).apply {
                    isVerticalScrollBarEnabled = false
                    addView(list)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(clipboardPanelHeight())
                )
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(createKeyButton(KeySpec("Back", KeyAction.RETURN_TO_PREVIOUS), colors))
        if (useClipboardHistory && clipboardHistory.values().isNotEmpty()) {
            controls.addView(chromeLabel(getString(R.string.clear_action), getString(R.string.clear_clipboard), colors, false).apply {
                setOnClickListener {
                    clipboardHistory.clear()
                    persistClipboard()
                    renderKeyboard()
                }
            })
        }
        keyboardRoot.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(navigationRowHeightDp())
            )
        )
    }

    private fun openClipboard() {
        toolbar.collapse()
        if (PanelTransitionPolicy.shouldFinishComposing(openingPanel = true)) {
            finishComposingForTransition()
        }
        directTypingState.clear()
        internalSelectionChange = false
        if (layoutMode != LayoutMode.CLIPBOARD) {
            modeHistory.remember(ModeSnapshot(language, layoutMode))
        }
        resetShift()
        layoutMode = LayoutMode.CLIPBOARD
        captureClipboard()
        renderKeyboard()
    }

    private fun insertClipboardText(text: String) {
        val composing = language == KeyboardLanguage.ROMAN && romanComposer.currentWord.isNotEmpty()
        ClipboardInsertion.prepareThenInsert(
            hasComposingText = composing || directTypingState.currentWord.isNotEmpty(),
            finishComposing = {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(finishRomanWord())
                } else {
                    InputConnectionCommitter.finishComposing(currentInputConnection)
                    rememberFinishedDirectWord(directTypingState.currentWord, learnUnknown = false)
                    directTypingState.clear()
                }
            },
            insert = { snippet -> InputConnectionCommitter.commit(currentInputConnection, snippet) },
            text = text
        )
        updateSuggestionRow()
    }

    private fun captureClipboard() {
        if (!useClipboardHistory) return
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = manager.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (clipboardHistory.record(text)) persistClipboard()
    }

    private fun persistClipboard() {
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyboardPreferences.KEY_CLIPBOARD_ITEMS, clipboardHistory.serialize())
            .apply()
    }

    private fun emojiGlyphsForCurrentCategory(): List<String> {
        val frequency = { glyph: String -> emojiUsage.score(glyph) }
        return when (emojiCategory) {
            EmojiCategory.SEARCH -> EmojiCatalog.search(emojiQuery, 48, frequency)
            EmojiCategory.RECENT -> recentEmojis.values()
            else -> EmojiCatalog.repository().emojis(emojiCategory, recentEmojis.values(), frequency)
        }
    }

    private fun addEmojiKeywordChips(colors: KeyboardPalette) {
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        EmojiCatalog.repository().popularKeywords(8).forEach { keyword ->
            chips.addView(TextView(this).apply {
                text = keyword
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(dp(8), 0, dp(8), 0)
                setTextColor(colors.text)
                background = KeyboardTheme.roundedRect(
                    colors.key,
                    dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat()
                )
                setOnClickListener {
                    giveFeedback(KeyAction.EMOJI, FeedbackKind.NAVIGATION)
                    emojiQuery = keyword
                    renderKeyboard()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(2), dp(4), dp(2), dp(4))
            })
        }
        keyboardRoot.addView(
            chips,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36))
        )
    }

    private fun createEmojiButton(emoji: String, colors: KeyboardPalette): View {
        return KeyboardKeyView(this).apply {
            bind(
                key = KeySpec(emoji),
                palette = colors,
                active = false,
                compactScreen = resources.configuration.screenWidthDp < 360,
                horizontalGapPx = preferredKeyMargin(),
                verticalGapPx = dp(2),
                radiusPx = dp(KeyboardUiMetrics.cornerRadiusDp(keyCorner)).toFloat(),
                shadowPx = if (themeStyle.shadows) dp(KeyboardTheme.SHADOW_DP) else 0,
                borderColor = if (themeStyle.borders) colors.divider else null,
                pressedEnabled = themeStyle.pressedHighlight
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            contentDescription = AccessibilityLabels.emoji(emoji)
            setOnClickListener {
                if (consumeChooserTap()) return@setOnClickListener
                giveFeedback(KeyAction.EMOJI)
                insertEmoji(emoji)
            }
            val variants = EmojiCatalog.variants(emoji)
            if (variants.isNotEmpty()) {
                setOnLongClickListener { view ->
                    previewView?.dismiss()
                    giveFeedback(KeyAction.EMOJI, FeedbackKind.LONG_PRESS)
                    showOverlayShield()
                    alternateChooser?.showAbove(
                        view,
                        overlayHost,
                        variants,
                        colors,
                        dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat()
                    )
                    true
                }
            }
        }
    }

    private fun insertEmoji(emoji: String) {
        if (language == KeyboardLanguage.ROMAN) {
            applyRomanEdit(PanelInsertionPolicy.romanEditBeforeInsert(romanComposer))
        } else {
            InputConnectionCommitter.finishComposing(currentInputConnection)
            directTypingState.clear()
        }
        val connection = currentInputConnection ?: return
        if (!InputConnectionCommitter.commit(connection, emoji)) return
        if (useEmojiRecents) {
            recentEmojis.record(emoji)
            emojiUsage.record(emoji)
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KeyboardPreferences.KEY_RECENT_EMOJIS, recentEmojis.serialize())
                .putString(KeyboardPreferences.KEY_EMOJI_USAGE, emojiUsage.serialize())
                .apply()
        }
        updateSuggestionRow()
    }

    private fun renderHandwriting(colors: KeyboardPalette) {
        keyboardRoot.addView(TextView(this).apply {
            text = HandwritingUiState.showsLanguage(language)
            contentDescription = AccessibilityLabels.handwritingCanvas(language)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            textSize = 13f
            setTextColor(colors.secondaryText)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

        val resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        handwritingResultRow = resultRow
        keyboardRoot.addView(
            resultRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KeyboardUiMetrics.HANDWRITING_RESULT_HEIGHT_DP))
        )

        val canvas = HandwritingCanvasView(this).apply {
            contentDescription = AccessibilityLabels.handwritingCanvas(language)
            setInkColor(colors.text)
            background = KeyboardTheme.roundedRect(
                colors.key,
                dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat()
            )
            onStrokeFinished = { points ->
                if (HandwritingLifecyclePolicy.shouldCancelOnNewStroke()) handwritingJobs.cancel()
                handwritingState.addStroke(points)
                handwritingSession.addStroke(points)
                updateHandwritingResultRow(colors)
            }
        }
        handwritingCanvas = canvas
        keyboardRoot.addView(
            canvas,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(preferredCanvasHeight())
            ).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) }
        )

        updateHandwritingResultRow(colors)
        addKeyRows(KeyboardLayouts.handwritingControls(), colors)
    }

    private fun updateHandwritingResultRow(colors: KeyboardPalette = keyboardColors()) {
        val row = handwritingResultRow ?: return
        row.removeAllViews()
        if (handwritingState.candidates.isNotEmpty()) {
            handwritingState.candidates.forEachIndexed { index, candidate ->
                row.addView(chromeLabel(
                    candidate,
                    AccessibilityLabels.handwritingCandidate(candidate, primary = index == 0),
                    colors,
                    selected = false
                ).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    contentDescription = AccessibilityLabels.handwritingCandidate(candidate, primary = index == 0)
                    setOnClickListener {
                        giveFeedback(KeyAction.TEXT)
                        insertHandwritingCandidate(index, colors)
                    }
                })
            }
            return
        }

        val message = when (handwritingState.status) {
            HandwritingStatus.EMPTY -> R.string.handwriting_hint
            HandwritingStatus.READY -> R.string.handwriting_ready
            HandwritingStatus.RECOGNIZING -> R.string.handwriting_recognizing
            HandwritingStatus.NO_MATCH -> R.string.handwriting_no_match
            HandwritingStatus.RECOGNIZER_UNAVAILABLE -> R.string.handwriting_unavailable
            HandwritingStatus.BLOCKED -> R.string.handwriting_blocked
            HandwritingStatus.RESULTS -> R.string.handwriting_no_match
        }
        row.addView(TextView(this).apply {
            text = getString(message)
            contentDescription = AccessibilityLabels.handwritingStatus(handwritingState.status)
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(colors.secondaryText)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun insertHandwritingCandidate(index: Int, colors: KeyboardPalette) {
        if (ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(inputViewActive)) return
        if (!HandwritingPrivacyPolicy.allowsRecognition(currentInputType)) {
            handwritingState.blockSensitiveField()
            updateHandwritingResultRow(colors)
            return
        }
        val candidate = handwritingState.confirm(index) ?: return
        if (HandwritingSuggestionBridge.shouldAutoCommit(emptyList())) return
        InputConnectionCommitter.commit(currentInputConnection, candidate)
        if (HandwritingPrivacyPolicy.shouldLearnCommitted(currentInputType, candidate)) {
            rememberFinishedDirectWord(candidate, learnUnknown = false)
        } else {
            lastCommittedWord = candidate
        }
        suggestionQueryCache.invalidate()
        handwritingCanvas?.clearInk()
        updateHandwritingResultRow(colors)
        updateSuggestionRow(colors)
    }

    private fun requestHandwritingRecognition() {
        val sessionResult = handwritingSession.recognize(currentInputType)
        if (sessionResult.status == HandwritingStatus.BLOCKED ||
            !HandwritingPrivacyPolicy.allowsRecognition(currentInputType)
        ) {
            handwritingJobs.cancel()
            handwritingState.blockSensitiveField()
            return
        }
        handwritingJobs.bump()
        if (sessionResult.status == HandwritingStatus.RESULTS) {
            handwritingState.acceptExternal(
                sessionResult.suggestionTexts(),
                HandwritingStatus.RESULTS
            )
            return
        }
        handwritingState.recognize(language)
    }

    private fun resetHandwriting() {
        if (ImeLifecyclePolicy.shouldCancelHandwritingOnHide() ||
            ImeLifecyclePolicy.shouldCancelHandwritingOnFieldChange()
        ) {
            handwritingJobs.cancel()
        }
        handwritingSession.reset()
        handwritingState.clear()
        handwritingCanvas?.clearInk()
        handwritingResultRow = null
        handwritingCanvas = null
    }

    private fun preferredCanvasHeight(): Int {
        val configuration = resources.configuration
        return KeyboardUiMetrics.handwritingCanvasHeightDp(
            configuration.screenHeightDp,
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            keyboardHeight
        )
    }

    private fun addSuggestionRow(colors: KeyboardPalette, includeSettings: Boolean) {
        suggestionSettingsVisible = includeSettings
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        suggestionRow = row
        lastSuggestionWords = emptyList()
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(suggestionRowHeightDp())
            )
        )
        updateSuggestionRow(colors)
    }

    private fun updateSuggestionRow(colors: KeyboardPalette = keyboardColors()) {
        val row = suggestionRow ?: return
        val currentWord = if (language == KeyboardLanguage.ROMAN) {
            romanComposer.currentWord
        } else {
            directTypingState.currentWord
        }
        val context = editorContext(currentWord)
        val previous = context.previousWord ?: lastCommittedWord.ifEmpty { null }
        val suggestionLanguage = when (language) {
            KeyboardLanguage.ENGLISH -> SuggestionLanguage.ENGLISH
            KeyboardLanguage.NEPALI -> SuggestionLanguage.NEPALI
            KeyboardLanguage.ROMAN -> SuggestionLanguage.ROMAN
        }
        val fingerprint = suggestionQueryCache.fingerprint(
            language = suggestionLanguage,
            input = currentWord,
            previous = previous,
            previousTwo = context.previousTwoWords,
            includeTypos = useTypoSuggestions,
            includeEmoji = language != KeyboardLanguage.NEPALI && useSuggestions,
            previousThree = context.previousThreeWords,
            editorKind = EditorFieldPolicy.kind(currentInputType),
            privateField = !fieldAllowsSuggestions || !fieldAllowsLearning,
            hasSelection = hasSelection
        )
        val cachedSuggestions = suggestionQueryCache.hit(fingerprint)
        val suggestions = if (cachedSuggestions != null) {
            cachedSuggestions
        } else {
        val query = SuggestionQuery(
            input = currentWord,
            language = suggestionLanguage,
            previousWord = previous,
            previousTwoWords = context.previousTwoWords,
            previousThreeWords = context.previousThreeWords,
            learned = when (language) {
                KeyboardLanguage.ENGLISH -> if (useLearning) learnedEnglishWords.suggestions(currentWord) else emptyList()
                KeyboardLanguage.NEPALI -> if (useLearning) learnedNepaliWords.suggestions(currentWord) else emptyList()
                KeyboardLanguage.ROMAN -> emptyList()
            },
            recent = when (language) {
                KeyboardLanguage.ENGLISH -> recentEnglishWords.matches(currentWord)
                KeyboardLanguage.NEPALI -> recentNepaliWords.matches(currentWord)
                KeyboardLanguage.ROMAN -> recentRomanWords.matches(currentWord)
            },
            learnedRoman = if (useLearning) learnedRomanWords.lookup(currentWord) else null,
            includeEmoji = language != KeyboardLanguage.NEPALI && useSuggestions,
            includeTypos = useTypoSuggestions,
            contextPredictions = when (language) {
                KeyboardLanguage.ENGLISH -> englishContext.predictions(
                    previous.orEmpty(),
                    currentWord,
                    MAX_SUGGESTIONS,
                    context.previousTwoWords,
                    context.previousThreeWords
                )
                KeyboardLanguage.NEPALI -> nepaliContext.predictions(
                    previous.orEmpty(),
                    currentWord,
                    MAX_SUGGESTIONS,
                    context.previousTwoWords,
                    context.previousThreeWords
                )
                KeyboardLanguage.ROMAN -> romanContext.predictions(
                    previous.orEmpty(),
                    currentWord,
                    MAX_SUGGESTIONS,
                    context.previousTwoWords,
                    context.previousThreeWords
                )
            },
            personalFrequency = when (language) {
                KeyboardLanguage.ENGLISH ->
                    if (useLearning) learnedEnglishWords.frequencyMap(currentWord) else emptyMap()
                KeyboardLanguage.NEPALI ->
                    if (useLearning) learnedNepaliWords.frequencyMap(currentWord) else emptyMap()
                KeyboardLanguage.ROMAN -> emptyMap()
            }
        )
        val engineResult = suggestionEngine.suggest(query, MAX_SUGGESTIONS)
        val rawSuggestions = engineResult.visible(MAX_SUGGESTIONS)
        val mapped = if (language == KeyboardLanguage.ENGLISH) {
            rawSuggestions.map {
                if (it.any { character -> character.code in 0x0900..0x097F } || EmojiCatalog.contains(it)) {
                    it
                } else {
                    CapitalizationPolicy.applyToWord(it, context.textBeforeCursor, useAutoCapitalization)
                }
            }
        } else {
            rawSuggestions
        }
        val computed = SuggestionBarState.display(mapped, currentWord, MAX_SUGGESTIONS)
            suggestionQueryCache.remember(fingerprint, computed)
            computed
        }
        if (SuggestionBarState.unchanged(lastSuggestionWords, suggestions) && row.childCount > 0) {
            return
        }
        lastSuggestionWords = suggestions
        row.removeAllViews()
        row.setBackgroundColor(KeyboardThemeTokens.suggestionBackground(colors))
        if (SuggestionBarStyle.showsEmptyHint(suggestions)) {
            val hint = when (language) {
                KeyboardLanguage.ENGLISH -> R.string.english_suggestion_hint
                KeyboardLanguage.NEPALI -> R.string.nepali_suggestion_hint
                KeyboardLanguage.ROMAN -> R.string.roman_suggestion_hint
            }
            row.addView(TextView(this).apply {
                text = if (currentWord.isEmpty()) getString(hint) else currentWord
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.secondaryText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            if (suggestionSettingsVisible) addSuggestionSettings(row, colors)
            return
        }

        SuggestionBarState.gboardSlots(suggestions, MAX_SUGGESTIONS).forEachIndexed { index, slot ->
            if (index > 0) row.addView(suggestionDivider(colors))
            val suggestion = slot.text
            if (suggestion == null) {
                row.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                return@forEachIndexed
            }
            row.addView(TextView(this).apply {
                text = suggestion
                contentDescription = AccessibilityLabels.suggestion(suggestion, slot.primary)
                isClickable = true
                isFocusable = true
                isAllCaps = false
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
                setTypeface(typeface, if (slot.primary) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SuggestionBarStyle.textSizeSp(slot.primary, suggestion))
                setTextColor(
                    if (slot.primary) KeyboardThemeTokens.suggestionPrimary(colors)
                    else KeyboardThemeTokens.suggestionSecondary(colors)
                )
                background = KeyboardTheme.flatKeyBackground(
                    KeyboardThemeTokens.suggestionBackground(colors),
                    dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat()
                )
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    if (consumeChooserTap()) return@setOnClickListener
                    giveFeedback(KeyAction.TEXT)
                    acceptSuggestion(suggestion, colors)
                }
            })
        }
        if (suggestionSettingsVisible) {
            row.addView(suggestionDivider(colors))
            addSuggestionSettings(row, colors)
        }
    }

    private fun addSuggestionSettings(row: LinearLayout, colors: KeyboardPalette) {
        row.addView(createKeyButton(KeySpec("⚙", KeyAction.SETTINGS), colors).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT)
        })
    }

    private fun suggestionDivider(colors: KeyboardPalette): View = View(this).apply {
        setBackgroundColor(KeyboardThemeTokens.suggestionDivider(colors))
        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    private fun acceptSuggestion(suggestion: String, colors: KeyboardPalette) {
        if (ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(inputViewActive)) return
        if (!suggestionBounce.allow(suggestion, SystemClock.uptimeMillis())) return
        if (ProductionIntegrationPolicy.shouldInvalidateSuggestionsOnAccept()) {
            suggestionQueryCache.invalidate()
        }
        val context = editorContext(
            if (language == KeyboardLanguage.ROMAN) romanComposer.currentWord else directTypingState.currentWord
        )
        val previous = context.previousWord ?: lastCommittedWord.ifEmpty { null }
        val previousTwo = context.previousTwoWords ?: lastTwoCommittedWords
        if (language == KeyboardLanguage.ROMAN) {
            val romanWord = romanComposer.currentWord
            if (romanWord.isEmpty()) {
                applyRomanEdit(RomanEdit.Commit(suggestion))
                rememberAcceptedContext(suggestion, previous, previousTwo)
                updateSuggestionRow(colors)
                return
            }
            if (useLearning && fieldAllowsLearning && learnedRomanWords.learn(romanWord, suggestion)) {
                getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KeyboardPreferences.KEY_LEARNED_ROMAN, learnedRomanWords.serialize())
                    .apply()
            }
            applyRomanEdit(romanComposer.acceptSuggestion(suggestion))
            recentRomanWords.record(suggestion)
            persistRecentWords(KeyboardPreferences.KEY_RECENT_ROMAN, recentRomanWords)
            rememberAcceptedContext(suggestion, previous, previousTwo)
            updateSuggestionRow(colors)
            return
        }

        val connection = currentInputConnection ?: return
        val before = textBeforeCursor(InputConnectionPolicy.SUGGESTION_CONTEXT)
        val after = textAfterCursor(InputConnectionPolicy.AFTER_CURSOR_CONTEXT)
        val tracked = directTypingState.currentWord
        val editorWord = WordBoundaryPolicy.wordBeforeCursor(before)
        if (tracked.isEmpty() && editorWord.isEmpty()) {
            commitText(suggestion)
            rememberFinishedDirectWord(suggestion, learnUnknown = false)
            if (useLearning && fieldAllowsLearning && PersonalDictionary.shouldAccept(suggestion)) {
                persistLearnedWord(suggestion)
            }
            rememberAcceptedContext(suggestion, previous, previousTwo)
            updateSuggestionRow(colors)
            return
        }
        val word = if (SuggestionSelectionPlan.matchesCurrentWord(before, tracked)) {
            tracked
        } else {
            editorWord.ifEmpty { tracked }
        }
        val replacement = SuggestionSelectionPlan.create(word, suggestion, before, after)
        if (replacement == null) {
            directTypingState.clear()
            updateSuggestionRow(colors)
            return
        }
        if (replacement.changesText || replacement.deleteAfterCodeUnits > 0) {
            internalSelectionChange = true
            val afterPoints = if (replacement.deleteAfterCodeUnits == 0) {
                0
            } else {
                after.take(replacement.deleteAfterCodeUnits)
                    .codePointCount(0, replacement.deleteAfterCodeUnits)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                InputConnectionCommitter.deleteSurroundingInCodePoints(
                    connection,
                    replacement.deleteCodePoints,
                    afterPoints,
                    replacement.deleteCodeUnits,
                    replacement.deleteAfterCodeUnits
                )
            } else {
                InputConnectionCommitter.deleteSurrounding(
                    connection,
                    replacement.deleteCodeUnits,
                    replacement.deleteAfterCodeUnits
                )
            }
            InputConnectionCommitter.commitRaw(connection, replacement.replacement)
        }
        directTypingState.replaceWith(suggestion)
        if (useLearning && fieldAllowsLearning && PersonalDictionary.shouldAccept(suggestion)) {
            persistLearnedWord(suggestion)
        }
        rememberAcceptedContext(suggestion, previous, previousTwo)
        updateSuggestionRow(colors)
    }

    private fun rememberFinishedDirectWord(word: String, learnUnknown: Boolean = true) {
        if (word.isBlank() || !fieldAllowsLearning) {
            if (word.isNotBlank()) lastCommittedWord = word
            return
        }
        when (language) {
            KeyboardLanguage.ENGLISH -> {
                recordContext(englishContext, KeyboardPreferences.KEY_CONTEXT_ENGLISH, word)
                recentEnglishWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_ENGLISH, recentEnglishWords)
                if (useLearning && learnUnknown &&
                    PersonalDictionary.shouldLearnRepeated(
                        word,
                        repeatFinishes.record(word),
                        englishSuggester::contains
                    )
                ) {
                    persistLearnedWord(word)
                }
            }
            KeyboardLanguage.NEPALI -> {
                recordContext(nepaliContext, KeyboardPreferences.KEY_CONTEXT_NEPALI, word)
                recentNepaliWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_NEPALI, recentNepaliWords)
                if (useLearning && learnUnknown &&
                    PersonalDictionary.shouldLearnRepeated(
                        word,
                        repeatFinishes.record(word),
                        nepaliSuggester::contains
                    )
                ) {
                    persistLearnedWord(word)
                }
            }
            KeyboardLanguage.ROMAN -> Unit
        }
        lastCommittedWord = word
    }

    private fun rememberFinishedRomanWord() {
        val typed = romanComposer.currentWord
        if (typed.isEmpty()) return
        if (!fieldAllowsLearning) {
            lastCommittedWord = typed
            return
        }
        recordContext(romanContext, KeyboardPreferences.KEY_CONTEXT_ROMAN, typed)
        recentRomanWords.record(typed)
        persistRecentWords(KeyboardPreferences.KEY_RECENT_ROMAN, recentRomanWords)
        lastCommittedWord = typed
    }

    private fun finishRomanWord(boundary: String = ""): RomanEdit {
        val previous = lastCommittedWord.ifEmpty { null }
        rememberFinishedRomanWord()
        return romanComposer.finishWord(boundary, previous)
    }

    private fun recordContext(model: ContextModel, key: String, word: String) {
        if (lastCommittedWord.isNotEmpty() &&
            PersonalDictionary.shouldLearnPhrase(lastCommittedWord, word)
        ) {
            model.record(lastCommittedWord, word, lastTwoCommittedWords)
            val suggestionLanguage = when (language) {
                KeyboardLanguage.ENGLISH -> SuggestionLanguage.ENGLISH
                KeyboardLanguage.NEPALI -> SuggestionLanguage.NEPALI
                KeyboardLanguage.ROMAN -> SuggestionLanguage.ROMAN
            }
            suggestionEngine.recordPhrase(suggestionLanguage, lastCommittedWord, word, lastTwoCommittedWords)
            persistContextAndPhrases(model, key, suggestionLanguage)
        }
        lastTwoCommittedWords = if (lastCommittedWord.isNotEmpty()) {
            "$lastCommittedWord $word"
        } else {
            word
        }
    }

    private fun rememberAcceptedContext(word: String, previous: String?, previousTwo: String?) {
        if (!useLearning || !fieldAllowsLearning || word.isBlank()) return
        val suggestionLanguage = when (language) {
            KeyboardLanguage.ENGLISH -> SuggestionLanguage.ENGLISH
            KeyboardLanguage.NEPALI -> SuggestionLanguage.NEPALI
            KeyboardLanguage.ROMAN -> SuggestionLanguage.ROMAN
        }
        val model = when (language) {
            KeyboardLanguage.ENGLISH -> englishContext
            KeyboardLanguage.NEPALI -> nepaliContext
            KeyboardLanguage.ROMAN -> romanContext
        }
        val key = when (language) {
            KeyboardLanguage.ENGLISH -> KeyboardPreferences.KEY_CONTEXT_ENGLISH
            KeyboardLanguage.NEPALI -> KeyboardPreferences.KEY_CONTEXT_NEPALI
            KeyboardLanguage.ROMAN -> KeyboardPreferences.KEY_CONTEXT_ROMAN
        }
        if (!previous.isNullOrBlank() && PersonalDictionary.shouldLearnPhrase(previous, word)) {
            model.record(previous, word, previousTwo)
            suggestionEngine.recordPhrase(suggestionLanguage, previous, word, previousTwo)
            persistContextAndPhrases(model, key, suggestionLanguage)
        }
        when (language) {
            KeyboardLanguage.ENGLISH -> {
                recentEnglishWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_ENGLISH, recentEnglishWords)
            }
            KeyboardLanguage.NEPALI -> {
                recentNepaliWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_NEPALI, recentNepaliWords)
            }
            KeyboardLanguage.ROMAN -> {
                recentRomanWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_ROMAN, recentRomanWords)
            }
        }
    }

    private fun persistContextAndPhrases(
        model: ContextModel,
        key: String,
        suggestionLanguage: SuggestionLanguage
    ) {
        val phraseKey = when (suggestionLanguage) {
            SuggestionLanguage.ENGLISH -> KeyboardPreferences.KEY_PHRASE_ENGLISH
            SuggestionLanguage.NEPALI -> KeyboardPreferences.KEY_PHRASE_NEPALI
            SuggestionLanguage.ROMAN -> KeyboardPreferences.KEY_PHRASE_ROMAN
        }
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, model.serialize())
            .putString(phraseKey, suggestionEngine.serializePhrases(suggestionLanguage))
            .apply()
    }

    private fun editorContext(composingWord: String = ""): EditorContext =
        EditorContext.from(textBeforeCursor(InputConnectionPolicy.SUGGESTION_CONTEXT), composingWord)

    private fun textBeforeCursor(limit: Int): String =
        InputConnectionCommitter.textBeforeCursor(currentInputConnection, limit)

    private fun textAfterCursor(limit: Int): String =
        InputConnectionCommitter.textAfterCursor(currentInputConnection, limit)

    private fun adoptWordBeforeCursor() {
        val word = EditorContext.wordAtEnd(textBeforeCursor(64))
        if (word.isNotEmpty()) directTypingState.replaceWith(word)
    }

    private fun persistRecentWords(key: String, store: RecentWordStore) {
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, store.serialize())
            .apply()
    }

    private fun persistTouchAdaptation() {
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyboardPreferences.KEY_TOUCH_ADAPTATION, adaptiveHitboxes.serialize())
            .apply()
    }

    private fun showKeyPreview(anchor: View, key: KeySpec, compactScreen: Boolean) {
        if (!KeyInteractionPolicy.showsPreview(key)) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        previewView?.showAbove(
            anchor,
            overlayHost,
            KeyInteractionPolicy.previewLabel(key),
            KeyInteractionPolicy.previewTextSizeSp(key.label, compactScreen),
            KeyboardUiMetrics.previewWidthDp(resources.configuration.screenWidthDp, landscape),
            KeyboardUiMetrics.previewHeightDp(resources.configuration.screenWidthDp, landscape)
        )
    }

    private fun resolveSlideNeighbor(origin: View, originSpec: KeySpec, localX: Float, localY: Float): KeySpec? {
        val parent = origin.parent as? android.view.ViewGroup ?: return null
        val x = origin.left + localX
        val y = origin.top + localY
        val screenWidth = resources.configuration.screenWidthDp
        val squeezed = OneHandedLayoutPolicy.presentation(oneHanded, presentationMode) ==
            KeyboardPresentationMode.ONE_HANDED
        val candidates = ArrayList<TouchCandidate>(parent.childCount)
        val specs = ArrayList<KeySpec>(parent.childCount)
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val bound = boundTypingKeys.firstOrNull { it.view === child }
            val spec = bound?.spec ?: continue
            candidates += TouchCandidate(
                id = TouchRecognition.keyId(spec),
                rect = TouchRect(
                    child.left.toFloat(),
                    child.top.toFloat(),
                    child.right.toFloat(),
                    child.bottom.toFloat()
                ),
                edge = bound.edge,
                row = 0
            )
            specs += spec
        }
        val hitId = TouchGeometryPolicy.resolve(
            candidates = candidates,
            x = x,
            y = y,
            screenWidthDp = screenWidth,
            oneHanded = squeezed,
            adaptive = adaptiveHitboxes,
            preferredId = TouchRecognition.keyId(originSpec)
        ) ?: return null
        val index = candidates.indexOfFirst { it.id == hitId }
        if (index < 0) return null
        val resolved = specs[index]
        return if (resolved.action == originSpec.action || resolved.action == KeyAction.TEXT) {
            displayKey(resolved, ShiftPolicy.lettersUppercase(shiftState), currentInputEditorInfo?.imeOptions ?: 0)
        } else {
            null
        }
    }

    private fun persistLearnedWord(word: String) {
        if (!fieldAllowsLearning) return
        val (store, preferenceKey) = when (language) {
            KeyboardLanguage.ENGLISH -> learnedEnglishWords to KeyboardPreferences.KEY_LEARNED_ENGLISH
            KeyboardLanguage.NEPALI -> learnedNepaliWords to KeyboardPreferences.KEY_LEARNED_NEPALI
            KeyboardLanguage.ROMAN -> return
        }
        if (PersonalDictionary.shouldAccept(word) && store.record(word)) {
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(preferenceKey, store.serialize())
                .apply()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKeyButton(
        rawKey: KeySpec,
        colors: KeyboardPalette,
        edge: KeyEdge = KeyEdge.MIDDLE
    ): View {
        val compactScreen = resources.configuration.screenWidthDp < 360
        val horizontalGap = if (rawKey.compact) {
            dp(KeyboardTheme.COMPACT_HORIZONTAL_GAP_DP)
        } else {
            preferredKeyMargin()
        }
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val stored = baseSpec(rawKey)
        val key = displayKey(stored, ShiftPolicy.lettersUppercase(shiftState), imeOptions)
        return KeyboardKeyView(this).apply {
            bindTypingKey(this, key, colors, compactScreen, horizontalGap, edge)
            if (key.action == KeyAction.SETTINGS) {
                contentDescription = getString(R.string.settings_key_description)
            }
            if (key.action == KeyAction.SHIFT) {
                contentDescription = AccessibilityLabels.shift(ShiftPolicy.isCapsLock(shiftState))
            }
            if (key.action == KeyAction.ENTER) {
                contentDescription = AccessibilityLabels.enter(imeOptions)
            }
            if (key.action == KeyAction.SPACE) {
                contentDescription = AccessibilityLabels.space(language)
            }
            if (key.action == KeyAction.LANGUAGE) {
                contentDescription = AccessibilityLabels.languageControl(language)
            }
            if (layoutMode == LayoutMode.LETTERS || layoutMode == LayoutMode.VOWELS ||
                layoutMode == LayoutMode.NUMBERS || layoutMode == LayoutMode.SYMBOLS
            ) {
                boundTypingKeys += BoundTypingKey(this, stored, edge)
            }
            var cancelled = false
            var handledOnDown = false
            var feedbackGiven = false
            var downX = 0f
            var downY = 0f
            var startedOnEdge = false
            var slideTarget: KeySpec? = null
            fun liveKey(): KeySpec = displayKey(
                stored,
                ShiftPolicy.lettersUppercase(shiftState),
                currentInputEditorInfo?.imeOptions ?: 0
            )
            setOnClickListener {
                if (consumeChooserTap()) return@setOnClickListener
                if (handledOnDown || cancelled) return@setOnClickListener
                hideOverlays()
                val live = slideTarget ?: liveKey()
                if (!feedbackGiven) giveFeedback(live.action)
                handleKey(live)
            }
            setOnTouchListener { view, event ->
                if (!ImeTouchLifecycle.shouldDeliverEventAfterHide(inputViewActive)) {
                    previewView?.dismiss()
                    stopBackspaceRepeat()
                    touchState.reset()
                    return@setOnTouchListener false
                }
                val pointerId = event.getPointerId(event.actionIndex)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                            !touchState.tryAcquire(pointerId, TouchRecognition.keyId(stored), event.x, event.y, event.eventTime)
                        ) {
                            if (event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) cancelled = true
                            return@setOnTouchListener false
                        }
                        cancelled = false
                        handledOnDown = false
                        feedbackGiven = false
                        slideTarget = null
                        downX = event.x
                        downY = event.y
                        val ownerRect = TouchRect(0f, 0f, view.width.toFloat(), view.height.toFloat())
                        startedOnEdge = TouchGeometryPolicy.isEdgeHit(ownerRect, event.x, event.y)
                        val live = liveKey()
                        if (KeyInteractionPolicy.showsPreview(live)) {
                            showKeyPreview(view, live, compactScreen)
                        }
                        if (key.action != KeyAction.SETTINGS && key.action != KeyAction.TOOLBAR_MORE) {
                            giveFeedback(key.action)
                            feedbackGiven = true
                        }
                        if (KeyTouchPolicy.commitsOnDown(key.action)) {
                            hideOverlays()
                            handleKey(key)
                            handledOnDown = true
                            startBackspaceRepeat()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!touchState.isOwner(event.getPointerId(0))) return@setOnTouchListener false
                        touchState.trajectory.move(event.x, event.y, event.eventTime)
                        val stillOnKey = KeyTouchPolicy.staysOnKey(
                            downX, downY, event.x, event.y, view.width, view.height
                        )
                        if (stillOnKey) return@setOnTouchListener false
                        val gesture = KeyTouchPolicy.classify(touchState.trajectory, view.width, view.height)
                        val neighbor = resolveSlideNeighbor(view, stored, event.x, event.y)
                        if (TouchRecognition.shouldSlideCorrect(startedOnEdge, gesture, TouchRecognition.keyId(stored), neighbor?.let { TouchRecognition.keyId(it) })) {
                            slideTarget = neighbor
                            touchState.resolvedId = neighbor?.let { TouchRecognition.keyId(it) }
                            neighbor?.let { showKeyPreview(view, it, compactScreen) }
                            stopBackspaceRepeat()
                        } else {
                            cancelled = true
                            slideTarget = null
                            previewView?.dismiss()
                            stopBackspaceRepeat()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                        previewView?.dismiss()
                        stopBackspaceRepeat()
                        val owner = touchState.isOwner(pointerId)
                        if (event.actionMasked != MotionEvent.ACTION_UP) cancelled = true
                        if (owner && event.actionMasked == MotionEvent.ACTION_UP && slideTarget != null && !handledOnDown) {
                            val fromId = TouchRecognition.keyId(stored)
                            val toId = TouchRecognition.keyId(slideTarget!!)
                            if (adaptiveHitboxes.record(fromId, toId)) persistTouchAdaptation()
                            cancelled = true
                            hideOverlays()
                            handleKey(slideTarget!!)
                        }
                        if (owner) touchState.release(pointerId)
                    }
                }
                false
            }
            val alternates = KeyVisuals.alternates(key.label)
            when {
                KeyInteractionPolicy.allowsLongPressAlternates(key) -> {
                    setOnLongClickListener { view ->
                        val stillOnKey = !cancelled && slideTarget == null &&
                            !KeyTouchPolicy.shouldCancelLongPress(
                                downX,
                                downY,
                                touchState.trajectory.currentX,
                                touchState.trajectory.currentY,
                                view.width,
                                view.height
                            )
                        if (!TouchRecognition.shouldOpenAlternates(touchState.trajectory, stillOnKey)) {
                            return@setOnLongClickListener false
                        }
                        cancelled = true
                        previewView?.dismiss()
                        giveFeedback(KeyAction.TEXT, FeedbackKind.LONG_PRESS)
                        showOverlayShield()
                        alternateChooser?.showAbove(
                            view,
                            overlayHost,
                            alternates,
                            colors,
                            dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat()
                        )
                        true
                    }
                }
                KeyVisuals.isImeSwitchKey(key) -> {
                    setOnLongClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            switchToNextInputMethod(false)
                        } else {
                            // switchToNextInputMethod is API 28+. On API 23-27 fall back to the
                            // system keyboard picker so long-press still offers a keyboard switch.
                            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .showInputMethodPicker()
                        }
                        true
                    }
                }
            }
        }
    }

    private fun chromeLabel(
        label: String,
        description: String,
        colors: KeyboardPalette,
        selected: Boolean
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        includeFontPadding = false
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
        minimumHeight = dp(AccessibilityLabels.MIN_TOUCH_DP)
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (resources.configuration.screenWidthDp < 360) 12f else 13f)
        setTextColor(if (selected) KeyboardThemeTokens.selectedLabel(colors) else colors.text)
        background = KeyboardTheme.keyBackground(
            if (selected) KeyboardThemeTokens.toolbarSelected(colors) else KeyboardThemeTokens.toolbarFill(colors),
            dp(KeyboardUiMetrics.cornerRadiusDp(keyCorner)).toFloat(),
            colors.shadow,
            if (themeStyle.shadows) dp(KeyboardTheme.SHADOW_DP) else 0,
            if (themeStyle.borders) colors.divider else null,
            themeStyle.pressedHighlight
        )
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            val margin = preferredKeyMargin()
            setMargins(margin, dp(2), margin, dp(2))
        }
    }

    private fun isActiveKey(key: KeySpec): Boolean {
        val typingOrHandwriting = layoutMode == LayoutMode.LETTERS ||
            layoutMode == LayoutMode.VOWELS ||
            layoutMode == LayoutMode.HANDWRITING
        return when (key.action) {
            KeyAction.SHIFT -> ShiftPolicy.isActive(shiftState)
            KeyAction.MODE_ENGLISH -> typingOrHandwriting && language == KeyboardLanguage.ENGLISH
            KeyAction.MODE_NEPALI -> typingOrHandwriting && language == KeyboardLanguage.NEPALI
            KeyAction.MODE_ROMAN -> typingOrHandwriting && language == KeyboardLanguage.ROMAN
            KeyAction.NUMBERS ->
                (layoutMode == LayoutMode.NUMBERS && key.label == "123") ||
                    (layoutMode == LayoutMode.SYMBOLS && key.label == "#+=")
            KeyAction.DIGIT_SCRIPT -> layoutMode == LayoutMode.NUMBERS
            KeyAction.SYMBOL_GROUP -> layoutMode == LayoutMode.SYMBOLS
            KeyAction.EMOJI -> layoutMode == LayoutMode.EMOJI
            KeyAction.HANDWRITING -> layoutMode == LayoutMode.HANDWRITING
            KeyAction.CLIPBOARD -> layoutMode == LayoutMode.CLIPBOARD
            else -> false
        }
    }

    private fun handleKey(key: KeySpec) {
        if (ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(inputViewActive)) return
        when (key.action) {
            KeyAction.TEXT -> {
                if (!keyBounce.allow("text:${key.output}", SystemClock.uptimeMillis())) return
                if (layoutMode == LayoutMode.EMOJI && emojiCategory == EmojiCategory.SEARCH &&
                    key.output.all(Char::isLetter)
                ) {
                    emojiQuery += key.output.lowercase()
                    renderKeyboard()
                } else if (language == KeyboardLanguage.ROMAN && layoutMode == LayoutMode.LETTERS) {
                    handleRomanText(key.output)
                } else {
                    insertPanelOrDirect(key.output)
                }
            }
            KeyAction.SPACE -> {
                val now = SystemClock.uptimeMillis()
                val before = textBeforeCursor(InputConnectionPolicy.SUGGESTION_CONTEXT)
                if (SpacePolicy.shouldApplyDoubleSpace(
                        before,
                        now - lastSpaceUptime,
                        useDoubleSpacePeriod && EditorFieldPolicy.allowsDoubleSpacePeriod(currentInputType)
                    )
                ) {
                    if (language == KeyboardLanguage.ROMAN) applyRomanEdit(finishRomanWord())
                    else {
                        rememberFinishedDirectWord(directTypingState.currentWord)
                        directTypingState.clear()
                    }
                    InputConnectionCommitter.deleteSurrounding(currentInputConnection, 1, 0)
                    commitText(SpacePolicy.replacementForDoubleSpace(language))
                } else if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(finishRomanWord(" "))
                } else {
                    rememberFinishedDirectWord(directTypingState.currentWord)
                    directTypingState.clear()
                    commitText(" ")
                }
                lastSpaceUptime = now
                updateSuggestionRow()
            }
            KeyAction.BACKSPACE -> {
                if (layoutMode == LayoutMode.EMOJI && emojiCategory == EmojiCategory.SEARCH && emojiQuery.isNotEmpty()) {
                    emojiQuery = emojiQuery.dropLast(1)
                    renderKeyboard()
                } else if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(romanComposer.backspace())
                    updateSuggestionRow()
                } else {
                    internalSelectionChange = true
                    val hadWord = directTypingState.currentWord.isNotEmpty()
                    directTypingState.backspace()
                    deleteOneCharacter()
                    if (!hadWord || directTypingState.currentWord.isEmpty()) {
                        adoptWordBeforeCursor()
                    }
                    updateSuggestionRow()
                }
            }
            KeyAction.ENTER -> {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(finishRomanWord())
                    updateSuggestionRow()
                } else {
                    rememberFinishedDirectWord(directTypingState.currentWord)
                    directTypingState.clear()
                    updateSuggestionRow()
                }
                sendEnter()
            }
            KeyAction.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val previous = shiftState
                shiftState = ShiftPolicy.tap(shiftState, now, lastShiftTapAt, language)
                lastShiftTapAt = now
                if (ShiftPolicy.refreshLabelsOnly(previous, shiftState)) {
                    refreshTypingKeys()
                }
            }
            KeyAction.NUMBERS -> openPanel(LayoutMode.NUMBERS)
            KeyAction.SYMBOLS -> openPanel(LayoutMode.SYMBOLS)
            KeyAction.SYMBOL_GROUP -> {
                symbolGroup = symbolGroup.next()
                renderKeyboard()
            }
            KeyAction.DIGIT_SCRIPT -> {
                digitScript = digitScript.toggle()
                renderKeyboard()
            }
            KeyAction.EMOJI -> openPanel(LayoutMode.EMOJI)
            KeyAction.RETURN_TO_PREVIOUS -> returnToPreviousLayout()
            KeyAction.LETTERS -> returnToPreviousLayout()
            KeyAction.LANGUAGE -> {
                if (!keyBounce.allow("language", SystemClock.uptimeMillis())) return
                switchTypingMode(language.next())
            }
            KeyAction.VOWELS -> {
                layoutMode = LayoutMode.VOWELS
                renderKeyboard()
            }
            KeyAction.CONSONANTS -> {
                layoutMode = LayoutMode.LETTERS
                renderKeyboard()
            }
            KeyAction.HANDWRITING -> openHandwriting()
            KeyAction.HANDWRITING_UNDO -> {
                handwritingJobs.cancel()
                handwritingSession.undo()
                if (handwritingState.undo()) handwritingCanvas?.undoStroke()
                updateHandwritingResultRow()
            }
            KeyAction.HANDWRITING_CLEAR -> {
                handwritingJobs.cancel()
                handwritingSession.clear()
                handwritingState.clear()
                handwritingCanvas?.clearInk()
                updateHandwritingResultRow()
            }
            KeyAction.HANDWRITING_CONFIRM -> {
                if (handwritingState.status == HandwritingStatus.RESULTS) {
                    insertHandwritingCandidate(0, keyboardColors())
                } else {
                    requestHandwritingRecognition()
                    updateHandwritingResultRow()
                }
            }
            KeyAction.HANDWRITING_CANCEL -> returnToPreviousLayout()
            KeyAction.MODE_ENGLISH -> switchTypingMode(KeyboardLanguage.ENGLISH)
            KeyAction.MODE_NEPALI -> switchTypingMode(KeyboardLanguage.NEPALI)
            KeyAction.MODE_ROMAN -> switchTypingMode(KeyboardLanguage.ROMAN)
            KeyAction.SETTINGS -> openSettings()
            KeyAction.CLIPBOARD -> openClipboard()
            KeyAction.TOOLBAR_MORE -> {
                toolbar.expand()
                renderKeyboard()
            }
            KeyAction.TOOLBAR_COLLAPSE -> {
                toolbar.collapse()
                renderKeyboard()
            }
        }
    }

    private fun insertAlternate(text: String) {
        hideOverlays()
        languagePicker.select(text)?.let { selected ->
            switchTypingMode(selected)
            return
        }
        LanguageSwitcher.fromPickerLabel(text)?.let { selected ->
            switchTypingMode(selected)
            return
        }
        giveFeedback(KeyAction.TEXT)
        if (layoutMode == LayoutMode.EMOJI || EmojiCatalog.contains(text)) {
            insertEmoji(text)
        } else {
            handleKey(KeySpec(label = text, output = text))
        }
    }

    private fun openSettings() {
        requestHideSelf(0)
        startActivity(
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun hideOverlays() {
        previewView?.dismiss()
        alternateChooser?.dismiss()
        languagePicker.dismiss()
        overlayDismissView?.let { shield ->
            if (::overlayHost.isInitialized) overlayHost.removeView(shield)
        }
        overlayDismissView = null
    }

    private fun isOverlayOpen(): Boolean =
        languagePicker.isOpen ||
            alternateChooser?.isShowing() == true ||
            previewView?.visibility == View.VISIBLE

    private fun isChooserOpen(): Boolean =
        languagePicker.isOpen || alternateChooser?.isShowing() == true

    private fun consumeChooserTap(): Boolean {
        if (!isChooserOpen()) return false
        hideOverlays()
        return true
    }

    private fun showOverlayShield() {
        if (overlayDismissView != null || !::overlayHost.isInitialized) return
        val shield = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { hideOverlays() }
        }
        overlayHost.addView(shield, 1)
        overlayDismissView = shield
    }

    private fun applyPresentationPadding(colors: KeyboardPalette) {
        val screenWidth = resources.configuration.screenWidthDp
        val alignment = if (
            OneHandedLayoutPolicy.presentation(oneHanded, presentationMode) ==
            KeyboardPresentationMode.ONE_HANDED
        ) {
            oneHanded
        } else {
            OneHandedAlignment.OFF
        }
        val insets = OneHandedLayoutPolicy.insets(screenWidth, alignment)
        if (::overlayHost.isInitialized) {
            overlayHost.setBackgroundColor(KeyboardThemeTokens.gutter(colors))
        }
        keyboardRoot.setBackgroundColor(KeyboardThemeTokens.gutter(colors))
        keyboardRoot.setPadding(
            dp(KeyboardUiMetrics.ROOT_HORIZONTAL_PADDING_DP + insets.startDp),
            dp(KeyboardUiMetrics.ROOT_VERTICAL_PADDING_DP / 2),
            dp(KeyboardUiMetrics.ROOT_HORIZONTAL_PADDING_DP + insets.endDp),
            dp(KeyboardUiMetrics.ROOT_VERTICAL_PADDING_DP / 2)
        )
    }

    private fun applyLanguageButton(config: ToolbarConfiguration): ToolbarConfiguration {
        val enabled = config.enabled.toMutableSet()
        if (useLanguageButton) enabled += ToolbarAction.LANGUAGE else enabled -= ToolbarAction.LANGUAGE
        if (enabled.isEmpty()) enabled += ToolbarAction.MORE
        return config.copy(enabled = enabled)
    }

    private fun showLanguagePicker(anchor: View) {
        val colors = keyboardColors()
        previewView?.dismiss()
        languagePicker.open(language)
        showOverlayShield()
        alternateChooser?.showAbove(
            anchor = anchor,
            host = overlayHost,
            options = LanguageSwitcher.pickerLabels(),
            palette = colors,
            radiusPx = dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat(),
            selected = LanguageSwitcher.pickerLabel(language)
        )
    }

    private fun suggestionRowHeightDp(): Int = KeyboardUiMetrics.suggestionHeightDp(
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        keyboardHeight
    )

    private fun navigationRowHeightDp(): Int = KeyboardUiMetrics.navigationHeightDp(
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        keyboardHeight
    )

    private fun clipboardPanelHeight(): Int = KeyboardUiMetrics.clipboardPanelHeightDp(
        resources.configuration.screenHeightDp,
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        keyboardHeight
    )

    private fun openPanel(target: LayoutMode) {
        if (layoutMode == target) return
        if (PanelTransitionPolicy.shouldFinishComposing(openingPanel = true)) {
            finishComposingForTransition()
        }
        directTypingState.clear()
        internalSelectionChange = false
        if (layoutMode == LayoutMode.HANDWRITING) {
            resetHandwriting()
            modeHistory.clear()
            layoutMode = LayoutMode.LETTERS
        }
        val numberOrSymbolSwitch =
            (layoutMode == LayoutMode.NUMBERS || layoutMode == LayoutMode.SYMBOLS) &&
                (target == LayoutMode.NUMBERS || target == LayoutMode.SYMBOLS)
        if (!numberOrSymbolSwitch && layoutMode != target) {
            modeHistory.remember(ModeSnapshot(language, layoutMode))
        }
        resetShift()
        if (target == LayoutMode.NUMBERS && layoutMode != LayoutMode.NUMBERS && layoutMode != LayoutMode.SYMBOLS) {
            digitScript = DigitScript.defaultFor(language)
        }
        if (target == LayoutMode.SYMBOLS && layoutMode != LayoutMode.SYMBOLS && layoutMode != LayoutMode.NUMBERS) {
            symbolGroup = SymbolGroup.COMMON
        }
        toolbar.collapse()
        layoutMode = target
        if (target == LayoutMode.EMOJI) {
            ensureEmojiDataset()
            emojiCategory = EmojiCategory.RECENT
            emojiQuery = ""
        }
        renderKeyboard()
    }

    private fun openHandwriting() {
        if (layoutMode == LayoutMode.HANDWRITING) return
        if (PanelTransitionPolicy.shouldFinishComposing(openingPanel = true)) {
            finishComposingForTransition()
        }
        directTypingState.clear()
        internalSelectionChange = false
        if (layoutMode != LayoutMode.HANDWRITING) {
            modeHistory.remember(ModeSnapshot(language, layoutMode))
        }
        handwritingJobs.cancel()
        handwritingState.clear()
        resetShift()
        layoutMode = LayoutMode.HANDWRITING
        renderKeyboard()
    }

    private fun returnToPreviousLayout() {
        if (layoutMode == LayoutMode.HANDWRITING) resetHandwriting()
        directTypingState.clear()
        internalSelectionChange = false
        val previous = modeHistory.previousOr(ModeSnapshot(language, LayoutMode.LETTERS))
        language = previous.language
        layoutMode = previous.layout
        resetShift()
        renderKeyboard()
    }

    private fun switchTypingMode(targetLanguage: KeyboardLanguage) {
        if (InputConnectionPolicy.shouldFinishComposingOnLanguageSwitch()) {
            finishComposingForTransition()
        }
        resetHandwriting()
        directTypingState.clear()
        internalSelectionChange = false
        modeHistory.clear()
        language = targetLanguage
        if (LanguageSwitchPolicy.resetShiftOnSwitch()) resetShift()
        layoutMode = LayoutMode.LETTERS
        lastSuggestionWords = emptyList()
        renderKeyboard()
    }

    private fun finishComposingForTransition() {
        typingGeneration.bump()
        stopBackspaceRepeat()
        if (language == KeyboardLanguage.ROMAN) {
            applyRomanEdit(finishRomanWord())
        } else {
            rememberFinishedDirectWord(directTypingState.currentWord)
            InputConnectionCommitter.finishComposing(currentInputConnection)
            directTypingState.clear()
        }
        lastSuggestionWords = emptyList()
    }

    private fun insertPanelOrDirect(text: String) {
        if (language == KeyboardLanguage.ROMAN) {
            applyRomanEdit(finishRomanWord(text))
        } else {
            handleDirectText(text)
        }
        if ((layoutMode == LayoutMode.NUMBERS || layoutMode == LayoutMode.SYMBOLS) &&
            recentSymbols.record(text)
        ) {
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KeyboardPreferences.KEY_RECENT_SYMBOLS, recentSymbols.serialize())
                .apply()
        }
    }

    private fun handleDirectText(text: String) {
        val typingLanguage = when (language) {
            KeyboardLanguage.ENGLISH -> DirectTypingLanguage.ENGLISH
            KeyboardLanguage.NEPALI -> DirectTypingLanguage.NEPALI
            KeyboardLanguage.ROMAN -> return
        }
        val before = textBeforeCursor(InputConnectionPolicy.SUGGESTION_CONTEXT)
        var incoming = text
        if (ShiftPolicy.allowsAutoCapitalization(shiftState, language) && useAutoCapitalization) {
            incoming = CapitalizationPolicy.applyIncomingLetter(incoming, before, useAutoCapitalization)
        }
        val spacing = PunctuationSpacing.plan(
            before,
            incoming,
            useSmartPunctuation && EditorFieldPolicy.allowsSmartPunctuation(currentInputType)
        )
        applySpacingPlan(spacing)
        val pending = directTypingState.currentWord
        val isWordText = directTypingState.append(spacing.text, typingLanguage)
        if (!isWordText && pending.isNotEmpty()) rememberFinishedDirectWord(pending)
        if (isWordText || directTypingState.currentWord.isEmpty()) updateSuggestionRow()
    }

    private fun handleRomanText(text: String) {
        val edit = if (text.all(Char::isLetter)) {
            romanComposer.type(text)
        } else {
            val finished = finishRomanWord()
            applyRomanEdit(finished)
            val spacing = SpacePolicy.planPunctuation(
                textBeforeCursor(InputConnectionPolicy.SUGGESTION_CONTEXT),
                text,
                useSmartPunctuation && EditorFieldPolicy.allowsSmartPunctuation(currentInputType)
            )
            applySpacingPlan(spacing)
            return
        }
        applyRomanEdit(edit)
        if (ShiftPolicy.consumesOneShot(text) && consumeOneShotShift()) {
            refreshTypingKeys()
        } else {
            updateSuggestionRow()
        }
    }

    private fun applyRomanEdit(edit: RomanEdit) {
        val connection = currentInputConnection ?: return
        when (edit) {
            is RomanEdit.SetComposing -> InputConnectionCommitter.setComposing(connection, edit.text)
            is RomanEdit.Commit -> InputConnectionCommitter.commitRaw(connection, edit.text)
            RomanEdit.ClearComposing -> {
                InputConnectionCommitter.setComposing(connection, "")
                InputConnectionCommitter.finishComposing(connection)
            }
            RomanEdit.DeletePrevious -> deleteOneCharacter()
            RomanEdit.NoOp -> Unit
        }
    }

    private fun commitText(text: String) {
        if (language != KeyboardLanguage.ROMAN &&
            (layoutMode == LayoutMode.LETTERS || layoutMode == LayoutMode.VOWELS)
        ) {
            internalSelectionChange = true
        }
        InputConnectionCommitter.commit(currentInputConnection, text)
        if (language == KeyboardLanguage.ENGLISH && ShiftPolicy.consumesOneShot(text) && consumeOneShotShift()) {
            refreshTypingKeys()
        }
    }

    private fun applySpacingPlan(spacing: SpacingPlan) {
        if (spacing.deleteBefore > 0) {
            InputConnectionCommitter.deleteSurrounding(currentInputConnection, spacing.deleteBefore, 0)
        }
        if (spacing.insertLeadingSpace) commitText(" ")
        commitText(spacing.text)
        if (spacing.insertTrailingSpace) commitText(" ")
    }

    private fun deleteOneCharacter() {
        val connection = currentInputConnection ?: return
        val selection = InputConnectionCommitter.selectedText(connection)
        if (!selection.isNullOrEmpty()) {
            InputConnectionCommitter.commitRaw(connection, "")
            return
        }
        val units = GraphemeBackspace.codeUnitsToDelete(textBeforeCursor(InputConnectionPolicy.BACKSPACE_CONTEXT))
        if (units > 0) InputConnectionCommitter.deleteSurrounding(connection, units, 0)
    }

    private fun sendEnter() {
        val connection = currentInputConnection ?: return
        val options = EnterActionPolicy.resolve(currentInputEditorInfo?.imeOptions)
        if (EnterActionPolicy.shouldPerformAction(options)) {
            InputConnectionCommitter.performEditorAction(connection, EnterActionPolicy.actionId(options))
        } else {
            InputConnectionCommitter.commitRaw(connection, "\n")
        }
    }

    private fun startBackspaceRepeat() {
        if (ImeLifecyclePolicy.shouldIgnoreInputWhenHidden(inputViewActive)) return
        stopBackspaceRepeat()
        val generation = typingGeneration.current()
        val task = object : Runnable {
            private var repeats = 0
            override fun run() {
                if (!typingGeneration.isCurrent(generation)) return
                handleKey(KeySpec("⌫", KeyAction.BACKSPACE))
                repeats = BackspaceRepeatPolicy.nextRepeatCount(repeats)
                mainHandler.postDelayed(this, BackspaceRepeatPolicy.intervalMs(repeats))
            }
        }
        backspaceRepeat = task
        mainHandler.postDelayed(task, BackspaceRepeatPolicy.INITIAL_DELAY_MS)
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeat?.let(mainHandler::removeCallbacks)
        backspaceRepeat = null
    }

    private fun displayKey(key: KeySpec, uppercase: Boolean, imeOptions: Int): KeySpec = when (key.action) {
        KeyAction.SHIFT -> key.copy(label = ShiftPolicy.label(shiftState))
        KeyAction.ENTER -> key.copy(label = EnterActionPolicy.label(imeOptions))
        KeyAction.SPACE -> key.copy(label = language.spaceLabel(), output = " ")
        KeyAction.TEXT -> {
            if (key.output.length == 1 && key.output[0].isLetter() && key.output[0].code < 128) {
                val text = if (uppercase) key.output.uppercase() else key.output.lowercase()
                key.copy(label = text, output = text)
            } else {
                key
            }
        }
        else -> key
    }

    private fun baseSpec(key: KeySpec): KeySpec {
        if (key.action == KeyAction.TEXT && key.output.length == 1 && key.output[0].isLetter() && key.output[0].code < 128) {
            val lower = key.output.lowercase()
            return key.copy(label = lower, output = lower)
        }
        if (key.action == KeyAction.SHIFT) return key.copy(label = "⇧")
        return key
    }

    private fun bindTypingKey(
        view: KeyboardKeyView,
        key: KeySpec,
        colors: KeyboardPalette,
        compactScreen: Boolean,
        horizontalGap: Int,
        edge: KeyEdge
    ) {
        view.bind(
            key = key,
            palette = colors,
            active = isActiveKey(key),
            compactScreen = compactScreen,
            horizontalGapPx = horizontalGap,
            verticalGapPx = dp(KeyboardTheme.KEY_VERTICAL_GAP_DP / 2),
            radiusPx = dp(KeyboardUiMetrics.cornerRadiusDp(keyCorner)).toFloat(),
            shadowPx = if (themeStyle.shadows) dp(KeyboardTheme.SHADOW_DP) else 0,
            borderColor = if (themeStyle.borders) colors.divider else null,
            pressedEnabled = themeStyle.pressedHighlight,
            edge = edge,
            fontScale = resources.configuration.fontScale
        )
    }

    private fun refreshTypingKeys() {
        if (boundTypingKeys.isEmpty() ||
            (layoutMode != LayoutMode.LETTERS && layoutMode != LayoutMode.VOWELS)
        ) {
            renderKeyboard()
            return
        }
        val colors = keyboardColors()
        val compactScreen = resources.configuration.screenWidthDp < 360
        val uppercase = ShiftPolicy.lettersUppercase(shiftState)
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        boundTypingKeys.forEach { bound ->
            val horizontalGap = if (bound.spec.compact) {
                dp(KeyboardTheme.COMPACT_HORIZONTAL_GAP_DP)
            } else {
                preferredKeyMargin()
            }
            val key = displayKey(bound.spec, uppercase, imeOptions)
            bindTypingKey(bound.view, key, colors, compactScreen, horizontalGap, bound.edge)
            when (key.action) {
                KeyAction.SHIFT -> bound.view.contentDescription =
                    AccessibilityLabels.shift(ShiftPolicy.isCapsLock(shiftState))
                KeyAction.ENTER -> bound.view.contentDescription = AccessibilityLabels.enter(imeOptions)
                KeyAction.SPACE -> bound.view.contentDescription = AccessibilityLabels.space(language)
                KeyAction.LANGUAGE -> bound.view.contentDescription = AccessibilityLabels.languageControl(language)
                else -> Unit
            }
        }
    }

    private data class BoundTypingKey(
        val view: KeyboardKeyView,
        val spec: KeySpec,
        val edge: KeyEdge
    )

    private fun giveFeedback(action: KeyAction, kind: FeedbackKind = FeedbackKind.KEY) {
        if (TouchFeedbackPolicy.shouldPlaySound(useSound, kind)) {
            val sound = when (TouchFeedbackPolicy.soundFor(action)) {
                KeyClickSound.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyClickSound.RETURN -> AudioManager.FX_KEYPRESS_RETURN
                KeyClickSound.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyClickSound.NONE -> null
                KeyClickSound.STANDARD -> AudioManager.FX_KEYPRESS_STANDARD
            }
            if (sound != null) {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(sound, TouchFeedbackPolicy.soundVolume(soundVolume))
            }
        }
        val duration = TouchFeedbackPolicy.vibrationDurationMs(kind, hapticStrength)
        if (TouchFeedbackPolicy.shouldVibrate(useVibration, kind) && duration > 0) {
            vibrator()?.let { deviceVibrator ->
                if (!deviceVibrator.hasVibrator()) return@let
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    deviceVibrator.vibrate(
                        VibrationEffect.createOneShot(
                            duration,
                            TouchFeedbackPolicy.vibrationAmplitude(hapticStrength)
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    deviceVibrator.vibrate(duration)
                }
            }
        }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun applyWindowAppearance() {
        val window = window.window ?: return
        val colors = keyboardColors()
        setNavigationBarColor(window, colors.background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    if (useDarkAppearance) {
                        0
                    } else {
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    },
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applyLegacyLightNavigationBars(window)
        }
    }

    /** API 26-29 fallback: systemUiVisibility is deprecated from API 30 but is the only
     *  pre-30 mechanism for light navigation-bar icons. */
    @Suppress("DEPRECATION")
    private fun applyLegacyLightNavigationBars(window: Window) {
        window.decorView.systemUiVisibility = if (useDarkAppearance) {
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        } else {
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    /** Navigation bar background color. Deprecated in API 35 but still the supported way
     *  to color the bar on API 23-34 devices, so it stays behind a single suppression. */
    @Suppress("DEPRECATION")
    private fun setNavigationBarColor(window: Window, color: Int) {
        window.navigationBarColor = color
    }


    private fun resetShift() {
        shiftState = ShiftLockState.OFF
        lastShiftTapAt = 0L
    }

    private fun consumeOneShotShift(): Boolean {
        val previous = shiftState
        shiftState = ShiftPolicy.afterLetter(shiftState)
        return previous == ShiftLockState.ONE_SHOT && shiftState == ShiftLockState.OFF
    }

    private fun preferredKeyHeight(): Int {
        val configuration = resources.configuration
        return KeyboardUiMetrics.keyHeightDp(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            keyboardHeight,
            keyDensity
        )
    }

    private fun systemUsesDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun keyboardColors(): KeyboardPalette =
        cachedPalette ?: AppearanceCatalog.resolve(
            visualTheme,
            if (useDarkAppearance) KeyboardAppearance.DARK else KeyboardAppearance.LIGHT,
            useDarkAppearance,
            colorPreset
        ).also { cachedPalette = it }

    private fun preferredKeyMargin(): Int {
        return dp(KeyboardUiMetrics.keyGapDp(resources.configuration.screenWidthDp, keySpacing))
    }

    private fun color(resource: Int): Int = resources.getColor(resource, theme)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class ModeSnapshot(
        val language: KeyboardLanguage,
        val layout: LayoutMode
    )

    private companion object {
        const val EMOJI_VISIBLE_ROWS = KeyboardUiMetrics.EMOJI_VISIBLE_ROWS
        const val MAX_SUGGESTIONS = 3
    }
}
