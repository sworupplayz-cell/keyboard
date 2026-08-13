package com.sworupplayz.keyboard

import java.util.ArrayDeque

/** Small LIFO history used by temporary keyboard panels. */
class PreviousLayoutStack<T> {
    private val entries = ArrayDeque<T>()

    val size: Int get() = entries.size

    fun remember(value: T) {
        entries.addLast(value)
    }

    fun previousOr(fallback: T): T = if (entries.isEmpty()) fallback else entries.removeLast()

    fun clear() {
        entries.clear()
    }
}

enum class PanelNavigationResult {
    NONE,
    CLOSED_OVERLAY,
    COLLAPSED_TOOLS,
    CLOSED_PANEL
}

/**
 * Back hierarchy for the IME. Popups close first. More is chrome overlay, so
 * it still collapses before a panel underneath (same contract as
 * [ToolbarController.consumeBack]). Letters/vowels are not panels.
 */
object PanelNavigation {
    fun isTemporaryPanel(letters: Boolean, vowels: Boolean): Boolean = !letters && !vowels

    fun consume(
        overlayOpen: Boolean,
        toolbarExpanded: Boolean,
        panelOpen: Boolean
    ): PanelNavigationResult = when {
        overlayOpen -> PanelNavigationResult.CLOSED_OVERLAY
        toolbarExpanded -> PanelNavigationResult.COLLAPSED_TOOLS
        panelOpen -> PanelNavigationResult.CLOSED_PANEL
        else -> PanelNavigationResult.NONE
    }
}
