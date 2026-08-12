package com.sworupplayz.keyboard

enum class KeyAction {
    TEXT,
    SHIFT,
    BACKSPACE,
    SPACE,
    ENTER,
    SYMBOLS,
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
    MODE_ROMAN
}

data class KeySpec(
    val label: String,
    val action: KeyAction = KeyAction.TEXT,
    val output: String = label,
    val width: Float = 1f
)

object KeyboardLayouts {
    fun english(
        shifted: Boolean,
        language: KeyboardLanguage = KeyboardLanguage.ENGLISH
    ): List<List<KeySpec>> {
        fun letters(value: String): List<KeySpec> = value.map { character ->
            val text = if (shifted) character.uppercaseChar().toString() else character.toString()
            KeySpec(label = text, output = text)
        }

        return listOf(
            letters("qwertyuiop"),
            letters("asdfghjkl"),
            listOf(KeySpec("⇧", KeyAction.SHIFT, width = 1.4f)) +
                letters("zxcvbnm") +
                KeySpec("⌫", KeyAction.BACKSPACE, width = 1.4f),
            commonControls(language, "space")
        )
    }

    fun symbols(language: KeyboardLanguage): List<List<KeySpec>> = listOf(
        "1234567890".map { KeySpec(it.toString()) },
        listOf("@", "#", "\$", "%", "&", "*", "(", ")", "-", "+").map(::KeySpec),
        listOf("_", "/", "\\", ":", ";", "\"", "'", "!", "?").map(::KeySpec) +
            KeySpec("⌫", KeyAction.BACKSPACE, width = 1.2f),
        listOf(
            KeySpec(language.lettersLabel(), KeyAction.LETTERS, width = 1.35f),
            KeySpec(language.nextModeLabel(), KeyAction.LANGUAGE, width = 1.4f),
            KeySpec(",", width = 0.8f),
            KeySpec("space", KeyAction.SPACE, output = " ", width = 2.6f),
            KeySpec(".", width = 0.8f),
            KeySpec("✍", KeyAction.HANDWRITING),
            KeySpec("↵", KeyAction.ENTER, width = 1.3f)
        )
    )

    fun nepaliConsonants(): List<List<KeySpec>> = listOf(
        textKeys("क ख ग घ ङ च छ ज झ ञ"),
        textKeys("ट ठ ड ढ ण त थ द ध न"),
        textKeys("प फ ब भ म य र ल व श"),
        textKeys("ष स ह क्ष त्र ज्ञ श्र रु ॐ") + KeySpec("⌫", KeyAction.BACKSPACE, width = 1.25f),
        nepaliControls("स्वर", KeyAction.VOWELS)
    )

    fun nepaliVowels(): List<List<KeySpec>> = listOf(
        textKeys("अ आ इ ई उ ऊ ए ऐ ओ औ"),
        textKeys("ा ि ी ु ू ृ े ै ो ौ"),
        textKeys("ं ः ँ ् ऽ ॐ । ॥ ॰") + KeySpec("⌫", KeyAction.BACKSPACE, width = 1.25f),
        textKeys("१ २ ३ ४ ५ ६ ७ ८ ९ ०"),
        nepaliControls("व्यञ्जन", KeyAction.CONSONANTS)
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
            KeySpec("नेपाली", KeyAction.MODE_NEPALI),
            KeySpec("Roman", KeyAction.MODE_ROMAN)
        )
    )

    private fun commonControls(language: KeyboardLanguage, spaceLabel: String): List<KeySpec> = listOf(
        KeySpec("?123", KeyAction.SYMBOLS, width = 1.3f),
        KeySpec(language.nextModeLabel(), KeyAction.LANGUAGE, width = 1.4f),
        KeySpec(",", width = 0.8f),
        KeySpec(spaceLabel, KeyAction.SPACE, output = " ", width = 2.6f),
        KeySpec(".", width = 0.8f),
        KeySpec("✍", KeyAction.HANDWRITING),
        KeySpec("↵", KeyAction.ENTER, width = 1.3f)
    )

    private fun textKeys(values: String): List<KeySpec> = values.split(' ').map(::KeySpec)

    private fun nepaliControls(toggleLabel: String, toggleAction: KeyAction): List<KeySpec> = listOf(
        KeySpec("?१२३", KeyAction.SYMBOLS, width = 1.2f),
        KeySpec(KeyboardLanguage.NEPALI.nextModeLabel(), KeyAction.LANGUAGE, width = 1.3f),
        KeySpec(toggleLabel, toggleAction, width = 1.4f),
        KeySpec("खाली", KeyAction.SPACE, output = " ", width = 2.2f),
        KeySpec("।", width = 0.8f),
        KeySpec("✍", KeyAction.HANDWRITING),
        KeySpec("↵", KeyAction.ENTER, width = 1.2f)
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
}
