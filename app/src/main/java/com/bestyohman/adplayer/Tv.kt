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

/** The TV's menu down the left side (instead of the tabs at the bottom). */
@Composable
fun TvRail() {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
    Column(
        Modifier.width(210.dp).fillMaxHeight().background(if (Look.low) EBg else Color(0xFF0E1116)).padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.padding(start = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(36.dp)
            Text("AD Player", color = EFg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
        }
        listOf(
            Triple("home", "Home", Ic.Home), Triple("search", "Search", Ic.Search), Triple("shows", "Shows", Ic.Tv),
            Triple("movies", "Movies", Ic.Movie), Triple("books", "Books", Ic.Headphones), Triple("you", "Settings", Ic.Settings)
        ).forEachIndexed { i, (k, l, ic) ->
            val on = W.tab == k || (k == "search" && W.stack.lastOrNull()?.kind == "search")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    .clip(RoundedCornerShape(14.dp)).background(if (on) (if (Look.low) EMain else Color(0x242DD4BF)) else Color.Transparent)
                    .clickable {
                        when (k) {
                            "shows" -> W.openTab(k, Page("libtab", "tvshows"))
                            "movies" -> W.openTab(k, Page("libtab", "movies"))
                            "books" -> W.openTab(k, Page("books"))
                            "you" -> W.openTab(k, Page("settings"))
                            "search" -> W.go(Page("search"))
                            else -> W.openTab(k)
                        }
                    }
                    .semantics { contentDescription = l + if (on) ", selected" else ""; role = Role.Tab }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val c = if (on) (if (Look.low) EInk else EGreen) else EFg
                Icon(ic, null, tint = c, modifier = Modifier.size(24.dp))
                Text(l, color = c, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}
