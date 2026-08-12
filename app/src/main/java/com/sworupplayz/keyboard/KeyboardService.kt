package com.sworupplayz.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
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
    private var language = KeyboardLanguage.ENGLISH
    private var layoutMode = LayoutMode.LETTERS
    private var shifted = false
    private var useSound = false
    private var useVibration = false
    private var useDarkAppearance = false
    private var useSuggestions = true
    private var useLearning = true
    private var showNumberRow = false
    private var keyboardHeight = KeyboardHeight.NORMAL
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        readPreferences()
        keyHeightDp = preferredKeyHeight()
        if (!restarting) {
            defaultMode?.let { language = it.toKeyboardLanguage() }
            layoutMode = LayoutMode.LETTERS
            lastCommittedWord = ""
        }
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            romanComposer.reset()
        }
        directTypingState.clear()
        internalSelectionChange = false
        resetHandwriting()
        modeHistory.clear()
        shifted = false
        if (::keyboardRoot.isInitialized) {
            applyWindowAppearance()
            renderKeyboard()
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            romanComposer.reset()
        }
        directTypingState.clear()
        internalSelectionChange = false
        resetHandwriting()
        modeHistory.clear()
        updateLanguageFromSubtype(newSubtype)
        shifted = false
        layoutMode = LayoutMode.LETTERS
        if (::keyboardRoot.isInitialized) renderKeyboard()
    }

    override fun onFinishInput() {
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            romanComposer.reset()
        }
        directTypingState.clear()
        internalSelectionChange = false
        resetHandwriting()
        modeHistory.clear()
        suggestionRow = null
        hideOverlays()
        super.onFinishInput()
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
        if (romanComposerDelegate.isInitialized() &&
            RomanSelectionState.movedAwayFromComposition(
                romanComposer.currentWord,
                newSelStart,
                newSelEnd,
                candidatesEnd
            )
        ) {
            romanComposer.reset()
            currentInputConnection?.finishComposingText()
            updateSuggestionRow()
        }
        if (language != KeyboardLanguage.ROMAN && directTypingState.currentWord.isNotEmpty()) {
            if (internalSelectionChange) {
                internalSelectionChange = false
            } else {
                directTypingState.clear()
                updateSuggestionRow()
            }
        } else if (language != KeyboardLanguage.ROMAN) {
            internalSelectionChange = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readPreferences()
        keyHeightDp = preferredKeyHeight()
        if (::keyboardRoot.isInitialized) {
            applyWindowAppearance()
            renderKeyboard()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

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
        showNumberRow = settings.numberRow
        keyboardHeight = settings.height
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
        emojiUsage = EmojiUsageStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_EMOJI_USAGE, null)
        )
        recentSymbols = RecentSymbolStore.fromSerialized(
            preferences.getString(KeyboardPreferences.KEY_RECENT_SYMBOLS, null)
        )
    }

    private fun ensureEmojiDataset() {
        emojiDataset.value
    }

    private fun renderKeyboard() {
        hideOverlays()
        val colors = keyboardColors()
        if (::overlayHost.isInitialized) overlayHost.setBackgroundColor(colors.background)
        keyboardRoot.setBackgroundColor(colors.background)
        keyboardRoot.removeAllViews()
        suggestionRow = null
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

        val supportsSuggestions = layoutMode == LayoutMode.LETTERS ||
            (language == KeyboardLanguage.NEPALI && layoutMode == LayoutMode.VOWELS)
        val useCompactNepaliSuggestions = useSuggestions && supportsSuggestions &&
            language == KeyboardLanguage.NEPALI
        if (useSuggestions && supportsSuggestions) {
            addSuggestionRow(colors, includeSettings = useCompactNepaliSuggestions)
        }
        if (!useCompactNepaliSuggestions) addNavigationRow(colors)

        val rows = when {
            layoutMode == LayoutMode.NUMBERS -> KeyboardLayouts.numbers(language, digitScript)
            layoutMode == LayoutMode.SYMBOLS -> KeyboardLayouts.symbols(language, symbolGroup, recentSymbols.values())
            language == KeyboardLanguage.ENGLISH || language == KeyboardLanguage.ROMAN ->
                KeyboardLayouts.english(shifted, language, showNumberRow)
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
                KeyboardUiMetrics.compactNumberRowHeightDp(keyboardHeight)
            } else {
                keyHeightDp
            }
            keyboardRoot.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(rowHeight))
            )
            keys.forEach { key -> row.addView(createKeyButton(key, colors)) }
        }
    }

    private fun addNavigationRow(colors: KeyboardPalette) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KeyboardUiMetrics.NAVIGATION_HEIGHT_DP))
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
                setTextColor(if (selected) Color.WHITE else colors.text)
                background = KeyboardTheme.keyBackground(
                    if (selected) colors.accent else colors.specialKey,
                    dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat(),
                    colors.shadow,
                    dp(KeyboardTheme.SHADOW_DP)
                )
                layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    val margin = preferredKeyMargin()
                    setMargins(margin, dp(2), margin, dp(2))
                }
                setOnClickListener {
                    giveFeedback(KeyAction.EMOJI)
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
            emojis.chunked(EMOJIS_PER_ROW).forEach { emojiRow ->
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
                repeat(EMOJIS_PER_ROW - emojiRow.size) {
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
                    giveFeedback(KeyAction.EMOJI)
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
                radiusPx = dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat(),
                shadowPx = dp(KeyboardTheme.SHADOW_DP)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setOnClickListener {
                giveFeedback(KeyAction.EMOJI)
                insertEmoji(emoji)
            }
            val variants = EmojiCatalog.variants(emoji)
            if (variants.isNotEmpty()) {
                setOnLongClickListener { view ->
                    previewView?.dismiss()
                    giveFeedback(KeyAction.EMOJI)
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
            currentInputConnection?.finishComposingText()
            directTypingState.clear()
        }
        val connection = currentInputConnection ?: return
        if (!InputConnectionCommitter.commit(connection, emoji)) return
        recentEmojis.record(emoji)
        emojiUsage.record(emoji)
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyboardPreferences.KEY_RECENT_EMOJIS, recentEmojis.serialize())
            .putString(KeyboardPreferences.KEY_EMOJI_USAGE, emojiUsage.serialize())
            .apply()
        updateSuggestionRow()
    }

    private fun renderHandwriting(colors: KeyboardPalette) {
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
            contentDescription = getString(R.string.handwriting_canvas_description)
            setInkColor(colors.text)
            background = KeyboardTheme.roundedRect(
                colors.key,
                dp(KeyboardTheme.PREVIEW_CORNER_RADIUS_DP).toFloat()
            )
            onStrokeFinished = { points ->
                handwritingState.addStroke(points)
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
                row.addView(chromeLabel(candidate, candidate, colors, selected = false).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
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
            HandwritingStatus.NO_MATCH -> R.string.handwriting_no_match
            HandwritingStatus.RECOGNIZER_UNAVAILABLE -> R.string.handwriting_unavailable
            HandwritingStatus.RESULTS -> R.string.handwriting_no_match
        }
        row.addView(TextView(this).apply {
            text = getString(message)
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(colors.secondaryText)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun insertHandwritingCandidate(index: Int, colors: KeyboardPalette) {
        val candidate = handwritingState.confirm(index) ?: return
        InputConnectionCommitter.commit(currentInputConnection, candidate)
        handwritingCanvas?.clearInk()
        updateHandwritingResultRow(colors)
    }

    private fun resetHandwriting() {
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
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KeyboardUiMetrics.SUGGESTION_HEIGHT_DP))
        )
        updateSuggestionRow(colors)
    }

    private fun updateSuggestionRow(colors: KeyboardPalette = keyboardColors()) {
        val row = suggestionRow ?: return
        row.removeAllViews()
        val currentWord = if (language == KeyboardLanguage.ROMAN) {
            romanComposer.currentWord
        } else {
            directTypingState.currentWord
        }
        val context = editorContext(currentWord)
        val rawSuggestions = if (currentWord.isEmpty()) emptyList() else when (language) {
            KeyboardLanguage.ENGLISH -> englishSuggester.suggestions(
                currentWord,
                if (useLearning) learnedEnglishWords.suggestions(currentWord) else emptyList(),
                MAX_SUGGESTIONS,
                recentEnglishWords.matches(currentWord),
                englishContext.predictions(context.previousWord.orEmpty(), currentWord)
            )
            KeyboardLanguage.NEPALI -> nepaliSuggester.suggestions(
                currentWord,
                if (useLearning) learnedNepaliWords.suggestions(currentWord) else emptyList(),
                MAX_SUGGESTIONS,
                recentNepaliWords.matches(currentWord),
                nepaliContext.predictions(context.previousWord.orEmpty(), currentWord)
            )
            KeyboardLanguage.ROMAN -> romanConverter.suggestions(
                currentWord,
                if (useLearning) learnedRomanWords.lookup(currentWord) else null,
                MAX_SUGGESTIONS,
                recent = recentRomanWords.matches(currentWord),
                previousWord = context.previousWord ?: lastCommittedWord.ifEmpty { null },
                contextPredictions = romanContext.predictions(
                    (context.previousWord ?: lastCommittedWord).orEmpty(),
                    currentWord
                )
            )
        }
        val suggestions = if (language == KeyboardLanguage.ENGLISH) {
            rawSuggestions.map { CapitalizationPolicy.applyToWord(it, context.textBeforeCursor) }
        } else {
            rawSuggestions
        }
        if (suggestions.isEmpty()) {
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

        suggestions.take(MAX_SUGGESTIONS).forEachIndexed { index, suggestion ->
            if (index > 0) row.addView(suggestionDivider(colors))
            row.addView(TextView(this).apply {
                text = suggestion
                contentDescription = suggestion
                isClickable = true
                isFocusable = true
                isAllCaps = false
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (suggestion.length > 10) 13f else KeyboardTheme.SUGGESTION_TEXT_SP
                )
                setTextColor(if (index == 0) colors.text else colors.secondaryText)
                background = null
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
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
        setBackgroundColor(colors.divider)
        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    private fun acceptSuggestion(suggestion: String, colors: KeyboardPalette) {
        if (language == KeyboardLanguage.ROMAN) {
            val romanWord = romanComposer.currentWord
            if (useLearning && learnedRomanWords.learn(romanWord, suggestion)) {
                getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KeyboardPreferences.KEY_LEARNED_ROMAN, learnedRomanWords.serialize())
                    .apply()
            }
            applyRomanEdit(romanComposer.acceptSuggestion(suggestion))
            recentRomanWords.record(romanWord)
            persistRecentWords(KeyboardPreferences.KEY_RECENT_ROMAN, recentRomanWords)
            updateSuggestionRow(colors)
            return
        }

        val typedWord = directTypingState.currentWord
        val connection = currentInputConnection ?: return
        val replacement = SuggestionSelectionPlan.create(
            typedWord,
            suggestion,
            connection.getTextBeforeCursor(typedWord.length, 0).toString()
        )
        if (replacement == null) {
            directTypingState.clear()
            updateSuggestionRow(colors)
            return
        }
        if (replacement.changesText) {
            internalSelectionChange = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.deleteSurroundingTextInCodePoints(replacement.deleteCodePoints, 0)
            } else {
                connection.deleteSurroundingText(replacement.deleteCodeUnits, 0)
            }
            connection.commitText(replacement.replacement, 1)
        }
        directTypingState.replaceWith(suggestion)
        if (useLearning) persistLearnedWord(suggestion)
        updateSuggestionRow(colors)
    }

    private fun rememberFinishedDirectWord(word: String, learnUnknown: Boolean = true) {
        if (word.isBlank()) return
        when (language) {
            KeyboardLanguage.ENGLISH -> {
                recordContext(englishContext, KeyboardPreferences.KEY_CONTEXT_ENGLISH, word)
                recentEnglishWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_ENGLISH, recentEnglishWords)
                if (useLearning && learnUnknown &&
                    WordLearningPolicy.shouldLearnUnknown(word, englishSuggester::contains)
                ) {
                    persistLearnedWord(word)
                }
            }
            KeyboardLanguage.NEPALI -> {
                recordContext(nepaliContext, KeyboardPreferences.KEY_CONTEXT_NEPALI, word)
                recentNepaliWords.record(word)
                persistRecentWords(KeyboardPreferences.KEY_RECENT_NEPALI, recentNepaliWords)
                if (useLearning && learnUnknown &&
                    WordLearningPolicy.shouldLearnUnknown(word, nepaliSuggester::contains)
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
        if (lastCommittedWord.isNotEmpty()) {
            model.record(lastCommittedWord, word)
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, model.serialize())
                .apply()
        }
    }

    private fun editorContext(composingWord: String = ""): EditorContext =
        EditorContext.from(textBeforeCursor(80), composingWord)

    private fun textBeforeCursor(limit: Int): String =
        currentInputConnection?.getTextBeforeCursor(limit, 0)?.toString().orEmpty()

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

    private fun persistLearnedWord(word: String) {
        val (store, preferenceKey) = when (language) {
            KeyboardLanguage.ENGLISH -> learnedEnglishWords to KeyboardPreferences.KEY_LEARNED_ENGLISH
            KeyboardLanguage.NEPALI -> learnedNepaliWords to KeyboardPreferences.KEY_LEARNED_NEPALI
            KeyboardLanguage.ROMAN -> return
        }
        if (store.record(word)) {
            getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(preferenceKey, store.serialize())
                .apply()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKeyButton(key: KeySpec, colors: KeyboardPalette): View {
        val compactScreen = resources.configuration.screenWidthDp < 360
        val horizontalGap = if (key.compact) {
            dp(KeyboardTheme.COMPACT_HORIZONTAL_GAP_DP)
        } else {
            preferredKeyMargin()
        }
        return KeyboardKeyView(this).apply {
            bind(
                key = key,
                palette = colors,
                active = isActiveKey(key),
                compactScreen = compactScreen,
                horizontalGapPx = horizontalGap,
                verticalGapPx = dp(KeyboardTheme.KEY_VERTICAL_GAP_DP / 2),
                radiusPx = dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat(),
                shadowPx = dp(KeyboardTheme.SHADOW_DP)
            )
            if (key.action == KeyAction.SETTINGS) {
                contentDescription = getString(R.string.settings_key_description)
            }
            setOnClickListener {
                hideOverlays()
                giveFeedback(key.action)
                handleKey(key)
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (KeyVisuals.showsPreview(key)) {
                            previewView?.showAbove(view, overlayHost, key.label)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> previewView?.dismiss()
                }
                false
            }
            val alternates = KeyVisuals.alternates(key.label)
            when {
                alternates.isNotEmpty() && key.action == KeyAction.TEXT -> {
                    setOnLongClickListener { view ->
                        previewView?.dismiss()
                        giveFeedback(KeyAction.TEXT)
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
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (resources.configuration.screenWidthDp < 360) 12f else 13f)
        setTextColor(if (selected) Color.WHITE else colors.text)
        background = KeyboardTheme.keyBackground(
            if (selected) colors.accent else colors.specialKey,
            dp(KeyboardTheme.KEY_CORNER_RADIUS_DP).toFloat(),
            colors.shadow,
            dp(KeyboardTheme.SHADOW_DP)
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
            KeyAction.SHIFT -> shifted
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
            else -> false
        }
    }

    private fun handleKey(key: KeySpec) {
        when (key.action) {
            KeyAction.TEXT -> {
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
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(finishRomanWord(" "))
                    updateSuggestionRow()
                } else {
                    rememberFinishedDirectWord(directTypingState.currentWord)
                    directTypingState.clear()
                    commitText(" ")
                    updateSuggestionRow()
                }
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
                shifted = !shifted
                renderKeyboard()
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
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(finishRomanWord())
                } else {
                    rememberFinishedDirectWord(directTypingState.currentWord)
                }
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
                if (handwritingState.undo()) handwritingCanvas?.undoStroke()
                updateHandwritingResultRow()
            }
            KeyAction.HANDWRITING_CLEAR -> {
                handwritingState.clear()
                handwritingCanvas?.clearInk()
                updateHandwritingResultRow()
            }
            KeyAction.HANDWRITING_CONFIRM -> {
                if (handwritingState.status == HandwritingStatus.RESULTS) {
                    insertHandwritingCandidate(0, keyboardColors())
                } else {
                    handwritingState.recognize()
                    updateHandwritingResultRow()
                }
            }
            KeyAction.HANDWRITING_CANCEL -> returnToPreviousLayout()
            KeyAction.MODE_ENGLISH -> switchTypingMode(KeyboardLanguage.ENGLISH)
            KeyAction.MODE_NEPALI -> switchTypingMode(KeyboardLanguage.NEPALI)
            KeyAction.MODE_ROMAN -> switchTypingMode(KeyboardLanguage.ROMAN)
            KeyAction.SETTINGS -> openSettings()
        }
    }

    private fun insertAlternate(text: String) {
        hideOverlays()
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
    }

    private fun openPanel(target: LayoutMode) {
        if (language == KeyboardLanguage.ROMAN) {
            applyRomanEdit(finishRomanWord())
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
        shifted = false
        if (target == LayoutMode.NUMBERS && layoutMode != LayoutMode.NUMBERS && layoutMode != LayoutMode.SYMBOLS) {
            digitScript = DigitScript.defaultFor(language)
        }
        if (target == LayoutMode.SYMBOLS && layoutMode != LayoutMode.SYMBOLS && layoutMode != LayoutMode.NUMBERS) {
            symbolGroup = SymbolGroup.COMMON
        }
        layoutMode = target
        if (target == LayoutMode.EMOJI) {
            ensureEmojiDataset()
            emojiCategory = EmojiCategory.RECENT
            emojiQuery = ""
        }
        renderKeyboard()
    }

    private fun openHandwriting() {
        if (language == KeyboardLanguage.ROMAN) {
            applyRomanEdit(finishRomanWord())
        }
        directTypingState.clear()
        internalSelectionChange = false
        if (layoutMode != LayoutMode.HANDWRITING) {
            modeHistory.remember(ModeSnapshot(language, layoutMode))
        }
        handwritingState.clear()
        shifted = false
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
        shifted = false
        renderKeyboard()
    }

    private fun switchTypingMode(targetLanguage: KeyboardLanguage) {
        resetHandwriting()
        directTypingState.clear()
        internalSelectionChange = false
        modeHistory.clear()
        language = targetLanguage
        shifted = false
        layoutMode = LayoutMode.LETTERS
        renderKeyboard()
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
        val before = textBeforeCursor(80)
        var incoming = text
        if (language == KeyboardLanguage.ENGLISH && !shifted) {
            incoming = CapitalizationPolicy.applyIncomingLetter(incoming, before)
        }
        val spacing = PunctuationSpacing.plan(before, incoming)
        if (spacing.deleteBefore > 0) {
            currentInputConnection?.deleteSurroundingText(spacing.deleteBefore, 0)
        }
        if (spacing.insertLeadingSpace) {
            commitText(" ")
        }
        val pending = directTypingState.currentWord
        val isWordText = directTypingState.append(spacing.text, typingLanguage)
        commitText(spacing.text)
        if (!isWordText && pending.isNotEmpty()) rememberFinishedDirectWord(pending)
        if (isWordText || directTypingState.currentWord.isEmpty()) updateSuggestionRow()
    }

    private fun handleRomanText(text: String) {
        val edit = if (text.all(Char::isLetter)) {
            romanComposer.type(text)
        } else {
            finishRomanWord(text)
        }
        applyRomanEdit(edit)
        if (shifted && text.firstOrNull()?.isLetter() == true) {
            shifted = false
            renderKeyboard()
        } else {
            updateSuggestionRow()
        }
    }

    private fun applyRomanEdit(edit: RomanEdit) {
        val connection = currentInputConnection ?: return
        when (edit) {
            is RomanEdit.SetComposing -> connection.setComposingText(edit.text, 1)
            is RomanEdit.Commit -> connection.commitText(edit.text, 1)
            RomanEdit.ClearComposing -> {
                connection.setComposingText("", 1)
                connection.finishComposingText()
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
        if (shifted && language == KeyboardLanguage.ENGLISH && text.firstOrNull()?.isLetter() == true) {
            shifted = false
            renderKeyboard()
        }
    }

    private fun deleteOneCharacter() {
        val connection = currentInputConnection ?: return
        val selection = connection.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connection.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun sendEnter() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        val supportsAction = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0

        if (supportsAction) {
            connection.performEditorAction(action)
        } else {
            connection.commitText("\n", 1)
        }
    }

    private fun giveFeedback(action: KeyAction) {
        if (useSound) {
            val sound = when (action) {
                KeyAction.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyAction.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(sound)
        }
        if (useVibration) vibrator()?.let { deviceVibrator ->
            if (!deviceVibrator.hasVibrator()) return@let
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deviceVibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(18)
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

    private fun preferredKeyHeight(): Int {
        val configuration = resources.configuration
        return KeyboardUiMetrics.keyHeightDp(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            keyboardHeight
        )
    }

    private fun systemUsesDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun keyboardColors(): KeyboardPalette =
        KeyboardTheme.palette(useDarkAppearance) { color(it) }

    private fun preferredKeyMargin(): Int {
        val compact = resources.configuration.screenWidthDp < 360
        val gap = if (compact) KeyboardTheme.COMPACT_HORIZONTAL_GAP_DP else KeyboardTheme.KEY_HORIZONTAL_GAP_DP
        return dp(maxOf(gap, KeyboardUiMetrics.keyMarginDp(resources.configuration.screenWidthDp)))
    }

    private fun color(resource: Int): Int = resources.getColor(resource, theme)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class ModeSnapshot(
        val language: KeyboardLanguage,
        val layout: LayoutMode
    )

    private companion object {
        const val EMOJIS_PER_ROW = 8
        const val EMOJI_VISIBLE_ROWS = 4
        const val MAX_SUGGESTIONS = 3
    }
}
