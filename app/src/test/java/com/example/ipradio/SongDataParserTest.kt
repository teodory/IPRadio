package com.example.ipradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongDataParserTest {
    @Test
    fun extractsStationArtworkAndKeepsArtistSeparate() {
        val song = parseSong(MetadataFormat.METACAST,
            """{"nowplaying":[{"artist":"Travis","title":"Side","imageUrl":"https://example.org/side.jpg"}]}""")
        assertEquals(SongInfo("Side", "Travis", "https://example.org/side.jpg"), song)
    }

    @Test
    fun ignoresTheGenericMagicImage() {
        val song = parseSong(MetadataFormat.MAGIC,
            """{"performer":"Boyzone","name":"Picture Of You","img":"https://m.netinfo.bg/magic/images/default.svg"}""",
            "https://www.magic.bg/magic/song.php")
        assertEquals(SongInfo("Picture Of You", "Boyzone"), song)
    }

    @Test
    fun splitsStreamTitlesWithoutLosingHyphensInTheSongName() {
        assertEquals(SongInfo("Forty-Six & 2 - Live", "Tool"),
            parseStreamSong(" Tool - Forty-Six & 2 - Live "))
    }

    @Test
    fun readsZRockImportedTitlesAndIgnoresStationPlaceholder() {
        val song = parseSong(
            MetadataFormat.ZROCK,
            """{"main_slider":{"now":{"title":"\ufeffEVANESCENCE","artist":"MY HEART IS BROKEN",
                "image":"/static/bg/microsites/zrock/img/music_top.jpg"}}}""",
            "https://zrock.bg/lbin/zrock/refresh_song_ajax.php?view=desktop"
        )
        assertEquals(SongInfo("MY HEART IS BROKEN", "EVANESCENCE"), song)
    }

    @Test
    fun parsesEachStationFormat() {
        assertEquals("SCORPIONS - NO PAIN NO GAIN ( LIVE )", parseSong(MetadataFormat.ZROCK,
            """{"main_slider":{"now":{"title":"NO PAIN NO GAIN ( LIVE )","artist":"SCORPIONS"}}}""")?.displayTitle)
        assertEquals("U2 - EVEN BETTER THE REAL THING", parseSong(MetadataFormat.METACAST,
            "\uFEFF" + """{"nowplaying":[{"artist":"U2","title":"EVEN BETTER THE REAL THING","status":"playing"},{"artist":"IRON MAIDEN","title":"THE NUMBER OF THE BEAST","status":"history"}]}""")?.displayTitle)
        assertEquals("Justin Timberlake - CRY ME A RIVER", parseSong(MetadataFormat.MAGIC,
            """{"performer":"Justin Timberlake","name":"CRY ME A RIVER"}""")?.displayTitle)
        assertEquals("MIKE OLDFIELD - MOONLIGHT SHADOW", parseSong(MetadataFormat.FLUMOTION,
            """{"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"MIKE OLDFIELD\", \"title\": \"MOONLIGHT SHADOW\"}"}%""")?.displayTitle)
        assertEquals("Robert Palmer - Every Kinda People", parseSong(MetadataFormat.PLAIN_TEXT,
            " Robert Palmer - Every Kinda People\n")?.displayTitle)
    }

    @Test
    fun keepsSongTitleWhenArtistIsMissing() {
        assertEquals("A \"quoted\" title", parseSong(MetadataFormat.MAGIC,
            """{"performer":null,"name":"A \"quoted\" title"}""")?.displayTitle)
    }

    @Test
    fun discardsMalformedEmptyAndHtmlPayloads() {
        assertNull(parseSong(MetadataFormat.METACAST, """{"nowplaying":[]}"""))
        assertNull(parseSong(MetadataFormat.ZROCK, """{"main_slider":[]}"""))
        assertNull(parseSong(MetadataFormat.FLUMOTION, """{"value":"invalid JSON"}"""))
        assertNull(parseSong(MetadataFormat.MAGIC, """{"performer":null,"name":" null "}"""))
        assertNull(parseSong(MetadataFormat.PLAIN_TEXT, "null - null"))
        assertNull(parseSong(MetadataFormat.PLAIN_TEXT, "<!DOCTYPE html><p>Error - Try again</p>"))
        assertNull(parseSong(MetadataFormat.MAGIC, """{"name":"<p>Unavailable</p>"}"""))
    }

    @Test
    fun discardsJsonWithExcessiveNesting() {
        val payload = """{"nested":""" + "[".repeat(10_000) + "0" + "]".repeat(10_000) + "}"
        assertNull(parseSong(MetadataFormat.MAGIC, payload))
    }
}
