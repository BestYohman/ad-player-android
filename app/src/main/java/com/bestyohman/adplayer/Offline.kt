package com.bestyohman.adplayer

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Download for later": a show, movie or audiobook kept on the phone, so it plays with no connection at all.
 * The PC first makes one file the phone can play (the video with its soundtrack, or just the audio description);
 * the app's own downloader (Fetch) then fetches it - from the PC's home address on the home Wi-Fi, the web address
 * away - carrying on where it stopped, with its progress in the notifications.
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
        val quality: String = "original", // video: original, medium or small
        val posAt: Long = 0             // when it was at pos (ms) - the newest place wins
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("kind", kind); put("title", title); put("state", state); put("progress", progress)
            put("bytes", bytes); put("runtime", runtime); put("itemType", itemType); put("hasNext", hasNext); put("job", job)
            put("url", url); put("file", file); put("dm", dm); put("author", author); put("duration", duration)
            put("chapters", chapters); put("parts", parts); put("pos", pos); put("error", error); put("retries", retries); put("quality", quality); put("posAt", posAt)
        }

        companion object {
            fun from(j: JSONObject) = Saved(
                j.s("id"), j.s("kind"), j.s("title"), j.s("state"), j.i("progress"), j.optLong("bytes"), j.d("runtime"),
                j.s("itemType"), j.b("hasNext"), j.s("job"), j.s("url"), j.s("file"), j.optLong("dm", -1), j.s("author"),
                j.d("duration"), j.s("chapters").ifEmpty { "[]" }, j.s("parts").ifEmpty { "[]" }, j.d("pos"), j.s("error"), j.i("retries"),
                j.s("quality").ifEmpty { "original" }, j.optLong("posAt", 0L)
            )
        }

        val done get() = state == "done"
        val partList: List<JSONObject> get() = try { JSONArray(parts).objs() } catch (e: Exception) { emptyList() }

        /** "Downloading, 45 percent" and so on. */
        fun stateText(): String = when (state) {
            "preparing" -> "The PC is getting it ready" + if (progress > 0) ", $progress percent" else ""
            "downloading" -> "Downloading" + if (progress > 0) ", $progress percent" else ""
            "done" -> "Downloaded" + if (bytes > 0) ", ${Offline.size(bytes)}" else ""
            else -> "Didn't download" + if (error.isNotEmpty()) ": $error" else ""
        }

        fun kindText(): String = when (kind) {
            "video" -> "Video" + when (quality) { "medium" -> ", medium quality"; "small" -> ", small"; else -> "" }
            "audio" -> "Audio description"
            else -> "Audiobook"
        }
    }

    val items = mutableStateListOf<Saved>()

    /** How a download is going right now (not kept): bytes so far, its size, speed, time left, parts. */
    data class Live(
        val got: Long = 0, val total: Long = 0, val speed: Double = 0.0, val eta: Long = -1,
        val partsDone: Int = 0, val parts: Int = 0, val waiting: String = "",
        val method: String = "", val speedX: Double = 0.0, val made: Long = 0, val est: Long = 0, val madeRate: Double = 0.0
    )
    val live = androidx.compose.runtime.mutableStateMapOf<String, Live>()
    private val samples = HashMap<String, ArrayDeque<Pair<Long, Long>>>()
    fun size(b: Long): String = when {
        b >= 1_000_000_000L -> "%.2f GB".format(b / 1e9)
        b >= 10_000_000L -> "${b / 1_000_000} MB"
        b >= 1_000_000L -> "%.1f MB".format(b / 1e6)
        else -> "${maxOf(1L, b / 1000)} KB"
    }

    /** Bytes a second as megabits a second, like an internet speed: "24 Mbps". */
    fun mbps(bytesPerSecond: Double): String {
        val m = bytesPerSecond * 8 / 1e6
        return (if (m >= 10) "%.0f" else "%.1f").format(m) + " Mbps"
    }

    fun timeLeft(s: Long): String = when {
        s < 0 -> ""
        s < 60 -> "less than a minute left"
        s < 3600 -> "about ${(s + 30) / 60} minute" + (if ((s + 30) / 60 == 1L) "" else "s") + " left"
        else -> "about ${s / 3600} hour" + (if (s / 3600 == 1L) "" else "s") + " ${(s % 3600) / 60} minutes left"
    }

    /** Everything about how it's going, in words: "Downloading, 45 percent: 1.20 GB of 2.62 GB, 3.4 MB a second, about 7 minutes left." */
    fun detail(s: Saved): List<String> {
        val l = live[s.kind + s.id]
        return when (s.state) {
            "preparing" -> {
                val how = when (l?.method) {
                    "gpu" -> "The PC is converting it on the graphics card"
                    "copy" -> "The PC is copying the video"
                    "audio" -> "The PC is making the audio file"
                    else -> "The PC is getting it ready"
                }
                val a = how + if (s.progress > 0) ", ${s.progress} percent" else ""
                val b = mutableListOf<String>()
                if (l != null && l.made > 0) b.add(size(l.made) + (if (l.est > 0) " of about ${size(l.est)}" else "") + " made")
                if (l != null && l.madeRate > 0) b.add(mbps(l.madeRate))
                if (l != null && l.eta >= 0) b.add(timeLeft(l.eta))
                listOf(a) + (if (b.isEmpty()) emptyList() else listOf(b.joinToString(", ")))
            }
            "downloading" -> {
                val a = (if (l?.waiting?.isNotEmpty() == true) l.waiting else "Downloading") + if (s.progress > 0) ", ${s.progress} percent" else ""
                val b = mutableListOf<String>()
                if (l != null && l.total > 0) b.add("${size(l.got)} of ${size(l.total)}") else if (l != null && l.got > 0) b.add(size(l.got))
                if (l != null && l.parts > 1) b.add("part ${minOf(l.partsDone + 1, l.parts)} of ${l.parts}")
                if (l != null && l.speed > 0) b.add(mbps(l.speed))
                if (l != null && l.eta >= 0) b.add(timeLeft(l.eta))
                listOf(a) + (if (b.isEmpty()) emptyList() else listOf(b.joinToString(", ")))
            }
            else -> listOf(s.stateText())
        }
    }
    private lateinit var store: File
    private lateinit var dir: File
    private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val app = c.applicationContext
        store = File(app.filesDir, "offline.json")
        dir = (app.getExternalFilesDir("offline") ?: File(app.filesDir, "offline")).apply { mkdirs() }
        Fetch.init(app)
        try {
            if (store.exists()) JSONArray(store.readText()).objs().forEach { items.add(Saved.from(it)) }
        } catch (e: Exception) { }
        // downloads Android's own downloader had (before the app fetched them itself): hand them over
        try {
            val dm = app.getSystemService(DownloadManager::class.java)
            for (s in items) if (s.state == "downloading") {
                val ids = if (s.kind == "book") s.partList.map { it.optLong("dm", -1) to it.s("file") } else listOf(s.dm to s.file)
                ids.filter { it.first >= 0 }.forEach { (id, f) ->
                    val ok = try {
                        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                            c != null && c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                        }
                    } catch (e: Exception) { false }
                    if (!ok) {
                        try { dm.remove(id) } catch (e: Exception) { }
                        try { File(f).delete() } catch (e: Exception) { }             // its half-made file
                    }
                }
            }
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
        if (i >= 0 && kotlin.math.abs(items[i].pos - pos) > 5) { items[i] = items[i].copy(pos = pos, posAt = System.currentTimeMillis()); save() }
    }

    /** Starts fetching [url] (where the PC has it, without the address) into [dest]. */
    private fun enqueue(url: String, dest: File, title: String): Long {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        Fetch.start(url, dest, title)
        return -1
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
                val s = find(id, kind)
                if (s == null) {                                    // stopped meanwhile: the PC stops making it too
                    if (d.s("id").isNotEmpty()) tellStopped(d.s("id"))
                    return
                }
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
                // how fast the PC is making the file, over the last 15 seconds
                val made = d.optLong("made_bytes")
                val now = System.currentTimeMillis()
                val q = samples.getOrPut("prep$kind$id") { ArrayDeque() }
                if (made > 0) q.addLast(now to made)
                while (q.size > 2 && now - q.first().first > 15_000) q.removeFirst()
                val rate = if (q.size >= 2 && q.last().first - q.first().first > 1500)
                    (q.last().second - q.first().second) * 1000.0 / (q.last().first - q.first().first) else 0.0
                live[kind + id] = Live(method = d.s("method"), speedX = d.d("speed_x"), made = made, madeRate = rate,
                    est = d.optLong("est_bytes"), eta = if (d.has("eta") && !d.isNull("eta")) d.optLong("eta") else -1)
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

    /** The files a download is made of: (where the PC has it, where it goes on the phone). */
    private fun files(s: Saved): List<Pair<String, String>> =
        if (s.kind == "book") s.partList.map { it.s("url") to it.s("file") } else listOf(s.url to s.file)

    private fun have(f: String): Boolean = File(f).let { it.exists() && it.length() > 1000 }

    /** Every few seconds while the app is open: progress, finished, or failed. */
    fun poll() {
        for (s in items.toList()) {
            if (s.state != "downloading") continue
            val fs = files(s)
            var got = 0L; var total = 0L; var partsDone = 0; var waiting = ""; var failed = ""
            for ((url, f) in fs) {
                val st = Fetch.status(f)
                when {
                    st == null && have(f) -> { partsDone++; File(f).length().let { got += it; total += it } }
                    st == null -> Fetch.start(url, File(f), s.title)        // not on its way (the app was closed): carry on
                    st.done -> { partsDone++; got += st.got; total += st.got }
                    else -> {
                        got += st.got; total += if (st.total > 0) st.total else 0
                        if (st.failed.isNotEmpty()) failed = st.failed
                        if (st.waiting.isNotEmpty() && waiting.isEmpty()) waiting = st.waiting
                    }
                }
            }
            when {
                failed.isNotEmpty() -> {
                    fs.forEach { Fetch.cancel(it.second) }
                    update(s.id, s.kind) { it.copy(state = "failed", error = failed) }
                    Engine.say("${s.title} didn't download: $failed.")
                }
                partsDone == fs.size -> {
                    update(s.id, s.kind) { it.copy(state = "done", progress = 100, bytes = total) }
                    live.remove(s.kind + s.id)
                    Engine.say("${s.title} is downloaded.")
                    if (s.job.isNotEmpty()) tellStopped(s.job, "done")     // the PC deletes its copy now
                }
                else -> {
                    // a book's later parts aren't sized until they start: count those at the book's average so far
                    val pct = if (total > 0) (got * 100 / total).toInt().coerceAtMost(99) else 0
                    val shown = if (fs.size > 1) ((partsDone * 100 + pct) / fs.size).coerceAtMost(99) else pct
                    if (shown != s.progress) update(s.id, s.kind) { it.copy(progress = shown) }
                    // speed over the last 15 seconds, and the time left at that speed
                    val key = s.kind + s.id
                    val now = System.currentTimeMillis()
                    val q = samples.getOrPut(key) { ArrayDeque() }
                    if (q.isNotEmpty() && got < q.last().second) q.clear()          // started a file again
                    q.addLast(now to got)
                    while (q.size > 2 && now - q.first().first > 15_000) q.removeFirst()
                    val (t0, g0) = q.first()
                    val speed = if (now - t0 > 1500) (got - g0) * 1000.0 / (now - t0) else 0.0
                    val eta = if (speed > 1000 && total > got && fs.size == 1) ((total - got) / speed).toLong() else -1L
                    live[key] = Live(got = got, total = if (fs.size == 1) total else 0, speed = speed, eta = eta,
                        partsDone = partsDone, parts = fs.size, waiting = waiting)
                }
            }
        }
    }

    /** After the app starts: carry on with any the PC was still getting ready. */
    fun resume() {
        for (s in items.toList()) if (s.state == "preparing") Engine.launchSafe { prepare(s.id, s.kind) }
        Prefs.str("stop_jobs").split(',').filter { it.isNotEmpty() }.forEach { tellStopped(it) }
    }

    /** Tells the PC this phone doesn't want its copy any more (it has it, or stopped): the PC stops converting and
     *  deletes what it made. With no connection it's told next time the app starts (a stop does the same as done). */
    private fun tellStopped(job: String, what: String = "cancel") {
        Engine.scope.launch {
            val ok = try { Api.post("/api/play/download/$what", mapOf("id" to job)).err().isEmpty() } catch (e: Exception) { false }
            val left = Prefs.str("stop_jobs").split(',').filter { it.isNotEmpty() && it != job }
            Prefs.put("stop_jobs", (if (ok) left else left + job).joinToString(","))   // no connection: told next time
        }
    }

    fun remove(s: Saved) {
        // still on its way (the PC making it, or the phone fetching it): the PC stops too and clears its copy
        if (!s.done && s.kind != "book" && s.job.isNotEmpty()) tellStopped(s.job)
        files(s).forEach { Fetch.cancel(it.second) }
        try { File(s.file).let { if (it.isDirectory) it.deleteRecursively() else it.delete() } } catch (e: Exception) { }
        items.removeAll { it.id == s.id && it.kind == s.kind }
        save()
        Engine.say("Removed the download of ${s.title}.")
    }
}
