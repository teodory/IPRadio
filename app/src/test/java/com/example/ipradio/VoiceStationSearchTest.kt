package com.example.ipradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStationSearchTest {
    private val stations = defaultStations

    @Test
    fun matchesSpokenNamesForEveryBuiltInStation() {
        val spokenNames = mapOf(
            "zrock" to listOf("Zirock", "Зи Рок", "Зет Рок"),
            "1rock" to listOf("Radio 1 Rock", "One Rock", "Радио 1 Рок"),
            "darik" to listOf("Darik Radio", "Радио Дарик", "Дарик"),
            "fmplus" to listOf("FM Plus", "FM Plus Radio", "ФМ Плюс"),
            "magic-fm" to listOf("Magic Radio", "Меджик ФМ", "Радио Меджик"),
            "tangra-mega-rock" to listOf("Radio Tangra", "Тангра Мега Рок", "Радио Тангра"),
            "rockfm" to listOf("Rock FM", "Рок ФМ", "Радио Рок ФМ"),
            "cadena100" to listOf("Cadena 100", "Cadena Cien", "Кадена сто"),
            "soft-rock-radio" to listOf("Soft Rock Radio", "Soft Rock", "Софт Рок Радио"),
            "melody" to listOf("Radio Melody", "Мелъди", "Радио Мелоди"),
            "radio-antena" to listOf("Radio Antena", "Antena Radio", "Антена"),
            "btv-radio" to listOf("B TV Radio", "БТВ Радио", "Би Ти Ви Радио"),
            "radio1" to listOf("Radio One", "Радио 1", "Радио Едно", "Рейдио Уан"),
            "radio-energy" to listOf("Energy", "NRJ", "Радио Енерджи", "Ен Ар Джей"),
            "radio-paradise-rock" to listOf("Paradise Rock", "Radio Paradise Rock", "Радио Парадайз Рок", "Радио Парадайс Рок"),
            "fluxfm" to listOf("Flux FM", "Радио Флукс", "Флукс ФМ", "Флакс ФМ"),
            "radio-swiss-pop" to listOf("Swiss Pop", "Радио Суис Поп", "Радио Свис Поп", "Швейцарско Поп Радио")
        )
        assertEquals(stations.map { it.id }.toSet(), spokenNames.keys)
        spokenNames.forEach { (stationId, queries) ->
            queries.forEach { query ->
                assertEquals(query, stationId, findStations(stations, query).firstOrNull()?.id)
            }
        }
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

    @Test
    fun normalizedNameSearchAlsoWorksForCustomStations() {
        val customStation = Station("custom", "My Cool Radio", "https://example.org/custom")
        assertEquals("custom", findStations(stations + customStation, "MyCoolRadio").first().id)
    }
}
