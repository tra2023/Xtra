package com.github.andreyasadchy.xtra.model.chat

/**
 * Payload of the old `imageClickListener`: everything the clicked-emote dialog needs. Text is
 * kept as data (instead of a lambda) so a rendered message can be cached and re-used across
 * recompositions.
 */
data class ChatImageClick(
    val name: String? = null,
    val format: String? = null,
    val isAnimated: Boolean? = null,
    val source: Int? = null,
    val thirdParty: Boolean? = null,
    val emoteId: String? = null,
)

/**
 * One inline image of a chat message: a Twitch/7TV/BTTV/FFZ badge, an emote or a channel point
 * reward icon.
 *
 * Replaces the offset-based [Image] the View renderer used, which tracked `start`/`end`
 * positions inside a `SpannableStringBuilder`. Tokens carry the image itself and the text flow
 * decides where it ends up.
 */
class ChatImage(
    val localData: ByteArray? = null,
    val url1x: String? = null,
    val url2x: String? = null,
    val url3x: String? = null,
    val url4x: String? = null,
    val isBadge: Boolean = false,
    val isAnimated: Boolean = false,
    val thirdParty: Boolean = false,
    val click: ChatImageClick? = null,
) {

    /** Zero-width (overlay) emote drawn on top of this one, if any. */
    var overlay: ChatImage? = null

    /**
     * Image passed to Coil, honoring the chat image quality preference. Locally downloaded
     * emotes win over the remote variants, exactly like the old `ChatAdapterUtils.loadImage`.
     */
    fun dataFor(quality: String?): Any? = localData ?: when (quality) {
        "4" -> url4x ?: url3x ?: url2x ?: url1x
        "3" -> url3x ?: url2x ?: url1x
        "2" -> url2x ?: url1x
        else -> url1x
    }
}

/** A single piece of a rendered chat message. */
sealed interface ChatToken {

    /** Plain text run. [color] is an ARGB value, `null` keeps the theme text color. */
    data class Text(val text: String, val color: Int? = null, val bold: Boolean = false) : ChatToken

    /** Clickable web link, matching the old `URLSpan` + `autoLink="web"` behavior. */
    data class Link(val text: String, val url: String) : ChatToken

    /** Inline badge/emote/reward icon. */
    data class Image(val image: ChatImage) : ChatToken

    /**
     * User name with a 7TV name paint. [paint] can be a gradient, a radial gradient, an image
     * fill (`type == "URL"`) or any combination with [NamePaint.shadows].
     */
    data class PaintedName(
        val text: String,
        val color: Int,
        val bold: Boolean,
        val paint: NamePaint,
    ) : ChatToken
}

/** Row highlight of a chat message, mirroring the old `R.color.chatMessage*` values. */
enum class ChatMessageBackground {
    NONE,
    FIRST,
    REWARD,
    NOTICE,
    MENTION,
}

/** Platform-independent result of formatting one [ChatMessage]. */
class ChatMessageContent(
    val tokens: List<ChatToken>,
    val background: ChatMessageBackground = ChatMessageBackground.NONE,
)

/**
 * Localized strings used by the chat formatter. The platform owns `getString`/`String.format`
 * argument handling, so the templates arrive as already-resolving lambdas.
 */
class ChatMessageStrings(
    /** `First time chat`. */
    val firstChatMsg: String,
    /** `Channel point redemption`. */
    val rewardChatMsg: String,
    /** `Redeemed %s`. */
    val redeemedChatMsg: (String?) -> String,
    /** `%1$s redeemed %2$s`. */
    val redeemedNoMsg: (String?, String?) -> String,
    /** `Replying to %1$s: %2$s`. */
    val replyMessage: (String?, String?) -> String,
    /** Human readable IRC message id (`highlighted-message` -> `Highlighted message`). */
    val messageIdLabel: (String) -> String,
)
