package com.example.ipradio

import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking

class RadioEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun blankStationKeepsEditorOpen() {
        openEditor()
        compose.onNodeWithText("Add").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Enter a station name.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Enter a station name.").assertIsDisplayed()
        compose.onNodeWithText("Add New Radio").assertIsDisplayed()
    }

    @Test
    fun stationDraftSurvivesActivityRecreation() {
        openEditor()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Draft station")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Draft station").assertIsDisplayed()
        compose.onNodeWithText("Add New Radio").assertIsDisplayed()
    }

    @Test
    fun editorSavesAndRestoresStationVolume() {
        val catalog = StationCatalog.getInstance(compose.activity)
        val original = catalog.allStations.value.first { it.id == "zrock" }
        try {
            enterEditMode()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.edit_radio, original.name))
                .performClick()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.station_gain))
                .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) {
                catalog.allStations.value.first { it.id == original.id }.gainDb == 6
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.edit_radio, original.name))
                .performClick()
            compose.onNodeWithText("+6 dB").assertIsDisplayed()
        } finally {
            runBlocking {
                catalog.save(original.id, original.name, original.url, original.image,
                    original.infoDataUrl, original.enabled, original.gainDb).getOrThrow()
            }
        }
    }

    @Test
    fun fullScreenSettingsOpensStationEditing() {
        val settings = compose.onNodeWithContentDescription("Settings")
        val stations = compose.onNodeWithText("Stations")
        val add = compose.onNodeWithContentDescription("Add Radio")
        settings.performClick()
        stations.assertDoesNotExist()
        compose.onNodeWithText("Settings").assertIsDisplayed()

        compose.onNodeWithText("Edit stations").performClick()
        stations.assertIsDisplayed()
        add.assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit ZRock").assertIsDisplayed()

        compose.onNodeWithContentDescription("Done editing").performClick()
        add.assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit stations").assertDoesNotExist()
        settings.assertIsDisplayed()
    }

    @Test
    fun draggingAStationChangesAndSavesItsPosition() {
        val catalog = StationCatalog.getInstance(compose.activity)
        val initial = catalog.allStations.value
        val station = initial.first()
        try {
            enterEditMode()
            compose.onNodeWithContentDescription("Drag ${station.name}", useUnmergedTree = true).performTouchInput {
                swipe(center, center.copy(y = center.y + 360f), durationMillis = 700)
            }
            compose.waitUntil(5_000) {
                catalog.allStations.value.indexOfFirst { it.id == station.id } > 0
            }
            assertEquals(catalog.allStations.value, StationCatalog(compose.activity.filesDir.resolve("stations.json")).allStations.value)
        } finally {
            runBlocking {
                val index = catalog.allStations.value.indexOfFirst { it.id == station.id }
                if (index > 0) catalog.move(station.id, -index).getOrThrow()
            }
        }
    }

    @Test
    fun stationCanBeDisabledAndRestoredFromTheEditList() {
        val catalog = StationCatalog.getInstance(compose.activity)
        val original = catalog.allStations.value.first { it.id == "zrock" }
        val originalIndex = catalog.allStations.value.indexOfFirst { it.id == original.id }
        val editLabel = compose.activity.getString(R.string.edit_radio, original.name)
        val stationLabel = original.displayName()
        val enabledLabel = compose.activity.getString(R.string.station_enabled)
        val disabledLabel = compose.activity.getString(R.string.station_disabled)
        runBlocking {
            catalog.setEnabled(original.id, true).getOrThrow()
        }

        try {
            enterEditMode()
            compose.onNodeWithContentDescription(editLabel).performClick()
            compose.onNodeWithText(enabledLabel).assertDoesNotExist()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Disable ${original.name}").performClick()
            compose.waitUntil(5_000) { catalog.stations.value.none { it.id == original.id } }
            assertEquals(original.id, catalog.allStations.value.last().id)
            compose.onNode(hasScrollAction()).performScrollToIndex(catalog.allStations.value.lastIndex)
            compose.onNode(hasText(stationLabel) and hasText(disabledLabel)).assertIsDisplayed()

            compose.onNodeWithContentDescription(compose.activity.getString(R.string.done_editing_stations)).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasContentDescription(compose.activity.getString(R.string.done_editing_stations)))
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(hasText(stationLabel) and hasClickAction()).assertDoesNotExist()

            compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.edit_stations)).performClick()
            compose.onNode(hasScrollAction()).performScrollToIndex(catalog.allStations.value.lastIndex)
            compose.onNodeWithContentDescription("Enable ${original.name}").performClick()
            compose.waitUntil(5_000) { catalog.stations.value.any { it.id == original.id } }
            assertEquals(original.id, catalog.stations.value.last().id)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.done_editing_stations)).performClick()
            compose.onNode(hasScrollAction()).performScrollToIndex(catalog.stations.value.lastIndex)
            compose.onNode(hasText(stationLabel) and hasClickAction()).assertExists()
        } finally {
            runBlocking {
                catalog.setEnabled(original.id, original.enabled).getOrThrow()
                val index = catalog.allStations.value.indexOfFirst { it.id == original.id }
                if (index != originalIndex) catalog.move(original.id, originalIndex - index).getOrThrow()
            }
        }
    }

    @Test
    fun musicBrainzSettingSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        val toggle = compose.onNodeWithText(compose.activity.getString(R.string.musicbrainz_artwork))
        toggle.assertIsDisplayed()
        val initiallyOn = toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] ==
            androidx.compose.ui.state.ToggleableState.On
        if (!initiallyOn) toggle.performClick()

        try {
            compose.activityRule.scenario.recreate()
            toggle.assertIsOn()
            compose.onNodeWithContentDescription("Add Radio").assertDoesNotExist()
        } finally {
            if (!initiallyOn) toggle.performClick()
        }
    }

    @Test
    fun bigPlayerAppearsBelowTheStationsAndSurvivesActivityRecreation() {
        val settings = compose.onNodeWithContentDescription("Settings")
        val toggle = compose.onNodeWithText("Big player")
        val back = compose.onNodeWithContentDescription("Back to stations")
        settings.performClick()
        val initiallyOn = toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] ==
            androidx.compose.ui.state.ToggleableState.On
        if (!initiallyOn) toggle.performClick()
        back.performClick()

        try {
            compose.activityRule.scenario.recreate()
            val next = compose.onNodeWithContentDescription("Next station").getUnclippedBoundsInRoot()
            enterEditMode()
            val firstRowEditor = compose.onNodeWithContentDescription("Edit ZRock")
                .getUnclippedBoundsInRoot()
            assertTrue(firstRowEditor.bottom < next.top)
            settings.performClick()
            toggle.assertIsOn()
            back.performClick()
        } finally {
            if (!initiallyOn) {
                settings.performClick()
                toggle.performClick()
                back.performClick()
            }
        }
    }

    @Test
    fun darkModeSwitchOverridesSystemThemeAndSurvivesActivityRecreation() {
        val preferences = compose.activity.radioPreferences
        val originalValue = preferences.all[DARK_MODE_ENABLED] as? Boolean
        preferences.edit().remove(DARK_MODE_ENABLED).apply()

        try {
            compose.activityRule.scenario.recreate()
            val systemDark = (compose.activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings)).performClick()
            val toggle = compose.onNodeWithText(compose.activity.getString(R.string.dark_mode))

            fun assertTheme(dark: Boolean) {
                if (dark) toggle.assertIsOn() else toggle.assertIsOff()
                compose.activityRule.scenario.onActivity { activity ->
                    assertEquals(dark, Color.luminance(activity.window.statusBarColor) < 0.5f)
                }
            }

            assertTheme(systemDark)
            toggle.performClick()
            assertTheme(!systemDark)

            compose.activityRule.scenario.recreate()
            assertTheme(!systemDark)
            toggle.performClick()
            assertTheme(systemDark)
        } finally {
            preferences.edit().apply {
                if (originalValue == null) remove(DARK_MODE_ENABLED) else putBoolean(DARK_MODE_ENABLED, originalValue)
            }.apply()
        }
    }

    private fun openEditor() {
        enterEditMode()
        compose.onNodeWithContentDescription("Add Radio").performClick()
    }

    private fun enterEditMode() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Edit stations").performClick()
    }
}
