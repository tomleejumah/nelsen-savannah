package com.app.nisisiafrica.Utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.LmsModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.Observer
import androidx.fragment.app.FragmentActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File

object AppUpdateBottomSheet {
    private const val TAG = "AppUpdateBottomSheet"
    fun show(
        activity: FragmentActivity,
        release: LmsModels.AppReleaseDto,
        mandatory: Boolean = true,
        onNoUpdateBlock: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val code = release.versionCode ?: return
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_app_update, null)
        val txtVersion = view.findViewById<TextView>(R.id.txtUpdateVersion)
        val txtSize = view.findViewById<TextView>(R.id.txtUpdateSize)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdateNow)
        val btnLater = view.findViewById<Button>(R.id.btnUpdateLater)
        val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val txtProgress = view.findViewById<TextView>(R.id.txtUpdateProgress)

        txtVersion.text = "Version ${release.versionName ?: "Latest"} (Build $code)"
        txtSize.text = release.sizeBytes?.let {
            String.format("%.1f MB", it.toDouble() / (1024 * 1024))
        } ?: "New build"

        dialog.setContentView(view)
        dialog.setCancelable(!mandatory)
        dialog.setCanceledOnTouchOutside(!mandatory)
        btnLater.visibility = if (mandatory) View.GONE else View.VISIBLE
        btnLater.setOnClickListener {
            dialog.dismiss()
            onNoUpdateBlock?.invoke()
        }

        fun refresh() {
            val apk = AppUpdateManager.downloadedApk(activity, code)
            val ready = apk.exists() && AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())
            if (ready) {
                progressBar.visibility = View.GONE
                txtProgress.visibility = View.VISIBLE
                txtProgress.text = "Update downloaded and ready to install."
                btnUpdate.isEnabled = true
                btnUpdate.text = "Install Update"
                btnUpdate.setOnClickListener { promptInstall(activity, apk) }
            } else {
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
                txtProgress.visibility = View.VISIBLE
                txtProgress.text = "Downloading update in the background..."
                btnUpdate.isEnabled = true
                btnUpdate.text = "Download Update"
                btnUpdate.setOnClickListener {
                    AppUpdateManager.enqueueDownload(activity, release)
                    txtProgress.text = "Downloading update in the background..."
                }
            }
        }

        refresh()
        val liveData = WorkManager.getInstance(activity)
            .getWorkInfosForUniqueWorkLiveData("app-update-$code")
        val observer = Observer<List<WorkInfo>> { infos ->
            if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) refresh()
            if (infos.any { it.state == WorkInfo.State.FAILED }) {
                txtProgress.text = "Download failed. Check your connection and retry."
                btnUpdate.text = "Retry Download"
                btnUpdate.isEnabled = true
            }
        }
        liveData.observe(activity, observer)
        dialog.setOnDismissListener { liveData.removeObserver(observer) }
        dialog.show()
    }

    fun promptInstall(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Please allow installation from unknown sources for Nelsen Savannah",
                        Toast.LENGTH_LONG
                    ).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Could not launch package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
