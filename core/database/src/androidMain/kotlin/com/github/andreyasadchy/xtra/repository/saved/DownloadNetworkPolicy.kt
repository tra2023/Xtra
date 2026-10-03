package com.github.andreyasadchy.xtra.repository.saved

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether a "download over Wi-Fi only" request should wait.
 *
 * The rule was inline in `MainViewModel` three times (stream, video and clip downloads): a download
 * waits when the current connection is cellular. Kept as a plain function over
 * [NetworkCapabilities] so the decision is readable and testable without a real network stack.
 */
object DownloadNetworkPolicy {

    /**
     * True when [capabilities] describes a cellular connection. No network, or any other transport
     * (Wi-Fi, Ethernet), is false — exactly the check the view model used to perform inline.
     */
    fun isMetered(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

    /** Reads the active network's capabilities; a missing network counts as not cellular. */
    fun isOnCellular(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = connectivityManager.activeNetwork ?: return false
        return isMetered(connectivityManager.getNetworkCapabilities(active))
    }
}

/** Typed `getSystemService`, so callers do not repeat the cast. */
inline fun <reified T : Any> Context.getSystemServiceOf(): T? =
    getSystemService(T::class.java)
