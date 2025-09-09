package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.R;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder;
import com.kizitonwose.calendar.view.ViewContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;

public class CalendarBinder {

    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }

    private final Context context;
    private final Set<LocalDate> mentorBookedDates;
    private final Set<LocalDate> myBookedDates;
    private final boolean showMentorBooked;
    private final OnDateSelectedListener listener;

    private LocalDate selectedDay;
    private TextView monthTitleText;

    public CalendarBinder(Context context,
                          Set<LocalDate> mentorBookedDates,
                          Set<LocalDate> myBookedDates,
                          boolean showMentorBooked,
                          OnDateSelectedListener listener) {
        this.context = context;
        this.mentorBookedDates = mentorBookedDates != null ? mentorBookedDates : Collections.emptySet();
        this.myBookedDates = myBookedDates != null ? myBookedDates : Collections.emptySet();
        this.showMentorBooked = showMentorBooked;
        this.listener = listener;
    }

    public void setup(CalendarView calendarView, TextView monthTitleText) {
        this.monthTitleText = monthTitleText;

        calendarView.setDayBinder(new MonthDayBinder<DayViewContainer>() {
            @NonNull
            @Override
            public DayViewContainer create(@NonNull android.view.View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(@NonNull DayViewContainer container, CalendarDay day) {
                container.bind(day);
            }
        });

        calendarView.setMonthHeaderBinder(new MonthHeaderFooterBinder<MonthViewContainer>() {
            @NonNull
            @Override
            public MonthViewContainer create(@NonNull android.view.View view) {
                return new MonthViewContainer(view);
            }

            @Override
            public void bind(@NonNull MonthViewContainer container, CalendarMonth month) {
                container.bind(month);
            }
        });

        calendarView.setMonthScrollListener(month -> {
            String title = month.getYearMonth()
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            if (this.monthTitleText != null) {
                this.monthTitleText.setText(title);
            }
            return null;
        });

        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.plusMonths(6);
        calendarView.setup(currentMonth, lastMonth, DayOfWeek.SUNDAY);
        calendarView.scrollToMonth(currentMonth);
    }

    private class DayViewContainer extends ViewContainer {
        TextView tvDayText;
        android.view.View viewBookingIndicator;
        LocalDate day;

        DayViewContainer(@NonNull android.view.View view) {
            super(view);
            tvDayText = view.findViewById(R.id.calendarDayText);
            viewBookingIndicator = view.findViewById(R.id.viewBookingIndicator);

            view.setOnClickListener(v -> {
                if (day != null && day.isAfter(LocalDate.now().minusDays(1))) {
                    boolean isMentorBooked = showMentorBooked && mentorBookedDates.contains(day);
                    if (isMentorBooked) {
                        Toast.makeText(context, "This date is not available", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedDay = day;
                    if (listener != null) {
                        listener.onDateSelected(day);
                    }
                }
            });
        }

        void bind(CalendarDay calendarDay) {
            day = calendarDay.getDate();
            tvDayText.setText(String.valueOf(day.getDayOfMonth()));

            if (calendarDay.getPosition() != DayPosition.MonthDate) {
                tvDayText.setVisibility(android.view.View.INVISIBLE);
                viewBookingIndicator.setVisibility(android.view.View.GONE);
                return;
            }

            tvDayText.setVisibility(android.view.View.VISIBLE);

            boolean isPastDate = day.isBefore(LocalDate.now());
            boolean isMentorBooked = mentorBookedDates.contains(day);
            boolean isMyBooked = myBookedDates.contains(day);
            boolean isSelected = day.equals(selectedDay);

            // Default state
            tvDayText.setBackgroundColor(Color.TRANSPARENT);
            tvDayText.setTextColor(Color.BLACK);
            viewBookingIndicator.setVisibility(android.view.View.GONE);

            if (isPastDate) {
                // Past dates
                tvDayText.setTextColor(Color.GRAY);
            } else if (isSelected) {
                // Selected date → blue background
                tvDayText.setBackgroundColor(ContextCompat.getColor(context, R.color.blue));
                tvDayText.setTextColor(Color.WHITE);
            } else if (showMentorBooked && isMentorBooked) {
                // Mentor booked → red underline
                showIndicator(R.color.red);
            } else if (isMyBooked) {
                // My booked date → blue underline
                showIndicator(R.color.blue);
            }
        }

        private void showIndicator(int colorRes) {
            viewBookingIndicator.setVisibility(android.view.View.VISIBLE);
            viewBookingIndicator.setBackgroundColor(ContextCompat.getColor(context, colorRes));
        }
    }

    private class MonthViewContainer extends ViewContainer {
        TextView tvMonthTitle;

        MonthViewContainer(@NonNull android.view.View view) {
            super(view);
            tvMonthTitle = view.findViewById(R.id.tvMonthTitle);
        }

        void bind(CalendarMonth month) {
            tvMonthTitle.setText(month.getYearMonth()
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        }
    }
}