package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatImageClick
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.ChatMessageBackground
import com.github.andreyasadchy.xtra.model.chat.ChatMessageContent
import com.github.andreyasadchy.xtra.model.chat.ChatToken
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.STVUser
import com.github.andreyasadchy.xtra.util.TwitchFormats
import com.github.andreyasadchy.xtra.util.formatGroupedCount
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Platform-independent replacement for `ChatAdapterUtils.prepareChatMessage`.
 *
 * Turns a [ChatMessage] plus the loaded emote/badge/paint collections into a list of
 * [ChatToken]s. The View renderer wrote placeholder characters into a `SpannableStringBuilder`
 * and replaced them with `ImageSpan`s afterwards, which is why it needed all the `builderIndex`
 * bookkeeping; with tokens the logic reduces to walking the words and emitting text, links and
 * images in order.
 *
 * Deliberate differences to the old renderer:
 * - a malformed message (huge bits count, missing emote name) falls back to plain text instead of
 *   leaving a half-processed string behind;
 * - the URL test is a common-code regex instead of `android.util.Patterns.WEB_URL`, which makes it
 *   slightly more permissive.
 */
object ChatMessageFormatter {

    private const val REPLY_COLOR = "#999999"
    private const val DEFAULT_USER_COLOR = -10066329
    private const val RED_HUE_DEGREES = 0f
    private const val GREEN_HUE_DEGREES = 120f
    private const val BLUE_HUE_DEGREES = 240f
    private const val PI_DEGREES = 180f
    private const val TWO_PI_DEGREES = 360f

    /** Same palette as the View renderer, used for the random user name colors. */
    private val twitchColors = intArrayOf(
        -65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096,
        -2987746, -10510688, -14774017, -38476, -7722014, -16711809,
    )

    /**
     * Word level web address test, replacing `android.util.Patterns.WEB_URL`. Covers the shapes
     * that show up in chat: `example.com`, `www.example.com/path`, `https://example.com`,
     * `host:port` and IPv4 addresses.
     */
    private val WEB_URL = Regex(
        "(?i)^(?:(?:https?|ftp)://)?" +
            "(?:(?:[a-z0-9\u00a0-\uffff](?:[a-z0-9\u00a0-\uffff-]{0,61}[a-z0-9\u00a0-\uffff])?\\.)+" +
            "[a-z\u00a0-\uffff]{2,63}" +
            "|(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d))" +
            "(?::\\d+)?(?:/\\S*)?$",
    )

    private class TokenBuilder {
        val tokens = ArrayList<ChatToken>()

        fun text(text: String?, color: Int? = null, bold: Boolean = false) {
            if (!text.isNullOrEmpty()) {
                tokens.add(ChatToken.Text(text, color, bold))
            }
        }

        fun link(text: String, url: String) {
            tokens.add(ChatToken.Link(text, url))
        }

        fun image(image: ChatImage) {
            tokens.add(ChatToken.Image(image))
        }

        fun paintedName(text: String, color: Int, bold: Boolean, paint: NamePaint) {
            tokens.add(ChatToken.PaintedName(text, color, bold, paint))
        }

        fun append(other: TokenBuilder) {
            tokens.addAll(other.tokens)
        }

        /** Drops one trailing space, used when a zero-width emote eats its separator. */
        fun removeTrailingSpace() {
            val last = tokens.lastOrNull()
            if (last is ChatToken.Text && last.color == null && !last.bold && last.text.endsWith(' ')) {
                if (last.text.length == 1) {
                    tokens.removeAt(tokens.lastIndex)
                } else {
                    tokens[tokens.lastIndex] = last.copy(text = last.text.dropLast(1))
                }
            }
        }
    }

    private class TwitchEmoteRef(
        val id: String?,
        val begin: Int,
        val isAnimated: Boolean,
        val format: String?,
        val localData: Pair<Long, Int>?,
    )

    private class EmoteResult(val builder: TokenBuilder, val wasMentioned: Boolean)

