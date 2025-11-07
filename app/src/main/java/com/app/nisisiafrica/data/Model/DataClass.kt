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

enum class QuestionType { RADIO, CHECKBOX, TEXT }

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

data class Chatroom(
    val chatroomId: String = "",
    val userIds: List<String> = emptyList(),
    val lastMessageTimestamp: Timestamp? = null,
    val lastMessageSenderId: String = "",
    val lastMessage: String? = null,
    val unreadCount: Map<String, Int> = emptyMap()
)

data class Message(
    val messageId : String,
    private var message: String? = null,
    var senderId: String? = null,
    var timestamp: Timestamp
)

data class Event(
    val eventId: String = "",
    val userId: String = "",
    val title: String = "",
    val date: Long = 0L,
    val startTime: String = "",
    val endTime: String = "",
    val eventType: Int = 0, // 1 = appointment, 2 = app event
    val mentorId: String? = null, // only for appointments
    val status: Int = 0, // 0 = upcoming, 1 = completed, 2 = today
    val description: String? = null
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

