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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
// The app for people who watch the video: laid out like the Emby app (posters, a home page of rows,
// a page for each show and movie, and the player's own controls). People who only listen keep the plain
// list in Ui.kt, made for TalkBack.
// ======================================================================================================

val EBg = Color(0xFF101010)
val ECard = Color(0xFF262626)
val EGreen = Color(0xFF52B54B)
val ESub = Color(0xFFA6A6A6)
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
            "Series", "Movie", "Season" -> go(Page("item", it.id, it.name, it.type))
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
            primary = EGreen, secondary = EGreen, background = EBg, surface = EBg, surfaceVariant = ECard,
            surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = ECard, onSurfaceVariant = ESub
        )
    ) {
        val vd = Engine.vd
        LaunchedEffect(vd?.item) { if (vd != null) W.player = true }
        val showPlayer = W.player && vd != null
        LaunchedEffect(showPlayer) { act.playerMode(showPlayer) }
        LaunchedEffect(Engine.message) {
            if (Engine.message.isNotEmpty()) { W.toast = Engine.message; val n = ++W.toastSeq; delay(3500); if (W.toastSeq == n) W.toast = "" }
        }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        BackHandler(enabled = drawer.isOpen || W.player || W.stack.size > 1 || W.tab != "home") {
            if (drawer.isOpen) scope.launch { drawer.close() } else W.back()
        }
        if (showPlayer) { PlayerScreen(act); return@MaterialTheme }
        ModalNavigationDrawer(drawerState = drawer, drawerContent = { Drawer { scope.launch { drawer.close() } } }) {
            Surface(Modifier.fillMaxSize(), color = EBg) {
                Box(Modifier.fillMaxSize()) {
                    val page = W.stack.last()
                    key(page) { PageContent(page) { scope.launch { drawer.open() } } }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                        if (W.toast.isNotEmpty()) Toast(W.toast)
                        MiniPlayer()
                        BottomNav()
                    }
                }
            }
        }
    }
}

@Composable
private fun Toast(t: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            t, color = Color.White, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Center).background(Color(0xEE333333), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PageContent(p: Page, openDrawer: () -> Unit) {
    when (p.kind) {
        "home" -> HomePage(p, false, openDrawer)
        "favorites" -> HomePage(p, true, openDrawer)
        "library", "children", "list" -> GridPage(p, openDrawer)
        "item" -> if (p.extra == "Season") SeasonPage(p, openDrawer) else DetailPage(p, openDrawer)
        "search" -> SearchPage(p, openDrawer)
        "downloads" -> DownloadsPage(p, openDrawer)
        "movies" -> MoviesTab(openDrawer)
        "books" -> BooksPage(p, openDrawer)
        "book" -> BookPage(p, openDrawer)
        "settings" -> SettingsPage(p, openDrawer)
        "nowplaying" -> NowPlayingPage(p, openDrawer)
        else -> HomePage(p, false, openDrawer)
    }
}

/** The bar at the top: menu (or back), the name, Play on, and who's signed in. */
@Composable
private fun TopBar(title: String?, openDrawer: () -> Unit, solid: Boolean = true) {
    var castOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (solid) EBg else Color.Transparent)
            .statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (W.stack.size > 1) IconButton(onClick = { W.back() }) { Icon(Ic.Back, "Back", tint = Color.White) }
        else IconButton(onClick = openDrawer) { Icon(Ic.Menu, "Menu", tint = Color.White) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (title == null) {
                Icon(Ic.Play, null, tint = EGreen, modifier = Modifier.size(30.dp))
                Text("AD Player", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.padding(start = 2.dp))
            } else Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        }
        IconButton(onClick = { castOpen = true }) {
            Icon(Ic.Cast, "Play on: " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "choose"), tint = Color.White)
        }
        Avatar { W.go(Page("settings")) }
    }
    if (castOpen) PlayOnDialog { castOpen = false }
}

@Composable
private fun Avatar(onClick: () -> Unit) {
    val name = Engine.me?.s("name") ?: ""
    Box(
        Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).background(EGreen).clickable(onClick = onClick)
            .semantics { contentDescription = "Settings - signed in as $name" },
        contentAlignment = Alignment.Center
    ) { Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
}

@Composable
fun PlayOnDialog(onClose: () -> Unit) {
    LaunchedEffect(Unit) { Engine.refreshDevices() }
    ChoiceDialog("Play on", Engine.devices.map { it.id to it.name }, Engine.dev, { Engine.pickDevice(it) }, onClose)
}

