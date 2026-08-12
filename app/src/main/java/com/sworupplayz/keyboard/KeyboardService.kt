package com.sworupplayz.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Button
import android.widget.LinearLayout
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

    private lateinit var keyboardRoot: LinearLayout
    private var suggestionRow: LinearLayout? = null
    private var handwritingResultRow: LinearLayout? = null
    private var handwritingCanvas: HandwritingCanvasView? = null
    private val handwritingState = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
    private val modeHistory = PreviousLayoutStack<ModeSnapshot>()
    private var emojiCategory = EmojiCategory.RECENT
    private val recentEmojis: RecentEmojiList by lazy {
        val saved = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .getString(KeyboardPreferences.KEY_RECENT_EMOJIS, null)
            .orEmpty()
            .split('\n')
            .filter(String::isNotEmpty)
        RecentEmojiList(saved)
    }
    private val learnedRomanWords: LearnedRomanWords by lazy {
        val serialized = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .getString(KeyboardPreferences.KEY_LEARNED_ROMAN, null)
        LearnedRomanWords.fromSerialized(serialized)
    }
    private val romanConverter: RomanNepaliConverter by lazy {
        resources.openRawResource(R.raw.roman_nepali_dictionary).bufferedReader().use {
            RomanNepaliConverter.from(it)
        }
    }
    private val romanComposerDelegate = lazy {
        RomanInputComposer(romanConverter, learnedRomanWords::lookup)
    }
    private val romanComposer: RomanInputComposer get() = romanComposerDelegate.value
    private var language = KeyboardLanguage.ENGLISH
    private var layoutMode = LayoutMode.LETTERS
    private var shifted = false
    private var useSound = false
    private var useVibration = false
    private var useDarkAppearance = false
    private var keyHeightDp = 48

    override fun onCreateInputView(): View {
        readPreferences()
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        updateLanguageFromSubtype(inputMethodManager.currentInputMethodSubtype)
        keyHeightDp = preferredKeyHeight()
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        applyWindowAppearance()
        renderKeyboard()
        return keyboardRoot
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        readPreferences()
        if (romanComposerDelegate.isInitialized() && romanComposer.currentWord.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            romanComposer.reset()
        }
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
        resetHandwriting()
        modeHistory.clear()
        suggestionRow = null
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
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun updateLanguageFromSubtype(subtype: InputMethodSubtype?) {
        val locale = subtype?.locale.orEmpty()
        language = if (locale.startsWith("ne", ignoreCase = true)) {
            KeyboardLanguage.NEPALI
        } else {
            KeyboardLanguage.ENGLISH
        }
    }

    private fun readPreferences() {
        val preferences = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
        useSound = preferences.getBoolean(KeyboardPreferences.KEY_SOUND, false)
        useVibration = preferences.getBoolean(KeyboardPreferences.KEY_VIBRATION, false)
        useDarkAppearance = preferences.getBoolean(KeyboardPreferences.KEY_DARK, false)
    }

    private fun renderKeyboard() {
        val colors = keyboardColors()
        keyboardRoot.setBackgroundColor(colors.background)
        keyboardRoot.removeAllViews()
        suggestionRow = null
        handwritingResultRow = null
        handwritingCanvas = null

        if (layoutMode == LayoutMode.HANDWRITING) {
            renderHandwriting(colors)
            return
        }
        if (layoutMode == LayoutMode.EMOJI) {
            renderEmojiPanel(colors)
            return
        }

        addNavigationRow(colors)
        val rows = when {
            layoutMode == LayoutMode.NUMBERS -> KeyboardLayouts.numbers(language)
            layoutMode == LayoutMode.SYMBOLS -> KeyboardLayouts.symbols(language)
            language == KeyboardLanguage.ENGLISH || language == KeyboardLanguage.ROMAN ->
                KeyboardLayouts.english(shifted, language)
            layoutMode == LayoutMode.VOWELS -> KeyboardLayouts.nepaliVowels()
            else -> KeyboardLayouts.nepaliConsonants()
        }
        if (language == KeyboardLanguage.ROMAN && layoutMode == LayoutMode.LETTERS) {
            addSuggestionRow(colors)
        }
        addKeyRows(rows, colors)
    }

    private fun addKeyRows(rows: List<List<KeySpec>>, colors: KeyboardColors) {
        rows.forEach { keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            keyboardRoot.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(keyHeightDp))
            )
            keys.forEach { key -> row.addView(createKeyButton(key, colors)) }
        }
    }

    private fun addNavigationRow(colors: KeyboardColors) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )
        KeyboardLayouts.navigationControls().forEach { key ->
            row.addView(createKeyButton(key, colors))
        }
    }

    private fun renderEmojiPanel(colors: KeyboardColors) {
        val categories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keyboardRoot.addView(
            categories,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )
        EmojiCategory.entries.forEach { category ->
            val selected = category == emojiCategory
            categories.addView(Button(this).apply {
                text = category.label
                contentDescription = category.name.lowercase()
                isAllCaps = false
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = 18f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(if (selected) Color.WHITE else colors.text)
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = false
                stateListAnimator = null
                background = roundedBackground(if (selected) colors.accent else colors.specialKey)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                setOnClickListener {
                    giveFeedback(KeyAction.EMOJI)
                    emojiCategory = category
                    renderKeyboard()
                }
            })
        }

        val emojis = EmojiCatalog.emojis(emojiCategory, recentEmojis.values())
        if (emojis.isEmpty()) {
            keyboardRoot.addView(TextView(this).apply {
                text = getString(R.string.emoji_no_recent)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.text)
                alpha = 0.75f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(92)))
        } else {
            emojis.chunked(EMOJIS_PER_ROW).forEach { emojiRow ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                keyboardRoot.addView(
                    row,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
                )
                emojiRow.forEach { emoji -> row.addView(createEmojiButton(emoji, colors)) }
                repeat(EMOJIS_PER_ROW - emojiRow.size) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
        }
        addKeyRows(KeyboardLayouts.emojiControls(), colors)
    }

    private fun createEmojiButton(emoji: String, colors: KeyboardColors): Button = Button(this).apply {
        text = emoji
        contentDescription = emoji
        isAllCaps = false
        gravity = Gravity.CENTER
        includeFontPadding = false
        textSize = 24f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(colors.text)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        stateListAnimator = null
        background = roundedBackground(colors.key)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener {
            giveFeedback(KeyAction.EMOJI)
            insertEmoji(emoji)
        }
    }

    private fun insertEmoji(emoji: String) {
        val connection = currentInputConnection ?: return
        if (!InputConnectionCommitter.commit(connection, emoji)) return
        recentEmojis.record(emoji)
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyboardPreferences.KEY_RECENT_EMOJIS, recentEmojis.values().joinToString("\n"))
            .apply()
        returnToPreviousLayout()
    }

    private fun renderHandwriting(colors: KeyboardColors) {
        val resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        handwritingResultRow = resultRow
        keyboardRoot.addView(
            resultRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )

        val canvas = HandwritingCanvasView(this).apply {
            contentDescription = getString(R.string.handwriting_canvas_description)
            setInkColor(colors.text)
            background = roundedBackground(colors.key)
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

    private fun updateHandwritingResultRow(colors: KeyboardColors = keyboardColors()) {
        val row = handwritingResultRow ?: return
        row.removeAllViews()
        if (handwritingState.candidates.isNotEmpty()) {
            handwritingState.candidates.forEachIndexed { index, candidate ->
                row.addView(Button(this).apply {
                    text = candidate
                    contentDescription = candidate
                    isAllCaps = false
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    textSize = 17f
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(2), 0, dp(2), 0)
                    setTextColor(colors.text)
                    isSoundEffectsEnabled = false
                    isHapticFeedbackEnabled = false
                    stateListAnimator = null
                    background = roundedBackground(colors.key)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
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
            setTextColor(colors.text)
            alpha = 0.75f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun insertHandwritingCandidate(index: Int, colors: KeyboardColors) {
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

    private fun preferredCanvasHeight(): Int =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 96 else 140

    private fun addSuggestionRow(colors: KeyboardColors) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        suggestionRow = row
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )
        updateSuggestionRow(colors)
    }

    private fun updateSuggestionRow(colors: KeyboardColors = keyboardColors()) {
        val row = suggestionRow ?: return
        row.removeAllViews()
        val suggestions = romanConverter.suggestions(
            romanComposer.currentWord,
            learnedRomanWords.lookup(romanComposer.currentWord)
        )
        if (suggestions.isEmpty()) {
            row.addView(TextView(this).apply {
                text = if (romanComposer.currentWord.isEmpty()) {
                    getString(R.string.roman_suggestion_hint)
                } else {
                    romanComposer.currentWord
                }
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.text)
                alpha = 0.7f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            return
        }

        suggestions.forEach { suggestion ->
            row.addView(Button(this).apply {
                text = suggestion
                contentDescription = suggestion
                isAllCaps = false
                includeFontPadding = false
                gravity = Gravity.CENTER
                textSize = if (suggestion.length > 10) 13f else 16f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(2), 0, dp(2), 0)
                setTextColor(colors.text)
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = false
                stateListAnimator = null
                background = roundedBackground(colors.key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                setOnClickListener {
                    giveFeedback(KeyAction.TEXT)
                    val romanWord = romanComposer.currentWord
                    if (learnedRomanWords.learn(romanWord, suggestion)) {
                        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KeyboardPreferences.KEY_LEARNED_ROMAN, learnedRomanWords.serialize())
                            .apply()
                    }
                    applyRomanEdit(romanComposer.acceptSuggestion(suggestion))
                    updateSuggestionRow(colors)
                }
            })
        }
    }

    private fun createKeyButton(key: KeySpec, colors: KeyboardColors): Button {
        val isSpecial = key.action != KeyAction.TEXT
        val isActiveShift = key.action == KeyAction.SHIFT && shifted
        val buttonColor = when {
            isActiveShift -> colors.accent
            isSpecial -> colors.specialKey
            else -> colors.key
        }

        return Button(this).apply {
            text = key.label
            contentDescription = key.label
            isAllCaps = false
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(1), 0, dp(1), 0)
            setTextColor(if (isActiveShift) Color.WHITE else colors.text)
            textSize = if (key.label.length > 3) 13f else 18f
            setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            stateListAnimator = null
            elevation = dp(1).toFloat()
            background = roundedBackground(buttonColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, key.width).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setOnClickListener {
                giveFeedback(key.action)
                handleKey(key)
            }
            if (key.action == KeyAction.LANGUAGE ||
                key.action == KeyAction.MODE_ENGLISH ||
                key.action == KeyAction.MODE_NEPALI ||
                key.action == KeyAction.MODE_ROMAN
            ) {
                setOnLongClickListener {
                    switchToNextInputMethod(false)
                    true
                }
            }
        }
    }

    private fun handleKey(key: KeySpec) {
        when (key.action) {
            KeyAction.TEXT -> {
                if (language == KeyboardLanguage.ROMAN && layoutMode == LayoutMode.LETTERS) {
                    handleRomanText(key.output)
                } else {
                    commitText(key.output)
                }
            }
            KeyAction.SPACE -> {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(romanComposer.finishWord(" "))
                    updateSuggestionRow()
                } else {
                    commitText(" ")
                }
            }
            KeyAction.BACKSPACE -> {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(romanComposer.backspace())
                    updateSuggestionRow()
                } else {
                    deleteOneCharacter()
                }
            }
            KeyAction.ENTER -> {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(romanComposer.finishWord())
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
            KeyAction.EMOJI -> openPanel(LayoutMode.EMOJI)
            KeyAction.RETURN_TO_PREVIOUS -> returnToPreviousLayout()
            KeyAction.LETTERS -> returnToPreviousLayout()
            KeyAction.LANGUAGE -> {
                if (language == KeyboardLanguage.ROMAN) {
                    applyRomanEdit(romanComposer.finishWord())
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
        }
    }

    private fun openPanel(target: LayoutMode) {
        if (language == KeyboardLanguage.ROMAN) applyRomanEdit(romanComposer.finishWord())
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
        layoutMode = target
        if (target == LayoutMode.EMOJI) emojiCategory = EmojiCategory.RECENT
        renderKeyboard()
    }

    private fun openHandwriting() {
        if (language == KeyboardLanguage.ROMAN) applyRomanEdit(romanComposer.finishWord())
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
        val previous = modeHistory.previousOr(ModeSnapshot(language, LayoutMode.LETTERS))
        language = previous.language
        layoutMode = previous.layout
        shifted = false
        renderKeyboard()
    }

    private fun switchTypingMode(targetLanguage: KeyboardLanguage) {
        resetHandwriting()
        modeHistory.clear()
        language = targetLanguage
        shifted = false
        layoutMode = LayoutMode.LETTERS
        renderKeyboard()
    }

    private fun handleRomanText(text: String) {
        val edit = if (text.all(Char::isLetter)) {
            romanComposer.type(text)
        } else {
            romanComposer.finishWord(text)
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
        window.navigationBarColor = colors.background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = if (useDarkAppearance) {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    private fun preferredKeyHeight(): Int {
        val configuration = resources.configuration
        return when {
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> 40
            configuration.screenWidthDp < 360 -> 44
            else -> 48
        }
    }

    private fun keyboardColors(): KeyboardColors = if (useDarkAppearance) {
        KeyboardColors(
            background = color(R.color.keyboard_dark_background),
            key = color(R.color.keyboard_dark_key),
            specialKey = color(R.color.keyboard_dark_special),
            text = color(R.color.keyboard_dark_text),
            accent = color(R.color.accent)
        )
    } else {
        KeyboardColors(
            background = color(R.color.keyboard_light_background),
            key = color(R.color.keyboard_light_key),
            specialKey = color(R.color.keyboard_light_special),
            text = color(R.color.keyboard_light_text),
            accent = color(R.color.accent)
        )
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(5).toFloat()
        setColor(color)
    }

    private fun color(resource: Int): Int = resources.getColor(resource, theme)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class ModeSnapshot(
        val language: KeyboardLanguage,
        val layout: LayoutMode
    )

    private data class KeyboardColors(
        val background: Int,
        val key: Int,
        val specialKey: Int,
        val text: Int,
        val accent: Int
    )

    private companion object {
        const val EMOJIS_PER_ROW = 8
    }
}
