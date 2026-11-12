package com.example.ipradio

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal class StationPlayer(private val delegate: Player, private val skip: (Int) -> Unit) :
    ForwardingPlayer(delegate) {
    private val listeners = mutableMapOf<Player.Listener, Player.Listener>()

    override fun getAvailableCommands(): Player.Commands = withStationCommands(delegate.availableCommands)

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun addListener(listener: Player.Listener) {
        if (listener in listeners) return
        val forwardingListener = StationListener(listener)
        listeners[listener] = forwardingListener
        super.addListener(forwardingListener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }

    override fun seekToPrevious() = skip(-1)
    override fun seekToPreviousMediaItem() = skip(-1)
    override fun seekToNext() = skip(1)
    override fun seekToNextMediaItem() = skip(1)

    override fun stop() {
        delegate.pause()
        delegate.stop()
    }

    // Kotlin interface delegation skips Java default methods. MediaSession consumes these
    // callbacks individually to publish current playback state and metadata to controllers.
    private inner class StationListener(private val listener: Player.Listener) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            listener.onAvailableCommandsChanged(withStationCommands(availableCommands))

        override fun onEvents(player: Player, events: Player.Events) =
            listener.onEvents(player, events)

        override fun onTimelineChanged(timeline: Timeline, reason: Int) =
            listener.onTimelineChanged(timeline, reason)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            listener.onMediaItemTransition(mediaItem, reason)

        override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged(tracks)

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
            listener.onMediaMetadataChanged(mediaMetadata)

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) =
            listener.onPlaylistMetadataChanged(mediaMetadata)

        override fun onIsLoadingChanged(isLoading: Boolean) = listener.onIsLoadingChanged(isLoading)

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
            listener.onTrackSelectionParametersChanged(parameters)

        override fun onPlaybackStateChanged(playbackState: Int) =
            listener.onPlaybackStateChanged(playbackState)

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
            listener.onPlayWhenReadyChanged(playWhenReady, reason)

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
            listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)

        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)

        override fun onRepeatModeChanged(repeatMode: Int) = listener.onRepeatModeChanged(repeatMode)

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
            listener.onShuffleModeEnabledChanged(shuffleModeEnabled)

        override fun onPlayerError(error: PlaybackException) = listener.onPlayerError(error)

        override fun onPlayerErrorChanged(error: PlaybackException?) =
            listener.onPlayerErrorChanged(error)

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) = listener.onPositionDiscontinuity(oldPosition, newPosition, reason)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
            listener.onPlaybackParametersChanged(playbackParameters)

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
            listener.onSeekBackIncrementChanged(seekBackIncrementMs)

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
            listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)

        override fun onVideoSizeChanged(videoSize: VideoSize) = listener.onVideoSizeChanged(videoSize)

        override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
            listener.onAudioAttributesChanged(audioAttributes)

        override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)

        override fun onCues(cueGroup: CueGroup) = listener.onCues(cueGroup)

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) =
            listener.onDeviceInfoChanged(deviceInfo)

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) =
            listener.onDeviceVolumeChanged(volume, muted)
    }

    private fun withStationCommands(commands: Player.Commands): Player.Commands = commands.buildUpon()
        .addAll(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )
        .build()
}
