package com.app.nisisiafrica.Auth

import com.app.nisisiafrica.Model.UserData

interface UserDataCallback {
    fun onUserDataReceived(userData: UserData?)
}