    fun format(message: ChatMessage, options: ChatRenderOptions): ChatMessageContent {
        val builder = TokenBuilder()
        var background = ChatMessageBackground.NONE
        when {
            message.type == ChatMessage.REPLY_MESSAGE -> {
                val reply = message.reply
                val replyUser = displayName(reply?.userName, reply?.userLogin, options.nameDisplay)
                val dim = getSavedColor(REPLY_COLOR, options)
                builder.text(options.strings.replyMessage(replyUser, ""), dim)
                val replyText = reply?.message
                if (replyText != null) {
                    builder.text(replyText, dim)
                    builder.append(formatEmotes(message, replyText, options, clickable = false).builder)
                }
            }
            message.message.isNullOrBlank() && (message.systemMsg != null || message.reward?.title != null) -> {
                val timestamp = timestampText(message, options)
                if (timestamp != null) {
                    builder.text("$timestamp ", getSavedColor(REPLY_COLOR, options))
                }
                val systemMsg = message.systemMsg
                if (systemMsg != null) {
                    builder.text(systemMsg, getSavedColor(REPLY_COLOR, options))
                    if (options.showSystemMessageEmotes) {
                        builder.append(formatEmotes(message, systemMsg, options, clickable = true).builder)
                    }
                } else {
                    val reward = message.reward
                    val rewardTitle = reward?.title
                    if (rewardTitle != null) {
                        val userName = displayName(message.userName, message.userLogin, options.nameDisplay)
                        val string = options.strings.redeemedNoMsg(userName, rewardTitle)
                        builder.text("$string ", getSavedColor(REPLY_COLOR, options))
                        if (options.showSystemMessageEmotes) {
                            builder.append(formatEmotes(message, string, options, clickable = true).builder)
                        }
                        builder.image(rewardImage(reward))
                        builder.text(" ")
                        reward.cost?.let { builder.text(formatGroupedCount(it)) }
                    }
                }
            }
            else -> {
                background = formatUserMessage(builder, message, options)
            }
        }
        return ChatMessageContent(builder.tokens, background)
    }

    private fun formatUserMessage(builder: TokenBuilder, message: ChatMessage, options: ChatRenderOptions): ChatMessageBackground {
        val dim = getSavedColor(REPLY_COLOR, options)
        val systemMsg = message.systemMsg
        if (systemMsg != null) {
            builder.text("$systemMsg\n")
        } else {
            val messageId = message.msgId
            if (messageId != null) {
                builder.text("${options.strings.messageIdLabel(messageId)}\n")
            }
        }
        if (message.isFirst && options.firstMsgVisibility == 0) {
            builder.text("${options.strings.firstChatMsg}\n")
        }
        val reward = message.reward
        val rewardTitle = reward?.title
        if (rewardTitle != null) {
            builder.text("${options.strings.redeemedChatMsg(rewardTitle)} ")
            builder.image(rewardImage(reward))
            builder.text(" ")
            reward.cost?.let { builder.text(formatGroupedCount(it)) }
            builder.text("\n")
        } else if (reward?.id != null && options.firstMsgVisibility == 0) {
            builder.text("${options.strings.rewardChatMsg}\n")
        }
        val timestamp = timestampText(message, options)
        if (timestamp != null) {
            builder.text("$timestamp ", dim)
        }
        message.badges?.forEach { chatBadge ->
            val badge = options.channelBadges.find { it.setId == chatBadge.setId && it.version == chatBadge.version }
                ?: options.globalBadges.find { it.setId == chatBadge.setId && it.version == chatBadge.version }
            if (badge != null) {
                builder.image(
                    ChatImage(
                        localData = badge.localData?.let { getLocalEmoteData(badge.setId + badge.version, it, options.cache.savedLocalBadges, options) },
                        url1x = badge.url1x,
                        url2x = badge.url2x,
                        url3x = badge.url3x,
                        url4x = badge.url4x,
                        isBadge = true,
                        click = ChatImageClick(name = badge.title),
                    )
                )
                builder.text(" ")
            }
        }
        val stvUser = if ((options.showSTVBadges || options.showNamePaints || options.showPersonalEmotes) && !message.userId.isNullOrBlank()) {
            options.stvUsers.find { it.userId == message.userId }
        } else null
        if (options.showSTVBadges && !message.userId.isNullOrBlank()) {
            val badge = stvUser?.badgeId?.let { badgeId -> options.stvBadges.find { it.id == badgeId } }
            if (badge != null) {
                builder.image(
                    ChatImage(
                        url1x = badge.url1x,
                        url2x = badge.url2x,
                        url3x = badge.url3x,
                        url4x = badge.url4x,
                        isBadge = true,
                        isAnimated = true,
                        thirdParty = true,
                        click = ChatImageClick(name = badge.name, format = badge.format, isAnimated = true, thirdParty = true),
                    )
                )
                builder.text(" ")
            }
        }
        var userColor: Int? = null
        if (!message.userName.isNullOrBlank()) {
            val userName = displayName(message.userName, message.userLogin, options.nameDisplay).orEmpty()
            val color = usernameColor(message, options)
            userColor = color
            val paint = if (options.showNamePaints && !message.userId.isNullOrBlank()) {
                stvUser?.paintId?.let { paintId -> options.namePaints.find { it.id == paintId } }
            } else null
            if (paint != null && supportsPaint(paint)) {
                builder.paintedName(userName, color, options.useBoldNames, paint)
            } else {
                builder.text(userName, color, options.useBoldNames)
            }
            builder.text(if (message.isAction) " " else ": ")
        }
        var wasMentioned = false
        val messageText = message.message
        if (messageText != null) {
            val emotes = formatEmotes(message, messageText, options, clickable = true, plainColor = if (message.isAction) userColor else null, stvUser = stvUser)
            builder.append(emotes.builder)
            wasMentioned = emotes.wasMentioned
        }
        return when {
            message.isFirst && options.firstMsgVisibility < 2 -> ChatMessageBackground.FIRST
            message.reward?.id != null && options.firstMsgVisibility < 2 -> ChatMessageBackground.REWARD
            message.systemMsg != null || message.msgId != null -> ChatMessageBackground.NOTICE
            wasMentioned -> ChatMessageBackground.MENTION
            else -> ChatMessageBackground.NONE
        }
    }

