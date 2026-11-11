package ch.desgin.basicsheets.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Sheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    private val repo = BasicSheetsApp.instance.library
    private val settings = BasicSheetsApp.instance.settings

    val tab = MutableStateFlow("songs")        // songs | recent | favorites | composer | genre
    val search = MutableStateFlow("")
    private val sortKey = MutableStateFlow("name")

    private val sheets = repo.observeSheets()
        .catch { emit(emptyList()) }   // a DB error shows an empty list, never a crash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { sortKey.value = settings.get("Sortierung", "name") } }

    /** The songs to show for the current tab + search + sort. */
    val shown: StateFlow<List<Sheet>> =
        combine(sheets, tab, search, sortKey) { list, tab, query, sort ->
            var r = list
            if (query.isNotBlank()) {
                r = r.filter {
                    it.name.contains(query, true) ||
                        it.composer.contains(query, true) ||
                        it.genre.contains(query, true)
                }
            }
            if (tab == "favorites") r = r.filter { it.rating > 0 }
            r = when (tab) {
                "recent" -> r.sortedByDescending { it.updated }
                "favorites" -> r.sortedByDescending { it.rating }
                "composer" -> r.sortedBy { it.composer.lowercase() }
                "genre" -> r.sortedBy { it.genre.lowercase() }
                else -> sortSongs(r, sort)
            }
            r
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun sortSongs(list: List<Sheet>, sort: String): List<Sheet> = when (sort) {
        "composer" -> list.sortedBy { it.composer.lowercase() }
        "date" -> list.sortedByDescending { it.created }
        "difficulty" -> list.sortedBy { difficultyRank(it.difficulty) }
        else -> list.sortedBy { it.name.lowercase() }
    }

    private fun difficultyRank(d: String) = when (d) {
        "beginner" -> 1; "intermediate" -> 2; "advanced" -> 3; "expert" -> 4; else -> 9
    }

    fun setTab(t: String) { tab.value = t }
    fun setSearch(q: String) { search.value = q }

    fun importPdf(context: Context, uri: Uri) = viewModelScope.launch { repo.importPdf(context, uri) }
    fun delete(sheet: Sheet) = viewModelScope.launch { repo.delete(sheet) }
    fun rate(sheet: Sheet, stars: Int) = viewModelScope.launch { repo.update(sheet.copy(rating = stars)) }
}
