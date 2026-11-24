package com.bestyohman.adplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** How a library is sorted and filtered (kept for each library on the phone). */
data class LibView(
    val sort: String = "title", val desc: Boolean = false, val state: String = "", val fav: Boolean = false,
    val ad: Boolean = true, val genre: String = "", val rating: String = "", val year: String = ""
) {
    fun query(): String = "&sort=$sort&desc=${if (desc) 1 else 0}" +
        (if (state.isNotEmpty()) "&state=$state" else "") + (if (fav) "&fav=1" else "") + (if (ad) "&ad=1" else "") +
        (if (genre.isNotEmpty()) "&genre=${enc(genre)}" else "") + (if (rating.isNotEmpty()) "&rating=${enc(rating)}" else "") +
        (if (year.isNotEmpty()) "&year=${enc(year)}" else "")

    val filtersOn: Int get() = listOf(state.isNotEmpty(), fav, ad, genre.isNotEmpty(), rating.isNotEmpty(), year.isNotEmpty()).count { it }

    fun toJson(): String = JSONObject().put("sort", sort).put("desc", desc).put("state", state).put("fav", fav).put("ad", ad)
        .put("genre", genre).put("rating", rating).put("year", year).toString()

    companion object {
        fun load(lib: String): LibView = try {
            val j = JSONObject(Prefs.str("libview_$lib", "{}"))
            LibView(j.optString("sort", "title"), j.optBoolean("desc"), j.optString("state"), j.optBoolean("fav"), j.optBoolean("ad", true),
                j.optString("genre"), j.optString("rating"), j.optString("year"))
        } catch (e: Exception) { LibView() }

        fun save(lib: String, v: LibView) = Prefs.put("libview_$lib", v.toJson())
    }
}

object LibSorts {
    /** key, name, newest/biggest first by default */
    val all = listOf(
        Triple("title", "Title", false), Triple("added", "Date Added", true), Triple("played", "Date Played", true),
        Triple("released", "Release Date", true), Triple("year", "Year", true), Triple("runtime", "Runtime", false),
        Triple("critic", "Critic Rating", true), Triple("rating", "Community Rating", true), Triple("parental", "Parental Rating", false),
        Triple("plays", "Plays", true), Triple("random", "Random", false)
    )
    fun name(k: String) = all.firstOrNull { it.first == k }?.second ?: "Title"
    fun way(v: LibView): String = when (v.sort) {
        "random" -> ""
        "title" -> if (v.desc) "Z to A" else "A to Z"
        "added", "played", "released", "year" -> if (v.desc) "newest first" else "oldest first"
        "runtime" -> if (v.desc) "longest first" else "shortest first"
        else -> if (v.desc) "highest first" else "lowest first"
    }
}

private val SheetBg: Color get() = if (Look.low) EBg else Color(0xFF262626)
private val FieldBg: Color get() = EBtn2

/** The chips under a library's name, as on the web page: With AD, Favorites, In progress, Unwatched. */
@Composable
fun FilterChips(view: LibView, edge: Boolean = true, onChange: (LibView) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (edge) 16.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)
    ) {
        item { FChip("With AD", view.ad) { onChange(view.copy(ad = !view.ad)) } }
        item { FChip("Favorites", view.fav) { onChange(view.copy(fav = !view.fav)) } }
        item { FChip("In progress", view.state == "resumable") { onChange(view.copy(state = if (view.state == "resumable") "" else "resumable")) } }
        item { FChip("Unwatched", view.state == "unplayed") { onChange(view.copy(state = if (view.state == "unplayed") "" else "unplayed")) } }
    }
}

@Composable
private fun FChip(text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(if (Look.low) 56.dp else 40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) (if (Look.low) EMain else Color(0x292DD4BF)) else EBtn2)
            .border(Look.line, if (on) (if (Look.low) EMain else EGreen) else ELine, RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .semantics { role = Role.Button; selected = on }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (on) Icon(Ic.Check, null, tint = if (Look.low) EInk else EGreen, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        Text(text, color = if (on && Look.low) EInk else EFg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = if (on) 4.dp else 0.dp))
    }
}

