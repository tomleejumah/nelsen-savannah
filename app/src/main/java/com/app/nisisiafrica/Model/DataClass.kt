package com.app.nisisiafrica.Model

//import com.app.nisisiafrica.Auth.FacebookAuthHelper.UserData

data class DataClass(
   var userData : UserData
)

data class UserData (
    var id: String,
    var email: String,
    var displayName: String,
    var firstName: String,
    var lastName: String,
    var photoUrl: String,
    var idToken: String
)
