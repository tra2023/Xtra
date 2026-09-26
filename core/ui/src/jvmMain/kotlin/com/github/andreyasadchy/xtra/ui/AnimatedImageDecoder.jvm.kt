package com.github.andreyasadchy.xtra.ui

import coil3.decode.Decoder

/**
 * Desktop has no animated-image decoder wired up yet: Coil's Skia decoder yields only the
 * first frame. Returning `null` keeps the shared image loader building on JVM while Android
 * gets real animated-emote support.
 */
actual fun xtraAnimatedImageDecoderFactory(): Decoder.Factory? = null
