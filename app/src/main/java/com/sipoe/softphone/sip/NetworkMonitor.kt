package com.sipoe.softphone.sip

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class NetworkMonitor(
    context: Context,
    private val onReachabilityChanged: (Boolean) -> Unit,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onReachabilityChanged(true)
        }

        override fun onLost(network: Network) {
            onReachabilityChanged(false)
        }
    }

    fun start() {
        val manager = connectivityManager ?: return
        onReachabilityChanged(manager.activeNetwork != null)
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
