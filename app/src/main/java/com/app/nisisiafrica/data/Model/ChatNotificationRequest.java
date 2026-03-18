package com.app.nisisiafrica.data.Model;

import com.google.gson.annotations.SerializedName;

public class ChatNotificationRequest {
    @SerializedName("receiverId")
    public String receiverId;

    @SerializedName("messageText")
    public String messageText;

    @SerializedName("conversationId")
    public String conversationId;

    public ChatNotificationRequest(String receiverId, String messageText, String conversationId) {
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.conversationId = conversationId;
    }
}