@Composable
private fun BottomNav() {
    if (W.player) return
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(Color(0xF0232323))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(40.dp)).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(Triple("home", "Home", Ic.Home), Triple("search", "Search", Ic.Search),
                Triple("downloads", "Downloads", Ic.Download), Triple("movies", "Movies", Ic.Movie)).forEach { (k, l, ic) ->
                val on = W.tab == k
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(30.dp)).clickable { W.openTab(k) }.padding(vertical = 4.dp)
                        .semantics { contentDescription = l + if (on) ", selected" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(ic, null, tint = if (on) EGreen else Color.White, modifier = Modifier.size(26.dp))
                    Text(l, color = if (on) EGreen else Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/** What's playing, above the bar at the bottom (tap it for the player). */
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(ECard)
            .clickable { if (v != null) W.player = true else W.go(Page("nowplaying")) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = if (b != null) 44.dp else 70.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
            if (pic.isNotEmpty()) AsyncImage(picUrl(pic, 160), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                if (st.tv) "On " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "the TV") else if (Engine.tg != null) "Playing together" else "On this phone",
                color = ESub, fontSize = 12.sp, maxLines = 1
            )
        }
        IconButton(onClick = { Engine.cmd("playpause") }) { Icon(if (st.paused) Ic.Play else Ic.Pause, if (st.paused) "Play" else "Pause", tint = Color.White) }
        IconButton(onClick = { Engine.cmd("stop") }) { Icon(Ic.Close, if (Engine.tg != null) "Leave playing together" else "Stop", tint = ESub) }
    }
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
                entry(l.s("name"), if (l.s("type") == "tvshows") Ic.Tv else Ic.Movie) { W.openTab("lib", Page("library", l.s("id"), l.s("name"))) }
            }
            entry("Audiobooks", Ic.Book) { W.openTab("books", Page("books")) }
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
fun Poster(item: Item?, src: String, w: Int, modifier: Modifier, fallback: String = "", round: Dp = 8.dp, icon: ImageVector? = null) {
    Box(modifier.clip(RoundedCornerShape(round)).background(ECard)) {
        if (src.isEmpty() || icon != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.4f))
            else Text(fallback, color = ESub, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp), fontSize = 14.sp)
        }
        if (src.isNotEmpty()) AsyncImage(picUrl(src, w), null, Img.loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (item != null) {
            val played = W.isPlayed(item)
            when {
                played -> Badge(Modifier.align(Alignment.TopEnd)) { Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                item.unplayed > 0 -> Badge(Modifier.align(Alignment.TopEnd)) { Text("${item.unplayed}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val src = when {
            wide -> item.thumb.ifEmpty { item.backdrop.ifEmpty { item.poster } }
            else -> item.poster.ifEmpty { item.thumb }
        }
        Poster(item, src, if (wide) 800 else 320, Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else if (square) 1f else 2f / 3f), title)
        Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (wide) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        if (subtitle.isNotEmpty()) Text(subtitle, color = ESub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (wide) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth())
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
fun WhitePill(text: String, icon: ImageVector?, modifier: Modifier, dark: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (dark) Color(0xFF2B2B2B) else Color.White).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(28.dp))
        Text(text, color = if (dark) Color.White else Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
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

// ------------------------------------------------------------------------------------------- home
@Composable
private fun HomePage(p: Page, favorites: Boolean, openDrawer: () -> Unit) {
    val (home, err) = rememberData(if (favorites || Engine.offline) null else "/api/play/home")
    val (fav, ferr) = rememberData(if (favorites) "/api/play/list?kind=favorites" else null)
    val (books, _) = rememberData(if (favorites || Engine.offline) null else "/api/play/books/list?kind=books_continue")
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = W.list(p), contentPadding = PaddingValues(bottom = 170.dp)) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(Modifier.height(56.dp)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("Home" to false, "Favorites" to true).forEach { (l, f) ->
                        val on = f == favorites
                        Text(l, color = if (on) Color.White else ESub, fontSize = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF3A3A3A) else Color.Transparent)
                                .clickable { W.openTab("home", Page(if (f) "favorites" else "home")) }.padding(horizontal = 18.dp, vertical = 6.dp))
                    }
                }
            }
            item { OfflineNote() }
            item { InviteCards() }
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
                                Modifier.width(170.dp).clickable { W.openTab("lib", Page("library", l.s("id"), l.s("name"))) }
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
            posterRow("Continue Watching", home.itemList("resume"), 300.dp, wide = true) { Engine.start(it, true) }
            posterRow("Next Up", home.itemList("next_up"), 300.dp, wide = true) { Engine.start(it, it.resume.isNotEmpty()) }
            val bl = books?.itemList() ?: emptyList()
            posterRow("Continue Listening", bl, 170.dp, square = true, onMore = { W.openTab("books", Page("books")) }) { Engine.bookPlay(it, true, null) }
            home.optJSONArray("latest").objs().forEach { r ->
                posterRow("Latest " + r.s("name"), r.itemList(), 130.dp, onMore = { W.openTab("lib", Page("library", r.s("id"), r.s("name"))) })
            }
        }
        TopBar(null, openDrawer)
    }
}

