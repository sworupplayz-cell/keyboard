package com.sworupplayz.keyboard

enum class DigitScript {
    LATIN,
    DEVANAGARI;

    fun toggle(): DigitScript = if (this == LATIN) DEVANAGARI else LATIN

    fun digits(): String = when (this) {
        LATIN -> "1234567890"
        DEVANAGARI -> "१२३४५६७८९०"
    }

    fun switchLabel(): String = when (this) {
        LATIN -> "१२३"
        DEVANAGARI -> "123"
    }

    companion object {
        fun defaultFor(language: KeyboardLanguage): DigitScript =
            if (language == KeyboardLanguage.NEPALI) DEVANAGARI else LATIN
    }
}

enum class SymbolGroup(val title: String) {
    COMMON("Common"),
    MATH("Math"),
    MORE("More");

    fun next(): SymbolGroup = when (this) {
        COMMON -> MATH
        MATH -> MORE
        MORE -> COMMON
    }
}

object SymbolCatalog {
    val punctuation = listOf(".", ",", "!", "?", ":", ";", "'", "\"", "-", "_", "…", "•")
    val brackets = listOf("(", ")", "[", "]", "{", "}")
    val math = listOf("+", "−", "×", "÷", "=", "≠", "<", ">", "≤", "≥", "±", "%", "√", "∞", "≈")
    val currency = listOf("$", "€", "£", "¥", "₹", "₨")
    val technical = listOf("@", "#", "&", "*", "/", "\\", "|", "~", "^", "`")
    val extra = listOf("©", "®", "™", "°", "§", "¶", "†", "•")

    fun glyphs(group: SymbolGroup): List<String> = when (group) {
        SymbolGroup.COMMON -> punctuation + brackets + listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "/", "_")
        SymbolGroup.MATH -> math + brackets
        SymbolGroup.MORE -> currency + technical + extra
    }

    fun contains(symbol: String): Boolean =
        symbol in punctuation || symbol in brackets || symbol in math ||
            symbol in currency || symbol in technical || symbol in extra

    fun isRecordable(symbol: String): Boolean =
        symbol.isNotEmpty() && !symbol.all { it.isDigit() || it.code in DEVANAGARI_DIGITS } && contains(symbol)

    private val DEVANAGARI_DIGITS = 0x0966..0x096F
}

class RecentSymbolStore(
    initial: List<String> = emptyList(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val recent = ArrayList<String>(limit)

    init {
        initial.asSequence()
            .filter(SymbolCatalog::isRecordable)
            .distinct()
            .take(limit)
            .forEach(recent::add)
    }

    fun record(symbol: String): Boolean {
        if (!SymbolCatalog.isRecordable(symbol)) return false
        recent.remove(symbol)
        recent.add(0, symbol)
        while (recent.size > limit) recent.removeAt(recent.lastIndex)
        return true
    }

    fun clear() {
        recent.clear()
    }

    fun values(): List<String> = recent.toList()

    fun serialize(): String = recent.joinToString("\n")

    companion object {
        const val DEFAULT_LIMIT = 24

        fun fromSerialized(value: String?, limit: Int = DEFAULT_LIMIT): RecentSymbolStore =
            RecentSymbolStore(
                value.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
                limit
            )
    }
}
