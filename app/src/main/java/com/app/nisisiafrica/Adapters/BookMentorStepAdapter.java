package com.app.nisisiafrica.Adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Utils.CalendarBinder;
import com.app.nisisiafrica.R;
import com.github.vipulasri.timelineview.TimelineView;
import com.kizitonwose.calendar.view.CalendarView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BookMentorStepAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "BookMentorStepAdapter";
    public static final int STEP_NAME = 0;
    public static final int STEP_CALENDAR = 1;
    public static final int STEP_TIME = 2;
    public static final int STEP_PAY = 3;
    private final Context context;
    private int currentStep = 0;
    private String firstName, lastName;
    private LocalDate selectedDay;
    private LocalTime selectedTime;
    private Set<LocalDate> bookedDates = new HashSet<>();
    private final StepCompleteListener stepCompleteListener;

    // Callback interface for step completion && changed status
    public interface StepCompleteListener {
        void onStepChanged(int step);
        void stepCompleteListener(boolean isComplete);
    }

    public BookMentorStepAdapter(Context context, StepCompleteListener listener) {
        this.context = context;
        this.stepCompleteListener = listener;
        //todo pass dates fetched from mentor(from database)
        // Example booked dates - replace with actual data
        bookedDates.add(LocalDate.now().plusDays(2));
        bookedDates.add(LocalDate.now().plusDays(5));
        bookedDates.add(LocalDate.now().plusDays(8));
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);

        return switch (viewType) {
            case STEP_NAME ->
                    new NameViewHolder(inflater.inflate(R.layout.item_step_name, parent, false));
            case STEP_CALENDAR ->
                    new CalendarViewHolder(inflater.inflate(R.layout.item_step_calendar, parent, false));
            case STEP_TIME ->
                    new TimeViewHolder(inflater.inflate(R.layout.item_step_time, parent, false));
            case STEP_PAY ->
                    new PayViewHolder(inflater.inflate(R.layout.item_step_pay, parent, false));
            default -> throw new IllegalArgumentException("Invalid view type");
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        boolean isEnabled = position == currentStep; // Only current step is enabled
        boolean isCompleted = position < currentStep; // Previous steps are completed

        if (holder instanceof NameViewHolder) {
            ((NameViewHolder) holder).bind(isEnabled, isCompleted);
        } else if (holder instanceof TimeViewHolder) {
            ((TimeViewHolder) holder).bind(isEnabled, isCompleted);
        } else if (holder instanceof CalendarViewHolder) {
            ((CalendarViewHolder) holder).bind(isEnabled, isCompleted);
        } else if (holder instanceof PayViewHolder) {
            ((PayViewHolder) holder).bind(isEnabled, isCompleted);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        return switch (position) {
            case 0 -> STEP_NAME;
            case 1 -> STEP_CALENDAR;
            case 2 -> STEP_TIME;
            case 3 -> STEP_PAY;
            default -> -1;
        };
    }

    class NameViewHolder extends RecyclerView.ViewHolder {
        EditText edtFirstName, edtLastName;
        TimelineView timeline;

        public NameViewHolder(@NonNull View itemView) {
            super(itemView);
            edtFirstName = itemView.findViewById(R.id.edtFirstName);
            edtLastName = itemView.findViewById(R.id.edtLastName);
            timeline = itemView.findViewById(R.id.timeline);
        }

        public void bind(boolean isEnabled, boolean isCompleted) {
            // Set timeline state
            modifyTimeLine(isEnabled,isCompleted, timeline,getAdapterPosition());

            timeline.initLine(TimelineView.getTimeLineViewType(getAdapterPosition(), getItemCount()));

            // Set view state
            itemView.setEnabled(isEnabled);
            itemView.setAlpha(isEnabled ? 1f : 0.5f);
            edtFirstName.setEnabled(isEnabled);
            edtLastName.setEnabled(isEnabled);

            if (firstName == null && lastName == null){
                edtFirstName.requestFocus();
            }else edtLastName.requestFocus();


            // Set existing values if available
            if (firstName != null) edtFirstName.setText(getFirstName());
            if (lastName != null) edtLastName.setText(getLastName());

//            if (!isEnabled) return;
            // Trigger initial state update
            if (stepCompleteListener != null) {
                stepCompleteListener.onStepChanged(STEP_NAME);
            }

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    firstName = edtFirstName.getText().toString().trim();
                    lastName = edtLastName.getText().toString().trim();

                    if (stepCompleteListener != null) {
                        stepCompleteListener.stepCompleteListener(true);
                    }
                }
            };

            edtFirstName.addTextChangedListener(textWatcher);
            edtLastName.addTextChangedListener(textWatcher);

        }

    }

    class CalendarViewHolder extends RecyclerView.ViewHolder {
        CalendarView calendarView;
        TimelineView timeline;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            calendarView = itemView.findViewById(R.id.calendarView);
            timeline = itemView.findViewById(R.id.timeline);
        }

        @SuppressLint("ClickableViewAccessibility")
        public void bind(boolean isEnabled, boolean isCompleted) {
            // Set timeline state
            modifyTimeLine(isEnabled,isCompleted, timeline,getAdapterPosition());

            timeline.initLine(TimelineView.getTimeLineViewType(getAdapterPosition(), getItemCount()));
            itemView.setEnabled(isEnabled);
            itemView.setAlpha(isEnabled ? 1f : 0.5f);

            if (!isEnabled) return;

            // Trigger initial state update
            if (stepCompleteListener != null) {
                stepCompleteListener.onStepChanged(STEP_CALENDAR);
            }

            setupCalendar(calendarView);
        }

        private void setupCalendar(CalendarView calendarView) {
            View view = itemView.findViewById(R.id.layoutMonthHeader);
            TextView tvMonthTitle = view.findViewById(R.id.tvMonthTitle);

            CalendarBinder binder = new CalendarBinder(context,
                    bookedDates, null, true, date -> {
                // Handle date selection in adapter
                if (getAdapterPosition() != currentStep) return;
                selectedDay = date;
                if (stepCompleteListener != null) {
                    stepCompleteListener.stepCompleteListener(true);
                }
//                Toast.makeText(context, "Selected: " + date, Toast.LENGTH_SHORT).show();
                calendarView.notifyCalendarChanged();
            });

            binder.setup(calendarView, tvMonthTitle);
        }
    }

    class TimeViewHolder extends RecyclerView.ViewHolder {
        TimePicker timePicker;
        TimelineView timeline;

        public TimeViewHolder(@NonNull View itemView) {
            super(itemView);
            timePicker = itemView.findViewById(R.id.timePicker);
            timeline = itemView.findViewById(R.id.timeline);
        }

        public void bind(boolean isEnabled, boolean isCompleted) {
            // Set timeline state
            modifyTimeLine(isEnabled,isCompleted, timeline,getAdapterPosition());

            timeline.initLine(TimelineView.getTimeLineViewType(getAdapterPosition(), getItemCount()));

            itemView.setEnabled(isEnabled);
            itemView.findViewById(R.id.timePicker).setEnabled(isEnabled);
            itemView.setAlpha(isEnabled ? 1f : 0.5f);

            if (!isEnabled) return;

            // Trigger initial state update
            if (stepCompleteListener != null) {
                stepCompleteListener.onStepChanged(STEP_TIME);
            }

            setupTimeSelection();
        }

        private void setupTimeSelection() {
            //todo update ui to have time of day to select

            if (timePicker != null) {
//                boolean is24HourFormat = DateFormat.is24HourFormat(context);
                timePicker.setIs24HourView(false);
                timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
                    selectedTime = LocalTime.of(hourOfDay, minute);
                    if (stepCompleteListener != null) {
                        stepCompleteListener.stepCompleteListener(true);
                    }
                });

                // Set existing time if available
                if (selectedTime != null) {
                    timePicker.setHour(selectedTime.getHour());
                    timePicker.setMinute(selectedTime.getMinute());
                }
            }
        }

        @SuppressLint("DefaultLocale")
        private List<String> generateTimeSlots() {
            List<String> slots = new ArrayList<>();
            // Generate time slots from 9 AM to 5 PM
            for (int hour = 9; hour <= 17; hour++) {
                for (int minute = 0; minute < 60; minute += 30) {
                    slots.add(String.format("%02d:%02d", hour, minute));
                }
            }
            return slots;
        }

        public boolean isStepComplete() {
            return selectedTime != null;
        }
    }

    class PayViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookingSummary;
        TimelineView timeline;

        public PayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingSummary = itemView.findViewById(R.id.tvBookingSummary);
            timeline = itemView.findViewById(R.id.timeline);
        }

        public void bind(boolean isEnabled, boolean isCompleted) {
            // Set timeline state
            modifyTimeLine(isEnabled,isCompleted, timeline,getAdapterPosition());
            timeline.initLine(TimelineView.getTimeLineViewType(getAdapterPosition(), getItemCount()));

            itemView.setEnabled(isEnabled);
            itemView.setAlpha(isEnabled ? 1f : 0.5f);

            if (!isEnabled) return;

            if (stepCompleteListener != null) {
                stepCompleteListener.onStepChanged(STEP_PAY);
            }

            // Display booking summary
            String summary = buildBookingSummary();
            tvBookingSummary.setText(summary);
            //todo update to mentor calender
        }

        private String buildBookingSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Booking Summary:\n\n");

            if (firstName != null && lastName != null) {
                sb.append("Name: ").append(firstName).append(" ").append(lastName).append("\n");
            }

            if (selectedDay != null) {
                sb.append("Date: ").append(selectedDay.format(
                        DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"))).append("\n");
            }

            if (selectedTime != null) {
                sb.append("Time: ").append(selectedTime.format(
                        DateTimeFormatter.ofPattern("h:mm a"))).append("\n");
            }

            return sb.toString();
        }

    }

    // Public methods for external step management
    public boolean isStepComplete(int step) {
        return switch (step) {
            case STEP_NAME -> firstName != null && !firstName.isEmpty() &&
                    lastName != null && !lastName.isEmpty();
            case STEP_CALENDAR -> selectedDay != null;
            case STEP_TIME -> selectedTime != null;
            case STEP_PAY -> true;
            default -> false;
        };
    }

    private void modifyTimeLine(boolean isEnabled, boolean isCompleted, TimelineView timeline,int position) {
        if (isCompleted) {
            timeline.setMarker(ContextCompat.getDrawable(context, R.drawable.marker_completed));
            timeline.setStartLineColor(ContextCompat.getColor(context, R.color.timeline_active), (position));
            timeline.setEndLineColor(ContextCompat.getColor(context, R.color.timeline_active), (position));
            timeline.setLineStyle(TimelineView.LineStyle.NORMAL);
        } else if (isEnabled) {
            timeline.setMarker(ContextCompat.getDrawable(context, R.drawable.marker_active));
            timeline.setStartLineColor(ContextCompat.getColor(context, R.color.timeline_active), (position));
            timeline.setEndLineColor(ContextCompat.getColor(context, R.color.timeline_inactive), (position));
            timeline.setLineStyle(TimelineView.LineStyle.NORMAL);
        } else {
            timeline.setMarker(ContextCompat.getDrawable(context, R.drawable.marker_inactive));
            timeline.setStartLineColor(ContextCompat.getColor(context, R.color.timeline_inactive), (position));
            timeline.setEndLineColor(ContextCompat.getColor(context, R.color.timeline_inactive), (position));
            timeline.setLineStyle(TimelineView.LineStyle.DASHED);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void moveToNextStep() {
        if (currentStep < 3) {
            currentStep++;
            notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void moveToPreviousStep() {
        if (currentStep > 0) {
            currentStep--;
            notifyDataSetChanged();
        }
    }

    public int getCurrentStep() {
        return currentStep;
    }

    // Setter for booked dates
    public void setBookedDates(Set<LocalDate> bookedDates) {
        this.bookedDates = bookedDates;
    }

    // Public methods to get booking data
    public String getSelectedDateFormatted() {
        return selectedDay != null ? selectedDay.toString() : null; // yyyy-MM-dd format
    }

    public LocalDate getSelectedDate() {
        return selectedDay;
    }

    public LocalTime getSelectedTime() {
        return selectedTime;
    }

    public String getSelectedTimeFormatted() {
        return selectedTime != null ? selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}