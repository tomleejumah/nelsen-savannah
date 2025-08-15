package com.app.nisisiafrica;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.Model.UserData;
import com.google.firebase.auth.FirebaseAuth;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BookMentor extends AppCompatActivity implements BookMentorStepAdapter.StepCompleteListener {
    private static final String TAG = "BookMentor";
    private RecyclerView recyclerView;
    private Button btnNext;
    private BookMentorStepAdapter adapter;
    private UserDao userDao;

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
        userDao = App.getUserDao();
//        currentUser = FirebaseAuth.getInstance().getCurrentUser().getUid();

        recyclerView = findViewById(R.id.recyclerView);
        btnNext = findViewById(R.id.btnNext);

        setupRecyclerView();
        setupNextButton();
        getName();
    }

    private void setupRecyclerView() {
        adapter = new BookMentorStepAdapter(this,this);
        recyclerView.setLayoutManager(new LinearLayoutManager((this)));
        recyclerView.setAdapter(adapter);
        // Update UI based on step completion
//        adapter.setStepCompleteListener(this::updateButtonState);
        // Initial button state
//        updateButtonState(adapter.getCurrentStep());
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

        // Update button text based on current step
        switch (currentStep) {
            case BookMentorStepAdapter.STEP_NAME,
                 BookMentorStepAdapter.STEP_CALENDAR,
                 BookMentorStepAdapter.STEP_TIME:
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

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private void getName() {
        Disposable disposable = userDao.getAllUsersRx()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(userDataList -> {
                    if (!userDataList.isEmpty()) {
                        // Just using the first user for this example
                        UserData user = userDataList.get(0);

                        adapter.setFirstName(user.getFirstName());
                        adapter.setLastName(user.getLastName());

                        Log.d(TAG, "First Name: " + user.getFirstName());
                        Log.d(TAG, "Last Name: " + user.getLastName());
                    } else {
                        Log.d(TAG, "No users found.");
                    }
                }, throwable -> Log.e(TAG, "Error fetching user data", throwable));

        compositeDisposable.add(disposable);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    @Override
    public void onStepChanged(int step) {
//        updateButtonState(step);
        setupNextButton();
    }

    @Override
    public void stepCompleteListener(boolean isComplete) {
        setupNextButton();
    }
}