package com.app.nisisiafrica.Utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

/**
 * One Firestore listener summing {@code unreadCount[uid]} across the user's chat rooms.
 * Same query shape as the chat list, so it needs no extra rules. Call {@link #stop()} in onDestroy.
 */
public final class ChatUnreadTracker {
    public interface Listener { void onTotal(int total); }

    private ListenerRegistration registration;

    @SuppressWarnings("unchecked")
    public void start(@NonNull String uid, @NonNull Listener listener) {
        stop();
        registration = FirebaseFirestore.getInstance().collection("chatRooms")
                .whereArrayContains("userIds", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w("ChatUnread", "Unread listener failed", error);
                        return;
                    }
                    if (snapshot == null) return;
                    int total = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (UnreadTotals.isExcludedRoom(doc.getId(), doc.getString("type"))) continue;
                        Object raw = doc.get("unreadCount");
                        Map<String, ?> counts = raw instanceof Map ? (Map<String, ?>) raw : null;
                        total += UnreadTotals.countFor(counts, uid);
                    }
                    listener.onTotal(total);
                });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
