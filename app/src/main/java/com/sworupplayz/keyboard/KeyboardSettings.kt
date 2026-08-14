package com.sworupplayz.keyboard

/** Small, typed model around the keyboard's local preferences. */
enum class DefaultKeyboardMode {
    ENGLISH,
    NEPALI,
    ROMAN;

    fun toKeyboardLanguage(): KeyboardLanguage = when (this) {
        ENGLISH -> KeyboardLanguage.ENGLISH
        NEPALI -> KeyboardLanguage.NEPALI
        ROMAN -> KeyboardLanguage.ROMAN
    }

    companion object {
        fun fromStored(value: String?): DefaultKeyboardMode =
            entries.firstOrNull { it.name == value } ?: ENGLISH
    }
}

enum class KeyboardAppearance {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): KeyboardAppearance =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class KeyboardHeight {
    SMALL,
    NORMAL,
    LARGE;

    companion object {
        fun fromStored(value: String?): KeyboardHeight =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

data class KeyboardSettings(
    val defaultMode: DefaultKeyboardMode = DefaultKeyboardMode.ENGLISH,
    val appearance: KeyboardAppearance = KeyboardAppearance.SYSTEM,
    val suggestions: Boolean = true,
    val learnedWords: Boolean = true,
    val numberRow: Boolean = false,
    val keySound: Boolean = false,
    val keyVibration: Boolean = false,
    val height: KeyboardHeight = KeyboardHeight.NORMAL,
    val smartPunctuation: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoCapitalization: Boolean = true,
    val emojiRecents: Boolean = true,
    val toolbar: Boolean = true,
    val clipboardHistory: Boolean = true,
    val toolbarAutoCollapse: Boolean = true,
    val languageButton: Boolean = true,
    val typoSuggestions: Boolean = true,
    val oneHanded: OneHandedAlignment = OneHandedAlignment.OFF,
    val presentationMode: KeyboardPresentationMode = KeyboardPresentationMode.NORMAL,
    val visualTheme: KeyboardVisualTheme = KeyboardVisualTheme.FOLLOW_APPEARANCE,
    val colorPreset: ColorPreset = ColorPreset.THEME,
    val keyDensity: KeyDensity = KeyDensity.NORMAL,
    val keySpacing: KeySpacing = KeySpacing.NORMAL,
    val keyCorner: KeyCornerStyle = KeyCornerStyle.NORMAL,
    val keyShadows: Boolean = true,
    val keyBorders: Boolean = false,
    val pressedHighlight: Boolean = true,
    val soundVolume: SoundVolume = SoundVolume.MEDIUM,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM
)

/** Minimal storage contract keeps preference behavior independently testable. */
interface SettingsStorage {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String): String?
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun remove(keys: Set<String>)
}

class KeyboardSettingsRepository(private val storage: SettingsStorage) {
    fun load(): KeyboardSettings = KeyboardSettings(
        defaultMode = DefaultKeyboardMode.fromStored(storage.getString(KeyboardPreferences.KEY_DEFAULT_MODE)),
        appearance = loadAppearance(),
        suggestions = storage.getBoolean(KeyboardPreferences.KEY_SUGGESTIONS, true),
        learnedWords = storage.getBoolean(KeyboardPreferences.KEY_LEARNING, true),
        numberRow = storage.getBoolean(KeyboardPreferences.KEY_NUMBER_ROW, false),
        keySound = storage.getBoolean(KeyboardPreferences.KEY_SOUND, false),
        keyVibration = storage.getBoolean(KeyboardPreferences.KEY_VIBRATION, false),
        height = KeyboardHeight.fromStored(storage.getString(KeyboardPreferences.KEY_HEIGHT)),
        smartPunctuation = storage.getBoolean(KeyboardPreferences.KEY_SMART_PUNCTUATION, true),
        doubleSpacePeriod = storage.getBoolean(KeyboardPreferences.KEY_DOUBLE_SPACE_PERIOD, true),
        autoCapitalization = storage.getBoolean(KeyboardPreferences.KEY_AUTO_CAPITALIZATION, true),
        emojiRecents = storage.getBoolean(KeyboardPreferences.KEY_EMOJI_RECENTS, true),
        toolbar = storage.getBoolean(KeyboardPreferences.KEY_TOOLBAR, true),
        clipboardHistory = storage.getBoolean(KeyboardPreferences.KEY_CLIPBOARD_HISTORY, true),
        toolbarAutoCollapse = storage.getBoolean(KeyboardPreferences.KEY_TOOLBAR_AUTO_COLLAPSE, true),
        languageButton = storage.getBoolean(KeyboardPreferences.KEY_LANGUAGE_BUTTON, true),
        typoSuggestions = storage.getBoolean(KeyboardPreferences.KEY_TYPO_SUGGESTIONS, true),
        oneHanded = OneHandedAlignment.fromStored(storage.getString(KeyboardPreferences.KEY_ONE_HANDED)),
        presentationMode = KeyboardPresentationMode.fromStored(storage.getString(KeyboardPreferences.KEY_PRESENTATION_MODE)),
        visualTheme = KeyboardVisualTheme.fromStored(storage.getString(KeyboardPreferences.KEY_VISUAL_THEME)),
        colorPreset = ColorPreset.fromStored(storage.getString(KeyboardPreferences.KEY_COLOR_PRESET)),
        keyDensity = KeyDensity.fromStored(storage.getString(KeyboardPreferences.KEY_DENSITY)),
        keySpacing = KeySpacing.fromStored(storage.getString(KeyboardPreferences.KEY_SPACING)),
        keyCorner = KeyCornerStyle.fromStored(storage.getString(KeyboardPreferences.KEY_CORNER)),
        keyShadows = storage.getBoolean(KeyboardPreferences.KEY_SHADOWS, true),
        keyBorders = storage.getBoolean(KeyboardPreferences.KEY_BORDERS, false),
        pressedHighlight = storage.getBoolean(KeyboardPreferences.KEY_PRESSED_HIGHLIGHT, true),
        soundVolume = SoundVolume.fromStored(storage.getString(KeyboardPreferences.KEY_SOUND_VOLUME)),
        hapticStrength = HapticStrength.fromStored(storage.getString(KeyboardPreferences.KEY_HAPTIC_STRENGTH))
    )

