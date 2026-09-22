package dev.aditya.tamper_guard

import io.flutter.plugin.common.EventChannel
import java.util.concurrent.CopyOnWriteArraySet

/** Every Flutter engine listening to a stream; the service emits to all. */
internal object Sinks {
    val windows = CopyOnWriteArraySet<EventChannel.EventSink>()
    val texts = CopyOnWriteArraySet<EventChannel.EventSink>()
}

internal fun Iterable<EventChannel.EventSink>.emit(value: Map<String, Any?>) {
    for (sink in this) sink.success(value)
}
