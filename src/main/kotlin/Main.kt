package org.example

fun main() {
    val count = 1_000_000_000L // 1 Billion operations
    var dummyWork = 0 // A shared mutable variable to prevent lambda from being fully optimized away

    // First, run a warm-up phase for the JIT compiler
    println("--- Warming up JIT compiler ---")
    val warmUpCount = 100_000_000L
    performOperationRegular(warmUpCount) { dummyWork++ }
    performOperationInline(warmUpCount) { dummyWork++ }
    println("Warm-up complete. Current dummyWork: $dummyWork")
    dummyWork = 0 // Reset for actual measurement

    println("\n--- Actual Measurement ---")
    performOperationRegular(count) { dummyWork++ }
    performOperationInline(count) { dummyWork++ }

    println("Final dummyWork value: $dummyWork")

    val itemCount = testListProcessor()
    println("Processed $itemCount items")

    logAndExecute("First", {})

    val myClass = MyClass()
    myClass.triggerInternalAction()
}

fun testListProcessor() : Int {
    val testData = listOf(
        "Alice",
        "Bob",
        "break",
        "Charlie",
        "Dan"
    )

    var processedElements = 0

    processList(testData) {
        if (it == "break")
            return processedElements

        processedElements++
    }

    return processedElements
}