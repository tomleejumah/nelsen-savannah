package com.app.nisisiafrica.Utils

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager

class NetworkStatusBanner(private val activity: Activity) {
    private val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private val workManager = WorkManager.getInstance(activity.applicationContext)
    private val syncWork = workManager.getWorkInfosForUniqueWorkLiveData(LmsStudySync.IMMEDIATE)
    private var syncObserver: Observer<List<WorkInfo>>? = null
    private var syncState: WorkInfo.State? = null
    private val banner = TextView(activity).apply {
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(8))
        setBackgroundColor(0xff323232.toInt())
        setTextColor(0xffffffff.toInt())
        visibility = TextView.GONE
        elevation = dp(8).toFloat()
    }
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    fun start() {
        if (banner.parent == null) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.addView(banner, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        if (!registered) {
            try { cm.registerDefaultNetworkCallback(callback); registered = true } catch (_: Exception) {}
        }
        val owner = activity as? LifecycleOwner
        if (owner != null && syncObserver == null) {
            val observer = Observer<List<WorkInfo>> { infos: List<WorkInfo> ->
                syncState = infos.lastOrNull()?.state
                refresh()
                if (syncState == WorkInfo.State.SUCCEEDED) {
                    main.postDelayed({
                        if (syncState == WorkInfo.State.SUCCEEDED) {
                            syncState = null
                            refresh()
                        }
                    }, 1800)
                }
            }
            syncObserver = observer
            syncWork.observe(owner, observer)
        }
        refresh()
    }

    fun stop() {
        if (registered) {
            try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            registered = false
        }
        syncObserver?.let { syncWork.removeObserver(it) }
        syncObserver = null
        main.removeCallbacksAndMessages(null)
    }

    private fun refresh() = activity.runOnUiThread {
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        when {
            !online -> {
                banner.text = "Offline · Waiting for network"
                banner.visibility = TextView.VISIBLE
            }
            syncState == WorkInfo.State.RUNNING -> {
                banner.text = "Syncing…"
                banner.visibility = TextView.VISIBLE
            }
            syncState == WorkInfo.State.ENQUEUED || syncState == WorkInfo.State.BLOCKED -> {
                banner.text = "Waiting to sync…"
                banner.visibility = TextView.VISIBLE
            }
            syncState == WorkInfo.State.SUCCEEDED -> {
                banner.text = "Synced"
                banner.visibility = TextView.VISIBLE
            }
            else -> banner.visibility = TextView.GONE
        }
    }

    private fun dp(value:Int) = (value * activity.resources.displayMetrics.density).toInt()
}
