/**
 * Class that represents the Application for the app, which is constructed before
 * anything else in the app
 */

package edu.moravian.csci215.mocalendar

import android.app.Application

class CalendarApplication : Application() {

    /**
     * Function that is called when the application is initially created
     */
    override fun onCreate() {
        super.onCreate()
        CalendarRepository.initialize(this)
    }
}