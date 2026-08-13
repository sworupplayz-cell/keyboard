package com.sworupplayz.keyboard

import java.util.Locale

/** Snapshot of text immediately before the cursor. Pure so IME and tests share one parser. */
data class EditorContext(
    val textBeforeCursor: String,
    val currentWord: String,
    val previousWord: String?,
    val shouldCapitalize: Boolean,
    val endsWithSpace: Boolean,
    val previousTwoWords: String? = null
) {
    companion object {
        fun from(textBeforeCursor: String, composingWord: String = ""): EditorContext {
            val current = composingWord.ifEmpty { wordAtEnd(textBeforeCursor) }
            val previous = previousWord(textBeforeCursor, current)
            return EditorContext(
                textBeforeCursor = textBeforeCursor,
                currentWord = current,
                previousWord = previous,
                shouldCapitalize = CapitalizationPolicy.shouldCapitalize(textBeforeCursor),
                endsWithSpace = textBeforeCursor.endsWith(' '),
                previousTwoWords = previousTwoWords(textBeforeCursor, current, previous)
            )
        }

        fun wordAtEnd(textBeforeCursor: String): String {
            if (textBeforeCursor.isEmpty() || textBeforeCursor.last().isWhitespace()) return ""
            var start = textBeforeCursor.length
            while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
            return textBeforeCursor.substring(start)
        }

        fun previousWord(textBeforeCursor: String, currentWord: String = wordAtEnd(textBeforeCursor)): String? {
            var end = textBeforeCursor.length
            if (currentWord.isNotEmpty() && textBeforeCursor.endsWith(currentWord)) {
                end -= currentWord.length
            }
            while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
            if (end == 0) return null
            var start = end
            while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
            val word = textBeforeCursor.substring(start, end)
            return word.takeIf { it.isNotEmpty() && !it.all { character -> character in SENTENCE_PUNCTUATION } }
        }

        fun previousTwoWords(
            textBeforeCursor: String,
            currentWord: String = wordAtEnd(textBeforeCursor),
            previous: String? = previousWord(textBeforeCursor, currentWord)
        ): String? {
            val last = previous ?: return null
            var end = textBeforeCursor.length
            if (currentWord.isNotEmpty() && textBeforeCursor.endsWith(currentWord)) {
                end -= currentWord.length
            }
            while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
            end -= last.length
            while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
            if (end <= 0) return null
            var start = end
            while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
            val older = textBeforeCursor.substring(start, end)
            if (older.isEmpty() || older.all { character -> character in SENTENCE_PUNCTUATION }) return null
            return "$older $last"
        }

        private fun isWordChar(character: Char): Boolean =
            character.isLetterOrDigit() || character == '\'' || character.code in DEVANAGARI_RANGE

        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private val SENTENCE_PUNCTUATION = setOf('.', '!', '?', '।', '…')
    }
}

object CapitalizationPolicy {
    private val SENTENCE_END = setOf('.', '!', '?', '।', '…')
    private val ABBREVIATIONS = setOf("e.g", "i.e", "vs", "etc", "mr", "mrs", "ms", "dr", "prof")

    fun shouldCapitalize(textBeforeCursor: String, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        if (SpecialTokenPolicy.blocksCapitalization(textBeforeCursor)) return false
        if (endsWithAbbreviation(textBeforeCursor)) return false
        var index = textBeforeCursor.lastIndex
        while (index >= 0 && textBeforeCursor[index] == ' ') index--
        if (index < 0) return true
        val last = textBeforeCursor[index]
        if (last == '\n' || last == '\r') return true
        return last in SENTENCE_END
    }

    fun applyToWord(word: String, textBeforeCursor: String, enabled: Boolean = true): String {
        if (word.isEmpty() || !shouldCapitalize(textBeforeCursor, enabled)) return word
        val first = word.first()
        if (!first.isLetter() || first.isUpperCase()) return word
        return word.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }

    fun applyIncomingLetter(letter: String, textBeforeCursor: String, enabled: Boolean = true): String {
        if (!enabled || letter.length != 1 || !letter[0].isLowerCase()) return letter
        return if (shouldCapitalize(textBeforeCursor, enabled)) letter.uppercase(Locale.ENGLISH) else letter
    }

    private fun endsWithAbbreviation(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd()
        if (!trimmed.endsWith('.')) return false
        val token = SpecialTokenPolicy.tokenAtEnd(trimmed.dropLast(1)).lowercase(Locale.ENGLISH)
        return token in ABBREVIATIONS
    }
}

