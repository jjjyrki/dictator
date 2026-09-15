package io.jyri.dictator.focus

data class TargetSnapshot(
    val packageName: String,
    val windowId: Int,
    val className: String?,
    val viewIdResourceName: String?,
)
