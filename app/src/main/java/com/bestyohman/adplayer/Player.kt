package com.bestyohman.adplayer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** "1:02:03" / "44:13". */
fun clock(sec: Double): String {
    val s = max(0, sec.toInt()); val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

/** The video player, with controls laid out like Emby's. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(act: MainActivity) {
    val v = Engine.vd ?: return
    val (d, _) = rememberData("/api/play/info?id=${enc(v.item)}")
    val info = d?.let { Item(it) }
    var shown by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var poke by remember { mutableLongStateOf(0L) }
    var sheet by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val t = Engine.tick
    val exo = PlayerHolder.exo
    val playing = exo.playWhenReady
    LaunchedEffect(shown, poke, playing, sheet, dialog) {
        if (shown && playing && sheet.isEmpty() && dialog.isEmpty()) { delay(4500); shown = false }
    }
    val touch = { poke = System.currentTimeMillis() }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(
                onTap = { shown = !shown; touch() },
                onDoubleTap = { o -> if (!locked) { Engine.cmd(if (o.x < size.width / 2) "back30" else "fwd30"); shown = true; touch() } }
            )
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = PlayerHolder.session
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { pv -> if (pv.player !== PlayerHolder.session) pv.player = PlayerHolder.session },
            onRelease = { pv -> pv.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (!Engine.mediaReady || exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(56.dp))
        AnimatedVisibility(shown && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(v, info, act, touch, { sheet = it }, { dialog = it }, { locked = true; shown = true })
        }
        if (locked && shown) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { locked = false; touch() }, modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0x99000000))) {
                    Icon(Ic.Unlock, "Unlock the controls", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Text("Tap to unlock", color = Color.White, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (sheet.isNotEmpty()) InfoSheet(sheet, v, info) { sheet = "" }
    when (dialog) {
        "cast" -> PlayOnDialog { dialog = "" }
        "speed" -> ChoiceDialog("Speed", listOf("0.5" to "0.5 times", "0.75" to "0.75 times", "1.0" to "Normal", "1.25" to "1.25 times",
            "1.5" to "1.5 times", "2.0" to "2 times"), exo.playbackParameters.speed.toString(),
            { PlayerHolder.exo.setPlaybackSpeed(it.toFloat()) }, { dialog = "" })
        "settings" -> ChoiceDialog("Settings", buildList {
            add("quality" to "Video quality: " + Engine.qualityLabel())
            if (Engine.tg != null) add("delay" to "Headphone delay: " + (Engine.delays.firstOrNull { it.first == Engine.headphoneDelay }?.second ?: Engine.headphoneDelay))
            add("stop" to if (Engine.tg != null) "Stop for everyone" else "Stop playing")
            if (Engine.tg != null) add("leave" to "Leave - the others carry on")
        }, "", {
            when (it) {
                "quality" -> dialog = "quality"
                "delay" -> dialog = "delay"
                "stop" -> { Engine.cmd("stop"); W.player = false }
                "leave" -> { Engine.tgLeave(false); W.player = false }
            }
        }, { if (dialog == "settings") dialog = "" })
        "quality" -> ChoiceDialog("Video quality (megabits a second)", Engine.qualities, Engine.quality, { Engine.chooseQuality(it) }, { dialog = "" })
        "delay" -> ChoiceDialog("Headphone delay (playing together)", Engine.delays, Engine.headphoneDelay, { Engine.chooseDelay(it) }, { dialog = "" })
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(v: Engine.Vid, info: Item?, act: MainActivity, touch: () -> Unit, openSheet: (String) -> Unit,
                     openDialog: (String) -> Unit, lock: () -> Unit) {
    val exo = PlayerHolder.exo
    @Suppress("UNUSED_VARIABLE") val t = Engine.tick
    val dur = (if (v.runtime > 0) v.runtime else max(0.0, exo.duration / 1000.0)).coerceAtLeast(1.0)
    val pos = (exo.currentPosition / 1000.0).coerceIn(0.0, dur)
    var dragging by remember { mutableStateOf(false) }
    var dragTo by remember { mutableFloatStateOf(0f) }
    val shownPos = if (dragging) dragTo.toDouble() else pos
    val ep = info?.type == "Episode"
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val land = conf.screenWidthDp > conf.screenHeightDp            // sideways: a lower, wider layout
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0xB0000000), 0.25f to Color(0x30000000), 0.55f to Color(0x30000000), 1f to Color(0xD0000000))
        )
    ) {
        // the top: back, the show's logo; sound on/off and Play on
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { W.player = false }) { Icon(Ic.Back, "Back - it keeps playing", tint = Color.White, modifier = Modifier.size(30.dp)) }
            var logoFailed by remember(info?.logo) { mutableStateOf(info?.logo.isNullOrEmpty()) }
            if (info != null && !logoFailed) AsyncImage(picUrl(info.logo, 300), info.seriesName.ifEmpty { info.name }, Img.loader,
                Modifier.heightIn(max = 40.dp).widthIn(max = 160.dp), contentScale = ContentScale.Fit, onError = { logoFailed = true })
            Spacer(Modifier.weight(1f))
            val muted = exo.volume == 0f
            IconButton(onClick = { exo.volume = if (muted) 1f else 0f; touch() }) {
                Icon(if (muted) Ic.VolumeOff else Ic.VolumeOn, if (muted) "Sound off - turn it on" else "Sound on - turn it off", tint = Color.White)
            }
            IconButton(onClick = { openDialog("cast") }) { Icon(Ic.Cast, "Play on", tint = Color.White) }
        }
        // the middle: back, play / pause, forward
        Row(Modifier.align(Alignment.Center).offset(y = if (land) (-46).dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (land) 64.dp else 44.dp)) {
            IconButton(onClick = { Engine.cmd("back30"); touch() }, modifier = Modifier.size(64.dp)) {
                Icon(Ic.Replay, "Back 30 seconds", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            val paused = !exo.playWhenReady
            IconButton(onClick = { Engine.cmd("playpause"); touch() }, modifier = Modifier.size(88.dp)) {
                Icon(if (paused) Ic.Play else Ic.Pause, if (paused) "Play" else "Pause", tint = Color.White, modifier = Modifier.size(80.dp))
            }
            IconButton(onClick = { Engine.cmd("fwd30"); touch() }, modifier = Modifier.size(64.dp)) {
                Icon(Ic.Forward, "Forward 30 seconds", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        // the bottom: what it is, where it's at, and the buttons
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val sub = when {
                info == null -> ""
                ep -> (if (info.se >= 0 && info.ep >= 0) "S${info.se}:E${info.ep} - " else "") + info.name
                else -> listOf(info.year, info.rt).filter { it.isNotEmpty() }.joinToString("  ")
            }
            val title = when {
                info == null -> v.title
                ep -> info.seriesName.ifEmpty { info.name }
                else -> info.name
            }
            if (sub.isNotEmpty()) Text(sub, color = Color(0xFFBDBDBD), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Color.White, fontSize = if (land) 24.sp else 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (Engine.tg != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Ic.Group, null, tint = EGreen, modifier = Modifier.size(18.dp))
                Text(" Playing together", color = EGreen, fontSize = 14.sp)
            }
            Slider(
                value = shownPos.toFloat(), valueRange = 0f..dur.toFloat(),
                onValueChange = { dragging = true; dragTo = it; touch() },
                onValueChangeFinished = { PlayerHolder.session.seekTo((dragTo * 1000).toLong()); dragging = false; touch() },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x66FFFFFF)),
                thumb = { Box(Modifier.size(16.dp).background(Color.White, CircleShape)) },
                track = { s ->
                    val span = (s.valueRange.endInclusive - s.valueRange.start).coerceAtLeast(0.001f)
                    val f = ((s.value - s.valueRange.start) / span).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color.White))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(if (land) 28.dp else 36.dp).semantics { contentDescription = "Position ${clock(shownPos)} of ${clock(dur)}" }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(shownPos), color = Color(0xFFBDBDBD), fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                val left = dur - shownPos
                val speed = exo.playbackParameters.speed.coerceAtLeast(0.1f)
                val ends = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + (left / speed * 1000).toLong()))
                    .lowercase(Locale.getDefault())
                Text("-${clock(left)}  /  $ends", color = Color(0xFFBDBDBD), fontSize = 16.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = if (land) 2.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (v.hasNext || ep) Btn(Ic.Next, "Next episode") { Engine.cmd("next"); touch() }
                Btn(Ic.Lock, "Lock the controls") { lock() }
                Btn(Ic.Speed, "Speed") { if (Engine.tg != null) Engine.say("The speed stays the same while playing together.") else openDialog("speed") }
                Btn(Ic.Settings, "Settings") { openDialog("settings") }
                Btn(Ic.Rotate, "Turn sideways") { act.toggleLandscape(); touch() }
                if (land) { Spacer(Modifier.weight(1f)); Tabs(info, ep, openSheet) }
            }
            if (!land) Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Tabs(info, ep, openSheet)
            }
        }
    }
}

@Composable
private fun Tabs(info: Item?, ep: Boolean, openSheet: (String) -> Unit) {
    Tab("Info") { openSheet("info") }
    if (info?.j?.optJSONArray("chapters").objs().isNotEmpty()) Tab("Chapters") { openSheet("chapters") }
    if (ep) Tab("Up Next") { openSheet("next") }
}

@Composable
private fun Btn(icon: ImageVector, label: String, onTap: () -> Unit) {
    IconButton(onClick = onTap, modifier = Modifier.size(52.dp)) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun Tab(label: String, onTap: () -> Unit) {
    Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTap).padding(vertical = 6.dp, horizontal = 8.dp))
}

/** Info, Chapters and Up Next, from the bottom of the player. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(which: String, v: Engine.Vid, info: Item?, close: () -> Unit) {
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val (eps, _) = rememberData(if (which == "next" && info?.seasonId?.isNotEmpty() == true) "/api/play/list?kind=episodes&parent=${enc(info.seasonId)}" else null)
    ModalBottomSheet(onDismissRequest = close, sheetState = st, containerColor = Color(0xFF1A1A1A)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            when (which) {
                "info" -> item {
                    Column {
                        Text(info?.label ?: v.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val m = listOfNotNull(info?.aired?.ifEmpty { null } ?: info?.year?.ifEmpty { null }, info?.rt?.ifEmpty { null },
                            info?.rating?.ifEmpty { null }).joinToString("  ·  ")
                        if (m.isNotEmpty()) Text(m, color = ESub, modifier = Modifier.padding(top = 4.dp))
                        if (info?.hasAd == false) Text("No audio description", color = Color(0xFFFFB74D), modifier = Modifier.padding(top = 4.dp))
                        Text(info?.overview ?: "", color = Color.White, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                "chapters" -> {
                    val ch = info?.j?.optJSONArray("chapters").objs()
                    items(ch) { c ->
                        Row(Modifier.fillMaxWidth().clickable { PlayerHolder.session.seekTo((c.d("start") * 1000).toLong()); close() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(ECard)) {
                                if (c.s("image").isNotEmpty()) AsyncImage(picUrl(c.s("image"), 400), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(c.s("name"), color = Color.White, fontSize = 16.sp)
                                Text(clock(c.d("start")), color = ESub, fontSize = 14.sp)
                            }
                        }
                    }
                }
                "next" -> {
                    val all = eps?.itemList() ?: emptyList()
                    val after = all.dropWhile { it.id != v.item }.drop(1)
                    if (eps == null) item { CircularProgressIndicator(color = EGreen) }
                    else if (after.isEmpty()) item { Text("This is the last episode of the season.", color = ESub) }
                    items(after) { e ->
                        Row(Modifier.fillMaxWidth().clickable { close(); Engine.start(e, e.resume.isNotEmpty()) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Poster(e, e.thumb, 480, Modifier.width(128.dp).aspectRatio(16f / 9f), round = 6.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text((if (e.ep >= 0) "${e.ep}. " else "") + e.name, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(e.aired, e.rt).filter { it.isNotEmpty() }.joinToString("  "), color = ESub, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
