package com.bestyohman.adplayer

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** The server's own settings page, for whoever runs it (the public server says so: "admin" in /api/play/me).
 *  Shown inside the app: the app asks the server for a one-time link with its sign-in, and the page opens signed in. */
object ServerSettings {
    var open by mutableStateOf(false)
    /** This person may open it (only the public server sends "admin"; the PC app at home never does). */
    val allowed: Boolean get() = Engine.me?.optBoolean("admin", false) == true
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ServerSettingsScreen() {
    var url by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }
    // Back: back through the settings page's own pages first, then out to the app
    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else ServerSettings.open = false }
    LaunchedEffect(Unit) {
        try {
            val d = Api.post("/api/play/admin/link", emptyMap())
            if (d.err().isNotEmpty()) err = d.err() else url = Api.url(d.s("url"))
        } catch (e: ApiError) {
            err = e.message ?: "Couldn't open the server's settings."
        } catch (e: Exception) {
            err = "Couldn't reach the server: ${e.message}"
        }
    }
    Column(Modifier.fillMaxSize().background(EBg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.Back, "Back to the AD Player") { ServerSettings.open = false }
            Spacer(Modifier.width(12.dp))
            Text("Server settings", color = EFg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
        }
        Box(Modifier.fillMaxSize()) {
            val u = url
            when {
                err.isNotEmpty() -> Text(err, color = EFg, modifier = Modifier.padding(20.dp))
                u == null -> CircularProgressIndicator(color = EGreen, modifier = Modifier.align(Alignment.Center))
                else -> AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            setBackgroundColor(android.graphics.Color.parseColor("#0B0D11"))
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                // only the server's own pages open here; anything else opens in the phone's browser
                                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                    val target = req.url.toString()
                                    if (target.startsWith(Api.base())) return false
                                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, req.url)) } catch (e: Exception) { }
                                    return true
                                }
                            }
                            loadUrl(u)
                            web = this
                        }
                    },
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0D11))
                )
            }
        }
    }
}
