package com.sworupplayz.keyboard

import java.util.Locale

/** Pure visual policy for Gboard-style chrome. Does not change committed text. */
object KeyVisuals {
    fun role(key: KeySpec): KeyVisualRole = when (key.action) {
        KeyAction.TEXT -> if (key.output == " ") KeyVisualRole.SPACE else KeyVisualRole.LETTER
        KeyAction.SPACE -> KeyVisualRole.SPACE
        KeyAction.ENTER -> KeyVisualRole.ENTER
        KeyAction.MODE_ENGLISH,
        KeyAction.MODE_NEPALI,
        KeyAction.MODE_ROMAN,
        KeyAction.SETTINGS -> KeyVisualRole.TOOLBAR
        else -> KeyVisualRole.MODIFIER
    }

    fun showsPreview(key: KeySpec): Boolean =
        key.action == KeyAction.TEXT && key.label.isNotBlank() && key.label.length <= 3

    fun isImeSwitchKey(key: KeySpec): Boolean = key.action == KeyAction.LANGUAGE ||
        key.action == KeyAction.MODE_ENGLISH ||
        key.action == KeyAction.MODE_NEPALI ||
        key.action == KeyAction.MODE_ROMAN

    fun alternates(label: String): List<String> {
        if (label.isEmpty()) return emptyList()
        val mapped = ALTERNATES[label.lowercase(Locale.ENGLISH)].orEmpty()
        if (mapped.isEmpty()) return ALTERNATES[label].orEmpty()
        return if (label.first().isUpperCase()) {
            mapped.map { value ->
                value.replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase(Locale.ENGLISH) else character.toString()
                }
            }
        } else {
            mapped
        }
    }

    fun emojiCategoryIcon(category: EmojiCategory): String = when (category) {
        EmojiCategory.RECENT -> "🕒"
        EmojiCategory.SMILEYS -> "😊"
        EmojiCategory.PEOPLE -> "👋"
        EmojiCategory.ANIMALS -> "🐱"
        EmojiCategory.FOOD -> "🍔"
        EmojiCategory.OBJECTS -> "💡"
        EmojiCategory.SYMBOLS -> "❤"
    }

    fun letterTextSizeSp(label: String, compactScreen: Boolean, role: KeyVisualRole): Float {
        if (role == KeyVisualRole.SPACE) {
            return if (compactScreen) 12f else KeyboardTheme.SPACE_TEXT_SP
        }
        if (role == KeyVisualRole.MODIFIER || role == KeyVisualRole.TOOLBAR) {
            return when {
                label.length > 6 -> if (compactScreen) 10f else 11f
                label.length > 3 -> if (compactScreen) 11f else 12f
                else -> if (compactScreen) 13f else KeyboardTheme.MODIFIER_TEXT_SP
            }
        }
        return when {
            label.length > 6 -> if (compactScreen) 11f else 12f
            label.length > 3 -> if (compactScreen) 13f else 14f
            else -> if (compactScreen) KeyboardTheme.COMPACT_LETTER_TEXT_SP else KeyboardTheme.LETTER_TEXT_SP
        }
    }

    private val ALTERNATES = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "ō"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "c" to listOf("ç"),
        "n" to listOf("ñ"),
        "s" to listOf("ß", "ś", "š"),
        "y" to listOf("ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "." to listOf("…", "•", "·"),
        "," to listOf("'", "‚"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "'" to listOf("\"", "`", "´"),
        "-" to listOf("—", "–", "_"),
        "/" to listOf("\\", "|"),
        "0" to listOf("°"),
        "1" to listOf("¹", "½", "¼"),
        "2" to listOf("²"),
        "3" to listOf("³", "¾"),
        "$" to listOf("¢", "£", "€", "¥", "₹"),
        "क" to listOf("ख", "क्"),
        "ग" to listOf("घ", "ग्"),
        "च" to listOf("छ", "च्"),
        "ज" to listOf("झ", "ज्"),
        "ट" to listOf("ठ", "ट्"),
        "ड" to listOf("ढ", "ड्"),
        "त" to listOf("थ", "त्"),
        "द" to listOf("ध", "द्"),
        "प" to listOf("फ", "प्"),
        "ब" to listOf("भ", "ब्"),
        "स" to listOf("श", "ष", "स्"),
        "।" to listOf("॥", "॰")
    )
}
