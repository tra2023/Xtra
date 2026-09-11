package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.STVBadge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

object StvParser {

    private val json = Json { ignoreUnknownKeys = true }

    class EmoteSetUpdate(
        val channelSet: Boolean,
        val setId: String,
        val added: List<Emote>,
        val removed: List<Emote>,
        val updated: List<Pair<Emote, Emote>>,
    )

    fun parseEmoteSetUpdate(jsonString: String, useWebp: Boolean, channelSTVEmoteSetId: String?): EmoteSetUpdate? =
        parseEmoteSetUpdate(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null, useWebp, channelSTVEmoteSetId)

    fun parseEmoteSetUpdate(body: JsonObject, useWebp: Boolean, channelSTVEmoteSetId: String?): EmoteSetUpdate? {
        val id = body.stringOrNullCompat("id")
        if (id != null) {
            val channelSet = id == channelSTVEmoteSetId
            val added = mutableListOf<Emote>()
            val removed = mutableListOf<Emote>()
            val updated = mutableListOf<Pair<Emote, Emote>>()
            val pushedArray = body["pushed"]?.asArrayOrNullCompat()
            if (pushedArray != null) {
                for (i in 0 until pushedArray.size) {
                    val pushedObject = pushedArray[i].asObjectOrNullCompat()
                    if (pushedObject?.stringOrNullCompat("key") == "emotes") {
                        parseEmote(pushedObject["value"]?.asObjectOrNullCompat(), useWebp, channelSet)?.let {
                            added.add(it)
                        }
                    }
                }
            }
            val pulledArray = body["pulled"]?.asArrayOrNullCompat()
            if (pulledArray != null) {
                for (i in 0 until pulledArray.size) {
                    val pulledObject = pulledArray[i].asObjectOrNullCompat()
                    if (pulledObject?.stringOrNullCompat("key") == "emotes") {
                        parseEmote(pulledObject["old_value"]?.asObjectOrNullCompat(), useWebp, channelSet)?.let {
                            removed.add(it)
                        }
                    }
                }
            }
            val updatedArray = body["updated"]?.asArrayOrNullCompat()
            if (updatedArray != null) {
                for (i in 0 until updatedArray.size) {
                    val updatedObject = updatedArray[i].asObjectOrNullCompat()
                    if (updatedObject?.stringOrNullCompat("key") == "emotes") {
                        val old = parseEmote(updatedObject["old_value"]?.asObjectOrNullCompat(), useWebp, channelSet)
                        val new = parseEmote(updatedObject["value"]?.asObjectOrNullCompat(), useWebp, channelSet)
                        if (old != null && new != null) {
                            updated.add(Pair(old, new))
                        }
                    }
                }
            }
            return EmoteSetUpdate(channelSet, id, added, removed, updated)
        }
        return null
    }

    private fun parseEmote(value: JsonObject?, useWebp: Boolean, channelSet: Boolean): Emote? {
        val objectData = value?.get("data")?.asObjectOrNullCompat()
        return if (objectData != null) {
            val name = objectData.stringOrNullCompat("name")
            val host = objectData["host"]?.asObjectOrNullCompat()
            if (name != null && host != null) {
                val template = host.stringOrNullCompat("url")
                if (template != null) {
                    val urls = mutableListOf<String>()
                    val files = host["files"]?.asArrayOrNullCompat()
                    if (files != null) {
                        for (i in 0 until files.size) {
                            val fileObject = files[i].asObjectOrNullCompat()
                            val fileName = fileObject?.stringOrNullCompat("name")
                            val fileFormat = fileObject?.stringOrNullCompat("format")
                            if (fileName != null &&
                                if (useWebp) {
                                    fileFormat == "WEBP"
                                } else {
                                    fileFormat == "GIF" || fileFormat == "PNG"
                                }
                            ) {
                                urls.add("https:${template}/${fileName}")
                            }
                        }
                    }
                    Emote(
                        name = name,
                        url1x = urls.getOrNull(0) ?: "https:${template}/1x.webp",
                        url2x = urls.getOrNull(1) ?: if (urls.isEmpty()) "https:${template}/2x.webp" else null,
                        url3x = urls.getOrNull(2) ?: if (urls.isEmpty()) "https:${template}/3x.webp" else null,
                        url4x = urls.getOrNull(3) ?: if (urls.isEmpty()) "https:${template}/4x.webp" else null,
                        format = urls.getOrNull(0)?.substringAfterLast(".") ?: "webp",
                        isAnimated = objectData["animated"]?.asPrimitiveOrNullCompat()?.booleanOrNull ?: true,
                        isOverlayEmote = objectData.intOrNullCompat("flags") == 1,
                        source = if (channelSet) Emote.CHANNEL_STV else Emote.PERSONAL_STV,
                    )
                } else null
            } else null
        } else null
    }

