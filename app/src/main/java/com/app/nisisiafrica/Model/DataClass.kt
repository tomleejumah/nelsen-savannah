package com.app.nisisiafrica.Model

import android.os.Parcel
import android.os.Parcelable

data class DataClass(
   var userData : UserData
)

data class UserData (
    var id: String,
    var email: String,
    var userTYpe: String,
    var displayName: String? = null,
    var firstName: String,
    var lastName: String,
    var photoUrl: String? = null,
    var idToken: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(userTYpe)
        parcel.writeString(email)
        parcel.writeString(displayName)
        parcel.writeString(firstName)
        parcel.writeString(lastName)
        parcel.writeString(photoUrl)
        parcel.writeString(idToken)
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

