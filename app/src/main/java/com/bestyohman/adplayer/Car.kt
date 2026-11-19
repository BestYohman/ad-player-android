package com.bestyohman.adplayer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * What Android Auto shows: only things to listen to - the audio description of shows and movies (just the AD
 * track), and audiobooks. Continue listening / watching, Audiobooks, Shows, Movies, and Downloaded.
 */
@OptIn(UnstableApi::class)
class CarLibrary : MediaLibrarySession.Callback {

    // how the car lays out what's in a folder: a list (episodes, books) or a grid (posters)
    private val LIST = 1
    private val GRID = 2
    private fun style(playable: Int, browsable: Int = playable) = Bundle().apply {
        putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", playable)
        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", browsable)
    }

    private fun folder(id: String, title: String, icon: String = "", type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                       look: Int = LIST, art: Uri? = null): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setArtworkUri(art ?: if (icon.isEmpty()) null else Uri.parse("android.resource://${PlayerHolder.app.packageName}/drawable/$icon"))
                .setMediaType(type).setExtras(style(look)).build()
        ).build()

    private fun playable(id: String, title: String, sub: String, art: Uri? = null, type: Int = MediaMetadata.MEDIA_TYPE_TV_SHOW): MediaItem =
        MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(sub).setIsBrowsable(false).setIsPlayable(true)
                    .setArtworkUri(art).setMediaType(type).build()
            ).build()

    /** Why the list is empty: a line to tap (no connection: it opens Downloads). */
    private fun note(e: Exception?): MediaItem {
        val login = (e as? ApiError)?.login == true || Prefs.token.isEmpty() || Api.base().isEmpty()
        return if (login) folder("note", "Sign in on the phone") else folder("downloads", "Can't reach the PC - see Downloads", "ic_tab_download")
    }

    /** The car (and lock screen) may use the extra buttons: 30 seconds back and forward, and a bookmark. */
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        CarButtons.all.forEach { cmds.add(it) }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(cmds.build())
            .setCustomLayout(CarButtons.layout(Engine.mode == Engine.Mode.BOOK)).build()
    }

    override fun onCustomCommand(
        session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle
    ): ListenableFuture<SessionResult> =
        Futures.immediateFuture(SessionResult(if (CarButtons.press(customCommand.customAction)) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_NOT_SUPPORTED))

    override fun onGetLibraryRoot(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        // the phone's "carry on" card after a restart asks for just the last thing played
        if (params?.isRecent == true) return Futures.immediateFuture(LibraryResult.ofItem(folder("last", "AD Player"), params))
        val p = LibraryParams.Builder().setExtras(style(LIST, LIST)).build()
        return Futures.immediateFuture(LibraryResult.ofItem(folder("root", "AD Player"), p))
    }

    // lists asked for in the last minute are shown again straight away (the car asks again each time it's opened)
    private val cache = HashMap<String, Pair<Long, List<MediaItem>>>()

    override fun onGetChildren(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val f = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        Engine.scope.launch {
            val now = android.os.SystemClock.elapsedRealtime()
            val hit = cache[parentId]?.takeIf { now - it.first < 60_000 }?.second
            val list = hit ?: try {
                val needPc = parentId !in listOf("root", "libraries", "downloads", "note", "last")
                if (needPc && (Prefs.token.isEmpty() || Api.base().isEmpty())) listOf(note(null))
                else children(parentId).also { if (needPc) cache[parentId] = now to it }
            } catch (e: Exception) { listOf(note(e)) }
            f.set(LibraryResult.ofItemList(ImmutableList.copyOf(list), params))
        }
        return f
    }

    private suspend fun list(kind: String, parent: String = ""): List<Item> =
        Api.get("/api/play/list?kind=$kind&device=__local&parent=${enc(parent)}").optJSONArray("items").objs().map { Item(it) }

    private suspend fun books(kind: String): List<Item> =
        Api.get("/api/play/books/list?kind=$kind").optJSONArray("items").objs().map { Item(it) }.filter { it.type == "Book" }

    /** A book: its cover, the author, and where you are in it. */
    private fun bookItem(it: Item): MediaItem =
        playable("book:${it.id}", it.label, listOf(it.j.optString("author"), if (it.resume.isNotEmpty()) "at ${it.resume}" else "")
            .filter { s -> s.isNotEmpty() }.joinToString(" · "), ArtProvider.uri("/api/play/books/cover/${it.id}"), MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)

    private fun ad(it: Item): MediaItem =
        playable("ad:${it.id}", it.label, listOf(it.length, if (it.resume.isNotEmpty()) "resumes at ${it.resume}" else "")
            .filter { s -> s.isNotEmpty() }.joinToString(", "),
            ArtProvider.uri(it.poster.ifEmpty { it.thumb }),
            if (it.type == "Movie") MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_TV_SHOW)

    /** Two lists from the PC at the same time; if both fail, the error (so the car can say why). */
    private suspend fun both(a: suspend () -> List<Item>, b: suspend () -> List<Item>): Pair<List<Item>, List<Item>> = coroutineScope {
        val x = async { try { Result.success(a()) } catch (e: Exception) { Result.failure(e) } }
        val y = async { try { Result.success(b()) } catch (e: Exception) { Result.failure(e) } }
        val ra = x.await(); val rb = y.await()
        if (ra.isFailure && rb.isFailure) throw ra.exceptionOrNull()!!
        (ra.getOrNull() ?: emptyList()) to (rb.getOrNull() ?: emptyList())
    }

    private suspend fun children(parentId: String): List<MediaItem> {
        when {
            // the car's tabs: Continue, Recent, Libraries, Downloads
            parentId == "root" -> return listOf(
                folder("continue_all", "Continue", "ic_tab_continue"), folder("recent", "Recent", "ic_tab_recent"),
                folder("libraries", "Libraries", "ic_tab_library"), folder("downloads", "Downloads", "ic_tab_download")
            )
            parentId == "libraries" -> return listOf(
                folder("books_all", "Audiobooks", "ic_tab_book", MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS),
                folder("shows", "Shows", "ic_tab_tv", MediaMetadata.MEDIA_TYPE_FOLDER_TV_SERIES, GRID),
                folder("movies", "Movies", "ic_tab_movie", MediaMetadata.MEDIA_TYPE_FOLDER_MOVIES, GRID)
            )
            // just the last thing played (the phone's card after a restart)
            parentId == "last" -> {
                val (id, title) = Engine.lastPlayed() ?: return emptyList()
                return listOf(playable(id, title.ifEmpty { "Carry on" }, "AD Player", null,
                    if (id.startsWith("book:")) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK else MediaMetadata.MEDIA_TYPE_TV_SHOW))
            }
            // the newest books, episodes and movies with AD, newest first
            parentId == "recent" -> {
                val (books, av) = both({ books("books_recent") }, { list("recent") })
                return (books.map { it to true } + av.map { it to false })
                    .sortedByDescending { it.first.j.optDouble("added", 0.0) }.take(40)
                    .map { (it, book) -> if (book) bookItem(it) else ad(it) }
            }
            // books and shows / movies together, the one played most recently first
            parentId == "continue_all" -> {
                // the books and the shows and movies (one list from the PC, "continue") at the same time
                val (books, av0) = both({ books("books_continue") }, { list("continue") })
                val av = av0.filter { it.hasAd != false }
                val all = books.map { it to true } + av.map { it to false }
                return all.withIndex().sortedWith(compareByDescending<IndexedValue<Pair<Item, Boolean>>> { it.value.first.j.optDouble("last", 0.0) }
                    .thenBy { it.index }).map { (_, v) ->
                    val (it, book) = v
                    if (book) bookItem(it) else ad(it)
                }
            }
            parentId == "books_continue" || parentId == "books_all" -> return books(parentId).map { bookItem(it) }
            parentId == "continue" -> return list("continue").filter { it.hasAd != false }.map { ad(it) }
            parentId == "shows" -> return list("shows").map {
                folder("series:${it.id}", it.label, type = MediaMetadata.MEDIA_TYPE_TV_SERIES, art = ArtProvider.uri(it.poster))
            }
            parentId.startsWith("series:") -> return list("seasons", parentId.substringAfter(':')).map {
                folder("season:${it.id}", it.label, type = MediaMetadata.MEDIA_TYPE_TV_SEASON)
            }
            parentId.startsWith("season:") ->
                return list("episodes", parentId.substringAfter(':')).filter { it.hasAd != false }.map { ad(it) }
            parentId == "movies" -> return list("movies").filter { it.hasAd != false }.map { ad(it) }
            parentId == "downloads" -> return Offline.items.filter { it.done && it.kind != "video" }.map {
                if (it.kind == "book") playable("book:${it.id}", it.title, it.author, null, MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                else playable("ad:${it.id}", it.title, "Downloaded", null, if (it.itemType == "Movie") MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_TV_SHOW)
            }
        }
        return emptyList()
    }

    /** Something chosen in the car (or asked for by voice: "Hey Google, play ... on AD Player"): start it (the app
     *  does the work), then hand the car what's playing. */
    override fun onSetMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        startIndex: Int, startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val first = mediaItems.getOrNull(if (startIndex in mediaItems.indices) startIndex else 0)
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Nothing chosen"))
        val q = first.requestMetadata.searchQuery
        if (first.mediaId.isEmpty() && q != null) return startThen { Engine.playFromSearch(q.toString()) }
        return startThen { Engine.playForCar(first.mediaId, first.mediaMetadata.title?.toString() ?: "") }
    }

    /** The headset's or the car's Play with nothing loaded (after the phone restarted, say): the last book or
     *  audio description, from where she left off. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val (id, title) = Engine.lastPlayed() ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to carry on with"))
        return startThen { Engine.playForCar(id, title) }
    }

    private fun startThen(go: suspend () -> Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val f = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        Engine.scope.launch {
            val ok = try { go() } catch (e: Exception) { false }
            if (!ok) { f.setException(IllegalStateException("Couldn't start it")); return@launch }
            val exo = PlayerHolder.exo
            val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
            Engine.adoptNext()
            f.set(MediaSession.MediaItemsWithStartPosition(items, exo.currentMediaItemIndex, exo.currentPosition))
        }
        return f
    }
}
