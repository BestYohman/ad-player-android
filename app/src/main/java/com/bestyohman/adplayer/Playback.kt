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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** The one player for everything this phone plays (AD audio, video, audiobooks). */
object PlayerHolder {
    lateinit var exo: ExoPlayer
    lateinit var session: Player          // what the lock screen, headset and the video's own controls talk to

    @OptIn(UnstableApi::class)
    fun init(ctx: Context) {
        if (::exo.isInitialized) return
        val app = ctx.applicationContext
        exo = ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Api.http)))
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.addListener(Engine.playerListener)
        session = SessionPlayer(exo)
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

/** Keeps playing with the screen off, with the lock-screen and notification controls. */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PlayerHolder.init(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, PlayerHolder.session).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
