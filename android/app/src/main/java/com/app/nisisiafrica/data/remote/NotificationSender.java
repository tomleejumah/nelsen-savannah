package com.app.nisisiafrica.data.remote;

import android.util.Log;

import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.data.Model.CommentNotificationRequest;
import com.app.nisisiafrica.data.Model.EventNotificationRequest;
import com.app.nisisiafrica.data.Model.LikeNotificationRequest;
import com.app.nisisiafrica.data.Model.NotificationResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Fire-and-forget helper for sending server push notifications through the
 * webhook ({@code api.tommlyjumah.dev/nisisi-africa/}). Each call fetches the
 * caller's Firebase ID token and enqueues the request; failures are logged but
 * never surfaced to the user since notifications are best-effort.
 */
public final class NotificationSender {

    private static final String TAG = "NotificationSender";

    private NotificationSender() {}

    private interface CallFactory {
        Call<NotificationResponse> create(NotificationApiService service, String bearer);
    }

    private static boolean isSelfOrEmpty(String recipientId) {
        String me = FirebaseAuth.getInstance().getUid();
        return recipientId == null || recipientId.isEmpty()
                || (me != null && me.equals(recipientId));
    }

    private static void send(CallFactory factory) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(result -> {
            String bearer = "Bearer " + result.getToken();
            NotificationApiService service = ApiClient.getNotificationService();
            factory.create(service, bearer).enqueue(new Callback<NotificationResponse>() {
                @Override
                public void onResponse(@androidx.annotation.NonNull Call<NotificationResponse> call,
                                       @androidx.annotation.NonNull Response<NotificationResponse> response) {
                    // best-effort; nothing to do
                }

                @Override
                public void onFailure(@androidx.annotation.NonNull Call<NotificationResponse> call,
                                      @androidx.annotation.NonNull Throwable t) {
                    Log.e(TAG, "push failed", t);
                }
            });
        });
    }

    /** Notify {@code authorId} that someone upvoted/liked their post. */
    public static void like(String authorId, String postId, String text) {
        if (isSelfOrEmpty(authorId)) return;
        send((s, b) -> s.sendLikeNotification(b, new LikeNotificationRequest(authorId, postId, text)));
    }

    /** Notify {@code authorId} that someone commented on their post. */
    public static void comment(String authorId, String postId, String text, String commentText) {
        if (isSelfOrEmpty(authorId)) return;
        send((s, b) -> s.sendCommentNotification(
                b, new CommentNotificationRequest(authorId, postId, text, commentText)));
    }

    /** Notify {@code recipientId} that a new event/session was scheduled with them. */
    public static void event(String recipientId, String eventId, String eventTitle, String text) {
        if (isSelfOrEmpty(recipientId)) return;
        send((s, b) -> s.sendEventNotification(
                b, new EventNotificationRequest(recipientId, eventId, eventTitle, text)));
    }
}
