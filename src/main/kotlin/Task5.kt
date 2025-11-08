package org.example

inline fun <reified T> checkType(
    value: Any
) : Boolean {
    return value is T
}