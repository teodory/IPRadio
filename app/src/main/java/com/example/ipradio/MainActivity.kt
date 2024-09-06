package com.example.ipradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ipradio.ui.theme.IPRadioTheme
import android.content.res.Configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer

import com.example.ipradio.RadioData.Radio


class MainActivity : ComponentActivity() {

    // ...
    private var songInfo by mutableStateOf<String>("Empty...")
    private lateinit var playbackManager: PlaybackManager
//    private var selectedRadio by mutableStateOf<Radio?>(null)
//    @Composable
//    fun PreviewMessageCard() {
//        IPRadioTheme {
//            Surface {
//                MessageCard(
//                    msg = Message("Lexi", "Hey, take a look at Jetpack Compose, it's great!")
//                )
//            }
//        }
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.playbackManager = PlaybackManager.getInstance(this)
//        playbackManager.observeSongText(this, Observer { newText ->
//            //            textView.text = newText
//            playbackManager.selectedRadio?.songAuthor = newText.toString()
//        })
        // Show played song info
        playbackManager.songInfo.observe(this, Observer {it ->
            songInfo = it.toString()
            println("Teodor: Main onCreate")
        })

//        playbackManager.playbackState.observe(this, Observer { state ->
//            // Update your UI based on the new state
//            when (state) {
//                PlaybackManagerState.PLAYING -> // Update UI for playing state
//                PlaybackManagerState.PAUSED -> // Update UI for paused state
//                PlaybackManagerState.STOPPED -> // Update UI for stopped state
//            }
//        })

        setContent {
            IPRadioTheme {
//                Conversation(SampleData.conversationSample)
//  ================================================================================
//                Surface(
//                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colorScheme.background
//                ) {
////                    Greeting("Android")
//                    GreetingPreview()
//                }
//  ================================================================================
                Column {
                    ShowRadioList(playbackManager.getRadiosList())
                    Row {
                        Button(
                            onClick = {
//                              player.stop()
                                playbackManager.playPrevRadio()
                            },
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                        {
                            Text("Prev")
                        }
                        Button(onClick = {
                            playbackManager.stop()
                        }, modifier = Modifier.padding(horizontal = 3.dp)) {
                            Text("Stop")
                        }
                        Button(
                            onClick = {
                                playbackManager.playNextRadio()
                            },
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                        {
                            Text("Next")
                        }
                    }
                    Row {
                        Text(
                            text = songInfo,
                            modifier = Modifier
                        )
                    }
//                    ShowSongInfo()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackManager.stop()
    }

    @Composable
    fun ShowRadioList(radios: List<Radio>) {
        LazyColumn {
            items(radios) { radio ->
                RadioCard(radio)
            }
        }
    }

    @Composable
    fun RadioCard(radio: Radio) {

        Row(modifier = Modifier.padding(all = 8.dp)) {
            Text(
                text = radio.name,
                modifier = Modifier.clickable(
                ) {
                    if (playbackManager.selectedRadio == radio) {
                        // If the same radio is selected, stop the player
                        playbackManager.stop()
                        // Set selectedRadio to null since no radio is currently selected
                        playbackManager.selectedRadio = null
                    } else {
                        // If a different radio is selected, play the new radio
//                        playbackManager.selectedRadio = radio // Teodor: check for bug; commented because playbackManager.play check and change .selectedRadio
                        playbackManager.play(radio)
                    }
//                    selectedRadio = radio
//                    player.createMediaPlayer(radio.url)
                },
                color = if (playbackManager.selectedRadio == radio) Color.Red else Color.Black
            )
        }
    }

//    @Composable
//    fun ShowSongInfo()
//    {
//        Row {
//            Text(
//                text = songInfo,
//                modifier = Modifier
//            )
//        }
//    }

    @Composable
    fun Greeting(name: String) {
        Text(text = "Hello $name!")
    }

    @Preview(name = "Light Mode")
    @Preview(
        uiMode = Configuration.UI_MODE_NIGHT_YES,
        showBackground = true,
        name = "Dark Mode"
    )
    @Composable
    fun GreetingPreview() {
        IPRadioTheme {
            Greeting("Androida")
        }
    }




}
