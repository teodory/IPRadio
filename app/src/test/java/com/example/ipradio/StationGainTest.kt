package com.example.ipradio

import org.junit.Assert.assertEquals
import org.junit.Test

class StationGainTest {
    @Test
    fun attenuationUsesDecibelsAndBoostKeepsPlayerAtFullVolume() {
        assertEquals(0.501f, playerVolumeForGain(-6), 0.001f)
        assertEquals(1f, playerVolumeForGain(0), 0f)
        assertEquals(1f, playerVolumeForGain(6), 0f)
    }
}
