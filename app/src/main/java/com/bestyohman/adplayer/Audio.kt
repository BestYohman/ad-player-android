package com.bestyohman.adplayer

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** A dark shade of the cover's own colour, for the player's background (like Emby's). */
fun shadeOf(d: Drawable): Color {
    return try {
        val bm = d.toBitmap(12, 12, Bitmap.Config.ARGB_8888)
        var r = 0L; var g = 0L; var b = 0L
        for (x in 0 until 12) for (y in 0 until 12) { val c = bm.getPixel(x, y); r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255 }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / 144).toInt(), (g / 144).toInt(), (b / 144).toInt(), hsv)
        hsv[1] = (hsv[1] * 0.55f).coerceAtMost(0.45f); hsv[2] = 0.36f
        Color(android.graphics.Color.HSVToColor(hsv))
    } catch (e: Exception) { Color(0xFF3B3B33) }
}

/** The audio player (audiobooks, and the audio description on its own): laid out like Emby's music player. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(act: MainActivity) {
    val b = Engine.bk
    val l = Engine.loc
    @Suppress("UNUSED_VARIABLE") val t = Engine.tick
    val exo = PlayerHolder.exo
    val ctx = LocalContext.current
    val off = b?.let { Offline.done(it.id, "book") }
    val coverFile = off?.let { File(it.file, "cover.jpg") }?.takeIf { it.exists() }
    val coverUrl = when {
        b != null -> picUrl("/api/play/books/cover/${b.id}", 480)
        l != null -> picUrl("/api/play/img/${l.item}/Primary", 480)
        else -> ""
    }
    var tint by remember(coverUrl) { mutableStateOf(Color(0xFF3B3B33)) }
    val req = remember(coverUrl, coverFile) { ImageRequest.Builder(ctx).data(coverFile ?: coverUrl).allowHardware(false).build() }
    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    // where it's at: in a book, the chapter (the bar) and the whole book (underneath the title)
    val book = b != null
    val pos = if (book) Engine.bookPos() else exo.currentPosition / 1000.0
    val total = if (book) b!!.duration else (l?.runtime?.takeIf { it > 0 } ?: max(1.0, exo.duration / 1000.0))
    val ci = if (book) Engine.chapterAt(pos) else -1
    val chs = b?.chapters ?: emptyList()
    val segStart = if (ci >= 0) chs[ci].start else 0.0
    val segEnd = if (ci >= 0) (chs[ci].end.takeIf { it > chs[ci].start } ?: chs.getOrNull(ci + 1)?.start ?: total) else total
    val segLen = max(1.0, segEnd - segStart)
    var dragging by remember { mutableStateOf(false) }
    var dragTo by remember { mutableFloatStateOf(0f) }
    val inSeg = if (dragging) dragTo.toDouble() else (pos - segStart).coerceIn(0.0, segLen)
    val title = when {
        book && ci >= 0 -> chs[ci].title
        book -> b!!.title
        else -> l?.title ?: ""
    }
    val sub = when {
        book -> listOf(b!!.title.takeIf { ci >= 0 } ?: "", b.author).filter { it.isNotEmpty() }.joinToString(" · ")
        else -> "Audio description"
    }
    val paused = !exo.playWhenReady

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Color(0xFF151515))))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { W.player = false }) { Icon(Ic.ChevronDown, "Close the player - it keeps playing", tint = Color.White, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { dialog = "cast" }) { Icon(Ic.Cast, "Play on", tint = Color.White) }
            }
            Spacer(Modifier.weight(0.4f))
            Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))) {
                AsyncImage(req, null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onSuccess = { s -> tint = shadeOf(s.result.drawable) })
            }
            Spacer(Modifier.weight(0.5f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(40.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotEmpty()) Text(sub, color = Color(0xFFBDBDB0), fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Ic.More, "More", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (book) {
                            DropdownMenuItem(text = { Text("Start from the beginning") }, leadingIcon = { Icon(Ic.Restart, null) },
                                onClick = { menu = false; Engine.seekTo(0.0) })
                            val saved = Offline.find(b!!.id, "book")
                            if (saved == null) DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Ic.Download, null) }, onClick = {
                                menu = false
                                Offline.downloadBook(Item(JSONObject().put("id", b.id).put("type", "Book").put("label", b.title).put("author", b.author)))
                            })
                        }
                        DropdownMenuItem(text = { Text(if (Engine.tg != null) "Stop for everyone" else "Stop playing") }, leadingIcon = { Icon(Ic.Stop, null) },
                            onClick = { menu = false; Engine.cmd("stop"); W.player = false })
                        if (Engine.tg != null) DropdownMenuItem(text = { Text("Leave - the others carry on") }, leadingIcon = { Icon(Ic.Logout, null) },
                            onClick = { menu = false; Engine.tgLeave(false); W.player = false })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Slider(
                value = inSeg.toFloat(), valueRange = 0f..segLen.toFloat(),
                onValueChange = { dragging = true; dragTo = it },
                onValueChangeFinished = { Engine.seekTo(segStart + dragTo); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
                thumb = { Box(Modifier.size(14.dp).background(Color.White, CircleShape)) },
                track = { s ->
                    val f = ((s.value - s.valueRange.start) / (s.valueRange.endInclusive - s.valueRange.start).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color.White))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(30.dp).semantics { contentDescription = "Position ${clock(inSeg)} of ${clock(segLen)}" }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(inSeg), color = Color(0xFFBDBDB0), fontSize = 16.sp)
                Text(
                    when {
                        ci >= 0 -> "Chapter ${ci + 1} of ${chs.size}"
                        book -> "${clock(total - pos)} left"
                        else -> ""
                    }, color = Color(0xFFBDBDB0), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Text("-" + clock(segLen - inSeg), color = Color(0xFFBDBDB0), fontSize = 16.sp)
            }
            if (book && ci >= 0) Text("${clock(total - pos)} left in the book" + (if (b!!.sleep != "off") " · sleep timer on" else ""),
                color = Color(0xFF9E9E92), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Small(Ic.Prev, if (book) "Previous chapter" else "Previous", enabled = book) { Engine.cmd("prev") }
                Small(Ic.Replay, "Back 30 seconds") { Engine.cmd("back30") }
                IconButton(onClick = { Engine.cmd("playpause") }, modifier = Modifier.size(64.dp)) {
                    Icon(if (paused) Ic.Play else Ic.Pause, if (paused) "Play" else "Pause", tint = Color.White, modifier = Modifier.size(52.dp))
                }
                Small(Ic.Stop, if (Engine.tg != null) "Stop for everyone" else "Stop") { Engine.cmd("stop"); W.player = false }
                Small(Ic.Forward, "Forward 30 seconds") { Engine.cmd("fwd30") }
                Small(Ic.Next, if (book) "Next chapter" else "Next episode", enabled = book || (l?.hasNext == true)) { Engine.cmd("next") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.VolumeLow, null, tint = Color(0xFFBDBDB0), modifier = Modifier.size(24.dp))
                Slider(
                    value = exo.volume, onValueChange = { exo.volume = it }, valueRange = 0f..1f,
                    thumb = { Box(Modifier.size(12.dp).background(Color.White, CircleShape)) },
                    track = { s ->
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF))) {
                            Box(Modifier.fillMaxWidth(s.value.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFCFCFC4)))
                        }
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Volume" }
                )
                Icon(Ic.VolumeOn, null, tint = Color(0xFFBDBDB0), modifier = Modifier.size(26.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Small(Ic.ListIc, "Chapters", enabled = chs.isNotEmpty()) { sheet = true }
                Small(Ic.Speed, "Speed") { dialog = "speed" }
                Small(Ic.Bedtime, "Sleep timer", tint = if (b?.sleep != null && b.sleep != "off") EGreen else Color.White, enabled = book) { dialog = "sleep" }
                Small(Ic.Settings, "Settings") { dialog = "settings" }
            }
        }
    }
    if (sheet && b != null) {
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = st, containerColor = Color(0xFF1C1C1A)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(b.chapters) { i, c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Engine.seekTo(c.start); sheet = false }
                            .background(if (i == ci) Color(0x2252B54B) else Color.Transparent).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", color = if (i == ci) EGreen else ESub, fontSize = 15.sp, modifier = Modifier.widthIn(min = 34.dp))
                        Text(c.title, color = if (i == ci) EGreen else Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(clock(c.start), color = ESub, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    when (dialog) {
        "cast" -> PlayOnDialog { dialog = "" }
        "speed" -> {
            val cur = (if (Engine.tg != null) Engine.tg!!.last.d("rate").takeIf { it > 0 }?.toFloat() else null) ?: exo.playbackParameters.speed
            ChoiceDialog("Speed", listOf("0.75" to "0.75 times", "1.0" to "Normal", "1.1" to "1.1 times", "1.25" to "1.25 times",
                "1.5" to "1.5 times", "1.75" to "1.75 times", "2.0" to "2 times"), cur.toString(),
                { if (book) Engine.setSpeed(it.toFloat()) else exo.setPlaybackSpeed(it.toFloat()) }, { dialog = "" })
        }
        "sleep" -> ChoiceDialog("Sleep timer", listOf("off" to "Off", "15" to "15 minutes", "30" to "30 minutes", "45" to "45 minutes",
            "60" to "1 hour", "chapter" to "End of this chapter"), b?.sleep ?: "off", { Engine.setSleep(it) }, { dialog = "" })
        "settings" -> ChoiceDialog("Settings", buildList {
            add("cast" to "Play on: " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "choose"))
            add("together" to "Play together: " + if (Engine.together) "on" else "off")
            if (Engine.together || Engine.tg != null) add("delay" to "Headphone delay: " +
                (Engine.delays.firstOrNull { it.first == Engine.headphoneDelay }?.second ?: Engine.headphoneDelay))
        }, "", {
            when (it) {
                "cast" -> dialog = "cast"
                "together" -> Engine.toggleTogether()
                "delay" -> dialog = "delay"
            }
        }, { if (dialog == "settings") dialog = "" })
        "delay" -> ChoiceDialog("Headphone delay (playing together)", Engine.delays, Engine.headphoneDelay, { Engine.chooseDelay(it) }, { dialog = "" })
    }
}

@Composable
private fun Small(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onTap: () -> Unit) {
    IconButton(onClick = onTap, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(icon, label, tint = if (enabled) tint else Color(0x55FFFFFF), modifier = Modifier.size(32.dp))
    }
}
