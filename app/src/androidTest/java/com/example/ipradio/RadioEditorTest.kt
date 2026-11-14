package com.example.ipradio

import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

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
