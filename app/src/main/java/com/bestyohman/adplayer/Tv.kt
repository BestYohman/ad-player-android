package com.bestyohman.adplayer

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.delay
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.Modifier.Node
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** On a TV, whatever the remote's arrows are on gets a thick outline and a light wash (every button and poster). */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7

    private class FocusNode(val src: InteractionSource) : Node(), DrawModifierNode {
        private var focused = false
        override fun onAttach() {
            coroutineScope.launch {
                src.interactions.collect { i ->
                    when (i) {
                        is FocusInteraction.Focus -> { focused = true; invalidateDraw() }
                        is FocusInteraction.Unfocus -> { focused = false; invalidateDraw() }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (focused) {
                val w = 4.dp.toPx(); val r = CornerRadius(14.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = r)
                drawRoundRect(EGreen, cornerRadius = r, style = Stroke(width = w),
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
                // a thin dark line just inside, so the outline stands out on a white button or a bright picture too
                val t = 2.dp.toPx(); val o = w + t / 2
                if (size.width > 2 * o && size.height > 2 * o) drawRoundRect(Color(0xB3000000), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(width = t),
                    topLeft = androidx.compose.ui.geometry.Offset(o, o), size = androidx.compose.ui.geometry.Size(size.width - 2 * o, size.height - 2 * o))
            }
        }
    }
}

/** How big everything is on this TV (Settings, How you see the screen): kept on this TV only. A TV shows the phone's
 *  layout at its own size, which is far too big from the sofa, so it starts at "Medium". */
object TvSize {
    val opts = listOf("small" to "Small", "medium" to "Medium", "large" to "Large", "larger" to "Larger")
    var v by mutableStateOf(Prefs.str("tvSize", "medium"))
        private set
    fun set(k: String) { v = k; Prefs.put("tvSize", k) }
    val scale: Float get() = when (v) { "small" -> 0.58f; "large" -> 0.86f; "larger" -> 1f; else -> 0.7f }
}

/** The TV's remote: whether it's on the menu or the page, and where it was on each page (to go back there). */
object TvNav {
    var navIn by mutableStateOf(false)               // the remote is on the menu at the top (then it shows)
    val navFr = FocusRequester()                     // the menu's item for the page that's open
    val pageFr = FocusRequester()                    // the page (it passes the remote on to its first button or card)
    var pageHas = false                              // the remote is somewhere on the page
    var started = false
    var fromMenu = false                             // the page was opened from the menu (the remote stays there)
    var restoreUntil = 0L                            // a page just opened: its remembered card may take the remote until then
    val last = HashMap<Page, String>()               // page -> the card or button the remote was last on
    var hideControls: (() -> Boolean)? = null        // the video player: hides its buttons (true if they were showing)
}

/** The page being shown (for remembering where the remote was on it). */
val LocalPage = compositionLocalOf<Page?> { null }

/** A TV: this card or button is remembered as where the remote was on this page, and gets it back when the page
 *  comes back (Back, or the player closing). */
@Composable
fun Modifier.tvFocusKey(key: String): Modifier {
    if (!Engine.isTv) return this
    val page = LocalPage.current ?: return this
    val fr = remember { FocusRequester() }
    LaunchedEffect(key) {
        delay(60)
        if (TvNav.last[page] == key && !TvNav.pageHas && System.currentTimeMillis() < TvNav.restoreUntil) runCatching { fr.requestFocus() }
    }
    return this.focusRequester(fr).onFocusChanged { if (it.hasFocus) TvNav.last[page] = key }
}

/** A TV: words the remote can stop on (so it can scroll to them - the remote only goes to things it can stop on). */
@Composable
fun Modifier.tvReadable(): Modifier {
    if (!Engine.isTv) return this
    val s = remember { MutableInteractionSource() }
    return this.indication(s, LocalIndication.current).focusable(interactionSource = s)
}

/** A TV: Down with nothing further down to stop on still scrolls to the very end of the page. */
@Composable
fun Modifier.tvScrollEnds(ls: androidx.compose.foundation.lazy.LazyListState): Modifier {
    if (!Engine.isTv) return this
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    val sc = androidx.compose.runtime.rememberCoroutineScope()
    return this.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionDown) return@onPreviewKeyEvent false
        if (!fm.moveFocus(FocusDirection.Down)) sc.launch { val n = ls.layoutInfo.totalItemsCount; if (n > 0) ls.animateScrollToItem(n - 1) }
        true
    }
}

/** A TV: a row that remembers which card the remote was on (down into it goes back there). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvRow(): Modifier = if (Engine.isTv) this.focusRestorer() else this

/** The TV's menu along the top, as on the web page on a computer: the logo, the pages, then Play on. It's only shown
 *  while the remote is on it (Up from the top of a page, or Back, brings it). */
@Composable
fun TvTopNav() {
    val first = TvNav.navFr
    var castOpen by remember { mutableStateOf(false) }
    // the remote starts on the menu when the app opens
    LaunchedEffect(Unit) { if (!TvNav.started) { TvNav.started = true; delay(150); try { first.requestFocus() } catch (e: Exception) { } } }
    val onSearch = W.stack.lastOrNull()?.kind == "search"
    val tabs = listOf(
        Triple("home", "Home", Ic.Home), Triple("shows", "Shows", Ic.Tv), Triple("movies", "Movies", Ic.Movie),
        Triple("books", "Books", Ic.Headphones), Triple("search", "Search", Ic.Search), Triple("you", "Settings", Ic.Settings)
    )
    val cur = if (onSearch) "search" else tabs.firstOrNull { it.first == W.tab }?.first ?: "home"
    Row(
        Modifier.fillMaxWidth().background(if (Look.low) androidx.compose.ui.graphics.SolidColor(EBg)
            else androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0xF00E1116), 0.7f to Color(0xB30E1116), 1f to Color(0x660E1116)))
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LogoMark(34.dp)
        Text("AD Player", color = EFg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
            modifier = Modifier.padding(start = 8.dp, end = 22.dp).semantics { heading() })
        tabs.forEach { (k, l, ic) ->
            val on = k == cur
            Row(
                Modifier.heightIn(min = 44.dp).then(if (on) Modifier.focusRequester(first) else Modifier)
                    .clip(RoundedCornerShape(22.dp)).background(if (on) (if (Look.low) EMain else Color(0x242DD4BF)) else Color.Transparent)
                    .clickable {
                        TvNav.fromMenu = true
                        when (k) {
                            "shows" -> W.openTab(k, Page("libtab", "tvshows"))
                            "movies" -> W.openTab(k, Page("libtab", "movies"))
                            "books" -> W.openTab(k, Page("books"))
                            "you" -> W.openTab(k, Page("settings"))
                            "search" -> if (!onSearch) W.go(Page("search"))
                            else -> W.openTab(k)
                        }
                    }
                    .semantics { contentDescription = l + if (on) ", selected" else ""; role = Role.Tab }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val c = if (on) (if (Look.low) EInk else EGreen) else EFg
                Icon(ic, null, tint = c, modifier = Modifier.size(20.dp))
                Text(l, color = c, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.weight(1f))
    }
    if (castOpen) PlayOnDialog { castOpen = false }
}
