package com.github.andreyasadchy.xtra.util.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asObjectOrNullCompat(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNullCompat(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asPrimitiveOrNullCompat(): JsonPrimitive? = this as? JsonPrimitive

internal fun JsonElement?.stringContentOrNullCompat(): String? {
    val p = this as? JsonPrimitive ?: return null
    return if (p.isString) {
        try {
            p.content
        } catch (e: Exception) {
            null
        }
    } else null
}

internal fun JsonObject.stringOrNullCompat(key: String): String? =
    get(key)?.stringContentOrNullCompat()?.takeIf { it.isNotBlank() }

internal fun JsonObject.intOrNullCompat(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull

internal fun JsonObject.longOrNullCompat(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

internal fun JsonObject.booleanOrNullCompat(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.doubleOrNullCompat(key: String): Double? =
    (get(key) as? JsonPrimitive)?.doubleOrNull
