package com.bestyohman.adplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ======================================================================================================
// The app for people who watch the video: looks and works like the AD Player web page (full vision) - the
// glass bar at the top, the five tabs at the bottom, a home page of rows, a page for each show and movie, and
// the mini player. Changes are made on the web page first, then here. People who only listen keep the plain
// list in Ui.kt, made for TalkBack.
// ======================================================================================================

// The web page's full-vision colours (AD Player web page, body.sight-full)
val EBg = Color(0xFF0B0D11)
val ECard = Color(0xFF14171D)
val EGreen = Color(0xFF2DD4BF)          // the accent (teal on the web page)
val ESub = Color(0xFFA3ABB8)
val ELine = Color(0xFF2B313C)
val EBtn2 = Color(0xFF1D212A)
val EInk = Color(0xFF0B0D11)            // dark words on white or teal
val ERed = Color(0xFFE53935)

/** A page in the watch app. */
data class Page(val kind: String, val id: String = "", val title: String = "", val extra: String = "")

object W {
    val stack = mutableStateListOf(Page("home"))
    var tab by mutableStateOf("home")
    var player by mutableStateOf(false)               // the full-screen player is showing
    var refresh by mutableIntStateOf(0)                // bumped when something changed (watched, finished...)
    val cache = mutableStateMapOf<String, JSONObject>()
    val played = mutableStateMapOf<String, Boolean>()  // marked played / not played here (before the lists catch up)
    private val lists = HashMap<Page, LazyListState>()
    private val grids = HashMap<Page, LazyGridState>()
    var toast by mutableStateOf("")
    var trk by mutableStateOf("")                      // the Audio and subtitles box is open for this video
    var toastSeq by mutableIntStateOf(0)

    fun go(p: Page) { stack.add(p) }
    fun back(): Boolean {
        if (player) { player = false; return true }
        if (stack.size > 1) { lists.remove(stack.last()); grids.remove(stack.last()); stack.removeAt(stack.lastIndex); return true }
        if (tab != "home") { openTab("home"); return true }
        return false
    }
    fun openTab(t: String, p: Page = Page(t)) {
        tab = t
        stack.clear(); lists.clear(); grids.clear()
        stack.add(p)
    }
    fun list(p: Page): LazyListState = lists.getOrPut(p) { LazyListState() }
    fun grid(p: Page): LazyGridState = grids.getOrPut(p) { LazyGridState() }
    fun isPlayed(it: Item) = played[it.id] ?: it.played

    fun open(it: Item) {
        when (it.type) {
            "Series", "Movie", "Season", "Episode" -> go(Page("item", it.id, it.name, it.type))
            "BoxSet", "Folder", "CollectionFolder" -> go(Page("children", it.id, it.name))
            "Book" -> go(Page("book", it.id, it.label, it.j.toString()))
            else -> Engine.start(it, it.resume.isNotEmpty())
        }
    }

    fun markPlayed(it: Item, on: Boolean) {
        played[it.id] = on
        Engine.launchSafe {
            val d = Api.post("/api/play/played", mapOf("item" to it.id, "on" to if (on) "1" else "0"))
            if (d.err().isNotEmpty()) { played.remove(it.id); Engine.say(d.err()) } else { Engine.say(d.s("message")); refresh++ }
        }
    }
}

/** Gets a page's data from the PC (shown at once from the last time, then brought up to date). */
@Composable
fun rememberData(path: String?): Pair<JSONObject?, String> {
    var d by remember(path) { mutableStateOf(path?.let { W.cache[it] }) }
    var err by remember(path) { mutableStateOf("") }
    LaunchedEffect(path, W.refresh, Engine.offline) {
        if (path == null) return@LaunchedEffect
        try {
            val j = Api.get(path)
            if (j.err().isNotEmpty()) { if (d == null) err = j.err() } else { W.cache[path] = j; d = j; err = "" }
        } catch (e: ApiError) {
            if (e.login) Engine.signedOut(e.message ?: "") else if (d == null) err = e.message ?: ""
        } catch (e: Exception) {
            if (d == null) err = "Something went wrong: ${e.message}"
        }
    }
    return d to err
}

fun JSONObject.itemList(k: String = "items"): List<Item> = optJSONArray(k).objs().map { Item(it) }

fun picUrl(src: String, w: Int): String = if (src.isEmpty()) "" else Api.url(src) + (if ("/books/" in src) "" else "?w=$w")

// ------------------------------------------------------------------------------------------- the frame
@Composable
fun WatchApp(act: MainActivity) {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(act.window, view).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EGreen, onPrimary = EInk, secondary = EGreen, onSecondary = EInk, background = EBg, surface = EBg, surfaceVariant = ECard,
            surfaceContainer = ECard, surfaceContainerHigh = EBtn2, surfaceContainerHighest = EBtn2, onSurfaceVariant = ESub, outline = ELine
        )
    ) {
        val vd = Engine.vd
        val bk = Engine.bk
        val loc = Engine.loc
        LaunchedEffect(vd) { if (vd != null) W.player = true }
        LaunchedEffect(bk) { if (bk != null) W.player = true }
        LaunchedEffect(loc) { if (loc != null) W.player = true }
        val video = Engine.mode == Engine.Mode.VIDEO && vd != null
        val audio = (Engine.mode == Engine.Mode.BOOK && bk != null) || (Engine.mode == Engine.Mode.AUDIO && loc != null)
        val showPlayer = W.player && (video || audio)
        LaunchedEffect(showPlayer, video) { act.playerMode(showPlayer && video) }
        LaunchedEffect(Engine.message) {
            if (Engine.message.isNotEmpty()) { W.toast = Engine.message; val n = ++W.toastSeq; delay(3500); if (W.toastSeq == n) W.toast = "" }
        }
        BackHandler(enabled = W.player || W.stack.size > 1 || W.tab != "home") { W.back() }
        if (showPlayer) { if (video) PlayerScreen(act) else AudioScreen(act); return@MaterialTheme }
        Surface(Modifier.fillMaxSize(), color = EBg) {
            Box(Modifier.fillMaxSize()) {
                val page = W.stack.last()
                key(page) { PageContent(page) {} }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                    if (W.toast.isNotEmpty()) Toast(W.toast)
                    MiniPlayer()
                    BottomNav()
                }
            }
        }
        if (W.trk.isNotEmpty()) TrackSheet(W.trk) { W.trk = "" }
    }
}

@Composable
private fun Toast(t: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            t, color = EInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center).background(Color(0xF2F2F4F7), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun PageContent(p: Page, openDrawer: () -> Unit) {
    when (p.kind) {
        "home" -> WebHome(p)
        "favorites" -> HomePage(p, true, openDrawer)
        "library", "children", "list" -> GridPage(p, openDrawer)
        "item" -> if (p.extra == "Season") SeasonPage(p, openDrawer) else DetailPage(p, openDrawer)
        "search" -> SearchPage(p, openDrawer)
        "downloads" -> DownloadsPage(p, openDrawer)
        "movies" -> LibTab("movies", openDrawer)
        "libtab" -> LibTab(p.id, openDrawer)
        "books" -> BooksPage(p, openDrawer)
        "book" -> BookPage(p, openDrawer)
        "settings" -> SettingsPage(p, openDrawer)
        "nowplaying" -> NowPlayingPage(p, openDrawer)
        else -> HomePage(p, false, openDrawer)
    }
}

/** The bar at the top, as on the web page: a floating glass bar with the logo, "AD Player", Search and Play on (Back
 *  in front when a page was opened from another). A page's name is its big heading under the bar. */
@Composable
private fun TopBar(title: String?, openDrawer: () -> Unit, solid: Boolean = true, heading: Boolean = true) {
    var castOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().then(if (solid) Modifier.background(EBg) else Modifier).statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (solid) Color(0xF212151C) else Color(0x5C12151C))
                    .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(20.dp)).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (W.stack.size > 1) { GlassButton(Ic.Back, "Back") { W.back() }; Spacer(Modifier.width(8.dp)) }
                LogoMark()
                Text("AD Player", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { heading() })
                GlassButton(Ic.Search, "Search") { if (W.stack.last().kind != "search") W.go(Page("search")) }
                Spacer(Modifier.width(8.dp))
                GlassButton(Ic.Cast, "Play on: " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "choose"), wide = true) { castOpen = true }
            }
        }
        if (heading && solid && !title.isNullOrEmpty()) Text(
            title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp,
            modifier = Modifier.fillMaxWidth().background(EBg).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).semantics { heading() }
        )
    }
    if (castOpen) PlayOnDialog { castOpen = false }
}

/** The web page's logo: headphones on a teal tile. */
@Composable
fun LogoMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5EEAD4), Color(0xFF2DD4BF), Color(0xFF0F9E8E)))),
        contentAlignment = Alignment.Center
    ) { Icon(Ic.Headphones, null, tint = EInk, modifier = Modifier.size(size * 0.62f)) }
}

