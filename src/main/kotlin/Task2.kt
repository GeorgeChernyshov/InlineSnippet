package org.example

inline fun processList(list: List<String>, processor: (String) -> Unit) {
    for (element in list)
        processor(element)
}