    sealed class Cosmetic {
        class Paint(val paint: NamePaint) : Cosmetic()
        class Badge(val badge: STVBadge) : Cosmetic()
    }

    fun parseCosmetic(jsonString: String, useWebp: Boolean): Cosmetic? =
        parseCosmetic(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null, useWebp)

    fun parseCosmetic(body: JsonObject, useWebp: Boolean): Cosmetic? {
        val obj = body["object"]?.asObjectOrNullCompat()
        val kind = obj?.stringOrNullCompat("kind")
        val objectData = obj?.get("data")?.asObjectOrNullCompat()
        if (kind != null && objectData != null) {
            when (kind) {
                "PAINT" -> {
                    val id = objectData.stringOrNullCompat("id")
                    if (id != null) {
                        val function = obj.stringOrNullCompat("function")
                            ?: objectData.stringOrNullCompat("function")
                        val shadows = mutableListOf<NamePaint.Shadow>()
                        val shadowsArray = objectData["shadows"]?.asArrayOrNullCompat()
                        if (shadowsArray != null) {
                            for (i in 0 until shadowsArray.size) {
                                val shadowObject = shadowsArray[i].asObjectOrNullCompat()
                                val xOffset = shadowObject?.doubleOrNullCompat("x_offset")?.toFloat()
                                val yOffset = shadowObject?.doubleOrNullCompat("y_offset")?.toFloat()
                                val radius = shadowObject?.doubleOrNullCompat("radius")?.toFloat()
                                val color = shadowObject?.intOrNullCompat("color")
                                if (xOffset != null && yOffset != null && radius != null && color != null) {
                                    shadows.add(NamePaint.Shadow(xOffset, yOffset, radius, parseRGBAColor(color)))
                                }
                            }
                        }
                        when (function) {
                            "LINEAR_GRADIENT", "RADIAL_GRADIENT" -> {
                                val colors = mutableListOf<Int>()
                                val positions = mutableListOf<Float>()
                                val stopsArray = objectData["stops"]?.asArrayOrNullCompat()
                                if (stopsArray != null) {
                                    for (i in 0 until stopsArray.size) {
                                        val stopObject = stopsArray[i].asObjectOrNullCompat()
                                        val position = stopObject?.doubleOrNullCompat("at")?.toFloat()
                                        val color = stopObject?.intOrNullCompat("color")
                                        if (color != null && position != null) {
                                            colors.add(parseRGBAColor(color))
                                            positions.add(position)
                                        }
                                    }
                                }
                                return Cosmetic.Paint(
                                    NamePaint(
                                        id = id,
                                        type = function,
                                        colors = colors.toIntArray(),
                                        colorPositions = positions.toFloatArray(),
                                        angle = objectData.intOrNullCompat("angle") ?: 0,
                                        repeat = objectData.booleanOrNullCompat("repeat") ?: false,
                                        shadows = shadows,
                                    )
                                )
                            }
                            "URL" -> {
                                val imageUrl = objectData.stringOrNullCompat("image_url")
                                if (imageUrl != null) {
                                    return Cosmetic.Paint(
                                        NamePaint(
                                            id = id,
                                            type = function,
                                            imageUrl = imageUrl,
                                            shadows = shadows,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                "BADGE" -> {
                    val id = objectData.stringOrNullCompat("id")
                    val host = objectData["host"]?.asObjectOrNullCompat()
                    if (id != null && host != null) {
                        val template = host.stringOrNullCompat("url")
                        if (template != null) {
                            val urls = mutableListOf<String>()
                            val files = host["files"]?.asArrayOrNullCompat()
                            if (files != null) {
                                for (i in 0 until files.size) {
                                    val fileObject = files[i].asObjectOrNullCompat()
                                    val fileName = fileObject?.stringOrNullCompat("name")
                                    val fileFormat = fileObject?.stringOrNullCompat("format")
                                    if (fileName != null &&
                                        if (useWebp) {
                                            fileFormat == "WEBP"
                                        } else {
                                            fileFormat == "GIF" || fileFormat == "PNG"
                                        }
                                    ) {
                                        urls.add("https:${template}/${fileName}")
                                    }
                                }
                            }
                            return Cosmetic.Badge(
                                STVBadge(
                                    id = id,
                                    url1x = urls.getOrNull(0) ?: "https:${template}/1x.webp",
                                    url2x = urls.getOrNull(1) ?: if (urls.isEmpty()) "https:${template}/2x.webp" else null,
                                    url3x = urls.getOrNull(2) ?: if (urls.isEmpty()) "https:${template}/3x.webp" else null,
                                    url4x = urls.getOrNull(3) ?: if (urls.isEmpty()) "https:${template}/4x.webp" else null,
                                    name = objectData.stringOrNullCompat("tooltip"),
                                    format = urls.getOrNull(0)?.substringAfterLast(".") ?: "webp",
                                )
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    fun parseRGBAColor(value: Int): Int {
        val a = value and 0xFF
        val r = value shr 24 and 0xFF
        val g = value shr 16 and 0xFF
        val b = value shr 8 and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    sealed class Entitlement {
        class Paint(val userId: String, val paintId: String) : Entitlement()
        class Badge(val userId: String, val badgeId: String) : Entitlement()
        class EmoteSet(val userId: String, val setId: String) : Entitlement()
    }

    fun parseEntitlement(jsonString: String): Entitlement? =
        parseEntitlement(json.parseToJsonElement(jsonString).asObjectOrNullCompat() ?: return null)

    fun parseEntitlement(body: JsonObject): Entitlement? {
        val obj = body["object"]?.asObjectOrNullCompat()
        val kind = obj?.stringOrNullCompat("kind")
        val user = obj?.get("user")?.asObjectOrNullCompat()
        if (kind != null && user != null) {
            var userId: String? = null
            val connections = user["connections"]?.asArrayOrNullCompat()
            if (connections != null) {
                for (i in 0 until connections.size) {
                    val connection = connections[i].asObjectOrNullCompat()
                    if (connection?.stringOrNullCompat("platform") == "TWITCH") {
                        userId = connection.stringOrNullCompat("id")
                        break
                    }
                }
            }
            if (userId != null) {
                when (kind) {
                    "PAINT" -> {
                        val style = user["style"]?.asObjectOrNullCompat()
                        val paintId = style?.stringOrNullCompat("paint_id")
                        if (paintId != null) {
                            return Entitlement.Paint(userId, paintId)
                        }
                    }
                    "BADGE" -> {
                        val style = user["style"]?.asObjectOrNullCompat()
                        val badgeId = style?.stringOrNullCompat("badge_id")
                        if (badgeId != null) {
                            return Entitlement.Badge(userId, badgeId)
                        }
                    }
                    "EMOTE_SET" -> {
                        val refId = obj.stringOrNullCompat("ref_id")
                        if (refId != null) {
                            return Entitlement.EmoteSet(userId, refId)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun JsonObject.doubleOrNullCompat(key: String): Double? =
        (get(key)?.asPrimitiveOrNullCompat())?.doubleOrNull
}
