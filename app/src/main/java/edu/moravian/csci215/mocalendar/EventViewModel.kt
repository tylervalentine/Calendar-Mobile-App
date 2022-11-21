package edu.moravian.csci215.mocalendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

/**
 * View model for a single event. Must be constructed with the factory
 * providing an event ID.
 */
class EventViewModel(private val eventId: UUID) : ViewModel() {
    /** The repository we are using to perform queries */
    private val repo = CalendarRepository.get()

    /** The private event state flow; this one is mutable */
    private val _event = MutableStateFlow<Event?>(null)

    /** The event state flow */
    val event: StateFlow<Event?> = _event.asStateFlow()

    /**
     * When this instance is initialized load the event from the database
     */
    init {
        viewModelScope.launch {
            _event.value = repo.getEventById(eventId)
        }
    }

    /**
     * Update the event in this view model.
     * @param onUpdate a function or lambda that takes an event (the old event)
     * and returns a new event instance to replace it.
     */
    fun updateEvent(onUpdate: (Event) -> Event) {
        _event.update { it?.let { onUpdate(it) } }
    }

    /**
     * When the view model is cleared we update the event in the database.
     */
    override fun onCleared() {
        super.onCleared()
        GlobalScope.launch {
            _event.value?.let {
                repo.updateEvent(it)
            }
        }
    }

    /**
     * Delete this event. This removes it from the database.
     */
    suspend fun deleteEvent() { repo.removeEventById(eventId) }
}

/**
 * The repo that creates view models for event objects. This is required
 * since the view model requires an id to know which event to get from
 * the database.
 * @param id the event ID to create the view model for
 */
class EventViewModelFactory(val id: UUID) : ViewModelProvider.Factory {
    /**
     * Create a new instance of the EventViewModel with the ID given to
     * the class.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EventViewModel(id) as T
    }
}
