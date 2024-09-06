package com.example.ipradio


import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import androidx.media3.common.MediaItem


class MyMediaBrowserServiceClass : MediaBrowserServiceCompat(), PlaybackStateListener {

    private var mSession: MediaSessionCompat? = null
    private var mCallback: MyMediaSessionCallback? = null
    private lateinit var mediaController: MediaControllerCompat
    private var playbackStateBuilder = PlaybackStateCompat.Builder()
    private lateinit var playbackManager: PlaybackManager
    private var radioMediaItems: List<MediaBrowserCompat.MediaItem> = emptyList()
//    var currentMetadata: MediaMetadataCompat = MediaMetadataCompat.fromMediaMetadata(null)

    companion object {
        const val DEFAULT_PATH = "com.example.ipradio."
        const val MY_MEDIA_ROOT_ID = "com.example.ipradio.ROOT"
        const val RADIO_INDEX = "com.example.ipradio.RADIO_INDEX"
        private const val CUSTOM_ACTION_THUMBS_UP = "com.example.android.ipradio.THUMBS_UP"
        private const val EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR = "com.example.android.ipradio.EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR"
    }


    override fun onCreate() {
        super.onCreate()
        println("Teodor: MyMediaBrowserServiceClass::onCreate")
//        player = ExoPlayer.Builder(applicationContext).build()

        playbackManager = PlaybackManager.getInstance(applicationContext)

        val a: (String) -> Unit = { ss ->

            println("Teodor: ------- $ss")
            if (ss == "play")
            {
                val extras = Bundle()
                extras.putBoolean(
                    MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
                )
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )

                mSession?.apply {
                    setPlaybackState(playbackStateBuilder.apply {
                        setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
//                        if (extras != null) {
//                            setBufferedPosition(extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
//                        }

// ???????? 2222 start
                        setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_STOP
                                or PlaybackStateCompat.ACTION_SEEK_TO
                                or PlaybackStateCompat.ACTION_PAUSE)
// ??????  2222 end

//??????? 111 start
                        val playbackStateExtras = Bundle().apply {
                            putString(
                                MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID,
                                extras?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                            )
                        }
                        setExtras(playbackStateExtras)
                        addActionsForPlayback(this)
// ??????? 111 end
                    }.build())

                    setMetadata(
                        MediaMetadataCompat.Builder().apply {
                            if (extras != null) {
                                putString(
//                                MediaMetadataCompat.METADATA_KEY_MEDIA_ID, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                                    MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playbackManager.selectedRadioInd.toString()
                                )
                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playbackManager.selectedRadio?.name.toString()
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist"
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "Album"
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "A" + playbackManager.selectedRadio?.songAuthor.toString()
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "DISPLAY_DESCRIPTION")
//                                putLong(
//                                    MediaMetadataCompat.METADATA_KEY_DURATION, 1235678
////                                    extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
//                                )
                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI, radio.url
                                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                                )
                                putLong(
                                    RADIO_INDEX, extras.getInt(RADIO_INDEX).toLong()
                                )
                                putString(
                                    "com.example.ipradio.METADATA_KEY_STREAM_BITRATE", "128kbps"
                                )
                                putText(
                                    "com.example.ipradio.METADATA_KEY_STREAM_BITRATE2", "128kbps"
                                )
                                val aa = extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)

                                putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
                                )
//                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
//                                    ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
//                                            + "com.example.ipradio" + "/" + R.drawable.darik_logo
//                                )
                            }
//                        if (albumCover != null) {
//                            putBitmap(
//                                MediaMetadataCompat.METADATA_KEY_ART, albumCover
//                            )
//                        } else {
////                            putBitmap(
////                                MediaMetadataCompat.METADATA_KEY_ART, defaultArt
////                            )
//                        }
//                        putLong(
//                            MediaConstants.METADATA_KEY_IS_EXPLICIT,
//                            extras.getLong(MediaConstants.METADATA_KEY_IS_EXPLICIT)
//                        )
                        }.build()
                    )
                }

                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)




            }
            else if (ss == "pause")
            {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }
            else if (ss == "stop")
            {
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            }
        }

