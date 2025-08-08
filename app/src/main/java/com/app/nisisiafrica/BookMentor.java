package com.app.nisisiafrica;

import static java.security.AccessController.getContext;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class BookMentor extends AppCompatActivity {
    private static final String TAG = "BookMentor";
    private RecyclerView recyclerView;
    private Button btnNext;
    private BookMentorStepAdapter adapter;
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

        recyclerView = findViewById(R.id.recyclerView);
        btnNext = findViewById(R.id.btnNext);

        setupRecyclerView();
        setupNextButton();
    }

    private void setupRecyclerView() {
        adapter = new BookMentorStepAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager((this)));
        recyclerView.setAdapter(adapter);
        // Update UI based on step completion
        adapter.setStepCompleteListener(this::updateButtonState);
        // Initial button state
        updateButtonState(adapter.getCurrentStep());
    }
    private void setupNextButton() {
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
                updateButtonState(currentStep);
            }
        });
    }

    private void updateButtonState(int currentStep) {
        boolean isStepComplete = adapter.isStepComplete(currentStep);

        // Enable/disable button
        btnNext.setEnabled(isStepComplete);
        btnNext.setAlpha(isStepComplete ? 1f : 0.5f);

        // Update button text based on current step
        switch (currentStep) {
            case BookMentorStepAdapter.STEP_NAME,
                 BookMentorStepAdapter.STEP_TIME,
                 BookMentorStepAdapter.STEP_CALENDAR:
                btnNext.setText("Next");
                break;
            case BookMentorStepAdapter.STEP_PAY:
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
}