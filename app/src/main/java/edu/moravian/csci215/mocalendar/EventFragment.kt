package edu.moravian.csci215.mocalendar

import android.opengl.Visibility
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import edu.moravian.csci215.mocalendar.databinding.FragmentEventBinding
import kotlinx.coroutines.launch
import java.util.*


/**
 * The fragment for a single event. It allows editing all of the details of the event, either with
 * text edit boxes (for the name and description) or popup windows (for the date, start time,
 * time and type). The event is not updated in the database until the user leaves this fragment.
 */
class EventFragment : Fragment() {
    /** Binding for the views of the fragment (nullable version) */
    private var _binding: FragmentEventBinding? = null

    /** Binding for the views of the fragment (non-nullable accessor) */
    private val binding: FragmentEventBinding
        get() = checkNotNull(_binding) { "Binding is currently null! Oh-uh!" }

    /**
     * The arguments passed to this fragment:
     *   - eventID: the event ID to use grab the event to show
     */
    private val args: EventFragmentArgs by navArgs()

    /** The view model containing the event we are showing/editing */
    private val viewModel: EventViewModel by viewModels {
        EventViewModelFactory(args.event)
    }

    /** Create the binding view for this layout. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    /**
     * Collect the event from the database, set up the edit text view listeners,
     * add the fragment result listeners, and setup the back handler.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * These update the event (using the view model) by copying the old event with the one value
         * change as appropriate.
        */
        binding.eventName.doOnTextChanged { text, _, _, _ ->
            viewModel.updateEvent { it.copy(name = text.toString()) }
        }

        binding.description.doOnTextChanged { text, _, _, _ ->
            viewModel.updateEvent { it.copy(description = text.toString()) }
        }

        /**
         * This updates the event (using the view model) with a copy of the old event with the type
         * changed using the information from the bundle.
         */
        setFragmentResultListener(EventTypePickerFragment.REQUEST_KEY_EVENT_TYPE) { _, bundle ->
            viewModel.updateEvent { it.copy(type = bundle.getSerializable(EventTypePickerFragment.BUNDLE_KEY_EVENT_TYPE) as EventType) }
        }

        /**
         * Updates the event as above but the new event has a changed start and end time which are
         * made by combining the old values with the date in the bundle.
        */
        setFragmentResultListener(DatePickerFragment.REQUEST_KEY_DATE) { _, bundle ->
            viewModel.updateEvent { it.copy(startTime = it.startTime.combineWithDate(bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date)) }
            viewModel.updateEvent { it.copy(endTime = it.endTime?.combineWithDate(bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date)) }
        }

        /**
         * Updates the event as above but new event has a changed start made by combining the old
         * value with the time in the bundle and a changed end time made utilizing the old start
         * time and the time in the bundle.
         */
        setFragmentResultListener(TimePickerFragment.REQUEST_KEY_START_TIME) { _, bundle ->
            viewModel.updateEvent { it.copy(startTime = it.startTime.combineWithTime(bundle.getSerializable(TimePickerFragment.BUNDLE_KEY_TIME) as Date))}
        }

        /**
         * Updates the event as above but new event has a changed end time from the bundle and fixed
         * to be after the start time.
         */
        setFragmentResultListener(TimePickerFragment.REQUEST_KEY_END_TIME) { _, bundle ->
            viewModel.updateEvent { it.copy(endTime = (bundle.getSerializable(TimePickerFragment.BUNDLE_KEY_TIME) as Date).fixTimeToBeAfter(it.startTime))}
        }

        // Create and add the back pressed callback handler (OnBackPressedCallback)
        // If the event name is empty, a toast is shown with an error, otherwise the callback is disabled
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (checkEventName()) isEnabled = false
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        requireActivity().addMenuProvider(EventMenuProvider(), viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect {
                    event -> event?.let { updateUI(event) }
                }
            }
        }
    }

    /** On destroying the view, clean up the binding. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Checks the current event name. If it is empty, then this shows an error
     * message toast and returns false. Otherwise it returns true.
     * @return true if the event name is good
     */
    private fun checkEventName(): Boolean {
        when(viewModel.event.value?.name) {
            "" -> {
                val toast = Toast.makeText(context, R.string.empty_name_error, Toast.LENGTH_SHORT)
                toast.show()
                return false
            }
            else -> return true
        }
    }


    /**
     * Save action for the menu. If the event name is valid, pops the back
     * stack. Otherwise does nothing.
     */
    private fun save() {
        if (checkEventName()) findNavController().popBackStack()
    }

    /**
     * Delete action for the menu. In a coroutine, deletes the event using the
     * view model then pops the back stack.
     */
    private fun delete() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteEvent()
            findNavController().popBackStack()
        }
    }

    private inner class EventMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.event_fragment_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.delete_event) {

                delete()
                return true
            }

            else if (menuItem.itemId == R.id.save_event) {

                save()
                return true
            }
            return false
        }

    }

    /**
     * Updates the UI to match the Event. This also sets click listeners that
     * require access to the current event value (i.e. any that open a dialog) (event, date, start/end time).
     * @param event the event to use information from in all of the views
     */
    private fun updateUI(event: Event) {

        // Update the views to reflect the event object

        if(binding.eventName.text.toString() != event.name)
            binding.eventName.setText(event.name)

        if(binding.description.text.toString() != event.description)
            binding.eventName.setText(event.description)

        binding.eventIcon.setImageResource(event.type.iconResourceId)
        binding.date.text = event.startTime.toFullDateString()
        binding.startTime.text = event.startTime.toTimeString()

        if(event.endTime == null) {
            binding.till.visibility = View.INVISIBLE
            binding.endTime.visibility = View.INVISIBLE
        }

        else
        {
            binding.till.visibility = View.VISIBLE
            binding.endTime.visibility = View.VISIBLE
            binding.endTime.text = event.endTime?.toTimeString()
        }

        // Set up click listeners

        binding.eventIcon.setOnClickListener {
            findNavController().navigate(
                EventFragmentDirections.changeEventType(event.type.name)
            )
        }

        binding.date.setOnClickListener {
            findNavController().navigate(
                EventFragmentDirections.changeDate(event.startTime)
            )
        }

        binding.startTime.setOnClickListener {
            findNavController().navigate(
                EventFragmentDirections.changeTime(event.startTime, true)
            )
        }

        binding.endTime.setOnClickListener {
            findNavController().navigate(
                EventFragmentDirections.changeTime(event.endTime, false)
            )
        }


    }
}