/** The round Sort and filter button beside a library's name (as on the web page), and its sheet. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterButton(lib: String, view: LibView, onChange: (LibView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val way = LibSorts.way(view)
    Box {
        Box(
            Modifier.size(if (Look.low) 56.dp else 44.dp).clip(RoundedCornerShape(if (Look.low) 28.dp else 22.dp)).background(if (open) EMain else EBtn2).border(Look.line, ELine, RoundedCornerShape(22.dp))
                .clickable { open = true }.semantics {
                    role = Role.Button
                    contentDescription = "Sort and filter: ${LibSorts.name(view.sort)}" + (if (way.isNotEmpty()) ", $way" else "") +
                        (if (view.filtersOn > 0) ", ${view.filtersOn} filters on" else "")
                },
            contentAlignment = Alignment.Center
        ) { Icon(Ic.Filter, null, tint = if (open) EInk else EFg, modifier = Modifier.size(22.dp)) }
        if (view.filtersOn > 1 || (view.filtersOn == 1 && !view.ad) || view.sort != "title")
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(EGreen))
    }
    if (open) {
        val (opts, optsErr) = rememberData("/api/play/filters?parent=${enc(lib)}")
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = st, containerColor = ECard) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort and filter", color = EFg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).semantics { heading() })
                    if (view.filtersOn > 0 || view.sort != "title") TextButton(onClick = { onChange(LibView(ad = false)) }) { Text("Clear all", color = EGreen) }
                }
                Choice("Sort by", LibSorts.all.map { it.first to it.second }, view.sort) { k ->
                    onChange(view.copy(sort = k, desc = LibSorts.all.firstOrNull { it.first == k }?.third ?: false))
                }
                if (view.sort != "random") {
                    val ways = listOf(view.copy(desc = false), view.copy(desc = true)).map { LibSorts.way(it) }
                    Choice("Order", listOf("0" to ways[0], "1" to ways[1]), if (view.desc) "1" else "0") { onChange(view.copy(desc = it == "1")) }
                }
                Choice("Watched", listOf("" to "Any", "unplayed" to "Unwatched", "played" to "Watched", "resumable" to "In progress"), view.state) { onChange(view.copy(state = it)) }
                Toggle("Favorites only", view.fav) { onChange(view.copy(fav = it)) }
                Toggle("Only with audio description", view.ad) { onChange(view.copy(ad = it)) }
                if (opts == null) Text(optsErr.ifEmpty { "Loading…" }, color = ESub, fontSize = 17.sp, modifier = Modifier.padding(vertical = 8.dp))
                val genres = opts?.optJSONArray("genres").strs()
                val ratings = opts?.optJSONArray("ratings").strs()
                val years = opts?.optJSONArray("years")?.let { a -> (0 until a.length()).map { a.opt(it).toString() } } ?: emptyList()
                Choice("Genre", listOf("" to "Any") + genres.map { it to it }, view.genre) { onChange(view.copy(genre = it)) }
                Choice("Parental rating", listOf("" to "Any") + ratings.map { it to it }, view.rating) { onChange(view.copy(rating = it)) }
                Choice("Year", listOf("" to "Any") + years.map { it to it }, view.year) { onChange(view.copy(year = it)) }
            }
        }
    }
}

/** A labelled box that opens a list to pick from, like Emby's. */
@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second ?: value.ifEmpty { "Any" }
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, color = EFg, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FieldBg).border(1.dp, ELine, RoundedCornerShape(12.dp)).clickable { open = true }
                    .semantics(mergeDescendants = true) { role = Role.DropdownList; contentDescription = "$label: $shown" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shown, color = EFg, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Icon(Ic.ChevronDown, null, tint = EFg)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                options.forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(name, fontWeight = if (k == value) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { open = false; onPick(k) },
                        trailingIcon = if (k == value) ({ Icon(Ic.Check, null) }) else null,
                        modifier = Modifier.semantics { selected = k == value })
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onSet: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onSet).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EFg, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = EGreen))
    }
}
