package com.github.andreyasadchy.xtra.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.LocalGameFollow
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalGameFollowsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
class GamePagerViewModel(
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val localGameFollowsRepository: LocalGameFollowsRepository,
    private val xtraHttpClient: XtraHttpClient,
    private val gameId: String?,
    private val gameSlug: String?,
    private val gameName: String?,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private var updatedLocalGame = false

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    fun loadGame(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_game.value == null) {
            viewModelScope.launch {
                _game.value = try {
                    val response = graphQLRepository.loadQueryGame(
                        headers = gqlHeaders,
                        id = gameId,
                        slug = gameSlug.takeIf { gameId.isNullOrBlank() },
                        name = gameName.takeIf { gameId.isNullOrBlank() && gameSlug.isNullOrBlank() },
                    )
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.game?.let {
                        Game(
                            id = it.id,
                            slug = it.slug,
                            name = it.displayName,
                            boxArtURL = it.boxArtURL,
                            viewerCount = it.viewersCount,
                            broadcasterCount = it.broadcastersCount,
                            followerCount = it.followersCount,
                            tags = it.tags?.map { tag ->
                                Tag(
                                    id = tag.id,
                                    name = tag.localizedName
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getGames(
                                headers = helixHeaders,
                                ids = gameId?.let { listOf(it) },
                                names = if (gameId.isNullOrBlank()) gameName?.let { listOf(it) } else null
                            ).data.firstOrNull()?.let {
                                Game(
                                    id = it.id,
                                    name = it.name,
                                    boxArtURL = it.boxArtURL
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun isFollowingGame(gameId: String?, setting: Int, gqlHeaders: Map<String, String>) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!gameId.isNullOrBlank()) {
                        _isFollowing.value = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            graphQLRepository.loadQueryFollowingGame(
                                headers = gqlHeaders,
                                id = gameId,
                            ).data?.game?.self?.follow?.followedAt != null
                        } else {
                            localGameFollowsRepository.getById(gameId) != null
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowGame(gameId: String?, gameSlug: String?, gameName: String?, setting: Int, filesDir: String, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!gameId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val errorMessage = graphQLRepository.loadFollowGame(gqlHeaders, gameId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("follow")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(true, errorMessage)
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, null)
                        }
                    } else {
                        File(filesDir, "box_art").mkdir()
                        val path = filesDir + File.separator + "box_art" + File.separator + gameId
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                try {
                                    graphQLRepository.loadQueryGameBoxArt(gqlHeaders, gameId).data!!.game?.boxArtURL
                                } catch (e: Exception) {
                                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                                        helixRepository.getGames(
                                            headers = helixHeaders,
                                            ids = listOf(gameId)
                                        ).data.firstOrNull()?.boxArtURL
                                    } else null
                                }.takeIf { !it.isNullOrBlank() }?.let { TwitchImageUrls.getGameBoxArt(it) }?.let { url ->
                                    xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        localGameFollowsRepository.save(LocalGameFollow(gameId, gameSlug, gameName, path))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowGame(gameId: String?, setting: Int, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!gameId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val errorMessage = graphQLRepository.loadUnfollowGame(gqlHeaders, gameId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("unfollow")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(false, errorMessage)
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, null)
                        }
                    } else {
                        localGameFollowsRepository.getById(gameId)?.let { localGameFollowsRepository.delete(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateLocalGame(filesDir: String, gameId: String?, gameName: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        if (!updatedLocalGame) {
            updatedLocalGame = true
            if (!gameId.isNullOrBlank()) {
                viewModelScope.launch {
                    File(filesDir, "box_art").mkdir()
                    val path = filesDir + File.separator + "box_art" + File.separator + gameId
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            try {
                                graphQLRepository.loadQueryGameBoxArt(gqlHeaders, gameId).data!!.game?.boxArtURL
                            } catch (e: Exception) {
                                if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                                    helixRepository.getGames(
                                        headers = helixHeaders,
                                        ids = listOf(gameId)
                                    ).data.firstOrNull()?.boxArtURL
                                } else null
                            }.takeIf { !it.isNullOrBlank() }?.let { TwitchImageUrls.getGameBoxArt(it) }?.let { url ->
                                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                            }
                        } catch (e: Exception) {

                        }
                    }
                    localGameFollowsRepository.getById(gameId)?.let {
                        localGameFollowsRepository.update(it.apply {
                            this.gameName = gameName
                            boxArt = path
                        })
                    }
                }
            }
        }
    }

}
