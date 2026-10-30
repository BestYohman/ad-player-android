package com.bestyohman.adplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The listener's whole audio track, kept on the phone (the last few), so it plays without the network. */
object Downloads {
    private lateinit var dir: File

    fun init(c: Context) {
        if (!::dir.isInitialized) dir = File(c.applicationContext.cacheDir, "listen").apply { mkdirs() }
    }

    /** Downloads [url] as [name] (carrying on where it stopped if the connection dropped). Null if it didn't finish. */
    suspend fun get(url: String, name: String): File? = withContext(Dispatchers.IO) {
        val f = File(dir, name)
        if (f.exists() && f.length() > 1000) { f.setLastModified(System.currentTimeMillis()); return@withContext f }
        val tmp = File(dir, "$name.part")
        try {
            val have = if (tmp.exists()) tmp.length() else 0L
            val rb = Request.Builder().url(url)
            if (have > 0) rb.header("Range", "bytes=$have-")
            Api.http.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null
                val append = have > 0 && r.code == 206
                FileOutputStream(tmp, append).use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
            if (tmp.length() < 1000) return@withContext null
            if (!tmp.renameTo(f)) return@withContext null
            prune()
            f
        } catch (e: IOException) {
            null                       // the part kept so far is used next time
        }
    }

    /** Downloads [url] to [f] (carrying on where it stopped). Null if it didn't finish. */
    suspend fun fetch(url: String, f: File): File? = withContext(Dispatchers.IO) {
        if (f.exists() && f.length() > 1000) { f.setLastModified(System.currentTimeMillis()); return@withContext f }
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".part")
        try {
            val have = if (tmp.exists()) tmp.length() else 0L
            val rb = Request.Builder().url(url)
            if (have > 0) rb.header("Range", "bytes=$have-")
            Api.http.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null
                FileOutputStream(tmp, have > 0 && r.code == 206).use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
            if (tmp.length() < 1000 || !tmp.renameTo(f)) return@withContext null
            f
        } catch (e: IOException) { null }
    }

    /** A book's pieces, kept on the phone while it's played (the piece it's on, and the next few). */
    fun bookDir(bookId: String): File = File(dir.parentFile, "bookparts/" + bookId.replace(Regex("[^\\w-]"), "_"))

    fun bookPart(bookId: String, i: Int, url: String): File =
        File(bookDir(bookId), "$i." + url.substringAfterLast('.', "mp3").substringBefore('?').take(4))

    /** Only the book being played keeps its pieces. */
    fun keepOnlyBook(bookId: String) {
        val keep = bookDir(bookId)
        keep.parentFile?.listFiles()?.forEach { if (it != keep) it.deleteRecursively() }
    }

    private fun prune() {
        val files = dir.listFiles()?.filter { it.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(4).forEach { it.delete() }
    }
}
