package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Poll
import com.github.andreyasadchy.xtra.model.chat.Prediction
import com.github.andreyasadchy.xtra.model.chat.Raid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object PubSubParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parsePlaybackMessage(jsonString: String): PlaybackMessage? =
        parsePlaybackMessage(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null)

    fun parsePlaybackMessage(message: JsonObject): PlaybackMessage? {
        val messageType = message.stringOrNullCompat("type").orEmpty()
        return when {
            messageType.startsWith("viewcount") -> PlaybackMessage(
                viewers = message.intOrNullCompat("viewers")
            )
            messageType.startsWith("stream-up") -> PlaybackMessage(
                true,
                message["server_time"]?.asPrimitiveOrNullCompat()?.longOrNull?.takeIf { it > 0 }
            )
            messageType.startsWith("stream-down") -> PlaybackMessage(false)
            else -> null
        }
    }

    fun parseStreamInfo(jsonString: String): StreamInfo =
        parseStreamInfo(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()))

    fun parseStreamInfo(message: JsonObject): StreamInfo {
        return StreamInfo(
            title = message.stringOrNullCompat("status"),
            gameId = message.intOrNullCompat("game_id")?.takeIf { it > 0 }?.toString(),
            gameName = message.stringOrNullCompat("game"),
        )
    }

    fun parseRewardMessage(jsonString: String): ChatMessage =
        parseRewardMessage(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()))

    fun parseRewardMessage(message: JsonObject): ChatMessage {
        val messageData = message["data"]?.asObjectOrNullCompat()
        val redemption = messageData?.get("redemption")?.asObjectOrNullCompat()
        val user = redemption?.get("user")?.asObjectOrNullCompat()
        val reward = redemption?.get("reward")?.asObjectOrNullCompat()
        val rewardImage = reward?.get("image")?.asObjectOrNullCompat()
        val defaultImage = reward?.get("default_image")?.asObjectOrNullCompat()
        val input = redemption?.stringOrNullCompat("user_input")
        return ChatMessage(
            type = ChatMessage.USER_MESSAGE,
            userId = user?.stringOrNullCompat("id"),
            userLogin = user?.stringOrNullCompat("login"),
            userName = user?.stringOrNullCompat("display_name"),
            message = input,
            reward = ChannelPointReward(
                id = reward?.stringOrNullCompat("id"),
                title = reward?.stringOrNullCompat("title"),
                cost = reward?.intOrNullCompat("cost"),
                url1x = rewardImage?.stringOrNullCompat("url_1x")
                    ?: defaultImage?.stringOrNullCompat("url_1x"),
                url2x = rewardImage?.stringOrNullCompat("url_2x")
                    ?: defaultImage?.stringOrNullCompat("url_2x"),
                url4x = rewardImage?.stringOrNullCompat("url_4x")
                    ?: defaultImage?.stringOrNullCompat("url_4x"),
            ),
            timestamp = messageData?.stringOrNullCompat("timestamp")?.let { EventSubParser.parseTimestamp(it) },
            fullMsg = message.toString(),
        )
    }

    fun parsePointsEarned(jsonString: String): Pair<PointsEarned, String?> =
        parsePointsEarned(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: JsonObject(emptyMap()))

    fun parsePointsEarned(message: JsonObject): Pair<PointsEarned, String?> {
        val messageData = message["data"]?.asObjectOrNullCompat()
        val messageChannelId = messageData?.stringOrNullCompat("channel_id")
        val pointGain = messageData?.get("point_gain")?.asObjectOrNullCompat()
        return Pair(
            PointsEarned(
                pointsGained = pointGain?.intOrNullCompat("total_points"),
                timestamp = messageData?.stringOrNullCompat("timestamp")?.let { EventSubParser.parseTimestamp(it) },
                fullMsg = message.toString()
            ),
            messageChannelId
        )
    }

    fun onRaidUpdate(jsonString: String, openStream: Boolean): Raid? =
        onRaidUpdate(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null, openStream)

    fun onRaidUpdate(message: JsonObject, openStream: Boolean): Raid? {
        val raid = message["raid"]?.asObjectOrNullCompat()
        return if (raid != null) {
            Raid(
                raidId = raid.stringOrNullCompat("id"),
                targetId = raid.stringOrNullCompat("target_id"),
                targetLogin = raid.stringOrNullCompat("target_login"),
                targetName = raid.stringOrNullCompat("target_display_name"),
                targetImageURL = raid.stringOrNullCompat("target_profile_image")?.replace("profile_image-%s", "profile_image-300x300"),
                viewerCount = raid.intOrNullCompat("viewer_count"),
                openStream = openStream
            )
        } else null
    }

    fun onPollUpdate(jsonString: String): Poll? =
        onPollUpdate(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null)

    fun onPollUpdate(message: JsonObject): Poll? {
        val messageData = message["data"]?.asObjectOrNullCompat()
        val poll = messageData?.get("poll")?.asObjectOrNullCompat()
        val choicesList = mutableListOf<Poll.PollChoice>()
        val choices = poll?.get("choices")?.asArrayOrNullCompat()
        if (choices != null) {
            for (i in 0 until choices.size) {
                val choice = choices[i].asObjectOrNullCompat()
                val title = choice?.stringOrNullCompat("title")
                if (!title.isNullOrBlank()) {
                    choicesList.add(
                        Poll.PollChoice(
                            title = title,
                            totalVotes = choice.get("votes")?.asObjectOrNullCompat()?.intOrNullCompat("total"),
                        )
                    )
                }
            }
        }
        return if (poll != null) {
            Poll(
                id = poll.stringOrNullCompat("poll_id"),
                title = poll.stringOrNullCompat("title"),
                status = poll.stringOrNullCompat("status"),
                choices = choicesList,
                totalVotes = poll.get("votes")?.asObjectOrNullCompat()?.intOrNullCompat("total"),
                remainingMilliseconds = poll.intOrNullCompat("remaining_duration_milliseconds"),
            )
        } else null
    }

    fun onPredictionUpdate(jsonString: String): Prediction? =
        onPredictionUpdate(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null)

    fun onPredictionUpdate(message: JsonObject): Prediction? {
        val messageData = message["data"]?.asObjectOrNullCompat()
        val prediction = messageData?.get("event")?.asObjectOrNullCompat()
        val outcomesList = mutableListOf<Prediction.PredictionOutcome>()
        val outcomes = prediction?.get("outcomes")?.asArrayOrNullCompat()
        if (outcomes != null) {
            for (i in 0 until outcomes.size) {
                val outcome = outcomes[i].asObjectOrNullCompat()
                val title = outcome?.stringOrNullCompat("title")
                if (!title.isNullOrBlank()) {
                    outcomesList.add(
                        Prediction.PredictionOutcome(
                            id = outcome.stringOrNullCompat("id"),
                            title = title,
                            totalPoints = outcome.intOrNullCompat("total_points"),
                            totalUsers = outcome.intOrNullCompat("total_users"),
                        )
                    )
                }
            }
        }
        return if (prediction != null) {
            Prediction(
                id = prediction.stringOrNullCompat("id"),
                createdAt = prediction.stringOrNullCompat("created_at")?.let { EventSubParser.parseTimestamp(it) },
                outcomes = outcomesList,
                predictionWindowSeconds = prediction.intOrNullCompat("prediction_window_seconds"),
                status = prediction.stringOrNullCompat("status"),
                title = prediction.stringOrNullCompat("title"),
                winningOutcomeId = prediction.stringOrNullCompat("winning_outcome_id"),
            )
        } else null
    }

    class PlaybackMessage(
        val live: Boolean? = null,
        val serverTime: Long? = null,
        val viewers: Int? = null,
    )

    class StreamInfo(
        val title: String? = null,
        val gameId: String? = null,
        val gameName: String? = null,
    )

    class PointsEarned(
        val pointsGained: Int? = null,
        val timestamp: Long? = null,
        val fullMsg: String? = null,
    )
}
