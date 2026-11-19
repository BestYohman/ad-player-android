package com.bestyohman.adplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * "Version 1.9.0 is ready": looks at the app's latest release on GitHub now and then, and installs it when asked
 * (downloads it, then hands it to Android's installer - the phone asks once to allow installing from this app).
 */
object Update {
    private const val LATEST = "https://api.github.com/repos/BestYohman/ad-player-android/releases/tags/latest"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    var mine = ""                                      // this app's version
    private var myBuild = 0L                           // and its build number (GitHub's run number)
    var available by mutableStateOf("")                // a newer one on GitHub ("" = none)
    var notes by mutableStateOf("")                    // what changed in it
    var progress by mutableStateOf(-1)                 // downloading it: percent (-1 = not downloading)
    var message by mutableStateOf("")
    private var apkUrl = ""
    private var wantSize = -1L                         // from version.json: the APK's size and SHA-256 (checked before installing)
    private var wantSha = ""
    private var lastCheck = 0L

    fun init(c: Context) {
        if (mine.isEmpty()) {
            mine = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
            myBuild = BuildConfig.VERSION_CODE.toLong()
        }
    }

    /** 1.10.0 is newer than 1.9.2. */
    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }

    /** Looks on GitHub (at most every 6 hours unless [now]). Says so when asked and there's nothing new. */
    suspend fun check(now: Boolean = false) = withContext(Dispatchers.IO) {
        if (!now && System.currentTimeMillis() - lastCheck < 6 * 3600_000L) return@withContext
        lastCheck = System.currentTimeMillis()
        try {
            http.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
                if (!r.isSuccessful) { if (now) message = "Couldn't look for a new version (GitHub said ${r.code})."; return@withContext }
                val j = JSONObject(r.body?.string() ?: "{}")
                val assets = j.optJSONArray("assets")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } ?: emptyList()
                val a = assets.firstOrNull { it.optString("name").endsWith(".apk") }
                // version.json: {version, build, size, sha256} - a rebuild of the same version has a higher build
                val vj = assets.firstOrNull { it.optString("name") == "version.json" }?.let { va ->
                    try {
                        http.newCall(Request.Builder().url(va.optString("browser_download_url")).build()).execute().use { vr ->
                            if (vr.isSuccessful) JSONObject(vr.body?.string() ?: "{}") else null
                        }
                    } catch (e: Exception) { null }
                }
                val v = vj?.optString("version")?.ifEmpty { null } ?: Regex("""(\d+(?:\.\d+)+)""").find(j.optString("name"))?.value ?: ""
                val build = vj?.optLong("build", 0L) ?: 0L
                val isNewer = newer(v, mine) || (v == mine && build > myBuild)
                if (v.isNotEmpty() && a != null && isNewer) {
                    available = v; apkUrl = a.optString("browser_download_url")
                    wantSize = vj?.optLong("size", -1L) ?: -1L
                    wantSha = vj?.optString("sha256")?.lowercase() ?: ""
                    notes = j.optString("body").substringBefore("\nOn the phone:").replace("**", "").trim()
                    message = ""
                } else {
                    available = ""
                    if (now) message = "You have the newest version ($mine)."
                }
            }
        } catch (e: Exception) {
            if (now) message = "Couldn't look for a new version: ${e.message}"
        }
    }

    /** Downloads the new version and opens Android's installer. */
    suspend fun install(c: Context) {
        val app = c.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            // once: Android asks to allow installing apps from this app
            message = "Allow \"Install unknown apps\" for AD Player, then come back and press Install again."
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (apkUrl.isEmpty() || progress >= 0) return        // (already downloading)
        progress = 0
        val f = File(File(app.cacheDir, "updates").apply { mkdirs() }, "ADPlayer-$available.apk")
        var bad = ""
        val ok = withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext false
                    val body = r.body ?: return@withContext false
                    val total = body.contentLength(); var got = 0L
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024); val inp = body.byteStream()
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); got += n
                            if (total > 0) progress = (got * 100 / total).toInt()
                        }
                    }
                }
                if (f.length() <= 100_000) return@withContext false
                // the same file GitHub built? (size and SHA-256 from version.json)
                if (wantSize > 0 && f.length() != wantSize) { bad = "size"; return@withContext false }
                if (wantSha.isNotEmpty()) {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    f.inputStream().use { inp -> val buf = ByteArray(64 * 1024); while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
                    if (md.digest().joinToString("") { "%02x".format(it) } != wantSha) { bad = "sha"; return@withContext false }
                }
                true
            } catch (e: Exception) { false } finally { progress = -1 }
        }
        if (!ok) {
            f.delete()
            message = if (bad.isNotEmpty()) "The new version didn't download right (it isn't the file GitHub made). Try again later."
            else "The download of the new version didn't finish - try again."
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", f)
        app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
