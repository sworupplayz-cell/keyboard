package com.sworupplayz.keyboard

import java.util.ArrayDeque

/** Small LIFO history used by temporary keyboard panels. */
class PreviousLayoutStack<T> {
    private val entries = ArrayDeque<T>()

    val size: Int get() = entries.size

    fun remember(value: T) {
        entries.addLast(value)
    }

    fun previousOr(fallback: T): T = if (entries.isEmpty()) fallback else entries.removeLast()

    fun clear() {
        entries.clear()
    }
}
