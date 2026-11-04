package com.bestyohman.adplayer

import androidx.compose.foundation.background
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
    val ad: Boolean = false, val genre: String = "", val rating: String = "", val year: String = ""
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
            LibView(j.optString("sort", "title"), j.optBoolean("desc"), j.optString("state"), j.optBoolean("fav"), j.optBoolean("ad"),
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

private val SheetBg = Color(0xFF262626)
private val FieldBg = Color(0xFF3A3A3E)

/** The Sort and Filter buttons above a library, and their sheets. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryControls(lib: String, view: LibView, onChange: (LibView) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val way = LibSorts.way(view)
        IconButton(onClick = { sortOpen = true }, modifier = Modifier.semantics {
            contentDescription = "Sort by ${LibSorts.name(view.sort)}" + if (way.isNotEmpty()) ", $way" else ""
        }) { Icon(Ic.Sort, null, tint = Color.White) }
        Spacer(Modifier.width(24.dp))
        Box {
            IconButton(onClick = { filterOpen = true }, modifier = Modifier.semantics {
                contentDescription = if (view.filtersOn > 0) "Filters, ${view.filtersOn} on" else "Filters"
            }) { Icon(Ic.Filter, null, tint = if (view.filtersOn > 0) EGreen else Color.White) }
            if (view.filtersOn > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(EGreen))
        }
    }

    if (sortOpen) {
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sortOpen = false }, sheetState = st, containerColor = SheetBg) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Sort By", color = ESub, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                LibSorts.all.forEach { (k, name, newestFirst) ->
                    val on = view.sort == k
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            // the one already chosen: the other way round; a new one: its usual way
                            val nv = if (on && k != "random") view.copy(desc = !view.desc) else view.copy(sort = k, desc = newestFirst)
                            onChange(nv); if (!on || k == "random") sortOpen = false
                        }.semantics {
                            role = Role.Button; selected = on
                            stateDescription = if (on) "chosen, " + LibSorts.way(view) + (if (k != "random") ". Tap to sort the other way round" else "") else ""
                        }.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(40.dp)) { if (on) Icon(Ic.Check, null, tint = Color.White) }
                        Text(name, color = Color.White, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        if (on && k != "random") Icon(if (view.desc) Ic.ArrowDown else Ic.ArrowUp, null, tint = ESub)
                    }
                }
            }
        }
    }

    if (filterOpen) {
        val (opts, _) = rememberData("/api/play/filters?parent=${enc(lib)}")
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { filterOpen = false }, sheetState = st, containerColor = SheetBg) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (view.filtersOn > 0) TextButton(onClick = { onChange(LibView(sort = view.sort, desc = view.desc)) }) { Text("Clear all", color = EGreen) }
                }
                Spacer(Modifier.height(8.dp))
                Choice("Playstate", listOf("" to "Any", "unplayed" to "Unplayed", "played" to "Played", "resumable" to "In progress"), view.state) { onChange(view.copy(state = it)) }
                Toggle("Favorite", view.fav) { onChange(view.copy(fav = it)) }
                Toggle("Has audio description", view.ad) { onChange(view.copy(ad = it)) }
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
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(FieldBg).clickable { open = true }
                    .semantics(mergeDescendants = true) { role = Role.DropdownList; contentDescription = "$label: $shown" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shown, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Icon(Ic.ChevronDown, null, tint = Color.White)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                options.forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(name + if (k == value) "  ✓" else "") }, onClick = { open = false; onPick(k) })
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onSet: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSet(!on) }.semantics(mergeDescendants = true) { role = Role.Switch }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = EGreen))
    }
}
