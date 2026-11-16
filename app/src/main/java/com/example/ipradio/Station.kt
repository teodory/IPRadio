package com.example.ipradio

import kotlin.math.pow

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val image: String = "",
    val logo: Int = 0,
    val infoDataUrl: String = "",
    val metadataFormat: MetadataFormat? = null,
    val enabled: Boolean = true,
    val gainDb: Int = 0,
    val countryCode: String? = null,
    val shortName: String = name.toShortStationName()
)

internal const val MAX_STATION_SHORT_NAME_LENGTH = 16

internal fun String.toShortStationName(): String {
    val name = lineSequence().joinToString(" ").trim()
    if (name.codePointCount(0, name.length) <= MAX_STATION_SHORT_NAME_LENGTH) return name

    val end = name.offsetByCodePoints(0, MAX_STATION_SHORT_NAME_LENGTH)
    val prefix = name.substring(0, end)
    if (name[end].isWhitespace() || prefix.last().isWhitespace()) return prefix.trimEnd()
    return prefix.substringBeforeLast(' ', prefix).trimEnd()
}

internal fun Station.androidAutoPlaybackTitle(artist: String?): String =
    if (artist.isNullOrBlank()) shortName else "$shortName\n$artist"

internal fun Station.displayName(forAndroidAuto: Boolean = false): String {
    val code = countryCode ?: return name
    if (forAndroidAuto) return "$name ($code)"
    val flag = code.map { Character.toChars(0x1F1E6 + it.code - 'A'.code).concatToString() }.joinToString("")
    return "$name $flag"
}

enum class MetadataFormat { ZROCK, METACAST, MAGIC, FLUMOTION, PLAIN_TEXT }

internal fun playerVolumeForGain(gainDb: Int): Float =
    if (gainDb >= 0) 1f else 10.0.pow(gainDb / 20.0).toFloat()

internal const val RADIO_ANTENA_STREAM_URL = "https://live.radioantena.bg/RadioAntena"
private const val ZROCK_STREAM_URL = "https://web.static.btv.bg/radio/zrock-radio-proxy/"
private const val ZROCK_SONG_URL = "https://zrock.bg/lbin/zrock/refresh_song_ajax.php?view=desktop"

