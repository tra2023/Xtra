package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.lifecycle.ViewModel
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.downloads.DownloadsList
import com.github.andreyasadchy.xtra.ui.downloads.StorageSelector
import com.github.andreyasadchy.xtra.ui.downloads.DownloadCheckBox
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.download.StreamDownloadService
import com.github.andreyasadchy.xtra.ui.download.VideoDownloadService
import com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsViewModel.Companion.DownloadsViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : PagedListFragment(), Scrollable {

    private val viewModel: DownloadsViewModel by viewModels { DownloadsViewModelFactory }
    private val progressViewModel: DownloadsProgressViewModel by viewModels()
    private var pagingDiffer: AsyncPagingDataDiffer<OfflineVideo>? = null
    private var collectionJob: Job? = null
    private var gridState by mutableStateOf<LazyGridState?>(null)
    private var bottomInset by mutableIntStateOf(0)
    private var pageVersion by mutableIntStateOf(0)
    private var insertionScroll by mutableIntStateOf(0)
    private var loading by mutableStateOf(true)
    private var actions by mutableStateOf<DownloadsAdapter?>(null)
    private val actionDialogs = mutableListOf<androidx.appcompat.app.AlertDialog>()
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
        var receivedInsertion = false
        bottomInset = 0
        pageVersion = 0
        insertionScroll = 0
        loading = true
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<OfflineVideo>() {
                override fun areItemsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo) = false
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    if (receivedInsertion && position == 0) insertionScroll++
                    receivedInsertion = true
                    pageVersion++
                }
                override fun onRemoved(position: Int, count: Int) { pageVersion++ }
                override fun onMoved(fromPosition: Int, toPosition: Int) { pageVersion++ }
                override fun onChanged(position: Int, count: Int, payload: Any?) { pageVersion++ }
            },
        )
        pagingDiffer = differ
        return ComposeView(requireContext()).apply {
            id = R.id.swipeRefresh
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration = LocalConfiguration.current
                val prefs = requireContext().prefs()
                val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
                    prefs.getString(if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) C.UI_THEME_DARK_ON else C.UI_THEME_DARK_OFF, if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "0" else "2")
                } else prefs.getString(C.THEME, "0")
                val columns = prefs.getString(if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT, if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "1" else "2")?.toIntOrNull() ?: 1
                val state = rememberLazyGridState()
                DisposableEffect(state) {
                    gridState = state
                    onDispose { gridState = null }
                }
                LaunchedEffect(insertionScroll) { if (insertionScroll > 0) state.animateScrollToItem(0) }
                val snapshot = remember(pageVersion) { differ.snapshot() }
                val progress by progressViewModel.progress.collectAsState()
                val adapter = actions
                val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
                fun find(id: Int) = differ.snapshot().items.find { it.id == id }
                XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                    DownloadsList(
                        itemCount = snapshot.size,
                        itemKey = { snapshot[it]?.id ?: "placeholder:$it" },
                        itemAt = { index ->
                            if (index < differ.itemCount) differ.getItem(index)
                            snapshot[index]?.let { adapter?.item(it, progress[it.id]) }
                        },
                        loading = loading, columns = columns, state = state,
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
                }
            }
        }
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
                    var checked by mutableIntStateOf(if (storage.size == 1) 0 else requireContext().prefs().getInt(C.DOWNLOAD_STORAGE, 0))
                    val mounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
                    showActionDialog(
                        content = {
                            StorageSelector(
                                title = getString(R.string.save_to), noStorageText = getString(R.string.no_storage_detected),
                                selectDirectoryText = getString(R.string.select_directory), available = mounted,
                                locations = emptyList(), location = 1, storageNames = storage.map { it.first },
                                selectedStorage = checked, directory = null, onLocation = {}, onStorage = { checked = it }, onDirectory = {},
                            )
                        },
                        confirm = {
                            if (mounted) storage.getOrNull(checked)?.let { storage ->
                                requireContext().prefs().edit { putInt(C.DOWNLOAD_STORAGE, checked) }
                                viewModel.moveToAppStorage(storage.second, it)
                            }
                        },
                    )
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
                val delete = getString(R.string.delete)
                var keepFiles by mutableStateOf(true)
                showActionDialog(
                    title = delete,
                    message = getString(R.string.are_you_sure),
                    confirmText = delete,
                    content = { DownloadCheckBox(getString(R.string.keep_files), keepFiles) { keepFiles = it } },
                    confirm = {
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
                        viewModel.delete(video, keepFiles)
                    },
                )
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
        val differ = pagingDiffer ?: return
        collectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.flow.collectLatest { differ.submitData(it) } }
                launch { differ.loadStateFlow.collectLatest { loading = it.refresh is LoadState.Loading; pageVersion++ } }
                launch { differ.onPagesUpdatedFlow.collectLatest { pageVersion++ } }
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

    private fun showActionDialog(
        title: String? = null,
        message: String? = null,
        confirmText: String = getString(android.R.string.ok),
        content: @androidx.compose.runtime.Composable () -> Unit,
        confirm: () -> Unit,
    ) {
        val builder = requireActivity().getAlertDialogBuilder().setTitle(title).setMessage(message)
        val composeView = ComposeView(builder.context).apply {
            setViewTreeLifecycleOwner(viewLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@DownloadsFragment)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val prefs = requireContext().prefs()
                val night = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) prefs.getString(if (night) C.UI_THEME_DARK_ON else C.UI_THEME_DARK_OFF, if (night) "0" else "2") else prefs.getString(C.THEME, "0")
                XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") { content() }
            }
        }
        val dialog = builder.setView(composeView)
            .setPositiveButton(confirmText) { _, _ -> confirm() }
            .setNegativeButton(android.R.string.cancel, null).create()
        actionDialogs.add(dialog)
        dialog.setOnDismissListener { composeView.disposeComposition(); actionDialogs.remove(dialog) }
        dialog.show()
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
        actionDialogs.toList().forEach { it.dismiss() }
        pagingDiffer = null
        actions = null
        gridState = null
        super.onDestroyView()
    }
}

data class DownloadProgressState(
    val live: Boolean,
    val progress: Int,
    val maxProgress: Int,
    val chatProgress: Int,
    val maxChatProgress: Int,
)

class DownloadsProgressViewModel : ViewModel() {
    private val _progress = MutableStateFlow<Map<Int, DownloadProgressState>>(emptyMap())
    val progress: StateFlow<Map<Int, DownloadProgressState>> = _progress

    fun update(progress: DownloadProgress, live: Boolean) {
        val snapshot = snapshot(progress, live)
        _progress.update { it + (progress.id to snapshot) }
    }

    fun replace(live: Boolean, downloads: List<DownloadProgress>) {
        val snapshots = downloads.associate { it.id to snapshot(it, live) }
        _progress.update { current -> current.filterValues { it.live != live } + snapshots }
    }

    private fun snapshot(progress: DownloadProgress, live: Boolean) = DownloadProgressState(
        live, progress.progress, progress.maxProgress, progress.chatProgress, progress.maxChatProgress,
    )
}
