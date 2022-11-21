package edu.moravian.csci215.mocalendar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.CalendarView
import android.widget.CalendarView.OnDateChangeListener
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import edu.moravian.csci215.mocalendar.databinding.EventListItemBinding
import edu.moravian.csci215.mocalendar.databinding.FragmentCalendarBinding
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

/**
 * A fragment that displays a calendar, the currently selected date, and a list
 * of events on that day. When a day within the calendar is clicked. This
 * calendar auto-updates its arguments with the last highlighted day so that
 * when it is rotated the same day is still highlighted. Because of this it
 * cannot use the special safe-args or nav-args (but since it is the first
 * fragment in the navigation this doesn't really matter).
 */
class CalendarFragment : Fragment(), OnDateChangeListener {
    /** Binding for the views of the fragment (nullable version) */
    private var _binding: FragmentCalendarBinding? = null
    /** Binding for the views of the fragment (non-nullable accessor) */
    private val binding: FragmentCalendarBinding
        get() = checkNotNull(_binding) { "Binding is currently null! Oh-uh!" }

    /** The view model containing the events we are listing */
    private val eventsViewModel: EventListViewModel by viewModels()

    /** The list of events we are showing */
    private var events: List<Event> = emptyList()

    /** The adapter for the calendar */
    private val adapter = CalendarAdapter()

    /** The repo for the calendar database */
    private val repo = CalendarRepository.get()

    /** Create the binding view for this layout. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    /**
     * Once the view is created we can:
     *    - set up the calendar view
     *    - set up the recycler view
     *    - add the menu provider to the host activity
     *    - use a coroutine to collect the events from the database (and notify
     *      the adapter that things have changed)
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Setup the calendar
        binding.calendarView.setOnDateChangeListener(this)
        setDate((arguments?.getSerializable("date") as? Date?).orNow())

        // Setup the recycler view
        binding.dayList.adapter = adapter
        ItemTouchHelper(SwipeToDeleteCallback()).attachToRecyclerView(binding.dayList)

        requireActivity().addMenuProvider(CalendarMenuProvider(), viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            eventsViewModel.events.collect {
                events = it
                adapter.notifyDataSetChanged()
            }
        }
    }

    /** On destroying the view, clean up the binding. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Set the date for the events being listed. This updates the calendar
     * view's displayed date, the date being shown, and the arguments for
     * the fragment (so that when rotated it is restored properly).
     * @param date the new date for the list to show events for
     */
    fun setDate(date: Date) {
        val dateNoTime = date.clearTime()
        binding.calendarView.date = dateNoTime.time
        binding.dateText.text = dateNoTime.toDateString()
        eventsViewModel.setDate(dateNoTime)
        arguments?.putSerializable("date", dateNoTime)
    }

    /**
     * When a new day is selected on the calendar view the day is set, causing
     * the list to update.
     * @param view the view that had the day selected on it
     * @param year the year that was selected
     * @param month the month that was selected
     * @param dayOfMonth the day of the month that was selected
     */
    override fun onSelectedDayChange(view: CalendarView, year: Int, month: Int, dayOfMonth: Int) {
        setDate(createDate(year, month, dayOfMonth))
    }


