package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Shared game row for the Compose paging lists: viewer/broadcaster counts,
 * tag list and [GameCollectionRow] styling.
 */
@Composable
fun GameListItem(
    game: Game,
    onTagClick: (Tag) -> Unit,
    onClick: (Game) -> Unit,
    followLabels: List<String> = emptyList(),
    diskCache: Boolean = true,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    GameCollectionRow(
        name = game.name,
        image = game.boxArt,
        viewers = game.viewerCount?.let { strings.viewers(it) },
        broadcasters = game.broadcasterCount
            .takeIf { settings.getBoolean(C.UI_BROADCASTERS_COUNT, true) }
            ?.let { strings.broadcasters(it) },
        tags = game.tags.orEmpty().takeIf { settings.getBoolean(C.UI_TAGS, true) }.orEmpty(),
        tagLabel = { it.name.orEmpty() },
        tagEnabled = { it.id != null },
        onTagClick = onTagClick,
        followLabels = followLabels,
        diskCache = diskCache,
        onClick = { onClick(game) },
    )
}
