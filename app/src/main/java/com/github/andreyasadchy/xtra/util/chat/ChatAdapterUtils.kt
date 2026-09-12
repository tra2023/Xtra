package com.github.andreyasadchy.xtra.util.chat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import coil3.asDrawable
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Image
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.STVBadge
import com.github.andreyasadchy.xtra.model.chat.STVUser
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.ui.view.CenteredImageSpan
import com.github.andreyasadchy.xtra.ui.view.NamePaintImageSpan
import com.github.andreyasadchy.xtra.ui.view.NamePaintSpan
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import java.text.NumberFormat
import java.util.Random
import kotlin.math.floor
import kotlin.math.pow
import androidx.core.graphics.toColorInt

object ChatAdapterUtils {

    private val twitchColors = intArrayOf(-65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096, -2987746, -10510688, -14774017, -38476, -7722014, -16711809)
    private const val RED_HUE_DEGREES = 0f
    private const val GREEN_HUE_DEGREES = 120f
    private const val BLUE_HUE_DEGREES = 240f
    private const val PI_DEGREES = 180f
    private const val TWO_PI_DEGREES = 360f

    fun prepareChatMessage(chatMessage: ChatMessage, context: Context, itemView: View, enableTimestamps: Boolean, timestampFormat: String?, firstMsgVisibility: Int, firstChatMsg: String, redeemedChatMsg: String, redeemedNoMsg: String, rewardChatMsg: String, replyMessage: String, imageClick: ((String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit)?, useRandomColors: Boolean, random: Random, useReadableColors: Boolean, isLightTheme: Boolean, nameDisplay: String?, useBoldNames: Boolean, showNamePaints: Boolean, namePaints: List<NamePaint>, showSTVBadges: Boolean, stvBadges: List<STVBadge>, showGifMessages: Boolean, showPersonalEmotes: Boolean, personalEmoteSets: Map<String, List<Emote>>, stvUsers: List<STVUser>, enableOverlayEmotes: Boolean, showSystemMessageEmotes: Boolean, loggedInUser: String?, chatUrl: String?, getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?, userColors: HashMap<String, Int>, savedColors: HashMap<String, Int>, localTwitchEmotes: List<TwitchEmote>, thirdPartyEmotes: List<Emote>, globalBadges: List<TwitchBadge>, channelBadges: List<TwitchBadge>, cheerEmotes: List<CheerEmote>, savedLocalTwitchEmotes: MutableMap<String, ByteArray>, savedLocalBadges: MutableMap<String, ByteArray>, savedLocalCheerEmotes: MutableMap<String, ByteArray>, savedLocalEmotes: MutableMap<String, ByteArray>): MessageResult {
        val builder = SpannableStringBuilder()
        val images = ArrayList<Image>()
        var imagePaint: NamePaint? = null
        var userName: String? = null
        var userNameStartIndex: Int? = null
        var wasMentioned = false
        var builderIndex = 0
        when {
            chatMessage.type == ChatMessage.REPLY_MESSAGE -> {
                val reply = chatMessage.reply
                val userName = if (reply?.userName != null && reply.userLogin != null && !reply.userLogin.equals(reply.userName, true)) {
                    when (nameDisplay) {
                        "0" -> "${reply.userName}(${reply.userLogin})"
                        "1" -> reply.userName
                        else -> reply.userLogin
                    }
                } else {
                    reply?.userName ?: reply?.userLogin
                }
                val string = replyMessage.format(userName, "")
                builder.append(string)
                builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), 0, string.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                builderIndex += string.length
                val message = chatMessage.reply?.message
                if (message != null) {
                    builder.append(message)
                    builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + message.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    prepareEmotes(chatMessage, message, builder, builderIndex, images, null, useReadableColors, isLightTheme, enableOverlayEmotes, useBoldNames, loggedInUser, chatUrl, getEmoteBytes, savedColors, localTwitchEmotes, showPersonalEmotes, personalEmoteSets, null, thirdPartyEmotes, cheerEmotes, savedLocalTwitchEmotes, savedLocalCheerEmotes, savedLocalEmotes)
                }
                itemView.setBackgroundResource(0)
            }
            chatMessage.message.isNullOrBlank() && (chatMessage.systemMsg != null || chatMessage.reward?.title != null) -> {
                val messageTimestamp = chatMessage.timestamp
                if (messageTimestamp != null && enableTimestamps) {
                    val timestamp = TwitchApiHelper.getTimestamp(messageTimestamp, timestampFormat)
                    if (timestamp != null) {
                        builder.append("$timestamp ")
                        builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), 0, timestamp.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                        builderIndex += timestamp.length + 1
                    }
                }
                val systemMsg = chatMessage.systemMsg
                if (systemMsg != null) {
                    builder.append(systemMsg)
                    builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + systemMsg.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (showSystemMessageEmotes) {
                        prepareEmotes(chatMessage, systemMsg, builder, builderIndex, images, imageClick, useReadableColors, isLightTheme, enableOverlayEmotes, useBoldNames, loggedInUser, chatUrl, getEmoteBytes, savedColors, localTwitchEmotes, showPersonalEmotes, personalEmoteSets, null, thirdPartyEmotes, cheerEmotes, savedLocalTwitchEmotes, savedLocalCheerEmotes, savedLocalEmotes)
                    }
                } else {
                    val reward = chatMessage.reward
                    val rewardTitle = reward?.title
                    if (rewardTitle != null) {
                        val messageUserLogin = chatMessage.userLogin
                        val messageUserName = chatMessage.userName
                        val userName = if (messageUserLogin != null && !messageUserLogin.equals(messageUserName, true)) {
                            when (nameDisplay) {
                                "0" -> "$messageUserName($messageUserLogin)"
                                "1" -> messageUserName
                                else -> messageUserLogin
                            }
                        } else {
                            messageUserName
                        }
                        val string = redeemedNoMsg.format(userName, rewardTitle)
                        builder.append("$string ")
                        builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + string.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (showSystemMessageEmotes) {
                            prepareEmotes(chatMessage, string, builder, builderIndex, images, imageClick, useReadableColors, isLightTheme, enableOverlayEmotes, useBoldNames, loggedInUser, chatUrl, getEmoteBytes, savedColors, localTwitchEmotes, showPersonalEmotes, personalEmoteSets, null, thirdPartyEmotes, cheerEmotes, savedLocalTwitchEmotes, savedLocalCheerEmotes, savedLocalEmotes)
                        }
                        builderIndex = builder.length
                        builder.append(". ")
                        builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        images.add(Image(
                            url1x = reward.url1x,
                            url2x = reward.url2x,
                            url3x = reward.url4x,
                            url4x = reward.url4x,
                            size = Image.IMAGE_SIZE_BADGE,
                            start = builderIndex++,
                            end = builderIndex++
                        ))
                        val rewardCost = reward.cost
                        if (rewardCost != null) {
                            val cost = NumberFormat.getInstance().format(rewardCost)
                            builder.append(cost)
                            builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + cost.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                itemView.setBackgroundResource(0)
            }
            else -> {
                val systemMsg = chatMessage.systemMsg
                if (systemMsg != null) {
                    builder.append("$systemMsg\n")
                    builderIndex += systemMsg.length + 1
                } else {
                    val messageId = chatMessage.msgId
                    if (messageId != null) {
                        val msgId = TwitchApiHelper.getMessageIdString(context, messageId) ?: messageId
                        builder.append("$msgId\n")
                        builderIndex += msgId.length + 1
                    }
                }
                if (chatMessage.isFirst && firstMsgVisibility == 0) {
                    builder.append("$firstChatMsg\n")
                    builderIndex += firstChatMsg.length + 1
                }
                val reward = chatMessage.reward
                val rewardTitle = reward?.title
                if (rewardTitle != null) {
                    val string = redeemedChatMsg.format(rewardTitle)
                    builder.append("$string ")
                    builderIndex += string.length + 1
                    builder.append(". ")
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    images.add(Image(
                        url1x = reward.url1x,
                        url2x = reward.url2x,
                        url3x = reward.url4x,
                        url4x = reward.url4x,
                        size = Image.IMAGE_SIZE_BADGE,
                        start = builderIndex++,
                        end = builderIndex++
                    ))
                    val rewardCost = reward.cost
                    if (rewardCost != null) {
                        val cost = NumberFormat.getInstance().format(rewardCost)
                        builder.append(cost)
                        builderIndex += cost.length
                    }
                    builder.append("\n")
                    builderIndex += 1
                } else {
                    if (reward?.id != null && firstMsgVisibility == 0) {
                        builder.append("$rewardChatMsg\n")
                        builderIndex += rewardChatMsg.length + 1
                    }
                }
                val messageTimestamp = chatMessage.timestamp
                if (messageTimestamp != null && enableTimestamps) {
                    val timestamp = TwitchApiHelper.getTimestamp(messageTimestamp, timestampFormat)
                    if (timestamp != null) {
                        builder.append("$timestamp ")
                        builder.setSpan(ForegroundColorSpan(getSavedColor("#999999", savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + timestamp.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                        builderIndex += timestamp.length + 1
                    }
                }
                chatMessage.badges?.forEach { chatBadge ->
                    val badge = synchronized(channelBadges) {
                        channelBadges.find { it.setId == chatBadge.setId && it.version == chatBadge.version }
                    } ?:
                    synchronized(globalBadges) {
                        globalBadges.find { it.setId == chatBadge.setId && it.version == chatBadge.version }
                    }
                    if (badge != null) {
                        builder.append(". ")
                        builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (imageClick != null) {
                            builder.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    imageClick(badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x, badge.title, null, null, null, null, null)
                                }

                                override fun updateDrawState(ds: TextPaint) {}
                            }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        images.add(Image(
                            localData = badge.localData?.let { getLocalEmoteData(badge.setId + badge.version, it, savedLocalBadges, chatUrl, getEmoteBytes) },
                            url1x = badge.url1x,
                            url2x = badge.url2x,
                            url3x = badge.url3x,
                            url4x = badge.url4x,
                            size = Image.IMAGE_SIZE_BADGE,
                            start = builderIndex++,
                            end = builderIndex++
                        ))
                    }
                }
                val stvUser = if ((showSTVBadges || showNamePaints || showPersonalEmotes) && !chatMessage.userId.isNullOrBlank()) {
                    synchronized(stvUsers) {
                        stvUsers.find { it.userId == chatMessage.userId }
                    }
                } else null
                if (showSTVBadges && !chatMessage.userId.isNullOrBlank()) {
                    val badge = stvUser?.badgeId?.let { badgeId ->
                        synchronized(stvBadges) {
                            stvBadges.find { it.id == badgeId }
                        }
                    }
                    if (badge != null) {
                        builder.append(". ")
                        builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (imageClick != null) {
                            builder.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    imageClick(badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x, badge.name, badge.format, true, null, true, null)
                                }

                                override fun updateDrawState(ds: TextPaint) {}
                            }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        images.add(Image(
                            url1x = badge.url1x,
                            url2x = badge.url2x,
                            url3x = badge.url3x,
                            url4x = badge.url4x,
                            format = badge.format,
                            size = Image.IMAGE_SIZE_BADGE,
                            isAnimated = true,
                            thirdParty = true,
                            start = builderIndex++,
                            end = builderIndex++
                        ))
                    }
                }
                val messageColor = chatMessage.color
                val messageUserNameForColor = chatMessage.userName
                val color = if (messageColor != null) {
                    getSavedColor(messageColor, savedColors, useReadableColors, isLightTheme)
                } else {
                    userColors[messageUserNameForColor] ?: if (useRandomColors) {
                        twitchColors[random.nextInt(twitchColors.size)]
                    } else {
                        -10066329
                    }.let { newColor ->
                        if (useReadableColors) {
                            adaptUsernameColor(newColor, isLightTheme)
                        } else {
                            newColor
                        }.also { val localUserName = chatMessage.userName; if (localUserName != null) userColors[localUserName] = it }
                    }
                }
                val messageUserName = chatMessage.userName
                val messageUserLogin = chatMessage.userLogin
                if (!messageUserName.isNullOrBlank()) {
                    userName = if (messageUserLogin != null && !messageUserLogin.equals(messageUserName, true)) {
                        when (nameDisplay) {
                            "0" -> "$messageUserName($messageUserLogin)"
                            "1" -> messageUserName
                            else -> messageUserLogin
                        }
                    } else {
                        messageUserName
                    }
                    val localUserName = userName
                    builder.append(localUserName)
                    builder.setSpan(ForegroundColorSpan(color), builderIndex, builderIndex + localUserName.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (useBoldNames) {
                        builder.setSpan(StyleSpan(Typeface.BOLD), builderIndex, builderIndex + localUserName.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (showNamePaints && !chatMessage.userId.isNullOrBlank()) {
                        stvUser?.paintId?.let { paintId ->
                            synchronized(namePaints) {
                                namePaints.find { it.id == paintId }
                            }
                        }?.let { paint ->
                            val paintType = paint.type
                            val paintColors = paint.colors
                            val paintColorPositions = paint.colorPositions
                            when (paintType) {
                                "LINEAR_GRADIENT", "RADIAL_GRADIENT" -> {
                                    if (paintColors != null && paintColorPositions != null) {
                                        builder.setSpan(
                                            NamePaintSpan(
                                                localUserName,
                                                paintType,
                                                paintColors,
                                                paintColorPositions,
                                                paint.angle,
                                                paint.repeat,
                                                paint.shadows
                                            ),
                                            builderIndex,
                                            builderIndex + localUserName.length,
                                            SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                                "URL" -> {
                                    if (!paint.imageUrl.isNullOrBlank()) {
                                        imagePaint = paint
                                        userNameStartIndex = builderIndex
                                    }
                                }
                            }
                        }
                    }
                    builderIndex += localUserName.length
                    if (!chatMessage.isAction) {
                        builder.append(": ")
                        builderIndex += 2
                    } else {
                        builder.append(" ")
                        builderIndex += 1
                    }
                }
                if (showGifMessages && !chatMessage.gif.isNullOrBlank()) {
                    builder.append("\n")
                    builderIndex += 1
                    builder.append(".")
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (imageClick != null) {
                        builder.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                imageClick(chatMessage.gif, chatMessage.message?.removeSurrounding("[", "]"), "gif", true, null, false, null)
                            }

                            override fun updateDrawState(ds: TextPaint) {}
                        }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    images.add(Image(
                        url1x = chatMessage.gif,
                        url2x = chatMessage.gif,
                        url3x = chatMessage.gif,
                        url4x = chatMessage.gif,
                        format = "gif",
                        isAnimated = true,
                        size = Image.IMAGE_SIZE_GIF,
                        start = builderIndex,
                        end = builderIndex + 1
                    ))
                } else {
                    val chatMessageText = chatMessage.message
                    if (chatMessageText != null) {
                        builder.append(chatMessageText)
                        if (chatMessage.isAction) {
                            builder.setSpan(ForegroundColorSpan(color), builderIndex, builderIndex + chatMessageText.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        val result = prepareEmotes(chatMessage, chatMessageText, builder, builderIndex, images, imageClick, useReadableColors, isLightTheme, enableOverlayEmotes, useBoldNames, loggedInUser, chatUrl, getEmoteBytes, savedColors, localTwitchEmotes, showPersonalEmotes, personalEmoteSets, stvUser, thirdPartyEmotes, cheerEmotes, savedLocalTwitchEmotes, savedLocalCheerEmotes, savedLocalEmotes)
                        wasMentioned = result
                    }
                }
                when {
                    chatMessage.isFirst && firstMsgVisibility < 2 -> itemView.setBackgroundResource(R.color.chatMessageFirst)
                    chatMessage.reward?.id != null && firstMsgVisibility < 2 -> itemView.setBackgroundResource(R.color.chatMessageReward)
                    chatMessage.systemMsg != null || chatMessage.msgId != null -> itemView.setBackgroundResource(R.color.chatMessageNotice)
                    wasMentioned -> itemView.setBackgroundResource(R.color.chatMessageMention)
                    else -> itemView.setBackgroundResource(0)
                }
            }
        }
        return MessageResult(builder, images, imagePaint, userName, userNameStartIndex)
    }

    class MessageResult(
        val builder: SpannableStringBuilder,
        val images: ArrayList<Image>,
        val imagePaint: NamePaint?,
        val userName: String?,
        val userNameStartIndex: Int?,
    )

    private fun getSavedColor(color: String, savedColors: HashMap<String, Int>, useReadableColors: Boolean, isLightTheme: Boolean): Int {
        return savedColors[color] ?: color.toColorInt().let { newColor ->
            if (useReadableColors) {
                adaptUsernameColor(newColor, isLightTheme)
            } else {
                newColor
            }.also { savedColors[color] = it }
        }
    }

    private fun adaptUsernameColor(color: Int, isLightTheme: Boolean): Int {
        val colorArray = FloatArray(3)
        ColorUtils.colorToHSL(color, colorArray)
        if (isLightTheme) {
            val luminanceMax = 0.75f -
                    maxOf(1f - ((colorArray[0] - GREEN_HUE_DEGREES) / 100f).pow(2f), RED_HUE_DEGREES) * 0.4f
            colorArray[2] = minOf(colorArray[2], luminanceMax)
        } else {
            val distToRed = RED_HUE_DEGREES - colorArray[0]
            val distToBlue = BLUE_HUE_DEGREES - colorArray[0]
            val normDistanceToRed = distToRed - TWO_PI_DEGREES * floor((distToRed + PI_DEGREES) / TWO_PI_DEGREES)
            val normDistanceToBlue = distToBlue - TWO_PI_DEGREES * floor((distToBlue + PI_DEGREES) / TWO_PI_DEGREES)

            val luminanceMin = 0.3f +
                    maxOf((1f - (normDistanceToBlue / 40f).pow(2f)) * 0.35f, RED_HUE_DEGREES) +
                    maxOf((1f - (normDistanceToRed / 40f).pow(2f)) * 0.1f, RED_HUE_DEGREES)
            colorArray[2] = maxOf(colorArray[2], luminanceMin)
        }

        return ColorUtils.HSLToColor(colorArray)
    }

    private fun prepareEmotes(chatMessage: ChatMessage, message: String, builder: SpannableStringBuilder, startIndex: Int, images: ArrayList<Image>, imageClick: ((String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit)?, useReadableColors: Boolean, isLightTheme: Boolean, enableOverlayEmotes: Boolean, useBoldNames: Boolean, loggedInUser: String?, chatUrl: String?, getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?, savedColors: HashMap<String, Int>, localTwitchEmotes: List<TwitchEmote>, showPersonalEmotes: Boolean, personalEmoteSets: Map<String, List<Emote>>, stvUser: STVUser?, thirdPartyEmotes: List<Emote>, cheerEmotes: List<CheerEmote>, savedLocalTwitchEmotes: MutableMap<String, ByteArray>, savedLocalCheerEmotes: MutableMap<String, ByteArray>, savedLocalEmotes: MutableMap<String, ByteArray>): Boolean {
        var wasMentioned = false
        try {
            var builderIndex = startIndex
            val split = builder.substring(builderIndex).split(" ")
            var previousImage: Image? = null
            val twitchEmotes = chatMessage.emotes?.map {
                val realBegin = message.offsetByCodePoints(0, it.begin)
                val realEnd = if (it.begin == realBegin) {
                    it.end
                } else {
                    it.end + realBegin - it.begin
                }
                localTwitchEmotes.find { emote -> emote.id == it.id }?.let { emote ->
                    TwitchEmote(
                        id = emote.id,
                        name = emote.name,
                        localData = emote.localData,
                        format = emote.format,
                        isAnimated = emote.isAnimated,
                        begin = realBegin,
                        end = realEnd,
                        setId = emote.setId,
                        ownerId = emote.ownerId
                    )
                } ?: TwitchEmote(id = it.id, begin = realBegin, end = realEnd)
            }?.sortedBy { it.begin }?.toMutableList()
            val personalEmotes = if (showPersonalEmotes) {
                stvUser?.emoteSetId?.let { setId ->
                    synchronized(personalEmoteSets) {
                        personalEmoteSets.entries.find { it.key == setId }?.value
                    }
                }
            } else null
            for (value in split) {
                if (chatMessage.bits != null) {
                    val bitsCount = value.takeLastWhile { it.isDigit() }
                    val bitsName = value.substringBeforeLast(bitsCount)
                    if (bitsCount.isNotEmpty()) {
                        val emote = synchronized(cheerEmotes) {
                            cheerEmotes.findLast { it.name.equals(bitsName, true) && it.minBits <= bitsCount.toInt() }
                        }
                        if (emote != null) {
                            builder.replace(builderIndex, builderIndex + bitsName.length, ".")
                            builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (imageClick != null) {
                                builder.setSpan(object : ClickableSpan() {
                                    override fun onClick(widget: View) {
                                        imageClick(emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x, value, emote.format, emote.isAnimated, null, null, null)
                                    }

                                    override fun updateDrawState(ds: TextPaint) {}
                                }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            images.add(Image(
                                localData = emote.localData?.let { getLocalEmoteData(emote.name + emote.minBits, it, savedLocalCheerEmotes, chatUrl, getEmoteBytes) },
                                url1x = emote.url1x,
                                url2x = emote.url2x,
                                url3x = emote.url3x,
                                url4x = emote.url4x,
                                format = emote.format,
                                isAnimated = emote.isAnimated,
                                size = Image.IMAGE_SIZE_EMOTE,
                                start = builderIndex,
                                end = builderIndex + 1
                            ))
                            builderIndex += 1
                            val emoteColor = emote.color
                            if (!emoteColor.isNullOrBlank()) {
                                builder.setSpan(ForegroundColorSpan(getSavedColor(emoteColor, savedColors, useReadableColors, isLightTheme)), builderIndex, builderIndex + bitsCount.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            if (!twitchEmotes.isNullOrEmpty()) {
                                val removed = bitsName.length - 1
                                twitchEmotes.forEach {
                                    it.begin -= removed
                                    it.end -= removed
                                }
                            }
                            previousImage = null
                            builderIndex += bitsCount.length + 1
                            continue
                        }
                    }
                }
                val emote = personalEmotes?.find {
                    it.name == value
                } ?: synchronized(thirdPartyEmotes) {
                    thirdPartyEmotes.find { it.name == value }
                }
                if (emote != null) {
                    if (emote.isOverlayEmote && enableOverlayEmotes && previousImage != null) {
                        builder.replace(builderIndex - 1, builderIndex + value.length, "")
                        val image = Image(
                            localData = emote.localData?.let { getLocalEmoteData(emote.name!!, it, savedLocalEmotes, chatUrl, getEmoteBytes) },
                            url1x = emote.url1x,
                            url2x = emote.url2x,
                            url3x = emote.url3x,
                            url4x = emote.url4x,
                            format = emote.format,
                            isAnimated = emote.isAnimated,
                            size = Image.IMAGE_SIZE_EMOTE,
                            thirdParty = emote.thirdParty,
                            start = previousImage.start,
                            end = previousImage.end
                        )
                        if (!twitchEmotes.isNullOrEmpty()) {
                            val removed = value.length + 1
                            twitchEmotes.forEach {
                                it.begin -= removed
                                it.end -= removed
                            }
                        }
                        previousImage.overlayEmote = image
                        previousImage = image
                        continue
                    } else {
                        builder.replace(builderIndex, builderIndex + value.length, ".")
                        builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (imageClick != null) {
                            builder.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    imageClick(emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x, emote.name, emote.format, emote.isAnimated, emote.source, emote.thirdParty, null)
                                }

                                override fun updateDrawState(ds: TextPaint) {}
                            }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        val image = Image(
                            localData = emote.localData?.let { getLocalEmoteData(emote.name!!, it, savedLocalEmotes, chatUrl, getEmoteBytes) },
                            url1x = emote.url1x,
                            url2x = emote.url2x,
                            url3x = emote.url3x,
                            url4x = emote.url4x,
                            format = emote.format,
                            isAnimated = emote.isAnimated,
                            size = Image.IMAGE_SIZE_EMOTE,
                            thirdParty = emote.thirdParty,
                            start = builderIndex,
                            end = builderIndex + 1
                        )
                        images.add(image)
                        if (!twitchEmotes.isNullOrEmpty()) {
                            val removed = value.length - 1
                            twitchEmotes.forEach {
                                it.begin -= removed
                                it.end -= removed
                            }
                        }
                        previousImage = image
                        builderIndex += 2
                        continue
                    }
                }
                val twitchEmote = twitchEmotes?.firstOrNull()?.let { first ->
                    val messageIndex = builderIndex - startIndex
                    when {
                        first.begin == messageIndex -> first
                        first.begin < messageIndex -> {
                            twitchEmotes.remove(first)
                            twitchEmotes.firstOrNull()?.takeIf { it.begin == messageIndex }
                        }
                        else -> null
                    }
                }
                if (twitchEmote != null) {
                    builder.replace(builderIndex, builderIndex + value.length, ".")
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (imageClick != null) {
                        builder.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                imageClick(twitchEmote.url4x ?: twitchEmote.url3x ?: twitchEmote.url2x ?: twitchEmote.url1x, value, twitchEmote.format, twitchEmote.isAnimated, null, null, twitchEmote.id)
                            }

                            override fun updateDrawState(ds: TextPaint) {}
                        }, builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val image = Image(
                        localData = twitchEmote.localData?.let { getLocalEmoteData(twitchEmote.id!!, it, savedLocalTwitchEmotes, chatUrl, getEmoteBytes) },
                        url1x = twitchEmote.url1x,
                        url2x = twitchEmote.url2x,
                        url3x = twitchEmote.url3x,
                        url4x = twitchEmote.url4x,
                        format = twitchEmote.format,
                        isAnimated = twitchEmote.isAnimated,
                        size = Image.IMAGE_SIZE_EMOTE,
                        start = builderIndex,
                        end = builderIndex + 1
                    )
                    images.add(image)
                    twitchEmotes.remove(twitchEmote)
                    if (twitchEmotes.isNotEmpty()) {
                        val removed = value.length - 1
                        twitchEmotes.forEach {
                            it.begin -= removed
                            it.end -= removed
                        }
                    }
                    previousImage = image
                    builderIndex += 2
                    continue
                }
                if (Patterns.WEB_URL.matcher(value).matches()) {
                    val url = if (value.startsWith("http")) value else "https://$value"
                    builder.setSpan(URLSpan(url), builderIndex, builderIndex + value.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    previousImage = null
                    builderIndex += value.length + 1
                    continue
                }
                if (value.startsWith('@') && useBoldNames) {
                    builder.setSpan(StyleSpan(Typeface.BOLD), builderIndex, builderIndex + value.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (!wasMentioned &&
                    !loggedInUser.isNullOrBlank() &&
                    value.contains(loggedInUser, true) &&
                    chatMessage.userId != null &&
                    chatMessage.userLogin != loggedInUser
                ) {
                    wasMentioned = true
                }
                previousImage = null
                builderIndex += value.length + 1
            }
        } catch (_: Exception) {

        }
        return wasMentioned
    }

    private fun getLocalEmoteData(name: String, data: Pair<Long, Int>, savedLocalEmotes: MutableMap<String, ByteArray>, chatUrl: String?, getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?): ByteArray? {
        return savedLocalEmotes[name] ?: chatUrl?.let { url ->
            getEmoteBytes?.let { get ->
                get(url, data)?.also {
                    if (savedLocalEmotes.size >= 100) {
                        savedLocalEmotes.remove(savedLocalEmotes.keys.first())
                    }
                    savedLocalEmotes[name] = it
                }
            }
        }
    }

    fun loadImages(fragment: Fragment, itemView: View, bind: (SpannableStringBuilder) -> Unit, images: List<Image>, imagePaint: NamePaint?, userName: String?, userNameStartIndex: Int?, backgroundColor: Int, builder: SpannableStringBuilder, emoteSize: Int, badgeSize: Int, gifSize: Int, emoteQuality: String, animateGifs: Boolean) {
        if (imagePaint != null) {
            fragment.requireContext().imageLoader.enqueue(
                ImageRequest.Builder(fragment.requireContext()).apply {
                    data(imagePaint.imageUrl)
                    httpHeaders(NetworkHeaders.Builder().apply {
                        add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME)
                    }.build())
                    target(
                        onSuccess = {
                            (it.asDrawable(fragment.resources)).let { result ->
                                if (result is Animatable && animateGifs) {
                                    result.callback = object : Drawable.Callback {
                                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                            itemView.removeCallbacks(what)
                                        }

                                        override fun invalidateDrawable(who: Drawable) {
                                            itemView.invalidate()
                                        }

                                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                            itemView.postDelayed(what, `when`)
                                        }
                                    }
                                    (result as Animatable).start()
                                }
                                try {
                                    builder.setSpan(
                                        NamePaintImageSpan(
                                            userName!!,
                                            imagePaint.shadows,
                                            (itemView.background as? ColorDrawable)?.color,
                                            backgroundColor,
                                            result
                                        ),
                                        userNameStartIndex!!,
                                        userNameStartIndex + userName.length,
                                        SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                } catch (_: IndexOutOfBoundsException) {
                                }
                                bind(builder)
                            }
                        },
                    )
                }.build()
            )
        }
        images.forEach { image ->
            loadImage(fragment, image, emoteQuality) { result ->
                val imageSize = when (image.size) {
                    Image.IMAGE_SIZE_EMOTE -> emoteSize
                    Image.IMAGE_SIZE_GIF -> gifSize
                    else -> badgeSize
                }
                val widthRatio = result.intrinsicWidth.toFloat() / result.intrinsicHeight.toFloat()
                val size = if (widthRatio == 1f) {
                    imageSize to imageSize
                } else {
                    (imageSize * widthRatio).toInt() to imageSize
                }
                result.setBounds(0, 0, size.first, size.second)
                if (result is Animatable && image.isAnimated && animateGifs) {
                    result.callback = object : Drawable.Callback {
                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                            itemView.removeCallbacks(what)
                        }

                        override fun invalidateDrawable(who: Drawable) {
                            itemView.invalidate()
                        }

                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                            itemView.postDelayed(what, `when`)
                        }
                    }
                    (result as Animatable).start()
                }
                if (image.overlayEmote != null) {
                    val drawables = arrayOf(result)
                    nextOverlayEmote(fragment, drawables, image.overlayEmote!!, image, itemView, bind, builder, emoteSize, emoteQuality, animateGifs)
                } else {
                    builder.setSpan(CenteredImageSpan(result), image.start, image.end, SPAN_EXCLUSIVE_EXCLUSIVE)
                    bind(builder)
                }
            }
        }
    }

    private fun nextOverlayEmote(fragment: Fragment, drawables: Array<Drawable>, image: Image, bottomImage: Image, itemView: View, bind: (SpannableStringBuilder) -> Unit, builder: SpannableStringBuilder, emoteSize: Int, emoteQuality: String, animateGifs: Boolean) {
        loadImage(fragment, image, emoteQuality) { result ->
            val widthRatio = result.intrinsicWidth.toFloat() / result.intrinsicHeight.toFloat()
            val size = if (widthRatio == 1f) {
                emoteSize to emoteSize
            } else {
                (emoteSize * widthRatio).toInt() to emoteSize
            }
            result.setBounds(0, 0, size.first, size.second)
            if (result is Animatable && image.isAnimated && animateGifs) {
                result.callback = object : Drawable.Callback {
                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                        itemView.removeCallbacks(what)
                    }

                    override fun invalidateDrawable(who: Drawable) {
                        itemView.invalidate()
                    }

                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                        itemView.postDelayed(what, `when`)
                    }
                }
                (result as Animatable).start()
            }
            val array = drawables.plus(result)
            if (image.overlayEmote != null) {
                nextOverlayEmote(fragment, array, image.overlayEmote!!, bottomImage, itemView, bind, builder, emoteSize, emoteQuality, animateGifs)
            } else {
                val layer = LayerDrawable(array)
                val width = array.maxOf { it.bounds.right }
                val height = array.maxOf { it.bounds.bottom }
                layer.setBounds(0, 0, width, height)
                builder.setSpan(CenteredImageSpan(layer), bottomImage.start, bottomImage.end, SPAN_EXCLUSIVE_EXCLUSIVE)
                bind(builder)
            }
        }
    }

    private fun loadImage(fragment: Fragment, image: Image, emoteQuality: String, onLoaded: (Drawable) -> Unit) {
        fragment.requireContext().imageLoader.enqueue(
            ImageRequest.Builder(fragment.requireContext()).apply {
                data(image.localData ?: when (emoteQuality) {
                    "4" -> image.url4x ?: image.url3x ?: image.url2x ?: image.url1x
                    "3" -> image.url3x ?: image.url2x ?: image.url1x
                    "2" -> image.url2x ?: image.url1x
                    else -> image.url1x
                })
                if (image.thirdParty) {
                    httpHeaders(NetworkHeaders.Builder().apply {
                        add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME)
                    }.build())
                }
                target(
                    onSuccess = {
                        onLoaded((it.asDrawable(fragment.resources)))
                    },
                )
            }.build()
        )
    }
}