/** A round glass button in the top bar (Search, Play on, Back). */
@Composable
fun GlassButton(icon: ImageVector, label: String, wide: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).width(if (wide) 54.dp else 44.dp).clip(RoundedCornerShape(if (wide) 22.dp else 12.dp))
            .background(Color(0x12FFFFFF)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(if (wide) 22.dp else 12.dp))
            .clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
fun PlayOnDialog(onClose: () -> Unit) {
    LaunchedEffect(Unit) { Engine.refreshDevices() }
    ChoiceDialog("Play on", Engine.devices.map { it.id to it.name }, Engine.dev, { Engine.pickDevice(it) }, onClose)
}

@Composable
private fun BottomNav() {
    if (W.player) return
    Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xE612151C))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("home", "Home", Ic.Home), Triple("shows", "Shows", Ic.Tv), Triple("movies", "Movies", Ic.Movie),
                Triple("books", "Books", Ic.Headphones), Triple("you", "You", Ic.Person)
            ).forEach { (k, l, ic) ->
                val on = W.tab == k
                Column(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (on) Color(0x242DD4BF) else Color.Transparent)
                        .clickable {
                            when (k) {
                                "shows" -> W.openTab(k, Page("libtab", "tvshows"))
                                "movies" -> W.openTab(k, Page("libtab", "movies"))
                                "books" -> W.openTab(k, Page("books"))
                                "you" -> W.openTab(k, Page("settings"))
                                else -> W.openTab(k)
                            }
                        }
                        .semantics { contentDescription = l + if (on) ", selected" else ""; role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(ic, null, tint = if (on) EGreen else ESub, modifier = Modifier.size(24.dp))
                    Text(l, color = if (on) EGreen else ESub, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** What's playing, above the tabs (as on the web page): the picture, its name and where it's at, and Stop, Back 10,
 *  Play/Pause, Forward 30 and Next. Tap the picture or the name for the player. */
@Composable
private fun MiniPlayer() {
    val st = Engine.status()
    if (!st.playing || W.player) return
    if (W.stack.lastOrNull()?.kind == "nowplaying") return
    val v = Engine.vd; val b = Engine.bk
    val pic = when {
        b != null -> "/api/play/books/cover/${b.id}"
        v != null -> "/api/play/img/${v.item}/Primary"
        Engine.loc != null -> "/api/play/img/${Engine.loc!!.item}/Primary"
        else -> ""
    }
    val title = b?.title ?: v?.title ?: Engine.loc?.title ?: st.title.ifEmpty { st.text }
    val (pos, len) = Engine.posDur()
    val where = (if (st.paused) "Paused" else "Playing") + when {
        st.tv -> " on " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "the TV")
        Engine.tg != null -> " together"
        len > 0 -> " · ${clock(pos)} / ${clock(len)}"
        else -> ""
    }
    val open = { if (v != null || b != null || Engine.loc != null) W.player = true else W.go(Page("nowplaying")) }
    val local = Engine.mode != Engine.Mode.NONE && Engine.tg == null
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xF012151C)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = open).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(start = 10.dp, end = 14.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(EBtn2)) {
                if (pic.isNotEmpty()) AsyncImage(picUrl(pic, 160), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(where, color = ESub, fontSize = 13.sp, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MiniBtn(Ic.Stop, if (Engine.tg != null) "Stop for everyone" else "Stop") { Engine.stopPressed() }
            MiniBtn(Ic.Replay, if (local) "Back 10 seconds" else "Back 30 seconds") { Engine.cmd(if (local) "back10" else "back30") }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable { Engine.cmd("playpause") }
                    .semantics { contentDescription = if (st.paused) "Play" else "Pause"; role = Role.Button },
                contentAlignment = Alignment.Center
            ) { Icon(if (st.paused) Ic.Play else Ic.Pause, null, tint = EInk, modifier = Modifier.size(30.dp)) }
            MiniBtn(Ic.Forward, "Forward 30 seconds") { Engine.cmd("fwd30") }
            if (st.hasNext && !st.book) MiniBtn(Ic.Next, "Next episode") { Engine.cmd("next") }
            else if (st.book && st.hasNext) MiniBtn(Ic.Next, "Next chapter") { Engine.cmd("next") }
            val tid = trkItem()
            if (tid.isNotEmpty()) MiniBtn(Ic.Subtitles, "Audio and subtitles") { W.trk = tid }
        }
        if (len > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((pos / len).toFloat().coerceIn(0f, 1f)).background(EGreen))
        }
    } }
}

/** The video whose sound track and subtitles can be changed now: on this phone, or on the TV. */
fun trkItem(): String = when {
    Engine.tg != null -> ""
    Engine.mode == Engine.Mode.VIDEO -> Engine.vd?.item ?: ""
    Engine.mode == Engine.Mode.NONE && !Engine.isPhone -> Engine.tvItem()
    else -> ""
}

/** The Audio and subtitles box, as on the web page: comes up from the bottom; what's picked is used when it closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(id: String, close: () -> Unit) {
    var t by remember(id) { mutableStateOf<JSONObject?>(null) }
    var err by remember(id) { mutableStateOf("") }
    var a by remember(id) { mutableStateOf<Int?>(null) }
    var sv by remember(id) { mutableIntStateOf(-1) }
    LaunchedEffect(id) {
        try {
            val j = Engine.tracksOf(id)
            if (j.err().isNotEmpty()) { err = j.err(); return@LaunchedEffect }
            val v = Engine.vd
            val (ca, cs) = if (v != null && v.item == id) v.aidx to v.sidx else Engine.trkGet(id)
            val dflt = if (j.has("ad_index") && !j.isNull("ad_index")) j.optInt("ad_index") else j.optInt("main_index")
            val audio = j.optJSONArray("audio").objs(); val subs = j.optJSONArray("subs").objs()
            a = if (ca != null && audio.any { it.optInt("index") == ca }) ca else dflt
            sv = if (cs != null && subs.any { it.optInt("index") == cs }) cs else -1
            t = j
        } catch (e: Exception) { err = e.message ?: "Couldn't reach the PC." }
    }
    val done = { if (t != null) Engine.trkUse(id, a, sv); close() }
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = done, sheetState = st, containerColor = ECard) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item { Text("Audio and subtitles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() }) }
            val j = t
            if (j == null) { item { Text(err.ifEmpty { "Loading…" }, color = ESub, modifier = Modifier.padding(vertical = 12.dp)) }; return@LazyColumn }
            item { Text("Audio", color = ESub, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }) }
            val audio = j.optJSONArray("audio").objs()
            if (audio.isEmpty()) item { Text("No sound tracks found", color = ESub) }
            items(audio) { x -> TrkRadio(x.s("label"), a == x.optInt("index")) { a = x.optInt("index") } }
            item { Text("Subtitles", color = ESub, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }) }
            val subs = j.optJSONArray("subs").objs()
            if (subs.isEmpty()) item { Text("None in this file", color = ESub) }
            else {
                item { TrkRadio("Off", sv < 0) { sv = -1 } }
                items(subs) { x -> TrkRadio(x.s("label"), sv == x.optInt("index")) { sv = x.optInt("index") } }
            }
            item { Box(Modifier.padding(top = 18.dp)) { WebBtn("Done", main = true, modifier = Modifier.fillMaxWidth()) { done() } } }
        }
    }
}

@Composable
private fun TrkRadio(label: String, on: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Color(0x242DD4BF) else Color.Transparent)
            .clickable(onClick = onPick).semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) EGreen else ESub, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(11.dp).clip(CircleShape).background(EGreen))
        }
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun MiniBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

// ------------------------------------------------------------------------------------------- the drawer
@Composable
private fun Drawer(close: () -> Unit) {
    val home = W.cache["/api/play/home"]
    val libs = home?.optJSONArray("libraries").objs()
    ModalDrawerSheet(drawerContainerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.statusBarsPadding().padding(16.dp)) {
            Text(Engine.me?.s("name") ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(if (Api.atHome) "At home" else "Away from home", color = ESub, fontSize = 13.sp)
        }
        HorizontalDivider(color = Color(0xFF333333))
        @Composable fun entry(label: String, icon: ImageVector, go: () -> Unit) {
            NavigationDrawerItem(
                label = { Text(label) }, selected = false, icon = { Icon(icon, null) },
                onClick = { close(); go() },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, unselectedTextColor = Color.White, unselectedIconColor = Color.White),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            entry("Home", Ic.Home) { W.openTab("home") }
            libs.forEach { l ->
                if (l.s("type") == "books") entry("Audiobooks", Ic.Book) { W.openTab("books", Page("books")) }   // the PC lists it with the libraries
                else entry(l.s("name"), if (l.s("type") == "tvshows") Ic.Tv else Ic.Movie) { W.openTab("lib", Page("library", l.s("id"), l.s("name"))) }
            }
            if (home == null) entry("Audiobooks", Ic.Book) { W.openTab("books", Page("books")) }        // not loaded yet
            entry("Favorites", Ic.HeartFull) { W.openTab("home", Page("favorites")) }
            entry("Downloads", Ic.Download) { W.openTab("downloads") }
            entry("Without audio description", Ic.Ear) { W.openTab("lib", Page("list", "noad", "Without audio description")) }
            entry("Requests for audio description", Ic.Ear) { W.openTab("lib", Page("list", "requests", "Requests for audio description")) }
            entry("Settings", Ic.Settings) { W.go(Page("settings")) }
            entry("Sign out", Ic.Logout) { Engine.signOut() }
        }
    }
}

// ------------------------------------------------------------------------------------------- shared pieces
@Composable
fun RowTitle(text: String, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (onMore != null) Icon(Ic.Chevron, null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/** A picture with Emby's marks: watched tick, episodes still to watch, how far in, no AD. */
@Composable
fun Poster(item: Item?, src: String, w: Int, modifier: Modifier, fallback: String = "", round: Dp = 12.dp, icon: ImageVector? = null) {
    Box(modifier.clip(RoundedCornerShape(round)).background(ECard)) {
        if (src.isEmpty() || icon != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.4f))
            else Text(fallback, color = ESub, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp), fontSize = 14.sp)
        }
        if (src.isNotEmpty()) AsyncImage(picUrl(src, w), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (item != null) {
            val played = W.isPlayed(item)
            when {
                played -> Badge(Modifier.align(Alignment.BottomEnd)) { Icon(Ic.Check, null, tint = EInk, modifier = Modifier.size(16.dp)) }
                item.unplayed > 0 -> Badge(Modifier.align(Alignment.TopEnd)) { Text("${item.unplayed}", color = EInk, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            if (item.hasAd == true && item.type != "Book") AdMark(Modifier.align(Alignment.TopStart).padding(8.dp))
            if (item.hasAd == false) Text(
                "No AD", color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xC0000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
            if (item.pct in 1..99 && !played) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.pct / 100f).background(EGreen))
            }
        }
    }
}

