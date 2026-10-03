package com.app.nisisiafrica.Utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.nisisiafrica.R
import com.app.nisisiafrica.Worker.AppUpdateDownloadWorker
import com.app.nisisiafrica.data.Model.LmsModels
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        val view = LayoutInflater.from(activity).inflate(R.layout.progress_layout, null)
        val progressText = view.findViewById<TextView>(R.id.operateProgressTv)
        val description = view.findViewById<TextView>(R.id.operateDescTv)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressbar)
        val closeButton = view.findViewById<AppCompatImageView>(R.id.btnStopExecutor)

        progressBar.max = 100
        progressBar.isIndeterminate = false
        closeButton.visibility = if (mandatory) View.GONE else View.VISIBLE
        closeButton.setOnClickListener {
            dialog.dismiss()
            onNoUpdateBlock?.invoke()
        }

        dialog.setContentView(view)
        dialog.setCancelable(!mandatory)
        dialog.setCanceledOnTouchOutside(!mandatory)

        fun showReadyState() {
            val apk = AppUpdateManager.downloadedApk(activity, code)
            if (!apk.exists() || !AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) return
            progressText.text = "100"
            progressBar.progress = 100
            description.text = "Update ready — tap to install"
            view.isClickable = true
            view.setOnClickListener { promptInstall(activity, apk) }
        }

        fun showDownloadState(progress: Int) {
            val safeProgress = progress.coerceIn(0, 100)
            progressText.text = safeProgress.toString()
            progressBar.progress = safeProgress
            description.text = "Downloading update..."
            view.isClickable = false
            view.setOnClickListener(null)
        }

        val apk = AppUpdateManager.downloadedApk(activity, code)
        if (apk.exists() && AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) {
            showReadyState()
        } else {
            showDownloadState(0)
        }

        val liveData = WorkManager.getInstance(activity)
            .getWorkInfosForUniqueWorkLiveData("app-update-$code")
        val observer = Observer<List<WorkInfo>> { infos ->
            val work = infos.maxByOrNull { it.runAttemptCount } ?: return@Observer
            when (work.state) {
                WorkInfo.State.SUCCEEDED -> showReadyState()
                WorkInfo.State.RUNNING -> {
                    val progress = work.progress.getInt(AppUpdateDownloadWorker.KEY_PROGRESS, 0)
                    showDownloadState(progress)
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    description.text = if (work.runAttemptCount > 0) {
                        "Waiting to retry download..."
                    } else {
                        "Preparing download..."
                    }
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    description.text = "Download failed. Reopen the app to retry."
                }
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
