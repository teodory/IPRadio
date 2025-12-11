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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.Divider
import androidx.compose.ui.Alignment

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth

import com.example.ipradio.RadioData.Radio


class MainActivity : ComponentActivity() {
    private var songInfo by mutableStateOf<String>("Empty...")
    private lateinit var playbackManager: PlaybackManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.playbackManager = PlaybackManager.getInstance(this)
        // Show played song info
        playbackManager.songInfo.observe(this, Observer {it ->
            songInfo = it.toString()
        })


        setContent {
            IPRadioTheme {
                var showDialog by remember { mutableStateOf(false) }
                var showAddDialog by remember { mutableStateOf(false) }
                var radioToEdit by remember { mutableStateOf<Radio?>(null) }
                var isEditMode by remember { mutableStateOf(false) }

                if (showDialog && radioToEdit != null) {
                    EditRadioDialog(
                        radio = radioToEdit!!,
                        onDismiss = { showDialog = false },
                        onSave = { newRadio ->
                            val index = playbackManager.getRadiosList().indexOf(radioToEdit!!)
                            if (index != -1) {
                                playbackManager.updateRadio(index, newRadio)
                            }
                            showDialog = false
                        }
                    )
                }

                if (showAddDialog) {
                    AddRadioDialog(
                        onDismiss = { showAddDialog = false },
                        onSave = { newRadio ->
                            playbackManager.addRadio(newRadio)
                            showAddDialog = false
                        }
                    )
                }

                Column(modifier = Modifier.padding(bottom = 48.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isEditMode) {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Radio")
                            }
                        }
                        IconButton(onClick = { isEditMode = !isEditMode }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    Divider(color = Color.Gray, thickness = 1.dp)

                    ShowRadioList(
                        radios = playbackManager.getRadiosList(),
                        isEditMode = isEditMode,
                        onEditClick = { radio ->
                            radioToEdit = radio
                            showDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        Button(
                            onClick = {
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
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackManager.stop()
    }

    @Composable
    fun ShowRadioList(radios: List<Radio>, isEditMode: Boolean, onEditClick: (Radio) -> Unit, modifier: Modifier = Modifier) {
        LazyColumn(modifier = modifier) {
            items(radios) { radio ->
                RadioCard(radio, isEditMode = isEditMode, onEditClick = { onEditClick(radio) })
            }
        }
    }

    @Composable
    fun RadioCard(radio: Radio, isEditMode: Boolean, onEditClick: () -> Unit) {

        Row(modifier = Modifier.padding(all = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = radio.name,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (playbackManager.selectedRadio == radio) {
                            // If the same radio is selected, stop the player
                            playbackManager.stop()
                            // Set selectedRadio to null since no radio is currently selected
                            playbackManager.selectedRadio = null
                        } else {
                            // If a different radio is selected, play the new radio
                            playbackManager.play(radio)
                        }
                    },
                color = if (playbackManager.selectedRadio == radio) Color.Red else Color.Black
            )
            if (isEditMode) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        }
    }

    @Composable
    fun EditRadioDialog(
        radio: Radio,
        onDismiss: () -> Unit,
        onSave: (Radio) -> Unit
    ) {
        var name by remember { mutableStateOf(radio.name) }
        var url by remember { mutableStateOf(radio.url) }
        var image by remember { mutableStateOf(radio.image) }
        var infoDataUrl by remember { mutableStateOf(radio.infoDataUrl) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Radio") },
            text = {
                Column {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    TextField(value = url, onValueChange = { url = it }, label = { Text("URL") })
                    TextField(value = image, onValueChange = { image = it }, label = { Text("Image URL") })
                    TextField(value = infoDataUrl, onValueChange = { infoDataUrl = it }, label = { Text("Info URL") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(radio.copy(name = name, url = url, image = image, infoDataUrl = infoDataUrl))
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Composable
    fun AddRadioDialog(
        onDismiss: () -> Unit,
        onSave: (Radio) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var image by remember { mutableStateOf("") }
        var infoDataUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add New Radio") },
            text = {
                Column {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    TextField(value = url, onValueChange = { url = it }, label = { Text("URL") })
                    TextField(value = image, onValueChange = { image = it }, label = { Text("Image URL") })
                    TextField(value = infoDataUrl, onValueChange = { infoDataUrl = it }, label = { Text("Info URL") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(Radio(name = name, url = url, image = image, infoDataUrl = infoDataUrl, songAuthor = "", logo = 0, headers = emptyMap()))
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }


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
