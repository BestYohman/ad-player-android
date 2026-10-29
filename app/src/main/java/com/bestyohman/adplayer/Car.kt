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

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
        ).build()

    private fun playable(id: String, title: String, sub: String, art: Uri? = null, book: Boolean = false): MediaItem =
        MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(sub).setIsBrowsable(false).setIsPlayable(true)
                    .setArtworkUri(art)
                    .setMediaType(if (book) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK else MediaMetadata.MEDIA_TYPE_TV_SHOW).build()
            ).build()

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

    private fun ad(it: Item): MediaItem =
        playable("ad:${it.id}", it.label, listOf(it.length, if (it.resume.isNotEmpty()) "resumes at ${it.resume}" else "")
            .filter { s -> s.isNotEmpty() }.joinToString(", "))

    private suspend fun children(parentId: String): List<MediaItem> {
        when {
            parentId == "root" -> return listOf(
                folder("books_continue", "Continue listening"), folder("continue", "Continue watching"),
                folder("books_all", "Audiobooks"), folder("shows", "Shows"), folder("movies", "Movies"),
                folder("downloads", "Downloaded")
            )
            parentId == "books_continue" || parentId == "books_all" ->
                return Api.get("/api/play/books/list?kind=$parentId").optJSONArray("items").objs().map { Item(it) }
                    .filter { it.type == "Book" }
                    .map { playable("book:${it.id}", it.label, it.author, Uri.parse(Api.url("/api/play/books/cover/${it.id}")), true) }
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
