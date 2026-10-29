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

    private fun prune() {
        val files = dir.listFiles()?.filter { it.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(4).forEach { it.delete() }
    }
}
