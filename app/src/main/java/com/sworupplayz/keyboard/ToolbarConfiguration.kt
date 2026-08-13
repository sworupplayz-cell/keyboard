package com.sworupplayz.keyboard

/**
 * Persisted toolbar layout. Tools are stored by stable enum IDs, never by view
 * positions, so order survives theme and density changes.
 */
data class ToolbarConfiguration(
    val order: List<ToolbarAction> = DEFAULT_ORDER,
    val enabled: Set<ToolbarAction> = DEFAULT_ORDER.toSet(),
    val alwaysVisible: Boolean = true,
    val autoCollapse: Boolean = true
) {
    fun isEnabled(action: ToolbarAction): Boolean = action in enabled

    fun toggle(action: ToolbarAction): ToolbarConfiguration {
        if (action !in CUSTOMIZABLE) return this
        val next = enabled.toMutableSet()
        if (action in next) next.remove(action) else next.add(action)
        if (next.isEmpty()) next += ToolbarAction.MORE
        return copy(enabled = next)
    }

    fun move(action: ToolbarAction, delta: Int): ToolbarConfiguration {
        val working = order.toMutableList()
        val index = working.indexOf(action)
        val target = index + delta
        if (index < 0 || target !in working.indices) return this
        working.removeAt(index)
        working.add(target, action)
        return copy(order = normalizeOrder(working))
    }

    fun serializeOrder(): String = order.joinToString(",") { it.name }

    fun serializeEnabled(): String = enabled.joinToString(",") { it.name }

    fun visibleCollapsed(maxItems: Int): List<ToolbarAction> {
        val chosen = order.filter { it in enabled && it != ToolbarAction.COLLAPSE }
        if (chosen.isEmpty()) return listOf(ToolbarAction.MORE)
        if (chosen.size <= maxItems) return chosen
        val keep = chosen.filter { it != ToolbarAction.MORE }.take((maxItems - 1).coerceAtLeast(1))
        return keep + ToolbarAction.MORE
    }

    fun overflow(maxItems: Int): List<ToolbarAction> {
        val shown = visibleCollapsed(maxItems).toSet()
        return CUSTOMIZABLE.filter { it !in shown && it != ToolbarAction.MORE }
    }

    companion object {
        val DEFAULT_ORDER = listOf(
            ToolbarAction.EMOJI,
            ToolbarAction.CLIPBOARD,
            ToolbarAction.LANGUAGE,
            ToolbarAction.SETTINGS,
            ToolbarAction.MORE
        )

        val CUSTOMIZABLE = listOf(
            ToolbarAction.EMOJI,
            ToolbarAction.CLIPBOARD,
            ToolbarAction.LANGUAGE,
            ToolbarAction.NUMBERS,
            ToolbarAction.SYMBOLS,
            ToolbarAction.HANDWRITING,
            ToolbarAction.SETTINGS,
            ToolbarAction.MORE
        )

        fun defaults(): ToolbarConfiguration = ToolbarConfiguration()

        fun fromSerialized(
            orderValue: String?,
            enabledValue: String?,
            alwaysVisible: Boolean = true,
            autoCollapse: Boolean = true
        ): ToolbarConfiguration {
            val parsedOrder = parseActions(orderValue)
            val parsedEnabled = parseActions(enabledValue).toSet()
            return ToolbarConfiguration(
                order = normalizeOrder(if (parsedOrder.isEmpty()) DEFAULT_ORDER else parsedOrder),
                enabled = if (parsedEnabled.isEmpty()) DEFAULT_ORDER.toSet() else parsedEnabled.filter { it in CUSTOMIZABLE }.toSet(),
                alwaysVisible = alwaysVisible,
                autoCollapse = autoCollapse
            )
        }

        private fun parseActions(value: String?): List<ToolbarAction> =
            value.orEmpty().split(',').mapNotNull { token ->
                ToolbarAction.entries.firstOrNull { it.name == token.trim() }
            }.filter { it in CUSTOMIZABLE || it == ToolbarAction.LANGUAGE }

        private fun normalizeOrder(order: List<ToolbarAction>): List<ToolbarAction> {
            val seen = LinkedHashSet<ToolbarAction>()
            order.filter { it in CUSTOMIZABLE }.forEach { seen += it }
            CUSTOMIZABLE.forEach { seen += it }
            return seen.toList()
        }
    }
}