@Composable
private fun Badge(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.padding(6.dp).heightIn(min = 26.dp).widthIn(min = 26.dp).clip(CircleShape).background(EGreen).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(item: Item, width: Dp, wide: Boolean = false, square: Boolean = false, sub: String? = null,
               onLong: (() -> Unit)? = null, onTap: () -> Unit = { W.open(item) }) {
    val ep = item.type == "Episode"
    val title = if (ep) item.seriesName.ifEmpty { item.name } else item.name.ifEmpty { item.label }
    val subtitle = sub ?: when {
        ep -> item.epTitle()
        item.type == "Series" -> item.years
        item.type == "Book" -> item.author
        else -> item.year
    }
    Column(
        Modifier.width(width).combinedClickable(onClick = onTap, onLongClick = onLong)
            .clearAndSetSemantics { contentDescription = item.spoken(); role = Role.Button; onClick { onTap(); true } },
        horizontalAlignment = Alignment.Start
    ) {
        val src = when {
            wide -> item.thumb.ifEmpty { item.backdrop.ifEmpty { item.poster } }
            else -> item.poster.ifEmpty { item.thumb }
        }
        Poster(item, src, if (wide) 800 else 320, Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else if (square) 1f else 2f / 3f), title)
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (subtitle.isNotEmpty()) Text(subtitle, color = ESub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth())
    }
}

fun LazyListScope.posterRow(title: String, list: List<Item>, width: Dp, wide: Boolean = false, square: Boolean = false,
                            onMore: (() -> Unit)? = null, click: ((Item) -> Unit)? = null) {
    if (list.isEmpty()) return
    item { RowTitle(title, onMore) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { it ->
                PosterCard(it, width, wide, square,
                    onLong = if (it.type == "Episode" && it.seriesId.isNotEmpty()) ({ W.go(Page("item", it.seriesId, it.seriesName, "Series")) }) else null,
                    onTap = { if (click != null) click(it) else W.open(it) })
            }
        }
    }
}

@Composable
private fun Loading(err: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        if (err.isNotEmpty()) Text(err, color = ESub, textAlign = TextAlign.Center) else CircularProgressIndicator(color = EGreen)
    }
}

@Composable
private fun OfflineNote() {
    if (!Engine.offline) return
    Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A1F1F)).padding(14.dp)) {
        Text("Can't reach the PC - what's downloaded still plays.", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text("Try again", color = EGreen, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { Engine.boot() })
            Text("Downloads", color = EGreen, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { W.openTab("downloads") })
        }
    }
}

/** "Version 1.9.0 is ready": what's new, and Install. */
@Composable
fun UpdateCard() {
    if (Update.available.isEmpty()) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2A1E)).padding(14.dp)) {
        Text("Version ${Update.available} is ready", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("You have ${Update.mine}.", color = ESub, fontSize = 14.sp)
        if (more) Text(Update.notes, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (Update.message.isNotEmpty()) Text(Update.message, color = Color(0xFFFFB74D), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Update.progress >= 0) {
                // Install greyed out while it downloads
                Text("Install", color = Color(0xFF8A8A8A), fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3A3A3A)).padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { role = Role.Button; disabled() })
                Text("Downloading, ${Update.progress / 10 * 10} percent…", color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                WhitePill("Install", null, Modifier) { Engine.scope.launch { Update.install(ctx) } }
                WhitePill(if (more) "Less" else "What's new", null, Modifier, dark = true) { more = !more }
            }
        }
        if (Update.progress >= 0) LinearProgressIndicator(progress = { Update.progress / 100f }, color = EGreen, trackColor = ECard,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
private fun InviteCards() {
    if (Engine.invites.isEmpty() || Engine.tg != null) return
    Engine.invites.forEach { r ->
        val w = (if (r.s("kind") == "book") "listen to " else "watch ") + r.s("title")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E3A1D)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Ic.Group, null, tint = EGreen)
            Text("${r.s("by")} wants to $w together.", color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            WhitePill("Join", Ic.Play, Modifier.width(100.dp).height(42.dp)) { Engine.tgJoin(r.s("id")) }
        }
    }
}

@Composable
fun WhitePill(text: String, icon: ImageVector?, modifier: Modifier, dark: Boolean = false, size: Int = 20, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (dark) EBtn2 else Color.White)
            .then(if (dark) Modifier.border(1.dp, ELine, RoundedCornerShape(14.dp)) else Modifier).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, tint = if (dark) Color.White else EInk, modifier = Modifier.size(26.dp))
        Text(text, color = if (dark) Color.White else EInk, fontSize = size.sp, fontWeight = if (dark) FontWeight.SemiBold else FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, tint: Color = Color.White, onTap: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTap).padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = label; role = Role.Button; onClick { onTap(); true } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
        Text(label, color = ESub, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Pull down to bring a page up to date (asks the PC again). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(modifier: Modifier, belowTopBar: Boolean, content: @Composable BoxScope.() -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; W.refresh++; scope.launch { delay(1200); refreshing = false } },
        modifier = modifier, state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).then(if (belowTopBar) Modifier.statusBarsPadding().padding(top = 56.dp) else Modifier)
            )
        },
        content = content
    )
}

// ------------------------------------------------------------------------------------------- home (as on the web page)
/** The "AD" mark on a poster that has audio description. */
@Composable
fun AdMark(modifier: Modifier = Modifier) {
    Text(
        "AD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xC70B0D11))
            .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics { contentDescription = "Audio description" }
    )
}

/** A row's name, with "See all" at the right. */
@Composable
private fun WebRowTitle(text: String, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (onAll != null) Text("See all", color = ESub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAll).padding(horizontal = 8.dp, vertical = 8.dp)
                .semantics { contentDescription = "See all: $text"; role = Role.Button })
    }
}

