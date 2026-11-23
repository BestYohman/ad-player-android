package com.bestyohman.adplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/** A phone notification for a play together invitation (while the app is in the background). */
object TogetherNote {
    private const val CHANNEL = "together"
    private fun nm() = PlayerHolder.app.getSystemService(NotificationManager::class.java)

    fun show(room: String, text: String) {
        try {
            val c = PlayerHolder.app
            val nm = nm()
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Play together invitations", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(c, room.hashCode(), Intent(c, MainActivity::class.java)
                .setData(Uri.parse("adplayer://invite?room=$room")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val join = PendingIntent.getActivity(c, room.hashCode() + 1, Intent(c, MainActivity::class.java)
                .setData(Uri.parse("adplayer://join?room=$room")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(c)
            @Suppress("DEPRECATION")
            val n = b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Play together")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Join", join).build())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_SOCIAL)
                .setPriority(Notification.PRIORITY_HIGH)
                .build()
            nm.notify(room.hashCode(), n)
        } catch (e: Exception) { }
    }

    fun cancel(room: String) { try { nm().cancel(room.hashCode()) } catch (e: Exception) { } }
    fun cancelAll() { try { Engine.invites.forEach { cancel(it.s("id")) } } catch (e: Exception) { } }
}

/** What's going on while playing together: one line, what each phone is doing, what happened lately, and Invite. */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun TogetherBox(fg: Color, sub: Color, line: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val t = Engine.tg ?: return
    val d = t.last
    var showEv by remember { mutableStateOf(false) }
    Column(modifier) {
        val st = d.s("status")
        if (st.isNotEmpty()) Text(st, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (compact) return@Column
        val ppl = d.optJSONArray("people").objs()
        if (ppl.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ppl.forEach { p ->
                val ok = p.s("state") == "playing" || p.s("state") == "ready"
                Text((if (p.b("me")) "You" else p.s("name")) + ": " + p.s("state_text").ifEmpty { p.s("state") }, color = fg, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(if (Look.low) Look.line else 1.dp, if (ok) EGreen else line, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { Engine.inviteOpen() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Invite people", color = fg, fontWeight = FontWeight.Bold) }
            val ev = d.optJSONArray("events").objs()
            if (ev.isNotEmpty()) TextButton(onClick = { showEv = !showEv }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showEv) "Hide what happened" else "What happened", color = sub)
            }
        }
        if (showEv) d.optJSONArray("events").objs().reversed().forEach { e ->
            val ago = e.i("ago").let { if (it < 45) "just now" else if (it < 3600) "${it / 60} min ago" else "${it / 3600} h ago" }
            Text("${e.s("text")} ($ago)", color = sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** Who to invite: only people who can sign in to the player. */
@Composable
fun InviteDialog() {
    val ppl = Engine.inviting ?: return
    val picked = remember(ppl) { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = { Engine.inviting = null },
        title = { Text("Invite people to play together", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("They see an invitation in AD Player (and a phone notification when one is set up). Only people who can sign in to the player are listed.",
                    fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                ppl.forEach { p ->
                    val n = p.s("name"); val on = n in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(on, role = Role.Checkbox) { if (it) picked.add(n) else picked.remove(n) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(n, fontWeight = FontWeight.Bold)
                            Text((if (p.b("online")) "Using the app now" else "Not using the app right now") +
                                (if (p.b("notify")) " - gets a phone notification" else ""), fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Engine.inviteSend(picked.toList()) }, enabled = picked.isNotEmpty()) { Text("Invite") } },
        dismissButton = { TextButton(onClick = { Engine.inviting = null }) { Text("Not now") } }
    )
}

/** An Apple TV shows a code: she types it in once. */
@Composable
fun PairDialog() {
    val d = Engine.pairAsk ?: return
    var pin by remember(d) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Engine.pairAsk = null },
        title = { Text("Pair ${d.name.substringBefore(" - pair")}") },
        text = {
            Column {
                Text("Type the code the TV shows.", modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        },
        confirmButton = { TextButton(onClick = { Engine.pairFinish(pin) }, enabled = pin.length >= 4) { Text("Pair") } },
        dismissButton = { TextButton(onClick = { Engine.pairAsk = null }) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------------------------------- stats for nerds
@OptIn(UnstableApi::class)
object NetStats : AnalyticsListener {
    val loads = ArrayDeque<Pair<Long, Long>>()          // (when, bytes) of each piece loaded
    var dropped = 0L
    var lastLoadMs = 0L
    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        synchronized(loads) {
            loads.addLast(System.currentTimeMillis() to loadEventInfo.bytesLoaded)
            while (loads.size > 60) loads.removeFirst()
        }
        lastLoadMs = loadEventInfo.loadDurationMs
    }
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) { dropped += droppedFrames }
    fun bytesPerSec(windowMs: Long = 5000): Long {
        val now = System.currentTimeMillis()
        val sum = synchronized(loads) { loads.filter { now - it.first < windowMs }.sumOf { it.second } }
        return sum * 1000 / windowMs
    }
}

private fun kb(n: Long) = if (n >= 1048576) String.format(Locale.US, "%.1f MB", n / 1048576.0) else if (n >= 1024) "${n / 1024} KB" else "$n B"

/** A panel over the video, like YouTube's "Stats for nerds": the phone's side and the PC's side. */
@OptIn(UnstableApi::class)
@Composable
fun StatsOverlay(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val v = Engine.vd ?: return
    var srv by remember { mutableStateOf<JSONObject?>(null) }
    var rtt by remember { mutableStateOf(0L) }
    @Suppress("UNUSED_VARIABLE") val t = Engine.tick
    LaunchedEffect(v.item) { NetStats.dropped = 0 }
    LaunchedEffect(v.tok) {
        while (true) {
            if (v.tok.isNotEmpty()) try {
                val t0 = System.currentTimeMillis()
                val d = Api.get("/api/play/video/stats?tok=${enc(v.tok)}")
                rtt = System.currentTimeMillis() - t0
                if (d.err().isEmpty()) srv = d
            } catch (e: Exception) { }
            delay(3000)
        }
    }
    val exo = PlayerHolder.exo
    val vf = exo.videoFormat; val af = exo.audioFormat
    val L = mutableListOf<String>()
    L += "Video          ${v.title}  [${v.item}]"
    L += "Resolution     ${exo.videoSize.width}x${exo.videoSize.height}" + (vf?.let { if (it.frameRate > 0) String.format(Locale.US, " @ %.2f fps", it.frameRate) else "" } ?: "")
    val dec = exo.videoDecoderCounters
    L += "Frames         ${NetStats.dropped} dropped" + (dec?.let { " of ${it.renderedOutputBufferCount + it.droppedBufferCount}" } ?: "")
    L += "Codecs         ${vf?.codecs ?: vf?.sampleMimeType ?: "?"} / ${af?.codecs ?: af?.sampleMimeType ?: "?"}" +
        (af?.let { if (it.channelCount > 0) ", ${it.channelCount} ch" else "" } ?: "")
    vf?.bitrate?.takeIf { it > 0 }?.let { L += String.format(Locale.US, "Stream         %.1f Mbps", it / 1e6) }
    val bw = try { DefaultBandwidthMeter.getSingletonInstance(PlayerHolder.app).bitrateEstimate } catch (e: Exception) { 0L }
    if (bw > 0) L += "Connection     ${bw / 1000} Kbps"
    L += "Network        ${kb(NetStats.bytesPerSec())}/s over the last 5 s" + (if (NetStats.lastLoadMs > 0) ", last piece in ${NetStats.lastLoadMs} ms" else "")
    L += String.format(Locale.US, "Buffer health  %.1f s", exo.totalBufferedDuration / 1000.0)
    L += "Playback       ${clock(exo.currentPosition / 1000.0)} of ${clock(maxOf(v.runtime, exo.duration / 1000.0))}   speed ${exo.playbackParameters.speed}" +
        (if (!exo.playWhenReady) "   paused" else "")
    Engine.tg?.let { L += "Together       ${it.last.s("status")}" }
    srv?.let { x ->
        x.optJSONObject("emby")?.let { e ->
            L += "Emby           video ${e.s("video")}, audio ${e.s("audio")}" + listOf(e.s("size"), if (e.d("mbps") > 0) "${e.d("mbps")} Mbps" else "",
                if (e.d("fps") > 0) "${e.d("fps")} fps" else "", e.s("hw"), if (e.d("done_pct") > 0) "${e.d("done_pct")}% made" else "")
                .filter { it.isNotEmpty() }.joinToString(", ", prefix = ", ").let { if (it == ", ") "" else it }
            if (e.s("reasons").isNotEmpty()) L += "  why remade   ${e.s("reasons")}"
        }
        x.optJSONObject("source")?.let { so ->
            L += "Source         ${so.s("container")}, ${so.d("mbps")} Mbps, ${so.i("size_mb")} MB"
            if (so.s("video").isNotEmpty()) L += "  video        ${so.s("video")}"
            if (so.s("audio").isNotEmpty()) L += "  sound        ${so.s("audio")}"
            if (so.s("audio_name").isNotEmpty()) L += "  track        ${so.s("audio_name")}"
        }
        L += "From the PC    ${if (x.has("out_kbps")) "${x.i("out_kbps")} Kbps" else "-"}" +
            (if (x.i("seg_kb") > 0) ", pieces ${x.i("seg_kb")} KB in ${x.i("seg_ms")} ms" else "") +
            ", ${kb(x.optLong("bytes", 0))} sent, limit ${x.d("max_mbps")} Mbps" + (if (x.b("sub_burned")) ", subtitles drawn in" else "")
        L += "PC answers in  $rtt ms"
    } ?: run { if (v.tok.isNotEmpty()) L += "Asking the PC…" else L += "(a downloaded video: nothing from the PC)" }
    Box(modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(8.dp)).background(if (Look.low) Color.Black else Color(0xCC000000))
        .then(if (Look.low) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
        .semantics(mergeDescendants = true) { }) {
        Text(L.joinToString("\n"), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = if (Look.low) 15.sp else 11.sp, lineHeight = if (Look.low) 20.sp else 15.sp,
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 48.dp))
        Box(Modifier.align(Alignment.TopEnd).size(48.dp).clickable(onClick = onClose)
            .clearAndSetSemantics { role = Role.Button; contentDescription = "Close stats" }, contentAlignment = Alignment.Center) {
            Text("×", color = Color.White, fontSize = 24.sp)
        }
    }
}
