package com.bestyohman.adplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** What the app remembers on the phone. */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.getSharedPreferences("adplayer", Context.MODE_PRIVATE)
    }
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun put(k: String, v: String) { sp.edit().putString(k, v).apply() }
    var server: String
        get() = str("server")
        set(v) = put("server", v)
    var token: String
        get() = str("token")
        set(v) = put("token", v)
    var dev: String
        get() = str("dev")
        set(v) = put("dev", v)
    var what: String
        get() = str("what", "shows")
        set(v) = put("what", v)
    var together: Boolean
        get() = sp.getBoolean("together", false)
        set(v) { sp.edit().putBoolean("together", v).apply() }
    var bookSpeed: Float
        get() = sp.getFloat("bookSpeed", 1f)
        set(v) { sp.edit().putFloat("bookSpeed", v).apply() }
    val cid: String
        get() {
            var c = str("cid")
            if (c.isEmpty()) { c = "a" + UUID.randomUUID().toString().replace("-", "").take(16); put("cid", c) }
            return c
        }
}

class ApiError(msg: String, val login: Boolean = false) : Exception(msg)

fun JSONObject.s(k: String): String = if (has(k) && !isNull(k)) optString(k) else ""
fun JSONObject.b(k: String): Boolean = has(k) && !isNull(k) && optBoolean(k, false)
fun JSONObject.d(k: String): Double { val v = optDouble(k, 0.0); return if (v.isNaN()) 0.0 else v }
fun JSONObject.i(k: String): Int = optInt(k, 0)
fun JSONObject.boolOrNull(k: String): Boolean? = if (!has(k) || isNull(k)) null else optBoolean(k)
fun JSONObject.err(): String = s("error")
fun JSONArray?.objs(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray?.strs(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** "1 hour 5 minutes", like the web player says it. */
fun fmtTime(sec: Double): String {
    val s = maxOf(0, sec.toInt())
    val h = s / 3600; val m = s % 3600 / 60
    if (h > 0) return "$h hour" + (if (h > 1) "s" else "") + " $m minute" + (if (m != 1) "s" else "")
    if (m > 0) return "$m minute" + (if (m != 1) "s" else "")
    return "$s second" + (if (s != 1) "s" else "")
}

/** Talks to the AD Player on the PC (the same addresses the web page uses). */
object Api {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val r = chain.request()
            val t = Prefs.token
            val mine = known(r.url.toString())
            chain.proceed(if (t.isNotEmpty() && mine) r.newBuilder().header("X-ADP-Token", t).build() else r)
        }
        .build()

    /** The address in use now: the PC's home-network address when on the home Wi-Fi, else the one typed in. */
    var active by mutableStateOf("")
    fun base(): String = active.ifEmpty { normalize(Prefs.server) }
    val atHome: Boolean get() = active.isNotEmpty() && active != normalize(Prefs.server)

    fun homeUrls(): List<String> = try { JSONArray(Prefs.str("home_urls", "[]")).strs() } catch (e: Exception) { emptyList() }

    private fun known(url: String): Boolean =
        (listOf(normalize(Prefs.server)) + homeUrls()).any { it.isNotEmpty() && url.startsWith(it) }

    /** Keeps what the PC says about itself: which PC it is and its home-network addresses. */
    fun rememberServer(me: JSONObject) {
        if (me.s("server_id").isNotEmpty()) Prefs.put("server_id", me.s("server_id"))
        me.optJSONArray("home_urls")?.let { Prefs.put("home_urls", it.toString()) }
    }

    private val quick: OkHttpClient by lazy {
        http.newBuilder().connectTimeout(1500, TimeUnit.MILLISECONDS).callTimeout(2500, TimeUnit.MILLISECONDS).build()
    }

    /** Which address to use: on Wi-Fi, this PC's home address if it answers (and it's the same PC); otherwise the
     *  address typed in (the web address away from home). */
    suspend fun chooseAddress(onWifi: Boolean): String = withContext(Dispatchers.IO) {
        val away = normalize(Prefs.server)
        val id = Prefs.str("server_id")
        if (!onWifi || id.isEmpty()) return@withContext away
        for (h in homeUrls()) {
            if (h == away) return@withContext away
            try {
                quick.newCall(Request.Builder().url("$h/api/play/me").build()).execute().use { r ->
                    if (r.isSuccessful && JSONObject(r.body?.string() ?: "{}").s("server_id") == id) return@withContext h
                }
            } catch (e: Exception) { }
        }
        away
    }

    fun normalize(addr: String): String {
        var a = addr.trim().trimEnd('/')
        if (a.isEmpty()) return ""
        if (!a.startsWith("http://", true) && !a.startsWith("https://", true)) a = "http://$a"
        if (a.endsWith("/play")) a = a.removeSuffix("/play")
        return a
    }

    fun url(path: String): String = if (path.contains("://")) path else base() + path

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        exec(Request.Builder().url(url(path)).header("Cache-Control", "no-cache").build())
    }

    suspend fun post(path: String, form: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val fb = FormBody.Builder()
        form.forEach { (k, v) -> fb.add(k, v) }
        exec(Request.Builder().url(url(path)).post(fb.build()).build())
    }

    /** Just checks an address answers like the AD Player. */
    suspend fun probe(addr: String): JSONObject = withContext(Dispatchers.IO) {
        exec(Request.Builder().url(normalize(addr) + "/api/play/me").build())
    }

    private fun exec(req: Request): JSONObject {
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw ApiError("Couldn't reach the PC. Check the address, and that the phone is online.")
        } catch (e: IllegalArgumentException) {
            throw ApiError("That address isn't right.")
        }
        resp.use {
            val body = it.body?.string() ?: ""
            val j = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw ApiError(if (it.code == 404) "That address doesn't look like the AD Player." else "Unexpected answer from the PC (${it.code}).")
            }
            if (it.code == 401 || j.b("login")) throw ApiError(j.err().ifEmpty { "Please sign in." }, login = true)
            return j
        }
    }
}

