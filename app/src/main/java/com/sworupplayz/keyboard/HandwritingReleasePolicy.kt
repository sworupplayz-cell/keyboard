package com.sworupplayz.keyboard

/**
 * v1 handwriting entry. The canvas and offline pipeline stay in the tree,
 * but the ✍ button must not claim OCR works.
 */
object HandwritingReleasePolicy {
    const val TITLE = "Handwriting Recognition — Coming Soon"
    const val MESSAGE =
        "Write-to-text is not included in GORKHEY KEYBOARD v1.0. English, नेपाली, and Roman typing stay fully offline."

    fun showsComingSoon(): Boolean = true

    fun opensCanvas(): Boolean = !showsComingSoon()
}
