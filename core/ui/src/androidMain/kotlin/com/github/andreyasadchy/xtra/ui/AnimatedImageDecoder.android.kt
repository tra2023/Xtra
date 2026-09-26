package com.github.andreyasadchy.xtra.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.ScaleDrawable
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.nio.ByteBuffer

private val GIF_87A = "GIF87a".encodeUtf8()
private val GIF_89A = "GIF89a".encodeUtf8()
private val WEBP_RIFF = "RIFF".encodeUtf8()
private val WEBP_WEBP = "WEBP".encodeUtf8()
private val WEBP_VP8X = "VP8X".encodeUtf8()

actual fun xtraAnimatedImageDecoderFactory(): Decoder.Factory? =
    XtraAnimatedImageDecoder.Factory()

/**
 * Decodes animated GIFs and animated WebPs into an [AnimatedImageDrawable] using the
 * platform [ImageDecoder] (API 28+). Unlike `coil-gif` this lives behind an `expect/actual`
 * factory so only the Android target links the implementation.
 */
private class XtraAnimatedImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val drawable = runInterruptible {
            val bytes = source.source().readByteArray()
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(buffer))
        }
        val image = if (drawable is AnimatedImageDrawable) {
            ScaleDrawable(drawable, options.scale).asImage()
        } else {
            drawable.asImage()
        }
        return DecodeResult(image = image, isSampled = false)
    }

    class Factory : Decoder.Factory {

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (options.disableAnimatedEmotes) return null
            if (!result.source.source().isAnimatedImage()) return null
            return XtraAnimatedImageDecoder(result.source, options)
        }
    }
}

private fun BufferedSource.isAnimatedImage(): Boolean = isGif() || isAnimatedWebP()

private fun BufferedSource.isGif(): Boolean =
    rangeEquals(0, GIF_89A) || rangeEquals(0, GIF_87A)

private fun BufferedSource.isAnimatedWebP(): Boolean =
    rangeEquals(0, WEBP_RIFF) &&
        rangeEquals(8, WEBP_WEBP) &&
        rangeEquals(12, WEBP_VP8X) &&
        request(21) &&
        (buffer[20].toInt() and 0b10) != 0
