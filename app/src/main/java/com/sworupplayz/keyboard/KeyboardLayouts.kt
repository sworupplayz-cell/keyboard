package com.sworupplayz.keyboard

enum class KeyAction {
    TEXT,
    SHIFT,
    BACKSPACE,
    SPACE,
    ENTER,
    NUMBERS,
    SYMBOLS,
    SYMBOL_GROUP,
    DIGIT_SCRIPT,
    EMOJI,
    RETURN_TO_PREVIOUS,
    LETTERS,
    LANGUAGE,
    VOWELS,
    CONSONANTS,
    HANDWRITING,
    HANDWRITING_UNDO,
    HANDWRITING_CLEAR,
    HANDWRITING_CONFIRM,
    HANDWRITING_CANCEL,
    MODE_ENGLISH,
    MODE_NEPALI,
    MODE_ROMAN,
    SETTINGS,
    CLIPBOARD,
    TOOLBAR_MORE,
    TOOLBAR_COLLAPSE
}

data class KeySpec(
    val label: String,
    val action: KeyAction = KeyAction.TEXT,
    val output: String = label,
    val width: Float = 1f,
    val compact: Boolean = false
)

object KeyboardLayouts {
    fun english(
        shifted: Boolean,
        language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
        includeNumberRow: Boolean = false
    ): List<List<KeySpec>> {
        fun letters(value: String): List<KeySpec> = value.map { character ->
            val text = if (shifted) character.uppercaseChar().toString() else character.toString()
            KeySpec(label = text, output = text)
        }

        val rows = listOf(
            letters("qwertyuiop"),
            letters("asdfghjkl"),
            listOf(KeySpec("⇧", KeyAction.SHIFT, width = 1.4f)) +
                letters("zxcvbnm") +
                KeySpec("⌫", KeyAction.BACKSPACE, width = 1.4f),
            commonControls(language, language.spaceLabel())
        )
        return if (includeNumberRow) listOf(compactNumberRow("1234567890")) + rows else rows
    }

    fun numbers(
        language: KeyboardLanguage,
        digitScript: DigitScript = DigitScript.defaultFor(language)
    ): List<List<KeySpec>> {
        val digits = digitScript.digits().map { it.toString() }
        return listOf(
            listOf(digits[0], digits[1], digits[2], "+", "−", "(").map(::KeySpec),
            listOf(digits[3], digits[4], digits[5], "×", "÷", ")").map(::KeySpec),
            listOf(digits[6], digits[7], digits[8], "=", "%", "₹").map(::KeySpec),
            listOf(".", digits[9], ",", "$", "€", "£").map(::KeySpec),
            listOf(
                KeySpec(language.lettersLabel(), KeyAction.LETTERS, width = 1.3f),
                KeySpec("#+=", KeyAction.SYMBOLS, width = 1.15f),
                KeySpec(digitScript.switchLabel(), KeyAction.DIGIT_SCRIPT),
                KeySpec("😊", KeyAction.EMOJI),
                KeySpec(language.spaceLabel(), KeyAction.SPACE, output = " ", width = 2.2f),
                KeySpec("⌫", KeyAction.BACKSPACE, width = 1.15f),
                KeySpec("↵", KeyAction.ENTER, width = 1.2f)
            )
        )
    }

