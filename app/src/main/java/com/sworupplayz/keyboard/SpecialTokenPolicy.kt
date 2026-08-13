package com.sworupplayz.keyboard

/** Detects URLs, emails, mentions, hashtags, and numeric tokens so smart edits stay conservative. */
object SpecialTokenPolicy {
    fun isProtectedContext(textBeforeCursor: String): Boolean {
        val token = tokenAtEnd(textBeforeCursor)
        if (token.isEmpty()) return false
        return looksLikeUrl(token) || looksLikeEmail(token) || looksLikeMentionOrHashtag(token) ||
            looksLikeNumber(token)
    }

    fun blocksCapitalization(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.endsWith('@') || trimmed.endsWith('#')) return true
        return isProtectedContext(trimmed)
    }

    fun looksLikeUrl(token: String): Boolean {
        val value = token.lowercase()
        return value.startsWith("http://") || value.startsWith("https://") ||
            value.startsWith("www.") || ('.' in value && '/' in value)
    }

    fun looksLikeEmail(token: String): Boolean {
        val at = token.indexOf('@')
        return at > 0 && token.indexOf('.', at) > at + 1 && ' ' !in token
    }

    fun looksLikeMentionOrHashtag(token: String): Boolean =
        (token.startsWith('@') || token.startsWith('#')) && token.length > 1

    fun looksLikeNumber(token: String): Boolean {
        if (token.isEmpty()) return false
        var sawDigit = false
        token.forEach { character ->
            when {
                character.isDigit() || character.code in DEVANAGARI_DIGITS -> sawDigit = true
                character == '.' || character == ',' || character == '-' || character == '+' -> Unit
                else -> return false
            }
        }
        return sawDigit
    }

    fun tokenAtEnd(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        var end = textBeforeCursor.length
        while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
        if (end == 0) return ""
        var start = end
        while (start > 0 && !textBeforeCursor[start - 1].isWhitespace()) start--
        return textBeforeCursor.substring(start, end)
    }

    private val DEVANAGARI_DIGITS = 0x0966..0x096F
}