private data class HomeRow(val kind: String, val title: String, val shape: String, val query: String = "")
private val HOME_ROWS = listOf(
    HomeRow("continue", "Continue watching", "land"), HomeRow("recent", "New with audio description", "land"),
    HomeRow("shows", "New TV shows", "post", "&ad=all&sort=added&desc=1"), HomeRow("movies", "New movies", "post", "&ad=all&sort=added&desc=1"),
    HomeRow("books_continue", "Audiobooks in progress", "sq")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebHome(p: Page) {
    val rows = HOME_ROWS.map { r ->
        val path = if (Engine.offline) null else if (r.kind.startsWith("books_")) "/api/play/books/list?kind=${r.kind}" else "/api/play/list?kind=${r.kind}${r.query}"
        r to rememberData(path)
    }
    // the big pictures at the top: up to 3 in progress, then what's new (as on the web page)
    val hero = remember(rows.map { it.second.first }) {
        val pick = mutableListOf<Pair<Item, String>>()
        listOf("continue" to 3, "recent" to 5, "movies" to 5, "shows" to 5).forEach { (k, n) ->
            var c = 0
            rows.firstOrNull { it.first.kind == k }?.second?.first?.itemList()?.forEach { it ->
                val art = it.j.s("series_backdrop").ifEmpty { it.backdrop.ifEmpty { it.thumb } }
                if (pick.size < 5 && c < n && art.isNotEmpty() && pick.none { q -> q.first.id == it.id }) { pick.add(it to k); c++ }
            }
        }
        pick.toList()
    }
    val loading = rows.all { it.second.first == null }
    Box(Modifier.fillMaxSize()) {
        Refreshable(Modifier.fillMaxSize(), belowTopBar = true) {
            LazyColumn(Modifier.fillMaxSize(), state = W.list(p), contentPadding = PaddingValues(bottom = 190.dp)) {
                if (hero.isNotEmpty()) item { HeroPager(hero) } else item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).height(84.dp)) }
                item { OfflineNote() }
                item { InviteCards() }
                item { UpdateCard() }
                if (Engine.offline) return@LazyColumn
                if (loading) item { Loading(rows.firstOrNull { it.second.second.isNotEmpty() }?.second?.second ?: "") }
                rows.forEach { (r, d) ->
                    val list = d.first?.itemList()?.take(30) ?: emptyList()
                    if (list.isEmpty()) return@forEach
                    item(key = "h_" + r.kind) {
                        WebRowTitle(r.title) {
                            if (r.kind.startsWith("books_")) W.openTab("books", Page("books")) else W.go(Page("list", r.kind, r.title, r.query))
                        }
                    }
                    item(key = "r_" + r.kind) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list) { it ->
                                when (r.shape) {
                                    "land" -> PosterCard(it, 250.dp, wide = true,
                                        onLong = if (it.type == "Episode" && it.seriesId.isNotEmpty()) ({ W.go(Page("item", it.seriesId, it.seriesName, "Series")) }) else null)
                                    "sq" -> PosterCard(it, 150.dp, square = true)
                                    else -> PosterCard(it, 124.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        val ls = W.list(p)
        val scrolled by remember { derivedStateOf { ls.firstVisibleItemIndex > 0 || ls.firstVisibleItemScrollOffset > 120 } }
        TopBar(null, {}, solid = scrolled, heading = false)
    }
}

/** The big picture at the top of Home: what's in progress and what's new, one at a time (swipe or the dashes). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(list: List<Pair<Item, String>>) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { list.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth()) { i -> HeroSlide(list[i].first, list[i].second) }
        if (list.size > 1) Row(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            list.forEachIndexed { j, (it, _) ->
                val on = pager.currentPage == j
                val t = if (it.type == "Episode") it.seriesName.ifEmpty { it.label } else it.name
                Box(
                    Modifier.size(width = 34.dp, height = 28.dp).clickable { scope.launch { pager.animateScrollToPage(j) } }
                        .semantics { contentDescription = "Featured ${j + 1} of ${list.size}: $t" + if (on) ", showing" else ""; role = Role.Button },
                    contentAlignment = Alignment.CenterStart
                ) { Box(Modifier.size(width = if (on) 28.dp else 16.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Color.White else Color(0x66FFFFFF))) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroSlide(it: Item, k: String) {
    val ep = it.type == "Episode"
    val series = it.type == "Series"
    val art = it.j.s("series_backdrop").ifEmpty { it.backdrop.ifEmpty { it.thumb } }
    val title = if (ep) it.seriesName.ifEmpty { it.name } else it.name
    Box(Modifier.fillMaxWidth().height(560.dp).background(Color.Black)) {
        if (art.isNotEmpty()) AsyncImage(picUrl(art, if ("/Backdrop" in art) 1920 else 1280), null, Img.loader, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x1A0B0D11), 0.58f to Color(0xB80B0D11), 1f to EBg)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (if (k == "continue") "Continue watching" else if (k == "recent") "New with audio description" else "With audio description").uppercase(),
                color = EGreen, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp
            )
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val bits = (if (ep) listOf(it.epTitle(), it.rt.ifEmpty { it.length }) else listOf(if (series) it.years else it.year, it.rt.ifEmpty { it.length }, it.rating))
                    .filter { b -> b.isNotEmpty() }
                Text(bits.joinToString("  ·  "), color = Color(0xFFD6DBE3), fontSize = 15.sp)
                if (it.hasAd != false) Text("AD", color = Color(0xFFD6DBE3), fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.border(1.5.dp, Color(0xFFD6DBE3), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
                        .semantics { contentDescription = "Audio description" })
                if (it.resume.isNotEmpty()) Text("Resumes at ${it.resume}", color = Color(0xFFD6DBE3), fontSize = 15.sp)
            }
            if (it.overview.isNotEmpty()) Text(it.overview, color = Color(0xFFD6DBE3), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val play = if (series) "Episodes" else if (it.resume.isNotEmpty()) "Resume" else "Play"
                Row(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .clickable { if (series) W.open(it) else Engine.start(it, it.resume.isNotEmpty()) }
                        .semantics(mergeDescendants = true) { contentDescription = (if (series) "Open " else "$play ") + it.label.ifEmpty { title }; role = Role.Button },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.Play, null, tint = EInk, modifier = Modifier.size(22.dp))
                    Text(play, color = EInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                }
                if (!series) Row(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF))
                        .clickable { if (!ep && it.seriesId.isNotEmpty()) W.go(Page("item", it.seriesId, it.seriesName, "Series")) else W.open(it) }
                        .semantics(mergeDescendants = true) { contentDescription = "More about $title"; role = Role.Button },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.Info, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("More info", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- favorites
@Composable
private fun HomePage(p: Page, favorites: Boolean, openDrawer: () -> Unit) {
    val (home, err) = rememberData(if (favorites || Engine.offline) null else "/api/play/home")
    val (fav, ferr) = rememberData(if (favorites) "/api/play/list?kind=favorites" else null)
    val (books, _) = rememberData(if (favorites || Engine.offline) null else "/api/play/books/list?kind=books_continue")
    Box(Modifier.fillMaxSize()) {
        Refreshable(Modifier.fillMaxSize(), belowTopBar = true) {
        LazyColumn(Modifier.fillMaxSize(), state = W.list(p), contentPadding = PaddingValues(bottom = 170.dp)) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(Modifier.height(76.dp)) }
            if (favorites) item { Text("Favorites", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() }) }
            item { OfflineNote() }
            item { InviteCards() }
            item { UpdateCard() }
            if (favorites) {
                val list = fav?.itemList()
                if (list == null) item { Loading(ferr) }
                else if (list.isEmpty()) item { Text("No favorites yet - tap the heart on a show or movie.", color = ESub, modifier = Modifier.padding(16.dp)) }
                else {
                    posterRow("Shows", list.filter { it.type == "Series" }, 130.dp)
                    posterRow("Movies", list.filter { it.type == "Movie" }, 130.dp)
                    posterRow("Episodes", list.filter { it.type == "Episode" }, 260.dp, wide = true)
                }
                return@LazyColumn
            }
            if (Engine.offline) return@LazyColumn
            if (home == null) { item { Loading(err) }; return@LazyColumn }
            val libs = home.optJSONArray("libraries").objs()
            if (libs.isNotEmpty()) {
                item { RowTitle("My Media") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(libs) { l ->
                            Column(
                                Modifier.width(170.dp).clickable {
                                    if (l.s("type") == "books") W.openTab("books", Page("books")) else W.openTab("lib", Page("library", l.s("id"), l.s("name")))
                                }
                                    .clearAndSetSemantics { contentDescription = l.s("name"); role = Role.Button },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Poster(null, l.s("image"), 480, Modifier.fillMaxWidth().aspectRatio(16f / 9f), l.s("name"))
                                Text(l.s("name"), color = Color.White, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            posterRow("Continue Watching", home.itemList("resume"), 300.dp, wide = true)
            posterRow("Next Up", home.itemList("next_up"), 300.dp, wide = true)
            val bl = books?.itemList() ?: emptyList()
            posterRow("Continue Listening", bl, 170.dp, square = true, onMore = { W.openTab("books", Page("books")) })
            home.optJSONArray("latest").objs().forEach { r ->
                posterRow("Latest " + r.s("name"), r.itemList(), 130.dp, onMore = { W.openTab("lib", Page("library", r.s("id"), r.s("name"))) })
            }
            posterRow("Latest Audiobooks", home.itemList("books_latest"), 150.dp, square = true, onMore = { W.openTab("books", Page("books")) })
        }
        }
        TopBar(null, openDrawer)
    }
}

// ------------------------------------------------------------------------------------------- a grid of posters
@Composable
private fun GridPage(p: Page, openDrawer: () -> Unit) {
    var view by remember(p.id) { mutableStateOf(LibView.load(p.id)) }
    val path = when (p.kind) {
        "library" -> "/api/play/list?kind=library&parent=${enc(p.id)}" + view.query()
        "children" -> "/api/play/list?kind=children&parent=${enc(p.id)}"
        else -> if (p.id == "noad") "/api/play/list?kind=noad" else "/api/play/list?kind=${enc(p.id)}" + p.extra
    }
    val (d, err) = rememberData(path)
    val list = d?.itemList()
    Column(Modifier.fillMaxSize()) {
        TopBar(null, openDrawer, heading = false)
        // the name and how many (as on the web page); a library: the round Sort and filter button, and the chips (kept for next time)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.title + (if (list != null) " (${list.size})" else ""), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 33.sp, modifier = Modifier.weight(1f).semantics { heading() })
            if (p.kind == "library") SortFilterButton(p.id, view) { v -> view = v; LibView.save(p.id, v) }
        }
        if (p.kind == "library") FilterChips(view) { v -> view = v; LibView.save(p.id, v) }
        Refreshable(Modifier.fillMaxSize(), belowTopBar = false) { when {
            list == null -> Loading(err)
            list.isEmpty() && p.kind == "library" && view.filtersOn > 0 ->
                Text("Nothing matches these filters.", color = ESub, modifier = Modifier.padding(16.dp))
            list.isEmpty() -> Text("Nothing here.", color = ESub, modifier = Modifier.padding(16.dp))
            p.id == "requests" -> LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 170.dp)) {
                items(list) { r ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(r.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${r.text}. Asked for on ${r.date}.", color = ESub, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }
            }
            else -> {
                val wide = list.all { it.type == "Episode" || it.type == "Video" && it.poster.isEmpty() }
                LazyVerticalGrid(
                    GridCells.Adaptive(if (wide) 170.dp else 108.dp), state = W.grid(p),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 190.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(list) { PosterCard(it, Dp.Unspecified, wide) }
                }
            }
        } }
    }
}

@Composable
private fun LibTab(type: String, openDrawer: () -> Unit) {
    val (home, err) = rememberData("/api/play/home")
    val lib = home?.optJSONArray("libraries").objs().firstOrNull { it.s("type") == type }
    val name = if (type == "tvshows") "Shows" else "Movies"
    LaunchedEffect(lib?.s("id")) { if (lib != null) W.stack[W.stack.lastIndex] = Page("library", lib.s("id"), name) }
    Column {
        TopBar(name, openDrawer)
        if (home != null && lib == null) Text("There's no ${name.lowercase()} library.", color = ESub, modifier = Modifier.padding(16.dp)) else Loading(err)
    }
}

// ------------------------------------------------------------------------------------------- a show or a movie
@Composable
private fun Hero(info: Item?, listState: LazyListState?, content: @Composable () -> Unit = {}) {
    val src = (if (info?.type == "Episode") info.thumb.ifEmpty { info.backdrop } else info?.backdrop?.ifEmpty { info.thumb }) ?: ""
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    Box(Modifier.fillMaxWidth().aspectRatio(if (conf.screenWidthDp > conf.screenHeightDp) 3f else 0.98f)) {
        if (src.isNotEmpty()) AsyncImage(picUrl(src, 1280), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to EBg)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) { content() }
    }
}

@Composable
private fun Logo(info: Item, name: String) {
    var failed by remember(info.logo) { mutableStateOf(info.logo.isEmpty()) }
    if (!failed) AsyncImage(
        picUrl(info.logo, 600), name, Img.loader,
        Modifier.heightIn(max = 110.dp).widthIn(max = 280.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        onError = { failed = true }
    ) else Text(name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(info: Item) {
    val j = info.j
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val style = Modifier.align(Alignment.CenterVertically)
        if (info.score.isNotEmpty()) Row(style, verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Star, null, tint = ERed, modifier = Modifier.size(22.dp))
            Text(info.score, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(start = 3.dp))
        }
        @Composable fun t(s: String) { if (s.isNotEmpty()) Text(s, color = Color.White, fontSize = 17.sp, modifier = style) }
        t(if (info.type == "Series") info.years else info.year)
        if (info.type == "Movie") t(info.rt)
        if (info.type == "Series") {
            t(j.s("studio"))
            val n = j.i("season_count"); if (n > 0) t("$n Season" + if (n == 1) "" else "s")
        }
        if (info.rating.isNotEmpty()) Text(info.rating, color = Color.White, fontSize = 15.sp,
            modifier = style.border(1.dp, Color.White, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        t(info.genres.split(", ").take(2).joinToString(", "))
        if (info.hasAd == false) Text("No audio description", color = Color(0xFFFFB74D), fontSize = 15.sp, modifier = style)
    }
}

@Composable
private fun Overview(text: String) {
    if (text.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = Color.White, fontSize = 17.sp, lineHeight = 24.sp, maxLines = if (open) 60 else 3, overflow = TextOverflow.Clip,
            onTextLayout = { if (!open) over = it.hasVisualOverflow })
        if (!open && over) Text("More", color = ESub, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).background(Brush.horizontalGradient(0f to Color.Transparent, 0.45f to EBg)).padding(start = 56.dp))
    }
}

/** The menu behind "More": start over, ask for AD, play on. */
@Composable
private fun MoreMenu(item: Item, open: Boolean, onClose: () -> Unit, extra: List<Pair<String, () -> Unit>> = emptyList()) {
    var castOpen by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf(false) }
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        extra.forEach { (l, f) -> DropdownMenuItem(text = { Text(l) }, onClick = { onClose(); f() }) }
        if (item.resume.isNotEmpty()) DropdownMenuItem(text = { Text("Start from the beginning") }, leadingIcon = { Icon(Ic.Restart, null) },
            onClick = { onClose(); Engine.start(item, false) })
        if (item.type == "Episode" || item.type == "Movie") DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Ic.Download, null) },
            onClick = { onClose(); dl = true })
        if (item.hasAd == false) DropdownMenuItem(
            text = { Text(if (item.requested) "Asked for audio description" else "Ask for audio description") }, leadingIcon = { Icon(Ic.Ear, null) },
            onClick = { onClose(); Engine.askForAd(item) })
        DropdownMenuItem(text = { Text("Play on…") }, leadingIcon = { Icon(Ic.Cast, null) }, onClick = { onClose(); castOpen = true })
    }
    if (castOpen) PlayOnDialog { castOpen = false }
    if (dl) DownloadChoices(item) { dl = false }
}

