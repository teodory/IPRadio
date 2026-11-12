package com.example.ipradio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsOn
import org.junit.Rule
import org.junit.Test

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
    fun musicBrainzSettingSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        val toggle = compose.onNodeWithText("Find cover art with MusicBrainz")
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

    private fun openEditor() {
        compose.onNodeWithContentDescription("Edit stations").performClick()
        compose.onNodeWithContentDescription("Add Radio").performClick()
    }
}
