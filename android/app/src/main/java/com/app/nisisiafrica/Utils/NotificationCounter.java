package com.app.nisisiafrica.Utils;

import androidx.annotation.NonNull;

import com.app.nisisiafrica.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NotificationCounter {

    public interface CountCallback {
        void onCountChanged(int count);
    }

    private static ValueEventListener listener;
    private static DatabaseReference activeRef;

    public static void startListening(CountCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onCountChanged(0);
            return;
        }

        stopListening();

        String currentUserId = user.getUid();
        activeRef = FirebaseDatabase.getInstance()
                .getReference("Notifications")
                .child(currentUserId);

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                int unreadCount = 0;
                for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                    Boolean read = child.child("read").getValue(Boolean.class);
                    if (read == null || !read) {
                        unreadCount++;
                    }
                }
                callback.onCountChanged(unreadCount);
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                callback.onCountChanged(0);
            }
        };

        activeRef.addValueEventListener(listener);
    }

    public static void stopListening() {
        if (listener != null && activeRef != null) {
            activeRef.removeEventListener(listener);
        }
        listener = null;
        activeRef = null;
    }
}
