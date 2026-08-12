package com.sworupplayz.keyboard

import java.util.Locale

/** Snapshot of text immediately before the cursor. Pure so IME and tests share one parser. */
data class EditorContext(
    val textBeforeCursor: String,
    val currentWord: String,
    val previousWord: String?,
    val shouldCapitalize: Boolean,
    val endsWithSpace: Boolean
) {
    companion object {
        fun from(textBeforeCursor: String, composingWord: String = ""): EditorContext {
            val current = composingWord.ifEmpty { wordAtEnd(textBeforeCursor) }
            return EditorContext(
                textBeforeCursor = textBeforeCursor,
                currentWord = current,
                previousWord = previousWord(textBeforeCursor, current),
                shouldCapitalize = CapitalizationPolicy.shouldCapitalize(textBeforeCursor),
                endsWithSpace = textBeforeCursor.endsWith(' ')
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

        private fun isWordChar(character: Char): Boolean =
            character.isLetterOrDigit() || character == '\'' || character.code in DEVANAGARI_RANGE

        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private val SENTENCE_PUNCTUATION = setOf('.', '!', '?', '।', '…')
    }
}

object CapitalizationPolicy {
    private val SENTENCE_END = setOf('.', '!', '?', '।', '…')

    fun shouldCapitalize(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.last() in SENTENCE_END
    }

    fun applyToWord(word: String, textBeforeCursor: String): String {
        if (word.isEmpty() || !shouldCapitalize(textBeforeCursor)) return word
        val first = word.first()
        if (!first.isLetter() || first.isUpperCase()) return word
        return word.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }

    fun applyIncomingLetter(letter: String, textBeforeCursor: String): String {
        if (letter.length != 1 || !letter[0].isLowerCase()) return letter
        return if (shouldCapitalize(textBeforeCursor)) letter.uppercase(Locale.ENGLISH) else letter
    }
}

data class SpacingPlan(
    val deleteBefore: Int = 0,
    val insertLeadingSpace: Boolean = false,
    val text: String
)

/** Conservative Gboard-like punctuation spacing. Never rewrites already committed words. */
object PunctuationSpacing {
    private val ATTACH_LEFT = setOf('.', ',', '!', '?', ';', ':', ')', ']', '}', '…', '।', '॥', '\'', '%')
    private val SENTENCE_END = setOf('.', '!', '?', '।', '…')

    fun plan(textBeforeCursor: String, incoming: String): SpacingPlan {
        if (incoming.length == 1 && incoming[0] in ATTACH_LEFT && textBeforeCursor.endsWith(' ')) {
            return SpacingPlan(deleteBefore = 1, text = incoming)
        }
        if (incoming.length == 1 && incoming[0].isLetter() && textBeforeCursor.isNotEmpty()) {
            val last = textBeforeCursor.last()
            if (last in SENTENCE_END) {
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
    fun keepAsEnglish(
        word: String,
        previousWord: String?,
        keepEnglish: Set<String>,
        hasNepaliEntry: Boolean
    ): Boolean {
        val normalized = word.trim().lowercase(Locale.ENGLISH)
        if (normalized.isEmpty()) return false
        if (normalized in keepEnglish && !hasNepaliEntry) return true
        if (!hasNepaliEntry) return false
        val previous = previousWord?.trim()?.lowercase(Locale.ENGLISH) ?: return false
        return normalized in keepEnglish && previous in keepEnglish
    }

    fun looksEnglish(word: String): Boolean =
        word.isNotEmpty() && word.all { it in 'A'..'Z' || it in 'a'..'z' || it == '\'' }
}