//        playbackManager.setCallback(a) // TRY

        ///////
        mCallback = MyMediaSessionCallback()
        mSession = MediaSessionCompat(baseContext, "MusicService").apply {
            setCallback(mCallback)
            setPlaybackState(
                playbackStateBuilder.apply {
                    setState(PlaybackStateCompat.STATE_NONE, 0L, 1f)
                    addActionsForPlayback(this)
//                    addCustomActionsForPlayback(this)
                }.build()
            )

            isActive = true
        }
        sessionToken = mSession?.sessionToken

        mediaController = MediaControllerCompat(baseContext, mSession!!.sessionToken).apply {
            registerCallback(MyMediaControllerCallback())
        }
        //////


        playbackManager.addPlaybackStateListener(this) // TRY


        // Create a new MediaSession.
//        mSession = MediaSessionCompat(this, "MusicService")
//        mSession = MediaSessionCompat(baseContext, "MusicService")
//
//        updatePlaybackState(PlaybackState.STATE_STOPPED)
//        mCallback = MyMediaSessionCallback()
//        mSession?.setCallback(mCallback)
//
//        mSession?.isActive = true
//
//        mSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)



        // ~~~~~~~~~~~~~~~~~~~~~ V
//        val sessionActivityPendingIntent1 = PendingIntent.getActivity(
//            /* context = */  applicationContext,
//            /* requestCode = */ 0,
//            /* intent = */ Intent( applicationContext, MainActivity::class.java),
//            /* flags = */ PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//        )
//        val sessionActivityPendingIntent =
//            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
//                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
//            }
//        mSession?.setSessionActivity(sessionActivityPendingIntent)
        // ~~~~~~~~~~~~~~~~~~~~~ ^^^


//        sessionToken = mSession?.sessionToken
    }

    override fun onPlaybackStateChanged(newState: PlaybackManagerState?) {
        println("Teodor: onPlaybackStateChanged: newState $newState")

        if (newState == PlaybackManagerState.PLAYING) {
            //----------------------------------------------- 1
            /*
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Song Title")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist Name")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Album Name")
                .build()

            mSession!!.setMetadata(metadata)

// Update the playback state
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()

            mSession!!.setPlaybackState(playbackState)
*/
            //----------------------------------------------- 1



            val extras = Bundle()
            extras.putBoolean(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
            )
            extras.putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
            extras.putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )

            mSession?.apply {
                setPlaybackState(playbackStateBuilder.apply {
                    setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
//                        if (extras != null) {
//                            setBufferedPosition(extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
//                        }

// ???????? 2222 start
                    setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SEEK_TO
                            or PlaybackStateCompat.ACTION_PAUSE)
// ??????  2222 end

//??????? 111 start
                    val playbackStateExtras = Bundle().apply {
                        putString(
                            MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID,
                            extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                        )
                    }
                    setExtras(playbackStateExtras)
                    addActionsForPlayback(this)
// ??????? 111 end
                }.build())

                setMetadata(
                    MediaMetadataCompat.Builder().apply {
                        putString(
//                                MediaMetadataCompat.METADATA_KEY_MEDIA_ID, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                            MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playbackManager.selectedRadioInd.toString()
                        )
                        putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playbackManager.selectedRadio?.name.toString()
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, playbackManager.selectedRadio?.songAuthor.toString() // 2
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                        )
                        putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "DISPLAY_DESCRIPTION")
                        putString(
                            MediaMetadataCompat.METADATA_KEY_MEDIA_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                        )
                        putLong(
                            RADIO_INDEX, extras.getInt(RADIO_INDEX).toLong()
                        )
                        putString(
                            "com.example.ipradio.METADATA_KEY_STREAM_BITRATE", "128kbps"
                        )
                        putText(
                            "com.example.ipradio.METADATA_KEY_STREAM_BITRATE2", "128kbps"
                        )
                        val aa = extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)

                        // HERE background image
                        putString(
//                            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, playbackManager.selectedRadio?.image
                        )
//                        putString(
//                            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
//                            ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
//                                    + "com.example.ipradio" + "/" + R.drawable.darik_logo
//                        )
                    }.build()
                )
            }

