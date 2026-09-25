package com.github.andreyasadchy.xtra.repository

/**
 * Platform-agnostic state machine for the "hide ads" player setting.
 * The [Host] supplies the player actions.
 */
class HideAdsController(
    private val host: Host,
) {
    interface Host {
        fun setVideoHidden(hidden: Boolean)
        fun onWaitingAds()
    }

    private var hidden = false

    val isHidden: Boolean get() = hidden

    fun shouldProcess(hideAds: Boolean): Boolean = hideAds

    fun onAdsChanged(isAd: Boolean, hideAds: Boolean) {
        if (!hideAds) {
            return
        }
        if (isAd) {
            if (!hidden) {
                hidden = true
                host.setVideoHidden(true)
                host.onWaitingAds()
            }
        } else if (hidden) {
            hidden = false
            host.setVideoHidden(false)
        }
    }
}
