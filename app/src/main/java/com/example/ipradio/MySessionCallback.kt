package com.example.ipradio

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat


internal class MySessionCallback2(private val context: Context) : MediaSessionCompat.Callback() {

    //internal class MySessionCallback() : MediaSessionCompat.Callback() {
    private var playbackManager: PlaybackManager = PlaybackManager.getInstance(context)

    init {
        // Initialize the player with the context
    }

    override fun onPlay() {
        // Start your media player
//        playbackManager.start()

        // Then update the playback state
//        val state = PlaybackStateCompat.Builder()
//            .setState(PlaybackStateCompat.STATE_PLAYING, playbackManager.playbackPosition, 1.0f)
//            .build()
//        playbackManager.mediaSession.setPlaybackState(state)
    }

    override fun onPause() {
        // Pause your media player
        playbackManager.pause()

        // Then update the playback state
//        val state = PlaybackStateCompat.Builder()
//            .setState(PlaybackStateCompat.STATE_PAUSED, playbackManager.playbackPosition, 1.0f)
//            .build()
//        playbackManager.mediaSession.setPlaybackState(state)
    }

    override fun onStop() {
        super.onStop()
        // Stop your media player
        playbackManager.stop()

        // Then update the playback state
//        val state = PlaybackStateCompat.Builder()
//            .setState(PlaybackStateCompat.STATE_STOPPED, playbackManager.playbackPosition, 1.0f)
//            .build()
//        playbackManager.mediaSession.setPlaybackState(state)
    }

    override fun onSkipToNext() {
        // Handle skip to next action
    }

    override fun onSkipToPrevious() {
        // Handle skip to previous action
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {

//        super.onPlayFromMediaId(mediaId, extras)
//        mSession!!.setMetadata(
//            MediaMetadataCompat.Builder()
//                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, "primeira musica")
//                .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, "Joãozinho das Rezas")
//                .putText(MediaMetadataCompat.METADATA_KEY_GENRE, "Gospel")
//                .putText(
//                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
//                    "http://70.38.6.72/~vivafe/web/wp-content/uploads/2016/08/01.jpg"
//                )
//                .build()
//        )
//        playMedia(0, mediaId)



//        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Handle play from media ID action
        super.onPlayFromMediaId(mediaId, extras)

        // Find the radio item with this mediaId
        val radio = playbackManager.getRadiosList().find { it.name == mediaId }


        // If found, create and start the MediaPlayer
        if (radio != null) {
//            playbackManager.createMediaPlayer(radio.url)
//            player.play() // Start playback

            // Set metadata for the radio station
            val builder = MediaMetadataCompat.Builder()
            // Set appropriate metadata for the radio station
            // This will depend on what metadata you have available for the radio station
            builder.putString (MediaMetadataCompat.METADATA_KEY_TITLE, radio.name)

            // ... set other metadata fields if available

            playbackManager.mediaSession.setMetadata(builder.build())

            // Update the playback state
//            val state = PlaybackStateCompat.Builder()
//                .setState(PlaybackStateCompat.STATE_PLAYING, playbackManager.playbackPosition, 1.0f)
//                .build()
//            playbackManager.mediaSession.setPlaybackState(state)
        } else {
            // Handle the case when the radio station is not found
//            Log.e("MediaSession", "Radio station with mediaId $mediaId not found")
            // Optionally, you can also provide feedback to the user
        }

//        // If found, create and start the MediaPlayer
//        radio?.let {
//            player.createMediaPlayer(it.url)
//            player.play()
//        }
//
//
//        val state = PlaybackStateCompat.Builder()
//            .setState(PlaybackStateCompat.STATE_PLAYING, player.playbackPosition, 1.0f)
//            .build()
//        player.mediaSession.setPlaybackState(state)


//        initMediaMetaData( radio.url );
//        toggleMediaPlaybackState( true );
//        playMedia( 0, radio.url );
    }

    private fun initMediaMetaData(id: String) {
//        for (song in mSongs) {
//            if (!TextUtils.isEmpty(song.getuId()) && song.getuId().equalsIgnoreCase(id)) {
//                val builder: MediaMetadata.Builder = Builder()
//                if (!TextUtils.isEmpty(song.getTitle())) builder.putText(
//                    MediaMetadata.METADATA_KEY_TITLE,
//                    song.getTitle()
//                )
//                if (!TextUtils.isEmpty(song.getArtist())) builder.putText(
//                    MediaMetadata.METADATA_KEY_ARTIST,
//                    song.getArtist()
//                )
//                if (!TextUtils.isEmpty(song.getGenre())) builder.putText(
//                    MediaMetadata.METADATA_KEY_GENRE,
//                    song.getGenre()
//                )
//                if (!TextUtils.isEmpty(song.getAlbum())) builder.putText(
//                    MediaMetadata.METADATA_KEY_ALBUM,
//                    song.getAlbum()
//                )
//                if (!TextUtils.isEmpty(song.getAlbumUrl())) builder.putText(
//                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
//                    song.getAlbumUrl()
//                )
//                mMediaSession.setMetadata(builder.build())
//            }
//        }
    }

    private fun playMedia(position: Int, id: String) {
//        if (mMediaPlayer != null) mMediaPlayer.reset()
//
//        //Should check id to determine what to play in a real app
//        val songId: Int = getApplicationContext().getResources().getIdentifier(
//            "geoff_ledak_dust_array_preview",
//            "raw",
//            getApplicationContext().getPackageName()
//        )
//        mMediaPlayer = MediaPlayer.create(getApplicationContext(), songId)
//        if (position > 0) mMediaPlayer.seekTo(position)
//        mMediaPlayer.start()
    }
}