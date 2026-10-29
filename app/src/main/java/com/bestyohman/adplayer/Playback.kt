package com.bestyohman.adplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession

/** The one player for everything this phone plays (AD audio, video, audiobooks). */
object PlayerHolder {
    lateinit var exo: ExoPlayer
    lateinit var session: Player          // what the lock screen, headset and the video's own controls talk to
    lateinit var app: Context

    @OptIn(UnstableApi::class)
    fun init(ctx: Context) {
        if (::exo.isInitialized) return
        app = ctx.applicationContext
        exo = ExoPlayer.Builder(app)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OkHttpDataSource.Factory(Api.http)).setLoadErrorHandlingPolicy(PatientPolicy())
            )
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // keep the last minute that played, so going back a little (or evening out with the other phone)
            // doesn't load it again over the phone network
            // load well ahead (up to 10 minutes; video is held to about 64 MB), so a weak spot or a
            // switch between Wi-Fi and mobile data doesn't stop it
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBackBuffer(60_000, true)
                    .setBufferDurationsMs(60_000, 600_000, 2_000, 5_000)
                    .setTargetBufferBytes(64 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build()
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.addListener(Engine.playerListener)
        session = SessionPlayer(exo)
    }
}

/** When the connection drops, keep trying (every few seconds) instead of giving up; the phone carries on when it's back.
 *  Only "this isn't there any more" answers from the PC stop it (the app then asks the PC again). */
@OptIn(UnstableApi::class)
class PatientPolicy : DefaultLoadErrorHandlingPolicy(Int.MAX_VALUE) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = loadErrorInfo.exception
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in listOf(401, 403, 404, 410)) return C.TIME_UNSET
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        return minOf(1000L * loadErrorInfo.errorCount, 5000L)
    }
}

/** Passes the phone's own buttons to the app first: playing together, next episode, chapters. */
class SessionPlayer(p: Player) : ForwardingPlayer(p) {
    override fun play() { if (!Engine.onTransport("play")) super.play() }
    override fun pause() { if (!Engine.onTransport("pause")) super.pause() }
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!Engine.onTransport(if (playWhenReady) "play" else "pause")) super.setPlayWhenReady(playWhenReady)
    }
    override fun seekBack() { if (!Engine.onTransport("back30")) super.seekBack() }
    override fun seekForward() { if (!Engine.onTransport("fwd30")) super.seekForward() }
    override fun seekToNext() { if (!Engine.onTransport("next")) super.seekToNext() }
    override fun seekToNextMediaItem() { if (!Engine.onTransport("next")) super.seekToNextMediaItem() }
    override fun seekToPrevious() { if (!Engine.onTransport("prev")) super.seekToPrevious() }
    override fun seekToPreviousMediaItem() { if (!Engine.onTransport("prev")) super.seekToPreviousMediaItem() }
    override fun seekTo(positionMs: Long) { if (!Engine.onSeek(currentMediaItemIndex, positionMs)) super.seekTo(positionMs) }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { if (!Engine.onSeek(mediaItemIndex, positionMs)) super.seekTo(mediaItemIndex, positionMs) }

    // the car already started it (see CarLibrary): don't load it a second time
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!Engine.takeAdopt()) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (!Engine.takeAdopt()) super.setMediaItems(mediaItems, resetPosition)
    }
    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!Engine.takeAdopt()) super.setMediaItems(mediaItems)
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_BACK).add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_BACK || command == Player.COMMAND_SEEK_FORWARD ||
            command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            super.isCommandAvailable(command)
}

/** Keeps playing with the screen off, with the lock-screen and notification controls - and is what Android Auto uses. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PlayerHolder.init(this)
        Offline.init(this)
        Downloads.init(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaLibrarySession.Builder(this, PlayerHolder.session, CarLibrary()).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