    fun savedDefaultMode(): DefaultKeyboardMode? =
        if (storage.contains(KeyboardPreferences.KEY_DEFAULT_MODE)) {
            DefaultKeyboardMode.fromStored(storage.getString(KeyboardPreferences.KEY_DEFAULT_MODE))
        } else {
            null
        }

    fun setDefaultMode(value: DefaultKeyboardMode) =
        storage.putString(KeyboardPreferences.KEY_DEFAULT_MODE, value.name)

    fun setAppearance(value: KeyboardAppearance) {
        storage.putString(KeyboardPreferences.KEY_APPEARANCE, value.name)
        storage.remove(setOf(KeyboardPreferences.KEY_DARK_LEGACY))
    }

    fun setSuggestions(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_SUGGESTIONS, value)

    fun setLearnedWords(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_LEARNING, value)

    fun setNumberRow(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_NUMBER_ROW, value)

    fun setKeySound(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_SOUND, value)

    fun setKeyVibration(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_VIBRATION, value)

    fun setHeight(value: KeyboardHeight) =
        storage.putString(KeyboardPreferences.KEY_HEIGHT, value.name)

    fun setSmartPunctuation(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_SMART_PUNCTUATION, value)

    fun setDoubleSpacePeriod(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_DOUBLE_SPACE_PERIOD, value)

    fun setAutoCapitalization(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_AUTO_CAPITALIZATION, value)

    fun setEmojiRecents(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_EMOJI_RECENTS, value)

    fun setToolbar(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_TOOLBAR, value)

    fun setClipboardHistory(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_CLIPBOARD_HISTORY, value)

    fun setToolbarAutoCollapse(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_TOOLBAR_AUTO_COLLAPSE, value)

    fun setLanguageButton(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_LANGUAGE_BUTTON, value)

    fun setTypoSuggestions(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_TYPO_SUGGESTIONS, value)

    fun setOneHanded(value: OneHandedAlignment) =
        storage.putString(KeyboardPreferences.KEY_ONE_HANDED, value.name)

    fun setPresentationMode(value: KeyboardPresentationMode) =
        storage.putString(KeyboardPreferences.KEY_PRESENTATION_MODE, value.name)

    fun setVisualTheme(value: KeyboardVisualTheme) =
        storage.putString(KeyboardPreferences.KEY_VISUAL_THEME, value.name)

