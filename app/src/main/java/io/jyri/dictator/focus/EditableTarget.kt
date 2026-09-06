package io.jyri.dictator.focus

import android.view.accessibility.AccessibilityNodeInfo

object EditableTarget {
    fun isUsable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!node.isVisibleToUser) return false
        if (!node.isEnabled) return false
        if (node.isPassword) return false
        return supportsTextInput(
            editable = node.isEditable,
            canSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT },
            canSetSelection = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_SELECTION },
        )
    }

    // Some custom/Compose inputs expose text actions without setting isEditable.
    internal fun supportsTextInput(
        editable: Boolean,
        canSetText: Boolean,
        canSetSelection: Boolean,
    ): Boolean = editable || canSetText || canSetSelection

    fun snapshot(node: AccessibilityNodeInfo): TargetSnapshot {
        return TargetSnapshot(
            packageName = node.packageName?.toString().orEmpty(),
            windowId = node.windowId,
            className = node.className?.toString(),
            viewIdResourceName = node.viewIdResourceName,
        )
    }

    fun matches(expected: TargetSnapshot, node: AccessibilityNodeInfo): Boolean {
        if (!isUsable(node)) return false
        if (node.packageName?.toString() != expected.packageName) return false
        if (node.windowId != expected.windowId) return false
        val className = node.className?.toString()
        if (expected.className != null && className != expected.className) return false
        val viewId = node.viewIdResourceName
        if (expected.viewIdResourceName != null && viewId != expected.viewIdResourceName) {
            return false
        }
        return true
    }
}
