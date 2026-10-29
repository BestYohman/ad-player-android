package com.bestyohman.adplayer

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Everything the app does: who you are, where it plays, the lists, playing, and playing together. */
object Engine {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
        say("Something went wrong: ${e.message}")
    })
    private val exo get() = PlayerHolder.exo

    // ---------------------------------------------------------------- screens, who, messages
    var screen by mutableStateOf("boot")          // boot, server, signin, main
    var bootError by mutableStateOf("")
    var me by mutableStateOf<JSONObject?>(null)
    val role: String get() = me?.s("role")?.ifEmpty { "listen" } ?: "listen"
    val watchUI: Boolean get() = role == "watch"
    var message by mutableStateOf("")
    var announcer: ((String) -> Unit)? = null
    var foreground = false
    var tick by mutableStateOf(0)                 // changes every second, so "Now playing" stays up to date

    fun say(t: String) {
        if (t.isEmpty()) return
        message = t
        announcer?.invoke(t)
    }

    fun launchSafe(block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e.login) signedOut(e.message ?: "Please sign in.") else say(e.message ?: "Something went wrong.")
        } catch (e: Exception) {
            say("Something went wrong: ${e.message}")
        }
    }

    private var started = false
    fun boot() {
        screen = "boot"
        if (!started) {
            started = true
            scope.launch { while (true) { delay(1000); everySecond() } }
            scope.launch {
                while (true) {
                    delay(5000)
                    try {
                        if (foreground && screen == "main") { pollInvites(); if (!isPhone && dev.isNotEmpty()) refreshTv() }
                    } catch (e: ApiError) {
                        if (e.login) signedOut(e.message ?: "")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) { }
                }
            }
        }
        if (Api.base().isEmpty()) { screen = "server"; return }
        scope.launch {
            try {
                Api.active = Api.chooseAddress(!onMobileData)
                val m = try {
                    Api.get("/api/play/me")
                } catch (e: ApiError) {
                    if (e.login || !Api.atHome) throw e
                    Api.active = ""; Api.get("/api/play/me")            // the home address didn't answer after all
                }
                Api.rememberServer(m)
                if (!m.b("signed_in") || Prefs.token.isEmpty()) { me = m; screen = "signin"; return@launch }
                me = m
                screen = "main"
                loadDevices(false)
                if (stack.isEmpty()) openList(if (what == "books") "books_continue" else "continue_$what", quiet = true)
                pollInvites()
            } catch (e: ApiError) {
                if (e.login) { screen = "signin" } else { bootError = e.message ?: ""; screen = "server" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bootError = "Something went wrong: ${e.message}"; screen = "server"
            }
        }
    }

    fun signedOut(msg: String) {
        Prefs.token = ""
        me = null
        say(msg)
        screen = "signin"
    }

    fun signIn(user: String, pw: String) = launchSafe {
        say("Signing in…")
        val d = Api.post("/api/play/login", mapOf("user" to user, "password" to pw, "app" to "1"))
        if (d.err().isNotEmpty()) { say(d.err()); return@launchSafe }
        Prefs.token = d.s("token")
        say(d.s("message"))
        boot()
    }

    fun signOut() = launchSafe {
        if (tg != null) tgLeave(true)
        stopPhone(true)
        try { Api.post("/api/play/logout", emptyMap()) } catch (e: ApiError) { }
        Prefs.token = ""
        me = null
        stack.clear()
        screen = "signin"
    }

    // ---------------------------------------------------------------- where it plays
    data class Dev(val id: String, val name: String)
    var devices by mutableStateOf(listOf<Dev>())
    var devHelp by mutableStateOf("")
    var dev by mutableStateOf(Prefs.dev)
    val isPhone: Boolean get() = dev == "__local" || dev == "__video"

    suspend fun loadDevices(announce: Boolean) {
        val phones = mutableListOf<Dev>()
        if (role == "watch") phones.add(Dev("__video", "This phone - the video"))
        phones.add(Dev("__local", "This phone - just the audio"))
        var tvs = listOf<Dev>()
        var other = ""
        try {
            val d = Api.get("/api/play/devices")
            if (d.err().isNotEmpty()) devHelp = d.err()
            tvs = d.optJSONArray("devices").objs().map {
                Dev(it.s("id"), it.s("name") + (if (it.s("playing").isNotEmpty()) " (playing ${it.s("playing")})" else ""))
            }
            val o = d.optJSONArray("other").strs()
            if (o.isNotEmpty()) other = " Connected but can't be controlled from here: ${o.joinToString(", ")}."
        } catch (e: ApiError) {
            if (e.login) throw e
            devHelp = e.message ?: ""
        }
        devices = tvs + phones
        val want = dev.ifEmpty { Prefs.dev }
        dev = when {
            devices.any { it.id == want } -> want
            tvs.isEmpty() -> phones[0].id
            else -> devices[0].id
        }
        Prefs.dev = dev
        if (tvs.isEmpty()) devHelp = "No TV found - it plays on this phone. For a TV, open Moonfin (or Emby) on it, then press Refresh TV list.$other"
        else if (devHelp.isEmpty() || devHelp.startsWith("No TV")) devHelp = (if (tvs.size > 1) "${tvs.size} TVs found." else "") + other
        if (announce) say(if (tvs.isEmpty()) "No TV found. It will play on this phone." else "${tvs.size} TV${if (tvs.size > 1) "s" else ""} found. Playing on ${devices.first { it.id == dev }.name}.")
        if (!isPhone) refreshTv()
    }

    fun refreshDevices() = launchSafe { loadDevices(true) }

    fun pickDevice(id: String) {
        if (isPhone && id != dev) {
            if (tg != null) tgLeave(true)
            stopPhone(true)
        }
        dev = id
        Prefs.dev = id
        if (!isPhone) launchSafe { refreshTv() }
    }

    // ---------------------------------------------------------------- lists
    data class View(val kind: String, val parent: String = "", val title: String = "", val q: String = "", val item: Item? = null)

    var what by mutableStateOf(Prefs.what)
    val stack = mutableStateListOf<View>()
    var items by mutableStateOf<List<Item>?>(null)
    var listError by mutableStateOf("")
    var browse by mutableStateOf(false)          // looking for something else while something plays
    private var listGen = 0

    val TITLES = mapOf(
        "continue_shows" to "Shows in progress", "continue_movies" to "Movies in progress",
        "shows" to "Shows with audio description", "movies" to "Movies with audio description",
        "favorites_shows" to "Favorite shows", "favorites_movies" to "Favorite movies",
        "noad_shows" to "Shows without audio description", "noad_movies" to "Movies without audio description",
        "requests" to "Requests for audio description",
        "books_continue" to "Continue listening", "books_all" to "All audiobooks", "books_series" to "Audiobook series",
        "books_authors" to "Audiobook authors"
    )

    fun pickWhat(w: String) {
        what = w
        Prefs.what = w
        openList(if (w == "books") "books_continue" else "continue_$w", quiet = true)
    }

    fun openList(kind: String, parent: String = "", title: String? = null, q: String = "", quiet: Boolean = false) {
        stack.clear()
        stack.add(View(kind, parent, title ?: TITLES[kind] ?: "", q))
        load(!quiet)
    }

    fun push(v: View) { stack.add(v); load(true) }

    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); load(false); return true }
        return false
    }

    fun search(q: String) {
        if (q.isBlank()) { say("Type something to search for."); return }
        if (what == "books") openList("books_search", "", "Audiobooks matching $q", q)
        else openList("search_$what", "", (if (what == "movies") "Movies" else "Shows") + " matching $q", q)
    }

    fun load(announce: Boolean) {
        val v = stack.lastOrNull() ?: return
        val gen = ++listGen
        items = null
        listError = ""
        if (v.kind == "detail") { items = emptyList(); loadSeasons(v, gen); return }
        scope.launch {
            try {
                val url = if (v.kind.startsWith("books_"))
                    "/api/play/books/list?kind=${v.kind}&parent=${enc(v.parent)}&q=${enc(v.q)}"
                else "/api/play/list?kind=${v.kind}&device=${enc(dev)}&parent=${enc(v.parent)}&q=${enc(v.q)}"
                val d = Api.get(url)
                if (gen != listGen) return@launch
                if (d.err().isNotEmpty()) { listError = d.err(); items = emptyList(); if (announce) say(d.err()); return@launch }
                val list = d.optJSONArray("items").objs().map { Item(it) }
                items = list
                if (announce) say(if (list.isEmpty()) "Nothing found." else "${list.size} item${if (list.size == 1) "" else "s"}.")
            } catch (e: ApiError) {
                if (gen != listGen) return@launch
                if (e.login) { signedOut(e.message ?: ""); return@launch }
                listError = e.message ?: ""
                items = emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == listGen) { listError = "Something went wrong: ${e.message}"; items = emptyList() }
            }
        }
    }

    // a show's page: its seasons, then the episodes of the chosen one
    var seasons by mutableStateOf(listOf<Item>())
    var seasonIdx by mutableStateOf(-1)
    var episodes by mutableStateOf<List<Item>?>(null)

    private fun loadSeasons(v: View, gen: Int) {
        seasons = emptyList(); seasonIdx = -1; episodes = null
        val it = v.item ?: return
        if (it.type != "Series") return
        launchSafe {
            val d = Api.get("/api/play/list?kind=seasons&parent=${enc(it.id)}")
            if (gen != listGen) return@launchSafe
            val ss = d.optJSONArray("items").objs().map { x -> Item(x) }
            seasons = ss
            if (ss.isEmpty()) { episodes = emptyList(); return@launchSafe }
            val want = Prefs.str("season_${it.id}")
            var pick = ss.indexOfFirst { s -> s.id == want }
            if (pick < 0) pick = ss.indexOfFirst { s -> !s.played }.let { i -> if (i < 0) 0 else i }
            pickSeason(pick)
        }
    }

    fun pickSeason(i: Int) {
        val v = stack.lastOrNull() ?: return
        val s = seasons.getOrNull(i) ?: return
        seasonIdx = i
        Prefs.put("season_${v.item?.id}", s.id)
        episodes = null
        val gen = listGen
        launchSafe {
            val d = Api.get("/api/play/list?kind=episodes&parent=${enc(s.id)}")
            if (gen == listGen) episodes = d.optJSONArray("items").objs().map { Item(it) }
        }
    }

    fun openItem(it: Item) {
        when (it.type) {
            "Series", "Movie" -> push(View("detail", title = it.name, item = it))
            "Season" -> push(View("episodes", it.id, (stack.lastOrNull()?.title ?: "").removeSuffix(" seasons") + ", " + it.label))
            "BookSeries" -> push(View("books_inseries", it.id, it.label))
            "BookAuthor" -> push(View("books_byauthor", it.id, it.label))
            "Book" -> bookPlay(it, true, null)
            "Chapter" -> bookPlay(it, false, it.start)
            "Request" -> {}
            else -> start(it, it.resume.isNotEmpty())
        }
    }

    fun toggleFavorite(it: Item) = launchSafe {
        val d = Api.post("/api/play/favorite", mapOf("item" to it.id, "on" to if (it.favorite) "0" else "1"))
        if (d.err().isNotEmpty()) say(d.err()) else { it.favorite = !it.favorite; say(d.s("message")) }
    }

    fun askForAd(it: Item) = launchSafe {
        val d = Api.post("/api/play/request", mapOf("item" to it.id))
        if (d.err().isNotEmpty()) say(d.err()) else { it.requested = true; say(d.s("message")) }
    }

    // ---------------------------------------------------------------- what plays
    enum class Mode { NONE, AUDIO, VIDEO, BOOK }
    var mode by mutableStateOf(Mode.NONE)
    var mediaReady by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)

    class Loc(val item: String, var title: String, val tg: Boolean) {
        var ready = false; var progress = 0; var hasNext = false; var hasAd = true
        var resumeS = 0.0; var runtime = 0.0; var job = ""; var seeking = false; var local = false
    }
    class Vid(val item: String, var title: String, val tg: Boolean) {
        var tok = ""; var hasNext = false; var runtime = 0.0; var mbps = 0.0
    }
    data class Ch(val start: Double, val end: Double, val title: String)
    data class Tr(val start: Double, val duration: Double, val url: String)
    class Bk(val id: String, var title: String, val tg: Boolean) {
        var author = ""; var duration = 0.0; var chapters = listOf<Ch>(); var tracks = listOf<Tr>(); var ready = false
        var sleep = "off"; var sleepAt = 0L; var sleepPos = -1.0
    }

    var loc by mutableStateOf<Loc?>(null)
    var vd by mutableStateOf<Vid?>(null)
    var bk by mutableStateOf<Bk?>(null)
    private var lastSave = 0L

    fun start(it: Item, resume: Boolean) {
        browse = false
        if (isPhone && together) { tgStartVideo(it, resume); return }
        if (tg != null) tgLeave(true)
        when (dev) {
            "__video" -> videoStart(it.id, it.label, resume)
            "__local" -> listenStart(it.id, it.label, resume)
            "" -> say("Choose where to play it first.")
            else -> tvStart(it, resume)
        }
    }

    private fun playUrl(url: String, title: String, artist: String, startS: Double, play: Boolean) {
        mediaReady = false
        val b = MediaItem.Builder().setUri(Api.url(url))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
        if (url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        exo.setMediaItem(b.build(), (startS * 1000).toLong().coerceAtLeast(0L))
        exo.setPlaybackSpeed(1f)
        exo.prepare()
        exo.playWhenReady = play
        lastSave = System.currentTimeMillis()
    }

    /** Just the audio (the AD track, or the soundtrack when playing together and there's no AD). */
    fun listenStart(id: String, label: String, resume: Boolean, tgPos: Double? = null) {
        stopPhone(true)
        val me = Loc(id, label, tgPos != null)
        loc = me; mode = Mode.AUDIO
        if (!me.tg) say("Getting the audio of $label ready…")
        launchSafe {
            var d = Api.post("/api/play/listen/prepare", mapOf("item" to id, "fmt" to "hls", "any" to if (me.tg) "1" else "0"))
            if (loc !== me) return@launchSafe
            if (d.err().isNotEmpty() && d.s("id").isEmpty()) { say(d.err()); loc = null; mode = Mode.NONE; return@launchSafe }
            me.title = d.s("title").ifEmpty { label }
            me.hasNext = d.b("has_next")
            me.hasAd = d.boolOrNull("has_ad") != false
            me.runtime = d.d("runtime_ticks") / 1e7
            me.resumeS = tgPos ?: if (resume) d.d("resume_ticks") / 1e7 else 0.0
            me.job = d.s("id")
            while (true) {
                if (loc !== me) return@launchSafe
                if (d.s("state") == "failed") {
                    say(d.err().ifEmpty { "Couldn't get the audio ready." }); loc = null; mode = Mode.NONE; return@launchSafe
                }
                val canStart = d.s("url").isNotEmpty() && (d.s("state") == "ready" ||
                    (d.b("playable") && (me.resumeS <= 5 || d.d("encoded_s") >= me.resumeS + 15)))
                if (canStart) break
                me.progress = d.i("progress"); me.seeking = d.b("playable") && me.resumeS > 5; tick++
                delay(1000)
                d = Api.get("/api/play/listen/job/${me.job}")
            }
            playUrl(d.s("url"), me.title, if (me.hasAd) "Audio description" else "Soundtrack", me.resumeS, !me.tg)
            me.ready = true
            fetchWhole(me)
            if (!me.tg) say("Playing ${me.title} on this phone" + (if (me.resumeS > 5) " from ${fmtTime(me.resumeS)}" else "") + ".")
        }
    }

    /** The video (Emby's stream through the PC), with the normal soundtrack. */
    fun videoStart(id: String, label: String, resume: Boolean, tgPos: Double? = null) {
        stopPhone(true)
        val me = Vid(id, label, tgPos != null)
        vd = me; mode = Mode.VIDEO
        if (!me.tg) say("Getting the video of $label ready…")
        stallTimes.clear()
        launchSafe {
            val d = videoPrepare(id, videoMbps())
            if (vd !== me) return@launchSafe
            if (d.err().isNotEmpty()) { say(d.err()); vd = null; mode = Mode.NONE; return@launchSafe }
            me.tok = d.s("tok"); me.title = d.s("title").ifEmpty { label }; me.hasNext = d.b("has_next")
            me.runtime = d.d("runtime_ticks") / 1e7; me.mbps = d.d("mbps")
            val pos = tgPos ?: if (resume) d.d("resume_ticks") / 1e7 else 0.0
            playUrl(d.s("url"), me.title, "", pos, !me.tg)
            if (!me.tg) say("Playing ${me.title}" + (if (pos > 5) " from ${fmtTime(pos)}" else "") + ".")
        }
    }

    // ---------------------------------------------------------------- staying up on a phone network
    // 1. The listener's audio is downloaded whole in the background, then played from the phone itself.
    // 2. When the connection drops (or the phone moves between Wi-Fi and mobile data) it carries on by itself.
    // 3. Video quality: chosen, or "Auto", which steps down when the video keeps getting stuck.
    private var wholeJob: Job? = null

    private fun fetchWhole(me: Loc) {
        wholeJob?.cancel()
        wholeJob = scope.launch {
            var tries = 0
            while (loc === me && tries < 900) {
                tries++
                try {
                    val j = Api.get("/api/play/listen/job/${me.job}")
                    if (j.s("state") == "failed") return@launch
                    val url = j.s("full_url")
                    if (url.isNotEmpty()) {
                        val f = Downloads.get(Api.url(url), "${me.job}.m4a")
                        if (f != null && loc === me) { switchToLocal(me, f); return@launch }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) { }
                delay(if (tries < 30) 4000L else 15000L)
            }
        }
    }

    private fun switchToLocal(me: Loc, f: java.io.File) {
        if (me.local || mode != Mode.AUDIO) return
        val pos = exo.currentPosition
        val play = exo.playWhenReady
        me.local = true
        mediaReady = false
        val mi = MediaItem.Builder().setUri(Uri.fromFile(f))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(me.title).setArtist(if (me.hasAd) "Audio description" else "Soundtrack").build())
            .build()
        exo.setMediaItem(mi, pos)
        exo.prepare()
        exo.playWhenReady = play
    }

    val qualities = listOf("auto" to "Auto", "8" to "High (8)", "6" to "Good (6)", "4" to "Medium (4)", "2.5" to "Low (2.5)", "1.5" to "Very low (1.5)", "1" to "Lowest (1)")
    private val autoSteps = listOf(6.0, 4.0, 2.5, 1.5, 1.0)
    var quality by mutableStateOf(Prefs.str("quality", "auto"))
    var onMobileData = false
    private val stallTimes = mutableListOf<Long>()
    private var stallSince = 0L
    private var lastSeekAt = 0L

    private fun autoKey() = if (onMobileData) "auto_mobile" else "auto_wifi"

    /** Megabits a second to ask for (0 = the PC's own setting). */
    fun videoMbps(): Double =
        if (quality != "auto") quality.toDoubleOrNull() ?: 0.0 else Prefs.str(autoKey()).toDoubleOrNull() ?: 0.0

    private suspend fun videoPrepare(id: String, mbps: Double): JSONObject {
        val f = mutableMapOf("item" to id)
        if (mbps > 0) f["mbps"] = mbps.toString()
        return Api.post("/api/play/video/prepare", f)
    }

    fun qualityLabel(): String {
        val q = qualities.firstOrNull { it.first == quality }?.second ?: "Auto"
        val now = vd?.mbps ?: 0.0
        return if (quality == "auto" && now > 0) "$q (now ${if (now % 1.0 == 0.0) now.toInt().toString() else now.toString()})" else q
    }

    fun chooseQuality(q: String) {
        quality = q
        Prefs.put("quality", q)
        if (q == "auto") Prefs.put(autoKey(), "")
        say("Video quality: " + (qualities.firstOrNull { it.first == q }?.second ?: q) + ".")
        reloadVideo(videoMbps())
    }

    /** The same video again from where it is, at another quality (or after the connection was lost). */
    private fun reloadVideo(mbps: Double) {
        val v = vd ?: return
        if (mode != Mode.VIDEO) return
        val pos = exo.currentPosition / 1000.0
        val play = exo.playWhenReady
        launchSafe {
            val d = videoPrepare(v.item, mbps)
            if (vd !== v) return@launchSafe
            if (d.err().isNotEmpty()) { say(d.err()); return@launchSafe }
            v.tok = d.s("tok"); v.mbps = d.d("mbps")
            stallTimes.clear(); stallSince = 0L
            playUrl(d.s("url"), v.title, "", pos, play)
        }
    }

    private fun stepDown() {
        val v = vd ?: return
        val cur = if (v.mbps > 0) v.mbps else 6.0
        val next = autoSteps.firstOrNull { it < cur - 0.01 } ?: return
        Prefs.put(autoKey(), next.toString())
        say("The connection is slow - switching to lower video quality.")
        reloadVideo(next)
    }

    private fun watchStalls() {
        if (mode != Mode.VIDEO || quality != "auto" || !mediaReady) { stallSince = 0L; return }
        val now = System.currentTimeMillis()
        val stuck = exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING && now - lastSeekAt > 4000
        if (!stuck) { stallSince = 0L; return }
        if (stallSince == 0L) { stallSince = now; stallTimes.add(now) }
        stallTimes.removeAll { now - it > 90_000 }
        if (now - stallSince > 8000 || stallTimes.size >= 3) { stallSince = 0L; stallTimes.clear(); stepDown() }
    }

    private var recoverTries = 0
    private var recovering = false

    /** Something went wrong loading (the connection dropped): try again by itself, from the same place. */
    private fun recover(error: PlaybackException) {
        if (mode == Mode.NONE || recovering) return
        val net = error.errorCode in 2000..2999 || error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        if (!net && recoverTries >= 2) {
            say("The phone couldn't play it: ${error.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase()}.")
            return
        }
        recoverTries++
        if (recoverTries > 60) { say("Couldn't get the connection back. Press Play to try again."); return }
        if (recoverTries == 1) say("The connection dropped - carrying on as soon as it's back.")
        recovering = true
        scope.launch {
            delay(min(10_000L, 1000L * recoverTries))
            recovering = false
            if (mode == Mode.NONE || exo.playerError == null) return@launch
            val gone = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode in listOf(401, 403, 404, 410)
            if (mode == Mode.VIDEO && gone) reloadVideo(vd?.mbps ?: videoMbps())
            else { exo.prepare() }
        }
    }

    /** The phone found a network again: don't wait for the next try. */
    fun networkBack(mobile: Boolean) {
        onMobileData = mobile
        try { Api.http.connectionPool.evictAll() } catch (e: Exception) { }
        recheckAddress()
    }

    private var addrJob: Job? = null

    /** On the home Wi-Fi: straight to the PC; elsewhere: the web address. Playing carries on at the new address. */
    fun recheckAddress() {
        if (Api.base().isEmpty()) return
        addrJob?.cancel()
        addrJob = scope.launch {
            val old = Api.base()
            val pick = Api.chooseAddress(!onMobileData)
            if (pick != old) {
                Api.active = pick
                moveMedia(old, pick)
                if (screen == "server" && bootError.isNotEmpty()) boot()
            }
            if (mode != Mode.NONE && exo.playerError != null && !recovering) exo.prepare()
        }
    }

    private fun moveMedia(old: String, new: String) {
        if (mode == Mode.NONE || exo.mediaItemCount == 0) return
        val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
        if (items.none { it.localConfiguration?.uri?.toString()?.startsWith(old) == true }) return
        val idx = exo.currentMediaItemIndex
        val pos = exo.currentPosition
        val play = exo.playWhenReady
        val moved = items.map { mi ->
            val u = mi.localConfiguration?.uri?.toString() ?: ""
            if (u.startsWith(old)) mi.buildUpon().setUri(new + u.removePrefix(old)).build() else mi
        }
        mediaReady = false
        exo.setMediaItems(moved, idx, pos)
        exo.prepare()
        exo.playWhenReady = play
    }

    // ---------------------------------------------------------------- audiobooks on the phone
    fun bookPlay(it: Item, resume: Boolean, at: Double?) {
        browse = false
        val id = if (it.type == "Chapter") it.book else it.id
        val label = if (it.type == "Chapter") it.bookTitle else it.label
        val t = tg
        if (t != null && t.kind == "book" && t.last.s("item") == id && at != null) { tgControl("seek", mapOf("pos" to at.toString())); return }
        if (isPhone && together) { tgStartBook(id, label, resume, at); return }
        if (t != null) tgLeave(true)
        if (isPhone) {
            val b = bk
            if (b != null && b.ready && b.id == id && at != null) {
                bookSeek(at); exo.play()
                val i = chapterAt(at); say(if (i >= 0) "Chapter ${i + 1}: ${b.chapters[i].title}." else "Playing from ${fmtTime(at)}.")
                return
            }
            bookStartPhone(id, label, resume, at)
            return
        }
        if (!dev.startsWith("dlna-")) {
            say("Audiobooks play on this phone, or on a TV whose name ends in DLNA with audio description. Choose one under Play on.")
            return
        }
        launchSafe {
            val f = mutableMapOf("device" to dev, "item" to id, "resume" to if (resume) "1" else "0")
            if (at != null) f["at"] = at.toString()
            val d = Api.post("/api/play/books/start", f)
            say(d.err().ifEmpty { d.s("message") })
            delay(2500); refreshTv()
        }
    }

    fun bookStartPhone(id: String, label: String, resume: Boolean, at: Double?, noplay: Boolean = false) {
        stopPhone(true)
        val me = Bk(id, label, noplay)
        bk = me; mode = Mode.BOOK
        if (!noplay) say("Getting $label ready…")
        launchSafe {
            val b = Api.get("/api/play/books/book?id=${enc(id)}")
            if (bk !== me) return@launchSafe
            if (b.err().isNotEmpty()) { say(b.err()); bk = null; mode = Mode.NONE; return@launchSafe }
            me.title = b.s("title"); me.author = b.s("author"); me.duration = b.d("duration")
            me.chapters = b.optJSONArray("chapters").objs().map { Ch(it.d("start"), it.d("end"), it.s("title")) }
            me.tracks = b.optJSONArray("tracks").objs().map { Tr(it.d("start"), it.d("duration"), it.s("url")) }
            if (me.tracks.isEmpty()) { say("That book has no audio."); bk = null; mode = Mode.NONE; return@launchSafe }
            var pos = at ?: if (resume && !b.b("finished")) max(0.0, b.d("position") - 2) else 0.0
            if (pos >= me.duration - 2) pos = 0.0
            val art = Uri.parse(Api.url(b.s("cover")))
            val list = me.tracks.mapIndexed { i, t ->
                MediaItem.Builder().setUri(Api.url(t.url)).setMediaId("part$i")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(me.title).setArtist(me.author).setArtworkUri(art).build())
                    .build()
            }
            val i = trackAt(me, pos)
            mediaReady = false
            exo.setMediaItems(list, i, ((pos - me.tracks[i].start) * 1000).toLong().coerceAtLeast(0L))
            exo.setPlaybackSpeed(if (noplay) 1f else Prefs.bookSpeed)
            exo.prepare()
            exo.playWhenReady = !noplay
            me.ready = true
            lastSave = System.currentTimeMillis()
            if (!noplay) say("Playing ${me.title}" + (if (me.author.isNotEmpty()) " by ${me.author}" else "") + (if (pos > 5) " from ${fmtTime(pos)}" else "") + ".")
        }
    }

    private fun trackAt(b: Bk, pos: Double): Int {
        var i = b.tracks.size - 1
        while (i > 0 && pos < b.tracks[i].start - 0.01) i--
        return max(0, i)
    }

    fun bookPos(): Double {
        val b = bk ?: return 0.0
        if (!b.ready || b.tracks.isEmpty()) return 0.0
        val i = exo.currentMediaItemIndex.coerceIn(0, b.tracks.size - 1)
        return b.tracks[i].start + exo.currentPosition / 1000.0
    }

    fun bookSeek(pos: Double) {
        val b = bk ?: return
        if (!b.ready) return
        val p = pos.coerceIn(0.0, max(0.0, b.duration - 1))
        val i = trackAt(b, p)
        exo.seekTo(i, ((p - b.tracks[i].start) * 1000).toLong())
    }

    fun chapterAt(pos: Double): Int {
        val c = bk?.chapters ?: return -1
        var i = -1
        for (n in c.indices) { if (c[n].start <= pos + 0.5) i = n else break }
        return i
    }

    fun setSpeed(v: Float) {
        if (tg != null) { tgControl("rate", mapOf("rate" to v.toString()), if (v == 1f) "Normal speed." else "Speed $v times."); return }
        Prefs.bookSpeed = v
        exo.setPlaybackSpeed(v)
        say(if (v == 1f) "Normal speed." else "Speed $v times.")
    }

    fun setSleep(v: String) {
        if (!isPhone && tg == null) {
            launchSafe { val d = Api.post("/api/play/cmd", mapOf("device" to dev, "cmd" to "sleep:$v")); say(d.err().ifEmpty { d.s("message") }); refreshTv() }
            return
        }
        val b = bk ?: return
        b.sleep = v; b.sleepAt = 0L; b.sleepPos = -1.0
        when (v) {
            "chapter" -> {
                val c = b.chapters; val i = chapterAt(bookPos())
                b.sleepPos = if (i >= 0) (if (c[i].end > 0) c[i].end else c.getOrNull(i + 1)?.start ?: b.duration) else b.duration
                say("It will pause at the end of this chapter.")
            }
            "off" -> say("Sleep timer off.")
            else -> { b.sleepAt = System.currentTimeMillis() + (v.toIntOrNull() ?: 0) * 60_000L; say("It will pause in $v minutes.") }
        }
    }

    // ---------------------------------------------------------------- saving the place, stopping, the end
    fun savePlace(finished: Boolean = false) {
        lastSave = System.currentTimeMillis()
        val l = loc; val v = vd; val b = bk
        val form: Map<String, String>
        val path: String
        when {
            mode == Mode.AUDIO && l != null && l.ready && mediaReady -> {
                path = "/api/play/listen/save"
                form = mapOf("item" to l.item, "pos" to (exo.currentPosition / 1000.0).toString(), "finished" to if (finished) "1" else "0")
            }
            mode == Mode.VIDEO && v != null && mediaReady -> {
                path = "/api/play/listen/save"
                form = mapOf("item" to v.item, "pos" to (exo.currentPosition / 1000.0).toString(), "finished" to if (finished) "1" else "0")
            }
            mode == Mode.BOOK && b != null && b.ready && mediaReady -> {
                path = "/api/play/books/save"
                form = mapOf("item" to b.id, "pos" to (if (finished) b.duration else bookPos()).toString(),
                    "duration" to b.duration.toString(), "finished" to if (finished) "1" else "0")
            }
            else -> return
        }
        scope.launch { try { Api.post(path, form) } catch (e: Exception) { } }
    }

    fun stopPhone(quiet: Boolean) {
        if (mode != Mode.NONE) savePlace()
        val v = vd
        if (v != null && v.tok.isNotEmpty()) scope.launch { try { Api.post("/api/play/video/stop", mapOf("tok" to v.tok)) } catch (e: Exception) { } }
        wholeJob?.cancel()
        loc = null; vd = null; bk = null
        mode = Mode.NONE
        recoverTries = 0
        mediaReady = false
        fullscreen = false
        try { exo.stop(); exo.clearMediaItems() } catch (e: Exception) { }
        if (!quiet) say("Stopped.")
    }

    private fun onEnded() {
        if (tg != null) { savePlace(true); tgEnded(); return }
        when (mode) {
            Mode.AUDIO, Mode.VIDEO -> {
                savePlace(true)
                val video = mode == Mode.VIDEO
                val item = loc?.item ?: vd?.item ?: return
                val title = loc?.title ?: vd?.title ?: ""
                val more = loc?.hasNext ?: vd?.hasNext ?: false
                if (!more) { stopPhone(true); say("Finished $title."); return }
                launchSafe {
                    val d = Api.get("/api/play/listen/next?item=${enc(item)}")
                    val nx = d.optJSONObject("item")
                    if (nx == null) { stopPhone(true); say("That was the last episode."); return@launchSafe }
                    val n = Item(nx)
                    say("Next: ${n.label}.")
                    if (video) videoStart(n.id, n.label, false) else listenStart(n.id, n.label, false)
                }
            }
            Mode.BOOK -> { val t = bk?.title ?: ""; savePlace(true); stopPhone(true); say("Finished $t.") }
            else -> {}
        }
    }

    val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                if (recoverTries > 0 && mediaReady) say("Back again.")
                mediaReady = true; recoverTries = 0
            }
            if (state == Player.STATE_ENDED) onEnded()
            tick++
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && mode != Mode.NONE && exo.playbackState == Player.STATE_READY) savePlace()
            tick++
        }
        override fun onPlayerError(error: PlaybackException) {
            recover(error)
            tick++
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) lastSeekAt = System.currentTimeMillis()
        }
    }

    private fun everySecond() {
        tick++
        watchStalls()
        if (mode != Mode.NONE && exo.isPlaying && System.currentTimeMillis() - lastSave > 30_000) savePlace()
        val b = bk
        if (b != null && b.ready && (b.sleepAt > 0 || b.sleepPos >= 0)) {
            if ((b.sleepAt > 0 && System.currentTimeMillis() >= b.sleepAt) || (b.sleepPos >= 0 && bookPos() >= b.sleepPos - 0.5)) {
                b.sleepAt = 0L; b.sleepPos = -1.0; b.sleep = "off"
                if (tg != null) tgControl("pause") else exo.pause()
                say("Sleep timer: paused.")
            }
        }
    }

    // ---------------------------------------------------------------- the buttons
    fun cmd(c: String) {
        if (tg != null) { tgCmd(c); return }
        if (isPhone || mode != Mode.NONE) { phoneCmd(c); return }
        tvCmd(c)
    }

    private fun seekRel(d: Double) {
        if (mode == Mode.BOOK) {
            val b = bk ?: return
            val np = (bookPos() + d).coerceIn(0.0, max(0.0, b.duration - 1))
            bookSeek(np); say((if (d < 0) "Back" else "Forward") + " 30 seconds. Now at ${fmtTime(np)}.")
        } else {
            val np = max(0.0, exo.currentPosition / 1000.0 + d)
            exo.seekTo((np * 1000).toLong()); say((if (d < 0) "Back" else "Forward") + " 30 seconds. Now at ${fmtTime(np)}.")
        }
    }

    fun phoneCmd(c: String) {
        when (c) {
            "volup", "voldown" -> say("Use the phone's volume buttons.")
            "stop" -> stopPhone(false)
            "playpause" -> if (exo.playerError != null) { recoverTries = 0; exo.prepare(); exo.play(); say("Trying again.") }
                else if (exo.playWhenReady) { exo.pause(); say("Paused.") } else { exo.play(); say("Playing.") }
            "pause" -> { exo.pause(); say("Paused.") }
            "unpause", "play" -> { exo.play(); say("Playing.") }
            "back30" -> seekRel(-30.0)
            "fwd30" -> seekRel(30.0)
            "next", "prev" -> {
                if (mode == Mode.BOOK) {
                    val b = bk ?: return
                    val ch = b.chapters
                    if (ch.isEmpty()) { say("This book has no chapters."); return }
                    val pos = bookPos(); var i = chapterAt(pos)
                    if (c == "next") { if (i + 1 >= ch.size) { say("This is the last chapter."); return }; i++ }
                    else { if (i < 0) i = 0 else if (pos - ch[i].start < 5 && i > 0) i-- }
                    bookSeek(ch[i].start); say("Chapter ${i + 1} of ${ch.size}: ${ch[i].title}.")
                    scope.launch { delay(1500); savePlace() }
                } else if (c == "next") {
                    val item = loc?.item ?: vd?.item ?: return
                    val video = mode == Mode.VIDEO
                    savePlace()
                    launchSafe {
                        val d = Api.get("/api/play/listen/next?item=${enc(item)}")
                        val nx = d.optJSONObject("item")
                        if (nx == null) { say("There is no next episode."); return@launchSafe }
                        val n = Item(nx)
                        if (video) videoStart(n.id, n.label, false) else listenStart(n.id, n.label, false)
                    }
                }
            }
        }
    }

    /** The lock screen, notification, headset or the video's own controls. True = handled here. */
    fun onTransport(c: String): Boolean {
        if (tg != null) { tgCmd(c); return true }
        if (mode == Mode.BOOK && (c == "next" || c == "prev")) { phoneCmd(c); return true }
        if ((mode == Mode.AUDIO || mode == Mode.VIDEO) && c == "next") { phoneCmd("next"); return true }
        return false
    }

    fun onSeek(index: Int, ms: Long): Boolean {
        val t = tg ?: return false
        val pos = if (t.kind == "book") (bk?.tracks?.getOrNull(index)?.start ?: 0.0) + ms / 1000.0 else ms / 1000.0
        tgControl("seek", mapOf("pos" to pos.toString()))
        return true
    }

    // ---------------------------------------------------------------- a TV
    var tvStatus by mutableStateOf<JSONObject?>(null)

    suspend fun refreshTv() {
        if (isPhone || dev.isEmpty()) return
        try {
            tvStatus = Api.get("/api/play/status?device=${enc(dev)}")
        } catch (e: ApiError) {
            if (e.login) throw e
        }
    }

    private fun tvStart(it: Item, resume: Boolean) = launchSafe {
        stopPhone(true)
        say("Starting ${it.label}…")
        val d = Api.post("/api/play/start", mapOf("device" to dev, "item" to it.id, "resume" to if (resume) "1" else "0"))
        say(d.err().ifEmpty { d.s("message") })
        delay(2500); refreshTv()
    }

    private fun tvCmd(c: String) = launchSafe {
        if (dev.isEmpty()) { say("Choose a TV first."); return@launchSafe }
        var real = c
        val st = tvStatus
        if (c == "playpause" && st != null && st.b("playing")) real = if (st.b("paused")) "unpause" else "pause"
        val d = Api.post("/api/play/cmd", mapOf("device" to dev, "cmd" to real))
        say(d.err().ifEmpty { d.s("message").ifEmpty { "Done." } })
        delay(if (real == "next") 2500 else 800)
        refreshTv()
    }

    // ---------------------------------------------------------------- Now playing
    data class St(
        val playing: Boolean, val paused: Boolean, val text: String, val hasNext: Boolean = false,
        val book: Boolean = false, val hasPrev: Boolean = false, val bookId: String = "", val title: String = "",
        val adOff: Boolean = false, val tv: Boolean = false, val video: Boolean = false
    )

    fun status(): St {
        @Suppress("UNUSED_VARIABLE") val t0 = tick
        val t = tg
        if (t != null) return tgStatus(t)
        when (mode) {
            Mode.VIDEO -> {
                val v = vd ?: return St(false, false, "")
                if (!mediaReady) return St(true, false, "Getting the video of ${v.title} ready…", video = true)
                val len = if (v.runtime > 0) v.runtime else max(0.0, exo.duration / 1000.0)
                return St(true, !exo.playWhenReady, (if (exo.playWhenReady) "Playing" else "Paused") +
                    ": ${v.title}. ${fmtTime(exo.currentPosition / 1000.0)}" + (if (len > 0) " of ${fmtTime(len)}" else "") + ".",
                    hasNext = v.hasNext, video = true)
            }
            Mode.AUDIO -> {
                val l = loc ?: return St(false, false, "")
                if (!l.ready) return St(true, false, if (l.seeking) "Getting to where you left off in ${l.title}…" else "Getting the audio of ${l.title} ready… ${l.progress} percent.")
                return St(true, !exo.playWhenReady, (if (exo.playWhenReady) "Playing" else "Paused") +
                    " on this phone: ${l.title}. ${fmtTime(exo.currentPosition / 1000.0)}" + (if (l.runtime > 0) " of ${fmtTime(l.runtime)}" else "") + ".",
                    hasNext = l.hasNext)
            }
            Mode.BOOK -> {
                val b = bk ?: return St(false, false, "")
                if (!b.ready) return St(true, false, "Getting ${b.title} ready…", book = true, bookId = b.id, title = b.title)
                val pos = bookPos(); val i = chapterAt(pos)
                var s = (if (exo.playWhenReady) "Playing" else "Paused") + " on this phone: ${b.title}" + (if (b.author.isNotEmpty()) " by ${b.author}" else "") + "."
                if (i >= 0) s += " Chapter ${i + 1} of ${b.chapters.size}: ${b.chapters[i].title}."
                s += " ${fmtTime(pos)} of ${fmtTime(b.duration)}."
                val sp = exo.playbackParameters.speed
                if (sp != 1f) s += " Speed $sp times."
                if (b.sleepAt > 0) s += " Pausing in ${max(1L, (b.sleepAt - System.currentTimeMillis()) / 60000)} minutes."
                else if (b.sleepPos >= 0) s += " Pausing at the end of this chapter."
                return St(true, !exo.playWhenReady, s, hasNext = b.chapters.isNotEmpty() && i < b.chapters.size - 1,
                    book = true, hasPrev = b.chapters.isNotEmpty(), bookId = b.id, title = b.title)
            }
            else -> {}
        }
        if (isPhone) return St(false, false, "Nothing is playing on this phone.")
        val s = tvStatus ?: return St(false, false, "")
        if (s.err().isNotEmpty()) return St(false, false, s.err(), tv = true)
        return St(s.b("playing"), s.b("paused"), s.s("text"), hasNext = s.b("has_next"), book = s.b("book"),
            hasPrev = s.b("has_prev"), bookId = s.s("book_id"), title = s.s("title"),
            adOff = s.b("has_ad") && s.boolOrNull("ad") == false, tv = true)
    }

    // ---------------------------------------------------------------- playing together
    var together by mutableStateOf(Prefs.together)
    class Tg(val room: String) {
        var kind = ""; var role = ""; var iseq = 0; var iseqLoaded = 0; var msgSeq = 0
        var last = JSONObject(); var rtt = 0.15
    }
    var tg by mutableStateOf<Tg?>(null)
    var invites by mutableStateOf(listOf<JSONObject>())
    private val seenInvites = mutableSetOf<String>()
    private var tgJob: Job? = null

    fun toggleTogether() {
        together = !together
        Prefs.together = together
        say(if (together) "Play together is on. What you start now also plays on the other person's phone once they join." else "Play together is off.")
    }

    private fun tgMode() = if (role == "watch" && dev != "__local") "video" else "audio"

    private fun tgStartVideo(it: Item, resume: Boolean) {
        if (tg != null) tgLeave(true)
        stopPhone(true)
        say("Starting ${it.label} to play together…")
        launchSafe {
            val pos = if (resume) it.resumeTicks / 1e7 else 0.0
            val d = Api.post("/api/play/together/start", mapOf("cid" to Prefs.cid, "kind" to "video", "item" to it.id,
                "pos" to pos.toString(), "mode" to tgMode(), "title" to it.label))
            if (d.err().isNotEmpty()) { say(d.err()); return@launchSafe }
            tgBegin(d)
            say("Ready to play ${d.s("title")} together. When the other person presses Join it starts by itself - or press Play to start now.")
        }
    }

    private fun tgStartBook(id: String, label: String, resume: Boolean, at: Double?) {
        if (tg != null) tgLeave(true)
        stopPhone(true)
        say("Starting $label to listen together…")
        launchSafe {
            val b = Api.get("/api/play/books/book?id=${enc(id)}")
            if (b.err().isNotEmpty()) { say(b.err()); return@launchSafe }
            var pos = at ?: if (resume && !b.b("finished")) max(0.0, b.d("position") - 2) else 0.0
            if (pos >= b.d("duration") - 2) pos = 0.0
            val d = Api.post("/api/play/together/start", mapOf("cid" to Prefs.cid, "kind" to "book", "item" to id,
                "pos" to pos.toString(), "mode" to "audio", "title" to b.s("title")))
            if (d.err().isNotEmpty()) { say(d.err()); return@launchSafe }
            tgBegin(d)
            say("Ready to listen to ${d.s("title")} together. When the other person presses Join it starts by itself - or press Play to start now.")
        }
    }

    fun tgJoin(room: String) {
        val m = tgMode()
        if (!isPhone) pickDevice(if (m == "video" && devices.any { it.id == "__video" }) "__video" else "__local")
        stopPhone(true)
        say("Joining…")
        launchSafe {
            val d = Api.post("/api/play/together/join", mapOf("cid" to Prefs.cid, "room" to room, "mode" to tgMode()))
            if (d.err().isNotEmpty()) { say(d.err()); pollInvites(); return@launchSafe }
            tgBegin(d)
            say("Joined ${d.s("by")}: ${d.s("title")}. " + when {
                d.s("role") == "video" -> "You have the video."
                d.s("kind") == "book" -> "Getting the book ready."
                else -> "Getting the audio ready - it starts in step with ${d.s("by")}."
            })
        }
    }

    private fun tgBegin(d: JSONObject) {
        val t = Tg(d.s("id"))
        t.kind = d.s("kind"); t.role = d.s("role"); t.msgSeq = d.i("msg_seq"); t.last = d
        skips = 0; stalls = 0
        tg = t
        invites = emptyList()
        browse = false
        tgApply(d)
        tgJob?.cancel()
        tgJob = scope.launch {
            while (tg === t) {
                delay(1000)
                if (tg === t) tgBeat(t)
            }
        }
    }

    private suspend fun tgBeat(t: Tg) {
        val rd = mReady()
        val t0 = SystemClock.elapsedRealtime()
        try {
            val d = Api.post("/api/play/together/beat", mapOf("cid" to Prefs.cid, "room" to t.room,
                "pos" to (if (rd) mTime() else 0.0).toString(), "ready" to if (rd) "1" else "0",
                "buf" to if (mBuf()) "1" else "0", "iseq" to t.iseqLoaded.toString(),
                "sk" to skips.toString(), "stl" to stalls.toString()))
            if (tg !== t) return
            val r = (SystemClock.elapsedRealtime() - t0) / 1000.0
            if (r < 3) t.rtt = t.rtt * 0.7 + r * 0.3
            tgApply(d)
        } catch (e: ApiError) {
            if (e.login) signedOut(e.message ?: "")
        }
    }

    private fun tgApply(d: JSONObject) {
        val t = tg ?: return
        if (d.b("gone")) { tgEnd(if (d.b("left")) null else d.s("msg").ifEmpty { "Playing together has ended." }); return }
        if (d.s("id").isEmpty()) return
        t.last = d
        if (d.s("role").isNotEmpty()) t.role = d.s("role")
        t.kind = d.s("kind")
        val ms = d.i("msg_seq")
        if (ms != t.msgSeq) { t.msgSeq = ms; if (d.s("msg").isNotEmpty() && d.s("msg_by") != Prefs.cid) say(d.s("msg")) }
        val iseq = d.i("iseq")
        if (iseq != t.iseq) { t.iseq = iseq; tgLoad(d) }
        else if (t.iseqLoaded == iseq && mReady()) tgSync(d)
        tick++
    }

    private fun tgLoad(d: JSONObject) {
        val t = tg ?: return
        val seq = d.i("iseq")
        when {
            t.kind == "book" -> bookStartPhone(d.s("item"), d.s("title"), false, d.d("pos"), noplay = true)
            t.role == "video" -> videoStart(d.s("item"), d.s("title"), false, d.d("pos"))
            else -> listenStart(d.s("item"), d.s("title"), false, d.d("pos"))
        }
        t.iseqLoaded = seq
    }

    private fun mReady(): Boolean {
        val t = tg ?: return false
        if (!mediaReady) return false
        return when {
            t.kind == "book" -> bk?.ready == true && bk?.id == t.last.s("item")
            t.role == "video" -> vd != null
            else -> loc?.ready == true
        }
    }

    private fun mTime(): Double = if (mode == Mode.BOOK) bookPos() else exo.currentPosition / 1000.0
    private var bufSince = 0L
    private var seekWait = 0L
    private var seekCost = 0.5
    private var stuck = false
    private var skips = 0
    private var stalls = 0

    /** Still loading for more than a second (a quick hiccup isn't reported). Also learns how long a skip takes here. */
    private fun mBuf(): Boolean {
        val b = mReady() && exo.playbackState == Player.STATE_BUFFERING
        val now = SystemClock.elapsedRealtime()
        if (seekWait > 0 && mReady() && !b) {
            seekCost = (seekCost * 0.6 + (now - seekWait) / 1000.0 * 0.4).coerceIn(0.2, 6.0); seekWait = 0L
        }
        if (!b) { bufSince = 0L; stuck = false; return false }
        if (bufSince == 0L) bufSince = now
        if (now - bufSince > 1000 && !stuck) { stuck = true; stalls++ }
        return now - bufSince > 1000
    }

    private fun mSeek(t: Double) {
        seekWait = SystemClock.elapsedRealtime()
        skips++
        if (mode == Mode.BOOK) bookSeek(t) else exo.seekTo((max(0.0, t) * 1000).toLong())
    }
    private fun mRate(r: Double) { if (abs(exo.playbackParameters.speed - r) > 0.001) exo.setPlaybackSpeed(r.toFloat()) }

    private fun tgSync(d: JSONObject) {
        val t = tg ?: return
        val base = d.d("rate").let { if (it > 0) it else 1.0 }
        val run = d.b("run")
        val target = d.d("pos") + if (run) t.rtt / 2 * base else 0.0
        val diff = mTime() - target
        // Small differences: play a little faster or slower. Skipping (which makes the phone load again - slow on a
        // phone network) only for big ones, aimed ahead by how long a skip takes on this phone.
        val far = if (t.role == "video") 4.0 else 2.5
        val lead = min(5.0, seekCost) * base
        if (run) {
            if (!exo.playWhenReady) {
                if (abs(diff) > 0.75) mSeek(target + lead)
                mRate(base); exo.play(); return
            }
            when {
                abs(diff) > far -> { mSeek(target + lead); mRate(base) }
                abs(diff) > 0.15 -> mRate(base * (1 - (diff * 0.15).coerceIn(-0.08, 0.08)))
                else -> mRate(base)
            }
        } else {
            if (exo.playWhenReady) exo.pause()
            mRate(base)
            if (abs(diff) > 0.25) mSeek(target)
        }
    }

    fun tgControl(action: String, extra: Map<String, String> = emptyMap(), okText: String? = null) {
        val t = tg ?: return
        launchSafe {
            val d = Api.post("/api/play/together/control", mapOf("cid" to Prefs.cid, "room" to t.room, "action" to action) + extra)
            if (tg !== t) return@launchSafe
            if (d.err().isNotEmpty()) say(d.err()) else if (okText != null) say(okText)
            tgApply(d)
        }
    }

    private fun tgEnded() { val t = tg ?: return; tgControl("ended", mapOf("item" to t.last.s("item"))) }

    fun tgCmd(c0: String) {
        val t = tg ?: return
        val d = t.last
        var c = c0
        if (c == "volup" || c == "voldown") { say("Use the phone's volume buttons."); return }
        if (c == "stop") { tgLeave(false); return }
        if (c == "playpause") c = if (d.b("want") && !d.b("waiting")) "pause" else "play"
        if (c == "unpause") c = "play"
        if (c == "play") { tgControl("play", emptyMap(), if (d.b("waiting")) "Starting." else "Playing."); return }
        if (c == "pause") { tgControl("pause", emptyMap(), "Paused."); return }
        val now = if (mReady()) mTime() else d.d("pos")
        if (c == "back30" || c == "fwd30") {
            val np = max(0.0, now + if (c == "back30") -30.0 else 30.0)
            tgControl("seek", mapOf("pos" to np.toString()), (if (c == "back30") "Back" else "Forward") + " 30 seconds. Now at ${fmtTime(np)}.")
            return
        }
        if (t.kind == "book" && (c == "next" || c == "prev")) {
            val ch = bk?.chapters ?: emptyList()
            if (ch.isEmpty()) { say("This book has no chapters."); return }
            var i = chapterAt(now)
            if (c == "next") { if (i + 1 >= ch.size) { say("This is the last chapter."); return }; i++ }
            else { if (i < 0) i = 0 else if (now - ch[i].start < 5 && i > 0) i-- }
            tgControl("seek", mapOf("pos" to ch[i].start.toString()), "Chapter ${i + 1} of ${ch.size}: ${ch[i].title}.")
            return
        }
        if (c == "next") tgControl("next")
    }

    fun tgLeave(quiet: Boolean) {
        val t = tg ?: return
        seenInvites.add(t.room)
        tgJob?.cancel()
        tg = null
        stopPhone(true)
        if (!quiet) say("You left playing together.")
        scope.launch {
            try { Api.post("/api/play/together/control", mapOf("cid" to Prefs.cid, "room" to t.room, "action" to "leave")) } catch (e: Exception) { }
            pollInvites()
        }
    }

    private fun tgEnd(msg: String?) {
        tgJob?.cancel()
        tg = null
        stopPhone(true)
        if (msg != null) say(msg)
        scope.launch { pollInvites() }
    }

    private fun tgStatus(t: Tg): St {
        val d = t.last
        val others = d.optJSONArray("others").strs()
        val names = others.joinToString(" and ")
        val ready = mReady()
        val part = when {
            t.kind == "book" -> "the book"
            t.role == "video" -> "the video"
            loc?.ready == true && loc?.hasAd == false -> "the soundtrack (this one has no audio description)"
            else -> "the audio"
        }
        val state = when {
            !ready -> "Getting $part ready" + (loc?.let { if (!it.ready && it.progress > 0) ", ${it.progress} percent" else "" } ?: "") + "."
            d.b("waiting") -> if (others.isNotEmpty()) "Waiting for ${d.optJSONArray("not_ready").strs().joinToString(" and ").ifEmpty { names }} to be ready."
                else "Waiting for someone to join. Press Play to start now."
            d.optJSONArray("hold").strs().isNotEmpty() -> "Waiting a moment for ${d.optJSONArray("hold").strs().joinToString(" and ")} to catch up."
            d.b("want") -> "Playing."
            else -> "Paused."
        }
        val pos = if (ready) mTime() else d.d("pos")
        val ch = if (t.kind == "book" && bk?.ready == true && bk?.chapters?.isNotEmpty() == true) chapterAt(pos) else -1
        var text = (if (others.isNotEmpty()) "Together with $names" else "Playing together") + ": ${d.s("title")}. $state ${fmtTime(pos)}" +
            (if (d.d("runtime") > 0) " of ${fmtTime(d.d("runtime"))}" else "") + "."
        if (ch >= 0) text += " Chapter ${ch + 1} of ${bk!!.chapters.size}: ${bk!!.chapters[ch].title}."
        if (ready && t.kind != "book") text += " " + when {
            t.role == "video" -> "You have the video."
            loc?.hasAd == false -> "You hear the soundtrack."
            else -> "You hear the audio description."
        }
        val chs = bk?.chapters?.size ?: 0
        return St(true, !(d.b("want") && !d.b("waiting")), text,
            hasNext = if (t.kind == "video") d.b("has_next") else (ch >= 0 && ch < chs - 1),
            book = t.kind == "book", hasPrev = ch >= 0, bookId = d.s("item"), title = d.s("title"),
            video = t.role == "video" && t.kind != "book")
    }

    suspend fun pollInvites() {
        if (tg != null || me == null) { invites = emptyList(); return }
        try {
            val d = Api.get("/api/play/together/list?cid=${enc(Prefs.cid)}")
            if (tg != null) return
            val rs = d.optJSONArray("rooms").objs()
            invites = rs
            for (r in rs) {
                if (seenInvites.add(r.s("id"))) {
                    val w = (if (r.s("kind") == "book") "listen to " else "play ") + r.s("title")
                    say("${r.s("by")} wants to $w together. The Join button is at the top.")
                }
            }
        } catch (e: ApiError) {
            if (e.login) signedOut(e.message ?: "")
        }
    }
}
