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
                drawRoundRect(if (Look.low) EGreen else Color.White, cornerRadius = r, style = Stroke(width = w),
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
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

/** The TV's menu along the top, as on the web page on a computer: the logo, the pages, then Search and Play on. */
@Composable
fun TvTopNav() {
    val first = remember { FocusRequester() }
    var castOpen by remember { mutableStateOf(false) }
    // the remote starts on the page's own menu item (also when the player closes and this comes back)
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
    val onSearch = W.stack.lastOrNull()?.kind == "search"
    val tabs = listOf(
        Triple("home", "Home", Ic.Home), Triple("shows", "Shows", Ic.Tv), Triple("movies", "Movies", Ic.Movie),
        Triple("books", "Books", Ic.Headphones), Triple("search", "Search", Ic.Search), Triple("you", "Settings", Ic.Settings)
    )
    val cur = if (onSearch) "search" else tabs.firstOrNull { it.first == W.tab }?.first ?: "home"
    Row(
        Modifier.fillMaxWidth().background(if (Look.low) EBg else Color(0xFF0E1116))
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
        GlassButton(Ic.Cast, "Play on: " + (Engine.devices.firstOrNull { it.id == Engine.dev }?.name ?: "choose"), wide = true) { castOpen = true }
    }
    if (castOpen) PlayOnDialog { castOpen = false }
}
