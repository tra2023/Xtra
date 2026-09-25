package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.saved.DownloadProgressState
import com.github.andreyasadchy.xtra.repository.saved.DownloadsProgressTracker
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.download.StreamDownloadService
import com.github.andreyasadchy.xtra.ui.download.VideoDownloadService
import com.github.andreyasadchy.xtra.ui.downloads.DownloadCheckBox
import com.github.andreyasadchy.xtra.ui.downloads.DownloadsList
import com.github.andreyasadchy.xtra.ui.downloads.StorageSelector
import com.github.andreyasadchy.xtra.ui.paging.rememberPagingSnapshot
import com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsViewModel.Companion.DownloadsViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : PagedListFragment(), Scrollable {

    private val viewModel: DownloadsViewModel by viewModels { DownloadsViewModelFactory }
    private val progressViewModel: DownloadsProgressViewModel by viewModels()
    private var collectionJob: Job? = null
    private var gridState by mutableStateOf<LazyGridState?>(null)
    private var bottomInset by mutableIntStateOf(0)
    private var insertionScroll by mutableIntStateOf(0)
    private var firstId: Int? = null
    private var receivedItems by mutableStateOf(false)
    private var lastCount by mutableIntStateOf(0)
    private var actions by mutableStateOf<DownloadsAdapter?>(null)
    private var moveDialogVideo by mutableStateOf<OfflineVideo?>(null)
    private var deleteDialogVideo by mutableStateOf<OfflineVideo?>(null)
    private var deleteKeepFiles by mutableStateOf(true)
    override var enableNetworkCheck = false
    private var fileResultLauncher: ActivityResultLauncher<Intent>? = null
    private var chatFileResultLauncher: ActivityResultLauncher<Intent>? = null
    private var videoDownloadService: VideoDownloadService? = null
    private var videoDownloadServiceConnection: ServiceConnection? = null
    private var streamDownloadService: StreamDownloadService? = null
    private var streamDownloadServiceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    viewModel.selectedVideo?.let { video ->
                        requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        viewModel.moveToSharedStorage(it, video)
                    }
                }
            }
        }
        chatFileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    viewModel.selectedVideo?.let { video ->
                        requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        viewModel.updateChatUrl(it, video)
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bottomInset = 0
        insertionScroll = 0
        firstId = null
        receivedItems = false
        lastCount = 0
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration = LocalConfiguration.current
                val prefs = requireContext().prefs()
                val theme = rememberThemeId()
                val columns = prefs.getString(if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT, if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "1" else "2")?.toIntOrNull() ?: 1
                val state = rememberLazyGridState()
                DisposableEffect(state) {
                    gridState = state
                    onDispose { gridState = null }
                }
                LaunchedEffect(insertionScroll) { if (insertionScroll > 0) state.animateScrollToItem(0) }
                val snapshot = rememberPagingSnapshot(viewModel.flow)
                val firstIdNow = if (snapshot.itemCount > 0) snapshot.peek(0)?.id else null
                LaunchedEffect(firstIdNow) {
                    if (receivedItems && firstIdNow != null && firstIdNow != firstId && snapshot.itemCount > lastCount) insertionScroll++
                    lastCount = snapshot.itemCount
                    if (snapshot.itemCount > 0) receivedItems = true
                    firstId = firstIdNow
                }
                val progress by progressViewModel.progress.collectAsState()
                val adapter = actions
                val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
                fun find(id: Int) = (0 until snapshot.itemCount).mapNotNull { snapshot.peek(it) }.find { it.id == id }
                XtraTheme(themeId = theme) {
                    DownloadsList(
                        itemCount = snapshot.itemCount,
                        itemKey = { snapshot.peek(it)?.id ?: "placeholder:$it" },
                        itemAt = { index -> snapshot[index]?.let { adapter?.item(it, progress[it.id]) } },
                        loading = snapshot.loading, columns = columns, state = state,
                        emptyText = getString(R.string.nothing_here),
                        optionsText = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        deleteText = getString(R.string.delete),
                        onOpen = { find(it)?.let { adapter?.open(it) } },
                        onChannel = { find(it)?.let { adapter?.channel(it) } },
                        onGame = { find(it)?.let { adapter?.game(it) } },
                        onDelete = { find(it)?.let { adapter?.deleteVideo?.invoke(it) } },
                        onAction = { id, action -> find(id)?.let { adapter?.action(it, action) } },
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        bottomPadding = with(LocalDensity.current) { bottomInset.toDp() },
                        cardMargin = if (!material3) 0.dp else if (prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
                        cornerRadius = if (!material3) 0.dp else when (prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) { "1" -> 9.dp; "2" -> 0.dp; else -> 12.dp },
                        material3 = material3,
                    )
                    MoveStorageDialog()
                    DeleteVideoDialog()
                }
            }
        }
    }

    @Composable
    private fun MoveStorageDialog() {
        val video = moveDialogVideo ?: return
        val storage = requireContext().getExternalFilesDirs(".downloads").mapIndexedNotNull { index, file ->
            file?.absolutePath?.let { path ->
                if (index == 0) {
                    getString(R.string.internal_storage) to path
                } else {
                    path.substringBefore("/Android/data", "").takeIf { it.isNotBlank() }?.let {
                        it.substringAfterLast(File.separatorChar) to path
                    }
                }
            }
        }
        val mounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        var checked by remember(video) { mutableIntStateOf(if (storage.size == 1) 0 else requireContext().prefs().getInt(C.DOWNLOAD_STORAGE, 0)) }
        AlertDialog(
            onDismissRequest = { moveDialogVideo = null },
            confirmButton = {
                TextButton(onClick = {
                    if (mounted) storage.getOrNull(checked)?.let {
                        requireContext().prefs().edit { putInt(C.DOWNLOAD_STORAGE, checked) }
                        viewModel.moveToAppStorage(it.second, video)
                    }
                    moveDialogVideo = null
                }) { Text(getString(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { moveDialogVideo = null }) { Text(getString(android.R.string.cancel)) } },
            text = {
                StorageSelector(
                    title = getString(R.string.save_to), noStorageText = getString(R.string.no_storage_detected),
                    selectDirectoryText = getString(R.string.select_directory), available = mounted,
                    locations = emptyList(), location = 1, storageNames = storage.map { it.first },
                    selectedStorage = checked, directory = null, onLocation = {}, onStorage = { checked = it }, onDirectory = {},
                )
            },
        )
    }

    @Composable
    private fun DeleteVideoDialog() {
        val video = deleteDialogVideo ?: return
        AlertDialog(
            onDismissRequest = { deleteDialogVideo = null },
            title = { Text(getString(R.string.delete)) },
            text = {
                Column {
                    Text(getString(R.string.are_you_sure))
                    DownloadCheckBox(getString(R.string.keep_files), deleteKeepFiles) { deleteKeepFiles = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelActiveDownload(video)
                    viewModel.delete(video, deleteKeepFiles)
                    deleteDialogVideo = null
                }) { Text(getString(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteDialogVideo = null }) { Text(getString(android.R.string.cancel)) } },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actions = DownloadsAdapter(
            fragment = this,
            stopDownload = { video ->
                if (video.status == OfflineVideo.STATUS_WAITING_FOR_NETWORK || video.status == OfflineVideo.STATUS_WAITING_FOR_WIFI) {
                    viewModel.updateDownloadStatus(video, false)
                } else {
                    if (video.live) {
                        if (video.status == OfflineVideo.STATUS_PENDING
                            || ((video.status == OfflineVideo.STATUS_DOWNLOADING
                                    || video.status == OfflineVideo.STATUS_QUEUED
                                    || video.status == OfflineVideo.STATUS_WAITING_FOR_STREAM)
                                    && streamDownloadService?.activeDownloads?.toList()?.find { it.id == video.id } == null)
                        ) {
                            viewModel.finishDownload(video)
                        } else {
                            val intent = Intent(requireContext(), StreamDownloadService::class.java).apply {
                                action = StreamDownloadService.INTENT_STOP
                                putExtra(StreamDownloadService.KEY_VIDEO_ID, video.id)
                            }
                            requireContext().startService(intent)
                            bindStreamDownloadService(true)
                        }
                    } else {
                        val intent = Intent(requireContext(), VideoDownloadService::class.java).apply {
                            action = VideoDownloadService.INTENT_STOP
                            putExtra(VideoDownloadService.KEY_VIDEO_ID, video.id)
                        }
                        requireContext().startService(intent)
                        bindVideoDownloadService(true)
                    }
                }
            },
            resumeDownload = {
                val waitForWifi = if (requireContext().prefs().getBoolean(C.DOWNLOAD_WIFI_ONLY, false)) {
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                } else false
                if (waitForWifi) {
                    viewModel.updateDownloadStatus(it, true)
                } else {
                    if (it.live) {
                        val intent = Intent(requireContext(), StreamDownloadService::class.java).apply {
                            action = StreamDownloadService.INTENT_START
                            putExtra(StreamDownloadService.KEY_VIDEO_ID, it.id)
                        }
                        requireContext().startService(intent)
                        bindStreamDownloadService(true)
                    } else {
                        val intent = Intent(requireContext(), VideoDownloadService::class.java).apply {
                            action = VideoDownloadService.INTENT_START
                            putExtra(VideoDownloadService.KEY_VIDEO_ID, it.id)
                        }
                        requireContext().startService(intent)
                        bindVideoDownloadService(true)
                    }
                }
            },
            convertVideo = {
                val convert = getString(R.string.convert)
                requireActivity().getAlertDialogBuilder()
                    .setTitle(convert)
                    .setMessage(getString(R.string.convert_message))
                    .setPositiveButton(convert) { _, _ -> viewModel.convertToFile(it) }
                    .setNegativeButton(getString(android.R.string.cancel), null)
                    .show()
            },
            moveVideo = {
                if (it.url?.toUri()?.scheme == ContentResolver.SCHEME_CONTENT) {
                    moveDialogVideo = it
                } else {
                    viewModel.selectedVideo = it
                    fileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                }
            },
            updateChatUrl = {
                viewModel.selectedVideo = it
                chatFileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                })
            },
            shareVideo = {
                it.url?.let { videoUrl ->
                    val uri = if (videoUrl.endsWith(".m3u8")) {
                        videoUrl.substringBefore("%2F").toUri()
                    } else {
                        videoUrl.toUri()
                    }
                    startActivity(Intent.createChooser(Intent().apply {
                        action = Intent.ACTION_SEND
                        setDataAndType(uri, requireContext().contentResolver.getType(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        it.name?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    }, null))
                }
            },
            deleteVideo = { video ->
                deleteKeepFiles = true
                deleteDialogVideo = video
            }
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { gridState?.canScrollBackward == true }.collectLatest { scrolled ->
                    val parent = parentFragment
                    if ((parent as? FragmentHost)?.currentFragment === this@DownloadsFragment && requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                        parent.view?.findViewById<AppBarLayout>(R.id.appBar)?.apply {
                            setLiftOnScrollTargetView(view)
                            isLifted = scrolled
                        }
                    }
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            bottomInset = if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom else 0
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onStart() {
        super.onStart()
        viewLifecycleOwner.lifecycleScope.launch {
            val activeDownloads = viewModel.getActiveDownloads()
            if (activeDownloads.isNotEmpty()) {
                if (activeDownloads.any { it.live }) {
                    bindStreamDownloadService()
                }
                if (activeDownloads.any { !it.live }) {
                    bindVideoDownloadService()
                }
            }
        }
    }

    override fun initialize() {
        if (collectionJob != null) return
        collectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        progressViewModel.replace(false, videoDownloadService?.activeDownloads?.toList().orEmpty())
                        progressViewModel.replace(true, streamDownloadService?.activeDownloads?.toList().orEmpty())
                        delay(500)
                    }
                }
            }
        }
    }

    private fun cancelActiveDownload(video: OfflineVideo) {
        if (video.live) {
            if (streamDownloadService?.activeDownloads?.find { it.id == video.id } != null) {
                val intent = Intent(requireContext(), StreamDownloadService::class.java).apply {
                    action = StreamDownloadService.INTENT_CANCEL
                    putExtra(StreamDownloadService.KEY_VIDEO_ID, video.id)
                }
                requireContext().startService(intent)
                bindStreamDownloadService(true)
            }
        } else {
            if (videoDownloadService?.activeDownloads?.find { it.id == video.id } != null) {
                val intent = Intent(requireContext(), VideoDownloadService::class.java).apply {
                    action = VideoDownloadService.INTENT_CANCEL
                    putExtra(VideoDownloadService.KEY_VIDEO_ID, video.id)
                }
                requireContext().startService(intent)
                bindVideoDownloadService(true)
            }
        }
    }

    fun bindVideoDownloadService(started: Boolean = false) {
        if (videoDownloadServiceConnection == null) {
            val listener = object : VideoDownloadService.Listener {
                override fun update(downloadProgress: DownloadProgress) {
                    progressViewModel.update(downloadProgress, false)
                }

                override fun unbind() {
                    progressViewModel.replace(false, emptyList())
                    videoDownloadService?.listener = null
                    videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
                    videoDownloadServiceConnection = null
                    videoDownloadService = null
                }
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (view != null) {
                        val binder = service as VideoDownloadService.ServiceBinder
                        videoDownloadService = binder.getService()
                        if (started || !videoDownloadService?.activeDownloads.isNullOrEmpty()) {
                            progressViewModel.replace(false, videoDownloadService?.activeDownloads?.toList().orEmpty())
                            videoDownloadService?.listener = listener
                            videoDownloadService?.activeDownloads?.toList()?.forEach {
                                listener.update(it)
                            }
                        } else {
                            videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
                            videoDownloadServiceConnection = null
                            videoDownloadService?.stopSelf()
                            videoDownloadService = null
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    videoDownloadService = null
                    progressViewModel.replace(false, emptyList())
                }
            }
            val intent = Intent(requireContext(), VideoDownloadService::class.java)
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
            videoDownloadServiceConnection = connection
        }
    }

    fun bindStreamDownloadService(started: Boolean = false) {
        if (streamDownloadServiceConnection == null) {
            val listener = object : StreamDownloadService.Listener {
                override fun unbind() {
                    progressViewModel.replace(true, emptyList())
                    streamDownloadService?.listener = null
                    streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
                    streamDownloadServiceConnection = null
                    streamDownloadService?.stopSelf()
                    streamDownloadService = null
                }
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (view != null) {
                        val binder = service as StreamDownloadService.ServiceBinder
                        streamDownloadService = binder.getService()
                        if (started || !streamDownloadService?.activeDownloads.isNullOrEmpty()) {
                            progressViewModel.replace(true, streamDownloadService?.activeDownloads?.toList().orEmpty())
                            streamDownloadService?.listener = listener
                        } else {
                            streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
                            streamDownloadServiceConnection = null
                            streamDownloadService = null
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    streamDownloadService = null
                    progressViewModel.replace(true, emptyList())
                }
            }
            val intent = Intent(requireContext(), StreamDownloadService::class.java)
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
            streamDownloadServiceConnection = connection
        }
    }

    override fun onStop() {
        super.onStop()
        progressViewModel.replace(false, emptyList())
        videoDownloadService?.listener = null
        videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
        videoDownloadServiceConnection = null
        videoDownloadService = null
        progressViewModel.replace(true, emptyList())
        streamDownloadService?.listener = null
        streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
        streamDownloadServiceConnection = null
        streamDownloadService = null
    }

    override fun scrollToTop() {
        viewLifecycleOwner.lifecycleScope.launch { gridState?.scrollToItem(0) }
    }

    override fun onNetworkRestored() {
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
    }

    override fun onDestroyView() {
        collectionJob?.cancel()
        collectionJob = null
        actions = null
        gridState = null
        super.onDestroyView()
    }
}

class DownloadsProgressViewModel : ViewModel() {
    private val tracker = DownloadsProgressTracker()
    val progress: StateFlow<Map<Int, DownloadProgressState>> = tracker.progress

    fun update(progress: DownloadProgress, live: Boolean) = tracker.update(progress, live)

    fun replace(live: Boolean, downloads: List<DownloadProgress>) = tracker.replace(live, downloads)
}
