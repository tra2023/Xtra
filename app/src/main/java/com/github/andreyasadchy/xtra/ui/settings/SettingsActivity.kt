package com.github.andreyasadchy.xtra.ui.settings

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.settings.SettingsViewModel.Companion.SettingsViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs

/**
 * Hosts the Compose settings screens from `:core:ui`.
 *
 * Navigation is a lightweight back stack over [SettingsRoute]; platform-only
 * work (permissions, file pickers, device admin, updates, locale) stays here
 * behind [SettingsAction].
 */
class SettingsActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModelFactory }
    private var changed = false

    private val backStack = mutableStateListOf(SettingsRoute.Root)
    private var searchActive by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")

    private val backupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { settingsViewModel.backupSettings(it.toString()) }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val list = mutableListOf<String>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { list.add(it.toString()) }
                }
            } ?: result.data?.data?.let { list.add(it.toString()) }
            settingsViewModel.restoreSettings(
                list = list,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(this, true),
                helixHeaders = TwitchApiHelper.getHelixHeaders(this),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_CHANGED) == true) {
            setResult()
        }
        applyTheme()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        setContentView(
            ComposeView(this).apply {
                setContent {
                    val theme = rememberThemeId()
                    XtraTheme(themeId = theme) {
                        val settings = remember {
                            AndroidXtraSettings(applicationContext.prefs(), applicationContext.tokenPrefs())
                        }
                        val strings = remember { settingsStrings() }
                        CompositionLocalProvider(LocalXtraSettings provides settings) {
                            SettingsScaffold(strings)
                        }
                    }
                }
            }
        )
    }

    private fun handleBack(): Boolean {
        if (searchActive) {
            searchActive = false
            searchQuery = ""
            return true
        }
        if (backStack.size > 1) {
            backStack.removeLast()
            return true
        }
        return false
    }

    private fun setResult() {
        if (!changed) {
            changed = true
            setResult(RESULT_OK)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_CHANGED, changed)
        super.onSaveInstanceState(outState)
    }

    private fun handleAction(action: SettingsAction) {
        when (action) {
            SettingsAction.ResultChanged -> setResult()
            SettingsAction.Recreate -> recreate()
            is SettingsAction.LanguageChanged -> {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(
                        if (action.value == "auto") null else action.value
                    )
                )
            }
            SettingsAction.RequestNotificationPermission -> {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
            is SettingsAction.ToggleNotifications -> {
                settingsViewModel.toggleNotifications(
                    enabled = action.enabled,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(this, true),
                    helixHeaders = TwitchApiHelper.getHelixHeaders(this),
                )
            }
            is SettingsAction.ChatWidthChanged -> {
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                prefs().edit {
                    putInt(C.LANDSCAPE_CHAT_WIDTH, ((if (height > width) height else width) * (action.percent / 100f)).toInt())
                }
            }
            SettingsAction.CheckUpdates -> {
                settingsViewModel.checkUpdates(
                    prefs().getString(C.UPDATE_URL, null) ?: "https://api.github.com/repos/crackededed/xtra/releases/tags/latest",
                    tokenPrefs().getLong(C.UPDATE_LAST_CHECKED, 0),
                )
            }
            SettingsAction.Backup -> backupLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            SettingsAction.Restore -> restoreLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            )
            SettingsAction.DeletePositions -> settingsViewModel.deletePositions()
            SettingsAction.DeleteSearches -> settingsViewModel.deleteRecentSearches()
            SettingsAction.ImportDownloads -> settingsViewModel.importDownloads()
            SettingsAction.RequestDeviceAdmin -> {
                val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(this, DeviceAdminReceiver::class.java)
                if (!devicePolicyManager.isAdminActive(admin)) {
                    startActivity(
                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        }
                    )
                }
            }
            SettingsAction.OpenDeviceAdminSettings -> {
                startActivity(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")))
            }
            SettingsAction.GetIntegrityToken -> {
                IntegrityDialog.newInstance(null).show(supportFragmentManager, null)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScaffold(strings: SettingsStrings) {
        val current = backStack.lastOrNull() ?: SettingsRoute.Root
        val supportsPip = remember {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }
        var pendingUpdateUrl by remember { mutableStateOf<String?>(null) }
        var downloading by remember { mutableStateOf(false) }
        var progress by remember { mutableIntStateOf(0) }
        var total by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            settingsViewModel.updateUrl.collect {
                if (it != null) {
                    pendingUpdateUrl = it
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.no_updates_found, Toast.LENGTH_LONG).show()
                }
            }
        }
        LaunchedEffect(Unit) {
            settingsViewModel.updateProgress.collect { progress = it }
        }
        LaunchedEffect(Unit) {
            settingsViewModel.closeUpdateDialog.collect {
                if (it) {
                    downloading = false
                    pendingUpdateUrl = null
                }
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titleFor(current)) },
                    navigationIcon = {
                        IconButton(onClick = { if (!handleBack()) finish() }) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.baseline_arrow_back_black_24),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (!searchActive) {
                            IconButton(onClick = { searchActive = true }) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(R.drawable.baseline_search_black_24),
                                    contentDescription = strings.search,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val navigate: (SettingsRoute) -> Unit = {
                    if (it == SettingsRoute.Search) {
                        searchActive = true
                    } else {
                        backStack.add(it)
                    }
                }
                if (searchActive) {
                    SettingsSearchScreen(
                        strings = strings,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onResultClick = {
                            searchActive = false
                            searchQuery = ""
                            backStack.add(it.route)
                        },
                    )
                } else {
                    when (current) {
                        SettingsRoute.Root -> RootSettingsScreen(strings, supportsPip, navigate, ::handleAction)
                        SettingsRoute.Theme -> ThemeSettingsScreen(strings, ::handleAction)
                        SettingsRoute.Ui -> UiSettingsScreen(strings, ::handleAction)
                        SettingsRoute.Chat -> ChatSettingsScreen(strings, ::handleAction)
                        SettingsRoute.Player -> PlayerSettingsScreen(strings, supportsPip, ::handleAction)
                        SettingsRoute.PlayerButtons -> PlayerButtonSettingsScreen(strings, navigate, ::handleAction)
                        SettingsRoute.PlayerMenu -> PlayerMenuSettingsScreen(strings)
                        SettingsRoute.Buffer -> BufferSettingsScreen(strings)
                        SettingsRoute.Playback -> PlaybackSettingsScreen(strings, navigate)
                        SettingsRoute.ApiToken -> ApiTokenSettingsScreen(strings)
                        SettingsRoute.Download -> DownloadSettingsScreen(strings, ::handleAction)
                        SettingsRoute.Update -> UpdateSettingsScreen(strings)
                        SettingsRoute.Debug -> DebugSettingsScreen(strings, ::handleAction)
                        SettingsRoute.Search -> SettingsSearchScreen(
                            strings = strings,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onResultClick = {
                                searchActive = false
                                searchQuery = ""
                                backStack.add(it.route)
                            },
                        )
                    }
                }
            }
        }
        pendingUpdateUrl?.let { url ->
            if (!downloading) {
                AlertDialog(
                    onDismissRequest = { pendingUpdateUrl = null },
                    title = { Text(getString(R.string.update_available)) },
                    text = { Text(getString(R.string.update_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (prefs().getBoolean(C.UPDATE_USE_BROWSER, false)) {
                                try {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                            addCategory(Intent.CATEGORY_BROWSABLE)
                                        }
                                    )
                                    tokenPrefs().edit {
                                        putLong(C.UPDATE_LAST_CHECKED, System.currentTimeMillis())
                                    }
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(this, R.string.no_browser_found, Toast.LENGTH_LONG).show()
                                }
                                pendingUpdateUrl = null
                            } else {
                                total = settingsViewModel.updateSize ?: 0L
                                progress = 0
                                downloading = true
                                settingsViewModel.downloadUpdate(url)
                            }
                        }) { Text(getString(R.string.yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingUpdateUrl = null }) { Text(getString(R.string.no)) }
                    },
                )
            }
        }
        if (downloading) {
            val size = total
            AlertDialog(
                onDismissRequest = {},
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            if (size > 0) {
                                getString(
                                    R.string.downloading_update_progress,
                                    Formatter.formatFileSize(this@SettingsActivity, progress.toLong()),
                                    Formatter.formatFileSize(this@SettingsActivity, size),
                                )
                            } else {
                                getString(R.string.downloading_update)
                            }
                        )
                        if (size > 0) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { (progress.toFloat() / size).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        settingsViewModel.updateJob?.cancel()
                        downloading = false
                        pendingUpdateUrl = null
                    }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }
    }

    private fun titleFor(route: SettingsRoute): String = when (route) {
        SettingsRoute.Root -> getString(R.string.settings)
        SettingsRoute.Theme -> getString(R.string.theme)
        SettingsRoute.Ui -> getString(R.string.ui_settings)
        SettingsRoute.Chat -> getString(R.string.chat_settings)
        SettingsRoute.Player -> getString(R.string.player_settings)
        SettingsRoute.PlayerButtons -> getString(R.string.player_buttons)
        SettingsRoute.PlayerMenu -> getString(R.string.player_menu_settings)
        SettingsRoute.Buffer -> getString(R.string.buffer_settings)
        SettingsRoute.Playback -> getString(R.string.playback_settings)
        SettingsRoute.ApiToken -> getString(R.string.api_token_settings)
        SettingsRoute.Download -> getString(R.string.download_settings)
        SettingsRoute.Update -> getString(R.string.update_settings)
        SettingsRoute.Debug -> getString(R.string.debug_settings)
        SettingsRoute.Search -> getString(R.string.search)
    }

    companion object {
        const val KEY_CHANGED = "changed"
    }
}
