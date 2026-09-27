package com.uchat.android.core.arch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Real connectivity probe for the Home dashboard (replaces the hardcoded "Connected"). */
object NetworkMonitor {

    fun isOnline(context: Context): Boolean {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
