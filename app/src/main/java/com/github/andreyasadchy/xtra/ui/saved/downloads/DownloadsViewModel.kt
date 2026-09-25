package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.util.chat.parseVideoMetadataFromChatJson
import com.github.andreyasadchy.xtra.util.m3u8.DownloadPlaylists
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import com.github.andreyasadchy.xtra.util.m3u8.parseMediaPlaylist
import com.github.andreyasadchy.xtra.util.m3u8.writeMediaPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max

class DownloadsViewModel(
    private val applicationContext: Context,
    private val offlineVideosRepository: OfflineVideosRepository,
) : ViewModel() {

    var selectedVideo: OfflineVideo? = null
    private val videosInUse = mutableListOf<OfflineVideo>()

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        offlineVideosRepository.getAll()
    }.flow.cachedIn(viewModelScope)

    suspend fun getActiveDownloads(): List<OfflineVideo> {
        return offlineVideosRepository.getActiveDownloads()
    }

    fun updateDownloadStatus(video: OfflineVideo, waitForWifi: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            offlineVideosRepository.update(video.apply {
                status = if (waitForWifi) {
                    OfflineVideo.STATUS_WAITING_FOR_WIFI
                } else {
                    OfflineVideo.STATUS_PENDING
                }
            })
        }
    }

    fun finishDownload(video: OfflineVideo) {
        video.chatUrl?.let { url ->
            val isShared = url.toUri().scheme == ContentResolver.SCHEME_CONTENT
            if (isShared) {
                applicationContext.contentResolver.openFileDescriptor(url.toUri(), "rw")!!.use {
                    FileOutputStream(it.fileDescriptor).use { output ->
                        output.channel.truncate(video.chatBytes)
                    }
                }
            } else {
                FileOutputStream(url).use { output ->
                    output.channel.truncate(video.chatBytes)
                }
            }
            if (isShared) {
                applicationContext.contentResolver.openOutputStream(url.toUri(), "wa")!!.bufferedWriter()
            } else {
                FileOutputStream(url, true).bufferedWriter()
            }.use { writer ->
                if (video.liveCommentsArrayStarted) {
                    writer.write("]")
                }
                writer.write("}")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            offlineVideosRepository.update(video.apply {
                status = OfflineVideo.STATUS_DOWNLOADED
            })
        }
    }

    fun convertToFile(video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                offlineVideosRepository.update(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_CONVERTING
                })
                if (videoUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                    val oldVideoDirectoryUri = videoUrl.substringBeforeLast("%2F")
                    val oldDirectoryUri = oldVideoDirectoryUri.substringBeforeLast("%2F", oldVideoDirectoryUri.substringBeforeLast("%3A") + "%3A")
                    val oldPlaylist = applicationContext.contentResolver.openInputStream(videoUrl.toUri())!!.use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                    val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.segments.first().uri.substringAfterLast(".")}"
                    val newVideoFileUri = DownloadPlaylists.joinDirectory(oldDirectoryUri, videoFileName)
                    val tracksToDelete = DownloadPlaylists.basenames(oldPlaylist.segments).toMutableList()
                    val playlists = offlineVideosRepository.getPlaylists().mapNotNull { video ->
                        video.url?.takeIf {
                            it.toUri().scheme == ContentResolver.SCHEME_CONTENT
                                    && it.substringBeforeLast("%2F") == oldVideoDirectoryUri
                                    && it != videoUrl
                        }
                    }
                    playlists.forEach { uri ->
                        try {
                            val p = applicationContext.contentResolver.openInputStream(uri.toUri())!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            tracksToDelete.removeAll(DownloadPlaylists.basenames(p.segments))
                        } catch (e: Exception) {

                        }
                    }
                    val new = try {
                        applicationContext.contentResolver.openOutputStream(newVideoFileUri.toUri())!!.close()
                        false
                    } catch (e: IllegalArgumentException) {
                        DocumentsContract.createDocument(applicationContext.contentResolver, oldDirectoryUri.toUri(), "", videoFileName)
                        true
                    }
                    val convertInitSegmentUri = oldPlaylist.initSegmentUri
                    if (convertInitSegmentUri != null && new) {
                        val oldFileUri = convertInitSegmentUri
                        applicationContext.contentResolver.openOutputStream(newVideoFileUri.toUri(), "wa")!!.use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldFileUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    offlineVideosRepository.update(video.apply {
                        maxProgress = tracksToDelete.count()
                    })
                    oldPlaylist.segments.forEach { track ->
                        val oldFileUri = track.uri
                        val oldFileName = DownloadPlaylists.basename(oldFileUri)
                        applicationContext.contentResolver.openOutputStream(newVideoFileUri.toUri(), "wa")!!.use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldFileUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        if (tracksToDelete.contains(oldFileName)) {
                            try {
                                DocumentsContract.deleteDocument(applicationContext.contentResolver, oldFileUri.toUri())
                            } catch (e: Exception) {

                            }
                        }
                        offlineVideosRepository.update(video.apply {
                            progress += 1
                        })
                    }
                    offlineVideosRepository.update(video.apply {
                        thumbnail.let {
                            if (it == null || it == url || !File(it).exists()) {
                                thumbnail = newVideoFileUri
                            }
                        }
                        url = newVideoFileUri
                    })
                    if (playlists.isNotEmpty()) {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, videoUrl.toUri())
                        } catch (e: Exception) {

                        }
                    } else {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, oldVideoDirectoryUri.toUri())
                        } catch (e: Exception) {

                        }
                    }
                } else {
                    val oldPlaylistFile = File(videoUrl)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        val oldDirectory = oldVideoDirectory?.parentFile
                        if (oldVideoDirectory != null && oldDirectory != null) {
                            val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.segments.first().uri.substringAfterLast(".")}"
                            val newVideoFileUri = "${oldDirectory.path}${File.separator}$videoFileName"
                            val tracksToDelete = DownloadPlaylists.basenames(oldPlaylist.segments).toMutableList()
                            val playlists = oldVideoDirectory.listFiles { it.extension == "m3u8" && it != oldPlaylistFile }
                            playlists?.forEach { file ->
                                val p = PlaylistUtils.parseMediaPlaylist(file.inputStream())
                                tracksToDelete.removeAll(DownloadPlaylists.basenames(p.segments))
                            }
                            val convertFileInitSegmentUri = oldPlaylist.initSegmentUri
                            if (convertFileInitSegmentUri != null && File(newVideoFileUri).length() == 0L) {
                                val oldFile = File(oldVideoDirectory.path + File.separator + DownloadPlaylists.basename(convertFileInitSegmentUri))
                                if (oldFile.exists()) {
                                    FileOutputStream(newVideoFileUri).use { outputStream ->
                                        oldFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                            }
                            offlineVideosRepository.update(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            oldPlaylist.segments.forEach { track ->
                                val oldFile = File(oldVideoDirectory.path + File.separator + DownloadPlaylists.basename(track.uri))
                                if (oldFile.exists()) {
                                    FileOutputStream(newVideoFileUri).use { outputStream ->
                                        oldFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                    if (tracksToDelete.contains(oldFile.name)) {
                                        oldFile.delete()
                                    }
                                }
                                offlineVideosRepository.update(video.apply {
                                    progress += 1
                                })
                            }
                            offlineVideosRepository.update(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newVideoFileUri
                                    }
                                }
                                url = newVideoFileUri
                            })
                            if (playlists?.isNotEmpty() == true) {
                                oldPlaylistFile.delete()
                            } else {
                                oldVideoDirectory.deleteRecursively()
                            }
                        }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    offlineVideosRepository.update(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun moveToSharedStorage(newUri: Uri, video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                offlineVideosRepository.update(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_MOVING
                })
                if (videoUrl.endsWith(".m3u8")) {
                    val oldPlaylistFile = File(videoUrl)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        if (oldVideoDirectory != null) {
                            val documentId = DocumentsContract.getTreeDocumentId(newUri)
                            val newDirectoryUri = DocumentsContract.buildDocumentUriUsingTree(newUri, documentId)
                            val newVideoDirectoryUri = DownloadPlaylists.joinDirectory(newDirectoryUri.toString(), oldVideoDirectory.name)
                            try {
                                applicationContext.contentResolver.openOutputStream(newVideoDirectoryUri.toUri())!!.close()
                            } catch (e: Exception) {
                                if (e is IllegalArgumentException) {
                                    DocumentsContract.createDocument(applicationContext.contentResolver, newDirectoryUri, DocumentsContract.Document.MIME_TYPE_DIR, oldVideoDirectory.name)
                                }
                            }
                            val newPlaylistFileUri = DownloadPlaylists.joinChild(newVideoDirectoryUri, oldPlaylistFile.name)
                            val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val mapUri = { uri: String -> DownloadPlaylists.joinChild(newVideoDirectoryUri, DownloadPlaylists.basename(uri)) }
                            val segments = DownloadPlaylists.remapSegments(oldPlaylist.segments, mapUri)
                            try {
                                applicationContext.contentResolver.openOutputStream(newPlaylistFileUri.toUri())!!
                            } catch (e: IllegalArgumentException) {
                                DocumentsContract.createDocument(applicationContext.contentResolver, newVideoDirectoryUri.toUri(), "", oldPlaylistFile.name)
                                applicationContext.contentResolver.openOutputStream(newPlaylistFileUri.toUri())!!
                            }.use {
                                PlaylistUtils.writeMediaPlaylist(oldPlaylist.copy(initSegmentUri = oldPlaylist.initSegmentUri?.let(mapUri), segments = segments), it)
                            }
                            val tracksToDelete = DownloadPlaylists.basenames(oldPlaylist.segments).toMutableList()
                            val playlists = oldVideoDirectory.listFiles { it.extension == "m3u8" && it != oldPlaylistFile }
                            playlists?.forEach { file ->
                                val p = PlaylistUtils.parseMediaPlaylist(file.inputStream())
                                tracksToDelete.removeAll(DownloadPlaylists.basenames(p.segments))
                            }
                            val moveSharedInitSegmentUri = oldPlaylist.initSegmentUri
                            if (moveSharedInitSegmentUri != null) {
                                val oldFile = File(oldVideoDirectory.path + File.separator + DownloadPlaylists.basename(moveSharedInitSegmentUri))
                                if (oldFile.exists()) {
                                    val newFileUri = DownloadPlaylists.joinChild(newVideoDirectoryUri, oldFile.name)
                                    try {
                                        applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                                    } catch (e: IllegalArgumentException) {
                                        DocumentsContract.createDocument(applicationContext.contentResolver, newVideoDirectoryUri.toUri(), "", oldFile.name)
                                        applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                                    }.use { outputStream ->
                                        oldFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                            }
                            offlineVideosRepository.update(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            oldPlaylist.segments.forEach { track ->
                                val oldFile = File(oldVideoDirectory.path + File.separator + DownloadPlaylists.basename(track.uri))
                                if (oldFile.exists()) {
                                    val newFileUri = DownloadPlaylists.joinChild(newVideoDirectoryUri, oldFile.name)
                                    try {
                                        applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                                    } catch (e: IllegalArgumentException) {
                                        DocumentsContract.createDocument(applicationContext.contentResolver, newVideoDirectoryUri.toUri(), "", oldFile.name)
                                        applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                                    }.use { outputStream ->
                                        oldFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                    if (tracksToDelete.contains(oldFile.name)) {
                                        oldFile.delete()
                                    }
                                }
                                offlineVideosRepository.update(video.apply {
                                    progress += 1
                                })
                            }
                            val oldChatFile = video.chatUrl?.let { uri -> File(uri).takeIf { it.exists() } }
                            val newChatFileUri = oldChatFile?.let { DownloadPlaylists.joinDirectory(newDirectoryUri.toString(), it.name) }
                            if (newChatFileUri != null) {
                                try {
                                    applicationContext.contentResolver.openOutputStream(newChatFileUri.toUri())!!
                                } catch (e: IllegalArgumentException) {
                                    DocumentsContract.createDocument(applicationContext.contentResolver, newDirectoryUri, "", oldChatFile.name)
                                    applicationContext.contentResolver.openOutputStream(newChatFileUri.toUri())!!
                                }.use { outputStream ->
                                    oldChatFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            offlineVideosRepository.update(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        oldPlaylist.segments.getOrNull(
                                            max(0, (oldPlaylist.segments.size / 2) - 1)
                                        )?.uri?.let { trackUri ->
                                            thumbnail = DownloadPlaylists.joinChild(newVideoDirectoryUri, DownloadPlaylists.basename(trackUri))
                                        }
                                    }
                                }
                                url = newPlaylistFileUri
                                chatUrl = newChatFileUri
                            })
                            if (playlists?.isNotEmpty() == true) {
                                oldPlaylistFile.delete()
                            } else {
                                oldVideoDirectory.deleteRecursively()
                            }
                            oldChatFile?.delete()
                        }
                    }
                } else {
                    val oldFile = File(videoUrl)
                    if (oldFile.exists()) {
                        val documentId = DocumentsContract.getTreeDocumentId(newUri)
                        val newDirectoryUri = DocumentsContract.buildDocumentUriUsingTree(newUri, documentId)
                        val newFileUri = DownloadPlaylists.joinDirectory(newDirectoryUri.toString(), oldFile.name)
                        try {
                            applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                        } catch (e: IllegalArgumentException) {
                            DocumentsContract.createDocument(applicationContext.contentResolver, newDirectoryUri, "", oldFile.name)
                            applicationContext.contentResolver.openOutputStream(newFileUri.toUri())!!
                        }.use { outputStream ->
                            oldFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        val oldChatFile = video.chatUrl?.let { uri -> File(uri).takeIf { it.exists() } }
                        val newChatFileUri = oldChatFile?.let { DownloadPlaylists.joinDirectory(newDirectoryUri.toString(), it.name) }
                        if (newChatFileUri != null) {
                            try {
                                applicationContext.contentResolver.openOutputStream(newChatFileUri.toUri())!!
                            } catch (e: IllegalArgumentException) {
                                DocumentsContract.createDocument(applicationContext.contentResolver, newDirectoryUri, "", oldChatFile.name)
                                applicationContext.contentResolver.openOutputStream(newChatFileUri.toUri())!!
                            }.use { outputStream ->
                                oldChatFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        offlineVideosRepository.update(video.apply {
                            thumbnail.let {
                                if (it == null || it == url || !File(it).exists()) {
                                    thumbnail = newFileUri
                                }
                            }
                            url = newFileUri
                            chatUrl = newChatFileUri
                        })
                        oldFile.delete()
                        oldChatFile?.delete()
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    offlineVideosRepository.update(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun moveToAppStorage(path: String, video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                offlineVideosRepository.update(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_MOVING
                })
                if (videoUrl.endsWith(".m3u8")) {
                    val oldPlaylistFileName = DownloadPlaylists.percentDecode(videoUrl.substringAfterLast("%2F"))
                    val oldVideoDirectoryUri = videoUrl.substringBeforeLast("%2F")
                    val oldVideoDirectoryName = DownloadPlaylists.percentDecode(oldVideoDirectoryUri.substringAfterLast("%2F").substringAfterLast("%3A"))
                    val newVideoDirectoryUri = path + File.separator + oldVideoDirectoryName
                    File(newVideoDirectoryUri).mkdir()
                    val newPlaylistFileUri = newVideoDirectoryUri + File.separator + oldPlaylistFileName
                    val oldPlaylist = applicationContext.contentResolver.openInputStream(videoUrl.toUri())!!.use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                    val mapUri = { uri: String -> newVideoDirectoryUri + File.separator + DownloadPlaylists.percentDecode(DownloadPlaylists.basename(uri)) }
                    val segments = DownloadPlaylists.remapSegments(oldPlaylist.segments, mapUri)
                    FileOutputStream(newPlaylistFileUri).use {
                        PlaylistUtils.writeMediaPlaylist(oldPlaylist.copy(initSegmentUri = oldPlaylist.initSegmentUri?.let(mapUri), segments = segments), it)
                    }
                    val tracksToDelete = DownloadPlaylists.basenames(oldPlaylist.segments).toMutableList()
                    val playlists = offlineVideosRepository.getPlaylists().mapNotNull { video ->
                        video.url?.takeIf {
                            it.toUri().scheme == ContentResolver.SCHEME_CONTENT
                                    && DownloadPlaylists.isSiblingPlaylist(it, oldVideoDirectoryUri, videoUrl)
                        }
                    }
                    playlists.forEach { uri ->
                        try {
                            val p = applicationContext.contentResolver.openInputStream(uri.toUri())!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            tracksToDelete.removeAll(DownloadPlaylists.decodedBasenames(p.segments))
                        } catch (e: Exception) {

                        }
                    }
                    val moveAppInitSegmentUri = oldPlaylist.initSegmentUri
                    if (moveAppInitSegmentUri != null) {
                        val oldFileName = DownloadPlaylists.basename(moveAppInitSegmentUri)
                        val oldFileUri = DownloadPlaylists.joinChild(oldVideoDirectoryUri, oldFileName)
                        val newFileUri = newVideoDirectoryUri + File.separator + DownloadPlaylists.percentDecode(oldFileName)
                        FileOutputStream(newFileUri).use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldFileUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    offlineVideosRepository.update(video.apply {
                        maxProgress = tracksToDelete.count()
                    })
                    oldPlaylist.segments.forEach { track ->
                        val oldFileName = DownloadPlaylists.basename(track.uri)
                        val oldFileUri = DownloadPlaylists.joinChild(oldVideoDirectoryUri, oldFileName)
                        val newFileUri = newVideoDirectoryUri + File.separator + DownloadPlaylists.percentDecode(oldFileName)
                        FileOutputStream(newFileUri).use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldFileUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        if (tracksToDelete.contains(DownloadPlaylists.percentDecode(oldFileName))) {
                            try {
                                DocumentsContract.deleteDocument(applicationContext.contentResolver, oldFileUri.toUri())
                            } catch (e: Exception) {

                            }
                        }
                        offlineVideosRepository.update(video.apply {
                            progress += 1
                        })
                    }
                    val oldChatUri = video.chatUrl
                    val oldChatFileName = oldChatUri?.substringAfterLast("%2F")?.substringAfterLast("/")?.substringAfterLast("%3A")?.let { DownloadPlaylists.percentDecode(it) }
                    val newChatFileUri = oldChatFileName?.let { path + File.separator + it }
                    if (oldChatUri != null && newChatFileUri != null) {
                        FileOutputStream(newChatFileUri).use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldChatUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    offlineVideosRepository.update(video.apply {
                        thumbnail.let {
                            if (it == null || it == url || !File(it).exists()) {
                                thumbnail = newVideoDirectoryUri + File.separator + oldPlaylist.segments.getOrNull(
                                    max(0, (oldPlaylist.segments.size / 2) - 1)
                                )?.uri?.let { DownloadPlaylists.percentDecode(DownloadPlaylists.basename(it)) }
                            }
                        }
                        url = newPlaylistFileUri
                        chatUrl = newChatFileUri
                    })
                    if (playlists.isNotEmpty()) {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, videoUrl.toUri())
                        } catch (e: Exception) {

                        }
                    } else {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, oldVideoDirectoryUri.toUri())
                        } catch (e: Exception) {

                        }
                    }
                    if (oldChatUri != null) {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, oldChatUri.toUri())
                        } catch (e: Exception) {

                        }
                    }
                } else {
                    val oldFileName = DownloadPlaylists.percentDecode(videoUrl.substringAfterLast("%2F").substringAfterLast("/").substringAfterLast("%3A"))
                    val newFileUri = path + File.separator + oldFileName
                    FileOutputStream(newFileUri).use { outputStream ->
                        applicationContext.contentResolver.openInputStream(videoUrl.toUri())!!.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    val oldChatUri = video.chatUrl
                    val oldChatFileName = oldChatUri?.substringAfterLast("%2F")?.substringAfterLast("/")?.substringAfterLast("%3A")?.let { DownloadPlaylists.percentDecode(it) }
                    val newChatFileUri = oldChatFileName?.let { path + File.separator + it }
                    if (oldChatUri != null && newChatFileUri != null) {
                        FileOutputStream(newChatFileUri).use { outputStream ->
                            applicationContext.contentResolver.openInputStream(oldChatUri.toUri())!!.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    offlineVideosRepository.update(video.apply {
                        thumbnail.let {
                            if (it == null || it == url || !File(it).exists()) {
                                thumbnail = newFileUri
                            }
                        }
                        url = newFileUri
                        chatUrl = newChatFileUri
                    })
                    try {
                        DocumentsContract.deleteDocument(applicationContext.contentResolver, videoUrl.toUri())
                    } catch (e: Exception) {

                    }
                    if (oldChatUri != null) {
                        try {
                            DocumentsContract.deleteDocument(applicationContext.contentResolver, oldChatUri.toUri())
                        } catch (e: Exception) {

                        }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    offlineVideosRepository.update(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun updateChatUrl(newUri: Uri, video: OfflineVideo) {
        if (!videosInUse.contains(video)) {
            viewModelScope.launch(Dispatchers.IO) {
                val metadata = try {
                    applicationContext.contentResolver.openInputStream(newUri)?.bufferedReader()?.use { it.readText() }
                        ?.let { parseVideoMetadataFromChatJson(it) }
                } catch (e: Exception) {
                    null
                }
                offlineVideosRepository.update(video.apply {
                    metadata?.title?.let { this.name = it }
                    metadata?.channelId?.let { this.channelId = it }
                    metadata?.channelLogin?.let { this.channelLogin = it }
                    metadata?.channelName?.let { this.channelName = it }
                    metadata?.gameId?.let { this.gameId = it }
                    metadata?.gameSlug?.let { this.gameSlug = it }
                    metadata?.gameName?.let { this.gameName = it }
                    metadata?.uploadDate?.let { this.uploadDate = it }
                    metadata?.id?.let { this.videoId = it }
                    chatUrl = newUri.toString()
                })
            }
        }
    }

    fun delete(video: OfflineVideo, keepFiles: Boolean) {
        val videoUrl = video.url
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                offlineVideosRepository.update(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_DELETING
                })
                if (videoUrl != null && !keepFiles) {
                    if (videoUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                        if (videoUrl.endsWith(".m3u8")) {
                            val videoDirectoryUri = videoUrl.substringBeforeLast("%2F")
                            val playlist = try {
                                applicationContext.contentResolver.openInputStream(videoUrl.toUri())!!.use {
                                    PlaylistUtils.parseMediaPlaylist(it)
                                }
                            } catch (e: Exception) {
                                null
                            }
                            val tracksToDelete = playlist?.segments?.toMutableSet() ?: mutableSetOf()
                            val playlists = offlineVideosRepository.getPlaylists().mapNotNull { video ->
                                video.url?.takeIf {
                                    it.toUri().scheme == ContentResolver.SCHEME_CONTENT
                                            && it.substringBeforeLast("%2F") == videoDirectoryUri
                                            && it != videoUrl
                                }
                            }
                            playlists.forEach { uri ->
                                try {
                                    val p = applicationContext.contentResolver.openInputStream(uri.toUri())!!.use {
                                        PlaylistUtils.parseMediaPlaylist(it)
                                    }
                                    tracksToDelete.removeAll(p.segments.toSet())
                                } catch (e: Exception) {

                                }
                            }
                            offlineVideosRepository.update(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            tracksToDelete.forEach {
                                try {
                                    DocumentsContract.deleteDocument(applicationContext.contentResolver, it.uri.toUri())
                                } catch (e: Exception) {

                                }
                                offlineVideosRepository.update(video.apply {
                                    progress += 1
                                })
                            }
                            try {
                                DocumentsContract.deleteDocument(applicationContext.contentResolver, videoUrl.toUri())
                            } catch (e: Exception) {

                            }
                            if (playlists.isEmpty()) {
                                try {
                                    DocumentsContract.deleteDocument(applicationContext.contentResolver, videoDirectoryUri.toUri())
                                } catch (e: Exception) {

                                }
                            }
                        } else {
                            try {
                                DocumentsContract.deleteDocument(applicationContext.contentResolver, videoUrl.toUri())
                            } catch (e: Exception) {

                            }
                        }
                        video.chatUrl?.let {
                            try {
                                DocumentsContract.deleteDocument(applicationContext.contentResolver, it.toUri())
                            } catch (e: Exception) {

                            }
                        }
                    } else {
                        val playlistFile = File(videoUrl)
                        if (!playlistFile.exists()) {
                            return@launch
                        }
                        if (videoUrl.endsWith(".m3u8")) {
                            val directory = playlistFile.parentFile
                            if (directory != null) {
                                val playlists = directory.listFiles { it.extension == "m3u8" && it != playlistFile }
                                if (playlists != null) {
                                    val playlist = PlaylistUtils.parseMediaPlaylist(playlistFile.inputStream())
                                    val tracksToDelete = playlist.segments.toMutableSet()
                                    playlists.forEach {
                                        val p = PlaylistUtils.parseMediaPlaylist(it.inputStream())
                                        tracksToDelete.removeAll(p.segments.toSet())
                                    }
                                    offlineVideosRepository.update(video.apply {
                                        maxProgress = tracksToDelete.count()
                                    })
                                    tracksToDelete.forEach {
                                        File(it.uri).delete()
                                        offlineVideosRepository.update(video.apply {
                                            progress += 1
                                        })
                                    }
                                    playlistFile.delete()
                                    if (playlists.isEmpty()) {
                                        directory.deleteRecursively()
                                    }
                                }
                            }
                        } else {
                            playlistFile.delete()
                        }
                        video.chatUrl?.let { File(it).delete() }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    offlineVideosRepository.delete(video)
                }
            }
        }
    }

    companion object {
        val DownloadsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                DownloadsViewModel(application.applicationContext, xtraModule.offlineVideosRepository)
            }
        }
    }
}