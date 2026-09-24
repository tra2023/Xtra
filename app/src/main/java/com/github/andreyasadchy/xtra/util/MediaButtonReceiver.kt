package com.github.andreyasadchy.xtra.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.player.ExoPlayerService
import kotlinx.coroutines.runBlocking

class MediaButtonReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                    val savedStates = runBlocking {
                        (context.applicationContext as XtraApp).xtraModule.playerRepository.getPlaybackStates()
                    }
                    if (savedStates.isNotEmpty()) {
                        context.startForegroundService(Intent(context, ExoPlayerService::class.java).apply {
                            fillIn(intent, 0)
                        })
                    }
                }
            }
        }
    }
}