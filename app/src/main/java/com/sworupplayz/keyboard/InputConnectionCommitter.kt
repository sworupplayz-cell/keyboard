package com.sworupplayz.keyboard

import android.view.inputmethod.InputConnection

/** Single safe insertion path for panel selections such as emoji and handwriting results. */
object InputConnectionCommitter {
    fun commit(connection: InputConnection?, text: String): Boolean {
        if (connection == null || text.isEmpty()) return false
        if (PanelInsertionPolicy.shouldFinishComposing(true)) {
            connection.finishComposingText()
        }
        return connection.commitText(text, 1)
    }
}