    /**
     * Formats the message body: emotes, bits/cheer emotes, mentions and links, in the same order
     * and with the same matching rules as the old `ChatAdapterUtils.prepareEmotes`.
     *
     * [plainColor] colors plain words, which is how Twitch actions (`/me`) keep the user color on
     * the whole line. [stvUser] enables the personal 7TV emote set and is only passed for the main
     * message body, exactly like before.
     */
    private fun formatEmotes(
        message: ChatMessage,
        text: String,
        options: ChatRenderOptions,
        clickable: Boolean,
        plainColor: Int? = null,
        stvUser: STVUser? = null,
    ): EmoteResult {
        val builder = TokenBuilder()
        var wasMentioned = false
        try {
            val twitchEmotes = message.emotes?.map { emote ->
                val local = emote.id?.let { id -> options.localTwitchEmotes.find { it.id == id } }
                TwitchEmoteRef(
                    id = emote.id,
                    begin = codePointOffset(text, emote.begin),
                    isAnimated = local?.isAnimated ?: true,
                    format = local?.format ?: "gif",
                    localData = local?.localData,
                )
            }?.sortedBy { it.begin }?.toMutableList() ?: mutableListOf()
            val personalEmotes = if (options.showPersonalEmotes) {
                stvUser?.emoteSetId?.let { setId -> options.personalEmoteSets[setId] }
            } else null
            var previousImage: ChatImage? = null
            var index = 0
            while (index < text.length || index == 0) {
                val spaceIndex = text.indexOf(' ', index)
                val wordEnd = if (spaceIndex == -1) text.length else spaceIndex
                val value = text.substring(index, wordEnd)
                val separator = wordEnd < text.length
                val wordStart = index
                index = wordEnd + 1
                var handled = false
                if (message.bits != null) {
                    val bitsCount = value.takeLastWhile { it.isDigit() }
                    val bitsName = value.substringBeforeLast(bitsCount)
                    val cheerEmote = if (bitsCount.isEmpty()) null else {
                        val bits = bitsCount.toIntOrNull()
                        if (bits == null) null else options.cheerEmotes.findLast { it.name.equals(bitsName, true) && it.minBits <= bits }
                    }
                    if (cheerEmote != null) {
                        builder.image(
                            ChatImage(
                                localData = cheerEmote.localData?.let { getLocalEmoteData(cheerEmote.name + cheerEmote.minBits, it, options.cache.savedLocalCheerEmotes, options) },
                                url1x = cheerEmote.url1x,
                                url2x = cheerEmote.url2x,
                                url3x = cheerEmote.url3x,
                                url4x = cheerEmote.url4x,
                                isAnimated = cheerEmote.isAnimated,
                                click = if (clickable) ChatImageClick(name = value, format = cheerEmote.format, isAnimated = cheerEmote.isAnimated) else null,
                            )
                        )
                        builder.text(bitsCount, cheerEmote.color?.let { getSavedColor(it, options) } ?: plainColor)
                        if (separator) {
                            builder.text(" ")
                        }
                        previousImage = null
                        handled = true
                    }
                }
                if (!handled) {
                    val emote = personalEmotes?.find { it.name == value }
                        ?: options.thirdPartyEmotes.find { it.name == value }
                    if (emote != null) {
                        val overlay = previousImage
                        val image = ChatImage(
                            localData = emote.localData?.let { getLocalEmoteData(emote.name.orEmpty(), it, options.cache.savedLocalEmotes, options) },
                            url1x = emote.url1x,
                            url2x = emote.url2x,
                            url3x = emote.url3x,
                            url4x = emote.url4x,
                            isAnimated = emote.isAnimated,
                            thirdParty = emote.thirdParty,
                            click = if (clickable) {
                                ChatImageClick(name = emote.name, format = emote.format, isAnimated = emote.isAnimated, source = emote.source, thirdParty = emote.thirdParty)
                            } else null,
                        )
                        if (emote.isOverlayEmote && options.enableOverlayEmotes && overlay != null) {
                            overlay.overlay = image
                            previousImage = image
                            builder.removeTrailingSpace()
                        } else {
                            builder.image(image)
                            if (separator) {
                                builder.text(" ")
                            }
                            previousImage = image
                        }
                        handled = true
                    }
                }
                if (!handled) {
                    while (twitchEmotes.isNotEmpty() && twitchEmotes.first().begin < wordStart) {
                        twitchEmotes.removeAt(0)
                    }
                    val twitchEmote = twitchEmotes.firstOrNull()?.takeIf { it.begin == wordStart }
                    if (twitchEmote != null) {
                        twitchEmotes.removeAt(0)
                        val id = twitchEmote.id
                        builder.image(
                            ChatImage(
                                localData = id?.let { emoteId -> twitchEmote.localData?.let { data -> getLocalEmoteData(emoteId, data, options.cache.savedLocalTwitchEmotes, options) } },
                                url1x = twitchEmoteUrl(id, "1.0"),
                                url2x = twitchEmoteUrl(id, "2.0"),
                                url3x = twitchEmoteUrl(id, "2.0"),
                                url4x = twitchEmoteUrl(id, "3.0"),
                                isAnimated = twitchEmote.isAnimated,
                                click = if (clickable) {
                                    ChatImageClick(name = value, format = twitchEmote.format, isAnimated = twitchEmote.isAnimated, emoteId = id)
                                } else null,
                            )
                        )
                        if (separator) {
                            builder.text(" ")
                        }
                        previousImage = null
                        handled = true
                    }
                }
                if (!handled && WEB_URL.matches(value)) {
                    builder.link(value, if (value.startsWith("http")) value else "https://$value")
                    if (separator) {
                        builder.text(" ")
                    }
                    previousImage = null
                    handled = true
                }
                if (!handled) {
                    builder.text(value, plainColor, value.startsWith('@') && options.useBoldNames)
                    if (separator) {
                        builder.text(" ")
                    }
                    if (!wasMentioned &&
                        !options.loggedInUser.isNullOrBlank() &&
                        value.contains(options.loggedInUser, true) &&
                        message.userId != null &&
                        message.userLogin != options.loggedInUser
                    ) {
                        wasMentioned = true
                    }
                    previousImage = null
                }
            }
        } catch (_: Exception) {
            // Fall back to the unformatted text rather than dropping parts of the message.
            return EmoteResult(TokenBuilder().apply { text(text, plainColor) }, false)
        }
        return EmoteResult(builder, wasMentioned)
    }

