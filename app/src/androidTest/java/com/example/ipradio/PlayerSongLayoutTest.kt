package com.example.ipradio

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ipradio.ui.theme.IPRadioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerSongLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactPlayerShowsArtistAboveSongTitle() = checkPlayer(big = false)

    @Test fun largePlayerShowsArtistAboveSongTitle() = checkPlayer(big = true)

    private fun checkPlayer(big: Boolean) {
        val station = defaultStations.first { it.id == "melody" }
        compose.setContent {
            IPRadioTheme(darkTheme = true) {
                RadioScreen(
                    stations = listOf(station),
                    allStations = listOf(station),
                    playback = PlaybackUiState(
                        connected = true,
                        stationId = station.id,
                        active = true,
                        playing = true,
                        songArtist = "ROBIN THICKE",
                        songTitle = "BLURRED LINES"
                    ),
                    editor = StationEditorState(),
                    musicBrainzEnabled = false,
                    onMusicBrainzChanged = {},
                    bigPlayerEnabled = big,
                    onBigPlayerChanged = {},
                    darkModeEnabled = true,
                    onDarkModeChanged = {},
                    onPlayStation = {},
                    onPrevious = {},
                    onNext = {},
                    onStop = {},
                    saveStation = { _, _, _, _, _, _, _ -> Result.success(station) },
                    setStationEnabled = { _, _ -> Result.success(false) },
                    moveStation = { _, _ -> Result.success(false) }
                )
            }
        }

        val artist = compose.onNodeWithText("ROBIN THICKE", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("BLURRED LINES", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("Artist must be on a line above the song title", artist.bottom <= title.top)
    }
}
