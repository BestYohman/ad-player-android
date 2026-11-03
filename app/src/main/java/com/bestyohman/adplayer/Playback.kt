package com.bestyohman.adplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import kotlin.math.max
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** The one player for everything this phone plays (AD audio, video, audiobooks). */
object PlayerHolder {
    lateinit var exo: ExoPlayer
    lateinit var session: SessionPlayer   // what the lock screen, headset, the car and the video's own controls talk to
    lateinit var app: Context
    var media: MediaLibrarySession? = null  // the session itself (its buttons change between books and the rest)

    @OptIn(UnstableApi::class)
    fun init(ctx: Context) {
        if (::exo.isInitialized) return
        app = ctx.applicationContext
        exo = ExoPlayer.Builder(app)
            .setMediaSourceFactory(
                // files on the phone (downloads) and the PC's links (through OkHttp, with the sign-in) both work;
                // MP3s without an index can still jump straight to where she left off (not read from the start)
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(app, OkHttpDataSource.Factory(Api.http)),
                    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true).setConstantBitrateSeekingAlwaysEnabled(true)
                ).setLoadErrorHandlingPolicy(PatientPolicy())
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

/** The car's (and lock screen's) extra buttons: 30 seconds back and forward, and a bookmark (books only). */
object CarButtons {
    val back30 = SessionCommand("adp.back30", Bundle.EMPTY)
    val fwd30 = SessionCommand("adp.fwd30", Bundle.EMPTY)
    val bookmark = SessionCommand("adp.bookmark", Bundle.EMPTY)
    val all = listOf(back30, fwd30, bookmark)

    @OptIn(UnstableApi::class)
    fun layout(book: Boolean): List<CommandButton> {
        val b = mutableListOf(
            CommandButton.Builder().setDisplayName("Back 30 seconds").setIconResId(R.drawable.ic_back30).setSessionCommand(back30).build(),
            CommandButton.Builder().setDisplayName("Forward 30 seconds").setIconResId(R.drawable.ic_fwd30).setSessionCommand(fwd30).build()
        )
        if (book) b.add(CommandButton.Builder().setDisplayName("Add a bookmark").setIconResId(R.drawable.ic_bookmark_add).setSessionCommand(bookmark).build())
        return b
    }

    /** A button was pressed: true if it was one of these. */
    fun press(action: String): Boolean {
        when (action) {
            back30.customAction -> Engine.cmd("back30")
            fwd30.customAction -> Engine.cmd("fwd30")
            bookmark.customAction -> Engine.addBookmark()          // named by the PC: the chapter and the time
            else -> return false
        }
        return true
    }
}

/** Passes the phone's own buttons to the app first: playing together, next episode, chapters.
 *  For an audiobook with chapters, what the car and lock screen see is the chapter: the bar runs through the chapter
 *  (how long is left of it), the title says which chapter it is, and the line under it how far into the whole book. */
@OptIn(UnstableApi::class)
class SessionPlayer(p: Player) : ForwardingPlayer(p) {
    private data class Chap(val start: Double, val end: Double, val i: Int, val n: Int, val title: String)

    /** The chapter playing now, when an audiobook with chapters is. */
    private fun chap(): Chap? {
        if (Engine.mode != Engine.Mode.BOOK) return null
        val b = Engine.bk ?: return null
        if (!b.ready || b.chapters.isEmpty()) return null
        val i = Engine.chapterAt(Engine.bookPos()).coerceIn(0, b.chapters.size - 1)
        val c = b.chapters[i]
        val end = if (c.end > c.start) c.end else b.chapters.getOrNull(i + 1)?.start ?: b.duration
        return Chap(c.start, max(end, c.start + 1), i, b.chapters.size, c.title)
    }

