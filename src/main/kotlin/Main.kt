package org.example

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.events.*
import org.example.events.EventSource
import org.example.events.MyEventSource
import org.example.events.UserLoggedIn
import org.example.events.analytics.AnalyticsEvent
import org.example.events.enrichedanalytics.EnrichedAnalyticsEvent
import org.example.events.eventPipeline

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

    runBlocking {
        executeSuspendedInline { delay(100) }
        executeSuspendedRegular { delay(100) }
    }

//    val manyItemsList = (0..100000000).toList()
//    processManyItems(
//        list = manyItemsList,
//        processor = {
//            calculateFibonacci(30)
//        }
//    )

    val eventSource = MyEventSource()

    val job = eventPipeline(eventSource) {
        filter<UserLoggedIn> { event ->
            event.userId.startsWith("user_")
        }.map { event ->
            AnalyticsEvent.Login(event.userId, System.currentTimeMillis())
        }.asyncMap { event ->
            // Simulating a network call
            delay(100)
            println("Enriching login event for ${event.userId}")
            EnrichedAnalyticsEvent.Login(
                userId = event.userId, event.timestamp, "Premium")
        }.consume { enrichedEvent ->
            println("Analytics Service: Logged enriched event: $enrichedEvent")
        }
    }

    runBlocking {
        eventSource.emit(UserLoggedIn("user_1"))
        eventSource.emit(UserLoggedIn("user_2"))
        eventSource.emit(ProductViewed("other", "product"))
//        eventSource.events.close()
        job.join()
    }
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

fun calculateFibonacci(order: Int): Long {
    if (order <= 0) return 0

    return generateSequence(1L to 1L) { (a, b) -> b to (a + b) }
        .map { it.first }
        .elementAt(order)
}