/** A button as on the web page: white for the main one, dark with a line round it for the rest. */
@Composable
fun WebBtn(text: String, main: Boolean = false, label: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = if (main) 52.dp else 46.dp).clip(RoundedCornerShape(12.dp)).background(if (main) Color.White else EBtn2)
            .then(if (main) Modifier else Modifier.border(1.dp, ELine, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick).semantics { role = Role.Button; if (label != null) contentDescription = label }
            .padding(horizontal = if (main) 26.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (main) EInk else Color.White, fontSize = if (main) 17.sp else 16.sp, fontWeight = if (main) FontWeight.ExtraBold else FontWeight.SemiBold) }
}

/** A show's, movie's or episode's page, as on the web page: the name, the picture, the buttons, what it's about, the
 *  details and the cast; a show has its seasons as buttons with that season's episodes under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailPage(p: Page, openDrawer: () -> Unit) {
    val (d, err) = rememberData("/api/play/info?id=${enc(p.id)}")
    val info = d?.let { Item(it) }
    val ls = W.list(p)
    var dl by remember { mutableStateOf(false) }
    var castOpen by remember { mutableStateOf(false) }
    val seasons = info?.j?.optJSONArray("seasons").objs().map { Item(it) }
    var seasonI by remember(p.id) { mutableIntStateOf(-1) }
    LaunchedEffect(seasons?.size) {
        if (seasons != null && seasons.isNotEmpty() && seasonI < 0) {
            val want = Prefs.str("season_${p.id}")
            seasonI = seasons.indexOfFirst { it.id == want }.takeIf { it >= 0 } ?: seasons.indexOfFirst { !W.isPlayed(it) }.coerceAtLeast(0)
        }
    }
    val season = seasons?.getOrNull(seasonI)
    val (eps, eerr) = rememberData(season?.let { "/api/play/list?kind=episodes&parent=${enc(it.id)}" })
    Column(Modifier.fillMaxSize()) {
        TopBar(null, openDrawer, heading = false)
        LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(bottom = 190.dp)) {
            val ep = p.extra == "Episode" || info?.type == "Episode"
            val head = if (info == null) p.title else if (ep && info.seriesName.isNotEmpty()) "${info.seriesName}, ${info.epTitle()}" else info.name
            item {
                Text(head, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp).semantics { heading() })
            }
            if (info == null) { item { Loading(err) }; return@LazyColumn }
            val j = info.j
            val playable = ep || info.type == "Movie"
            item {
                val bd = info.backdrop.ifEmpty { j.s("series_backdrop") }
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(ECard)) {
                        if (bd.isNotEmpty()) AsyncImage(picUrl(bd, 1920), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color(0x000B0D11), 1f to EBg)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 80.dp), verticalAlignment = Alignment.Bottom) {
                        val pic = if (ep) info.thumb.ifEmpty { info.poster } else info.poster.ifEmpty { info.thumb }
                        Poster(info, pic, if (ep) 600 else 360, Modifier.width(if (ep) 180.dp else 120.dp).aspectRatio(if (ep) 16f / 9f else 2f / 3f), info.name)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            if (ep && info.seriesName.isNotEmpty()) Text(
                                info.seriesName + (if (info.se >= 0 && info.ep >= 0) " · Season ${info.se}, episode ${info.ep}" else ""),
                                color = ESub, fontSize = 15.sp, lineHeight = 20.sp)
                            Text(info.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                            val meta = listOf(if (info.type == "Series") info.years else info.year,
                                if (info.type == "Series") j.i("season_count").let { n -> if (n > 0) "$n season" + (if (n == 1) "" else "s") else "" } else info.length,
                                info.rating, if (info.score.isNotEmpty()) "★ ${info.score}" else "").filter { it.isNotEmpty() }.joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, color = ESub, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                            if (info.resume.isNotEmpty()) Text((if (info.pct > 0) "${info.pct}% watched · " else "") + "Resumes at ${info.resume}",
                                color = ESub, fontSize = 15.sp, lineHeight = 20.sp)
                            if (info.hasAd == false) Text("Without description", color = Color(0xFFFFB74D), fontSize = 15.sp)
                        }
                    }
                }
            }
            item {
                FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (playable) {
                        WebBtn(if (info.resume.isNotEmpty()) "Resume from ${info.resume}" else "Play", main = true,
                            label = (if (info.resume.isNotEmpty()) "Resume " else "Play ") + info.label) { Engine.start(info, info.resume.isNotEmpty()) }
                        WebBtn("Play together", label = "Play ${info.label} together") { Engine.start(info, info.resume.isNotEmpty(), together = true) }
                        if (info.resume.isNotEmpty()) WebBtn("Start over", label = "Start ${info.label} from the beginning") { Engine.start(info, false) }
                    } else {
                        val next = j.optJSONObject("next_up")?.let { Item(it) }
                        if (next != null) WebBtn(
                            (if (next.resume.isNotEmpty()) "Resume " else "Play ") + if (next.se >= 0 && next.ep >= 0) "S${next.se}:E${next.ep}" else next.name,
                            main = true, label = (if (next.resume.isNotEmpty()) "Resume " else "Play ") + next.label) { Engine.start(next, next.resume.isNotEmpty()) }
                    }
                    if (ep && info.seriesId.isNotEmpty()) WebBtn("Go to ${info.seriesName.ifEmpty { "the show" }}") {
                        W.go(Page("item", info.seriesId, info.seriesName, "Series"))
                    }
                    if (!ep) WebBtn(if (info.favorite) "Remove from favorites" else "Add to favorites",
                        label = (if (info.favorite) "Remove ${info.label} from favorites" else "Add ${info.label} to favorites")) { Engine.toggleFavorite(info) }
                    if (playable) {
                        val saved = Offline.forItem(info.id)
                        WebBtn(if (saved.any { it.done }) "Downloaded" else if (saved.isNotEmpty()) "Downloading…" else "Download") {
                            if (saved.isNotEmpty()) W.go(Page("downloads")) else dl = true
                        }
                    }
                    val pl = W.isPlayed(info)
                    WebBtn(if (pl) "Mark as not watched" else "Mark as watched") { W.markPlayed(info, !pl) }
                    if (info.hasAd == false) WebBtn(if (info.requested) "Asked for audio description" else "Ask for audio description") { Engine.askForAd(info) }
                    WebBtn("Play on…") { castOpen = true }
                }
            }
            if (j.s("tagline").isNotEmpty()) item {
                Text(j.s("tagline"), color = ESub, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            }
            if (info.overview.isNotEmpty()) item {
                Text(info.overview, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
            }
            // a show: its seasons as buttons, that season's episodes under them
            if (!seasons.isNullOrEmpty()) {
                item {
                    LazyRow(Modifier.padding(top = 18.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(seasons) { i, se ->
                            Chip(se.name, i == seasonI) { seasonI = i; Prefs.put("season_${p.id}", se.id) }
                        }
                    }
                }
                val list = eps?.itemList()
                when {
                    list == null -> item { Loading(eerr) }
                    list.isEmpty() -> item { Text("No episodes.", color = ESub, modifier = Modifier.padding(16.dp)) }
                    else -> items(list, key = { "ep_" + it.id }) { EpRow(it) }
                }
            }
            // the details, as on the web page
            val facts = listOf("Genres" to info.genres, "Directed by" to j.s("directors"), "Written by" to j.s("writers"),
                "Length" to (if (info.type == "Series") "" else info.length), "Rated" to info.rating, "Studio" to j.s("studio"),
                "Picture" to j.s("video_info"), "Sound" to j.s("audio_info")).filter { it.second.isNotEmpty() }
            if (facts.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
                    Text("Details", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                    facts.forEach { (k, v) ->
                        Column(Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) {}) {
                            Text(k, color = ESub, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(v, color = Color.White, fontSize = 17.sp)
                        }
                    }
                }
            }
            val people = j.optJSONArray("people").objs()
            if (people.isNotEmpty()) {
                item { Text("Cast", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp).semantics { heading() }) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(people) { pp ->
                            Column(Modifier.width(96.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(84.dp).clip(CircleShape).background(EBtn2), contentAlignment = Alignment.Center) {
                                    if (pp.s("image").isNotEmpty()) AsyncImage(picUrl(pp.s("image"), 240), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else Icon(Ic.Person, null, tint = ESub, modifier = Modifier.size(40.dp))
                                }
                                Text(pp.s("name"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                    lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
                                Text(pp.s("role"), color = ESub, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    if (dl && info != null) DownloadChoices(info) { dl = false }
    if (castOpen) PlayOnDialog { castOpen = false }
}

/** A pressed / not pressed button in a row of choices (seasons, filters), as on the web page. */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(if (on) Color.White else EBtn2)
            .then(if (on) Modifier else Modifier.border(1.dp, ELine, RoundedCornerShape(21.dp)))
            .clickable(onClick = onClick).semantics { role = Role.Button; selected = on }.padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (on) EInk else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/** An episode in a show's list, as on the web page: its picture, name, length and what it's about; then Play, Play
 *  together and Start over. The picture and words open its page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpRow(it: Item) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { W.go(Page("item", it.id, it.name, "Episode")) }
                .semantics(mergeDescendants = true) { role = Role.Button },
            verticalAlignment = Alignment.Top
        ) {
            Poster(it, it.thumb, 480, Modifier.width(150.dp).aspectRatio(16f / 9f), it.name)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(it.epTitle(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                val m = listOf(it.length.ifEmpty { it.rt }, if (it.resume.isNotEmpty()) "resumes at ${it.resume}" else if (W.isPlayed(it)) "watched" else "",
                    if (it.hasAd == false) "without description" else "").filter { x -> x.isNotEmpty() }.joinToString(" · ")
                if (m.isNotEmpty()) Text(m, color = ESub, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                if (it.overview.isNotEmpty()) Text(it.overview, color = ESub, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WebBtn(if (it.resume.isNotEmpty()) "Resume" else "Play", label = (if (it.resume.isNotEmpty()) "Resume " else "Play ") + it.label) {
                Engine.start(it, it.resume.isNotEmpty())
            }
            WebBtn("Play together", label = "Play ${it.label} together") { Engine.start(it, it.resume.isNotEmpty(), together = true) }
            if (it.resume.isNotEmpty()) WebBtn("Start over", label = "Start ${it.label} from the beginning") { Engine.start(it, false) }
            if (it.hasAd == false) WebBtn(if (it.requested) "Asked for audio description" else "Ask for audio description") { Engine.askForAd(it) }
        }
    }
}

private fun buildLabel(a: String, b: String, grey: Boolean = false) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = if (grey) ESub else Color.White))
    append(if (grey) a else "$a  "); pop()
    pushStyle(androidx.compose.ui.text.SpanStyle(color = if (grey) Color.White else ESub, fontWeight = if (grey) FontWeight.SemiBold else FontWeight.Normal))
    append(b); pop()
}

// ------------------------------------------------------------------------------------------- a season
@Composable
private fun SeasonPage(p: Page, openDrawer: () -> Unit) {
    val (d, err) = rememberData("/api/play/info?id=${enc(p.id)}")
    val (eps, eerr) = rememberData("/api/play/list?kind=episodes&parent=${enc(p.id)}")
    val info = d?.let { Item(it) }
    val list = eps?.itemList()
    val ls = W.list(p)
    val scrolled by remember { derivedStateOf { ls.firstVisibleItemIndex > 0 || ls.firstVisibleItemScrollOffset > 400 } }
    var more by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                Hero(info, ls) {
                    if (info != null) Column {
                        Logo(info, info.seriesName.ifEmpty { info.name })
                        Text(info.name, color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
            if (info == null) { item { Loading(err) }; return@LazyColumn }
            item {
                val next = list?.firstOrNull { it.resume.isNotEmpty() } ?: list?.firstOrNull { !W.isPlayed(it) } ?: list?.firstOrNull()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhitePill(if (next?.resume?.isNotEmpty() == true) "Resume" else "Play", Ic.Play, Modifier.weight(1f).height(56.dp)) {
                        if (next != null) Engine.start(next, next.resume.isNotEmpty())
                    }
                    if (next != null) WhitePill("Play together", null, Modifier.weight(1f).height(56.dp), dark = true, size = 18) {
                        Engine.start(next, next.resume.isNotEmpty(), together = true)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    val pl = W.isPlayed(info)
                    Action("Played", Ic.Check, if (pl) EGreen else Color.White) { W.markPlayed(info, !pl) }
                    if (info.seriesId.isNotEmpty()) Action("Show", Ic.Tv) { W.go(Page("item", info.seriesId, info.seriesName, "Series")) }
                    Box {
                        Action("More", Ic.More) { more = true }
                        MoreMenu(info, more, { more = false })
                    }
                }
            }
            when {
                list == null -> item { Loading(eerr) }
                list.isEmpty() -> item { Text("No episodes.", color = ESub, modifier = Modifier.padding(16.dp)) }
                else -> items(list) { EpisodeCard(it) }
            }
        }
        TopBar(if (scrolled) info?.seriesName?.ifEmpty { p.title } ?: p.title else "", openDrawer, solid = scrolled, heading = false)
    }
}

@Composable
private fun EpisodeCard(item: Item) {
    var menu by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf(false) }
    val play = { Engine.start(item, item.resume.isNotEmpty()) }
    val page = { W.go(Page("item", item.id, item.name, "Episode")) }
    // the picture plays it; the words open its page (like Emby)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clickable(onClick = play)
                .clearAndSetSemantics { contentDescription = "Play " + item.spoken(); role = Role.Button; onClick { play(); true } }) {
                Poster(item, item.thumb, 480, Modifier.fillMaxSize(), round = 6.dp)
                Box(
                    Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color(0x55000000))
                        .border(BorderStroke(3.dp, Color.White), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.Play, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Column(Modifier.weight(1f).clickable(onClick = page).padding(start = 14.dp, top = 4.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = "About " + item.spoken(); role = Role.Button }) {
                Text((if (item.ep >= 0) "${item.ep}. " else "") + item.name, color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (item.aired.isNotEmpty()) Text(item.aired, color = ESub, fontSize = 15.sp)
                    if (item.rt.isNotEmpty()) Text(item.rt, color = ESub, fontSize = 15.sp)
                    if (Offline.done(item.id, "video") != null || Offline.done(item.id, "audio") != null)
                        Icon(Ic.Download, "Downloaded", tint = EGreen, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { Engine.start(item, item.resume.isNotEmpty(), together = true) }) {
                Icon(Ic.Group, "Play ${item.label} together", tint = Color.White)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Ic.More, "More for ${item.name}", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val pl = W.isPlayed(item)
                    if (item.resume.isNotEmpty()) DropdownMenuItem(text = { Text("Start from the beginning") }, leadingIcon = { Icon(Ic.Restart, null) },
                        onClick = { menu = false; Engine.start(item, false) })
                    DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Ic.Download, null) }, onClick = { menu = false; dl = true })
                    DropdownMenuItem(text = { Text(if (pl) "Mark not played" else "Mark played") }, leadingIcon = { Icon(Ic.Check, null) },
                        onClick = { menu = false; W.markPlayed(item, !pl) })
                    if (item.hasAd == false) DropdownMenuItem(text = { Text(if (item.requested) "Asked for audio description" else "Ask for audio description") },
                        leadingIcon = { Icon(Ic.Ear, null) }, onClick = { menu = false; Engine.askForAd(item) })
                }
            }
        }
        if (item.overview.isNotEmpty()) Text(item.overview, color = ESub, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).clickable(onClick = page))
        if (item.hasAd == false) Text("No audio description", color = Color(0xFFFFB74D), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (dl) DownloadChoices(item) { dl = false }
}

// ------------------------------------------------------------------------------------------- search
@Composable
private fun SearchPage(p: Page, openDrawer: () -> Unit) {
    var q by remember { mutableStateOf(p.id) }
    var asked by remember { mutableStateOf(p.id) }
    LaunchedEffect(q) { delay(450); if (q.trim().length >= 2) asked = q.trim() }
    val (d, err) = rememberData(if (asked.isEmpty()) null else "/api/play/list?kind=search&q=${enc(asked)}")
    val (b, _) = rememberData(if (asked.isEmpty()) null else "/api/play/books/list?kind=books_search&q=${enc(asked)}")
    Column(Modifier.fillMaxSize()) {
        TopBar("Search", openDrawer)
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Shows, movies, episodes, audiobooks") },
            leadingIcon = { Icon(Ic.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = ""; asked = "" }) { Icon(Ic.Close, "Clear") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { asked = q.trim() }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = ECard, unfocusedContainerColor = ECard,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (asked.isEmpty()) return@Column
        val list = d?.itemList()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
            if (list == null) { item { Loading(err) }; return@LazyColumn }
            val books = b?.itemList() ?: emptyList()
            if (list.isEmpty() && books.isEmpty()) item { Text("Nothing found for \"$asked\".", color = ESub, modifier = Modifier.padding(16.dp)) }
            posterRow("Shows", list.filter { it.type == "Series" }, 130.dp)
            posterRow("Movies", list.filter { it.type == "Movie" }, 130.dp)
            posterRow("Episodes", list.filter { it.type == "Episode" }, 260.dp, wide = true)
            posterRow("Audiobooks", books, 150.dp, square = true)
        }
    }
}

// ------------------------------------------------------------------------------------------- downloads
@Composable
private fun DownloadsPage(p: Page, openDrawer: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Downloads", openDrawer)
        val list = Offline.items.toList()
        if (list.isEmpty()) {
            Text("Nothing downloaded yet. Use Download on a movie, an episode's ⋯ menu, or an audiobook - it then plays with no connection at all.",
                color = ESub, modifier = Modifier.padding(16.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
            items(list, key = { it.kind + ":" + it.id }) { s ->
                Column {
                Row(Modifier.fillMaxWidth().clickable(enabled = s.done) { Engine.playDownload(s) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val book = s.kind == "book"
                    val bookCover = File(s.file, "cover.jpg")
                    Box(Modifier.width(if (book) 70.dp else 124.dp).aspectRatio(if (book) 1f else 16f / 9f).clip(RoundedCornerShape(6.dp)).background(ECard)) {
                        if (book && bookCover.exists()) AsyncImage(bookCover, null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else if (!book) AsyncImage(picUrl("/api/play/img/${s.id}/" + (if (s.itemType == "Episode") "Primary" else "Backdrop"), 480), null, Img.loader,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(s.kindText(), color = ESub, fontSize = 13.sp)
                        Offline.detail(s).forEachIndexed { i, line ->
                            Text(line, color = if (s.state == "failed") Color(0xFFFF8A80) else if (i == 0) Color(0xFFDDDDDD) else ESub, fontSize = 13.sp)
                        }
                        if (s.state == "preparing" || s.state == "downloading")
                            LinearProgressIndicator(progress = { s.progress / 100f }, color = EGreen, trackColor = ECard,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    if (s.done) IconButton(onClick = { Engine.playDownload(s) }) { Icon(Ic.Play, "Play ${s.title}", tint = Color.White) }
                    IconButton(onClick = { Offline.remove(s) }) {
                        Icon(if (s.done) Ic.Close else Ic.Stop, (if (s.done) "Remove the download of " else "Stop downloading ") + s.title, tint = ESub)
                    }
                }
                DownloadLive(s, ESub, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- audiobooks
@Composable
private fun BooksPage(p: Page, openDrawer: () -> Unit) {
    val (cont, _) = rememberData("/api/play/books/list?kind=books_continue")
    val (all, err) = rememberData("/api/play/books/list?kind=books_all")
    val list = all?.itemList()
    Column(Modifier.fillMaxSize()) {
        TopBar("Audiobooks", openDrawer)
        LazyVerticalGrid(
            GridCells.Adaptive(110.dp), state = W.grid(p),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val c = cont?.itemList() ?: emptyList()
            if (c.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text("Continue Listening", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(c) { PosterCard(it, 150.dp, square = true, sub = if (it.pct > 0) "${it.pct}% done" else it.author) }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("All Audiobooks", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            if (list == null) item(span = { GridItemSpan(maxLineSpan) }) { Loading(err) }
            else items(list) { PosterCard(it, Dp.Unspecified, square = true) }
        }
    }
}

@Composable
private fun BookPage(p: Page, openDrawer: () -> Unit) {
    // (a link with bad data: back where it was, instead of closing the app)
    val item = remember(p) { try { Item(JSONObject(p.extra)).takeIf { it.id.isNotEmpty() } } catch (e: Exception) { null } }
    if (item == null) { LaunchedEffect(p) { W.back() }; return }
    val (bd, _) = rememberData(if (Engine.offline) null else "/api/play/books/book?id=${enc(item.id)}")
    val chapters = bd?.optJSONArray("chapters").objs()
    val ls = W.list(p)
    var more by remember { mutableStateOf(false) }
    var castOpen by remember { mutableStateOf(false) }
    var bmOpen by remember { mutableStateOf(false) }
    val series = bd?.s("series")?.ifEmpty { null } ?: item.series
    val pos = bd?.d("position") ?: 0.0
    val finished = bd?.b("finished") ?: item.finished
    val resume = !finished && (pos > 5 || item.resume.isNotEmpty())
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(bottom = 170.dp)) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val off = Offline.done(item.id, "book")
                    val local = off?.let { File(it.file, "cover.jpg") }?.takeIf { it.exists() }
                    Box(Modifier.size(270.dp).clip(RoundedCornerShape(8.dp)).background(ECard)) {
                        AsyncImage(local ?: picUrl(item.poster.ifEmpty { "/api/play/books/cover/${item.id}" }, 480), null, Img.loader,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text(bd?.s("title")?.ifEmpty { null } ?: item.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                    val author = bd?.s("author")?.ifEmpty { null } ?: item.author
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        if (series.isNotEmpty()) {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = EGreen)); append(series); pop()
                            if (author.isNotEmpty()) append(" - ")
                        }
                        append(author)
                    }, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    val dur = bd?.d("duration") ?: 0.0
                    val m = listOf(bd?.s("year") ?: "", if (dur > 0) clock(dur) else item.length,
                        if (finished) "Finished" else if (pos > 5 && dur > 0) "${(pos * 100 / dur).toInt()}% done" else if (item.pct > 0) "${item.pct}% done" else "")
                        .filter { it.isNotEmpty() }.joinToString("   ")
                    if (m.isNotEmpty()) Text(m, color = ESub, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WhitePill(if (resume) "Resume" else "Play", Ic.Play, Modifier.weight(1f).height(56.dp)) {
                            Engine.bookPlay(item, true, null)
                        }
                        WhitePill("Play together", null, Modifier.weight(1f).height(56.dp), dark = true, size = 18) {
                            Engine.bookPlay(item, true, null, together = true)
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                        val saved = Offline.find(item.id, "book")
                        Action(if (saved?.done == true) "Downloaded" else if (saved != null) "Downloading" else "Download", Ic.Download,
                            if (saved?.done == true) EGreen else Color.White) { if (saved != null) W.openTab("downloads") else Offline.downloadBook(item) }
                        Action("Start over", Ic.Restart) { Engine.bookPlay(item, false, 0.0) }
                        Action("Bookmarks", Ic.Bookmark) { bmOpen = true }
                        Box {
                            Action("More", Ic.More) { more = true }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text("Play on…") }, leadingIcon = { Icon(Ic.Cast, null) }, onClick = { more = false; castOpen = true })
                            }
                        }
                    }
                }
            }
            item { Overview(bd?.s("description") ?: "") }
            item {
                val about = listOf("Author" to (bd?.s("author") ?: item.author), "Read by" to (bd?.s("narrator") ?: item.narrator),
                    "Series" to series, "Genres" to (bd?.s("genres") ?: ""), "Published" to (bd?.s("year") ?: "")).filter { it.second.isNotEmpty() }
                if (about.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("About", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    about.forEach { (k, v) ->
                        Text(k, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        Text(v, color = ESub, fontSize = 16.sp)
                    }
                }
            }
            if (chapters.isNotEmpty()) {
                item { RowTitle("Chapters") }
                itemsIndexed(chapters) { i, c ->
                    Row(Modifier.fillMaxWidth().clickable { Engine.bookPlay(item, false, c.d("start")) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("${i + 1}", color = ESub, fontSize = 15.sp, modifier = Modifier.width(34.dp))
                        Text(c.s("title"), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(clock(c.d("start")), color = ESub, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        TopBar("", openDrawer, solid = false, heading = false)
    }
    if (castOpen) PlayOnDialog { castOpen = false }
    if (bmOpen) BookmarkSheet(item.id, bd?.s("title")?.ifEmpty { null } ?: item.label) { bmOpen = false }
}

// ------------------------------------------------------------------------------------------- settings
/** You (settings), as on the web page: who's signed in, then a card for each part; each opens its own page. */
@Composable
private fun SettingsPage(p: Page, openDrawer: () -> Unit) {
    var castOpen by remember { mutableStateOf(false) }
    var qOpen by remember { mutableStateOf(false) }
    var dOpen by remember { mutableStateOf(false) }
    val name = Engine.me?.s("name") ?: ""
    val dev = Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "Choose"
    @Composable fun card(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(ECard)
                .border(1.dp, ELine, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(EGreen), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = EInk, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (sub.isNotEmpty()) Text(sub, color = ESub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Ic.Chevron, null, tint = ESub)
        }
    }
    @Composable fun row(title: String, sub: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(ECard)
                .border(1.dp, ELine, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (sub.isNotEmpty()) Text(sub, color = ESub, fontSize = 14.sp)
            }
            trailing?.invoke()
        }
    }
    @Composable fun switchRow(title: String, sub: String, key: String, def: String, onVal: String) {
        var on by remember { mutableStateOf(Prefs.str(key, def) == onVal) }
        val off = if (onVal == "1" && def == "1") "0" else ""
        row(title, sub, trailing = { Switch(checked = on, onCheckedChange = { on = it; Prefs.put(key, if (it) onVal else off) }) }) {
            on = !on; Prefs.put(key, if (on) onVal else off)
        }
    }
    val section = p.id
    val head = when (section) {
        "account" -> "Account"; "playback" -> "Playback"; "downloads" -> "Downloads"; "ad" -> "Audio description"; "app" -> "This app"; else -> "Settings"
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(head, openDrawer)
        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 190.dp)) {
            when (section) {
                "account" -> {
                    item { row("Signed in as $name", if (Api.atHome) "At home, straight to the PC" else "Through ${Api.base().removePrefix("http://").removePrefix("https://")}") {} }
                    item { row("Change the PC address", "") { Engine.screen = "server" } }
                    item { Box(Modifier.padding(16.dp)) { WebBtn("Sign out") { Engine.signOut() } } }
                }
                "playback" -> {
                    item { row("Headphone delay (playing together)", Engine.delays.firstOrNull { it.first == Engine.headphoneDelay }?.second ?: Engine.headphoneDelay) { dOpen = true } }
                    item { row("Video quality", Engine.qualityLabel()) { qOpen = true } }
                }
                "downloads" -> {
                    item { row("What's on this phone", "Downloaded episodes, movies and books") { W.go(Page("downloads")) } }
                    item { switchRow("Remove downloads once finished", "A downloaded episode, movie or book is deleted from the phone when it has played to the end.", "dl_remove_done", "", "1") }
                    item { switchRow("Download on Wi-Fi only", "Downloads wait while the phone is on mobile data, and carry on on Wi-Fi.", "dl_wifi_only", "1", "1") }
                    item { switchRow("Download the next episode on Wi-Fi", "When a downloaded episode ends, the next one is downloaded (on Wi-Fi only).", "dl_next_wifi", "", "1") }
                }
                "ad" -> {
                    item { row("Without audio description", "Shows and movies that have none yet") { W.go(Page("list", "noad", "Without audio description")) } }
                    item { row("Requests for audio description", "What's been asked for") { W.go(Page("list", "requests", "Requests for audio description")) } }
                }
                "app" -> {
                    item {
                        row("Version ${Update.mine}", Update.message.ifEmpty { if (Update.available.isNotEmpty()) "Version ${Update.available} is ready" else "Check for a new version" }) {
                            Engine.scope.launch { Update.check(now = true) }
                        }
                    }
                    item { UpdateCard() }
                }
                else -> {
                    item {
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(EGreen), contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase(), color = EInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Signed in", color = ESub, fontSize = 15.sp)
                            }
                        }
                    }
                    item { card("Account", "Signed in as $name", Ic.Person) { W.go(Page("settings", "account")) } }
                    item { card("Playback", "Video quality ${Engine.qualityLabel()}", Ic.Play) { W.go(Page("settings", "playback")) } }
                    item { card("Play on", dev, Ic.Cast) { castOpen = true } }
                    if (Engine.devHelp.isNotEmpty()) item { Text(Engine.devHelp, color = ESub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)) }
                    item { card("Downloads", "What's on this phone, and how it downloads", Ic.Download) { W.go(Page("settings", "downloads")) } }
                    item { card("Favorites", "Shows and movies you marked", Ic.HeartFull) { W.go(Page("favorites")) } }
                    item { card("Audio description", "Requests, and what has none yet", Ic.Ear) { W.go(Page("settings", "ad")) } }
                    item { card("This app", "Version ${Update.mine}" + if (Update.available.isNotEmpty()) " · ${Update.available} is ready" else "", Ic.Info) { W.go(Page("settings", "app")) } }
                    item { Box(Modifier.padding(16.dp)) { WebBtn("Sign out") { Engine.signOut() } } }
                }
            }
        }
    }
    if (castOpen) PlayOnDialog { castOpen = false }
    if (qOpen) ChoiceDialog("Video quality (megabits a second)", Engine.qualities, Engine.quality, { Engine.chooseQuality(it) }, { qOpen = false })
    if (dOpen) ChoiceDialog("Headphone delay (playing together)", Engine.delays, Engine.headphoneDelay, { Engine.chooseDelay(it) }, { dOpen = false })
}

@Composable
private fun NowPlayingPage(p: Page, openDrawer: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Now playing", openDrawer)
        val playing by remember { derivedStateOf { Engine.status().playing } }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 170.dp)) {
            item {
                val b = Engine.bk
                if (b != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Poster(null, "/api/play/books/cover/${b.id}", 480, Modifier.size(260.dp).padding(bottom = 12.dp))
                }
            }
            item { if (playing) NowPlaying() else Text("Nothing is playing.", color = ESub) }
        }
    }
}
