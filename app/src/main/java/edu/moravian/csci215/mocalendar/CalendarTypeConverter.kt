/**
 * Class that converts values for the Event objects that are stored in a database for
 * a Calendar
 */

package edu.moravian.csci215.mocalendar

import androidx.room.TypeConverter
import java.util.*

class CalendarTypeConverter {

    /** Type converter that converts the Date to a long so it can be stored in the database */
    @TypeConverter
    fun fromDate(date: Date): Long = date.time

    /** Type converter that converts a Long from the database to a Date object */
    @TypeConverter
    fun toDate(sinceEpoch: Long): Date = Date(sinceEpoch)
}