// ------------------------------------------------------------------------------------------- a grid of posters
@Composable
private fun GridPage(p: Page, openDrawer: () -> Unit) {
    val path = when (p.kind) {
        "library" -> "/api/play/list?kind=library&parent=${enc(p.id)}"
        "children" -> "/api/play/list?kind=children&parent=${enc(p.id)}"
        else -> if (p.id == "noad") "/api/play/list?kind=noad" else "/api/play/list?kind=${enc(p.id)}"
    }
    val (d, err) = rememberData(path)
    val list = d?.itemList()
    Column(Modifier.fillMaxSize()) {
        TopBar(p.title, openDrawer)
        when {
            list == null -> Loading(err)
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
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("${list.size} items", color = ESub, fontSize = 13.sp) }
                    items(list) { PosterCard(it, Dp.Unspecified, wide) }
                }
            }
        }
    }
}

@Composable
private fun MoviesTab(openDrawer: () -> Unit) {
    val (home, err) = rememberData("/api/play/home")
    val lib = home?.optJSONArray("libraries").objs().firstOrNull { it.s("type") == "movies" }
    LaunchedEffect(lib?.s("id")) { if (lib != null) W.stack[W.stack.lastIndex] = Page("library", lib.s("id"), lib.s("name")) }
    Column { TopBar("Movies", openDrawer); if (home != null && lib == null) Text("There's no movie library.", color = ESub, modifier = Modifier.padding(16.dp)) else Loading(err) }
}

// ------------------------------------------------------------------------------------------- a show or a movie
@Composable
private fun Hero(info: Item?, listState: LazyListState?, content: @Composable () -> Unit = {}) {
    val src = info?.backdrop?.ifEmpty { info.thumb } ?: ""
    Box(Modifier.fillMaxWidth().aspectRatio(0.98f)) {
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
    Box(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = Color.White, fontSize = 17.sp, lineHeight = 24.sp, maxLines = if (open) 60 else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = if (open) 0.dp else 0.dp))
        if (!open && text.length > 140) Text("More", color = ESub, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).background(EBg).padding(start = 12.dp))
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

