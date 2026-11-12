package com.example.ipradio

import android.content.Context
import android.content.SharedPreferences

internal const val MUSICBRAINZ_ARTWORK_ENABLED = "musicbrainz_artwork_enabled"

internal val Context.radioPreferences: SharedPreferences
    get() = getSharedPreferences("radio_preferences", Context.MODE_PRIVATE)