internal val defaultStations = listOf(
    Station(
        "zrock", "ZRock",
        ZROCK_STREAM_URL,
        "https://zrock.bg/static/bg/microsites/zrock/img/logo.png",
        logo = R.drawable.station_zrock,
        metadataFormat = MetadataFormat.ZROCK,
        countryCode = "BG"
    ),
    Station(
        "1rock", "1Rock",
        "http://149.13.0.81/radio1rock.ogg",
        "https://www.radio1rock.bg/theme_assets/radio1rock/images/logo.png?v=0",
        logo = R.drawable.station_rock1,
        infoDataUrl = "http://meta.metacast.eu/aim/?radio=radio1rock",
        metadataFormat = MetadataFormat.METACAST,
        countryCode = "BG"
    ),
    Station(
        "darik", "Darik",
        "https://a12.asurahosting.com/listen/darik_radio/radio.mp3",
        "https://darikradio.bg/img/logo.new.png",
        logo = R.drawable.station_darik,
        countryCode = "BG"
    ),
    Station(
        "fmplus", "FM+",
        "https://streamer2.atlantis.bg:8443/fmplusnew",
        "https://radiofmplus.bg/i/logo.png",
        logo = R.drawable.station_fmplus,
        countryCode = "BG"
    ),
    Station(
        "magic-fm", "Magic FM",
        "https://bss1.neterra.tv/magicfm/magicfm.m3u8",
        "https://m.netinfo.bg/media/images/50346/50346532/orig-orig-magic-tv-radio.jpg",
        logo = R.drawable.station_magic,
        infoDataUrl = "https://www.magic.bg/magic/song.php",
        metadataFormat = MetadataFormat.MAGIC,
        countryCode = "BG"
    ),
    Station(
        "tangra-mega-rock", "Tangra Mega Rock",
        "http://stream-bg-01.radiotangra.com:8000/Tangra-high",
        "https://radiotangra.com/wp-content/uploads/2023/02/Vector-Smart-Object.svg",
        logo = R.drawable.station_tangra,
        countryCode = "BG",
        shortName = "Tangra Rock"
    ),
    Station(
        "rockfm", "RockFM",
        "https://rockfm-cope.flumotion.com/playlist.m3u8",
        "https://static.mytuner.mobi/media/tvos_radios/7WH7nKXkwa.png",
        logo = R.drawable.station_rockfm,
        infoDataUrl = "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=78",
        metadataFormat = MetadataFormat.FLUMOTION,
        countryCode = "ES"
    ),
    Station(
        "cadena100", "Cadena100",
        "https://cadena100-cope.flumotion.com/chunks.m3u8",
        "https://www.cadena100.es/estaticos/apple-touch-icon-192x192.png",
        logo = R.drawable.station_cadena100,
        infoDataUrl = "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=76",
        metadataFormat = MetadataFormat.FLUMOTION,
        countryCode = "ES"
    ),
    Station(
        "soft-rock-radio", "SoftRockRadio.net",
        "https://gold.streamguys1.com/softrock.mp3",
        "https://cdn-radiotime-logos.tunein.com/s113401d.png",
        logo = R.drawable.station_softrock,
        infoDataUrl = "https://status.rcast.net/70632",
        metadataFormat = MetadataFormat.PLAIN_TEXT,
        countryCode = "US",
        shortName = "Soft Rock Radio"
    ),
    Station(
        "melody", "Melody",
        "http://193.108.24.6:8000/melody?file=.mp3",
        "https://radiomelody.bg/i/logo.png",
        logo = R.drawable.station_melody,
        countryCode = "BG"
    ),
    Station(
        "radio-antena", "Радио Антена",
        RADIO_ANTENA_STREAM_URL,
        "https://radioantena.bg/radio-antena-logo.jpg",
        logo = R.drawable.station_antena,
        countryCode = "BG"
    ),
    Station(
        "btv-radio", "bTV Radio",
        "https://cdn.bweb.bg/radio/btv-radio.mp3",
        "https://btvnovinite.bg/static/bg/microsites/btvnovinite/img/logos/btv_radio.png",
        logo = R.drawable.station_btv_radio,
        countryCode = "BG"
    ),
    Station(
        "radio1", "Radio 1",
        "https://play.global.audio/radio1128?dist=WEBSITEBG",
        "https://media.metacast.eu/pictures/11_2015/k1RAhBlx0YU67dP5uVOc-7898.png",
        logo = R.drawable.station_radio1,
        infoDataUrl = "http://meta.metacast.eu/aim/?radio=radio1",
        metadataFormat = MetadataFormat.METACAST,
        countryCode = "BG"
    ),
    Station(
        "radio-energy", "Radio ENERGY",
        "https://playerservices.streamtheworld.com/api/livestream-redirect/RADIO_ENERGYAAC_H.aac?dist=WEBSITEBG",
        "https://media.metacast.eu/pictures/10_2015/OdriLYWpbHJqKDAcGCMv-7384.png",
        logo = R.drawable.station_energy,
        infoDataUrl = "http://meta.metacast.eu/aim/?radio=radioenergy",
        metadataFormat = MetadataFormat.METACAST,
        countryCode = "BG"
    ),
    Station(
        "radio-paradise-rock", "Radio Paradise Rock Mix",
        "https://stream.radioparadise.com/rock-128",
        "https://radioparadise.com/apple-touch-icon.png",
        logo = R.drawable.station_paradise_rock,
        countryCode = "US",
        shortName = "Paradise Rock"
    ),
    Station(
        "fluxfm", "FluxFM",
        "https://channels.fluxfm.de/FluxFM/externalembedflxhp/stream.mp3",
        "https://www.fluxfm.de/assets/favicons/android-icon-192x192.png",
        logo = R.drawable.station_fluxfm,
        countryCode = "DE"
    ),
    Station(
        "radio-swiss-pop", "Radio Swiss Pop",
        "https://stream.srg-ssr.ch/srgssr/rsp/aac/192",
        "https://www.radioswisspop.ch/social-media/rsp-web.png",
        logo = R.drawable.station_swiss_pop,
        countryCode = "CH",
        shortName = "Swiss Pop"
    ),
    Station(
        "choice-classic-rock", "Choice Classic Rock",
        "https://streamer.radio.co/s66f2626b4/listen",
        "https://www.choiceclassicrock.com/wp-content/uploads/2020/08/Choice-Classic-Rock-flaming-guitar-300-x-300.jpg",
        logo = R.drawable.station_choice_classic_rock,
        countryCode = "CA",
        shortName = "Choice Rock"
    ),
    Station(
        "radio-paradise-main", "Radio Paradise Main Mix",
        "https://stream.radioparadise.com/mp3-192",
        "https://radioparadise.com/apple-touch-icon.png",
        logo = R.drawable.station_paradise_rock,
        countryCode = "US",
        shortName = "Paradise Main"
    ),
    Station(
        "muzaica-radio", "Muzaica Radio",
        "https://radio.muzaica.fm:9000/stream",
        "https://muzaica.fm/wp-content/uploads/2025/01/cropped-logo-2.png",
        logo = R.drawable.station_muzaica,
        countryCode = "BG"
    ),
    Station(
        "rainbow-89", "89 Rainbow",
        "https://stream.radiojar.com/083wqknmsuhvv",
        "https://89rainbow.gr/wp-content/uploads/2022/11/cropped-rainbow89-192x192.webp",
        logo = R.drawable.station_rainbow,
        countryCode = "GR"
    ),
    Station(
        "rainbow-alternative", "Rainbow Alternative",
        "https://stream.radiojar.com/vhknc3c8ktuvv",
        "https://89rainbow.gr/wp-content/uploads/2023/08/02.jpg",
        logo = R.drawable.station_rainbow_alternative,
        countryCode = "GR",
        shortName = "Rainbow Alt"
    ),
    Station(
        "rainbow-pop", "Rainbow Pop",
        "https://stream.radiojar.com/ztqh29c8ktuvv",
        "https://89rainbow.gr/wp-content/uploads/2023/08/02-Pop-orange.png",
        logo = R.drawable.station_rainbow_pop,
        countryCode = "GR"
    )
)

internal val defaultStationsById = defaultStations.associateBy(Station::id)

internal val Station.customArtworkUrl: String?
    get() = image.takeIf { it.isNotEmpty() && it != defaultStationsById[id]?.image }

internal val Station.songDataUrl: String
    get() = infoDataUrl.ifBlank {
        ZROCK_SONG_URL.takeIf { id == "zrock" && url == ZROCK_STREAM_URL }.orEmpty()
    }

internal fun adjacentStation(stations: List<Station>, currentId: String?, offset: Int): Station? {
    val currentIndex = stations.indexOfFirst { it.id == currentId }
    if (currentIndex == -1) return stations.firstOrNull()
    val index = Math.floorMod(currentIndex.toLong() + offset, stations.size.toLong())
    return stations[index.toInt()]
}
