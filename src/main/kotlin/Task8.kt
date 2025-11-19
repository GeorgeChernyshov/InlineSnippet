package org.example

inline fun processManyItems(list: List<Int>, processor: (Int) -> Unit) {
    for (element in list)
        processor(element)
}