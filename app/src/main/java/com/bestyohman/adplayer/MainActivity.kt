package com.bestyohman.adplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

private var netWatched = false

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        PlayerHolder.init(applicationContext)
        Img.init(applicationContext)
        Downloads.init(applicationContext)
        watchNetwork()
        Engine.announcer = { t ->
            @Suppress("DEPRECATION")
            window.decorView.announceForAccessibility(t)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { AppTheme { AppRoot(this) } }
        if (Engine.screen == "boot" || Engine.me == null) Engine.boot()
    }

    override fun onStart() {
        super.onStart()
        Engine.foreground = true
        // connecting starts the playback service, which keeps playing with the screen off
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        Engine.foreground = false
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    /** Tells the player when the phone finds a network again, and whether it's mobile data (for Auto video quality). */
    private fun watchNetwork() {
        if (netWatched) return
        netWatched = true
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        fun mobile(n: Network?): Boolean =
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        Engine.onMobileData = mobile(cm.activeNetwork)
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val m = mobile(network)
                    Engine.scope.launch { Engine.networkBack(m) }
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    Engine.onMobileData = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                }
            })
        } catch (e: Exception) { }
    }

    /** Full screen video: sideways, without the phone's bars. */
    fun setFullscreen(on: Boolean) {
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF8AB4F8), secondary = Color(0xFF52B54B))
        else lightColorScheme(primary = Color(0xFF0B57D0), secondary = Color(0xFF2E7D32)),
        content = content
    )
}
