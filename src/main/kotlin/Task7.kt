package org.example

suspend inline fun executeSuspendedInline(
    block: suspend () -> Unit
) {
    block()
}

suspend fun executeSuspendedRegular(
    block: suspend () -> Unit
) {
    block()
}