//  TRY 222
//            // Set metadata for the radio station
//            val builder = MediaMetadataCompat.Builder()
//            // Set appropriate metadata for the radio station
//            // This will depend on what metadata you have available for the radio station
//            builder.putString (MediaMetadataCompat.METADATA_KEY_TITLE, "test 123")
// TRY 222



//            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
//            mCallback?.onPlay()



            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()

            mSession!!.setPlaybackState(playbackState)
        }
        if (newState == PlaybackManagerState.STOPPED) {
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
//            mCallback?.onStop()
        }
        if (newState == PlaybackManagerState.PAUSED) {
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
//            mCallback?.onPause()
        }
        if (newState == PlaybackManagerState.UPDATE) {

            val extras = Bundle()
            extras.putBoolean(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
            )
            extras.putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
            extras.putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )

            mSession?.apply {
                setPlaybackState(playbackStateBuilder.apply {
                    setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)

                    setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SEEK_TO
                            or PlaybackStateCompat.ACTION_PAUSE)

                    val playbackStateExtras = Bundle().apply {
                        putString(
                            MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID,
                            extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                        )
                    }
                    setExtras(playbackStateExtras)
                    addActionsForPlayback(this)

                }.build())

                setMetadata(
                    MediaMetadataCompat.Builder().apply {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playbackManager.selectedRadioInd.toString()
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playbackManager.selectedRadio?.name.toString()
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, playbackManager.selectedRadio?.songAuthor.toString()
                        )
                        putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "DISPLAY_DESCRIPTION")
                        putString(
                            MediaMetadataCompat.METADATA_KEY_MEDIA_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                        )
                        putLong(
                            RADIO_INDEX, extras.getInt(RADIO_INDEX).toLong()
                        )
                        putString(
                            "com.example.ipradio.METADATA_KEY_STREAM_BITRATE", "128kbps"
                        )
                        putText(
                            "com.example.ipradio.METADATA_KEY_STREAM_BITRATE2", "128kbps"
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, playbackManager.selectedRadio?.image
                        )
                    }.build()
                )
            }

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()

            mSession!!.setPlaybackState(playbackState)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val extras = Bundle()
        extras.putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
        )
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )

        // Check if the client is allowed to browse media.
        // You'll need to implement this check based on your own app logic.
        return if (isClientAllowedToBrowse(clientPackageName, clientUid)) {
            // If the client is allowed to browse, return your root media ID
            BrowserRoot(MY_MEDIA_ROOT_ID, extras)
        } else {
            // If the client is not allowed to browse, return null
            null
        }
    }

    private fun isClientAllowedToBrowse(clientPackageName: String, clientUid: Int): Boolean {
        // Implement your logic here to check if the client is allowed to browse your media
        // For example, you might check if the clientPackageName is known to your app
        return true
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        println("Teodor: MyMediaBrowserServiceClass::onLoadChildren")

        // Teodor: Here small list icon
        val drawableResourceId: Int = android.R.drawable.ic_menu_slideshow
        val drawableUri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                    + "com.example.ipradio" + "/" + drawableResourceId
        )

        when (parentId) {
            MY_MEDIA_ROOT_ID -> {
                val list = playbackManager.getRadiosList().mapIndexed { index,  radio ->
                    val extras = Bundle()
                    extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, radio.name)
                    extras.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, radio.url)
                    extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, radio.image)
                    extras.putInt(RADIO_INDEX, index)

//                    extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, station.getDuration())
                    val radioImageUrl = Uri.parse(radio.image)

                    // Convert each Radio to a MediaItem
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(radio.name) // use the radio's name as the ID
                            .setTitle(radio.name) // use the radio's name as the title
//                            .setSubtitle(radio.url) // use the radio's url as the subtitle
                            .setDescription("Radio station") // set a general description
                            .setIconUri(radioImageUrl)
