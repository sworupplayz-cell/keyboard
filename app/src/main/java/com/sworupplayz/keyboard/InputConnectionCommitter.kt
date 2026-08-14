package com.sworupplayz.keyboard

import android.view.inputmethod.InputConnection

/**
 * Single safe InputConnection facade. Unusual or dying editors must not crash
 * the IME. This is not a second typing engine.
 */
object InputConnectionCommitter {
    fun commit(connection: InputConnection?, text: String): Boolean {
        if (connection == null || text.isEmpty()) return false
        return runCatching {
            if (PanelInsertionPolicy.shouldFinishComposing(true)) {
                connection.finishComposingText()
            }
            connection.commitText(text, 1)
        }.getOrDefault(false)
    }

    fun commitRaw(connection: InputConnection?, text: String): Boolean {
        if (connection == null) return false
        return runCatching { connection.commitText(text, 1) }.getOrDefault(false)
    }

    fun finishComposing(connection: InputConnection?): Boolean {
        if (connection == null) return false
        return runCatching { connection.finishComposingText() }.getOrDefault(false)
    }

    fun setComposing(connection: InputConnection?, text: String): Boolean {
        if (connection == null) return false
        return runCatching { connection.setComposingText(text, 1) }.getOrDefault(false)
    }

    fun deleteSurrounding(
        connection: InputConnection?,
        beforeLength: Int,
        afterLength: Int = 0
    ): Boolean {
        if (connection == null) return false
        if (beforeLength <= 0 && afterLength <= 0) return true
        return runCatching { connection.deleteSurroundingText(beforeLength, afterLength) }
            .getOrDefault(false)
    }

    fun deleteSurroundingInCodePoints(
        connection: InputConnection?,
        beforeCodePoints: Int,
        afterCodePoints: Int,
        fallbackBeforeUnits: Int,
        fallbackAfterUnits: Int
    ): Boolean {
        if (connection == null) return false
        if (beforeCodePoints <= 0 && afterCodePoints <= 0 &&
            fallbackBeforeUnits <= 0 && fallbackAfterUnits <= 0
        ) {
            return true
        }
        val codePointResult = runCatching {
            connection.deleteSurroundingTextInCodePoints(beforeCodePoints, afterCodePoints)
        }
        if (codePointResult.isSuccess) return codePointResult.getOrDefault(false)
        return deleteSurrounding(connection, fallbackBeforeUnits, fallbackAfterUnits)
    }

    fun textBeforeCursor(connection: InputConnection?, limit: Int): String {
        if (connection == null || limit <= 0) return ""
        return runCatching { connection.getTextBeforeCursor(limit, 0)?.toString().orEmpty() }
            .getOrDefault("")
    }

    fun textAfterCursor(connection: InputConnection?, limit: Int): String {
        if (connection == null || limit <= 0) return ""
        return runCatching { connection.getTextAfterCursor(limit, 0)?.toString().orEmpty() }
            .getOrDefault("")
    }

    fun selectedText(connection: InputConnection?): String? {
        if (connection == null) return null
        return runCatching { connection.getSelectedText(0)?.toString() }.getOrNull()
    }

    fun performEditorAction(connection: InputConnection?, actionId: Int): Boolean {
        if (connection == null) return false
        return runCatching { connection.performEditorAction(actionId) }.getOrDefault(false)
    }

    fun setSelection(connection: InputConnection?, start: Int, end: Int): Boolean {
        if (connection == null) return false
        return runCatching { connection.setSelection(start, end) }.getOrDefault(false)
    }
}
