package com.example.ipradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongDataParserTest {
    @Test
    fun parsesEachStationFormat() {
        assertEquals("SCORPIONS - NO PAIN NO GAIN ( LIVE )", parseSongTitle(MetadataFormat.ZROCK,
            """{"main_slider":{"now":{"title":"NO PAIN NO GAIN ( LIVE )","artist":"SCORPIONS"}}}"""))
        assertEquals("U2 - EVEN BETTER THE REAL THING", parseSongTitle(MetadataFormat.METACAST,
            "\uFEFF" + """{"nowplaying":[{"artist":"U2","title":"EVEN BETTER THE REAL THING","status":"playing"},{"artist":"IRON MAIDEN","title":"THE NUMBER OF THE BEAST","status":"history"}]}"""))
        assertEquals("Justin Timberlake - CRY ME A RIVER", parseSongTitle(MetadataFormat.MAGIC,
            """{"performer":"Justin Timberlake","name":"CRY ME A RIVER"}"""))
        assertEquals("MIKE OLDFIELD - MOONLIGHT SHADOW", parseSongTitle(MetadataFormat.FLUMOTION,
            """{"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"MIKE OLDFIELD\", \"title\": \"MOONLIGHT SHADOW\"}"}%"""))
        assertEquals("Robert Palmer - Every Kinda People", parseSongTitle(MetadataFormat.PLAIN_TEXT,
            " Robert Palmer - Every Kinda People\n"))
    }

    @Test
    fun keepsSongTitleWhenArtistIsMissing() {
        assertEquals("A \"quoted\" title", parseSongTitle(MetadataFormat.MAGIC,
            """{"performer":null,"name":"A \"quoted\" title"}"""))
    }

    @Test
    fun discardsMalformedEmptyAndHtmlPayloads() {
        assertNull(parseSongTitle(MetadataFormat.METACAST, """{"nowplaying":[]}"""))
        assertNull(parseSongTitle(MetadataFormat.ZROCK, """{"main_slider":[]}"""))
        assertNull(parseSongTitle(MetadataFormat.FLUMOTION, """{"value":"invalid JSON"}"""))
        assertNull(parseSongTitle(MetadataFormat.MAGIC, """{"performer":null,"name":" null "}"""))
        assertNull(parseSongTitle(MetadataFormat.PLAIN_TEXT, "null - null"))
        assertNull(parseSongTitle(MetadataFormat.PLAIN_TEXT, "<!DOCTYPE html><p>Error - Try again</p>"))
        assertNull(parseSongTitle(MetadataFormat.MAGIC, """{"name":"<p>Unavailable</p>"}"""))
    }

    @Test
    fun discardsJsonWithExcessiveNesting() {
        val payload = """{"nested":""" + "[".repeat(10_000) + "0" + "]".repeat(10_000) + "}"
        assertNull(parseSongTitle(MetadataFormat.MAGIC, payload))
    }
}
