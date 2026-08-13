package com.sworupplayz.keyboard

/**
 * Shared typing-pipeline rules. KeyboardService stays the orchestrator;
 * these objects never talk to InputConnection or rebuild the keyboard.
 */
object CoreTypingPolicy {
    const val KEY_BOUNCE_MS = KeyTouchPolicy.KEY_BOUNCE_MS
    const val SUGGESTION_BOUNCE_MS = KeyTouchPolicy.KEY_BOUNCE_MS

    fun shouldAcceptKey(
        previousToken: String,
        previousAt: Long,
        token: String,
        now: Long
    ): Boolean = KeyTouchPolicy.shouldAcceptTap(previousToken, previousAt, token, now)

    fun shouldAcceptSuggestion(
        previousSuggestion: String,
        previousAt: Long,
        suggestion: String,
        now: Long
    ): Boolean {
        if (suggestion.isEmpty()) return false
        if (suggestion != previousSuggestion) return true
        return now - previousAt >= SUGGESTION_BOUNCE_MS
    }

    fun allowsPredictionOnMove(): Boolean = false

    fun allowsKeyboardRebuildOnMove(): Boolean = false

    fun allowsVocabularyScanOnMove(): Boolean = false
}

/** Invalidates delayed backspace ticks after the user has moved on. */
class TypingGeneration {
    private var value = 0

    fun current(): Int = value

    fun bump(): Int {
        value += 1
        return value
    }

    fun isCurrent(seen: Int): Boolean = seen == value
}

/**
 * Current-token detection for English, Nepali, Roman, contractions, and
 * hyphenated words. Emails and URLs stay protected whole tokens.
 */
object WordBoundaryPolicy {
    private val DEVANAGARI_RANGE = 0x0900..0x097F
    private val DEVANAGARI_DIGITS = 0x0966..0x096F
    private val DEVANAGARI_STOPS = setOf(0x0964, 0x0965, 0x0970)

    fun isWordChar(character: Char): Boolean {
        if (character.isLetter()) return true
        if (character.isDigit()) return true
        if (character == '\'' || character == '\u2019' || character == '-') return true
        val code = character.code
        return code in DEVANAGARI_RANGE && code !in DEVANAGARI_STOPS
    }

    fun isEnglishWordContinue(character: Char): Boolean =
        character in 'A'..'Z' || character in 'a'..'z' ||
            character == '\'' || character == '\u2019' || character == '-'

    fun isEmojiChar(character: Char): Boolean {
        val code = character.code
        return code in 0x1F300..0x1FAFF ||
            code in 0x1F1E6..0x1F1FF ||
            code in 0x2600..0x27BF ||
            code in 0xFE00..0xFE0F ||
            code == 0x200D ||
            code in 0x1F3FB..0x1F3FF
    }

    fun wordBeforeCursor(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty() || textBeforeCursor.last().isWhitespace()) return ""
        if (!isWordChar(textBeforeCursor.last())) return ""
        var start = textBeforeCursor.length
        while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
        return textBeforeCursor.substring(start)
    }

    fun wordAfterCursor(textAfterCursor: String): String {
        if (textAfterCursor.isEmpty() || !isWordChar(textAfterCursor.first())) return ""
        var end = 0
        while (end < textAfterCursor.length && isWordChar(textAfterCursor[end])) end++
        return textAfterCursor.substring(0, end)
    }

    fun tokenAroundCursor(textBeforeCursor: String, textAfterCursor: String = ""): String =
        wordBeforeCursor(textBeforeCursor) + wordAfterCursor(textAfterCursor)

    fun surroundingToken(textBeforeCursor: String): String =
        SpecialTokenPolicy.tokenAtEnd(textBeforeCursor)

    fun isProtectedSuggestionToken(token: String): Boolean {
        if (token.isEmpty()) return false
        return SpecialTokenPolicy.looksLikeUrl(token) ||
            SpecialTokenPolicy.looksLikeEmail(token) ||
            SpecialTokenPolicy.looksLikeMentionOrHashtag(token)
    }

    fun blocksSuggestionReplacement(textBeforeCursor: String, currentWord: String): Boolean {
        if (currentWord.isEmpty()) return true
        val surrounding = surroundingToken(textBeforeCursor)
        if (isProtectedSuggestionToken(surrounding)) return true
        if (SpecialTokenPolicy.looksLikeNumber(currentWord) && currentWord.any { it == '.' || it == ',' }) {
            return true
        }
        return false
    }

    fun looksLikeContraction(word: String): Boolean {
        val mark = word.indexOf('\'').takeIf { it >= 0 } ?: word.indexOf('\u2019')
        if (mark == null || mark <= 0 || mark >= word.lastIndex) return false
        return word.filterIndexed { index, character -> index != mark }.all { it.isLetter() }
    }

    fun looksLikeHyphenatedWord(word: String): Boolean {
        if ('-' !in word || word.startsWith('-') || word.endsWith('-')) return false
        return word.all { it.isLetter() || it == '-' } && word.any { it.isLetter() }
    }
}

