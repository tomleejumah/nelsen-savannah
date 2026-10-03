package com.app.nisisiafrica.Utils

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.setPadding

class NetworkStatusBanner(private val activity: Activity) {
    private val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val banner = TextView(activity).apply {
        text = "Offline · Waiting for network"
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
        refresh()
    }

    fun stop() {
        if (registered) {
            try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            registered = false
        }
    }

    private fun refresh() = activity.runOnUiThread {
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        banner.visibility = if (online) TextView.GONE else TextView.VISIBLE
    }

    private fun dp(value:Int) = (value * activity.resources.displayMetrics.density).toInt()
}
