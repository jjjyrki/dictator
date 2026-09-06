package io.jyri.dictator.focus

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedInputSearchTest {
    private class Node(
        val focused: Boolean = false,
        val usable: Boolean = false,
        val children: List<Node> = emptyList(),
    )

    private fun search(root: Node) = findFocusedInput(
        root,
        children = { it.children.asSequence() },
        isFocused = { it.focused },
        isUsable = { it.usable },
    )

    @Test
    fun `finds nested focused input past unfocused and unusable nodes`() {
        val input = Node(focused = true, usable = true)
        val root = Node(children = listOf(
            Node(usable = true),
            Node(focused = true),
            Node(children = listOf(input)),
        ))
        assertSame(input, search(root))
    }

    @Test
    fun `does not substitute an unfocused field or unsafe focused node`() {
        assertNull(search(Node(children = listOf(Node(usable = true), Node(focused = true)))))
    }

    @Test
    fun `bounds both traversal and child retrieval for cyclic trees`() {
        val root = Node()
        var retrieved = 0
        var checked = 0
        assertNull(findFocusedInput(
            root,
            children = { generateSequence { retrieved++; root } },
            isFocused = { checked++; false },
            isUsable = { true },
            limit = 8,
        ))
        assertTrue(checked <= 8)
        assertTrue(retrieved <= 8)
    }
}
