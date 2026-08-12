package com.sworupplayz.keyboard

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputConnectionCommitterTest {
    @Test
    fun unicodeEmojiIsCommittedToInputConnectionAtCursor() {
        var committed: String? = null
        var cursorPosition: Int? = null
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, arguments ->
            if (method.name == "commitText") {
                committed = arguments?.get(0).toString()
                cursorPosition = arguments?.get(1) as Int
                true
            } else {
                defaultValue(method.returnType)
            }
        } as InputConnection

        assertTrue(InputConnectionCommitter.commit(connection, "😊"))
        assertEquals("😊", committed)
        assertEquals(1, cursorPosition)
    }

    @Test
    fun numberAndSymbolAreCommittedWithoutTransformation() {
        val committed = mutableListOf<String>()
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, arguments ->
            if (method.name == "commitText") {
                committed += arguments?.get(0).toString()
                true
            } else {
                defaultValue(method.returnType)
            }
        } as InputConnection

        assertTrue(InputConnectionCommitter.commit(connection, "7"))
        assertTrue(InputConnectionCommitter.commit(connection, "&"))
        assertEquals(listOf("7", "&"), committed)
    }

    @Test
    fun missingConnectionOrEmptyTextDoesNotInsert() {
        assertFalse(InputConnectionCommitter.commit(null, "😊"))
        assertFalse(InputConnectionCommitter.commit(null, ""))
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
