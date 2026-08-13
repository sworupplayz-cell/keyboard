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
        if (alignment == OneHandedAlignment.OFF || screenWidthDp < 320) {
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
    fun suggestion(word: String): String = "Suggestion $word"

    fun toolbar(item: ToolbarItem): String = item.description

    fun languageOption(option: LanguageOption): String = option.description

    const val MIN_TOUCH_DP = 40
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
}
