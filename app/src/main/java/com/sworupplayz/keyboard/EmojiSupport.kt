package com.sworupplayz.keyboard

enum class EmojiCategory(val label: String) {
    RECENT("◷"),
    SMILEYS("🙂"),
    PEOPLE("👋"),
    ANIMALS("🐾"),
    FOOD("🍎"),
    OBJECTS("💡"),
    SYMBOLS("♥")
}

/** Small Unicode-only catalog. No image assets or downloaded data are used. */
object EmojiCatalog {
    private val entries = mapOf(
        EmojiCategory.SMILEYS to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "😊",
            "🙂", "😍", "😘", "😎", "🤔", "😢", "😭", "😡"
        ),
        EmojiCategory.PEOPLE to listOf(
            "👋", "👍", "👎", "🙏", "👏", "🙌", "👌", "✌",
            "🤞", "💪", "🤝", "👀", "🧑", "👩", "👨", "👶"
        ),
        EmojiCategory.ANIMALS to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐦"
        ),
        EmojiCategory.FOOD to listOf(
            "🍎", "🍌", "🍇", "🍉", "🍓", "🍒", "🥭", "🍍",
            "🍔", "🍕", "🍟", "🍚", "🍜", "🍰", "☕", "🍺"
        ),
        EmojiCategory.OBJECTS to listOf(
            "💡", "📱", "💻", "⌚", "📷", "🎧", "🎵", "🎮",
            "🚗", "✈", "🚲", "🏠", "🎁", "📌", "✏", "🔑"
        ),
        EmojiCategory.SYMBOLS to listOf(
            "❤", "💛", "💚", "💙", "💜", "💔", "✨", "⭐",
            "🔥", "💯", "✅", "❌", "⚠", "❓", "❗", "♻"
        )
    )

    fun emojis(category: EmojiCategory, recent: List<String> = emptyList()): List<String> =
        if (category == EmojiCategory.RECENT) recent else entries[category].orEmpty()

    fun contains(emoji: String): Boolean = entries.values.any { emoji in it }
}

class RecentEmojiList(
    initial: List<String> = emptyList(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val recent = ArrayList<String>(limit)

    init {
        initial.asSequence()
            .filter(EmojiCatalog::contains)
            .distinct()
            .take(limit)
            .forEach(recent::add)
    }

    fun record(emoji: String): String {
        if (!EmojiCatalog.contains(emoji)) return emoji
        recent.remove(emoji)
        recent.add(0, emoji)
        while (recent.size > limit) recent.removeAt(recent.lastIndex)
        return emoji
    }

    fun values(): List<String> = recent.toList()

    companion object {
        const val DEFAULT_LIMIT = 12
    }
}
