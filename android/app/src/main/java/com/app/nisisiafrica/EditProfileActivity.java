package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Adapters.CoursesAdapter;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.FirebaseDatabase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import kotlin.Unit;

public class EditProfileActivity extends AppCompatActivity {
    private static final String TAG = "EditProfileActivity";
    Dialog dialog;
    MaterialButton btnCancel, btnConfirm;
    boolean isMentor;
    private UserData userData;
    private String id, firstName, lastName, description, name, dpImageUrl;
    private CourseItem courseItem;
    private MentorItem mentorItem;
    private CoursesAdapter coursesAdapter;
    private RecyclerView rcCourses;
    private List<CourseItem> courseList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null) {
            isMentor = intent.getBooleanExtra(Constants.IS_MENTOR, false);
            id = intent.getStringExtra(Constants.CURRENT_USER_ID);
        }

        // Fall back to the cached/authenticated uid so we never query Room with null.
        if (id == null || id.isEmpty()) {
            id = com.app.nisisiafrica.Utils.Util.getState(Constants.CURRENT_USER_ID, "");
        }
        if (id == null || id.isEmpty()) {
            com.google.firebase.auth.FirebaseUser fu =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fu != null) id = fu.getUid();
        }
        Log.d(TAG, "onCreate: " + id);

        if (id != null && !id.isEmpty()) {
            UserViewModel sharedUserViewModel = new ViewModelProvider(this).get(UserViewModel.class);
            sharedUserViewModel.fetchingCurrentUserDataFromDB(id).observe(this, data -> {
                if (data != null) {
                    firstName = data.getFirstName();
                    lastName = data.getLastName();
                    description = data.getBio();
                    dpImageUrl = data.getPhotoUrl();
                    name = firstName + " " + lastName;

                    userData = data;

                    Glide.with(this).load(data.getPhotoUrl()).into((CircleImageView) findViewById(R.id.editprofileImage));
                    ((EditText) findViewById(R.id.FirstNameEditText)).setText(firstName);
                    ((EditText) findViewById(R.id.LastNameEditText)).setText(lastName);
                    ((EditText) findViewById(R.id.txtDescription)).setText(description);
                }
            });
        }

        CircleImageView editprofileImage = findViewById(R.id.editprofileImage);
        editprofileImage.setOnClickListener(v -> {
            CustomSnackbar.show(this, "Coming Soon", Snackbar.LENGTH_SHORT, 4);
        });

        findViewById(R.id.editprofileImageView).setOnClickListener(v -> {
            CustomSnackbar.show(this, "Coming Soon", Snackbar.LENGTH_SHORT, 4);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.coursesTitle).setVisibility(isMentor ? View.VISIBLE : View.GONE);
        findViewById(R.id.addCoursesBtn).setVisibility(isMentor ? View.VISIBLE : View.GONE);

        EditText txtDescription = findViewById(R.id.txtDescription);

        rcCourses = findViewById(R.id.rcCourses);
        if (isMentor) {
            LinearLayoutManager layoutManager = new LinearLayoutManager((this), LinearLayoutManager.VERTICAL, false);
            rcCourses.setLayoutManager(layoutManager);
            coursesAdapter = new CoursesAdapter(this);
            rcCourses.setAdapter(coursesAdapter);
            setUpDialog();
            fetchCoursesById(id);

        } else {
            //fetch Mentee Media
        }

        findViewById(R.id.addCoursesBtn).setOnClickListener(v -> {
            dialog.show();
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            firstName = ((EditText) findViewById(R.id.FirstNameEditText)).getText().toString();
            lastName = ((EditText) findViewById(R.id.LastNameEditText)).getText().toString();
            description = txtDescription.getText().toString();

            if (firstName.isEmpty() || lastName.isEmpty()) {
                CustomSnackbar.show(this, "Please enter all name", Snackbar.LENGTH_SHORT, 4);
                return;
            }

            //update profile
            if (isMentor) {
                name = firstName + " " + lastName;
                updateMentorProfile(name, description);

            } else {
                updateMenteeProfile(firstName, lastName, description);
//               updateMedia(gallerieItema); 
            }
        });
    }

    private void updateMentorProfile(String name, String description) {

        //todo use realtime student count/images/booked dates

        mentorItem = new MentorItem(id, dpImageUrl, name, description, "", new ArrayList<>(), new HashSet<>(), null, null);
        FirebaseRemoteDataSource.INSTANCE.saveOrUpdateMentor(mentorItem, aBoolean -> {
            Log.d(TAG, "updateMentorProfile: " + aBoolean);
            //update bio
            if (!mentorItem.getMentorDescription().isEmpty()) {
                FirebaseRemoteDataSource.INSTANCE.updateUserBio(id, mentorItem.getMentorDescription());
            }
            updateCourseList(id);
            return Unit.INSTANCE;
        }, e -> {
            Log.e(TAG, "Failed to add mentor", e);
            return Unit.INSTANCE;
        });

    }

    @SuppressLint("CheckResult")
    private void updateMenteeProfile(String firstName, String lastName, String description) {
        userData.setFirstName(firstName);
        userData.setLastName(lastName);
        userData.setBio(description);
        FirebaseRemoteDataSource.INSTANCE.saveOrUpdateUser(userData,
                FirebaseDatabase.getInstance().getReference().child("users"), aBoolean -> {

                    UserViewModel viewModel = new ViewModelProvider(this).get(UserViewModel.class);
                    viewModel.updateUserDataa(userData)
                            .subscribe(
                                    () -> {
                                        Log.d(TAG, "User updated successfully");

                                        CustomSnackbar.show(this, "Profile Update", Snackbar.LENGTH_SHORT, 1);
                                        Intent intent = new Intent((this), ProfileActivity.class);
                                        intent.putExtra(Constants.IS_MENTOR, false);
                                        intent.putExtra(Constants.CURRENT_USER_ID, userData.getId());
                                        startActivity(intent);
                                        finish();
                                    },
                                    throwable -> {
                                        Log.e(TAG, "Error updating user", throwable);
                                        CustomSnackbar.show(this, "Database update failed", Snackbar.LENGTH_SHORT, 3);
                                    }
                            );
                    return Unit.INSTANCE;
                }, e -> {
                    CustomSnackbar.show(this, "Failed check on your internet and retry", Snackbar.LENGTH_SHORT, 3);
                    Log.e(TAG, "Failed to update user", e);
                    return Unit.INSTANCE;
                });

    }

    private void fetchCoursesById(String id) {
        FirebaseRemoteDataSource.INSTANCE.fetchCoursesByMentorId(id, new FirebaseCallback() {
            @Override
            public void onUserDataReceived(@Nullable UserData userData) {

            }

            @Override
            public void onMentorDataFetched(@Nullable MentorItem mentors) {
            }

            @Override
            public void onMentorsIDFetched(@Nullable List<@Nullable String> mentorIds) {
            }

            @Override
            public void onCoursesFetched(@Nullable List<CourseItem> courses) {

                if (courses == null) return;
                courseList.clear();
                courseList.addAll(courses);
                coursesAdapter.notifyItemInserted(courseList.size() - 1);
            }

            @Override
            public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {
            }

            @Override
            public void onError(@Nullable Exception e) {
                Log.e(TAG, "Failed to fetch courses", e);
            }
        });
    }

    private void setUpDialog() {
        dialog = new Dialog(EditProfileActivity.this);
        dialog.setContentView(R.layout.dialog_add_course);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(getDrawable(R.drawable.bg_card));
        dialog.setCancelable(false);

        EditText courseTitle = dialog.findViewById(R.id.etCourseTitle);
        EditText courseDuration = dialog.findViewById(R.id.etDuration);
        EditText courseLessons = dialog.findViewById(R.id.etLessons);
        EditText courseLink = dialog.findViewById(R.id.etCourseLink);
        EditText courseImageUrl = dialog.findViewById(R.id.etCourseImageUrl);

        btnCancel = dialog.findViewById(R.id.btnCancel);
        btnConfirm = dialog.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                btnConfirm.setEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String title = courseTitle.getText().toString().trim();
                String duration = courseDuration.getText().toString().trim();
                String lessons = courseLessons.getText().toString().trim();
                String link = courseLink.getText().toString().trim();
                String imageUrl = courseImageUrl.getText().toString().trim();

                btnConfirm.setEnabled(!title.isEmpty() && !duration.isEmpty() && !lessons.isEmpty() && !link.isEmpty() && !imageUrl.isEmpty());
            }
        };

        courseTitle.addTextChangedListener(textWatcher);
        courseDuration.addTextChangedListener(textWatcher);
        courseLessons.addTextChangedListener(textWatcher);
        courseLink.addTextChangedListener(textWatcher);
        courseImageUrl.addTextChangedListener(textWatcher);

        btnConfirm.setOnClickListener(v -> {
            String title = courseTitle.getText().toString().trim();
            String duration = courseDuration.getText().toString().trim();
            String lessons = courseLessons.getText().toString().trim();
            String link = courseLink.getText().toString().trim();
            String imageUrl = courseImageUrl.getText().toString().trim();

            courseItem = new CourseItem("", id, imageUrl, dpImageUrl, name, title, duration, lessons, link, false, "");
            courseList.add(courseItem);
            coursesAdapter.notifyItemInserted(courseList.size() - 1);
            dialog.dismiss();

        });
    }

    private void updateCourseList(String mentorId) {
        if (courseList.isEmpty()) {
            CustomSnackbar.show(this, "Updated", Snackbar.LENGTH_SHORT, 4);
            finish();
            return;
        }
        FirebaseRemoteDataSource.INSTANCE.saveOrUpdateCourse(courseItem, mentorId, aBoolean -> {
            Log.d(TAG, "updateCourseList: " + aBoolean);
            CustomSnackbar.show(this, "Profile Update & Course Success", Snackbar.LENGTH_SHORT, 4);
            finish();
            return Unit.INSTANCE;
        }, e -> {
            CustomSnackbar.show(this, "Failed check on your internet and retry", Snackbar.LENGTH_SHORT, 3);
            Log.e(TAG, "Failed to add course", e);
            return Unit.INSTANCE;
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseRemoteDataSource.INSTANCE.stopFetchingCoursesByMentorId();
    }
}