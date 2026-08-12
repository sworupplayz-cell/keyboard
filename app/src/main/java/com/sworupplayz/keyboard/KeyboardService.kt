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

class KeyboardService : InputMethodService() {
    private enum class LayoutMode {
        LETTERS,
        VOWELS,
        SYMBOLS
    }

    private lateinit var keyboardRoot: LinearLayout
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
        shifted = false
        if (::keyboardRoot.isInitialized) {
            applyWindowAppearance()
            renderKeyboard()
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        updateLanguageFromSubtype(newSubtype)
        shifted = false
        layoutMode = LayoutMode.LETTERS
        if (::keyboardRoot.isInitialized) renderKeyboard()
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
        val rows = when {
            layoutMode == LayoutMode.SYMBOLS -> KeyboardLayouts.symbols(language)
            language == KeyboardLanguage.ENGLISH -> KeyboardLayouts.english(shifted)
            layoutMode == LayoutMode.VOWELS -> KeyboardLayouts.nepaliVowels()
            else -> KeyboardLayouts.nepaliConsonants()
        }

        val colors = keyboardColors()
        keyboardRoot.setBackgroundColor(colors.background)
        keyboardRoot.removeAllViews()

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
            if (key.action == KeyAction.LANGUAGE) {
                setOnLongClickListener {
                    switchToNextInputMethod(false)
                    true
                }
            }
        }
    }

    private fun handleKey(key: KeySpec) {
        when (key.action) {
            KeyAction.TEXT -> commitText(key.output)
            KeyAction.SPACE -> commitText(" ")
            KeyAction.BACKSPACE -> deleteOneCharacter()
            KeyAction.ENTER -> sendEnter()
            KeyAction.SHIFT -> {
                shifted = !shifted
                renderKeyboard()
            }
            KeyAction.SYMBOLS -> {
                shifted = false
                layoutMode = LayoutMode.SYMBOLS
                renderKeyboard()
            }
            KeyAction.LETTERS -> {
                layoutMode = LayoutMode.LETTERS
                renderKeyboard()
            }
            KeyAction.LANGUAGE -> {
                shifted = false
                language = if (language == KeyboardLanguage.ENGLISH) {
                    KeyboardLanguage.NEPALI
                } else {
                    KeyboardLanguage.ENGLISH
                }
                layoutMode = LayoutMode.LETTERS
                renderKeyboard()
            }
            KeyAction.VOWELS -> {
                layoutMode = LayoutMode.VOWELS
                renderKeyboard()
            }
            KeyAction.CONSONANTS -> {
                layoutMode = LayoutMode.LETTERS
                renderKeyboard()
            }
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
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

    private data class KeyboardColors(
        val background: Int,
        val key: Int,
        val specialKey: Int,
        val text: Int,
        val accent: Int
    )
}