//                            .setIconUri(drawableUri)
                            .setExtras(extras)
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                }
                radioMediaItems = radioMediaItems.plus(list)
                result.sendResult(list)
            }
            else -> {
                result.sendResult(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackManager.removePlaybackStateListener(this)
        playbackManager.stop()
        mSession?.release()
    }



    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            // Handle focus change events here
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> playbackManager.play()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> playbackManager.volume = 0.2f // Lower the volume
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> playbackManager.pause()
                AudioManager.AUDIOFOCUS_LOSS -> {
                    playbackManager.stop()
                    // Release the audio focus when you're done playing
                    abandonAudioFocus()
                }
                // Handle other focus changes if necessary
            }
        }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }


// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private fun addActionsForPlayback(playbackStateCompatBuilder: PlaybackStateCompat.Builder) {
        println("Teodor: MyMediaBrowserServiceClass::addActionsForPlayback")
        playbackStateCompatBuilder.apply {
            setActions(PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    or PlaybackStateCompat.ACTION_SEEK_TO)
        }
    }

    private fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {
//        val currentMusic: MediaSessionCompat.QueueItem = mQueueManager.getCurrentMusic() ?: return
        // Set appropriate "Favorite" icon on Custom action:
//        val mediaId = currentMusic.description.mediaId ?: return
//        val musicId: String = MediaIDHelper.extractMusicIDFromMediaID(mediaId)
        val favoriteIcon: Int = androidx.media3.ui.R.drawable.exo_icon_shuffle_on
//            if (mMusicProvider.isFavorite(musicId)) R.drawable.star_off else R.drawable.star_on
        Log.d(
            "TAG", "updatePlaybackState, setting Favorite custom action of music ",
//            musicId, " current favorite=", mMusicProvider.isFavorite(musicId)
        )
        val customActionExtras = Bundle()
//        WearHelper.setShowCustomActionOnWear(customActionExtras, true)

        customActionExtras.putBoolean(EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR, true)

        stateBuilder.addCustomAction(
//            PlaybackStateCompat.CustomAction.Builder( CUSTOM_ACTION_THUMBS_UP, mResources.getString(R.string.favorite), favoriteIcon )
            PlaybackStateCompat.CustomAction.Builder( CUSTOM_ACTION_THUMBS_UP, "dummy abcd", favoriteIcon )
                .setExtras(customActionExtras)
                .build()
        )
    }
    private fun updatePlaybackState(state: Int) {

        println("updatePlaybackState : $state")
        println("Teodor: MyMediaBrowserServiceClass::updatePlaybackState : $state")

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
            )
//            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
        setCustomAction(stateBuilder)
        stateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

