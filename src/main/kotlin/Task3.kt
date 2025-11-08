package org.example

inline fun logAndExecute(tag: String, action: () -> Unit) {
    println(tag)
    action()
}