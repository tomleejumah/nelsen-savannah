package com.app.nisisiafrica.Utils

import android.app.Activity
import android.util.Log
import com.app.nisisiafrica.BuildConfig
import com.app.nisisiafrica.data.Model.LmsModels
import com.app.nisisiafrica.data.remote.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    @JvmStatic
    @JvmOverloads
    fun checkForUpdates(activity: Activity, forceShow: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return

        ApiClient.getLmsService().appRelease.enqueue(object : Callback<LmsModels.AppReleaseEnvelope> {
            override fun onResponse(
                call: Call<LmsModels.AppReleaseEnvelope>,
                response: Response<LmsModels.AppReleaseEnvelope>
            ) {
                if (activity.isFinishing || activity.isDestroyed) return

                val envelope = response.body()
                val release = envelope?.data
                if (response.isSuccessful && release != null && release.available) {
                    val currentVersionCode = BuildConfig.VERSION_CODE
                    val serverVersionCode = release.versionCode ?: 0
                    val serverVersionName = release.versionName ?: ""

                    val isNewer = if (serverVersionCode > 0) {
                        serverVersionCode > currentVersionCode
                    } else {
                        serverVersionName.isNotBlank() && serverVersionName != BuildConfig.VERSION_NAME
                    }

                    Log.d(TAG, "Check update: serverVersionCode=$serverVersionCode, localVersionCode=$currentVersionCode, isNewer=$isNewer")

                    if (isNewer || forceShow) {
                        AppUpdateBottomSheet.show(activity, release)
                    }
                } else {
                    if (forceShow) {
                        Log.d(TAG, "No update available or server response empty")
                    }
                }
            }

            override fun onFailure(call: Call<LmsModels.AppReleaseEnvelope>, t: Throwable) {
                Log.e(TAG, "Failed to check for app update", t)
            }
        })
    }
}