//        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build());
//
//        if (state == PlaybackStateCompat.STATE_PLAYING ||
//            state == PlaybackStateCompat.STATE_PAUSED) {
//            mServiceCallback.onNotificationRequired();
//        }
        mSession?.setPlaybackState(stateBuilder.build())
    }

    //   ------------------------------------  MyMediaSessionCallback  BEGIN ------------------------------------
    inner class MyMediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPrepare() {
            println("Preparing")
            println("Teodor: MyMediaSessionCallback::onPrepare")
            super.onPrepare()
        }

        override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
            println("Preparing from media id")
            println("Teodor: MyMediaSessionCallback::onPrepareFromMediaId")
            super.onPrepareFromMediaId(mediaId, extras)
        }

        override fun onPlay() {
            println("Playing")
            println("Teodor: MyMediaSessionCallback::onPlay")
            // Request audio focus
            Toast.makeText(applicationContext, "Play", Toast.LENGTH_SHORT).show()

            if (requestAudioFocus()) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                // Start media player
                playbackManager.play()
            }
            super.onPlay()
        }

        // Don't forget to abandon audio focus when appropriate
        override fun onPause() {
            println("Teodor: MyMediaSessionCallback::onPause")
            // Pause your media player
            Toast.makeText(applicationContext, "Pause", Toast.LENGTH_SHORT).show()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            playbackManager.pause()
            abandonAudioFocus()
            super.onPause()
        }

        override fun onStop() {
            Toast.makeText(applicationContext, "Stop", Toast.LENGTH_SHORT).show()
            println("Teodor: MyMediaSessionCallback::onStop")
            super.onStop()
            // Stop your media player
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            playbackManager.stop()
            abandonAudioFocus()
            super.onStop()
        }

        override fun onSkipToNext() {
            println("Teodor: MyMediaSessionCallback::onSkipToNext")
            // Handle skip to next action
            // TODO: Teodor: Do I need this?
//            Toast.makeText(applicationContext, "onSkipToNext", Toast.LENGTH_SHORT).show()
//            Log.e("onSkipToNext", "onSkipToNext current: ${player.currentMediaItemIndex}")
//            Log.e("onSkipToNext", "onSkipToNext next: ${player.nextMediaItemIndex}")
//            Log.e("onSkipToNext", "onSkipToNext prev: ${player.previousMediaItemIndex}")
//            var nextMediaItemIndex = player.currentMediaItemIndex + 1
//            if (nextMediaItemIndex >= radioMediaItems.size)
//            {
//                nextMediaItemIndex = 0
//            }
            val nextInd = playbackManager.getNextInd()
            val a = radioMediaItems[nextInd].mediaId
            if (a != null) {
                onPlayFromMediaId(a, radioMediaItems[nextInd].description.extras)
            }
        }

        override fun onSkipToPrevious() {
            println("Teodor: MyMediaSessionCallback::onSkipToPrevious")
            // Handle skip to previous action
            // TODO: Teodor: Do I need this?

//            Toast.makeText(applicationContext, "onSkipToPrevious", Toast.LENGTH_SHORT).show()
//            Log.e("onSkipToPrevious", "onSkipToPrevious ")
//            var prevMediaItemIndex = player.currentMediaItemIndex - 1
//            if (prevMediaItemIndex < 0)
//            {
//                prevMediaItemIndex = radioMediaItems.size - 1
//            }
//            val a = radioMediaItems[prevMediaItemIndex].mediaId
//            if (a != null) {
//                onPlayFromMediaId(a, radioMediaItems[prevMediaItemIndex].description.extras)
//            }

            val prevInd = playbackManager.getPrevInd()
            val a = radioMediaItems[prevInd].mediaId
            if (a != null) {
                onPlayFromMediaId(a, radioMediaItems[prevInd].description.extras)
            }
        }

        override fun onSkipToQueueItem(id: Long) {
            println("Teodor: MyMediaSessionCallback::onSkipToQueueItem")
            super.onSkipToQueueItem(id)
            Toast.makeText(applicationContext, "onSkipToQueueItem", Toast.LENGTH_SHORT).show()
            Log.e("onSkipToQueueItem", "onSkipToQueueItem id: $id ")
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            println("Teodor: MyMediaSessionCallback::onPlayFromMediaId: $mediaId")
            println("Playing from Media Id")

//            super.onPlayFromMediaId(mediaId, extras)

            // Handle play from media ID action
            Toast.makeText(applicationContext, "onPlayFromMediaId: $mediaId", Toast.LENGTH_SHORT).show()
            Log.e("onPlayFromMediaId", "onPlayFromMediaId id: $mediaId ")
            // Find the radio item with this mediaId
            val radio = playbackManager.getRadiosList().find { it.name == mediaId }

//            updatePlaybackState(PlaybackState.STATE_PLAYING)

            // If found, create and start the MediaPlayer
            if (radio != null) {
                val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(radio.url))

                mSession?.apply {
                    setPlaybackState(playbackStateBuilder.apply {
                        setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
//                        if (extras != null) {
//                            setBufferedPosition(extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
//                        }

// ???????? 2222 start
//                        setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
//                                or PlaybackStateCompat.ACTION_STOP
//                                or PlaybackStateCompat.ACTION_SEEK_TO
//                                or PlaybackStateCompat.ACTION_PAUSE)
// ??????  2222 end

//??????? 111 start
//                        val playbackStateExtras = Bundle().apply {
//                            putString(
//                                MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID,
//                                extras?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
//                            )
//                        }
//                        setExtras(playbackStateExtras)
//                        addActionsForPlayback(this)
// ??????? 111 end
                    }.build())

                    setMetadata(
                        MediaMetadataCompat.Builder().apply {
                            if (extras != null) {
                                putString(
//                                MediaMetadataCompat.METADATA_KEY_MEDIA_ID, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                                    MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId
                                )
                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, radio.name
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist"
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "Album"
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "C" + playbackManager.selectedRadio?.songAuthor.toString() //3
//                                    extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                                )
                                putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "DISPLAY_DESCRIPTION")
//                                putLong(
//                                    MediaMetadataCompat.METADATA_KEY_DURATION, 1235678
////                                    extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
//                                )
                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI, radio.url
                                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                                )
                                putLong(
                                    RADIO_INDEX, extras.getInt(RADIO_INDEX).toLong()
                                )
                                putString(
                                    "com.example.ipradio.METADATA_KEY_STREAM_BITRATE", "128kbps"
                                )
                                putText(
                                    "com.example.ipradio.METADATA_KEY_STREAM_BITRATE2", "128kbps"
                                )
                                val aa = extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)

//                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, extras.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
//                                )
//                                putString(
//                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
//                                    ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
//                                            + "com.example.ipradio" + "/" + R.drawable.darik_logo
//                                )
                            }
//                        if (albumCover != null) {
//                            putBitmap(
//                                MediaMetadataCompat.METADATA_KEY_ART, albumCover
//                            )
//                        } else {
////                            putBitmap(
////                                MediaMetadataCompat.METADATA_KEY_ART, defaultArt
////                            )
//                        }
//                        putLong(
//                            MediaConstants.METADATA_KEY_IS_EXPLICIT,
//                            extras.getLong(MediaConstants.METADATA_KEY_IS_EXPLICIT)
//                        )
                        }.build()
                    )
                }

                var mediaItems : List<MediaItem> = emptyList()

