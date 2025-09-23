package com.app.nisisiafrica;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.BookMentorStepAdapter;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.SharedUserViewModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class BookMentor extends AppCompatActivity implements BookMentorStepAdapter.StepCompleteListener {
    private static final String TAG = "BookMentor";
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton btnNext;
    private BookMentorStepAdapter adapter;
    private UserData userData;

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

        SharedUserViewModel viewModel = new ViewModelProvider((this)).get(SharedUserViewModel.class);
        String userID = Util.getState(Constants.CURRENT_USER_ID, "");
        viewModel.fetchingCurrentUserDataFromDB(userID).observe((this), data -> {
            if (data != null) {
                userData = data;
                adapter.setFirstName(userData.getFirstName());
                adapter.setLastName(userData.getLastName());
                adapter.notifyDataSetChanged();

            }else Log.d(TAG, "User data is null");
        });

        recyclerView = findViewById(R.id.recyclerView);
        btnNext = findViewById(R.id.btnNext);

        setupRecyclerView();
        setupNextButton();
    }
    private void setupRecyclerView() {
        adapter = new BookMentorStepAdapter(this,this);
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
    }
    @Override
    public void onStepChanged(int step) {
        setupNextButton();
    }

    @Override
    public void stepCompleteListener(boolean isComplete) {
        setupNextButton();
    }
}