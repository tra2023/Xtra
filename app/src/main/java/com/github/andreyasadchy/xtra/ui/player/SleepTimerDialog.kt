package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.getThemeId
import com.github.andreyasadchy.xtra.util.prefs

class SleepTimerDialog : DialogFragment() {

    companion object {
        private const val KEY_TIME_LEFT = "timeLeft"
        private const val KEY_HOURS = "hours"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_LOCK = "lock"

        fun newInstance(timeLeft: Long): SleepTimerDialog {
            return SleepTimerDialog().apply {
                arguments = Bundle().apply {
                    putLong(KEY_TIME_LEFT, timeLeft)
                }
            }
        }
    }

    private var state by mutableStateOf(SleepTimerUiState("0", "15", false))
    private var composeView: ComposeView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val prefs = context.prefs()
        val timeLeft = requireArguments().getLong(KEY_TIME_LEFT)
        val initialMinutes = if (timeLeft < 0L) {
            prefs.getInt(C.SLEEP_TIMER_MINUTES, 15)
        } else {
            (timeLeft / 60_000L).toInt()
        }
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        val adminActive = devicePolicyManager.isAdminActive(admin)
        state = SleepTimerUiState(
            hours = savedInstanceState?.getString(KEY_HOURS) ?: (initialMinutes / 60).coerceIn(0, 23).toString(),
            minutes = savedInstanceState?.getString(KEY_MINUTES) ?: (initialMinutes % 60).coerceIn(0, 59).toString(),
            lockScreen = adminActive && (savedInstanceState?.getBoolean(KEY_LOCK) ?: prefs.getBoolean(C.SLEEP_TIMER_LOCK, false)),
        )
        val builder = context.getAlertDialogBuilder()
        val theme = context.getThemeId()
        val view = ComposeView(builder.context).apply {
            setViewTreeLifecycleOwner(this@SleepTimerDialog)
            setViewTreeSavedStateRegistryOwner(this@SleepTimerDialog)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@SleepTimerDialog.lifecycle))
            setContent {
                XtraTheme(themeId = theme) {
                    SleepTimerDialogContent(
                        state = state,
                        title = getString(R.string.sleep_timer),
                        hoursLabel = getString(R.string.hours),
                        minutesLabel = getString(R.string.minutes),
                        lockLabel = getString(if (adminActive) R.string.sleep_timer_lock else R.string.sleep_timer_lock_permissions),
                        confirmLabel = getString(if (timeLeft < 0L) R.string.start else R.string.set),
                        cancelLabel = getString(android.R.string.cancel),
                        stopLabel = if (timeLeft < 0L) null else getString(R.string.stop),
                        onHoursChanged = { state = state.copy(hours = it) },
                        onMinutesChanged = { state = state.copy(minutes = it) },
                        onLockChanged = {
                            if (adminActive) {
                                state = state.copy(lockScreen = it)
                            } else {
                                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                })
                                dismiss()
                            }
                        },
                        onConfirm = {
                            (parentFragment as? PlayerFragment)?.onSleepTimerChanged(state.durationMs, state.hoursValue, state.minutesValue, state.lockScreen)
                            prefs.edit { putInt(C.SLEEP_TIMER_MINUTES, state.hoursValue * 60 + state.minutesValue) }
                            dismiss()
                        },
                        onCancel = { dismiss() },
                        onStop = {
                            (parentFragment as? PlayerFragment)?.onSleepTimerChanged(-1L, 0, 0, state.lockScreen)
                            dismiss()
                        },
                    )
                }
            }
        }
        composeView = view
        return builder.setView(view).create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HOURS, state.hours)
        outState.putString(KEY_MINUTES, state.minutes)
        outState.putBoolean(KEY_LOCK, state.lockScreen)
    }

    override fun onDestroyView() {
        composeView?.disposeComposition()
        composeView = null
        super.onDestroyView()
    }
}
