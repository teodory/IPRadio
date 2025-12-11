package com.example.ipradio

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.MediaSessionCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.ipradio.RadioData.Radio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.toMutableStateList


enum class PlaybackManagerState {
    PLAYING, PAUSED, STOPPED, UPDATE
}


interface PlaybackStateListener {
    fun onPlaybackStateChanged(newState: PlaybackManagerState?)
}
class PlaybackManager private constructor(context: Context) {
    var volume: Float = 0.0f
    private val appContext = context.applicationContext
    private var player: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private val radioData: RadioData = RadioData()
    val mediaSession = MediaSessionCompat(context, "MySession") // TODO: Teodor: Do I need this

    private var _playbackState = MutableLiveData<PlaybackManagerState>()
    var playbackState: LiveData<PlaybackManagerState> = _playbackState
    private val listeners = mutableListOf<PlaybackStateListener>()

    private val radios = radioData.radios.toMutableStateList()

    fun updateRadio(index: Int, newRadio: Radio) {
        if (index in radios.indices) {
            val oldRadio = radios[index]
            radios[index] = newRadio
            
            if (selectedRadio == oldRadio) {
                selectedRadio = newRadio
                songManager.radio = newRadio
                songManager.fetchTextPeriodically()
            }
        }
    }

    fun addRadio(radio: Radio) {
        radios.add(radio)
    }
//    lateinit var selectedRadio: Radio
    var selectedRadio by mutableStateOf<Radio?>(null)
    var selectedRadioInd = -1

    private var songManager = SongManager()
    private var _songInfo = MutableLiveData<String>("")
    var songInfo: LiveData<String> = _songInfo

    init {
        // Observe the SongManager's text LiveData
        songManager.text.observeForever { newText ->
            _songInfo.postValue(newText)
            setPlaybackState(PlaybackManagerState.UPDATE)
        }
    }

    fun play(radio: Radio) {
        if (selectedRadio == radio && _playbackState.value == PlaybackManagerState.PLAYING)
        {
            return
        }



        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(radio.url))

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        selectedRadio = radio
        selectedRadioInd = radios.indexOf(radio)
        _playbackState.postValue(PlaybackManagerState.PLAYING) //?

        setPlaybackState(PlaybackManagerState.PLAYING)

        // Get Radio name
        songManager.radio = radio
        songManager.fetchTextPeriodically()
    }

    fun play() {
        selectedRadio?.let {
            play(it)
        }
    }

    fun stop() {
        player.stop()
//        player.release()
        mediaSession.release()
        selectedRadio = null
        _playbackState.postValue(PlaybackManagerState.STOPPED) //?
        setPlaybackState(PlaybackManagerState.STOPPED)
    }

    fun pause() {
        player.pause()
        _playbackState.postValue(PlaybackManagerState.PAUSED) // ?
        setPlaybackState(PlaybackManagerState.PAUSED)
    }

    fun getRadiosList(): List<Radio> {
        return radios
    }

    fun getPrevInd(): Int {
        if (selectedRadioInd == -1)
        {
            return 0
        }
        var prevMediaItemIndex = selectedRadioInd - 1
        if (prevMediaItemIndex < 0)
        {
            prevMediaItemIndex = radios.size - 1
        }
        return prevMediaItemIndex
    }

    fun getNextInd(): Int {
        if (selectedRadioInd == -1)
        {
            return 0
        }
//        var nextMediaItemIndex = player.currentMediaItemIndex + 1 // TODO: use this?
        var nextMediaItemIndex = selectedRadioInd + 1
        if (nextMediaItemIndex >= radios.size)
        {
            nextMediaItemIndex = 0
        }
        return nextMediaItemIndex
    }

    fun setMediaItems(mediaItems: List<MediaItem>, int: Int, timeUnset: Long) {
        // TODO: rework this
        player.setMediaItems(mediaItems, int, timeUnset)
    }

    fun playNextRadio() {
        play(radios[getNextInd()])
    }

    fun playPrevRadio() {
        play(radios[getPrevInd()])
    }

    fun addPlaybackStateListener(listener: PlaybackStateListener) {

        listeners.add(listener)
    }

    fun removePlaybackStateListener(listener: PlaybackStateListener) {
        listeners.remove(listener)
    }

    private fun notifyPlaybackStateChange(newState: PlaybackManagerState) {
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(newState)
        }
    }

    fun setPlaybackState(newState: PlaybackManagerState) {
        _playbackState.postValue(newState)
        notifyPlaybackStateChange(newState)
    }


    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {

            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackManager(context).also { INSTANCE = it }
            }
        }
    }
}