data class SpacingPlan(
    val deleteBefore: Int = 0,
    val insertLeadingSpace: Boolean = false,
    val insertTrailingSpace: Boolean = false,
    val text: String
)

/** Conservative Gboard-like punctuation spacing. Never rewrites already committed words. */
object PunctuationSpacing {
    private val ATTACH_LEFT = setOf('.', ',', '!', '?', ';', ':', ')', ']', '}', '…', '।', '॥', '\'', '%')
    private val TRAILING_SPACE = setOf('.', ',', '!', '?', ';', ':', '%', '।')
    private val SENTENCE_END = setOf('.', '!', '?', '।', '…')

    fun plan(textBeforeCursor: String, incoming: String, enabled: Boolean = true): SpacingPlan {
        if (!enabled || incoming.isEmpty()) return SpacingPlan(text = incoming)
        val mark = incoming[0]
        if (incoming.length == 1 && mark in ATTACH_LEFT) {
            val protectedToken = SpecialTokenPolicy.isProtectedContext(textBeforeCursor.trimEnd())
            val decimal = mark == '.' && SpecialTokenPolicy.looksLikeNumber(SpecialTokenPolicy.tokenAtEnd(textBeforeCursor))
            val deleteBefore = if (textBeforeCursor.endsWith(' ') && !protectedToken && !decimal) 1 else 0
            val trailing = mark in TRAILING_SPACE && !protectedToken && !decimal
            return SpacingPlan(deleteBefore = deleteBefore, insertTrailingSpace = trailing, text = incoming)
        }
        if (incoming.length == 1 && incoming[0].isLetter() && textBeforeCursor.isNotEmpty()) {
            val last = textBeforeCursor.last()
            if (last in SENTENCE_END && !SpecialTokenPolicy.isProtectedContext(textBeforeCursor)) {
                return SpacingPlan(insertLeadingSpace = true, text = incoming)
            }
        }
        return SpacingPlan(text = incoming)
    }

    fun needsSpaceBeforeWord(textBeforeCursor: String): Boolean =
        textBeforeCursor.isNotEmpty() && textBeforeCursor.last() in SENTENCE_END
}

object CorrectionPolicy {
    fun shouldAutoReplace(typed: String, candidate: String): Boolean = false

    fun shouldOfferTypo(typed: String, candidate: String): Boolean {
        if (typed.equals(candidate, ignoreCase = true)) return true
        if (typed.length < 3 || candidate.length < 3) return false
        val distance = TypoCorrector.damerauDistance(typed.lowercase(Locale.ENGLISH), candidate.lowercase(Locale.ENGLISH))
        return distance in 1..2 && TypoCorrector.isConservativeTypo(typed, candidate)
    }
}

object MixedLanguagePolicy {
    private val STRONG_ENGLISH_PREVIOUS = setOf(
        "i", "am", "is", "are", "was", "were", "the", "a", "an", "to", "of", "and",
        "you", "we", "they", "my", "your", "this", "that", "it"
    )

    fun keepAsEnglish(
        word: String,
        previousWord: String?,
        keepEnglish: Set<String>,
        hasNepaliEntry: Boolean,
        originalWord: String? = null
    ): Boolean {
        val normalized = word.trim().lowercase(Locale.ENGLISH)
        if (normalized.isEmpty()) return false
        val original = originalWord ?: word
        val previous = previousWord?.trim()?.lowercase(Locale.ENGLISH)
        if (isCapitalizedEnglish(original) && (previous == null || isEnglishContext(previous, keepEnglish))) {
            return true
        }
        if (normalized in keepEnglish && !hasNepaliEntry) return true
        if (!hasNepaliEntry) return false
        if (previous == null) return false
        return normalized in keepEnglish && (previous in keepEnglish || previous in STRONG_ENGLISH_PREVIOUS)
    }

    fun looksEnglish(word: String): Boolean =
        word.isNotEmpty() && word.all { it in 'A'..'Z' || it in 'a'..'z' || it == '\'' }

    private fun isCapitalizedEnglish(word: String): Boolean =
        looksEnglish(word) && word.first().isUpperCase()

    private fun isEnglishContext(previous: String, keepEnglish: Set<String>): Boolean =
        previous in keepEnglish || previous in STRONG_ENGLISH_PREVIOUS
}