    fun symbols(
        language: KeyboardLanguage,
        group: SymbolGroup = SymbolGroup.COMMON,
        recent: List<String> = emptyList()
    ): List<List<KeySpec>> {
        val pages = when (group) {
            SymbolGroup.COMMON -> listOf(
                "1234567890".map { KeySpec(it.toString()) },
                listOf(".", ",", "?", "!", "'", "\"", "@", "#", "\$", "%").map(::KeySpec),
                listOf("&", "*", "(", ")", "-", "+", "=", "/", ":", ";").map(::KeySpec),
                listOf("_", "\\", "[", "]", "{", "}", "<", ">", "€", "₹").map(::KeySpec)
            )
            SymbolGroup.MATH -> listOf(
                listOf("+", "−", "×", "÷", "=", "≠", "<", ">", "≤", "≥").map(::KeySpec),
                listOf("±", "%", "√", "∞", "≈", "^", "π", "∑", "∆", "°").map(::KeySpec),
                listOf("(", ")", "[", "]", "{", "}", ",", ".", ":", ";").map(::KeySpec)
            )
            SymbolGroup.MORE -> listOf(
                listOf("\$", "€", "£", "¥", "₹", "₨", "¢", "₩", "₦", "₱").map(::KeySpec),
                listOf("@", "#", "&", "*", "/", "\\", "|", "~", "^", "`").map(::KeySpec),
                listOf("©", "®", "™", "§", "¶", "•", "…", "†", "‡", "⁂").map(::KeySpec)
            )
        }
        val recentRow = recent.take(10).map(::KeySpec).takeIf { it.isNotEmpty() }.orEmpty()
        val controls = listOf(
            KeySpec(language.lettersLabel(), KeyAction.LETTERS, width = 1.35f),
            KeySpec("123", KeyAction.NUMBERS),
            KeySpec(group.next().title, KeyAction.SYMBOL_GROUP),
            KeySpec("😊", KeyAction.EMOJI),
            KeySpec(language.spaceLabel(), KeyAction.SPACE, output = " ", width = 2.2f),
            KeySpec("✍", KeyAction.HANDWRITING),
            KeySpec("⌫", KeyAction.BACKSPACE, width = 1.2f),
            KeySpec("↵", KeyAction.ENTER, width = 1.2f)
        )
        return (if (recentRow.isEmpty()) pages else listOf(recentRow) + pages) + listOf(controls)
    }

    fun emojiSearchLetters(): List<List<KeySpec>> = listOf(
        "qwertyuiop".map { KeySpec(it.toString(), compact = true) },
        "asdfghjkl".map { KeySpec(it.toString(), compact = true) },
        "zxcvbnm".map { KeySpec(it.toString(), compact = true) }
    )

    fun nepaliConsonants(includeNumberRow: Boolean = false): List<List<KeySpec>> {
        val rows = listOf(
            textKeys("क ख ग घ ङ च छ ज झ ञ"),
            textKeys("ट ठ ड ढ ण त थ द ध न"),
            textKeys("प फ ब भ म य र ल व श"),
            textKeys("ष स ह क्ष त्र ज्ञ श्र रु ॐ") + KeySpec("⌫", KeyAction.BACKSPACE, width = 1.25f),
            nepaliControls("स्वर", KeyAction.VOWELS)
        )
        return if (includeNumberRow) listOf(compactNumberRow("१२३४५६७८९०")) + rows else rows
    }

    fun nepaliVowels(includeNumberRow: Boolean = true): List<List<KeySpec>> {
        val rows = listOf(
            textKeys("अ आ इ ई उ ऊ ए ऐ ओ औ"),
            textKeys("ा ि ी ु ू ृ े ै ो ौ"),
            textKeys("ं ः ँ ् ऽ ॐ । ॥ ॰") + KeySpec("⌫", KeyAction.BACKSPACE, width = 1.25f),
            nepaliControls("व्यञ्जन", KeyAction.CONSONANTS)
        )
        return if (includeNumberRow) {
            rows.dropLast(1) + listOf(compactNumberRow("१२३४५६७८९०"), rows.last())
        } else {
            rows
        }
    }

    fun navigationControls(numberLabel: String = "123"): List<KeySpec> = listOf(
        KeySpec("EN", KeyAction.MODE_ENGLISH),
        KeySpec("नेपाली", KeyAction.MODE_NEPALI),
        KeySpec("Roman", KeyAction.MODE_ROMAN),
        KeySpec(numberLabel, KeyAction.NUMBERS),
        KeySpec("😊", KeyAction.EMOJI),
        KeySpec("✍", KeyAction.HANDWRITING)
    )

