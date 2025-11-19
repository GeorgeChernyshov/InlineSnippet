package org.example.events

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

interface EventSource<T> {
    val stream: Flow<T>
    suspend fun emit(value: T)
}

// Imagine an EventSource that emits these
class MyEventSource : EventSource<AppEvent> {

    val events = Channel<AppEvent>(Channel.UNLIMITED)

    override val stream = events.consumeAsFlow()

    override suspend fun emit(value: AppEvent) {
        events.send(value)
    }
}