    /**
     * Add the event to the calendar database and then show that event (as if
     * it had been clicked). This requires a coroutine.
     * @param event the event to add to the database
     */
    fun addEvent(event: Event) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.addEvent(event)
            showEvent(event)
        }
    }

    /**
     * Delete the event from the calendar database. This requires a coroutine.
     * @param event the event to remove from the database
     */
    fun deleteEvent(event: Event) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.removeEvent(event)
        }
    }

    /**
     * Navigate to the event details fragment showing the specified event.
     * @param event the event to show the details for
     */
    fun showEvent(event: Event) {
        findNavController().navigate(CalendarFragmentDirections.createEvent(event.id))
    }

    /**
     * Create a new event or assignment and add it to the database. For either,
     * have the start time set to getStartTime(). Additionally, new events have
     * an end time 1 hour later and no other changes but new assignments have
     * their type set to ASSIGNMENT and no other changes (their end time is not
     * changed from the default of null).
     */
    private fun createAndAddEventOrAssignment(createEvent: Boolean) {
        when(createEvent){
            true -> {
                val event = Event(startTime = getStartTime(), endTime = getStartTime().combineWithTime(
                    createTime(getStartTime().time.hours.toInt(DurationUnit.HOURS) + 2,
                        getStartTime().time.minutes.toInt(DurationUnit.MINUTES) - 7)))
                addEvent(event)
            }
            else -> {
                val assignment = Event(startTime = getStartTime(), type = EventType.ASSIGNMENT)
                addEvent(assignment)
            }
        }
    }

    /**
     * Get the start time based on the current date selected on the calender
     * and the current hour in the day. This is the date/time that should be
     * used for the start time of new events/assignments.
     * @return the start time to use for new events/assignments
     */
    private fun getStartTime(): Date {
        val cal = Calendar.getInstance()
        return Date(binding.calendarView.date).combineWithTime(
            createTime(cal[Calendar.HOUR_OF_DAY], 0)
        )
    }

    /** Menu Provider for the calendar fragment. */
    private inner class CalendarMenuProvider: MenuProvider {

        /** Inflates the view when the menu is created. */
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.calendar_fragment_menu, menu)
        }

        /**
         * Checks if user wants to create a new event or assignment when an item is selected,
         * then creates and adds it accordingly.
         */
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.new_event) {

                createAndAddEventOrAssignment(true)
                return true
            }

            else if (menuItem.itemId == R.id.new_assignment) {

                createAndAddEventOrAssignment(false)
                return true
            }
            return false
        }

    }

    /** The viewholder for a calendar. */
    private inner class CalendarViewHolder(val binding: EventListItemBinding) : RecyclerView.ViewHolder(binding.root) {

        /** Sets text, icon for event specified. */
        fun bind(event: Event) {
            binding.apply {
                eventTitle.text = event.name
                eventName.text = event.description
                eventIcon.setImageResource(event.type.iconResourceId)
                startTime.text = event.startTime.toTimeString()
                endTime.text = event.endTime?.toTimeString() ?: getString(R.string.empty_string)
                root.setOnClickListener {
                    showEvent(event)
                }
            }
        }
    }
    /** The RecyclerView adapter to show a list of nodes. */
    private inner class CalendarAdapter : RecyclerView.Adapter<CalendarViewHolder>() {
        /** Creates a new view holder using the event_list_item layout. */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
                = CalendarViewHolder(EventListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        /** Bind the given view holder to the event at the given position. */
        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) =
            holder.bind(events[position])

        /** Get the number of events. */
        override fun getItemCount() = events.size
    }

    /**
     * A touch helping callback to add swipe action to the RecyclerView to
     * support deleting items.
     */
    private inner class SwipeToDeleteCallback :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val icon = ContextCompat.getDrawable(requireContext(), R.drawable.delete)!!
        private val background = ColorDrawable(Color.RED)

        /** For up/down swipes we do nothing, daily events are always sorted in time order */
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
            false

        /** For left/right swipes delete the item */
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) =
            deleteEvent(events[viewHolder.bindingAdapterPosition])

        /**
         * Draw the element. This draws the child normally except for the red background with the
         * delete icon while swiping to show we are deleting it.
         */
        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                                 dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView

            // Draw the red tinted background
            val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
            val iconMarginWidth = iconMargin + icon.intrinsicWidth
            val alpha = max(min(255f * 2 * (abs(dX) - iconMarginWidth) / itemView.width, 255f), 0f).toInt()
            background.color = 0xFF0000 or (alpha/2 shl 24)
            background.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
            background.draw(c)

            // Draw the regular view holder
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            // Draw the trash icon
            val iconTop = itemView.top + iconMargin
            val iconBottom = iconTop + icon.intrinsicHeight
            val iconLeft = if (dX > 0) itemView.left + iconMargin else itemView.right - iconMarginWidth
            val iconRight = if (dX > 0) itemView.left + iconMarginWidth else itemView.right - iconMargin
            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            icon.alpha = alpha
            icon.draw(c)
        }
    }
}
