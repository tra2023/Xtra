package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

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
    val context = LocalContext.current
    GameCollectionRow(
        name = game.name,
        image = game.boxArt,
        viewers = context.collectionCount(game.viewerCount, R.plurals.viewers),
        broadcasters = context.collectionCount(
            game.broadcasterCount.takeIf { context.prefs().getBoolean(C.UI_BROADCASTERS_COUNT, true) },
            R.plurals.broadcasters,
        ),
        tags = game.tags.orEmpty().takeIf { context.prefs().getBoolean(C.UI_TAGS, true) }.orEmpty(),
        tagLabel = { it.name.orEmpty() },
        tagEnabled = { it.id != null },
        onTagClick = onTagClick,
        followLabels = followLabels,
        diskCache = diskCache,
        onClick = { onClick(game) },
    )
}
