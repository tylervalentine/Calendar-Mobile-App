/**
 * Database class that contains the Event objects to use in the Calendar
 */

package edu.moravian.csci215.mocalendar

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ Event::class ], version = 1)

@TypeConverters(CalendarTypeConverter::class)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}