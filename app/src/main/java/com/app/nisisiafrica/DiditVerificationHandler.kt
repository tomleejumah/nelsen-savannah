package com.app.nisisiafrica
import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.app.nisisiafrica.Interfaces.KYCCallBack
import com.app.nisisiafrica.data.Model.DiditSessionRequest
import com.app.nisisiafrica.data.Model.DiditSessionResponse
import com.app.nisisiafrica.data.remote.ApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.didit.sdk.DiditSdk
import me.didit.sdk.DiditSdkState
import me.didit.sdk.VerificationResult
import me.didit.sdk.models.ContactDetails
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.jvm.functions.Function1

class DiditVerificationHandler(
    private val activity: Activity,
    private val apiClient: KYCCallBack,
    private val onStatusChanged: Function1<String, Unit>
) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private var statusListener: ValueEventListener? = null
    private var stateJob: Job? = null
    private  val TAG = "DiditVerificationHandle"

    fun startVerification(onSdkReady: (() -> Unit)? = null) {
        val user = auth.currentUser ?: run {
            onSdkReady?.invoke()
            onStatusChanged.invoke("error")
            return
        }

        apiClient.createDiditSession(DiditSessionRequest(user.uid))
            .enqueue(object : Callback<DiditSessionResponse> {
                override fun onResponse(
                    call: Call<DiditSessionResponse>,
                    response: Response<DiditSessionResponse>
                ) {
                    if (response.isSuccessful) {
                        val token = response.body()?.sessionToken
                        if (token != null) {
                            launchSdk(token, onSdkReady)
                        } else {
                            onSdkReady?.invoke()
                            onStatusChanged.invoke("error")
                        }
                    } else {
                        onSdkReady?.invoke()
                        onStatusChanged.invoke("error")
                    }
                }

                override fun onFailure(call: Call<DiditSessionResponse>, t: Throwable) {
                    onSdkReady?.invoke()
                    Log.e("Didit", "API error", t)
                    onStatusChanged.invoke("error")
                }
            })
    }

    private fun launchSdk(sessionToken: String, onSdkReady: (() -> Unit)? = null) {
        DiditSdk.startVerification(token = sessionToken) { result ->
            when (result) {
                is VerificationResult.Completed -> onStatusChanged.invoke("pending")
                is VerificationResult.Cancelled -> onStatusChanged.invoke("cancelled")
                is VerificationResult.Failed -> onStatusChanged.invoke("error")
            }
        }

        stateJob = CoroutineScope(Dispatchers.Main).launch {
            DiditSdk.state.collect { state ->
                when (state) {
                    is DiditSdkState.Ready -> {
                        onSdkReady?.invoke()  // Dismiss loading
                        DiditSdk.launchVerificationUI(activity)
                    }
                    is DiditSdkState.Error -> {
                        onSdkReady?.invoke()
                        onStatusChanged.invoke("error")
                    }
                    else -> { }
                }
            }
        }
    }

    fun cleanup() {
        stateJob?.cancel()
        stopListening()
    }
    fun listenToStatus() {
        val uid = auth.currentUser?.uid ?: return

        statusListener = database.reference.child("users").child(uid)
            .child("verificationStatus")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java) ?: "none"
                    onStatusChanged(status)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Didit", "DB error", error.toException())
                }
            })
    }

    fun stopListening() {
        val uid = auth.currentUser?.uid ?: return
        statusListener?.let {
            database.reference.child("users").child(uid)
                .child("verificationStatus")
                .removeEventListener(it)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun checkStatus(callback: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            callback("none")
            return
        }

        database.reference.child("users").child(uid)
            .child("verificationStatus")
            .get()
            .addOnSuccessListener { snapshot ->
                callback(snapshot.getValue(String::class.java) ?: "none")
            }
            .addOnFailureListener {
                callback("none")
            }
    }
}