@Composable
private fun DetailPage(p: Page, openDrawer: () -> Unit) {
    val (d, err) = rememberData("/api/play/info?id=${enc(p.id)}")
    val info = d?.let { Item(it) }
    val ls = W.list(p)
    val scrolled by remember { derivedStateOf { ls.firstVisibleItemIndex > 0 || ls.firstVisibleItemScrollOffset > 400 } }
    var more by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(bottom = 170.dp)) {
            item { Hero(info, ls) { if (info != null) Logo(info, info.name) } }
            if (info == null) { item { Loading(err) }; return@LazyColumn }
            val j = info.j
            item { MetaRow(info) }
            if (info.type == "Movie" && (j.s("video_info").isNotEmpty() || j.s("audio_info").isNotEmpty())) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (j.s("video_info").isNotEmpty()) Text(buildLabel("Video", j.s("video_info")), fontSize = 16.sp)
                    if (j.s("audio_info").isNotEmpty()) Text(buildLabel("Audio", j.s("audio_info")), fontSize = 16.sp)
                }
            }
            item {
                val next = j.optJSONObject("next_up")?.let { Item(it) }
                val target = if (info.type == "Series") next else info
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val label = when {
                        target == null -> "Play"
                        target.resume.isNotEmpty() -> "Resume"
                        info.type == "Series" && target.se >= 0 && target.ep >= 0 -> "Play S${target.se}:E${target.ep}"
                        else -> "Play"
                    }
                    WhitePill(label, Ic.Play, Modifier.weight(1f).height(56.dp)) {
                        if (target != null) Engine.start(target, target.resume.isNotEmpty())
                        else info.j.optJSONArray("seasons").objs().firstOrNull()?.let { W.open(Item(it)) }
                    }
                    if (j.s("trailer").isNotEmpty()) WhitePill("Trailer", Ic.Movie, Modifier.weight(1f).height(56.dp), dark = true) {
                        try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(j.s("trailer")))) }
                        catch (e: Exception) { Engine.say("Couldn't open the trailer.") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (info.type == "Movie") {
                        val saved = Offline.forItem(info.id)
                        Action(if (saved.any { it.done }) "Downloaded" else if (saved.isNotEmpty()) "Downloading" else "Download", Ic.Download,
                            if (saved.any { it.done }) EGreen else Color.White) { if (saved.isNotEmpty()) W.openTab("downloads") else dl = true }
                    }
                    val pl = W.isPlayed(info)
                    Action("Played", Ic.Check, if (pl) EGreen else Color.White) { W.markPlayed(info, !pl) }
                    Action("Favorite", if (info.favorite) Ic.HeartFull else Ic.Heart, if (info.favorite) ERed else Color.White) { Engine.toggleFavorite(info) }
                    Box {
                        Action("More", Ic.More) { more = true }
                        MoreMenu(info, more, { more = false })
                    }
                }
            }
            if (j.s("tagline").isNotEmpty()) item {
                Text(j.s("tagline"), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
            }
            item { Overview(info.overview) }
            if (j.s("directors").isNotEmpty()) item {
                Text(buildLabel("Director: ", j.s("directors"), grey = true), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val seasons = j.optJSONArray("seasons").objs().map { Item(it) }
            posterRow("Seasons", seasons, 130.dp)
            val people = j.optJSONArray("people").objs()
            if (people.isNotEmpty()) {
                item { RowTitle("Cast & Crew") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(people) { pp ->
                            Column(Modifier.width(130.dp).semantics(mergeDescendants = true) {}) {
                                Poster(null, pp.s("image"), 320, Modifier.fillMaxWidth().aspectRatio(2f / 3f), icon = if (pp.s("image").isEmpty()) Ic.Person else null)
                                Text(pp.s("name"), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                Text(pp.s("role"), color = ESub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            posterRow("More Like This", j.optJSONArray("similar").objs().map { Item(it) }, 130.dp)
        }
        TopBar(if (scrolled) p.title else "", openDrawer, solid = scrolled)
        if (dl && info != null) DownloadChoices(info) { dl = false }
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
                WhitePill(if (next?.resume?.isNotEmpty() == true) "Resume" else "Play", Ic.Play,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).height(56.dp)) {
                    if (next != null) Engine.start(next, next.resume.isNotEmpty())
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
        TopBar(if (scrolled) info?.seriesName?.ifEmpty { p.title } ?: p.title else "", openDrawer, solid = scrolled)
    }
}

@Composable
private fun EpisodeCard(item: Item) {
    var menu by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf(false) }
    val play = { Engine.start(item, item.resume.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = play)
                .semantics(mergeDescendants = true) { contentDescription = item.spoken(); role = Role.Button },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(150.dp).aspectRatio(16f / 9f)) {
                Poster(item, item.thumb, 480, Modifier.fillMaxSize(), round = 6.dp)
                Box(
                    Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color(0x55000000))
                        .border(BorderStroke(3.dp, Color.White), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.Play, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text((if (item.ep >= 0) "${item.ep}. " else "") + item.name, color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (item.aired.isNotEmpty()) Text(item.aired, color = ESub, fontSize = 15.sp)
                    if (item.rt.isNotEmpty()) Text(item.rt, color = ESub, fontSize = 15.sp)
                    if (Offline.done(item.id, "video") != null || Offline.done(item.id, "audio") != null)
                        Icon(Ic.Download, "Downloaded", tint = EGreen, modifier = Modifier.size(18.dp))
                }
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
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
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
            items(list) { s ->
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
                        Text(s.stateText(), color = if (s.state == "failed") Color(0xFFFF8A80) else ESub, fontSize = 13.sp)
                        if (s.state == "preparing" || s.state == "downloading")
                            LinearProgressIndicator(progress = { s.progress / 100f }, color = EGreen, trackColor = ECard,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    if (s.done) IconButton(onClick = { Engine.playDownload(s) }) { Icon(Ic.Play, "Play ${s.title}", tint = Color.White) }
                    IconButton(onClick = { Offline.remove(s) }) {
                        Icon(if (s.done) Ic.Close else Ic.Stop, (if (s.done) "Remove the download of " else "Stop downloading ") + s.title, tint = ESub)
                    }
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
    val item = remember(p) { Item(JSONObject(p.extra)) }
    val (ch, _) = rememberData("/api/play/books/list?kind=books_chapters&parent=${enc(item.id)}")
    val chapters = ch?.itemList() ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        TopBar(item.label, openDrawer)
        LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Poster(item, item.poster, 480, Modifier.size(240.dp), item.label)
                    Text(item.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                    if (item.author.isNotEmpty()) Text("by ${item.author}", color = Color.White, fontSize = 17.sp)
                    if (item.narrator.isNotEmpty()) Text("Read by ${item.narrator}", color = ESub, fontSize = 15.sp)
                    val m = listOf(item.length, if (item.series.isNotEmpty()) item.series else "", if (item.pct > 0) "${item.pct}% done" else if (item.finished) "Finished" else "")
                        .filter { it.isNotEmpty() }.joinToString("  ·  ")
                    if (m.isNotEmpty()) Text(m, color = ESub, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    WhitePill(if (item.resume.isNotEmpty()) "Resume" else "Play", Ic.Play, Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp)) {
                        Engine.bookPlay(item, true, null)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        if (item.resume.isNotEmpty() || item.finished) Action("Start over", Ic.Restart) { Engine.bookPlay(item, false, 0.0) }
                        val saved = Offline.find(item.id, "book")
                        Action(if (saved?.done == true) "Downloaded" else if (saved != null) "Downloading" else "Download", Ic.Download,
                            if (saved?.done == true) EGreen else Color.White) { if (saved != null) W.openTab("downloads") else Offline.downloadBook(item) }
                    }
                }
            }
            if (chapters.isNotEmpty()) {
                item { RowTitle("Chapters") }
                items(chapters) { c ->
                    Row(Modifier.fillMaxWidth().clickable { Engine.bookPlay(c, false, c.start) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(c.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(c.at, color = ESub, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- settings
@Composable
private fun SettingsPage(p: Page, openDrawer: () -> Unit) {
    var castOpen by remember { mutableStateOf(false) }
    var qOpen by remember { mutableStateOf(false) }
    var dOpen by remember { mutableStateOf(false) }
    @Composable fun row(title: String, sub: String, icon: ImageVector, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp)
                if (sub.isNotEmpty()) Text(sub, color = ESub, fontSize = 14.sp)
            }
            trailing?.invoke()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", openDrawer)
        LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
            item { row("Play on", Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "Choose", Ic.Cast) { castOpen = true } }
            if (Engine.devHelp.isNotEmpty()) item { Text(Engine.devHelp, color = ESub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 56.dp)) }
            item {
                row("Play together", "What you start here also plays on another person's phone, in step.", Ic.Group,
                    trailing = { Switch(checked = Engine.together, onCheckedChange = { Engine.toggleTogether() }) }) { Engine.toggleTogether() }
            }
            if (Engine.together) item {
                row("Headphone delay", Engine.delays.firstOrNull { it.first == Engine.headphoneDelay }?.second ?: Engine.headphoneDelay, Ic.Headphones) { dOpen = true }
            }
            item { row("Video quality", Engine.qualityLabel(), Ic.Settings) { qOpen = true } }
            item { HorizontalDivider(color = Color(0xFF2A2A2A)) }
            item {
                row("Signed in as ${Engine.me?.s("name") ?: ""}",
                    if (Api.atHome) "At home, straight to the PC" else "Through ${Api.base().removePrefix("http://").removePrefix("https://")}", Ic.Person) {}
            }
            item { row("Change the PC address", "", Ic.Tv) { Engine.screen = "server" } }
            item { row("Sign out", "", Ic.Logout) { Engine.signOut() } }
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
        val st = Engine.status()
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 170.dp)) {
            item {
                val b = Engine.bk
                if (b != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Poster(null, "/api/play/books/cover/${b.id}", 480, Modifier.size(260.dp).padding(bottom = 12.dp))
                }
            }
            item { if (st.playing) NowPlaying(st) else Text("Nothing is playing.", color = ESub) }
        }
    }
}
