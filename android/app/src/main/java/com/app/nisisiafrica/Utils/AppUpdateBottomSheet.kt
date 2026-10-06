package com.app.nisisiafrica.Utils

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.nisisiafrica.R
import com.app.nisisiafrica.Worker.AppUpdateDownloadWorker
import com.app.nisisiafrica.data.Model.LmsModels
import java.io.File

object AppUpdateBottomSheet {
    private const val TAG = "AppUpdateDialog"

    fun show(activity: FragmentActivity, release: LmsModels.AppReleaseDto, mandatory: Boolean = true, onNoUpdateBlock: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val code = release.versionCode ?: return
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(activity).inflate(R.layout.progress_layout, null)
        dialog.setContentView(view)
        // Full app surface, but keep system status/navigation bars visible and keep
        // update content inside their safe insets.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
        dialog.setCancelable(!mandatory)
        dialog.setCanceledOnTouchOutside(!mandatory)

        val number = view.findViewById<TextView>(R.id.operateProgressTv)
        val description = view.findViewById<TextView>(R.id.operateDescTv)
        val progress = view.findViewById<ProgressBar>(R.id.progressbar)
        val close = view.findViewById<AppCompatImageView>(R.id.btnStopExecutor)
        progress.max = 100

        close.visibility = if (mandatory) android.view.View.GONE else android.view.View.VISIBLE
        close.setOnClickListener { dialog.dismiss(); onNoUpdateBlock?.invoke() }

        fun showProgress(value: Int, text: String) {
            val pct = value.coerceIn(0, 100)
            progress.progress = pct
            number.text = pct.toString()
            description.text = text
        }
        fun ready() {
            val apk = AppUpdateManager.downloadedApk(activity, code)
            if (!apk.exists() || !AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) return
            showProgress(100, "Update downloaded.\nTap to install.")
            description.setOnClickListener { promptInstall(activity, apk) }
            view.setOnClickListener { promptInstall(activity, apk) }
        }
        fun retry() {
            description.text = "Download failed. Tap to retry."
            description.setOnClickListener {
                description.setOnClickListener(null)
                showProgress(progress.progress, "Waiting for network / retry…")
                AppUpdateManager.enqueueDownload(activity, release, restart = true)
            }
        }

        val apk = AppUpdateManager.downloadedApk(activity, code)
        if (apk.exists() && AppUpdateManager.verifySha256(apk, release.sha256.orEmpty())) {
            ready()
        } else {
            showProgress(0, "Waiting for network / retry…")
            AppUpdateManager.enqueueDownload(activity, release)
        }

        val liveData = WorkManager.getInstance(activity).getWorkInfosForUniqueWorkLiveData("app-update-$code")
        val observer = Observer<List<WorkInfo>> { infos ->
            val work = infos.lastOrNull() ?: return@Observer
            val pct = work.progress.getInt(AppUpdateDownloadWorker.KEY_PROGRESS, progress.progress)
            when (work.state) {
                WorkInfo.State.SUCCEEDED -> ready()
                WorkInfo.State.RUNNING -> showProgress(pct, "Downloading update…")
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> showProgress(pct, "Waiting for network / retry…")
                WorkInfo.State.FAILED -> {
                    val error = work.outputData.getString(AppUpdateDownloadWorker.KEY_ERROR)
                    if (!error.isNullOrBlank()) {
                        description.text = "$error. Tap to retry."
                        description.setOnClickListener {
                            description.setOnClickListener(null)
                            showProgress(0, "Waiting for network / retry…")
                            AppUpdateManager.enqueueDownload(activity, release, restart = true)
                        }
                    } else retry()
                }
                WorkInfo.State.CANCELLED -> retry()
            }
        }
        liveData.observe(activity, observer)
        dialog.setOnDismissListener { liveData.removeObserver(observer) }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, true)
                androidx.core.view.WindowInsetsControllerCompat(this, decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            }
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, true)
            androidx.core.view.WindowInsetsControllerCompat(this, decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    fun promptInstall(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow installation from unknown sources for Nelsen Savannah", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
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
