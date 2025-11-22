package com.app.nisisiafrica.Utils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class NotificationCounter {

    public interface CountCallback {
        void onCountChanged(int count);
    }

    private static ValueEventListener listener;

    public static void startListening(CountCallback callback) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference notifRef = FirebaseDatabase.getInstance()
                .getReference("Notifications")
                .child(currentUserId);

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean read = child.child("read").getValue(Boolean.class);
                    if (read == null || !read) {
                        unreadCount++;
                    }
                }

                callback.onCountChanged(unreadCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onCountChanged(0);
            }
        };

        notifRef.addValueEventListener(listener);
    }

    public static void stopListening() {
        if (listener != null) {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseDatabase.getInstance()
                    .getReference("Notifications")
                    .child(currentUserId)
                    .removeEventListener(listener);
        }
    }
}