    private fun rewardImage(reward: ChannelPointReward?): ChatImage = ChatImage(
        url1x = reward?.url1x,
        url2x = reward?.url2x,
        url3x = reward?.url4x,
        url4x = reward?.url4x,
        isBadge = true,
    )

    private fun timestampText(message: ChatMessage, options: ChatRenderOptions): String? {
        val timestamp = message.timestamp
        return if (timestamp != null && options.enableTimestamps) {
            TwitchFormats.formatTimestampMillis(timestamp, options.timestampFormat)
        } else null
    }

    /** `name(login)` / `name` / `login`, following the "name display" preference. */
    private fun displayName(userName: String?, userLogin: String?, nameDisplay: String?): String? {
        return if (userLogin != null && !userLogin.equals(userName, true)) {
            when (nameDisplay) {
                "0" -> "$userName($userLogin)"
                "1" -> userName
                else -> userLogin
            }
        } else {
            userName
        }
    }

    /** Name paints only render as a painted name when they carry something to draw. */
    private fun supportsPaint(paint: NamePaint): Boolean {
        return when (paint.type) {
            "URL" -> !paint.imageUrl.isNullOrBlank()
            "LINEAR_GRADIENT", "RADIAL_GRADIENT" -> paint.colors != null && paint.colorPositions != null
            else -> false
        }
    }

