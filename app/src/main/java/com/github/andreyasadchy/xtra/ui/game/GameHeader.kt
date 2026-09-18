package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Shared game banner content for the game pager screens: artwork, counts and
 * tags. The collapsing container lives with the caller (see
 * CollapsingBanner); this is just the content.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameBannerContent(
    game: Game?,
    fallbackName: String?,
    fallbackArt: String?,
    onTagClick: (Tag) -> Unit,
) {
    val context = LocalContext.current
    val name = game?.name ?: fallbackName
    val art = game?.boxArt ?: fallbackArt
    val viewerCount = game?.viewerCount
    val broadcasterCount = game?.broadcasterCount?.takeIf { context.prefs().getBoolean(C.UI_BROADCASTERS_COUNT, true) }
    val followerCount = game?.followerCount
    val tags = game?.tags.orEmpty().takeIf { context.prefs().getBoolean(C.UI_TAGS, true) }.orEmpty()
    val truncate = context.prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (art != null) {
                XtraAsyncImage(
                    model = art,
                    contentDescription = name,
                    modifier = Modifier.size(width = 76.dp, height = 97.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                if (!name.isNullOrBlank()) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium)
                }
                if (viewerCount != null) {
                    Text(
                        text = pluralStringResource(R.plurals.viewers, viewerCount, TwitchApiHelper.formatCount(viewerCount, truncate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (broadcasterCount != null) {
                    Text(
                        text = pluralStringResource(R.plurals.broadcasters, broadcasterCount, TwitchApiHelper.formatCount(broadcasterCount, truncate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (followerCount != null) {
                    Text(
                        text = pluralStringResource(R.plurals.followers, followerCount, TwitchApiHelper.formatCount(followerCount, truncate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 7.dp)) {
                for (tag in tags) {
                    val tagName = tag.name ?: continue
                    Text(
                        text = tagName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .then(if (tag.id != null) Modifier.clickable { onTagClick(tag) } else Modifier),
                    )
                }
            }
        }
    }
}

/**
 * Shared sort row for the game pager screens, mirroring sort_bar.xml: sort
 * and filter texts on the left, sort affordance on the right.
 */
@Composable
fun GameSortRow(
    sortText: CharSequence?,
    filtersText: CharSequence?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            if (sortText != null) {
                Text(text = sortText.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            if (filtersText != null) {
                Text(text = filtersText.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(painterResource(R.drawable.baseline_sort_black_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(R.string.sort),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
