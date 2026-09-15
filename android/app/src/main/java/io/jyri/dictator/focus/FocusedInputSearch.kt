package io.jyri.dictator.focus

/** Bounded breadth-first search so malformed or large app trees cannot monopolize the service. */
internal fun <T : Any> findFocusedInput(
    root: T,
    children: (T) -> Sequence<T>,
    isFocused: (T) -> Boolean,
    isUsable: (T) -> Boolean,
    limit: Int = 128,
): T? {
    val pending = ArrayDeque<T>()
    pending.add(root)
    var visited = 0
    while (pending.isNotEmpty() && visited < limit) {
        val node = pending.removeFirst()
        visited++
        if (isFocused(node) && isUsable(node)) return node
        val remaining = limit - visited - pending.size
        if (remaining > 0) pending.addAll(children(node).take(remaining))
    }
    return null
}