    fun emojiControls(): List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("Back", KeyAction.RETURN_TO_PREVIOUS, width = 1.4f),
            KeySpec("123", KeyAction.NUMBERS),
            KeySpec(" ", KeyAction.SPACE, output = " ", width = 3f),
            KeySpec("⌫", KeyAction.BACKSPACE, width = 1.2f),
            KeySpec("↵", KeyAction.ENTER, width = 1.3f)
        )
    )

    fun handwritingControls(): List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("Undo", KeyAction.HANDWRITING_UNDO),
            KeySpec("Clear", KeyAction.HANDWRITING_CLEAR),
            KeySpec("Confirm", KeyAction.HANDWRITING_CONFIRM, width = 1.3f),
            KeySpec("⌫", KeyAction.BACKSPACE),
            KeySpec("Cancel", KeyAction.HANDWRITING_CANCEL)
        ),
        listOf(
            KeySpec("EN", KeyAction.MODE_ENGLISH),
            KeySpec("नेपाली", KeyAction.MODE_NEPALI, width = 1.2f),
            KeySpec("Roman", KeyAction.MODE_ROMAN),
            KeySpec("123", KeyAction.NUMBERS),
            KeySpec("😊", KeyAction.EMOJI)
        )
    )

    private fun commonControls(language: KeyboardLanguage, spaceLabel: String): List<KeySpec> = listOf(
        KeySpec("?123", KeyAction.NUMBERS, width = 1.3f),
        KeySpec(language.nextModeLabel(), KeyAction.LANGUAGE, width = 1.4f),
        KeySpec(",", width = 0.8f),
        KeySpec(spaceLabel, KeyAction.SPACE, output = " ", width = 2.6f),
        KeySpec(".", width = 0.8f),
        KeySpec("✍", KeyAction.HANDWRITING),
        KeySpec("↵", KeyAction.ENTER, width = 1.3f)
    )

    private fun textKeys(values: String): List<KeySpec> = values.split(' ').map(::KeySpec)

    private fun compactNumberRow(values: String): List<KeySpec> =
        values.map { KeySpec(it.toString(), compact = true) }

    private fun nepaliControls(toggleLabel: String, toggleAction: KeyAction): List<KeySpec> = listOf(
        KeySpec("?१२३", KeyAction.NUMBERS, width = 1.2f),
        KeySpec(KeyboardLanguage.NEPALI.nextModeLabel(), KeyAction.LANGUAGE, width = 1.3f),
        KeySpec(toggleLabel, toggleAction, width = 1.4f),
        KeySpec(KeyboardLanguage.NEPALI.spaceLabel(), KeyAction.SPACE, output = " ", width = 2.2f),
        KeySpec("।", width = 0.8f),
        KeySpec("✍", KeyAction.HANDWRITING),
        KeySpec("↵", KeyAction.ENTER, width = 1.3f)
    )

    private fun KeyboardLanguage.lettersLabel(): String = when (this) {
        KeyboardLanguage.NEPALI -> "कखग"
        KeyboardLanguage.ENGLISH -> "ABC"
        KeyboardLanguage.ROMAN -> "Roman"
    }
}

enum class KeyboardLanguage {
    ENGLISH,
    NEPALI,
    ROMAN;

    fun next(): KeyboardLanguage = when (this) {
        ENGLISH -> NEPALI
        NEPALI -> ROMAN
        ROMAN -> ENGLISH
    }

    fun nextModeLabel(): String = when (this) {
        ENGLISH -> "नेपाली"
        NEPALI -> "Roman"
        ROMAN -> "EN"
    }

    fun spaceLabel(): String = when (this) {
        ENGLISH -> "English"
        NEPALI -> "नेपाली"
        ROMAN -> "Roman"
    }
}
