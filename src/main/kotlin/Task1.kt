package org.example

import kotlin.system.measureNanoTime

fun performOperationRegular(count: Long, operation: () -> Unit) {
    val timeElapsed = measureNanoTime {
        var i = 0L
        while (i < count) {
            operation()
            i++
        }
    }

    println("Completed $count operations in $timeElapsed nanoseconds")
}

inline fun performOperationInline(count: Long, operation: () -> Unit) {
    val timeElapsed = measureNanoTime {
        var i = 0L
        while (i < count) {
            operation()
            i++
        }
    }

    println("Completed $count operations in $timeElapsed nanoseconds")
}