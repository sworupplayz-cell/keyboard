package com.sworupplayz.keyboard

/**
 * Splits mixed Roman text into convertible words and preserved tokens.
 * URLs, mentions, hashtags, numbers, emoji, punctuation, and Devanagari stay intact.
 */
object RomanTextProcessor {
    data class Segment(val text: String, val convertible: Boolean)

    fun convert(
        text: String,
        previousWord: String? = null,
        convertWord: (word: String, previous: String?) -> String
    ): String {
        val output = StringBuilder(text.length * 2)
        var previous = previousWord
        segments(text).forEach { segment ->
            if (segment.convertible) {
                output.append(convertWord(segment.text, previous))
                previous = segment.text
            } else {
                output.append(segment.text)
            }
        }
        return output.toString()
    }

    fun segments(text: String): List<Segment> {
        val result = ArrayList<Segment>()
        var index = 0
        while (index < text.length) {
            val preserved = matchPreservedToken(text, index)
            if (preserved != null) {
                result += Segment(preserved, convertible = false)
                index += preserved.length
                continue
            }
            if (isLatinLetter(text[index])) {
                val end = extendWhile(text, index) { isLatinLetter(it) }
                result += Segment(text.substring(index, end), convertible = true)
                index = end
                continue
            }
            val end = index + Character.charCount(text.codePointAt(index))
            result += Segment(text.substring(index, end), convertible = false)
            index = end
        }
        return result
    }

    private fun matchPreservedToken(text: String, start: Int): String? {
        matchPrefixRun(text, start, "https://", ::isUrlChar)?.let { return it }
        matchPrefixRun(text, start, "http://", ::isUrlChar)?.let { return it }
        matchPrefixRun(text, start, "www.", ::isUrlChar)?.let { return it }
        matchMentionOrHashtag(text, start)?.let { return it }
        matchEmail(text, start)?.let { return it }
        matchNumber(text, start)?.let { return it }
        return null
    }

    private fun matchPrefixRun(
        text: String,
        start: Int,
        prefix: String,
        body: (Char) -> Boolean
    ): String? {
        if (!text.startsWith(prefix, start)) return null
        var end = start + prefix.length
        if (end >= text.length || !body(text[end])) return null
        while (end < text.length && body(text[end])) end++
        return text.substring(start, end)
    }

    private fun matchMentionOrHashtag(text: String, start: Int): String? {
        val marker = text[start]
        if (marker != '@' && marker != '#') return null
        if (start + 1 >= text.length || !isHandleChar(text[start + 1])) return null
        val end = extendWhile(text, start + 1, ::isHandleChar)
        return text.substring(start, end)
    }

    private fun matchEmail(text: String, start: Int): String? {
        if (!isLatinLetter(text[start]) && text[start] !in '0'..'9') return null
        var end = start
        var sawAt = false
        var sawDotAfterAt = false
        while (end < text.length && !text[end].isWhitespace()) {
            val character = text[end]
            when {
                character == '@' -> {
                    if (sawAt || end == start) return null
                    sawAt = true
                }
                character == '.' -> if (sawAt) sawDotAfterAt = true
                !isEmailChar(character) -> return null
            }
            end++
        }
        if (!sawAt || !sawDotAfterAt || end == start) return null
        return text.substring(start, end)
    }

    private fun matchNumber(text: String, start: Int): String? {
        if (text[start] !in '0'..'9') return null
        var end = start
        var sawDot = false
        while (end < text.length) {
            val character = text[end]
            when {
                character in '0'..'9' -> end++
                character == '.' && !sawDot && end + 1 < text.length && text[end + 1] in '0'..'9' -> {
                    sawDot = true
                    end++
                }
                else -> break
            }
        }
        return text.substring(start, end)
    }

    private fun extendWhile(text: String, start: Int, predicate: (Char) -> Boolean): Int {
        var end = start
        while (end < text.length && predicate(text[end])) end++
        return end
    }

    private fun isLatinLetter(character: Char): Boolean =
        character in 'A'..'Z' || character in 'a'..'z'

    private fun isUrlChar(character: Char): Boolean =
        !character.isWhitespace() && character != ',' && character != ')' && character != '('

    private fun isHandleChar(character: Char): Boolean =
        isLatinLetter(character) || character in '0'..'9' || character == '_'

    private fun isEmailChar(character: Char): Boolean =
        isLatinLetter(character) || character in '0'..'9' || character == '.' ||
            character == '_' || character == '+' || character == '-'
}
