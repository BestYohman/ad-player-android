package com.bestyohman.adplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The app's own downloader for "download for later". Android's built-in one can't reach the PC's home-network
 * address on newer Android, so at home it would have to go the long way round through the web address. This one
 * runs in the app (kept alive by a small notification while it works), always uses the address in use right now -
 * the PC's home address on the home Wi-Fi, the web address away - carries on where it stopped, and keeps trying
 * when the connection drops or the phone moves between home and away.
 */
object Fetch {
    class St(
        @Volatile var got: Long = 0, @Volatile var total: Long = 0,
        @Volatile var waiting: String = "", @Volatile var failed: String = "", @Volatile var done: Boolean = false
    )

    private class Work(val key: String, val path: String, val file: File, val title: String) {
        val st = St()
        @Volatile var cancelled = false
    }

    private val queue = ArrayList<Work>()           // in order: a book's parts one after another
    private var worker: Job? = null
    @Volatile private var nudge = false
    private lateinit var app: Context

    private val client: OkHttpClient by lazy {
        Api.http.newBuilder().readTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    }

    fun init(c: Context) { if (!::app.isInitialized) app = c.applicationContext }

    /** Fetches [path] (like /api/play/download/file/x.mp4) into [file]. Nothing happens if it's already queued. */
    @Synchronized
    fun start(path: String, file: File, title: String) {
        val key = file.absolutePath
        if (queue.any { it.key == key && !it.cancelled }) return
        queue.add(Work(key, path, file, title))
        run()
    }

    @Synchronized
    fun status(file: String): St? = queue.firstOrNull { it.key == file && !it.cancelled }?.st

    @Synchronized
    fun cancel(file: String) {
        queue.filter { it.key == file }.forEach { it.cancelled = true }
        queue.removeAll { it.key == file }
        File("$file.part").delete()
    }

    /** The network changed (home Wi-Fi / away): try straight away instead of waiting out the pause. */
    fun networkChanged() { nudge = true }

    @Synchronized
    private fun next(): Work? = queue.firstOrNull { !it.cancelled && !it.st.done && it.st.failed.isEmpty() }

    @Synchronized
    private fun finished(w: Work) { queue.remove(w) }

    private fun run() {
        if (worker?.isActive == true) return
        if (::app.isInitialized) FetchService.start(app)
        worker = Engine.scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val w = next() ?: break
                    get(w)
                    if (w.st.done) {
                        delay(2500)                       // then the file itself says it's there
                        finished(w)
                    }                                     // a failed one stays until the Downloads page has seen it
                }
            } finally {
                if (::app.isInitialized) FetchService.stop(app)
            }
        }
    }

    /** One file, trying until it's there, it's stopped, or the PC turns it down for good. */
    private suspend fun get(w: Work) {
        var pause = 3_000L
        var serverErrors = 0
        while (!w.cancelled) {
            val url = Api.url(w.path)
            val host = try { java.net.URI(url).host ?: "" } catch (e: Exception) { "" }
            val part = File(w.file.absolutePath + ".part")
            try {
                w.file.parentFile?.mkdirs()
                val have = if (part.exists()) part.length() else 0L
                val rb = Request.Builder().url(url).header("Cache-Control", "no-cache")
                if (have > 0) rb.header("Range", "bytes=$have-")
                client.newCall(rb.build()).execute().use { r ->
                    when {
                        r.code == 416 -> { part.delete(); throw IOException("starting again") }
                        r.code in 500..599 || r.code == 408 || r.code == 429 -> {
                            serverErrors++
                            if (serverErrors >= 8) { w.st.failed = "the PC answered with error ${r.code}"; return }
                            throw IOException("the PC is busy (${r.code})")
                        }
                        !r.isSuccessful -> { w.st.failed = if (r.code == 404) "the PC doesn't have that file any more" else "the PC answered with error ${r.code}"; return }
                    }
                    val body = r.body ?: throw IOException("no data")
                    val append = have > 0 && r.code == 206
                    val len = body.contentLength()
                    w.st.got = if (append) have else 0L
                    w.st.total = if (len > 0) w.st.got + len else 0L
                    w.st.waiting = ""
                    pause = 3_000L
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(256 * 1024)
                        val inp = body.byteStream()
                        while (true) {
                            if (w.cancelled) return
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            w.st.got += n
                        }
                    }
                    if (w.st.total > 0 && part.length() < w.st.total) throw IOException("the connection dropped")
                }
                if (w.cancelled) return
                if (part.length() < 1000) { part.delete(); throw IOException("the file came back empty") }
                w.file.delete()
                if (!part.renameTo(w.file)) { w.st.failed = "the phone couldn't save the file"; return }
                w.st.done = true
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (w.cancelled) return
                if (e is java.io.FileNotFoundException || (e.message ?: "").contains("ENOSPC")) {
                    w.st.failed = "the phone couldn't save the file (is it full?)"; return
                }
                w.st.waiting = "Paused - couldn't reach the PC, trying again soon" + if (host.isNotEmpty()) " ($host)" else ""
                // wait, but go at once if the network changes (home Wi-Fi / away)
                nudge = false
                var waited = 0L
                while (waited < pause && !nudge && !w.cancelled) { delay(500); waited += 500 }
                pause = (pause * 2).coerceAtMost(60_000L)
            }
        }
    }

    /** For the notification: "Harry Potter - 45%". */
    @Synchronized
    fun summary(): Pair<String, Int> {
        val w = queue.firstOrNull { !it.cancelled && !it.st.done } ?: return "Finishing" to -1
        val pct = if (w.st.total > 0) (w.st.got * 100 / w.st.total).toInt() else -1
        return w.title to pct
    }

    @Synchronized
    fun busy(): Boolean = queue.any { !it.cancelled && !it.st.done && it.st.failed.isEmpty() }
}

/** Keeps the app running while it downloads (Android stops apps in the background otherwise), with a progress notification. */
class FetchService : Service() {
    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 4207

        fun start(c: Context) {
            try {
                val i = Intent(c, FetchService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { }          // not allowed right now (the app in the background): it still runs while it can
        }

        fun stop(c: Context) {
            try { c.stopService(Intent(c, FetchService::class.java)) } catch (e: Exception) { }
        }
    }

    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    private fun note(): Notification {
        val (title, pct) = Fetch.summary()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText(if (pct >= 0) "$pct percent" else "AD Player")
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, note())
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (ticker?.isActive != true) ticker = Engine.scope.launch {
            while (isActive) {
                delay(3000)
                if (!Fetch.busy()) { stopSelf(); break }
                try { getSystemService(NotificationManager::class.java).notify(ID, note()) } catch (e: Exception) { }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 lets this kind of service run 6 hours a day: then it stops (downloads carry on with the app open). */
    override fun onTimeout(startId: Int) { stopSelf() }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        ticker?.cancel()
        super.onDestroy()
    }
}
