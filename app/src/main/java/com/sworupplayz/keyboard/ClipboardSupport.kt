package com.sworupplayz.keyboard

/**
 * Conservative local clipboard filter. Nothing here leaves the device.
 * Passwords, tokens, binary blobs, and huge pastes are dropped.
 */
object ClipboardPolicy {
    const val MAX_ENTRIES = 20
    const val MAX_TEXT_LENGTH = 500

    fun shouldStore(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        if (clean.length > MAX_TEXT_LENGTH) return false
        if (looksBinary(clean)) return false
        if (looksSensitive(clean)) return false
        return true
    }

    fun looksSensitive(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()
        if (PASSWORD_MARKERS.any { it in lower }) return true
        if (TOKEN_MARKERS.any { it in lower }) return true
        if (looksLikeJwt(clean)) return true
        if (looksLikeOpaqueToken(clean)) return true
        return false
    }

    fun preview(text: String, limit: Int = 80): String {
        val collapsed = text.trim().replace(WHITESPACE, " ")
        if (collapsed.length <= limit) return collapsed
        return collapsed.take(limit - 1) + "…"
    }

    private fun looksBinary(text: String): Boolean {
        var controls = 0
        text.forEach { character ->
            if (character == '\u0000') return true
            if (character < ' ' && character != '\n' && character != '\r' && character != '\t') controls++
        }
        return controls > 0 && controls * 4 >= text.length
    }

    private fun looksLikeJwt(text: String): Boolean {
        val parts = text.split('.')
        if (parts.size != 3) return false
        return parts.all { part -> part.length >= 8 && part.all { it.isLetterOrDigit() || it == '-' || it == '_' } }
    }

    private fun looksLikeOpaqueToken(text: String): Boolean {
        if (text.length < 32 || text.any { it.isWhitespace() }) return false
        return text.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '+' || it == '/' || it == '=' }
    }

    private val PASSWORD_MARKERS = listOf("password", "passwd", "pwd=", "pwd:", "passcode")
    private val TOKEN_MARKERS = listOf(
        "bearer ",
        "api_key",
        "apikey",
        "secret_key",
        "access_token",
        "refresh_token",
        "sk-live",
        "sk-test",
        "authorization:"
    )
    private val WHITESPACE = Regex("\\s+")
}

data class ClipboardEntry(
    val id: Long,
    val text: String
)

/** Bounded on-device clipboard history. Never uploaded. */
class ClipboardRepository(
    initial: List<ClipboardEntry> = emptyList(),
    private val limit: Int = ClipboardPolicy.MAX_ENTRIES
) {
    private val entries = ArrayList<ClipboardEntry>(limit)
    private var nextId = 1L

    init {
        initial.forEach { record(it.text) }
    }

    fun record(text: String): Boolean {
        if (!ClipboardPolicy.shouldStore(text)) return false
        val clean = text
        entries.removeAll { it.text == clean }
        entries.add(0, ClipboardEntry(nextId++, clean))
        while (entries.size > limit) entries.removeAt(entries.lastIndex)
        return true
    }

    fun delete(id: Long): Boolean = entries.removeAll { it.id == id }

    fun clear() {
        entries.clear()
    }

    fun values(): List<ClipboardEntry> = entries.toList()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun serialize(): String = entries.joinToString("\n") { entry ->
        "${entry.id}\t${escape(entry.text)}"
    }

    companion object {
        fun fromSerialized(value: String?, limit: Int = ClipboardPolicy.MAX_ENTRIES): ClipboardRepository {
            val parsed = ArrayList<ClipboardEntry>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) {
                    val text = unescape(parts[1])
                    if (ClipboardPolicy.shouldStore(text)) {
                        parsed += ClipboardEntry(parts[0].toLongOrNull() ?: 0L, text)
                    }
                }
            }
            return ClipboardRepository(parsed, limit)
        }

        private fun escape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        private fun unescape(value: String): String {
            val output = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                if (character == '\\' && index + 1 < value.length) {
                    when (value[index + 1]) {
                        'n' -> output.append('\n')
                        'r' -> output.append('\r')
                        't' -> output.append('\t')
                        '\\' -> output.append('\\')
                        else -> output.append(value[index + 1])
                    }
                    index += 2
                } else {
                    output.append(character)
                    index++
                }
            }
            return output.toString()
        }
    }
}

/** Clipboard paste must finish composing, then insert the raw Unicode text. */
object ClipboardInsertion {
    fun prepareThenInsert(
        hasComposingText: Boolean,
        finishComposing: () -> Unit,
        insert: (String) -> Boolean,
        text: String
    ): Boolean {
        if (text.isEmpty()) return false
        if (hasComposingText) finishComposing()
        return insert(text)
    }
}