// ???? 3333 start
//                radioMediaItems.map { i ->
//                    val itemExtras = i.description.extras!!
//                    val medItem : MediaItem = MediaItem.Builder()
//                        .setUri(itemExtras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))
//                        .build()
//                    mediaItems = mediaItems.plus(medItem)
//                }
//                val list = MainActivity.RadioData.radios.mapIndexed { index,  radio ->
// ???? 3333 end

                val a = mSession?.controller?.extras
                val b = mediaItem.mediaMetadata.extras
                val c = mediaItem.mediaMetadata.description
//                mediaItem.mediaMetadata.extras = mSession?.controller?.extras

//                1 ---------------------------------------------------------------------------------------------------------
                // TODO: DO I need this?
//                playbackManager.setMediaItems(mediaItems, extras!!.getInt(RADIO_INDEX), C.TIME_UNSET)
////                player.setMediaItem(mediaItem2)
//                player.prepare()
//                val d = player.currentMediaItemIndex
//                val e = player.nextMediaItemIndex
//                val f = mSession?.controller?.extras?.getInt(RADIO_INDEX)
//                1 ---------------------------------------------------------------------------------------------------------

                if (requestAudioFocus()) {
                    // Start your media player
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    playbackManager.play(radio)
                }


                // Set metadata for the radio station
                val builder = MediaMetadataCompat.Builder()
                // Set appropriate metadata for the radio station
                // This will depend on what metadata you have available for the radio station
                builder.putString (MediaMetadataCompat.METADATA_KEY_TITLE, radio.name)
            } else {
                // Handle the case when the radio station is not found

                Log.e("MediaSession", "Radio station with mediaId $mediaId not found")
                // Optionally, you can also provide feedback to the user
            }
        }

        override fun onRewind() {
            println("Rewind command")

            super.onRewind()
        }
    }

    //   ------------------------------------  MyMediaSessionCallback  END ------------------------------------
    inner class MyMediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            println("Teodor: MyMediaControllerCallback::onMetadataChanged")
            println("Controller media id: ${metadata?.description?.mediaId}")

            super.onMetadataChanged(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            println("Teodor: MyMediaControllerCallback::onPlaybackStateChanged")
            println("Controller playback state : ${state?.state}")

            super.onPlaybackStateChanged(state)
        }

        override fun onSessionReady() {
            println("Teodor: MyMediaControllerCallback::onSessionReady")
            println("Controller session is ready")

            super.onSessionReady()
        }
    }

}