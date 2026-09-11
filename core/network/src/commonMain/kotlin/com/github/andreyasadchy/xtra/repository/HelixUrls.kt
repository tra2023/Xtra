package com.github.andreyasadchy.xtra.repository

/**
 * Pure-Kotlin replacement for `androidx.core.net.toUri().buildUpon()`.
 * Percent-encodes query keys/values as UTF-8 (RFC 3986 unreserved set left as-is).
 */
object HelixUrls {

    fun build(base: String, params: Builder.() -> Unit = {}): String {
        val builder = Builder().apply(params)
        if (builder.pairs.isEmpty()) return base
        return buildString {
            append(base)
            append('?')
            builder.pairs.forEachIndexed { index, (key, value) ->
                if (index > 0) append('&')
                append(encode(key))
                append('=')
                append(encode(value))
            }
        }
    }

    class Builder {
        internal val pairs = mutableListOf<Pair<String, String>>()

        fun param(key: String, value: String?) {
            if (value != null) pairs.add(key to value)
        }

        fun param(key: String, value: Int?) {
            if (value != null) pairs.add(key to value.toString())
        }

        fun param(key: String, value: Boolean?) {
            if (value != null) pairs.add(key to value.toString())
        }

        fun params(key: String, values: List<String>?) {
            values?.forEach { pairs.add(key to it) }
        }
    }

    internal fun encode(value: String): String {
        val bytes = value.encodeToByteArray()
        var result: StringBuilder? = null
        var lastUnencoded = 0
        for (i in bytes.indices) {
            val c = bytes[i].toInt() and 0xFF
            if (!isUnreserved(c)) {
                if (result == null) {
                    result = StringBuilder(value.length + 16)
                }
                for (j in lastUnencoded until i) {
                    result.append((bytes[j].toInt() and 0xFF).toChar())
                }
                result.append('%')
                result.append(HEX[c shr 4])
                result.append(HEX[c and 0x0F])
                lastUnencoded = i + 1
            }
        }
        if (result == null) return value
        for (j in lastUnencoded until bytes.size) {
            result.append((bytes[j].toInt() and 0xFF).toChar())
        }
        return result.toString()
    }

    private fun isUnreserved(c: Int): Boolean {
        return c in 'a'.code..'z'.code ||
            c in 'A'.code..'Z'.code ||
            c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
    }

    private const val HEX = "0123456789ABCDEF"
}
