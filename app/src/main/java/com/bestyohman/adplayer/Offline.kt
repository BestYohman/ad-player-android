package com.bestyohman.adplayer

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Download for later": a show, movie or audiobook kept on the phone, so it plays with no connection at all.
 * The PC first makes one file the phone can play (the video with its soundtrack, or just the audio description);
 * Android's own downloader then fetches it in the background (it carries on through a weak signal, shows its
 * progress in the notifications, and keeps going with the app closed).
 */
object Offline {
    data class Saved(
        val id: String,                 // the Emby item, or the Audiobookshelf book
        val kind: String,               // video, audio or book
        val title: String,
        val state: String = "preparing", // preparing (the PC makes the file), downloading, done, failed
        val progress: Int = 0,
        val bytes: Long = 0,
        val runtime: Double = 0.0,
        val itemType: String = "",
        val hasNext: Boolean = false,
        val job: String = "",           // the PC's job for the file
        val url: String = "",           // where the PC has it (without the address, which can change)
        val file: String = "",
        val dm: Long = -1,
        val author: String = "",
        val duration: Double = 0.0,
        val chapters: String = "[]",    // books: [{start, end, title}]
        val parts: String = "[]",       // books: [{start, duration, url, file, dm}]
        val pos: Double = 0.0,          // where it was left, kept on the phone too
        val error: String = "",
        val retries: Int = 0,
        val quality: String = "original" // video: original, medium or small
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("kind", kind); put("title", title); put("state", state); put("progress", progress)
            put("bytes", bytes); put("runtime", runtime); put("itemType", itemType); put("hasNext", hasNext); put("job", job)
            put("url", url); put("file", file); put("dm", dm); put("author", author); put("duration", duration)
            put("chapters", chapters); put("parts", parts); put("pos", pos); put("error", error); put("retries", retries); put("quality", quality)
        }

        companion object {
            fun from(j: JSONObject) = Saved(
                j.s("id"), j.s("kind"), j.s("title"), j.s("state"), j.i("progress"), j.optLong("bytes"), j.d("runtime"),
                j.s("itemType"), j.b("hasNext"), j.s("job"), j.s("url"), j.s("file"), j.optLong("dm", -1), j.s("author"),
                j.d("duration"), j.s("chapters").ifEmpty { "[]" }, j.s("parts").ifEmpty { "[]" }, j.d("pos"), j.s("error"), j.i("retries"),
                j.s("quality").ifEmpty { "original" }
            )
        }

        val done get() = state == "done"
        val partList: List<JSONObject> get() = try { JSONArray(parts).objs() } catch (e: Exception) { emptyList() }

        /** "Downloading, 45 percent" and so on. */
        fun stateText(): String = when (state) {
            "preparing" -> "The PC is getting it ready" + if (progress > 0) ", $progress percent" else ""
            "downloading" -> "Downloading" + if (progress > 0) ", $progress percent" else ""
            "done" -> "Downloaded" + if (bytes > 0) ", ${"%.1f".format(bytes / 1e9)} GB" else ""
            else -> "Didn't download" + if (error.isNotEmpty()) ": $error" else ""
        }

