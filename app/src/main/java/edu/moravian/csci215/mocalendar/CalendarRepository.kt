/**
 * Singleton class that acts as the only way to access the contents of the database
 */
package edu.moravian.csci215.mocalendar

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import java.util.*

private const val DATABASE_NAME = "calendar_database"

class CalendarRepository private constructor(context: Context) {

    /** The database object */
    private val database: CalendarDatabase = Room
        .databaseBuilder(
            context.applicationContext,
            CalendarDatabase::class.java,
            DATABASE_NAME
        ).createFromAsset(DATABASE_NAME).build()

    /** The DAO (Data Access Object) */
    private val dao = database.calendarDao()

    /**
     * Function to obtain all of the events in the calendar
     */
    fun getAllEvents(): Flow<List<Event>> = dao.allEvents

    /**
     * Function to obtain a specific event using a unique ID
     */
    suspend fun getEventById(id: UUID): Event = dao.getEventById(id)

    /**
     * Function to obtain the events on a specific day
     */
    fun getEventsOnDay(date: Date): Flow<List<Event>> = dao.getEventsOnDay(date)

    /**
     * Function to add an event to the calendar
     */
    suspend fun addEvent(event: Event) = dao.addEvent(event)

    /**
     * Function to update an event on the calendar
     */
    suspend fun updateEvent(event: Event) = dao.updateEvent(event)

    /**
     * Function to remove an event given the event that was deleted from the calendar
     */
    suspend fun removeEvent(event: Event) = dao.removeEvent(event)

    /**
     * Function to remove an event given the unique ID of an event that was deleted
     * from the calendar
     */
    suspend fun removeEventById(id: UUID) = dao.removeEventById(id)

    /** Static methods to call to get the Singleton instance of the repository */
    companion object {
        /** The only instance of the repository. */
        private var INSTANCE: CalendarRepository? = null

        /** Only initializes the repository if it was not initialized before, ensuring it is
         *  a Singleton */
        fun initialize(context: Context) {
            if (INSTANCE == null) {
                INSTANCE = CalendarRepository(context)
            }
        }

        /** Returns the only repository instance */
        fun get(): CalendarRepository {
            return INSTANCE ?:
            throw IllegalStateException("Repository must be initialized")
        }
    }
}