/** Composing-text lifecycle. Never assumes the cursor is at the document end. */
object InputConnectionPolicy {
    const val SUGGESTION_CONTEXT = 80
    const val BACKSPACE_CONTEXT = 64
    const val AFTER_CURSOR_CONTEXT = 24

    fun shouldFinishComposingOnPanelOpen(): Boolean = true

    fun shouldFinishComposingOnLanguageSwitch(): Boolean = true

    fun shouldFinishComposingOnFieldChange(): Boolean = true

    fun shouldInvalidateOnCursorMove(hadComposing: Boolean, movedAway: Boolean): Boolean =
        hadComposing && movedAway

    fun restoresComposingAfterPanelClose(): Boolean = false

    fun suggestionLookbehindLimit(): Int = SUGGESTION_CONTEXT
}

/** Space insertion that never rewrites protected tokens. */
object SpacePolicy {
    fun isRepeatedSpace(textBeforeCursor: String): Boolean =
        textBeforeCursor.endsWith(' ')

    fun blocksSmartEdits(textBeforeCursor: String): Boolean =
        SpecialTokenPolicy.isProtectedContext(textBeforeCursor)

    fun shouldApplyDoubleSpace(
        textBeforeCursor: String,
        elapsedMs: Long,
        enabled: Boolean
    ): Boolean = DoubleSpacePolicy.shouldReplace(textBeforeCursor, elapsedMs, enabled)

    fun replacementForDoubleSpace(language: KeyboardLanguage): String =
        DoubleSpacePolicy.replacement(language)

    fun allowsSpaceAfterEmoji(textBeforeCursor: String): Boolean {
        if (textBeforeCursor.isEmpty()) return true
        val last = textBeforeCursor.codePointBefore(textBeforeCursor.length)
        return Character.isWhitespace(last) ||
            WordBoundaryPolicy.isEmojiCodePoint(last) ||
            Character.isLetterOrDigit(last) ||
            last in 0x0900..0x097F
    }

    fun allowsSpaceAfterNumber(textBeforeCursor: String): Boolean {
        val token = SpecialTokenPolicy.tokenAtEnd(textBeforeCursor)
        return token.isEmpty() || !token.endsWith('.') || !SpecialTokenPolicy.looksLikeNumber(token)
    }

    fun planPunctuation(
        textBeforeCursor: String,
        incoming: String,
        enabled: Boolean = true
    ): SpacingPlan = PunctuationSpacing.plan(textBeforeCursor, incoming, enabled)
}

/**
 * EditorInfo inputType flags. Values match [android.text.InputType] so tests
 * do not need the Android SDK.
 */
object EditorFieldPolicy {
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_CLASS_TEXT = 0x00000001
    const val TYPE_CLASS_NUMBER = 0x00000002
    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_TEXT_VARIATION_URI = 0x00000010
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x00080000

    fun classOf(inputType: Int): Int = inputType and TYPE_MASK_CLASS

    fun variationOf(inputType: Int): Int = inputType and TYPE_MASK_VARIATION

    fun isPasswordField(inputType: Int): Boolean {
        val variation = variationOf(inputType)
        if (classOf(inputType) == TYPE_CLASS_NUMBER) {
            return variation == TYPE_NUMBER_VARIATION_PASSWORD
        }
        return variation == TYPE_TEXT_VARIATION_PASSWORD ||
            variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    fun isEmailField(inputType: Int): Boolean {
        val variation = variationOf(inputType)
        return variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    fun isUriField(inputType: Int): Boolean = variationOf(inputType) == TYPE_TEXT_VARIATION_URI

    fun isSensitiveField(inputType: Int): Boolean = isPasswordField(inputType)

    fun shouldLearn(inputType: Int): Boolean = !isPasswordField(inputType)

    fun shouldSuggest(inputType: Int): Boolean {
        if (isPasswordField(inputType)) return false
        return inputType and TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
    }
}

object LanguageSwitchPolicy {
    fun shouldCommitComposing(from: KeyboardLanguage): Boolean {
        from
        return true
    }

    fun rewritesCommittedText(): Boolean = false

    fun preservesCursor(): Boolean = true

    fun resetShiftOnSwitch(): Boolean = true
}

object CursorMovementPolicy {
    fun shouldInvalidateComposing(movedAway: Boolean): Boolean = movedAway

    fun shouldInvalidateSuggestions(cursorMoved: Boolean, internal: Boolean): Boolean =
        cursorMoved && !internal

    fun preservesCommittedText(): Boolean = true
}

object PanelTransitionPolicy {
    fun shouldFinishComposing(openingPanel: Boolean): Boolean =
        openingPanel && InputConnectionPolicy.shouldFinishComposingOnPanelOpen()

    fun shouldResetShift(): Boolean = true

    fun duplicatesSpacesOnInsert(textBeforeCursor: String, incoming: String): Boolean {
        if (incoming.isEmpty()) return false
        return incoming.first().isWhitespace() && textBeforeCursor.endsWith(' ')
    }
}
