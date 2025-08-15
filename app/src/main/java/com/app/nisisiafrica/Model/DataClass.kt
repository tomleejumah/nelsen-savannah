package com.app.nisisiafrica.Model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

data class DataClass(
   var userData : UserData
)

data class CourseItem(
    val courseImageUrl: String,
//    val isOnline: Boolean,
    val tutorAvatarUrl: String,
    val tutorName: String,
    val courseTitle: String,
    val duration: String,
    val lessons: String,
    val courseLink: String,
    val isLiked: Boolean,
)

data class MentorItem(
    val mentorImageUrl: String,
    val mentorName: String,
    val mentorDescription: String,
    val studentsCount: String,
    val studentImages: List<String>,
    val bookedDates: Set<LocalDate>,
    val courses: List<CourseItem>,
//    val mentorLink: String,
)

//todo remove the parceble boiler code
@Entity(tableName = "user_data")
data class UserData (
    @PrimaryKey var id: String,
    var email: String,
    var userRole: String? = null,
    var displayName: String? = null,
    var firstName: String,
    var lastName: String,
    var photoUrl: String? = null,
    var bio: String? = null,
    var lastLogin: Long? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong() ?: 0

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

