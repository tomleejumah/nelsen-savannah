package com.app.nisisiafrica.Interfaces;

import com.app.nisisiafrica.data.Model.ChatNotificationRequest;
import com.app.nisisiafrica.data.Model.LikeNotificationRequest;
import com.app.nisisiafrica.data.Model.NotificationListResponse;
import com.app.nisisiafrica.data.Model.NotificationResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface NotificationApiService {
    @POST("notifications/like")
    Call<NotificationResponse> sendLikeNotification(
            @Header("Authorization") String token,
            @Body LikeNotificationRequest request
    );

//    @POST("notifications/comment")
//    Call<NotificationResponse> sendCommentNotification(
//            @Header("Authorization") String token,
//            @Body CommentNotificationRequest request
//    );

    @GET("notifications/{userId}")
    Call<NotificationListResponse> getUserNotifications(
            @Header("Authorization") String token,
            @Path("userId") String userId
    );

    @PATCH("notifications/{userId}/{notificationId}/read")
    Call<NotificationResponse> markAsRead(
            @Header("Authorization") String token,
            @Path("userId") String userId,
            @Path("notificationId") String notificationId
    );

    @DELETE("notifications/{userId}/{notificationId}")
    Call<NotificationResponse> deleteNotification(
            @Header("Authorization") String token,
            @Path("userId") String userId,
            @Path("notificationId") String notificationId
    );

    @POST("chat/notify")
    Call<NotificationResponse> sendChatNotification(
            @Header("Authorization") String token,
            @Body ChatNotificationRequest request
    );
}
