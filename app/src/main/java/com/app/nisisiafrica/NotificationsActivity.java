package com.app.nisisiafrica;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.NotificationAdapter;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.data.Model.NotificationData;
import com.app.nisisiafrica.data.Model.NotificationListResponse;
import com.app.nisisiafrica.data.Model.NotificationResponse;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private List<NotificationData> notificationList;
    private ProgressBar progressBar;
    private LinearLayout tvEmpty;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.recyclerViewNotifications);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(this, notificationList, this::markNotificationAsRead);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        loadNotifications();
    }
    private void loadNotifications() {
        progressBar.setVisibility(View.VISIBLE);

        FirebaseAuth.getInstance().getCurrentUser().getIdToken(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult().getToken();

                        NotificationApiService service = ApiClient.getNotificationService();
                        Call<NotificationListResponse> call = service.getUserNotifications(
                                "Bearer " + token,
                                currentUserId
                        );

                        call.enqueue(new Callback<NotificationListResponse>() {
                            @Override
                            public void onResponse(Call<NotificationListResponse> call,
                                                   Response<NotificationListResponse> response) {
                                progressBar.setVisibility(View.GONE);

                                if (response.isSuccessful() && response.body() != null) {
                                    notificationList.clear();
                                    notificationList.addAll(response.body().getNotifications());

                                    if (notificationList.isEmpty()) {
                                        tvEmpty.setVisibility(View.VISIBLE);
                                        recyclerView.setVisibility(View.GONE);
                                    } else {
                                        tvEmpty.setVisibility(View.GONE);
                                        recyclerView.setVisibility(View.VISIBLE);
                                        loadNotificationDetails();
                                    }
                                } else {
                                    Log.e(TAG, "API Error: " + response.code());
                                    tvEmpty.setVisibility(View.VISIBLE);
                                }
                            }

                            @Override
                            public void onFailure(Call<NotificationListResponse> call, Throwable t) {
                                progressBar.setVisibility(View.GONE);
                                tvEmpty.setVisibility(View.VISIBLE);
                                Log.e(TAG, "Network error: " + t.getMessage());
                            }
                        });
                    }
                });
    }

    private void loadNotificationDetails() {
        for (int i = 0; i < notificationList.size(); i++) {
            final int position = i;
            NotificationData notification = notificationList.get(position);

            // Load sender details from Firebase
            FirebaseDatabase.getInstance().getReference("users")
                    .child(notification.getSenderId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String username = snapshot.child("lastName").getValue(String.class);
                            String avatar = snapshot.child("photoUrl").getValue(String.class);

                            notification.setSenderName(username != null ? username : "User");
                            notification.setSenderAvatar(avatar);
                            adapter.notifyItemChanged(position);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error loading user: " + error.getMessage());
                        }
                    });

            // Load course details from Firebase
             FirebaseDatabase.getInstance().getReference("courses")
                    .child(notification.getCourseID())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String courseName = snapshot.child("courseTitle").getValue(String.class);
                            String courseImage = snapshot.child("courseImageUrl").getValue(String.class);

                            notification.setCourseName(courseName != null ? courseName : "Course");
                            notification.setCourseImage(courseImage);
                            adapter.notifyItemChanged(position);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error loading course: " + error.getMessage());
                        }
                    });
        }
    }

    private void markNotificationAsRead(NotificationData notification) {
        if (notification.getRead()) {
            return; // Already read
        }

        FirebaseAuth.getInstance().getCurrentUser().getIdToken(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult().getToken();

                        NotificationApiService service = ApiClient.getNotificationService();
                        Call<NotificationResponse> call = service.markAsRead(
                                "Bearer " + token,
                                currentUserId,
                                notification.getId()
                        );

                        call.enqueue(new Callback<>() {
                            @Override
                            public void onResponse(Call<NotificationResponse> call,
                                                   Response<NotificationResponse> response) {
                                if (response.isSuccessful()) {
                                    notification.setRead(true);
                                    adapter.notifyDataSetChanged();
                                }
                            }

                            @Override
                            public void onFailure(Call<NotificationResponse> call, Throwable t) {
                                Log.e(TAG, "Failed to mark as read: " + t.getMessage());
                            }
                        });
                    }
                });
    }
}