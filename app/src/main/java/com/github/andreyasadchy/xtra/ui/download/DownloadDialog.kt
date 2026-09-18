package com.github.andreyasadchy.xtra.ui.download

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.download.DownloadViewModel.Companion.DownloadViewModelFactory
import com.github.andreyasadchy.xtra.ui.downloads.DownloadForm
import com.github.andreyasadchy.xtra.ui.downloads.DownloadFormLabels
import com.github.andreyasadchy.xtra.ui.downloads.DownloadFormState
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.launch
import java.io.File

class DownloadDialog : DialogFragment(), IntegrityDialog.Listener {

    companion object {
        private const val STREAM = "stream"
        private const val VIDEO = "video"
        private const val CLIP = "clip"
        private const val KEY_TYPE = "type"
        private const val KEY_STREAM_ID = "streamId"
        private const val KEY_VIDEO_ID = "videoId"
        private const val KEY_CLIP_ID = "clipId"
        private const val KEY_CHANNEL_ID = "channelId"
        private const val KEY_CHANNEL_LOGIN = "channelLogin"
        private const val KEY_CHANNEL_NAME = "channelName"
        private const val KEY_CHANNEL_IMAGE = "channelImage"
        private const val KEY_GAME_ID = "gameId"
        private const val KEY_GAME_SLUG = "gameSlug"
        private const val KEY_GAME_NAME = "gameName"
        private const val KEY_TITLE = "title"
        private const val KEY_THUMBNAIL = "thumbnail"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_DURATION_SECONDS = "durationSeconds"
        private const val KEY_VIDEO_TYPE = "videoType"
        private const val KEY_VIDEO_OFFSET_SECONDS = "videoOffsetSeconds"
        private const val KEY_VIDEO_CREATED_AT = "videoCreatedAt"
        private const val KEY_VIDEO_ANIMATED_PREVIEW = "animatedPreviewUrl"
        private const val KEY_VIDEO_TOTAL_DURATION = "totalDuration"
        private const val KEY_VIDEO_CURRENT_POSITION = "currentPosition"
        private const val KEY_QUALITY_NAMES = "quality_names"
        private const val KEY_QUALITY_RESOLUTIONS = "quality_resolutions"
        private const val KEY_QUALITY_FRAME_RATES = "quality_frame_rates"
        private const val KEY_QUALITY_BITRATES = "quality_bitrates"
        private const val KEY_QUALITY_CODECS = "quality_codecs"
        private const val KEY_QUALITY_URLS = "quality_urls"

        fun newStreamInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = commonArguments(channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, qualityNames, qualityResolutions, qualityFrameRates, qualityBitrates, qualityCodecs, qualityUrls).apply {
                    putString(KEY_TYPE, STREAM)
                    putString(KEY_STREAM_ID, id)
                }
            }
        }

        fun newVideoInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, durationSeconds: Int?, type: String?, animatedPreviewUrl: String?, totalDuration: Long? = null, currentPosition: Long? = null, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = commonArguments(channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, qualityNames, qualityResolutions, qualityFrameRates, qualityBitrates, qualityCodecs, qualityUrls).apply {
                    putString(KEY_TYPE, VIDEO)
                    putString(KEY_VIDEO_ID, id)
                    putInt(KEY_DURATION_SECONDS, durationSeconds ?: -1)
                    putString(KEY_VIDEO_TYPE, type)
                    putString(KEY_VIDEO_ANIMATED_PREVIEW, animatedPreviewUrl)
                    putLong(KEY_VIDEO_TOTAL_DURATION, totalDuration ?: -1)
                    putLong(KEY_VIDEO_CURRENT_POSITION, currentPosition ?: -1)
                }
            }
        }

        fun newClipInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, durationSeconds: Int?, videoId: String?, videoOffsetSeconds: Int?, videoCreatedAt: String?, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = commonArguments(channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, qualityNames, qualityResolutions, qualityFrameRates, qualityBitrates, qualityCodecs, qualityUrls).apply {
                    putString(KEY_TYPE, CLIP)
                    putString(KEY_CLIP_ID, id)
                    putInt(KEY_DURATION_SECONDS, durationSeconds ?: -1)
                    putString(KEY_VIDEO_ID, videoId)
                    putInt(KEY_VIDEO_OFFSET_SECONDS, videoOffsetSeconds ?: -1)
                    putString(KEY_VIDEO_CREATED_AT, videoCreatedAt)
                }
            }
        }

        private fun commonArguments(channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, qualityNames: Array<String>?, qualityResolutions: Array<String>?, qualityFrameRates: Array<String>?, qualityBitrates: Array<String>?, qualityCodecs: Array<String>?, qualityUrls: Array<String>?) = Bundle().apply {
            putString(KEY_CHANNEL_ID, channelId)
            putString(KEY_CHANNEL_LOGIN, channelLogin)
            putString(KEY_CHANNEL_NAME, channelName)
            putString(KEY_CHANNEL_IMAGE, channelImage)
            putString(KEY_GAME_ID, gameId)
            putString(KEY_GAME_SLUG, gameSlug)
            putString(KEY_GAME_NAME, gameName)
            putString(KEY_TITLE, title)
            putString(KEY_THUMBNAIL, thumbnail)
            putString(KEY_CREATED_AT, createdAt)
            putStringArray(KEY_QUALITY_NAMES, qualityNames)
            putStringArray(KEY_QUALITY_RESOLUTIONS, qualityResolutions)
            putStringArray(KEY_QUALITY_FRAME_RATES, qualityFrameRates)
            putStringArray(KEY_QUALITY_BITRATES, qualityBitrates)
            putStringArray(KEY_QUALITY_CODECS, qualityCodecs)
            putStringArray(KEY_QUALITY_URLS, qualityUrls)
        }
    }

    private val viewModel: DownloadViewModel by viewModels { DownloadViewModelFactory }
    private var composeView: ComposeView? = null
    private var storage = emptyList<Pair<String, String>>()
    private val totalDuration get() = requireArguments().getLong(KEY_VIDEO_TOTAL_DURATION, -1).takeIf { it != -1L }
        ?: requireArguments().getInt(KEY_DURATION_SECONDS, -1).takeIf { it != -1 }?.times(1000L) ?: 0L
    private val currentPosition get() = requireArguments().getLong(KEY_VIDEO_CURRENT_POSITION)
    private val directoryResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                if (it.authority?.startsWith("com.android.providers") == true) {
                    Toast.makeText(requireActivity(), R.string.invalid_directory, Toast.LENGTH_LONG).show()
                } else {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    viewModel.sharedPath = it.toString()
                    viewModel.updateForm(viewModel.form.value.copy(directory = it.path?.substringAfter("/tree/")?.removeSuffix(":")))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = requireContext().prefs()
        storage = requireContext().getExternalFilesDirs(".downloads").mapIndexedNotNull { index, file ->
            file?.absolutePath?.let { path ->
                if (index == 0) getString(R.string.internal_storage) to path
                else path.substringBefore("/Android/data", "").takeIf { it.isNotBlank() }?.let { it.substringAfterLast(File.separatorChar) to path }
            }
        }
        if (!viewModel.form.value.initialized) {
            viewModel.sharedPath = savedInstanceState?.getString("formSharedPath") ?: prefs.getString(C.DOWNLOAD_SHARED_PATH, null)
            viewModel.updateForm(DownloadFormState(
                initialized = true,
                quality = savedInstanceState?.getInt("formQuality") ?: 0,
                from = savedInstanceState?.getString("formFrom").orEmpty(),
                to = savedInstanceState?.getString("formTo").orEmpty(),
                location = savedInstanceState?.getInt("formLocation") ?: prefs.getInt(C.DOWNLOAD_LOCATION, 0),
                storage = if (storage.size <= 1) 0 else savedInstanceState?.getInt("formStorage") ?: prefs.getInt(C.DOWNLOAD_STORAGE, 0),
                directory = viewModel.sharedPath?.let { Uri.decode(it.substringAfter("/tree/")) },
                downloadChat = savedInstanceState?.getBoolean("formChat") ?: prefs.getBoolean(C.DOWNLOAD_CHAT, false),
                downloadChatEmotes = savedInstanceState?.getBoolean("formEmotes") ?: prefs.getBoolean(C.DOWNLOAD_CHAT_EMOTES, false),
            ))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.integrity.collect { (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) } }
                launch {
                    viewModel.dismiss.collect {
                        if (it) {
                            Toast.makeText(requireActivity(), R.string.video_subscribers_only, Toast.LENGTH_LONG).show()
                            dismiss()
                        }
                    }
                }
            }
        }
        loadQualities(requireArguments().getString(KEY_TYPE))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = requireContext().getAlertDialogBuilder()
        val view = ComposeView(builder.context).apply {
            setViewTreeLifecycleOwner(this@DownloadDialog)
            setViewTreeSavedStateRegistryOwner(this@DownloadDialog)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@DownloadDialog.lifecycle))
            setContent {
                val state by viewModel.form.collectAsState()
                val qualities by viewModel.qualities.collectAsState()
                val theme = rememberThemeId()
                val qualityNames = qualityNames(qualities.orEmpty())
                XtraTheme(themeId = theme) {
                    DownloadForm(
                        state = state,
                        labels = DownloadFormLabels(
                            getString(R.string.select_quality), getString(R.string.specify_time), getString(R.string.from), getString(R.string.to),
                            getString(R.string.save_to), getString(R.string.no_storage_detected), getString(R.string.select_directory),
                            getString(R.string.download_chat), getString(R.string.download_chat_emotes), getString(android.R.string.cancel), getString(R.string.download),
                        ),
                        qualities = qualityNames,
                        preview = requireArguments().getString(KEY_THUMBNAIL),
                        previewTitle = requireArguments().getString(KEY_TITLE),
                        duration = if (requireArguments().getString(KEY_TYPE) == VIDEO) getString(R.string.duration, DateUtils.formatElapsedTime(totalDuration / 1000L)) else null,
                        defaultFrom = timeHint(currentPosition), defaultTo = timeHint(totalDuration),
                        storageAvailable = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED,
                        locations = resources.getStringArray(R.array.spinnerStorage).toList(),
                        storageNames = if (storage.size > 1) storage.map { it.first } else emptyList(),
                        onChange = viewModel::updateForm,
                        onDirectory = {
                            viewModel.selectedQuality = qualityNames.getOrNull(state.quality)
                            savePreferences(state)
                            directoryResultLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { putExtra(DocumentsContract.EXTRA_INITIAL_URI, viewModel.sharedPath) })
                        },
                        onDownload = ::download,
                        onCancel = { dismiss() },
                    )
                }
            }
        }
        composeView = view
        return builder.setView(view).create()
    }

    private fun timeHint(time: Long) = DateUtils.formatElapsedTime(time / 1000L).let { if (it.length == 5) "00:$it" else it }

    private fun qualityNames(qualities: List<VideoQuality>): List<String> {
        val hideCodecs = qualities.all { val codec = it.codecs?.substringBefore('.'); codec == "avc1" || codec == "mp4a" || codec.isNullOrBlank() }
        val names = mutableListOf<String>()
        qualities.forEach { quality ->
            val qualityNameProp = quality.name
            val name = when (qualityNameProp) {
                VideoQuality.SOURCE_QUALITY -> getString(R.string.source)
                VideoQuality.AUDIO_ONLY_QUALITY -> getString(R.string.audio_only)
                else -> {
                    val frameRate = qualityNameProp?.substringAfter("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                    val qualityName = if (qualityNameProp != null && frameRate != null && frameRate <= 30) qualityNameProp.substring(0, qualityNameProp.indexOf('p') + 1) else qualityNameProp.toString()
                    if (hideCodecs) qualityName else {
                        val codec = quality.codecs?.substringBefore('.')
                        val codecName = when {
                            codec == "av01" -> "AV1"
                            codec == "hev1" || codec == "hvc1" -> "H.265"
                            codec == "avc1" || codec.isNullOrBlank() -> "H.264"
                            else -> codec
                        }
                        "$qualityName $codecName"
                    }
                }
            }
            names.add(if (name in names) "$name ${quality.bitrate?.div(1000)} Kbps" else name)
        }
        return names
    }

    private fun argumentQualities(): List<VideoQuality>? {
        val args = requireArguments()
        val names = args.getStringArray(KEY_QUALITY_NAMES) ?: return null
        val resolutions = args.getStringArray(KEY_QUALITY_RESOLUTIONS) ?: return null
        val frameRates = args.getStringArray(KEY_QUALITY_FRAME_RATES) ?: return null
        val bitrates = args.getStringArray(KEY_QUALITY_BITRATES) ?: return null
        val codecs = args.getStringArray(KEY_QUALITY_CODECS) ?: return null
        val urls = args.getStringArray(KEY_QUALITY_URLS) ?: return null
        return names.mapIndexed { index, name ->
            VideoQuality(name, resolutions.getOrNull(index).takeIf { it != "null" }?.toIntOrNull(), frameRates.getOrNull(index).takeIf { it != "null" }?.toFloatOrNull(), bitrates.getOrNull(index).takeIf { it != "null" }?.toIntOrNull(), codecs.getOrNull(index).takeIf { it != "null" }, urls.getOrNull(index))
        }
    }

    private fun loadQualities(type: String?) {
        val prefs = requireContext().prefs()
        when (type) {
            STREAM -> viewModel.setStream(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN), qualities = argumentQualities(),
                platform = prefs.getString(C.TOKEN_PLATFORM, "web"), playerType = prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"), enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
            VIDEO -> viewModel.setVideo(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                videoId = requireArguments().getString(KEY_VIDEO_ID), animatedPreviewUrl = requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW),
                videoType = requireArguments().getString(KEY_VIDEO_TYPE), qualities = argumentQualities(),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"), enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
            CLIP -> viewModel.setClip(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()), clipId = requireArguments().getString(KEY_CLIP_ID),
                qualities = argumentQualities(), enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    private fun savePreferences(state: DownloadFormState) {
        requireContext().prefs().edit {
            putInt(C.DOWNLOAD_LOCATION, state.location)
            when (state.location) {
                0 -> putString(C.DOWNLOAD_SHARED_PATH, viewModel.sharedPath)
                1 -> putInt(C.DOWNLOAD_STORAGE, if (storage.size > 1) state.storage else 0)
            }
            putBoolean(C.DOWNLOAD_CHAT, state.downloadChat)
            putBoolean(C.DOWNLOAD_CHAT_EMOTES, state.downloadChatEmotes)
        }
    }

    private fun download() {
        val state = viewModel.form.value
        val quality = viewModel.qualities.value?.getOrNull(state.quality)
        val path = when (state.location) {
            0 -> viewModel.sharedPath
            1 -> storage.getOrNull(if (storage.size > 1) state.storage else 0)?.second
            else -> null
        }
        val qualityName = quality?.name
        val qualityUrl = quality?.url
        if (qualityName != null && qualityUrl != null && !path.isNullOrBlank()) {
            val args = requireArguments()
            val prefs = requireContext().prefs()
            when (args.getString(KEY_TYPE)) {
                STREAM -> (requireActivity() as? MainActivity)?.downloadStream(
                    filesDir = requireContext().filesDir.path,
                    id = args.getString(KEY_STREAM_ID), title = args.getString(KEY_TITLE), createdAt = args.getString(KEY_CREATED_AT),
                    channelId = args.getString(KEY_CHANNEL_ID), channelLogin = args.getString(KEY_CHANNEL_LOGIN), channelName = args.getString(KEY_CHANNEL_NAME), channelImage = args.getString(KEY_CHANNEL_IMAGE),
                    thumbnail = args.getString(KEY_THUMBNAIL), gameId = args.getString(KEY_GAME_ID), gameSlug = args.getString(KEY_GAME_SLUG), gameName = args.getString(KEY_GAME_NAME),
                    downloadPath = path, quality = qualityName, downloadChat = state.downloadChat, downloadChatEmotes = state.downloadChatEmotes, wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false),
                )
                VIDEO -> {
                    val from = if (state.from.isEmpty()) currentPosition else parseTime(state.from)
                    if (from == null) { viewModel.updateForm(state.copy(fromError = getString(R.string.invalid_time))); return }
                    val to = if (state.to.isEmpty()) totalDuration else parseTime(state.to)
                    if (to == null) { viewModel.updateForm(state.copy(toError = getString(R.string.invalid_time))); return }
                    when {
                        to > totalDuration -> { viewModel.updateForm(state.copy(toError = getString(R.string.to_is_longer))); return }
                        from >= to -> { viewModel.updateForm(state.copy(fromError = getString(R.string.from_is_greater))); return }
                        from < to -> (requireActivity() as? MainActivity)?.downloadVideo(
                            filesDir = requireContext().filesDir.path,
                            id = args.getString(KEY_VIDEO_ID), title = args.getString(KEY_TITLE), createdAt = args.getString(KEY_CREATED_AT), type = args.getString(KEY_VIDEO_TYPE),
                            channelId = args.getString(KEY_CHANNEL_ID), channelLogin = args.getString(KEY_CHANNEL_LOGIN), channelName = args.getString(KEY_CHANNEL_NAME), channelImage = args.getString(KEY_CHANNEL_IMAGE),
                            thumbnail = args.getString(KEY_THUMBNAIL), gameId = args.getString(KEY_GAME_ID), gameSlug = args.getString(KEY_GAME_SLUG), gameName = args.getString(KEY_GAME_NAME),
                            url = qualityUrl, downloadPath = path, quality = qualityName, from = from, to = to,
                            downloadChat = state.downloadChat, downloadChatEmotes = state.downloadChatEmotes,
                            playlistToFile = prefs.getBoolean(C.DOWNLOAD_PLAYLIST_TO_FILE, false), wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false),
                        )
                        else -> { viewModel.updateForm(state.copy(toError = getString(R.string.to_is_lesser))); return }
                    }
                }
                CLIP -> (requireActivity() as? MainActivity)?.downloadClip(
                    filesDir = requireContext().filesDir.path,
                    clipId = args.getString(KEY_CLIP_ID), title = args.getString(KEY_TITLE), createdAt = args.getString(KEY_CREATED_AT), durationSeconds = args.getInt(KEY_DURATION_SECONDS),
                    videoId = args.getString(KEY_VIDEO_ID), videoOffsetSeconds = args.getInt(KEY_VIDEO_OFFSET_SECONDS), videoCreatedAt = args.getString(KEY_VIDEO_CREATED_AT),
                    channelId = args.getString(KEY_CHANNEL_ID), channelLogin = args.getString(KEY_CHANNEL_LOGIN), channelName = args.getString(KEY_CHANNEL_NAME), channelImage = args.getString(KEY_CHANNEL_IMAGE),
                    thumbnail = args.getString(KEY_THUMBNAIL), gameId = args.getString(KEY_GAME_ID), gameSlug = args.getString(KEY_GAME_SLUG), gameName = args.getString(KEY_GAME_NAME),
                    url = qualityUrl, downloadPath = path, quality = qualityName, downloadChat = state.downloadChat, downloadChatEmotes = state.downloadChatEmotes, wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false),
                )
            }
            savePreferences(state)
            if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !requireActivity().prefs().getBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, false)) {
                requireActivity().prefs().edit { putBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, true) }
                val activity = requireActivity()
                activity.getAlertDialogBuilder().setMessage(R.string.notification_permission_message).setTitle(R.string.notification_permission_title)
                    .setPositiveButton(android.R.string.ok) { _, _ -> ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        dismiss()
    }

    private fun parseTime(text: CharSequence): Long? {
        val list = text.split(':', limit = 3).reversed()
        val seconds = list.getOrNull(0)?.let { it.toLongOrNull()?.takeIf { it in 0..59 } ?: return null } ?: 0
        val minutes = list.getOrNull(1)?.let { it.toLongOrNull()?.takeIf { it in 0..59 } ?: return null } ?: 0
        val hours = list.getOrNull(2)?.let { it.toLongOrNull() ?: return null } ?: 0
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000
    }

    override fun onIntegrityTokenLoaded(callback: String?) { loadQualities(callback) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = viewModel.form.value
        outState.putString("formSharedPath", viewModel.sharedPath)
        outState.putInt("formQuality", state.quality)
        outState.putString("formFrom", state.from)
        outState.putString("formTo", state.to)
        outState.putInt("formLocation", state.location)
        outState.putInt("formStorage", state.storage)
        outState.putBoolean("formChat", state.downloadChat)
        outState.putBoolean("formEmotes", state.downloadChatEmotes)
    }

    override fun onDestroyView() {
        composeView?.disposeComposition()
        composeView = null
        super.onDestroyView()
    }
}
