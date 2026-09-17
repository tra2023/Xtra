package com.github.andreyasadchy.xtra.ui.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.content.res.use
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerVolumeDialog : BottomSheetDialogFragment() {

    companion object {
        private const val VOLUME = "volume"
        private const val KEY_CURRENT_VOLUME = "currentVolume"

        fun newInstance(volume: Float?): PlayerVolumeDialog {
            return PlayerVolumeDialog().apply {
                arguments = Bundle().apply {
                    putFloat(VOLUME, volume ?: 1f)
                }
            }
        }
    }

    private var volume by mutableFloatStateOf(100f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volume = (savedInstanceState?.getFloat(KEY_CURRENT_VOLUME)
            ?: (requireArguments().getFloat(VOLUME, 1f) * 100f)).let {
            if (it.isFinite()) it.coerceIn(0f, 100f) else 100f
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = requireContext().prefs()
        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> prefs.getString(C.UI_THEME_DARK_ON, "0")
                else -> prefs.getString(C.UI_THEME_DARK_OFF, "2")
            }
        } else {
            prefs.getString(C.THEME, "0")
        }
        val padding = inflater.context.obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).use {
            it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
        }
        return ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                    PlayerVolumeDialogContent(
                        volume = volume,
                        volumeLabel = getString(R.string.volume),
                        volumeIcon = painterResource(if (volume == 0f) R.drawable.baseline_volume_off_black_24 else R.drawable.baseline_volume_up_black_24),
                        onVolumeChanged = ::changeVolume,
                        onVolumeChangeFinished = { prefs.edit { putInt(C.PLAYER_VOLUME, volume.toInt()) } },
                        onMuteClick = {
                            changeVolume(if (volume == 0f) 100f else 0f)
                            prefs.edit { putInt(C.PLAYER_VOLUME, volume.toInt()) }
                        },
                        modifier = Modifier.padding(padding.dp),
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun changeVolume(value: Float) {
        volume = value
        (parentFragment as? PlayerFragment)?.changeVolume(value / 100f)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(KEY_CURRENT_VOLUME, volume)
    }
}