    override fun getDuration(): Long = chap()?.let { ((it.end - it.start) * 1000).toLong() } ?: super.getDuration()
    override fun getContentDuration(): Long = chap()?.let { ((it.end - it.start) * 1000).toLong() } ?: super.getContentDuration()
    override fun getCurrentPosition(): Long =
        chap()?.let { ((Engine.bookPos() - it.start) * 1000).toLong().coerceIn(0, ((it.end - it.start) * 1000).toLong()) } ?: super.getCurrentPosition()
    override fun getContentPosition(): Long = currentPosition
    override fun getBufferedPosition(): Long {
        val c = chap() ?: return super.getBufferedPosition()
        val b = Engine.bk ?: return super.getBufferedPosition()
        val i = super.getCurrentMediaItemIndex().coerceIn(0, max(0, b.tracks.size - 1))
        val abs = (b.tracks.getOrNull(i)?.start ?: 0.0) + super.getBufferedPosition() / 1000.0
        return ((abs - c.start) * 1000).toLong().coerceIn(0, ((c.end - c.start) * 1000).toLong())
    }
    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val c = chap() ?: return base
        val b = Engine.bk ?: return base
        val name = c.title.trim()
        val title = when {
            name.isEmpty() -> "Chapter ${c.i + 1} of ${c.n}"
            Regex("^(chapter|ch\\.?|part)\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "$name (${c.i + 1} of ${c.n})"
            else -> "Chapter ${c.i + 1} of ${c.n}: $name"
        }
        val played = "${fmtTime(Engine.bookPos())} of ${fmtTime(b.duration)}"
        return base.buildUpon().setTitle(title).setDisplayTitle(title)
            .setArtist("$played · ${b.title}").setSubtitle("$played · ${b.title}").setAlbumTitle(b.title)
            .setAlbumArtist(b.author).build()
    }

    // the car's bar is the chapter: a place on it is a place in this chapter
    override fun seekTo(positionMs: Long) {
        val c = chap()
        if (c != null) { Engine.seekTo(c.start + positionMs / 1000.0); return }
        if (!Engine.onSeek(currentMediaItemIndex, positionMs)) super.seekTo(positionMs)
    }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val c = chap()
        if (c != null && mediaItemIndex == super.getCurrentMediaItemIndex()) { Engine.seekTo(c.start + positionMs / 1000.0); return }
        if (!Engine.onSeek(mediaItemIndex, positionMs)) super.seekTo(mediaItemIndex, positionMs)
    }

    // the car and lock screen are told when the chapter (or the minute played) changes, so they show it
    // (each listener gets this player's own idea of the details - the chapter - also when the underlying player says
    // its details changed)
    private val listeners = LinkedHashMap<Player.Listener, Player.Listener>()
    override fun addListener(listener: Player.Listener) {
        val w = object : Player.Listener by listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = listener.onMediaMetadataChanged(this@SessionPlayer.mediaMetadata)
        }
        synchronized(listeners) { listeners[listener] = w }
        super.addListener(w)
    }
    override fun removeListener(listener: Player.Listener) {
        val w = synchronized(listeners) { listeners.remove(listener) } ?: listener
        super.removeListener(w)
    }
    private val ticker = Handler(Looper.getMainLooper())
    private var lastKey = ""
    private var lastBook: Boolean? = null
    private val tick = object : Runnable {
        override fun run() {
            try {
                val c = chap()
                val key = if (c == null) "" else "${Engine.bk?.id}|${c.i}|${(Engine.bookPos() / 60).toInt()}"
                if (key != lastKey) {
                    val chapterChanged = key.substringBeforeLast('|') != lastKey.substringBeforeLast('|')
                    lastKey = key
                    val mm = mediaMetadata
                    val ls = synchronized(listeners) { listeners.keys.toList() }
                    ls.forEach { it.onMediaMetadataChanged(mm) }
                    if (chapterChanged) {
                        // the bar starts again for the new chapter
                        val idx = super@SessionPlayer.getCurrentMediaItemIndex()
                        val pos = currentPosition
                        val info = Player.PositionInfo(null, idx, null, null, idx, pos, pos, C.INDEX_UNSET, C.INDEX_UNSET)
                        ls.forEach { it.onPositionDiscontinuity(info, info, Player.DISCONTINUITY_REASON_INTERNAL) }
                    }
                }
                val book = Engine.mode == Engine.Mode.BOOK
                if (book != lastBook) { lastBook = book; PlayerHolder.media?.setCustomLayout(CarButtons.layout(book)) }
            } catch (e: Exception) { }
            ticker.postDelayed(this, 1000)
        }
    }
    init { ticker.postDelayed(tick, 1000) }

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
            .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_BACK || command == Player.COMMAND_SEEK_FORWARD ||
            command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
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
        mediaSession = MediaLibrarySession.Builder(this, PlayerHolder.session, CarLibrary()).setSessionActivity(open)
            .setCustomLayout(CarButtons.layout(Engine.mode == Engine.Mode.BOOK)).build()
        PlayerHolder.media = mediaSession
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlayerHolder.media = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