    /** The user name color: the message color, a cached random color or a readable variant. */
    private fun usernameColor(message: ChatMessage, options: ChatRenderOptions): Int {
        val messageColor = message.color
        if (messageColor != null) {
            return getSavedColor(messageColor, options)
        }
        val userName = message.userName
        val cache = options.cache
        cache.userColors[userName]?.let { return it }
        val color = if (options.useRandomColors) {
            twitchColors[cache.random.nextInt(twitchColors.size)]
        } else {
            DEFAULT_USER_COLOR
        }.let { newColor ->
            if (options.useReadableColors) {
                adaptUsernameColor(newColor, options.isLightTheme)
            } else {
                newColor
            }
        }
        if (userName != null) {
            cache.userColors[userName] = color
        }
        return color
    }

    private fun twitchEmoteUrl(id: String?, variant: String): String =
        "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/$variant"

    // __FORMAT_HELPERS_B__

    private fun getSavedColor(color: String, options: ChatRenderOptions): Int =
        getSavedColor(color, options.cache.savedColors, options.useReadableColors, options.isLightTheme)

    private fun getSavedColor(color: String, savedColors: HashMap<String, Int>, useReadableColors: Boolean, isLightTheme: Boolean): Int {
        return savedColors[color] ?: (parseColor(color) ?: DEFAULT_USER_COLOR).let { newColor ->
            val adapted = if (useReadableColors) adaptUsernameColor(newColor, isLightTheme) else newColor
            savedColors[color] = adapted
            adapted
        }
    }

    /**
     * Nudges a color towards the readable range of the current theme, ported from
     * `ChatAdapterUtils.adaptUsernameColor` (`ColorUtils.colorToHSL` + `HSLToColor`).
     */
    private fun adaptUsernameColor(color: Int, isLightTheme: Boolean): Int {
        val hsl = colorToHsl(color)
        if (isLightTheme) {
            val luminanceMax = 0.75f -
                maxOf(1f - ((hsl[0] - GREEN_HUE_DEGREES) / 100f).pow(2f), RED_HUE_DEGREES) * 0.4f
            hsl[2] = minOf(hsl[2], luminanceMax)
        } else {
            val distToRed = RED_HUE_DEGREES - hsl[0]
            val distToBlue = BLUE_HUE_DEGREES - hsl[0]
            val normDistanceToRed = distToRed - TWO_PI_DEGREES * floor((distToRed + PI_DEGREES) / TWO_PI_DEGREES)
            val normDistanceToBlue = distToBlue - TWO_PI_DEGREES * floor((distToBlue + PI_DEGREES) / TWO_PI_DEGREES)
            val luminanceMin = 0.3f +
                maxOf((1f - (normDistanceToBlue / 40f).pow(2f)) * 0.35f, RED_HUE_DEGREES) +
                maxOf((1f - (normDistanceToRed / 40f).pow(2f)) * 0.1f, RED_HUE_DEGREES)
            hsl[2] = maxOf(hsl[2], luminanceMin)
        }
        return hslToColor(hsl)
    }