    fun setColorPreset(value: ColorPreset) =
        storage.putString(KeyboardPreferences.KEY_COLOR_PRESET, value.name)

    fun setKeyDensity(value: KeyDensity) =
        storage.putString(KeyboardPreferences.KEY_DENSITY, value.name)

    fun setKeySpacing(value: KeySpacing) =
        storage.putString(KeyboardPreferences.KEY_SPACING, value.name)

    fun setKeyCorner(value: KeyCornerStyle) =
        storage.putString(KeyboardPreferences.KEY_CORNER, value.name)

    fun setKeyShadows(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_SHADOWS, value)

    fun setKeyBorders(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_BORDERS, value)

    fun setPressedHighlight(value: Boolean) =
        storage.putBoolean(KeyboardPreferences.KEY_PRESSED_HIGHLIGHT, value)

    fun setSoundVolume(value: SoundVolume) =
        storage.putString(KeyboardPreferences.KEY_SOUND_VOLUME, value.name)

    fun setHapticStrength(value: HapticStrength) =
        storage.putString(KeyboardPreferences.KEY_HAPTIC_STRENGTH, value.name)

    fun resetAppearance() = storage.remove(KeyboardPreferences.APPEARANCE_KEYS)

    fun resetLayout() {
        storage.remove(KeyboardPreferences.LAYOUT_KEYS)
        restoreDefaultToolbar()
    }

    fun resetAllSettings() = storage.remove(KeyboardPreferences.ALL_SETTING_KEYS)

    fun toolbarConfiguration(): ToolbarConfiguration = ToolbarConfiguration.fromSerialized(
        orderValue = storage.getString(KeyboardPreferences.KEY_TOOLBAR_ORDER),
        enabledValue = storage.getString(KeyboardPreferences.KEY_TOOLBAR_ENABLED_ITEMS),
        alwaysVisible = storage.getBoolean(KeyboardPreferences.KEY_TOOLBAR, true),
        autoCollapse = storage.getBoolean(KeyboardPreferences.KEY_TOOLBAR_AUTO_COLLAPSE, true)
    )

    fun saveToolbarConfiguration(value: ToolbarConfiguration) {
        storage.putString(KeyboardPreferences.KEY_TOOLBAR_ORDER, value.serializeOrder())
        storage.putString(KeyboardPreferences.KEY_TOOLBAR_ENABLED_ITEMS, value.serializeEnabled())
        storage.putBoolean(KeyboardPreferences.KEY_TOOLBAR, value.alwaysVisible)
        storage.putBoolean(KeyboardPreferences.KEY_TOOLBAR_AUTO_COLLAPSE, value.autoCollapse)
    }

    fun restoreDefaultToolbar() {
        saveToolbarConfiguration(ToolbarConfiguration.defaults())
    }

    fun clearClipboardHistory() = storage.remove(setOf(KeyboardPreferences.KEY_CLIPBOARD_ITEMS))

    fun clearLocalData() = storage.remove(KeyboardPreferences.LOCAL_DATA_KEYS)

    fun clearLearnedWords() = storage.remove(KeyboardPreferences.LEARNED_WORD_KEYS)

    fun clearRecentEmojiAndSymbols() = storage.remove(
        setOf(
            KeyboardPreferences.KEY_RECENT_EMOJIS,
            KeyboardPreferences.KEY_EMOJI_USAGE,
            KeyboardPreferences.KEY_RECENT_SYMBOLS
        )
    )

    private fun loadAppearance(): KeyboardAppearance {
        storage.getString(KeyboardPreferences.KEY_APPEARANCE)?.let {
            return KeyboardAppearance.fromStored(it)
        }
        return if (storage.contains(KeyboardPreferences.KEY_DARK_LEGACY)) {
            if (storage.getBoolean(KeyboardPreferences.KEY_DARK_LEGACY, false)) {
                KeyboardAppearance.DARK
            } else {
                KeyboardAppearance.LIGHT
            }
        } else {
            KeyboardAppearance.SYSTEM
        }
    }
}

object KeyboardModePolicy {
    fun initialLanguage(savedMode: DefaultKeyboardMode?, subtypeLocale: String): KeyboardLanguage =
        savedMode?.toKeyboardLanguage() ?: if (subtypeLocale.startsWith("ne", ignoreCase = true)) {
            KeyboardLanguage.NEPALI
        } else {
            KeyboardLanguage.ENGLISH
        }
}