/** One thing in a list: a show, season, episode, movie, book, chapter or request. */
class Item(val j: JSONObject) {
    val id get() = j.s("id")
    val type get() = j.s("type")
    val label get() = j.s("label")
    val name get() = j.s("name").ifEmpty { label }
    val length get() = j.s("length")
    val played get() = j.b("played")
    val resume get() = j.s("resume")
    val resumeTicks get() = j.optLong("resume_ticks", 0L)
    val hasAd get() = j.boolOrNull("has_ad")
    var favorite by mutableStateOf(j.b("favorite"))
    var requested by mutableStateOf(j.b("requested"))
    val count get() = j.i("count")
    val year get() = j.s("year")
    val overview get() = j.s("overview")
    val rating get() = j.s("rating")
    val score get() = j.s("score")
    val genres get() = j.s("genres")
    val pct get() = j.i("pct")
    val seriesName get() = j.s("series_name")
    val se get() = j.optInt("se", -1)
    val ep get() = j.optInt("ep", -1)
    val poster get() = j.s("poster")
    val thumb get() = j.s("thumb")
    val backdrop get() = j.s("backdrop")
    // audiobooks
    val author get() = j.s("author")
    val narrator get() = j.s("narrator")
    val series get() = j.s("series")
    val chapters get() = j.i("chapters")
    val finished get() = j.b("finished")
    val at get() = j.s("at")
    val start get() = j.d("start")
    val book get() = j.s("book")
    val bookTitle get() = j.s("book_title")
    // requests
    val text get() = j.s("text")
    val date get() = j.s("date")

    fun epTitle(): String = (if (se >= 0 && ep >= 0) "S$se:E$ep - " else "") + name

    /** What TalkBack reads for a card. */
    fun spoken(): String {
        val p = mutableListOf(label)
        if (year.isNotEmpty() && type != "Episode") p.add(year)
        if (length.isNotEmpty()) p.add(length)
        if (resume.isNotEmpty()) p.add("resumes at $resume") else if (played) p.add("watched")
        if (hasAd == false) p.add("no audio description")
        return p.joinToString(", ")
    }
}