    /** Returns `[hue 0..360, saturation 0..1, lightness 0..1]`. */
    private fun colorToHsl(color: Int): FloatArray {
        val red = ((color shr 16) and 0xFF) / 255f
        val green = ((color shr 8) and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        val lightness = (max + min) / 2f
        val hue: Float
        val saturation: Float
        if (delta == 0f) {
            hue = 0f
            saturation = 0f
        } else {
            saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
            hue = when (max) {
                red -> (green - blue) / delta + (if (green < blue) 6f else 0f)
                green -> (blue - red) / delta + 2f
                else -> (red - green) / delta + 4f
            } * 60f
        }
        return floatArrayOf(hue, saturation, lightness)
    }

    private fun hslToColor(hsl: FloatArray): Int {
        val hue = ((hsl[0] % TWO_PI_DEGREES) + TWO_PI_DEGREES) % TWO_PI_DEGREES
        val saturation = hsl[1].coerceIn(0f, 1f)
        val lightness = hsl[2].coerceIn(0f, 1f)
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val second = chroma * (1f - abs((hue / 60f) % 2f - 1f))
        val match = lightness - chroma / 2f
        val (red, green, blue) = when {
            hue < 60f -> Triple(chroma, second, 0f)
            hue < 120f -> Triple(second, chroma, 0f)
            hue < 180f -> Triple(0f, chroma, second)
            hue < 240f -> Triple(0f, second, chroma)
            hue < 300f -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        fun channel(value: Float) = ((value + match) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    /** `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`, replacing `String.toColorInt()`. */
    private fun parseColor(value: String): Int? {
        if (!value.startsWith("#")) {
            return null
        }
        val hex = value.substring(1)
        if (hex.isEmpty() || hex.any { it.digitToIntOrNull(16) == null }) {
            return null
        }
        return when (hex.length) {
            3, 4 -> {
                val number = hex.toLongOrNull(16)?.toInt() ?: return null
                val alpha = if (hex.length == 4) (number shr 12) and 0xF else 0xF
                val red = (number shr 8) and 0xF
                val green = (number shr 4) and 0xF
                val blue = number and 0xF
                val expandedAlpha = (alpha shl 4) or alpha
                val expandedRed = (red shl 4) or red
                val expandedGreen = (green shl 4) or green
                val expandedBlue = (blue shl 4) or blue
                (expandedAlpha shl 24) or (expandedRed shl 16) or (expandedGreen shl 8) or expandedBlue
            }
            6 -> {
                val number = hex.toLongOrNull(16)?.toInt() ?: return null
                (0xFF shl 24) or number
            }
            8 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    private fun getLocalEmoteData(name: String, data: Pair<Long, Int>, savedLocalEmotes: HashMap<String, ByteArray>, options: ChatRenderOptions): ByteArray? {
        return savedLocalEmotes[name] ?: options.chatUrl?.let { url ->
            options.getEmoteBytes?.invoke(url, data)?.also {
                if (savedLocalEmotes.size >= 100) {
                    savedLocalEmotes.remove(savedLocalEmotes.keys.first())
                }
                savedLocalEmotes[name] = it
            }
        }
    }

    /**
     * UTF-16 index of the [codePointIndex]-th code point, the counterpart of the
     * `String.offsetByCodePoints` call the Twitch emote offsets rely on.
     */
    private fun codePointOffset(text: String, codePointIndex: Int): Int {
        var index = 0
        var count = 0
        while (index < text.length && count < codePointIndex) {
            index += if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return index
    }
}

