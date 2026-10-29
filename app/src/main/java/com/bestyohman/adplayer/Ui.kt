package com.bestyohman.adplayer

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Pictures come from the PC with the sign-in attached. */
object Img {
    lateinit var loader: ImageLoader
    fun init(c: Context) {
        if (!::loader.isInitialized) loader = ImageLoader.Builder(c.applicationContext).okHttpClient(Api.http).crossfade(true).build()
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
fun AppRoot(act: MainActivity) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (Engine.screen) {
            "server" -> ServerScreen()
            "signin" -> SignInScreen()
            "main" -> MainScreen(act)
            else -> Column(
                Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Connecting to the PC…", Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun Heading(text: String, big: Boolean = false) {
    Text(
        text, style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp).semantics { heading() }
    )
}

@Composable
private fun Said() {
    if (Engine.message.isNotEmpty()) Text(Engine.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}

// ------------------------------------------------------------------------------------ first start
@Composable
fun ServerScreen() {
    var addr by remember { mutableStateOf(Prefs.server) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val connect: () -> Unit = {
        scope.launch {
            busy = true
            try {
                Api.probe(addr)
                Prefs.server = Api.normalize(addr)
                Engine.bootError = ""
                Engine.boot()
            } catch (e: ApiError) {
                if (e.login) { Prefs.server = Api.normalize(addr); Engine.screen = "signin" }
                else Engine.bootError = e.message ?: ""
            }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Heading("AD Player", true)
        Text(
            "Type the address of the PC that runs the app - the same one the AD Player web page uses, like 192.168.1.20:5021 " +
                "at home, or your web address away from home."
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = addr, onValueChange = { addr = it }, label = { Text("PC address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { connect() }), modifier = Modifier.fillMaxWidth()
        )
        if (Engine.bootError.isNotEmpty()) Text(Engine.bootError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = connect, enabled = !busy, modifier = Modifier.padding(top = 12.dp)) { Text(if (busy) "Connecting…" else "Connect") }
    }
}

@Composable
fun SignInScreen() {
    var user by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    val go: () -> Unit = {
        if (user.isBlank()) Engine.say("Type your user name.") else Engine.signIn(user.trim(), pw)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Heading("Sign in", true)
        Text("Use your Emby user name and password.")
        Said()
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text("User name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(onClick = go, modifier = Modifier.padding(top = 12.dp)) { Text("Sign in") }
        TextButton(onClick = { Engine.screen = "server" }) { Text("Change the PC address") }
    }
}

// ------------------------------------------------------------------------------------ the main page
@Composable
fun MainScreen(act: MainActivity) {
    val st = Engine.status()
    val fs = Engine.fullscreen && Engine.vd != null
    LaunchedEffect(fs) { act.setFullscreen(fs) }
    BackHandler(enabled = fs || Engine.stack.size > 1 || (Engine.browse && st.playing)) {
        when {
            fs -> Engine.fullscreen = false
            Engine.stack.size > 1 -> Engine.back()
            else -> Engine.browse = false
        }
    }
    if (fs) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { VideoView(Modifier.fillMaxSize()) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = if (maxWidth < 600.dp) 6 else 12
        val landSpan = if (cols == 6) 3 else 4
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            full {
                Column {
                    Heading("AD Player", true)
                    Said()
                }
            }
            if (Engine.invites.isNotEmpty() && Engine.tg == null) full { Invites() }
            if (st.playing && !Engine.browse) {
                full { NowPlaying(st) }
            } else {
                if (st.playing) full { Button(onClick = { Engine.browse = false }, modifier = Modifier.fillMaxWidth()) { Text("Back to Now playing") } }
                full { WhereSection() }
                full { FindSection() }
                listSection(landSpan)
                full { AccountSection() }
            }
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun Invites() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Heading("Play together")
            Engine.invites.forEach { r ->
                val w = (if (r.s("kind") == "book") "listen to " else "play ") + r.s("title")
                Text("${r.s("by")} wants to $w together.", Modifier.padding(vertical = 4.dp))
                Button(onClick = { Engine.tgJoin(r.s("id")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Join ${r.s("by")}: ${r.s("title")}")
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = id == selected, onClick = { onPick(id); onClose() }, role = Role.RadioButton)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = id == selected, onClick = null)
                        Text(label, Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun WhereSection() {
    var open by remember { mutableStateOf(false) }
    Section {
        Heading("Play on")
        val cur = Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "Choose…"
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Play on: $cur. Change" }) {
            Text(cur, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
        if (open) ChoiceDialog("Play on", Engine.devices.map { it.id to it.name }, Engine.dev, { Engine.pickDevice(it) }, { open = false })
        TextButton(onClick = { Engine.refreshDevices() }) { Text("Refresh TV list") }
        if (Engine.devHelp.isNotEmpty()) Text(Engine.devHelp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("books" to "Audiobooks", "shows" to "Shows", "movies" to "Movies").forEach { (k, l) ->
                val on = Engine.what == k
                val m = Modifier.weight(1f).semantics { selected = on }
                if (on) Button(onClick = { Engine.pickWhat(k) }, modifier = m, contentPadding = PaddingValues(4.dp)) { Text(l, maxLines = 1) }
                else OutlinedButton(onClick = { Engine.pickWhat(k) }, modifier = m, contentPadding = PaddingValues(4.dp)) { Text(l, maxLines = 1) }
            }
        }
        if (Engine.dev == "__video") QualityButton()
        if (Engine.isPhone) {
            val t = "Play together: " + if (Engine.together) "on" else "off"
            if (Engine.together) Button(onClick = { Engine.toggleTogether() }, modifier = Modifier.padding(top = 8.dp)) { Text(t) }
            else OutlinedButton(onClick = { Engine.toggleTogether() }, modifier = Modifier.padding(top = 8.dp)) { Text(t) }
            Text("When on, what you start here also plays on another person's phone, in step. They get a Join button.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindSection() {
    var q by remember { mutableStateOf("") }
    val w = Engine.what
    val books = w == "books"
    Section {
        Heading(if (books) "Audiobooks" else if (w == "movies") "Movies" else "Shows")
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text(if (books) "Search audiobooks" else if (w == "movies") "Search movies" else "Search shows") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { Engine.search(q) }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { Engine.search(q) }, modifier = Modifier.padding(top = 6.dp)) { Text("Search") }
        val btns = if (books) listOf(
            "Continue listening" to "books_continue", "All books" to "books_all", "Series" to "books_series", "Authors" to "books_authors"
        ) else listOf(
            "Continue watching" to "continue_$w", (if (w == "movies") "All movies" else "All shows") to w,
            "Favorites" to "favorites_$w", (if (w == "movies") "Movies without audio description" else "Shows without audio description") to "noad_$w",
            "Requests for audio description" to "requests"
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            btns.forEach { (label, kind) -> OutlinedButton(onClick = { Engine.openList(kind) }) { Text(label) } }
        }
    }
}

@Composable
private fun AccountSection() {
    val m = Engine.me ?: return
    Section {
        Heading("You")
        Text(
            "Signed in as ${m.s("name")}. " + if (Engine.watchUI) "On this phone you can watch the video, or just hear the audio."
            else "On this phone you hear just the audio - with audio description when there is one."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Engine.signOut() }) { Text("Sign out") }
            OutlinedButton(onClick = { Engine.screen = "server" }) { Text("Change the PC address") }
        }
    }
}

// ------------------------------------------------------------------------------------ Now playing
@Composable
fun VideoView(modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = PlayerHolder.session
                useController = true
                keepScreenOn = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setFullscreenButtonClickListener { Engine.fullscreen = !Engine.fullscreen }
            }
        },
        update = { pv -> if (pv.player !== PlayerHolder.session) pv.player = PlayerHolder.session },
        onRelease = { pv -> pv.player = null },
        modifier = modifier.background(Color.Black)
    )
}

@Composable
private fun QualityButton() {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Video quality: " + Engine.qualityLabel()) }
    if (open) ChoiceDialog("Video quality (megabits a second)", Engine.qualities, Engine.quality, { Engine.setQuality(it) }, { open = false })
}

@Composable
private fun NowPlaying(st: Engine.St) {
    var speedOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    Section {
        Heading("Now playing")
        if (st.video && Engine.vd != null) VideoView(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
        Text(st.text, Modifier.padding(vertical = 8.dp))
        val b = mutableListOf<Triple<String, Boolean, () -> Unit>>()
        b.add(Triple(if (st.paused) "Play" else "Pause", true, { Engine.cmd("playpause") }))
        b.add(Triple(if (Engine.tg != null) "Leave playing together" else "Stop", false, { Engine.cmd("stop") }))
        b.add(Triple("Back 30 seconds", false, { Engine.cmd("back30") }))
        b.add(Triple("Forward 30 seconds", false, { Engine.cmd("fwd30") }))
        if (st.book && st.hasPrev) b.add(Triple("Previous chapter", false, { Engine.cmd("prev") }))
        if (st.hasNext) b.add(Triple(if (st.book) "Next chapter" else "Next episode", false, { Engine.cmd("next") }))
        if (st.adOff) b.add(Triple("Turn on audio description", true, { Engine.cmd("ad") }))
        if (st.tv && Engine.tg == null) {
            b.add(Triple("Volume down", false, { Engine.cmd("voldown") }))
            b.add(Triple("Volume up", false, { Engine.cmd("volup") }))
        }
        b.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, primary, act) ->
                    if (primary) Button(onClick = act, modifier = Modifier.weight(1f)) { Text(label, textAlign = TextAlign.Center) }
                    else OutlinedButton(onClick = act, modifier = Modifier.weight(1f)) { Text(label, textAlign = TextAlign.Center) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (st.book) {
            OutlinedButton(onClick = {
                Engine.browse = true
                Engine.openList("books_chapters", st.bookId, st.title.ifEmpty { "Book" } + " chapters")
            }, modifier = Modifier.fillMaxWidth()) { Text("Chapters") }
            val phone = Engine.isPhone || Engine.tg != null
            val sp = if (Engine.tg != null) Engine.tg!!.last.d("rate").let { if (it > 0) it.toFloat() else 1f } else Prefs.bookSpeed
            if (phone) {
                OutlinedButton(onClick = { speedOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(if (sp == 1f) "Speed: normal" else "Speed: $sp times") }
            }
            val sl = Engine.bk?.sleep ?: "off"
            OutlinedButton(onClick = { sleepOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Sleep timer: " + when (sl) { "off" -> "off"; "chapter" -> "end of this chapter"; else -> "$sl minutes" })
            }
            if (speedOpen) ChoiceDialog("Speed",
                listOf("0.75" to "0.75 times", "1.0" to "Normal speed", "1.1" to "1.1 times", "1.25" to "1.25 times",
                    "1.5" to "1.5 times", "1.75" to "1.75 times", "2.0" to "2 times"),
                sp.toString(), { Engine.setSpeed(it.toFloat()) }, { speedOpen = false })
            if (sleepOpen) ChoiceDialog("Sleep timer",
                listOf("off" to "Off", "15" to "15 minutes", "30" to "30 minutes", "45" to "45 minutes", "60" to "1 hour", "chapter" to "End of this chapter"),
                sl, { Engine.setSleep(it) }, { sleepOpen = false })
        }
        if (st.video && Engine.vd != null) {
            OutlinedButton(onClick = { Engine.fullscreen = true }, modifier = Modifier.fillMaxWidth()) { Text("Full screen") }
            QualityButton()
        }
        OutlinedButton(onClick = { Engine.browse = true }, modifier = Modifier.fillMaxWidth()) { Text("Find something else") }
    }
}

// ------------------------------------------------------------------------------------ the lists
private fun noneText(kind: String): String = when {
    kind.startsWith("continue") || kind == "books_continue" -> "Nothing in progress."
    kind.startsWith("favorites") -> "No favorites yet. Use Add to favorites on a show or movie."
    kind == "requests" -> "Nothing asked for yet. Shows and movies without audio description have an Ask for audio description button."
    else -> "Nothing found."
}

private fun LazyGridScope.listSection(landSpan: Int) {
    val v = Engine.stack.lastOrNull() ?: return
    val list = Engine.items
    full {
        Column {
            Heading(v.title + if (v.kind != "detail" && list != null && list.isNotEmpty()) " (${list.size})" else "")
            if (Engine.stack.size > 1) OutlinedButton(onClick = { Engine.back() }) { Text("Back to ${Engine.stack[Engine.stack.size - 2].title}") }
        }
    }
    if (v.kind == "detail") { detail(v); return }
    if (list == null) { full { Text("Loading…") }; return }
    if (Engine.listError.isNotEmpty()) { full { Text(Engine.listError) }; return }
    if (list.isEmpty()) { full { Text(noneText(v.kind)) }; return }
    val rich = Engine.watchUI && !v.kind.startsWith("books_") && v.kind != "requests"
    if (!rich) {
        items(list, span = { GridItemSpan(maxLineSpan) }) { SimpleRow(it, v) }
        return
    }
    when {
        v.kind.startsWith("continue") -> items(list, span = { GridItemSpan(landSpan) }) { PosterCard(it, true) }
        v.kind == "episodes" -> items(list, span = { GridItemSpan(maxLineSpan) }) { EpisodeRow(it) }
        else -> {
            val big = list.filter { it.type == "Series" || it.type == "Movie" || it.type == "Season" }
            val eps = list.filter { it.type == "Episode" }
            items(big, span = { GridItemSpan(2) }) { PosterCard(it, false) }
            items(eps, span = { GridItemSpan(maxLineSpan) }) { EpisodeRow(it) }
        }
    }
}

private fun LazyGridScope.detail(v: Engine.View) {
    val item = v.item ?: return
    full { DetailHeader(item) }
    if (item.type != "Series") return
    full { SeasonChips() }
    val eps = Engine.episodes
    when {
        Engine.seasons.isEmpty() && eps == null -> full { Text("Loading…") }
        eps == null -> full { Text("Loading…") }
        eps.isEmpty() -> full { Text("No episodes.") }
        else -> items(eps, span = { GridItemSpan(maxLineSpan) }) { EpisodeRow(it) }
    }
}

@Composable
private fun Pic(item: Item, land: Boolean, modifier: Modifier) {
    val src = if (land) item.thumb.ifEmpty { item.backdrop.ifEmpty { item.poster } } else item.poster
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            item.name, Modifier.align(Alignment.Center).padding(6.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (src.isNotEmpty()) AsyncImage(
            model = Api.url(src) + "?w=" + (if (land) 480 else 320), contentDescription = null, imageLoader = Img.loader,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        if (item.played) Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xFF52B54B), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp)
        ) { Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        else if (item.hasAd == false) Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xB4000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp)
        ) { Text("No AD", color = Color.White, style = MaterialTheme.typography.labelSmall) }
        if (item.pct > 0 && !item.played) Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color(0x8C000000))
        ) { Box(Modifier.fillMaxHeight().fillMaxWidth(item.pct.coerceIn(0, 100) / 100f).background(Color(0xFF52B54B))) }
    }
}

@Composable
private fun PosterCard(item: Item, land: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .clickable { Engine.openItem(item) }
            .clearAndSetSemantics {
                contentDescription = item.spoken(); role = Role.Button
                onClick { Engine.openItem(item); true }
            }
    ) {
        Pic(item, land, Modifier.fillMaxWidth().aspectRatio(if (land) 16f / 9f else 2f / 3f))
        val ep = item.type == "Episode"
        Text(
            if (ep) item.seriesName.ifEmpty { item.name } else item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
        )
        val sub = when {
            ep -> item.epTitle()
            item.type == "Season" -> if (item.count > 0) "${item.count} episodes" else ""
            else -> item.year
        }
        Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EpisodeRow(item: Item) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth()
                .clickable { Engine.start(item, item.resume.isNotEmpty()) }
                .clearAndSetSemantics {
                    contentDescription = item.spoken(); role = Role.Button
                    onClick { Engine.start(item, item.resume.isNotEmpty()); true }
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Pic(item, true, Modifier.weight(0.42f).aspectRatio(16f / 9f))
            Column(Modifier.weight(0.58f)) {
                val t = if (Engine.stack.lastOrNull()?.kind in listOf("episodes", "detail")) item.epTitle()
                else (if (item.seriesName.isNotEmpty()) item.seriesName + " - " else "") + item.epTitle()
                Text(t, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                val m = mutableListOf<String>()
                if (item.length.isNotEmpty()) m.add(item.length)
                if (item.resume.isNotEmpty()) m.add("resumes at ${item.resume}") else if (item.played) m.add("watched")
                if (item.hasAd == false) m.add("no audio description")
                Text(m.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.overview.isNotEmpty()) Text(
                    item.overview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.resume.isNotEmpty()) OutlinedButton(
                onClick = { Engine.start(item, false) },
                modifier = Modifier.semantics { contentDescription = "Start ${item.label} from the beginning" }
            ) { Text("Start over") }
            if (item.hasAd == false) RequestButton(item)
        }
    }
}

@Composable
private fun DetailHeader(item: Item) {
    Column(Modifier.fillMaxWidth()) {
        if (item.backdrop.isNotEmpty()) AsyncImage(
            model = Api.url(item.backdrop) + "?w=1280", contentDescription = null, imageLoader = Img.loader,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(10.dp))
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pic(item, false, Modifier.size(width = 118.dp, height = 177.dp))
            Column {
                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val meta = listOf(
                    item.year,
                    if (item.type == "Series") (if (item.count > 0) "${item.count} season" + (if (item.count == 1) "" else "s") else "") else item.length,
                    item.rating, if (item.score.isNotEmpty()) "★ ${item.score}" else "", item.genres
                ).filter { it.isNotEmpty() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.hasAd == false) Text("No audio description", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.type == "Movie") {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Engine.start(item, item.resume.isNotEmpty()) }) {
                    Text(if (item.resume.isNotEmpty()) "Resume from ${item.resume}" else "Play")
                }
                if (item.resume.isNotEmpty()) OutlinedButton(onClick = { Engine.start(item, false) }) { Text("Start over") }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FavButton(item)
            if (item.hasAd == false) RequestButton(item)
        }
        if (item.overview.isNotEmpty()) Text(item.overview, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonChips() {
    val ss = Engine.seasons
    if (ss.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(ss) { i, s ->
            FilterChip(selected = i == Engine.seasonIdx, onClick = { Engine.pickSeason(i) }, label = { Text(s.label) })
        }
    }
}

@Composable
private fun FavButton(item: Item) {
    OutlinedButton(
        onClick = { Engine.toggleFavorite(item) },
        modifier = Modifier.semantics {
            contentDescription = (if (item.favorite) "Remove " else "Add ") + item.label + (if (item.favorite) " from favorites" else " to favorites")
        }
    ) { Text(if (item.favorite) "Remove from favorites" else "Add to favorites") }
}

@Composable
private fun RequestButton(item: Item) {
    OutlinedButton(
        onClick = { Engine.askForAd(item) },
        modifier = Modifier.semantics {
            contentDescription = (if (item.requested) "Asked for audio description for " else "Ask for audio description for ") + item.label
        }
    ) { Text(if (item.requested) "Asked for audio description" else "Ask for audio description") }
}

@Composable
private fun RowButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

/** The plain list - for people who only listen, for audiobooks and for requests. */
@Composable
private fun SimpleRow(item: Item, v: Engine.View) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        when (item.type) {
            "Request" -> Text("${item.label}: ${item.text}. Asked for on ${item.date}.", Modifier.padding(vertical = 6.dp))
            "BookSeries", "BookAuthor" -> RowButton(item.label + if (item.count > 0) ", ${item.count} book" + (if (item.count == 1) "" else "s") else "") { Engine.openItem(item) }
            "Chapter" -> {
                val n = (item.id.substringAfter("|").toIntOrNull() ?: 0) + 1
                val lab = if (Regex("^(chapter|part)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item.label)) item.label else "Chapter $n: ${item.label}"
                RowButton("$lab, starts at ${item.at}") { Engine.openItem(item) }
            }
            "Book" -> {
                val p = mutableListOf(item.label)
                if (item.author.isNotEmpty()) p.add("by ${item.author}")
                if (item.narrator.isNotEmpty()) p.add("read by ${item.narrator}")
                if (item.series.isNotEmpty() && v.kind != "books_inseries") p.add(item.series)
                if (item.length.isNotEmpty()) p.add(item.length)
                if (item.resume.isNotEmpty()) p.add((if (item.pct > 0) "${item.pct} percent done, " else "") + "resumes at ${item.resume}")
                else if (item.finished) p.add("finished")
                RowButton(p.joinToString(", ")) { Engine.bookPlay(item, true, null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.resume.isNotEmpty() || item.finished) OutlinedButton(
                        onClick = { Engine.bookPlay(item, false, 0.0) },
                        modifier = Modifier.semantics { contentDescription = "Start ${item.label} from the beginning" }
                    ) { Text("Start over") }
                    if (item.chapters > 1) OutlinedButton(
                        onClick = { Engine.push(Engine.View("books_chapters", item.id, "${item.label} chapters")) },
                        modifier = Modifier.semantics { contentDescription = "Chapters of ${item.label}" }
                    ) { Text("Chapters") }
                }
            }
            "Series" -> {
                RowButton(item.label + (if (item.count > 0) ", ${item.count} seasons" else "") + (if (item.hasAd == false) ", no audio description" else "")) {
                    Engine.push(Engine.View("seasons", item.id, "${item.label} seasons"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FavButton(item)
                    if (item.hasAd == false) RequestButton(item)
                }
            }
            "Season" -> RowButton(item.label + (if (item.count > 0) ", ${item.count} episodes" else "") + (if (item.played) ", watched" else "")) {
                Engine.push(Engine.View("episodes", item.id, v.title.removeSuffix(" seasons") + ", " + item.label))
            }
            else -> {
                val p = mutableListOf(item.label)
                if (item.length.isNotEmpty()) p.add(item.length)
                if (item.resume.isNotEmpty()) p.add("resumes at ${item.resume}") else if (item.played) p.add("watched")
                if (item.hasAd == false) p.add("no audio description")
                RowButton(p.joinToString(", ")) { Engine.start(item, item.resume.isNotEmpty()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.resume.isNotEmpty()) OutlinedButton(
                        onClick = { Engine.start(item, false) },
                        modifier = Modifier.semantics { contentDescription = "Start ${item.label} from the beginning" }
                    ) { Text("Start over") }
                    if (item.type == "Movie") FavButton(item)
                    if (item.hasAd == false) RequestButton(item)
                }
            }
        }
    }
}
