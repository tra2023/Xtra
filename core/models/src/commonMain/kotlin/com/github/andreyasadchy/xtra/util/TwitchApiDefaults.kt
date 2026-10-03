package com.github.andreyasadchy.xtra.util

/**
 * Twitch constants that used to live on the app-only `TwitchApiHelper`. They are plain values, so
 * they belong with the shared models: `:core` view models need them too (the download dialog's
 * fallback quality list, the player's VOD-domain probing).
 *
 * `TwitchApiHelper` in `:app` delegates here so both stay in sync.
 */
object TwitchApiDefaults {

    val defaultQualityList = listOf(
        "chunked", "1080p60", "1080p30", "720p60", "720p30", "480p30", "360p30", "160p30",
        "144p30", "high", "medium", "low", "mobile", "audio_only",
    )

    val vodDomains = listOf(
        "https://vod-secure.twitch.tv",
        "https://vod-metro.twitch.tv",
        "https://vod-pop-secure.twitch.tv",
        "https://d2e2de1etea730.cloudfront.net",
        "https://dqrpb9wgowsf5.cloudfront.net",
        "https://ds0h3roq6wcgc.cloudfront.net",
        "https://d2nvs31859zcd8.cloudfront.net",
        "https://d2aba1wr3818hz.cloudfront.net",
        "https://d3c27h4odz752x.cloudfront.net",
        "https://dgeft87wbj63p.cloudfront.net",
        "https://d1m7jfoe9zdc1j.cloudfront.net",
        "https://d3vd9lfkzbru3h.cloudfront.net",
        "https://d2vjef5jvl6bfs.cloudfront.net",
        "https://d1ymi26ma8va5x.cloudfront.net",
        "https://d1mhjrowxxagfy.cloudfront.net",
        "https://ddacn6pr5v0tl.cloudfront.net",
        "https://d3aqoihi2n8ty8.cloudfront.net",
        "https://d3fi1amfgojobc.cloudfront.net",
        "https://d3stzm2eumvgb4.cloudfront.net",
        "https://d2vi6trrdongqn.cloudfront.net",
        "https://d1ndex63qxojbr.cloudfront.net",
    )
}
