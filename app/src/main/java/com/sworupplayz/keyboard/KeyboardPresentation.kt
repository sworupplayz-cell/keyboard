package com.sworupplayz.keyboard

enum class KeyboardPresentationMode {
    NORMAL,
    ONE_HANDED,
    FLOATING;

    companion object {
        fun fromStored(value: String?): KeyboardPresentationMode =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class OneHandedAlignment {
    OFF,
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        fun fromStored(value: String?): OneHandedAlignment =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

data class OneHandedInsets(
    val startDp: Int,
    val endDp: Int,
    val contentWidthDp: Int
)

/**
 * Layout-only one-handed mode. FLOATING is accepted as a stored preference but
 * is not applied — there is no WindowManager floating implementation yet.
 */
object OneHandedLayoutPolicy {
    const val WIDTH_RATIO = 0.82f

    fun presentation(
        alignment: OneHandedAlignment,
        requested: KeyboardPresentationMode
    ): KeyboardPresentationMode = when {
        requested == KeyboardPresentationMode.FLOATING -> KeyboardPresentationMode.NORMAL
        alignment != OneHandedAlignment.OFF -> KeyboardPresentationMode.ONE_HANDED
        else -> KeyboardPresentationMode.NORMAL
    }

    fun isActive(alignment: OneHandedAlignment): Boolean = alignment != OneHandedAlignment.OFF

    fun insets(screenWidthDp: Int, alignment: OneHandedAlignment): OneHandedInsets {
        if (alignment == OneHandedAlignment.OFF || screenWidthDp < 360) {
            return OneHandedInsets(0, 0, screenWidthDp)
        }
        val content = (screenWidthDp * WIDTH_RATIO).toInt().coerceIn(260, screenWidthDp)
        val gutter = (screenWidthDp - content).coerceAtLeast(0)
        return when (alignment) {
            OneHandedAlignment.LEFT -> OneHandedInsets(0, gutter, content)
            OneHandedAlignment.RIGHT -> OneHandedInsets(gutter, 0, content)
            OneHandedAlignment.CENTER -> OneHandedInsets(gutter / 2, gutter - gutter / 2, content)
            OneHandedAlignment.OFF -> OneHandedInsets(0, 0, screenWidthDp)
        }
    }
}

object AccessibilityLabels {
    const val MIN_TOUCH_DP = 40

    fun suggestion(word: String): String = "Suggestion $word"

    fun toolbar(item: ToolbarItem): String = item.description

    fun languageOption(option: LanguageOption, selected: Boolean = false): String =
        if (selected) "${option.description}, selected" else option.description

    fun panel(name: String): String = name

    fun key(key: KeySpec): String = when (key.action) {
        KeyAction.BACKSPACE -> "Backspace"
        KeyAction.ENTER -> "Enter"
        KeyAction.SHIFT -> "Shift"
        KeyAction.SPACE -> "Space"
        KeyAction.LANGUAGE -> "Next language"
        KeyAction.SETTINGS -> "Settings"
        KeyAction.NUMBERS -> "Numbers"
        KeyAction.SYMBOLS -> "Symbols"
        KeyAction.EMOJI -> "Emoji"
        KeyAction.HANDWRITING -> "Handwriting"
        KeyAction.LETTERS -> "Letters"
        KeyAction.RETURN_TO_PREVIOUS -> "Back"
        KeyAction.VOWELS -> "Nepali vowels"
        KeyAction.CONSONANTS -> "Nepali consonants"
        KeyAction.MODE_ENGLISH -> "English"
        KeyAction.MODE_NEPALI -> "Nepali"
        KeyAction.MODE_ROMAN -> "Roman Nepali"
        KeyAction.CLIPBOARD -> "Clipboard"
        KeyAction.DIGIT_SCRIPT -> "Digit script"
        KeyAction.SYMBOL_GROUP -> "More symbols"
        KeyAction.TOOLBAR_MORE -> "More tools"
        KeyAction.TOOLBAR_COLLAPSE -> "Hide extra tools"
        KeyAction.HANDWRITING_UNDO -> "Undo stroke"
        KeyAction.HANDWRITING_CLEAR -> "Clear handwriting"
        KeyAction.HANDWRITING_CONFIRM -> "Confirm handwriting"
        KeyAction.HANDWRITING_CANCEL -> "Cancel handwriting"
        KeyAction.TEXT -> key.label
    }
}

object SuggestionBarState {
    fun display(suggestions: List<String>, typed: String, limit: Int = 3): List<String> {
        val clean = suggestions.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(limit)
        if (clean.isEmpty()) return emptyList()
        if (clean.size == 1 && clean.first().equals(typed, ignoreCase = true) && WordLearningPolicy.isGarbage(typed)) {
            return emptyList()
        }
        return clean
    }

    fun isEmpty(suggestions: List<String>): Boolean = suggestions.isEmpty()

    fun unchanged(previous: List<String>, next: List<String>): Boolean = previous == next

    fun cells(suggestions: List<String>, limit: Int = 3): List<String?> =
        List(limit.coerceAtLeast(0)) { suggestions.getOrNull(it) }
}
