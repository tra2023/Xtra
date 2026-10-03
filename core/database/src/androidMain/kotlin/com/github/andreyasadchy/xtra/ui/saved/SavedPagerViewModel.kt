package com.github.andreyasadchy.xtra.ui.saved

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.util.chat.parseVideoMetadataFromChatJson
import com.github.andreyasadchy.xtra.util.m3u8.DownloadPlaylists
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import com.github.andreyasadchy.xtra.util.m3u8.parseMediaPlaylist
import com.github.andreyasadchy.xtra.util.m3u8.writeMediaPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Import of already-downloaded files into the offline library. Lives in `:core:database`'s Android
 * source set because enumerating a directory tree needs `DocumentsContract` against a
 * `ContentResolver`; the Android host supplies the resolver from its Context.
 */
class SavedPagerViewModel(
    private val contentResolver: ContentResolver,
    private val offlineVideosRepository: OfflineVideosRepository,
) : ViewModel() {

    private fun readText(uri: String): String? =
        try {
            contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

    fun saveFolders(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(url))
            val directoryUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(url), documentId)
            val directoryUris = mutableListOf<Uri>()
            val chatFiles = mutableMapOf<String, String>()
            contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ), null, null, null
            ).use { cursor ->
                while (cursor?.moveToNext() == true) {
                    val documentId = cursor.getString(0)
                    val mimeType = cursor.getString(1)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val directoryUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
                        directoryUris.add(directoryUri)
                    } else {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                        if (documentUri.toString().endsWith(".json")) {
                            val fileName = documentUri.toString().substringAfterLast("%2F").substringAfterLast("%3A").removeSuffix(".json").removeSuffix("_chat")
                            chatFiles[fileName] = documentUri.toString()
                        }
                    }
                }
            }
            val playlistFileUris = mutableListOf<Uri>()
            directoryUris.forEach { uri ->
                contentResolver.query(
                    uri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null
                ).use { cursor ->
                    while (cursor?.moveToNext() == true) {
                        val documentId = cursor.getString(0)
                        val mimeType = cursor.getString(1)
                        if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                            if (documentUri.toString().endsWith(".m3u8")) {
                                playlistFileUris.add(documentUri)
                            }
                        }
                    }
                }
            }
            playlistFileUris.forEach { uri ->
                val existingVideo = offlineVideosRepository.getByUrl(uri.toString())
                if (existingVideo == null) {
                    val videoDirectoryUri = uri.toString().substringBeforeLast("%2F")
                    val videoDirectoryName = videoDirectoryUri.substringAfterLast("%2F").substringAfterLast("%3A")
                    val playlist = contentResolver.openInputStream(uri)!!.use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                    val totalDuration = DownloadPlaylists.totalDurationMs(playlist.segments)
                    val mapUri = { uri: String -> videoDirectoryUri + "%2F" + DownloadPlaylists.basename(uri) }
                    val segments = DownloadPlaylists.remapSegments(playlist.segments, mapUri)
                    contentResolver.openOutputStream(uri)!!.use {
                        PlaylistUtils.writeMediaPlaylist(playlist.copy(initSegmentUri = playlist.initSegmentUri?.let(mapUri), segments = segments), it)
                    }
                    val chatFileUri = chatFiles[videoDirectoryName + uri.toString().substringAfterLast("%2F").removeSuffix(".m3u8")]
                    val metadata = chatFileUri?.let { chatUri ->
                        readText(chatUri)?.let { parseVideoMetadataFromChatJson(it) }
                    }
                    offlineVideosRepository.save(OfflineVideo(
                        url = uri.toString(),
                        name = if (!metadata?.title.isNullOrBlank()) metadata.title else Uri.decode(videoDirectoryName),
                        channelId = metadata?.channelId,
                        channelLogin = metadata?.channelLogin,
                        channelName = metadata?.channelName,
                        thumbnail = segments.getOrNull(max(0, (segments.size / 2) - 1))?.uri,
                        gameId = metadata?.gameId,
                        gameSlug = metadata?.gameSlug,
                        gameName = metadata?.gameName,
                        duration = totalDuration,
                        uploadDate = metadata?.uploadDate,
                        progress = 100,
                        maxProgress = 100,
                        status = OfflineVideo.STATUS_DOWNLOADED,
                        videoId = metadata?.id,
                        chatUrl = chatFileUri
                    ))
                }
            }
        }
    }

    fun saveVideos(list: List<String>) {
        viewModelScope.launch {
            val chatFiles = mutableMapOf<String, String>()
            list.filter { it.endsWith(".json") }.forEach { url ->
                val fileName = url.substringAfterLast("%2F").substringAfterLast("%3A").removeSuffix(".json").removeSuffix("_chat")
                chatFiles[fileName] = url
            }
            list.filter { !it.endsWith(".json") }.forEach { url ->
                val existingVideo = offlineVideosRepository.getByUrl(url)
                if (existingVideo == null) {
                    val fileName = url.substringAfterLast("%2F").substringAfterLast("%3A").removeSuffix(".mp4").removeSuffix(".ts")
                    val chatFile = chatFiles[fileName]
                    val metadata = chatFile?.let { uri -> readText(uri)?.let { parseVideoMetadataFromChatJson(it) } }
                    offlineVideosRepository.save(
                        OfflineVideo(
                            url = url,
                            name = if (!metadata?.title.isNullOrBlank()) metadata.title else Uri.decode(fileName),
                            channelId = metadata?.channelId,
                            channelLogin = metadata?.channelLogin,
                            channelName = metadata?.channelName,
                            thumbnail = url,
                            gameId = metadata?.gameId,
                            gameSlug = metadata?.gameSlug,
                            gameName = metadata?.gameName,
                            uploadDate = metadata?.uploadDate,
                            progress = 100,
                            maxProgress = 100,
                            status = OfflineVideo.STATUS_DOWNLOADED,
                            videoId = metadata?.id,
                            chatUrl = chatFile
                        )
                    )
                }
            }
        }
    }
}
