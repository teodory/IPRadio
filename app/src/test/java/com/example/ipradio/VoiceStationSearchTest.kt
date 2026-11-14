package com.example.ipradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStationSearchTest {
    private val stations = listOf(
        Station("zrock", "ZRock", "https://example.org/zrock"),
        Station("fmplus", "FM+", "https://example.org/fmplus"),
        Station("radio-antena", "Радио Антена", "https://example.org/antena"),
        Station("magic-fm", "Magic FM", "https://example.org/magic")
    )

    @Test
    fun matchesSpokenStationNamesFromAndroidAuto() {
        assertEquals("fmplus", findStations(stations, "FM Plus").first().id)
        assertEquals("zrock", findStations(stations, "Zirock").first().id)
        assertEquals("zrock", findStations(stations, "Z Rock").first().id)
        assertEquals("radio-antena", findStations(stations, "Radio Antena").first().id)
    }

    @Test
    fun exactStationNameWinsOverVoiceAlias() {
        val customStation = Station("custom", "FM Plus", "https://example.org/custom")
        assertEquals("custom", findStations(stations + customStation, "FM Plus").first().id)
    }

    @Test
    fun retainsPartialNameSearchWithoutGuessingUnknownStations() {
        assertEquals("magic-fm", findStations(stations, "Magic").first().id)
        assertTrue(findStations(stations, "Zero Radio").isEmpty())
    }
}
