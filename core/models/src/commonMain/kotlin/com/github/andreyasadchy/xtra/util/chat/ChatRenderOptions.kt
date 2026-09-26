package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessageStrings
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.STVBadge
import com.github.andreyasadchy.xtra.model.chat.STVUser
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import kotlin.random.Random

/**
 * Memoization shared by every rendered chat row, replacing the maps the three RecyclerView
 * adapters used to keep. Holds caches only: clearing it never changes what a message renders as,
 * it just re-computes colors and re-reads locally stored emotes.
 *
 * A single instance is shared by the chat list and both message dialogs so a user keeps the same
 * random name color everywhere.
 */
class ChatRenderCache(
    val random: Random = Random.Default,
) {
    val userColors = HashMap<String, Int>()
    val savedColors = HashMap<String, Int>()
    val savedLocalTwitchEmotes = HashMap<String, ByteArray>()
    val savedLocalBadges = HashMap<String, ByteArray>()
    val savedLocalCheerEmotes = HashMap<String, ByteArray>()
    val savedLocalEmotes = HashMap<String, ByteArray>()
}

/**
 * Everything [ChatMessageFormatter] needs besides the message itself: the loaded emote/badge/paint
 * collections, the chat display preferences and the shared [cache].
 *
 * Replaces `ChatAdapterUtils.prepareChatMessage`'s 40 parameters. [generation] must be bumped
 * whenever one of the collections or preferences changes, because the formatter result is cached
 * per message and generation.
 */
class ChatRenderOptions(
    val strings: ChatMessageStrings,
    val localTwitchEmotes: List<TwitchEmote> = emptyList(),
    val thirdPartyEmotes: List<Emote> = emptyList(),
    val globalBadges: List<TwitchBadge> = emptyList(),
    val channelBadges: List<TwitchBadge> = emptyList(),
    val cheerEmotes: List<CheerEmote> = emptyList(),
    val namePaints: List<NamePaint> = emptyList(),
    val stvBadges: List<STVBadge> = emptyList(),
    val personalEmoteSets: Map<String, List<Emote>> = emptyMap(),
    val stvUsers: List<STVUser> = emptyList(),
    val enableTimestamps: Boolean = false,
    val timestampFormat: String? = null,
    val firstMsgVisibility: Int = 0,
    val nameDisplay: String? = null,
    val useRandomColors: Boolean = true,
    val useReadableColors: Boolean = true,
    val isLightTheme: Boolean = false,
    val useBoldNames: Boolean = false,
    val showNamePaints: Boolean = true,
    val showSTVBadges: Boolean = true,
    val showPersonalEmotes: Boolean = true,
    val showSystemMessageEmotes: Boolean = true,
    val enableOverlayEmotes: Boolean = true,
    val loggedInUser: String? = null,
    val chatUrl: String? = null,
    val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)? = null,
    val emoteQuality: String = "4",
    val cache: ChatRenderCache = ChatRenderCache(),
    val generation: Int = 0,
)
