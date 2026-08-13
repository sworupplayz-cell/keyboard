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
    const val KEY_SMART_PUNCTUATION = "smart_punctuation_enabled"
    const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period_enabled"
    const val KEY_AUTO_CAPITALIZATION = "auto_capitalization_enabled"
    const val KEY_EMOJI_RECENTS = "emoji_recents_enabled"
    const val KEY_TOOLBAR = "toolbar_enabled"
    const val KEY_TOOLBAR_ORDER = "toolbar_order"
    const val KEY_TOOLBAR_ENABLED_ITEMS = "toolbar_enabled_items"
    const val KEY_TOOLBAR_AUTO_COLLAPSE = "toolbar_auto_collapse"
    const val KEY_LANGUAGE_BUTTON = "language_button_enabled"
    const val KEY_TYPO_SUGGESTIONS = "typo_suggestions_enabled"
    const val KEY_ONE_HANDED = "one_handed_alignment"
    const val KEY_PRESENTATION_MODE = "keyboard_presentation_mode"
    const val KEY_CLIPBOARD_HISTORY = "clipboard_history_enabled"
    const val KEY_CLIPBOARD_ITEMS = "clipboard_items"

    // Read only for migration from Phase 1–7 installations.
    const val KEY_DARK_LEGACY = "dark_appearance"

    const val KEY_RECENT_EMOJIS = "recent_emojis"
    const val KEY_EMOJI_USAGE = "emoji_usage"
    const val KEY_RECENT_SYMBOLS = "recent_symbols"
    const val KEY_LEARNED_ROMAN = "learned_roman_words"
    const val KEY_LEARNED_ENGLISH = "learned_english_words"
    const val KEY_LEARNED_NEPALI = "learned_nepali_words"
    const val KEY_RECENT_ENGLISH = "recent_english_words"
    const val KEY_RECENT_NEPALI = "recent_nepali_words"
    const val KEY_RECENT_ROMAN = "recent_roman_words"
    const val KEY_CONTEXT_ENGLISH = "context_english_pairs"
    const val KEY_CONTEXT_NEPALI = "context_nepali_pairs"
    const val KEY_CONTEXT_ROMAN = "context_roman_pairs"

    val LEARNED_WORD_KEYS = setOf(
        KEY_LEARNED_ROMAN,
        KEY_LEARNED_ENGLISH,
        KEY_LEARNED_NEPALI
    )

    val LOCAL_DATA_KEYS = LEARNED_WORD_KEYS + setOf(
        KEY_RECENT_EMOJIS,
        KEY_EMOJI_USAGE,
        KEY_RECENT_SYMBOLS,
        KEY_RECENT_ENGLISH,
        KEY_RECENT_NEPALI,
        KEY_RECENT_ROMAN,
        KEY_CONTEXT_ENGLISH,
        KEY_CONTEXT_NEPALI,
        KEY_CONTEXT_ROMAN,
        KEY_CLIPBOARD_ITEMS
    )
}
