package com.sworupplayz.keyboard

/** Compact Gboard-style tool strip that sits above suggestions. */
enum class ToolbarPresentation { COLLAPSED, EXPANDED }

enum class ToolbarAction {
    EMOJI,
    CLIPBOARD,
    LANGUAGE,
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
    var configuration: ToolbarConfiguration = ToolbarConfiguration.defaults()

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

    fun maybeAutoCollapse() {
        if (configuration.autoCollapse) collapse()
    }

    fun items(
        language: KeyboardLanguage,
        currentAction: ToolbarAction? = null,
        screenWidthDp: Int = 360
    ): List<ToolbarItem> {
        val maxItems = KeyboardUiMetrics.maxToolbarItems(screenWidthDp)
        val actions = if (isExpanded) {
            expandedActions(maxItems)
        } else {
            configuration.visibleCollapsed(maxItems)
        }
        return actions.distinct().map { action ->
            describe(action, language).copy(
                selected = action == currentAction || isLanguageSelected(action, language)
            )
        }
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
        ToolbarAction.SETTINGS,
        ToolbarAction.CLIPBOARD
    )

    private fun expandedActions(maxItems: Int): List<ToolbarAction> {
        val overflow = configuration.overflow(maxItems)
        val extras = listOf(
            ToolbarAction.NUMBERS,
            ToolbarAction.SYMBOLS,
            ToolbarAction.HANDWRITING,
            ToolbarAction.MODE_ENGLISH,
            ToolbarAction.MODE_NEPALI,
            ToolbarAction.MODE_ROMAN
        )
        return (overflow + extras + ToolbarAction.COLLAPSE).distinct()
    }

    private fun describe(action: ToolbarAction, language: KeyboardLanguage): ToolbarItem = when (action) {
        ToolbarAction.EMOJI -> ToolbarItem(action, "😊", "Emoji")
        ToolbarAction.CLIPBOARD -> ToolbarItem(action, "📋", "Clipboard")
        ToolbarAction.LANGUAGE -> ToolbarItem(
            action,
            LanguageSwitcher.toolbarLabel(language),
            LanguageSwitcher.toolbarDescription(language)
        )
        ToolbarAction.SETTINGS -> ToolbarItem(action, "⚙", "Settings")
        ToolbarAction.MORE -> ToolbarItem(action, "⋯", "More tools")
        ToolbarAction.COLLAPSE -> ToolbarItem(action, "▴", "Hide extra tools")
        ToolbarAction.NUMBERS -> ToolbarItem(action, "123", "Numbers")
        ToolbarAction.SYMBOLS -> ToolbarItem(action, "#+=", "Symbols")
        ToolbarAction.HANDWRITING -> ToolbarItem(action, "✍", "Handwriting")
        ToolbarAction.MODE_ENGLISH -> ToolbarItem(action, "EN", "English")
        ToolbarAction.MODE_NEPALI -> ToolbarItem(action, "नेपाली", "Nepali")
        ToolbarAction.MODE_ROMAN -> ToolbarItem(action, "Roman", "Roman Nepali")
    }

    private fun isLanguageSelected(action: ToolbarAction, language: KeyboardLanguage): Boolean =
        (action == ToolbarAction.LANGUAGE) ||
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
            KeyAction.LANGUAGE -> ToolbarAction.LANGUAGE
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
            ToolbarAction.LANGUAGE -> KeyAction.LANGUAGE
        }
    }
}
