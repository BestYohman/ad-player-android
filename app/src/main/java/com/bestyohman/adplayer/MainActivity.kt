package com.bestyohman.adplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        PlayerHolder.init(applicationContext)
        Img.init(applicationContext)
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
