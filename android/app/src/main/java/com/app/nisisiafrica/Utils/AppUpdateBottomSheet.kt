package com.app.nisisiafrica.Utils

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.nisisiafrica.R
import com.app.nisisiafrica.Worker.AppUpdateDownloadWorker
import com.app.nisisiafrica.data.Model.LmsModels
import java.io.File
import java.util.Locale

object AppUpdateBottomSheet {
    private const val TAG = "AppUpdateDialog"

    fun show(activity: FragmentActivity, release: LmsModels.AppReleaseDto, mandatory: Boolean = true, onNoUpdateBlock: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val code = release.versionCode ?: return
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_app_update, null)
        dialog.setContentView(view)
        dialog.setCancelable(!mandatory)
        dialog.setCanceledOnTouchOutside(!mandatory)

        val version = view.findViewById<TextView>(R.id.txtUpdateVersion)
        val size = view.findViewById<TextView>(R.id.txtUpdateSize)
        val progress = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val progressText = view.findViewById<TextView>(R.id.txtUpdateProgress)
        val later = view.findViewById<AppCompatButton>(R.id.btnUpdateLater)
        val action = view.findViewById<AppCompatButton>(R.id.btnUpdateNow)
        version.text = "Version ${release.versionName ?: "new"} (Build $code)"
        size.text = formatBytes(release.sizeBytes ?: 0)
        later.visibility = if (mandatory) View.GONE else View.VISIBLE
        later.setOnClickListener { dialog.dismiss(); onNoUpdateBlock?.invoke() }
        progress.max = 100

        fun ready() {
            val apk = AppUpdateManager.downloadedApk(activity, code)
            if (!apk.exists() || !AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) return
            progress.visibility = View.VISIBLE; progress.progress = 100
            progressText.visibility = View.VISIBLE; progressText.text = "Ready to install"
            action.isEnabled = true; action.text = "Install Update"
            action.setOnClickListener { promptInstall(activity, apk) }
        }
        fun downloading(value: Int, waiting: Boolean = false) {
            progress.visibility = View.VISIBLE; progressText.visibility = View.VISIBLE
            progress.progress = value.coerceIn(0, 100)
            progressText.text = if (waiting) "Waiting for network / retry…" else "${progress.progress}%"
            action.isEnabled = false; action.text = "Downloading…"
        }
        fun retry() {
            progress.visibility = View.VISIBLE; progressText.visibility = View.VISIBLE
            progressText.text = "Download failed. Check your connection and retry."
            action.isEnabled = true; action.text = "Retry"
            action.setOnClickListener { downloading(0, true); AppUpdateManager.enqueueDownload(activity, release, restart = true) }
        }
        fun start() {
            action.isEnabled = true; action.text = "Update Now"
            action.setOnClickListener { downloading(0, true); AppUpdateManager.enqueueDownload(activity, release) }
        }

        val apk = AppUpdateManager.downloadedApk(activity, code)
        if (apk.exists() && AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) ready() else start()

        val liveData = WorkManager.getInstance(activity).getWorkInfosForUniqueWorkLiveData("app-update-$code")
        val observer = Observer<List<WorkInfo>> { infos ->
            val work = infos.maxByOrNull { it.runAttemptCount } ?: return@Observer
            when (work.state) {
                WorkInfo.State.SUCCEEDED -> ready()
                WorkInfo.State.RUNNING -> downloading(work.progress.getInt(AppUpdateDownloadWorker.KEY_PROGRESS, 0))
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> downloading(work.progress.getInt(AppUpdateDownloadWorker.KEY_PROGRESS, 0), true)
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> retry()
            }
        }
        liveData.observe(activity, observer)
        dialog.setOnDismissListener { liveData.removeObserver(observer) }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "APK"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun promptInstall(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow installation from unknown sources for Nelsen Savannah", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }); return
            }
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Could not launch package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
