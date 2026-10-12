package com.sole.cinevault

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.util.FlagSet
import java.util.IdentityHashMap

/** Routes session commands and reported state to the engine that owns playback. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CineVaultForwardingPlayer(private val rawPlayer: Player) : ForwardingPlayer(rawPlayer) {
    private val proxies = IdentityHashMap<Player.Listener, Player.Listener>()
    private var observedBridge: NativePlaybackBridge? = null
    private fun bridge(): NativePlaybackBridge? = CineVaultPlayerHolder.nativeBridge?.takeIf { it.owner === rawPlayer }
    private fun native(): NativePlaybackSnapshot? = bridge()?.snapshot
    private val nativeListener: (NativePlaybackSnapshot?, NativePlaybackSnapshot?, Boolean) -> Unit = { old, next, seek ->
        val flags = FlagSet.Builder()
        val clients = proxies.keys.toList()
        if (old?.playing != next?.playing || old?.ended != next?.ended || (old == null) != (next == null)) {
            flags.add(Player.EVENT_PLAYBACK_STATE_CHANGED).add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                .add(Player.EVENT_IS_PLAYING_CHANGED).add(Player.EVENT_PLAYER_ERROR)
            clients.forEach {
                it.onPlaybackStateChanged(playbackState)
                it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                it.onIsPlayingChanged(isPlaying)
                it.onPlayerErrorChanged(playerError)
            }
        }
        if (old?.durationMs != next?.durationMs || (old == null) != (next == null)) {
            flags.add(Player.EVENT_TIMELINE_CHANGED).add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
            clients.forEach {
                it.onTimelineChanged(currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                it.onAvailableCommandsChanged(availableCommands)
            }
        }
        if (old?.speed != next?.speed) {
            flags.add(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            clients.forEach { it.onPlaybackParametersChanged(playbackParameters) }
        }
        if (old?.volume != next?.volume) {
            flags.add(Player.EVENT_VOLUME_CHANGED)
            clients.forEach { it.onVolumeChanged(volume) }
        }
        if (seek) {
            flags.add(Player.EVENT_POSITION_DISCONTINUITY)
            val previous = positionInfo(old?.positionMs ?: currentPosition)
            val current = positionInfo(next?.positionMs ?: currentPosition)
            clients.forEach { it.onPositionDiscontinuity(previous, current, Player.DISCONTINUITY_REASON_SEEK) }
        }
        val events = Player.Events(flags.build())
        if (events.size() > 0) clients.forEach { it.onEvents(this, events) }
    }

    private fun positionInfo(position: Long): Player.PositionInfo = Player.PositionInfo(
        null, currentMediaItemIndex, currentMediaItem, null, currentPeriodIndex, position, position,
        C.INDEX_UNSET, C.INDEX_UNSET,
    )

    override fun addListener(listener: Player.Listener) {
        if (proxies.containsKey(listener)) return
        val proxy = object : Player.Listener by listener {
            override fun onEvents(player: Player, events: Player.Events) { listener.onEvents(this@CineVaultForwardingPlayer, events) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { listener.onIsPlayingChanged(this@CineVaultForwardingPlayer.isPlaying) }
            override fun onPlaybackStateChanged(playbackState: Int) { listener.onPlaybackStateChanged(this@CineVaultForwardingPlayer.playbackState) }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { listener.onPlayWhenReadyChanged(this@CineVaultForwardingPlayer.playWhenReady, reason) }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { listener.onPlaybackParametersChanged(this@CineVaultForwardingPlayer.playbackParameters) }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { listener.onTimelineChanged(currentTimeline, reason) }
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { listener.onAvailableCommandsChanged(this@CineVaultForwardingPlayer.availableCommands) }
            override fun onIsLoadingChanged(isLoading: Boolean) { listener.onIsLoadingChanged(this@CineVaultForwardingPlayer.isLoading) }
            override fun onPlayerError(error: PlaybackException) { if (native() == null) listener.onPlayerError(error) }
            override fun onPlayerErrorChanged(error: PlaybackException?) { listener.onPlayerErrorChanged(playerError) }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (native() == null) listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }
        }
        proxies[listener] = proxy
        super.addListener(proxy)
        if (observedBridge == null) {
            observedBridge = bridge()
            observedBridge?.addListener(nativeListener)
        }
    }
    override fun removeListener(listener: Player.Listener) {
        proxies.remove(listener)?.let { super.removeListener(it) }
        if (proxies.isEmpty()) { observedBridge?.removeListener(nativeListener); observedBridge = null }
    }
    override fun play() { if (native() != null) bridge()?.play() else super.play() }
    override fun pause() { if (native() != null) bridge()?.pause() else super.pause() }
    override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) play() else pause() }
    override fun prepare() { if (native() == null) super.prepare() }
    override fun stop() { if (native() != null) bridge()?.stop() else super.stop() }
    override fun seekTo(positionMs: Long) { if (native() != null) bridge()?.seekTo(positionMs) else super.seekTo(positionMs) }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (native() != null && mediaItemIndex == currentMediaItemIndex) bridge()?.seekTo(positionMs) else super.seekTo(mediaItemIndex, positionMs)
    }
    override fun seekToDefaultPosition() { if (native() != null) bridge()?.seekTo(0L) else super.seekToDefaultPosition() }
    override fun seekToDefaultPosition(mediaItemIndex: Int) { if (native() != null && mediaItemIndex == currentMediaItemIndex) bridge()?.seekTo(0L) else super.seekToDefaultPosition(mediaItemIndex) }
    override fun seekBack() { if (native() != null) bridge()?.seekTo(currentPosition - seekBackIncrement) else super.seekBack() }
    override fun seekForward() { if (native() != null) bridge()?.seekTo(currentPosition + seekForwardIncrement) else super.seekForward() }
    override fun setPlaybackSpeed(speed: Float) { if (native() != null) bridge()?.setSpeed(speed) else super.setPlaybackSpeed(speed) }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) { if (native() != null) bridge()?.setSpeed(playbackParameters.speed) else super.setPlaybackParameters(playbackParameters) }
    override fun setVolume(volume: Float) { if (native() != null) bridge()?.setVolume(volume) else super.setVolume(volume) }
    override fun getVolume(): Float = native()?.volume ?: super.getVolume()
    override fun getPlaybackParameters(): PlaybackParameters = native()?.let { PlaybackParameters(it.speed) } ?: super.getPlaybackParameters()
    override fun getCurrentPosition(): Long = native()?.positionMs ?: super.getCurrentPosition()
    override fun getContentPosition(): Long = if (native() != null) currentPosition else super.getContentPosition()
    override fun getDuration(): Long = native()?.durationMs?.takeIf { it > 0 } ?: if (native() != null) C.TIME_UNSET else super.getDuration()
    override fun getContentDuration(): Long = if (native() != null) duration else super.getContentDuration()
    override fun getBufferedPosition(): Long = if (native() != null) currentPosition else super.getBufferedPosition()
    override fun getContentBufferedPosition(): Long = if (native() != null) currentPosition else super.getContentBufferedPosition()
    override fun getTotalBufferedDuration(): Long = if (native() != null) 0L else super.getTotalBufferedDuration()
    override fun getBufferedPercentage(): Int = if (native() != null) { if (duration > 0) (currentPosition * 100L / duration).toInt().coerceIn(0, 100) else 0 } else super.getBufferedPercentage()
    override fun getPlayWhenReady(): Boolean = native()?.playing ?: super.getPlayWhenReady()
    override fun isPlaying(): Boolean = native()?.playing ?: super.isPlaying()
    override fun isLoading(): Boolean = if (native() != null) false else super.isLoading()
    override fun getPlaybackSuppressionReason(): Int = if (native() != null) Player.PLAYBACK_SUPPRESSION_REASON_NONE else super.getPlaybackSuppressionReason()
    override fun getPlaybackState(): Int = native()?.let { if (it.ended) Player.STATE_ENDED else Player.STATE_READY } ?: super.getPlaybackState()
    override fun getPlayerError(): PlaybackException? = if (native() != null) null else super.getPlayerError()
    override fun getCurrentMediaItemIndex(): Int = if (native() != null) 0 else super.getCurrentMediaItemIndex()
    override fun getCurrentPeriodIndex(): Int = if (native() != null) 0 else super.getCurrentPeriodIndex()
    override fun getCurrentTimeline(): Timeline = native()?.let { snapshot ->
        super.getCurrentMediaItem()?.let { NativeSessionTimeline(it, snapshot.durationMs) }
    } ?: super.getCurrentTimeline()


    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands().buildUpon()
        if (native() != null) {
            commands.add(Player.COMMAND_PLAY_PAUSE).add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK).add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SET_SPEED_AND_PITCH).add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        }
        return commands
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> availableCommands.contains(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        CineVaultPlayerHolder.onNextRequested?.invoke()
    }

    override fun seekToNextMediaItem() {
        CineVaultPlayerHolder.onNextRequested?.invoke()
    }

    override fun seekToPrevious() {
        CineVaultPlayerHolder.onPreviousRequested?.invoke()
    }

    override fun seekToPreviousMediaItem() {
        CineVaultPlayerHolder.onPreviousRequested?.invoke()
    }
}
