package com.sworupplayz.keyboard

object KeyboardPreferences {
    const val FILE_NAME = "keyboard_preferences"

    const val KEY_DEFAULT_MODE = "default_keyboard_mode"
    const val KEY_APPEARANCE = "keyboard_appearance"
    const val KEY_HEIGHT = "keyboard_height"
    const val KEY_NUMBER_ROW = "number_row_enabled"
    const val KEY_SOUND = "key_sound"
    const val KEY_VIBRATION = "key_vibration"
    const val KEY_SUGGESTIONS = "suggestions_enabled"
    const val KEY_LEARNING = "learned_words_enabled"

    // Read only for migration from Phase 1–7 installations.
    const val KEY_DARK_LEGACY = "dark_appearance"

    const val KEY_RECENT_EMOJIS = "recent_emojis"
    const val KEY_LEARNED_ROMAN = "learned_roman_words"
    const val KEY_LEARNED_ENGLISH = "learned_english_words"
    const val KEY_LEARNED_NEPALI = "learned_nepali_words"

    val LEARNED_WORD_KEYS = setOf(
        KEY_LEARNED_ROMAN,
        KEY_LEARNED_ENGLISH,
        KEY_LEARNED_NEPALI
    )
}
