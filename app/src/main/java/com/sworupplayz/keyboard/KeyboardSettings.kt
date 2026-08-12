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
    val height: KeyboardHeight = KeyboardHeight.NORMAL
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
        height = KeyboardHeight.fromStored(storage.getString(KeyboardPreferences.KEY_HEIGHT))
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
