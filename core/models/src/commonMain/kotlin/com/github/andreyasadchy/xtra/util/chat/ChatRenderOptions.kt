package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessageContent
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

    /**
     * Formatted payloads keyed by [com.github.andreyasadchy.xtra.model.chat.ChatMessage.key]. Lets
     * a row that scrolled out and back in reuse its formatted text instead of re-running the
     * formatter. Cleared whenever the emote collections change and trimmed with the message window.
     */
    val messageContents = HashMap<Long, ChatMessageContent>()
}

/**
 * Everything [ChatMessageFormatter] needs besides the message itself: the loaded emote/badge/paint
 * collections, the chat display preferences and the shared [cache].
 *
 * Replaces `ChatAdapterUtils.prepareChatMessage`'s 40 parameters. [generation] must be bumped
 * whenever one of the collections or preferences changes, because the formatter result is cached
 * per message and generation.
 */
data class ChatRenderOptions(
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
) {
    /** First-wins name index, matching the `List.find { it.name == value }` it replaces. */
    private fun <T> List<T>.indexFirstByName(name: (T) -> String?): Map<String, T> {
        val map = HashMap<String, T>(size)
        for (item in this) {
            val key = name(item) ?: continue
            map.putIfAbsent(key, item)
        }
        return map
    }

    val thirdPartyEmotesByName: Map<String, Emote> by lazy(LazyThreadSafetyMode.NONE) {
        thirdPartyEmotes.indexFirstByName { it.name }
    }

    val localTwitchEmotesById: Map<String, TwitchEmote> by lazy(LazyThreadSafetyMode.NONE) {
        localTwitchEmotes.indexFirstByName { it.id }
    }

    val stvUsersById: Map<String, STVUser> by lazy(LazyThreadSafetyMode.NONE) {
        stvUsers.indexFirstByName { it.userId }
    }

    val stvBadgesById: Map<String, STVBadge> by lazy(LazyThreadSafetyMode.NONE) {
        stvBadges.indexFirstByName { it.id }
    }

    val namePaintsById: Map<String, NamePaint> by lazy(LazyThreadSafetyMode.NONE) {
        namePaints.indexFirstByName { it.id }
    }

    val channelBadgesByKey: Map<String, TwitchBadge> by lazy(LazyThreadSafetyMode.NONE) {
        badgeIndex(channelBadges)
    }

    val globalBadgesByKey: Map<String, TwitchBadge> by lazy(LazyThreadSafetyMode.NONE) {
        badgeIndex(globalBadges)
    }

    val cheerEmotesByName: Map<String, List<CheerEmote>> by lazy(LazyThreadSafetyMode.NONE) {
        val map = HashMap<String, MutableList<CheerEmote>>()
        for (emote in cheerEmotes) {
            map.getOrPut(emote.name.lowercase()) { ArrayList() }.add(emote)
        }
        map
    }

    val personalEmotesByName: Map<String, Map<String, Emote>> by lazy(LazyThreadSafetyMode.NONE) {
        personalEmoteSets.mapValues { (_, list) -> list.indexFirstByName { it.name } }
    }

    private fun badgeIndex(badges: List<TwitchBadge>): Map<String, TwitchBadge> {
        val map = HashMap<String, TwitchBadge>(badges.size)
        for (badge in badges) {
            map.putIfAbsent(badgeKey(badge.setId, badge.version), badge)
        }
        return map
    }

    companion object {
        fun badgeKey(setId: String?, version: String?): String = "$setId\u0000$version"
    }
}
