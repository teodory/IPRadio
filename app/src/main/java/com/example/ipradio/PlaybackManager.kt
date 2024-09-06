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

    private val radios = radioData.radios
//    lateinit var selectedRadio: Radio
    var selectedRadio by mutableStateOf<Radio?>(null)
    var selectedRadioInd = -1

    private var blii : (text: String) -> Unit = {}

    private var songManager = SongManager()
    var testStr : String = ""
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

        println("Teodor: PlaybackManager::play   ---  ${listeners.size}")


        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(radio.url))

//        player = ExoPlayer.Builder(appContext)
////            .setMediaSourceFactory(custom)
//            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        selectedRadio = radio
        selectedRadioInd = radios.indexOf(radio)
        _playbackState.postValue(PlaybackManagerState.PLAYING) //?
        blii("play")

        setPlaybackState(PlaybackManagerState.PLAYING)

        songManager.fetchText(radio)
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

        blii("stop")
    }

    fun pause() {
        player.pause()
        _playbackState.postValue(PlaybackManagerState.PAUSED) // ?
        setPlaybackState(PlaybackManagerState.PAUSED)
        blii("pause")
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

        println("Teodor: PlaybackManager::addPlaybackStateListener   ---  size: ${listeners.size}")
    }

    fun removePlaybackStateListener(listener: PlaybackStateListener) {
        listeners.remove(listener)
    }

    private fun notifyPlaybackStateChange(newState: PlaybackManagerState) {
        println("Teodor: PlaybackManager::notifyPlaybackStateChange: newState: $newState")
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(newState)
        }
    }

    fun setPlaybackState(newState: PlaybackManagerState) {

        println("Teodor: PlaybackManager::setPlaybackState")
        _playbackState.postValue(newState)
        notifyPlaybackStateChange(newState)
    }

    fun setCallback(bli : (String) -> Unit)
    {
        println(" TEODOR  ")
        blii = bli
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





/*
class PlaybackManager(context: Context) {
//    private val player: SimpleExoPlayer = SimpleExoPlayer.Builder(context).build()
private val player = ExoPlayer.Builder(context).build()
//    val mediaSession = MediaSession.Builder(context, player).build()
//    private var mediaSession: MediaSession? = null
    val mediaSession = MediaSessionCompat(context, "MySession")


//    fun createMediaPlayer(url: String) {
//        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(url))
//        player.setMediaItem(mediaItem)
//        player.prepare()
//        player.playWhenReady = true
//    }

    // Get the current playback position from the player
    val playbackPosition: Long
        get() = player.currentPosition

    fun createMediaPlayer(url: String) {
        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(url))

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun start() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
    }

    fun releasePlayer() {
        player.release() // Release the player resources
        mediaSession.release() // Release the media session resources
    }

    fun play() {
        player.play()
    }

}
*/