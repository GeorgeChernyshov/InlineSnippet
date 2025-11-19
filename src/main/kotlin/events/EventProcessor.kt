package org.example.events

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

interface EventPipelineBuilder<T> {
    val events: Flow<T>
    val scope: CoroutineScope
}

inline fun <reified R> EventPipelineBuilder<in R>.filter(
    crossinline predicate: (R) -> Boolean = { true }
) : EventPipelineBuilder<R> {
    return EventPipelineBuilderImpl(
        events = unsafeFlow {
            events.collect {
                if (it is R && predicate(it))
                    emit(it)
            }
        },
        scope = scope
    )
}

fun <T> unsafeFlow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
    return object : Flow<T> {
        override suspend fun collect(collector: FlowCollector<T>) {
            collector.block()
        }
    }
}

inline fun <T, R> EventPipelineBuilder<T>.map(
    crossinline transform: (T) -> R
) = EventPipelineBuilderImpl(
    events = unsafeFlow {
        events.collect {
            emit(transform(it))
        }
    },
    scope = scope
)

inline fun <T, R> EventPipelineBuilder<T>.asyncMap(
    crossinline transform: suspend (T) -> R
) = EventPipelineBuilderImpl(
    events = channelFlow {
        events.collect {
            send(transform(it))
        }
    },
    scope = scope
)

inline fun <T> EventPipelineBuilder<T>.consume(
    crossinline consumer: (T) -> Unit
) = scope.launch {
    events.collect {
        consumer(it)
    }
}

class EventPipelineBuilderImpl<T>(
    override val events: Flow<T>,
    override val scope: CoroutineScope
) : EventPipelineBuilder<T>

inline fun <T> eventPipeline(
    source: EventSource<T>,
    crossinline block: EventPipelineBuilder<T>.() -> Job
): Job {
    val pipelineScope = CoroutineScope(SupervisorJob())
    val builder = EventPipelineBuilderImpl(
        events = source.stream,
        scope = pipelineScope
    )

    val consumeJob = block(builder)

    consumeJob.invokeOnCompletion { cause ->
        pipelineScope.cancel(cause?.let {
            CancellationException("Pipeline cancelled", it)
        })
    }

    return consumeJob
}