        fun kindText(): String = when (kind) {
            "video" -> "Video" + when (quality) { "medium" -> ", medium quality"; "small" -> ", small"; else -> "" }
            "audio" -> "Audio description"
            else -> "Audiobook"
        }
    }

    val items = mutableStateListOf<Saved>()
    private lateinit var store: File
    private lateinit var dir: File
    private lateinit var dm: DownloadManager
    private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val app = c.applicationContext
        store = File(app.filesDir, "offline.json")
        dir = (app.getExternalFilesDir("offline") ?: File(app.filesDir, "offline")).apply { mkdirs() }
        dm = app.getSystemService(DownloadManager::class.java)
        try {
            if (store.exists()) JSONArray(store.readText()).objs().forEach { items.add(Saved.from(it)) }
        } catch (e: Exception) { }
    }

    private fun save() {
        try {
            val a = JSONArray(); items.forEach { a.put(it.toJson()) }
            store.writeText(a.toString())
        } catch (e: Exception) { }
    }

    fun find(id: String, kind: String): Saved? = items.firstOrNull { it.id == id && it.kind == kind }
    fun done(id: String, kind: String): Saved? = find(id, kind)?.takeIf { it.done && (kind == "book" || File(it.file).exists()) }
    fun forItem(id: String): List<Saved> = items.filter { it.id == id }

    private fun put(s: Saved) {
        val i = items.indexOfFirst { it.id == s.id && it.kind == s.kind }
        if (i >= 0) items[i] = s else items.add(s)
        save()
    }

    private fun update(id: String, kind: String, f: (Saved) -> Saved) {
        val i = items.indexOfFirst { it.id == id && it.kind == kind }
        if (i >= 0) { items[i] = f(items[i]); save() }
    }

    /** Remembers where it was left (on the phone, for playing with no connection). */
    fun notePos(id: String, pos: Double) {
        val i = items.indexOfFirst { it.id == id && it.done }
        if (i >= 0 && kotlin.math.abs(items[i].pos - pos) > 5) { items[i] = items[i].copy(pos = pos); save() }
    }

    private fun enqueue(url: String, dest: File, title: String): Long {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        val r = DownloadManager.Request(Uri.parse(Api.url(url)))
            .setTitle(title)
            .setDescription("AD Player")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(dest))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        if (Prefs.token.isNotEmpty()) r.addRequestHeader("X-ADP-Token", Prefs.token)
        return dm.enqueue(r)
    }

    // ---------------------------------------------------------------- starting a download

    /** kind: "video" (the video with its soundtrack) or "audio" (just the audio description).
     *  quality (video): original, medium (about 3 Mbps) or small (about 1.5 Mbps). */
    fun download(it: Item, kind: String, quality: String = "original") {
        val have = find(it.id, kind)
        if (have != null && have.state != "failed") { Engine.say("${it.label} is already ${if (have.done) "downloaded" else "downloading"}."); return }
        put(Saved(it.id, kind, it.label, itemType = it.type, hasNext = it.type == "Episode", quality = quality))
        Engine.say("Getting ${it.label} ready to download. It carries on by itself - see Downloads.")
        Engine.launchSafe { prepare(it.id, kind) }
    }

    private suspend fun prepare(id: String, kind: String) {
        try {
            val q = find(id, kind)?.quality ?: "original"
            var d = Api.post("/api/play/download/prepare", mapOf("item" to id, "kind" to kind, "quality" to q))
            while (true) {
                val s = find(id, kind) ?: return                    // removed meanwhile
                if (d.err().isNotEmpty() || d.s("state") == "failed") {
                    update(id, kind) { it.copy(state = "failed", error = d.err().ifEmpty { "the PC couldn't make the file" }) }
                    Engine.say("${s.title} didn't download: ${d.err().ifEmpty { "the PC couldn't make the file" }}.")
                    return
                }
                if (d.s("state") == "ready") {
                    val url = d.s("url")
                    val ext = if (url.endsWith(".m4a")) "m4a" else "mp4"
                    val dest = File(dir, "${kind}_${id}.$ext")
                    val dmId = enqueue(url, dest, s.title)
                    update(id, kind) {
                        it.copy(state = "downloading", progress = 0, job = d.s("id"), url = url, file = dest.absolutePath, dm = dmId,
                            runtime = d.d("runtime_ticks") / 1e7, bytes = d.optLong("bytes"))
                    }
                    return
                }
                update(id, kind) { it.copy(progress = d.i("progress"), job = d.s("id")) }
                delay(3000)
                d = Api.get("/api/play/download/job/${d.s("id")}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            // no connection right now: tried again when the app is next open
        }
    }

    fun downloadBook(it: Item) {
        val have = find(it.id, "book")
        if (have != null && have.state != "failed") { Engine.say("${it.label} is already ${if (have.done) "downloaded" else "downloading"}."); return }
        Engine.launchSafe {
            val b = Api.get("/api/play/books/book?id=${enc(it.id)}")
            if (b.err().isNotEmpty()) { Engine.say(b.err()); return@launchSafe }
            val title = b.s("title").ifEmpty { it.label }
            val bookDir = File(dir, "book_${it.id}")
            val parts = JSONArray()
            b.optJSONArray("tracks").objs().forEachIndexed { i, t ->
                val url = t.s("url")
                val ext = url.substringAfterLast('.', "mp3").take(5)
                val dest = File(bookDir, "part$i.$ext")
                val id = enqueue(url, dest, if (b.optJSONArray("tracks")!!.length() > 1) "$title (part ${i + 1})" else title)
                parts.put(JSONObject().put("start", t.d("start")).put("duration", t.d("duration")).put("url", url)
                    .put("file", dest.absolutePath).put("dm", id))
            }
            if (parts.length() == 0) { Engine.say("That book has no audio."); return@launchSafe }
            // the cover, for the lock screen
            try {
                withContext(Dispatchers.IO) {
                    Api.http.newCall(Request.Builder().url(Api.url(b.s("cover"))).build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.bytes()?.let { bytes -> File(bookDir, "cover.jpg").apply { parentFile?.mkdirs() }.writeBytes(bytes) }
                    }
                }
            } catch (e: Exception) { }
            put(Saved(it.id, "book", title, state = "downloading", author = b.s("author"), duration = b.d("duration"),
                chapters = b.optJSONArray("chapters")?.toString() ?: "[]", parts = parts.toString(),
                file = bookDir.absolutePath, pos = if (b.b("finished")) 0.0 else b.d("position")))
            Engine.say("Downloading $title. It carries on by itself - see Downloads.")
        }
    }

    // ---------------------------------------------------------------- keeping an eye on them

    /** Every few seconds while the app is open: progress, finished, or try again. */
    fun poll() {
        for (s in items.toList()) {
            if (s.state != "downloading") continue
            val ids = if (s.kind == "book") s.partList.map { it.optLong("dm", -1) } else listOf(s.dm)
            var got = 0L; var total = 0L; var allDone = true; var failed = false
            for (id in ids) {
                if (id < 0) { allDone = false; continue }
                try {
                    dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                        if (c == null || !c.moveToFirst()) { failed = true; return@use }
                        val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        got += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                        total += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
                        if (st != DownloadManager.STATUS_SUCCESSFUL) allDone = false
                        if (st == DownloadManager.STATUS_FAILED) failed = true
                    }
                } catch (e: Exception) { allDone = false }
            }
            when {
                allDone -> {
                    update(s.id, s.kind) { it.copy(state = "done", progress = 100, bytes = if (total > 0) total else got) }
                    Engine.say("${s.title} is downloaded.")
                    if (s.job.isNotEmpty()) Engine.launchSafe { Api.post("/api/play/download/done", mapOf("id" to s.job)) }
                }
                failed -> retry(s)
                else -> {
                    val pct = if (total > 0) (got * 100 / total).toInt() else 0
                    if (pct != s.progress) update(s.id, s.kind) { it.copy(progress = pct) }
                }
            }
        }
    }

    /** A download that failed (often the address changed - home Wi-Fi / away): start it again at today's address. */
    private fun retry(s: Saved) {
        if (s.retries >= 5) { update(s.id, s.kind) { it.copy(state = "failed", error = "the download kept stopping") }; return }
        if (s.kind == "book") {
            val parts = JSONArray()
            for (p in s.partList) {
                val ok = try {
                    dm.query(DownloadManager.Query().setFilterById(p.optLong("dm", -1))).use { c ->
                        c != null && c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                    }
                } catch (e: Exception) { false }
                if (!ok) {
                    try { dm.remove(p.optLong("dm", -1)) } catch (e: Exception) { }
                    p.put("dm", enqueue(p.s("url"), File(p.s("file")), s.title))
                }
                parts.put(p)
            }
            update(s.id, s.kind) { it.copy(parts = parts.toString(), retries = it.retries + 1) }
        } else {
            try { dm.remove(s.dm) } catch (e: Exception) { }
            val id = enqueue(s.url, File(s.file), s.title)
            update(s.id, s.kind) { it.copy(dm = id, retries = it.retries + 1) }
        }
    }

    /** After the app starts: carry on with any the PC was still getting ready. */
    fun resume() {
        for (s in items.toList()) if (s.state == "preparing") Engine.launchSafe { prepare(s.id, s.kind) }
    }

    fun remove(s: Saved) {
        try {
            if (s.kind == "book") s.partList.forEach { p -> dm.remove(p.optLong("dm", -1)) } else if (s.dm >= 0) dm.remove(s.dm)
        } catch (e: Exception) { }
        try { File(s.file).let { if (it.isDirectory) it.deleteRecursively() else it.delete() } } catch (e: Exception) { }
        items.removeAll { it.id == s.id && it.kind == s.kind }
        save()
        Engine.say("Removed the download of ${s.title}.")
    }
}
