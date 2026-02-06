package com.app.nisisiafrica.data.Model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class DataClass(
    var userData: UserData
)

enum class QuestionType { RADIO, CHECKBOX, TEXT ,DOCUMENT_UPLOAD, VIDEO_UPLOAD}

data class Question(
    val id: String,
    val text: String,
    val type: QuestionType,
    val options: List<String> = emptyList()
)

data class Booking(
    val id: Int,
    val mentorId: String = "",
    val studentId: String = "",
    val date: String = "",
    val time: String = ""
) {
    fun toLocalDateTime(): LocalDateTime {
        val localDate = LocalDate.parse(date)
        val localTime = LocalTime.parse(time)
        return LocalDateTime.of(localDate, localTime)
    }
}

data class Section(
    val id: String,
    val title: String,
    val questions: List<Question>
)

data class CourseItem(
    val courseId: String = "",
    val tutorId: String = "",
    val courseImageUrl: String = "",
    val tutorAvatarUrl: String = "",
    val tutorName: String = "",
    val courseTitle: String = "",
    val duration: String = "",
    val lessons: String = "",
    val courseLink: String = "",
    val isLiked: Boolean = false
)

data class MentorItem(
    val mentorId: String = "",
    val mentorImageUrl: String = "",
    val mentorName: String = "",
    val mentorDescription: String = "",
    val studentsCount: String? = null,
    val studentImages: List<String>? = null,
    val bookedDates: Set<LocalDate>? = null

//    val isOnline: Boolean = false
)

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val type: String = "text"
)

data class Chatroom(
    val chatroomId: String = "",
    val userIds: List<String> = emptyList(),
    val userNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Timestamp? = null,
    val lastMessageSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap()
) {
    /**
     * Get the other user's name (not the current user)
     */
    fun getOtherUserName(currentUserId: String): String {
        // Find the other user's ID (the one that's not current user)
        val otherUserId = userIds.firstOrNull { it != currentUserId }

        // Get their name from the map
        return if (otherUserId != null) {
            userNames[otherUserId] ?: "Unknown User"
        } else {
            "Unknown User"
        }
    }

    /**
     * Get the other user's ID
     */
    fun getOtherUserId(currentUserId: String): String? {
        return userIds.firstOrNull { it != currentUserId }
    }

    /**
     * Check if this is the announcements room
     */
    fun isAnnouncementRoom(): Boolean {
        return chatroomId == "announcements"
    }

    /**
     * Get display name for the chatroom
     */
    fun getDisplayName(currentUserId: String): String {
        return if (isAnnouncementRoom()) {
            "Announcements"
        } else {
            getOtherUserName(currentUserId)
        }
    }
}

data class Message(
    val messageId: String,
    private var message: String? = null,
    var senderId: String? = null,
    var timestamp: Timestamp
)

data class LikeNotificationRequest(
    var coursePublisher: String,
    var postID: String,
    var text: String
)

data class NotificationResponse(
    var success: Boolean,
    var message: String,
    var notificationId: String,
    var fcmSent: Boolean
)

data class NotificationListResponse(
    val notifications: List<NotificationData> = emptyList()
)

data class NotificationData(
    var id: String = "",
    var senderId: String = "",
    var text: String = "",
    var courseID: String = "",
    var type: String = "",
    var timestamp: Long = 0L,
    var read: Boolean = false,

    @Transient var senderName: String? = null,
    @Transient var senderAvatar: String? = null,
    @Transient var courseName: String? = null,
    @Transient var courseImage: String? = null
)

data class Event(
    val eventId: String = "",
//    val userId: String = "",
    val title: String = "",
    val date: Long = 0L,
    val startTime: String = "",
    val endTime: String = "",
    val eventType: String = "",
    val mentorId: String = "",      // Change from String? to String
    val menteeId: String = "",      // Add this field
    val mentorName: String = "",
    val menteeName: String = "",
    val status: Int = 0,
    val description: String? = null,
//    val participants: Map<String, Boolean>? = null
    val participants: List<String>? = null
)

data class Announcement(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Long = 0L,
    val targetAudience: String = "all"  // "all", "mentors", "mentees"
)

@Entity(tableName = "user_data")
data class UserData(
    @PrimaryKey var id: String,
    var email: String,
    var userRole: String? = null,
    var displayName: String? = null,
    var firstName: String,
    var lastName: String,
    var photoUrl: String? = null,
    var bio: String? = null,
    var lastLogin: Long? = null
)

    : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong()

    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(userRole)
        parcel.writeString(email)
        parcel.writeString(displayName)
        parcel.writeString(firstName)
        parcel.writeString(lastName)
        parcel.writeString(photoUrl)
        parcel.writeString(bio)
        parcel.writeLong(lastLogin ?: 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<UserData> {
        override fun createFromParcel(parcel: Parcel): UserData {
            return UserData(parcel)
        }

        override fun newArray(size: Int): Array<UserData?> {
            return arrayOfNulls(size)
        }
    }
}

