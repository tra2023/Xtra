package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.ui.common.LocalXtraStrings
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Shared game banner content for the game pager screens: artwork, counts and
 * tags. The collapsing container lives with the caller (see `CollapsingBanner`);
 * this is just the content. Preferences come from [LocalXtraSettings] and the
 * counts/plural labels from [LocalXtraStrings], so this works on any platform.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameBannerContent(
    game: Game?,
    fallbackName: String?,
    fallbackArt: String?,
    onTagClick: (Tag) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val name = game?.name ?: fallbackName
    val art = game?.boxArt ?: fallbackArt
    val viewerCount = game?.viewerCount
    val broadcasterCount = game?.broadcasterCount?.takeIf { settings.getBoolean(C.UI_BROADCASTERS_COUNT, true) }
    val followerCount = game?.followerCount
    val tags = game?.tags.orEmpty().takeIf { settings.getBoolean(C.UI_TAGS, true) }.orEmpty()
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
                        text = strings.viewers(viewerCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (broadcasterCount != null) {
                    Text(
                        text = strings.broadcasters(broadcasterCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (followerCount != null) {
                    Text(
                        text = strings.followers(followerCount),
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
