package com.example.ipradio

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val image: String = "",
    val logo: Int = 0,
    val infoDataUrl: String = "",
    val metadataFormat: MetadataFormat? = null
)

enum class MetadataFormat { ZROCK, METACAST, MAGIC, FLUMOTION, PLAIN_TEXT }

internal const val RADIO_ANTENA_STREAM_URL = "https://live.radioantena.bg/RadioAntena"
private const val ZROCK_STREAM_URL = "https://web.static.btv.bg/radio/zrock-radio-proxy/"
private const val ZROCK_SONG_URL = "https://zrock.bg/lbin/zrock/refresh_song_ajax.php?view=desktop"

internal val defaultStations = listOf(
    Station(
        "zrock", "ZRock",
        ZROCK_STREAM_URL,
        "https://zrock.bg/static/bg/microsites/zrock/img/logo.png",
        logo = R.drawable.station_zrock,
        metadataFormat = MetadataFormat.ZROCK
    ),
    Station(
        "1rock", "1Rock",
        "http://149.13.0.81/radio1rock.ogg",
        "https://www.radio1rock.bg/theme_assets/radio1rock/images/logo.png?v=0",
        logo = R.drawable.station_rock1,
        infoDataUrl = "http://meta.metacast.eu/aim/?radio=radio1rock",
        metadataFormat = MetadataFormat.METACAST
    ),
    Station(
        "darik", "Darik",
        "https://a12.asurahosting.com/listen/darik_radio/radio.mp3",
        "https://darikradio.bg/img/logo.new.png",
        logo = R.drawable.station_darik
    ),
    Station(
        "fmplus", "FM+",
        "https://streamer2.atlantis.bg:8443/fmplusnew",
        "https://radiofmplus.bg/i/logo.png",
        logo = R.drawable.station_fmplus
    ),
    Station(
        "magic-fm", "Magic FM",
        "https://bss1.neterra.tv/magicfm/magicfm.m3u8",
        "https://m.netinfo.bg/media/images/50346/50346532/orig-orig-magic-tv-radio.jpg",
        logo = R.drawable.station_magic,
        infoDataUrl = "https://www.magic.bg/magic/song.php",
        metadataFormat = MetadataFormat.MAGIC
    ),
    Station(
        "tangra-mega-rock", "Tangra Mega Rock",
        "http://stream-bg-01.radiotangra.com:8000/Tangra-high",
        "https://radiotangra.com/wp-content/uploads/2023/02/Vector-Smart-Object.svg",
        logo = R.drawable.station_tangra
    ),
    Station(
        "rockfm", "RockFM",
        "https://rockfm-cope.flumotion.com/playlist.m3u8",
        "https://static.mytuner.mobi/media/tvos_radios/7WH7nKXkwa.png",
        logo = R.drawable.station_rockfm,
        infoDataUrl = "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=78",
        metadataFormat = MetadataFormat.FLUMOTION
    ),
    Station(
        "cadena100", "Cadena100",
        "https://cadena100-cope.flumotion.com/chunks.m3u8",
        "https://www.cadena100.es/estaticos/apple-touch-icon-192x192.png",
        logo = R.drawable.station_cadena100,
        infoDataUrl = "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=76",
        metadataFormat = MetadataFormat.FLUMOTION
    ),
    Station(
        "soft-rock-radio", "SoftRockRadio.net",
        "https://gold.streamguys1.com/softrock.mp3",
        "https://cdn-radiotime-logos.tunein.com/s113401d.png",
        logo = R.drawable.station_softrock,
        infoDataUrl = "https://status.rcast.net/70632",
        metadataFormat = MetadataFormat.PLAIN_TEXT
    ),
    Station(
        "melody", "Melody",
        "http://193.108.24.6:8000/melody?file=.mp3",
        "https://radiomelody.bg/i/logo.png",
        logo = R.drawable.station_melody
    ),
    Station(
        "radio-antena", "Радио Антена",
        RADIO_ANTENA_STREAM_URL,
        "https://radioantena.bg/radio-antena-logo.jpg",
        logo = R.drawable.station_antena
    ),
    Station(
        "btv-radio", "bTV Radio",
        "https://cdn.bweb.bg/radio/btv-radio.mp3",
        "https://btvnovinite.bg/static/bg/microsites/btvnovinite/img/logos/btv_radio.png",
        logo = R.drawable.station_btv_radio
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
