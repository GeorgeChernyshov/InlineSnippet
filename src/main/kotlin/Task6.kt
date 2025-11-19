package org.example

val storedActions = mutableListOf<() -> Unit>()

inline fun processAndStore(
    tag: String,
    noinline action: () -> Unit,
    cleanup: () -> Unit
) {
    println(tag)
    storedActions.add(action)
    cleanup()
}