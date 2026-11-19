package com.bestyohman.adplayer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Pictures for the car (posters, book covers). Android Auto can't fetch the PC's links itself (they need the
 * sign-in), so it is given content://<app>.art/<the PC's picture> instead: this fetches the picture once and keeps
 * it on the phone. Only the PC's pictures are handed out, nothing else.
 */
class ArtProvider : ContentProvider() {
    companion object {
        private val ALLOWED = Regex("^/api/play/(img/[\\w-]+/\\w+|books/cover/[\\w-]+|chapimg/[\\w-]+/\\d+)(\\?[\\w=&%.-]*)?$")

        /** The car's link for one of the PC's pictures ("/api/play/img/..."); null when there is none. */
        fun uri(path: String): Uri? {
            if (!ALLOWED.matches(path)) return null
            return Uri.Builder().scheme("content").authority("${PlayerHolder.app.packageName}.art").appendPath(path).build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        val path = uri.lastPathSegment ?: throw FileNotFoundException("No picture")
        if (!ALLOWED.matches(path)) throw FileNotFoundException("Not a picture")
        Prefs.init(ctx.applicationContext)
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, "$name.img")
        if (!f.exists() || f.length() == 0L || System.currentTimeMillis() - f.lastModified() > 7 * 86_400_000L) {
            val tmp = File.createTempFile(name, ".part", dir)
            try {
                Api.http.newCall(Request.Builder().url(Api.url(path)).build()).execute().use { r ->
                    val body = r.body
                    if (!r.isSuccessful || body == null || body.contentType()?.type != "image") {
                        if (f.exists() && f.length() > 0) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                        throw FileNotFoundException("No picture")
                    }
                    tmp.outputStream().use { o -> body.byteStream().copyTo(o) }
                }
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            } catch (e: FileNotFoundException) {
                throw e
            } catch (e: Exception) {
                if (!(f.exists() && f.length() > 0)) throw FileNotFoundException("Couldn't fetch the picture")
            } finally {
                tmp.delete()
            }
            // at most 400 pictures kept (the oldest go)
            dir.listFiles { x -> x.name.endsWith(".img") }?.let { all ->
                if (all.size > 400) all.sortedBy { it.lastModified() }.take(all.size - 400).forEach { it.delete() }
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
