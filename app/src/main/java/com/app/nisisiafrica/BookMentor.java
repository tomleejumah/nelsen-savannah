package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.BookMentorStepAdapter;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.Event;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import kotlin.Unit;

public class BookMentor extends AppCompatActivity implements BookMentorStepAdapter.StepCompleteListener, SnackbarHandler {
    private static final String TAG = "BookMentor";
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton btnNext;
    private BookMentorStepAdapter adapter;
    private UserData userData;
    private String mentorId, mentorName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_book_mentor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null) {
            mentorId = intent.getStringExtra(Constants.MENTOR_ID);
            mentorName = intent.getStringExtra(Constants.MENTOR_NAME);
            Log.d(TAG, "onCreate: "+mentorName);
        }

        UserViewModel viewModel = new ViewModelProvider((this)).get(UserViewModel.class);
        String userID = Util.getState(Constants.CURRENT_USER_ID, "");
        viewModel.fetchingCurrentUserDataFromDB(userID).observe((this), data -> {
            if (data != null) {
                userData = data;
                adapter.setFirstName(userData.getFirstName());
                adapter.setLastName(userData.getLastName());
                adapter.notifyDataSetChanged();

            } else Log.d(TAG, "User data is null");
        });

        recyclerView = findViewById(R.id.recyclerView);
        btnNext = findViewById(R.id.btnNext);

        setupRecyclerView();
        setupNextButton();
    }

    private void setupRecyclerView() {
        adapter = new BookMentorStepAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager((this)));
        recyclerView.setAdapter(adapter);
    }

    private void setupNextButton() {
        updateButtonState(adapter.getCurrentStep());
        btnNext.setOnClickListener(v -> {
            int currentStep = adapter.getCurrentStep();

            if (adapter.isStepComplete(currentStep)) {
                if (currentStep == BookMentorStepAdapter.STEP_PAY) {
                    // Handle payment
                    handlePayment();
                } else {
                    // Move to next step
                    adapter.moveToNextStep();
                    // Scroll to current step
                    recyclerView.smoothScrollToPosition(adapter.getCurrentStep());
                }
            }
        });
    }

    private void updateButtonState(int currentStep) {
        boolean isStepComplete = adapter.isStepComplete(currentStep);

        // Enable/disable button
        btnNext.setEnabled(isStepComplete);
        btnNext.setAlpha(isStepComplete ? 1f : 0.5f);

        switch (currentStep) {
            case BookMentorStepAdapter.STEP_NAME,
                 BookMentorStepAdapter.STEP_CALENDAR,
                 BookMentorStepAdapter.STEP_TIME:
                btnNext.setText("Next");
                break;
            case BookMentorStepAdapter.STEP_PAY:
                btnNext.shrink();
                btnNext.setText("Book Now");
                break;
        }
    }

    private void handlePayment() {
        // Get booking data
        String firstName = adapter.getFirstName();
        String lastName = adapter.getLastName();
        String date = adapter.getSelectedDateFormatted(); // yyyy-MM-dd
        String time = adapter.getSelectedTimeFormatted(); // HH:mm

        // Process payment
        // TODO: Implement payment logic

        // Show success message or navigate to success screen


        // Get participant IDs
        String mentorId2 = mentorId; // or however you get mentor ID
        String menteeId = Util.getState(Constants.CURRENT_USER_ID, "");

// Create participants map
//        Map<String, Boolean> participants = new HashMap<>();
//        participants.put(mentorId2, true);
//        participants.put(menteeId, true);

        List<String> participants = new ArrayList<>();
        participants.add(mentorId2);
        participants.add(menteeId);

// Parse date and time
        String dateStr = adapter.getSelectedDateFormatted(); // yyyy-MM-dd
        String timeStr = adapter.getSelectedTimeFormatted(); // HH:mm

// Convert date string to timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long dateTimestamp = 0L;
        try {
            Date parsedDate = sdf.parse(dateStr);
            if (parsedDate != null) {
                dateTimestamp = parsedDate.getTime();
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

// Calculate end time (start time + 2 hours)
        String endTime = "";
        try {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date startDate = timeFormat.parse(timeStr);
            if (startDate != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(startDate);
                calendar.add(Calendar.HOUR_OF_DAY, 2); // Add 2 hours
                endTime = timeFormat.format(calendar.getTime());
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }


        UserViewModel sharedUserViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        sharedUserViewModel.fetchingCurrentUserDataFromDB(Constants.CURRENT_USER_ID).observe(
                this, data -> {
            if (data != null) userData = data; });

        Event event = new Event(
                "",// eventId (will be set in createEvent)
                "",
                dateTimestamp,                                // date
                timeStr,                                      // startTime
                endTime,                                      // endTime (start + 2 hours)
                "",
                mentorId2,
                menteeId,
                mentorName,
                userData.getFirstName() + " "+userData.getLastName(), //todo get user name from cache
                0,                                            // status
                null,                                         // description
                participants                                  // participants map
        );
//todo switch to view model/repository
        FirebaseRemoteDataSource.INSTANCE.createEvent(event,event.getMentorId(), event.getMenteeId(), success ->{
            if (success) {
                Log.d(TAG, "Event created successfully");
                // Navigate or show success message

               FirebaseRemoteDataSource.INSTANCE.createOrGetDirectChatRoom(event.getMentorId(),
                       event.getMentorName(), event.getMenteeName(),complete -> {
                   if (complete != null){
                       Log.d(TAG, "Chatroom created successfully");
                       startActivity(new Intent(BookMentor.this, MainActivity.class));
                       finish();
                   } else {
                       Log.e(TAG, "Failed to create chatroom");
                       finish();
                       showSnackbar("Failed to create chatroom", Snackbar.LENGTH_SHORT, 1);
                   }
                       return Unit.INSTANCE;
               });

            } else {
                Log.e(TAG, "Failed to create event");
            }
            return Unit.INSTANCE;
        });

// Create the event
     /*   Event event = new Event(
                "",                                           // eventId (will be set in createEvent)
                menteeId,                                     // userId (current user)
//      todo update this   adapter.getFirstName() + " " + adapter.getLastName(), // title
                "Mentor Appointment :" + mentorName,
                dateTimestamp,                                // date
                timeStr,                                      // startTime
                endTime,                                      // endTime (start + 2 hours)
                0,                                            // eventType
                mentorId2,                                     // mentorId
                0,                                            // status
                null,                                         // description
                participants                                  // participants map
        );
      */

// Save the event
//        FirebaseRemoteDataSource.INSTANCE.createEvent(event, success -> {
//            if (success) {
//                Log.d(TAG, "Event created successfully");
//                // Navigate or show success message
//            } else {
//                Log.e(TAG, "Failed to create event");
//            }
//      return Unit.INSTANCE;  });
    }

    @Override
    public void onStepChanged(int step) {
        setupNextButton();
    }

    @Override
    public void stepCompleteListener(boolean isComplete) {
        setupNextButton();
    }

    @Override
    public void showSnackbar(String message, int duration, int type) {

    }
}