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
import kotlinx.coroutines.launch

/**
 * What Android Auto shows: only things to listen to - the audio description of shows and movies (just the AD
 * track), and audiobooks. Continue listening / watching, Audiobooks, Shows, Movies, and Downloaded.
 */
@OptIn(UnstableApi::class)
class CarLibrary : MediaLibrarySession.Callback {

    private fun folder(id: String, title: String, icon: String = ""): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setArtworkUri(if (icon.isEmpty()) null else Uri.parse("android.resource://${PlayerHolder.app.packageName}/drawable/$icon"))
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
        ).build()

    private fun playable(id: String, title: String, sub: String, art: Uri? = null, book: Boolean = false): MediaItem =
        MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(sub).setIsBrowsable(false).setIsPlayable(true)
                    .setArtworkUri(art)
                    .setMediaType(if (book) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK else MediaMetadata.MEDIA_TYPE_TV_SHOW).build()
            ).build()

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
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder("root", "AD Player"), params))

    override fun onGetChildren(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val f = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        Engine.scope.launch {
            val list = try { children(parentId) } catch (e: Exception) { emptyList() }
            f.set(LibraryResult.ofItemList(ImmutableList.copyOf(list), params))
        }
        return f
    }

    private suspend fun list(kind: String, parent: String = ""): List<Item> =
        Api.get("/api/play/list?kind=$kind&device=__local&parent=${enc(parent)}").optJSONArray("items").objs().map { Item(it) }

    /** A book: its cover, the author, and where you are in it. */
    private fun bookItem(it: Item): MediaItem =
        playable("book:${it.id}", it.label, listOf(it.j.optString("author"), if (it.resume.isNotEmpty()) "at ${it.resume}" else "")
            .filter { s -> s.isNotEmpty() }.joinToString(" · "), Uri.parse(Api.url("/api/play/books/cover/${it.id}")), true)

    private fun ad(it: Item): MediaItem =
        playable("ad:${it.id}", it.label, listOf(it.length, if (it.resume.isNotEmpty()) "resumes at ${it.resume}" else "")
            .filter { s -> s.isNotEmpty() }.joinToString(", "),
            (it.poster.ifEmpty { it.thumb }).takeIf { p -> p.isNotEmpty() }?.let { p -> Uri.parse(Api.url(p)) })

    private suspend fun children(parentId: String): List<MediaItem> {
        when {
            // the car's tabs: Continue, Recent, Libraries, Downloads
            parentId == "root" -> return listOf(
                folder("continue_all", "Continue", "ic_tab_continue"), folder("recent", "Recent", "ic_tab_recent"),
                folder("libraries", "Libraries", "ic_tab_library"), folder("downloads", "Downloads", "ic_tab_download")
            )
            parentId == "libraries" -> return listOf(
                folder("books_all", "Audiobooks", "ic_tab_book"), folder("shows", "Shows", "ic_tab_tv"), folder("movies", "Movies", "ic_tab_movie")
            )
            // the newest books, episodes and movies with AD, newest first
            parentId == "recent" -> {
                val books = try { Api.get("/api/play/books/list?kind=books_recent").optJSONArray("items").objs().map { Item(it) }
                    .filter { it.type == "Book" } } catch (e: Exception) { emptyList() }
                val av = try { list("recent") } catch (e: Exception) { emptyList() }
                return (books.map { it to true } + av.map { it to false })
                    .sortedByDescending { it.first.j.optDouble("added", 0.0) }.take(40)
                    .map { (it, book) -> if (book) bookItem(it) else ad(it) }
            }
            // books and shows / movies together, the one played most recently first
            parentId == "continue_all" -> {
                val books = try { Api.get("/api/play/books/list?kind=books_continue").optJSONArray("items").objs().map { Item(it) }
                    .filter { it.type == "Book" } } catch (e: Exception) { emptyList() }
                val av = try { (list("continue_shows") + list("continue_movies")).filter { it.hasAd != false } } catch (e: Exception) { emptyList() }
                val all = books.map { it to true } + av.map { it to false }
                return all.withIndex().sortedWith(compareByDescending<IndexedValue<Pair<Item, Boolean>>> { it.value.first.j.optDouble("last", 0.0) }
                    .thenBy { it.index }).map { (_, v) ->
                    val (it, book) = v
                    if (book) bookItem(it) else ad(it)
                }
            }
            parentId == "books_continue" || parentId == "books_all" ->
                return Api.get("/api/play/books/list?kind=$parentId").optJSONArray("items").objs().map { Item(it) }
                    .filter { it.type == "Book" }
                    .map { bookItem(it) }
            parentId == "continue" ->
                return (list("continue_shows") + list("continue_movies")).filter { it.hasAd != false }.map { ad(it) }
            parentId == "shows" -> return list("shows").map { folder("series:${it.id}", it.label) }
            parentId.startsWith("series:") -> return list("seasons", parentId.substringAfter(':')).map { folder("season:${it.id}", it.label) }
            parentId.startsWith("season:") ->
                return list("episodes", parentId.substringAfter(':')).filter { it.hasAd != false }.map { ad(it) }
            parentId == "movies" -> return list("movies").filter { it.hasAd != false }.map { ad(it) }
            parentId == "downloads" -> return Offline.items.filter { it.done && it.kind != "video" }.map {
                if (it.kind == "book") playable("book:${it.id}", it.title, it.author, null, true)
                else playable("ad:${it.id}", it.title, "Downloaded")
            }
        }
        return emptyList()
    }

    /** Something chosen in the car: start it (the app does the work), then hand the car what's playing. */
    override fun onSetMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        startIndex: Int, startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val first = mediaItems.getOrNull(if (startIndex in mediaItems.indices) startIndex else 0)
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Nothing chosen"))
        val f = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        Engine.scope.launch {
            val ok = try { Engine.playForCar(first.mediaId, first.mediaMetadata.title?.toString() ?: "") } catch (e: Exception) { false }
            if (!ok) { f.setException(IllegalStateException("Couldn't start it")); return@launch }
            val exo = PlayerHolder.exo
            val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
            Engine.adoptNext()
            f.set(MediaSession.MediaItemsWithStartPosition(items, exo.currentMediaItemIndex, exo.currentPosition))
        }
        return f
    }
}
