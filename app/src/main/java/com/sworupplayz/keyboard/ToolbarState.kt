package com.sworupplayz.keyboard

/** Compact Gboard-style tool strip that sits above suggestions. */
enum class ToolbarPresentation { COLLAPSED, EXPANDED }

enum class ToolbarAction {
    EMOJI,
    CLIPBOARD,
    SETTINGS,
    MORE,
    COLLAPSE,
    NUMBERS,
    SYMBOLS,
    HANDWRITING,
    MODE_ENGLISH,
    MODE_NEPALI,
    MODE_ROMAN
}

data class ToolbarItem(
    val action: ToolbarAction,
    val label: String,
    val description: String,
    val selected: Boolean = false
)

enum class ToolbarBackResult {
    NONE,
    COLLAPSED_TOOLS,
    CLOSED_PANEL
}

/**
 * Pure toolbar state. KeyboardService only asks it what to show and how Back
 * should move between collapsed, expanded, and temporary panels.
 */
class ToolbarController {
    var presentation: ToolbarPresentation = ToolbarPresentation.COLLAPSED
        private set

    val isExpanded: Boolean get() = presentation == ToolbarPresentation.EXPANDED

    fun collapse() {
        presentation = ToolbarPresentation.COLLAPSED
    }

    fun expand() {
        presentation = ToolbarPresentation.EXPANDED
    }

    fun toggleMore() {
        presentation = if (isExpanded) ToolbarPresentation.COLLAPSED else ToolbarPresentation.EXPANDED
    }

    fun reset() {
        presentation = ToolbarPresentation.COLLAPSED
    }

    fun items(
        language: KeyboardLanguage,
        currentAction: ToolbarAction? = null
    ): List<ToolbarItem> {
        val expanded = isExpanded
        val raw = if (expanded) expandedItems(language) else collapsedItems()
        return raw
            .distinctBy { it.action }
            .map { item -> item.copy(selected = item.action == currentAction || isLanguageSelected(item.action, language)) }
    }

    fun consumeBack(panelOpen: Boolean): ToolbarBackResult {
        if (isExpanded) {
            collapse()
            return ToolbarBackResult.COLLAPSED_TOOLS
        }
        if (panelOpen) return ToolbarBackResult.CLOSED_PANEL
        return ToolbarBackResult.NONE
    }

    fun routesToExistingPanel(action: ToolbarAction): Boolean = action in setOf(
        ToolbarAction.EMOJI,
        ToolbarAction.NUMBERS,
        ToolbarAction.SYMBOLS,
        ToolbarAction.HANDWRITING,
        ToolbarAction.SETTINGS
    )

    private fun collapsedItems(): List<ToolbarItem> = listOf(
        ToolbarItem(ToolbarAction.EMOJI, "😊", "Emoji"),
        ToolbarItem(ToolbarAction.CLIPBOARD, "📋", "Clipboard"),
        ToolbarItem(ToolbarAction.SETTINGS, "⚙", "Settings"),
        ToolbarItem(ToolbarAction.MORE, "⋯", "More tools")
    )

    private fun expandedItems(language: KeyboardLanguage): List<ToolbarItem> = listOf(
        ToolbarItem(ToolbarAction.NUMBERS, "123", "Numbers"),
        ToolbarItem(ToolbarAction.SYMBOLS, "#+=", "Symbols"),
        ToolbarItem(ToolbarAction.HANDWRITING, "✍", "Handwriting"),
        ToolbarItem(ToolbarAction.MODE_ENGLISH, "EN", "English", selected = language == KeyboardLanguage.ENGLISH),
        ToolbarItem(ToolbarAction.MODE_NEPALI, "नेपाली", "Nepali", selected = language == KeyboardLanguage.NEPALI),
        ToolbarItem(ToolbarAction.MODE_ROMAN, "Roman", "Roman", selected = language == KeyboardLanguage.ROMAN),
        ToolbarItem(ToolbarAction.CLIPBOARD, "📋", "Clipboard"),
        ToolbarItem(ToolbarAction.SETTINGS, "⚙", "Settings"),
        ToolbarItem(ToolbarAction.COLLAPSE, "▴", "Hide extra tools")
    )

    private fun isLanguageSelected(action: ToolbarAction, language: KeyboardLanguage): Boolean =
        (action == ToolbarAction.MODE_ENGLISH && language == KeyboardLanguage.ENGLISH) ||
            (action == ToolbarAction.MODE_NEPALI && language == KeyboardLanguage.NEPALI) ||
            (action == ToolbarAction.MODE_ROMAN && language == KeyboardLanguage.ROMAN)

    companion object {
        fun actionForKey(action: KeyAction): ToolbarAction? = when (action) {
            KeyAction.EMOJI -> ToolbarAction.EMOJI
            KeyAction.CLIPBOARD -> ToolbarAction.CLIPBOARD
            KeyAction.SETTINGS -> ToolbarAction.SETTINGS
            KeyAction.TOOLBAR_MORE -> ToolbarAction.MORE
            KeyAction.TOOLBAR_COLLAPSE -> ToolbarAction.COLLAPSE
            KeyAction.NUMBERS -> ToolbarAction.NUMBERS
            KeyAction.SYMBOLS -> ToolbarAction.SYMBOLS
            KeyAction.HANDWRITING -> ToolbarAction.HANDWRITING
            KeyAction.MODE_ENGLISH -> ToolbarAction.MODE_ENGLISH
            KeyAction.MODE_NEPALI -> ToolbarAction.MODE_NEPALI
            KeyAction.MODE_ROMAN -> ToolbarAction.MODE_ROMAN
            else -> null
        }

        fun keyAction(action: ToolbarAction): KeyAction = when (action) {
            ToolbarAction.EMOJI -> KeyAction.EMOJI
            ToolbarAction.CLIPBOARD -> KeyAction.CLIPBOARD
            ToolbarAction.SETTINGS -> KeyAction.SETTINGS
            ToolbarAction.MORE -> KeyAction.TOOLBAR_MORE
            ToolbarAction.COLLAPSE -> KeyAction.TOOLBAR_COLLAPSE
            ToolbarAction.NUMBERS -> KeyAction.NUMBERS
            ToolbarAction.SYMBOLS -> KeyAction.SYMBOLS
            ToolbarAction.HANDWRITING -> KeyAction.HANDWRITING
            ToolbarAction.MODE_ENGLISH -> KeyAction.MODE_ENGLISH
            ToolbarAction.MODE_NEPALI -> KeyAction.MODE_NEPALI
            ToolbarAction.MODE_ROMAN -> KeyAction.MODE_ROMAN
        }
    }
}
