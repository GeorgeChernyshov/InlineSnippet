package org.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.events.*
import org.example.events.analytics.AnalyticsEvent
import org.example.events.enrichedanalytics.EnrichedAnalyticsEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventProcessingDslTest {

    private lateinit var eventSource: MyEventSource
    private val consumedEvents = ConcurrentLinkedQueue<Any>() // Thread-safe queue to collect events
    private val currentTime = 1678886400000L // A fixed timestamp for predictable results

    @BeforeEach
    fun setup() {
        eventSource = MyEventSource()
        consumedEvents.clear()
    }

    @Test
    fun testFilterByTypeAndPredicate() = runTest {
        val job = eventPipeline(eventSource) {
            filter<UserLoggedIn> { event ->
                event.userId.startsWith("premium_")
            }.consume { event ->
                consumedEvents.add(event)
            }
        }

        // Emit events
        eventSource.emit(UserLoggedIn("user_123"))
        eventSource.emit(ProductViewed("premium_456", "prod_A")) // Should be filtered out by type
        eventSource.emit(UserLoggedIn("premium_789")) // Should pass filter
        eventSource.emit(UserLoggedIn("guest_007")) // Should be filtered out by predicate
        eventSource.emit(ProductViewed("user_123", "prod_B")) // Should be filtered out by type

        // Give pipeline time to process events
        advanceUntilIdle()
        job.cancelAndJoin() // Clean up the coroutine job

        assertEquals(1, consumedEvents.size)
        assertTrue(consumedEvents.first() is UserLoggedIn)
        assertEquals(
            expected = "premium_789",
            actual = (consumedEvents.first() as UserLoggedIn)
                .userId
        )
    }

    @Test
    fun testMapTransformation() = runTest {
        val job = eventPipeline(eventSource) {
            this.filter<UserLoggedIn>() // Filter by type only (no predicate)
                .map { event ->
                    AnalyticsEvent.Login(event.userId, currentTime)
                }
                .consume { event -> consumedEvents.add(event) }
        }

        eventSource.emit(UserLoggedIn("map_user_1"))
        eventSource.emit(ProductViewed("some_id", "some_prod")) // Should be filtered out
        eventSource.emit(UserLoggedIn("map_user_2"))

        advanceUntilIdle()
        delay(500)
        job.cancelAndJoin()

        assertEquals(2, consumedEvents.size)
        assertTrue(consumedEvents.all { it is AnalyticsEvent.Login })
        assertEquals(
            expected = AnalyticsEvent.Login("map_user_1", currentTime),
            actual = consumedEvents.elementAt(0)
        )

        assertEquals(
            expected = AnalyticsEvent.Login("map_user_2", currentTime),
            actual = consumedEvents.elementAt(1)
        )
    }

    @Test
    fun testAsyncMapTransformation() = runTest {
        val job = eventPipeline(eventSource) {
            this.filter<UserLoggedIn>()
                .asyncMap { event ->
                    delay(100)
                    println("Test: Async mapping ${event.userId}") // For debug output
                    EnrichedAnalyticsEvent.Login(
                        userId = event.userId,
                        timestamp = currentTime,
                        userType = "Gold"
                    )
                }
                .consume { event ->
                    consumedEvents.add(event)
                    print("hello")
                }
        }

        val startTime = System.nanoTime()
        eventSource.emit(UserLoggedIn("async_user_1"))
        eventSource.emit(UserLoggedIn("async_user_2"))
        eventSource.emit(ProductViewed("other", "product")) // Filtered out

        advanceUntilIdle() // This will advance past the delay(100)
        advanceTimeBy(1000)
        eventSource.events.close()
        job.join()
        val endTime = System.nanoTime()

        assertEquals(2, consumedEvents.size)
        assertTrue(consumedEvents.all { it is EnrichedAnalyticsEvent.Login })
        assertEquals(
            expected = EnrichedAnalyticsEvent.Login(
                userId = "async_user_1",
                timestamp = currentTime,
                userType = "Gold"
            ),
            actual = consumedEvents.elementAt(0)
        )

        assertEquals(
            expected = EnrichedAnalyticsEvent.Login(
                userId = "async_user_2",
                timestamp = currentTime,
                userType = "Gold"
            ),
            consumedEvents.elementAt(1)
        )
    }

    @Test
    fun testChainedOperations() = runTest {
        val job = eventPipeline(eventSource) {
            this.filter<UserLoggedIn> { it.userId.startsWith("test_") }
                .map { event ->
                    AnalyticsEvent.Login(event.userId.uppercase(), currentTime)
                }
                .asyncMap { event ->
                    delay(50) // Short delay
                    EnrichedAnalyticsEvent.Login(
                        userId = event.userId,
                        timestamp = event.timestamp,
                        userType = "Tier1"
                    )
                }
                .consume { event -> consumedEvents.add(event) }
        }

        eventSource.emit(UserLoggedIn("test_user_A")) // -> A
        eventSource.emit(ProductViewed("test_user_B", "prod")) // Filtered by type
        eventSource.emit(UserLoggedIn("guest_user_C")) // Filtered by predicate
        eventSource.emit(UserLoggedIn("test_user_D")) // -> D
        eventSource.events.close()

        advanceUntilIdle()
        advanceTimeBy(1000)
        job.join()

        assertEquals(2, consumedEvents.size)
        assertTrue(consumedEvents.all { it is EnrichedAnalyticsEvent.Login })
        assertEquals(
            expected = EnrichedAnalyticsEvent.Login(
                userId = "TEST_USER_A",
                timestamp = currentTime,
                userType = "Tier1"
            ),
            actual = consumedEvents.elementAt(0)
        )

        assertEquals(
            expected = EnrichedAnalyticsEvent.Login(
                userId = "TEST_USER_D",
                timestamp = currentTime,
                userType = "Tier1"
            ),
            actual = consumedEvents.elementAt(1)
        )
    }

    @Test
    fun testFilterOutAllEvents() = runTest {
        val job = eventPipeline(eventSource) {
            this.filter<UserLoggedIn> { false } // Predicate always returns false
                .map { it } // No-op map
                .consume { event -> consumedEvents.add(event) }
        }

        eventSource.emit(UserLoggedIn("any_user_1"))
        eventSource.emit(UserLoggedIn("any_user_2"))
        eventSource.emit(ProductViewed("any_user_3", "prod"))

        advanceUntilIdle()
        delay(1)
        job.cancelAndJoin()

        assertTrue(consumedEvents.isEmpty())
    }

    @Test
    fun testMultipleEventTypesInSource() = runTest {
        val job = eventPipeline(eventSource) {
            // Only process ProductViewed events
            this.filter<ProductViewed>()
                .map { AnalyticsEvent.ProductAnalytics(
                    userId = it.userId,
                    productId = it.productId,
                    timestamp = currentTime
                ) }
                .consume { event -> consumedEvents.add(event) }
        }

        eventSource.emit(UserLoggedIn("user_1"))
        eventSource.emit(ProductViewed("viewer_1", "item_X")) // -> X
        eventSource.emit(DataUpdated("key_1", 123))
        eventSource.emit(ProductViewed("viewer_2", "item_Y")) // -> Y

        advanceUntilIdle()
        delay(1)
        job.cancelAndJoin()

        assertEquals(2, consumedEvents.size)
        assertTrue(consumedEvents.all {
            it is AnalyticsEvent.ProductAnalytics
        })

        assertEquals(
            expected = AnalyticsEvent.ProductAnalytics(
                userId = "viewer_1",
                productId = "item_X",
                timestamp = currentTime
            ),
            actual = consumedEvents.elementAt(0)
        )

        assertEquals(
            expected = AnalyticsEvent.ProductAnalytics(
                userId = "viewer_2",
                productId = "item_Y",
                timestamp = currentTime
            ),
            actual = consumedEvents.elementAt(1)
        )
    }

    @Test
    fun testMixingFilterTypes() = runTest {
        val job = eventPipeline(eventSource) {
            // Filter specific events that are AppEvent
            this.filter<ProductViewed> { it.productId.startsWith("special_") }
                .map { it.userId } // Map to String (userId)
                .consume { userId -> consumedEvents.add(userId) }
        }

        eventSource.emit(UserLoggedIn("user_logged_in")) // Ignored (type filter)
        eventSource.emit(ProductViewed("user_A", "regular_product")) // Ignored (predicate filter)
        eventSource.emit(ProductViewed("user_B", "special_item_1")) // -> user_B
        eventSource.emit(DataUpdated("data_key", "data_val")) // Ignored (type filter)
        eventSource.emit(ProductViewed("user_C", "special_item_2")) // -> user_C

        advanceUntilIdle()
        delay(1)
        job.cancelAndJoin()

        assertEquals(2, consumedEvents.size)
        assertEquals("user_B", consumedEvents.elementAt(0))
        assertEquals("user_C", consumedEvents.elementAt(1))
    }

    @Test
    fun testEmptySource() = runTest {
        val job = eventPipeline(eventSource) {
            this.map { it.toString() }
                .consume { event -> consumedEvents.add(event) }
        }

        // Don't emit any events
        advanceUntilIdle()
        delay(1)
        job.cancelAndJoin()

        assertTrue(consumedEvents.isEmpty())
    }
}