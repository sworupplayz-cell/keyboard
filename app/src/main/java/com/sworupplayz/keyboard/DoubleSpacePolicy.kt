package com.sworupplayz.keyboard

/** Optional double-space → period shortcut. Never fires inside protected tokens. */
object DoubleSpacePolicy {
    const val WINDOW_MS = 450L

    fun canTrigger(textBeforeCursor: String): Boolean {
        if (!textBeforeCursor.endsWith(' ') || textBeforeCursor.endsWith("  ")) return false
        val withoutSpace = textBeforeCursor.dropLast(1)
        if (withoutSpace.isEmpty()) return false
        if (SpecialTokenPolicy.isProtectedContext(withoutSpace)) return false
        val last = withoutSpace.last()
        if (last in ".!?,;:।॥") return false
        return last.isLetterOrDigit() || last.code in DEVANAGARI_RANGE || last.code in 0x1F300..0x1FAFF
    }

    fun replacement(language: KeyboardLanguage): String =
        if (language == KeyboardLanguage.NEPALI) "। " else ". "

    fun shouldReplace(textBeforeCursor: String, elapsedMs: Long, enabled: Boolean): Boolean =
        enabled && elapsedMs in 0..WINDOW_MS && canTrigger(textBeforeCursor)

    private val DEVANAGARI_RANGE = 0x0900..0x097F
}
