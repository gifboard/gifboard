package com.gifboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Events emitted by GifBoard for tutorial tracking.
 */
sealed class TutorialEvent {
    object SearchBarActivated : TutorialEvent()
    object SearchPerformed : TutorialEvent()
}

/**
 * Singleton event bus for tutorial state tracking.
 * GifBoardService emits events, TutorialStepFragment observes them.
 * 
 * Events are only emitted when [isActive] is true to avoid
 * unnecessary processing during normal keyboard usage.
 */
object TutorialEventBus {
    private val _events = MutableStateFlow<TutorialEvent?>(null)
    val events: StateFlow<TutorialEvent?> = _events

    /** Set to true when tutorial is actively listening for events */
    var isActive = false

    fun emit(event: TutorialEvent) {
        if (isActive) {
            _events.value = event
        }
    }

    fun reset() {
        _events.value = null
    }
}
