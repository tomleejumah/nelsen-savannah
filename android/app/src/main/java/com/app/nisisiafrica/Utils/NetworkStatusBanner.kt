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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var hideRunnable: Runnable? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    fun start() {
        if (banner.parent == null) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            root.addView(banner, params)
            // Keep the strip immediately below each screen's custom top bar/tab strip.
            // We derive the bar's bottom on every layout so this also works in nested
            // LMS screens such as Enroll Schools without per-activity banner code.
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                positionBelowTopChrome(root)
            }
            ViewCompat.setOnApplyWindowInsetsListener(banner) { _, insets ->
                positionBelowTopChrome(root)
                insets
            }
            ViewCompat.requestApplyInsets(banner)
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
        hideRunnable?.let(main::removeCallbacks)
        hideRunnable = null
    }

    private fun positionBelowTopChrome(root: ViewGroup) {
        val statusTop = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        var anchorBottom = statusTop
        fun inspect(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child === banner || child.visibility != View.VISIBLE) continue
                val name = runCatching { activity.resources.getResourceEntryName(child.id) }.getOrNull()
                val looksLikeTopChrome = name != null && (
                    name.contains("top", true) || name.contains("header", true) ||
                    name.contains("tab", true) || name.contains("toolbar", true)
                )
                if (looksLikeTopChrome && child.y < activity.resources.displayMetrics.heightPixels * 0.35f) {
                    anchorBottom = maxOf(anchorBottom, (child.y + child.height).toInt())
                }
                if (child is ViewGroup) inspect(child)
            }
        }
        inspect(root)
        (banner.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.topMargin != anchorBottom) {
                lp.topMargin = anchorBottom
                banner.layoutParams = lp
            }
        }
    }

    private fun show(text: String, autoHideMs: Long? = null) {
        hideRunnable?.let(main::removeCallbacks)
        hideRunnable = null
        banner.text = text
        if (banner.visibility != View.VISIBLE) {
            banner.alpha = 0f
            banner.visibility = View.VISIBLE
            banner.animate().alpha(1f).setDuration(180).start()
        }
        if (autoHideMs != null) {
            hideRunnable = Runnable { hide() }.also { main.postDelayed(it, autoHideMs) }
        }
    }

    private fun hide() {
        if (banner.visibility != View.VISIBLE) return
        banner.animate().alpha(0f).setDuration(220).withEndAction {
            banner.visibility = View.GONE
            banner.alpha = 1f
        }.start()
    }

    private fun refresh() = activity.runOnUiThread {
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        when {
            !online -> {
                show("Offline · Waiting for network")
            }
            syncState == WorkInfo.State.RUNNING -> {
                show("Syncing…")
            }
            syncState == WorkInfo.State.ENQUEUED || syncState == WorkInfo.State.BLOCKED -> {
                show("Waiting to sync…")
            }
            syncState == WorkInfo.State.SUCCEEDED -> {
                show("Synced", 1800)
            }
            else -> hide()
        }
    }

    private fun dp(value:Int) = (value * activity.resources.displayMetrics.density).toInt()
}
