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
import java.text.Normalizer

class LibraryViewModel : ViewModel() {
    private val repo = BasicSheetsApp.instance.library
    private val settings = BasicSheetsApp.instance.settings

    val tab = MutableStateFlow("songs")        // songs | recent | favorites | composer | genre
    val search = MutableStateFlow("")
    val searchField = MutableStateFlow("all")  // all | Name | Composer | Part | Genre | Tags
    val sortOrder = MutableStateFlow("name")   // name | date | composer | rating | difficulty

    private val sheets = repo.observeSheets()
        .catch { emit(emptyList()) }   // a DB error shows an empty list, never a crash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { sortOrder.value = settings.get("Sortierung", "name") } }

    /** The songs to show for the current tab + search (field-scoped) + sort. */
    val shown: StateFlow<List<Sheet>> =
        combine(sheets, tab, search, sortOrder, searchField) { list, tab, query, sort, field ->
            var r = list
            if (query.isNotBlank()) {
                val term = normDia(query)
                fun m(f: String?) = normDia(f).contains(term)   // diacritic- + case-insensitive
                r = r.filter { s ->
                    if (field == "all") {
                        m(s.name) || m(s.composer) || m(s.part) || m(s.genre) || m(s.tags)
                    } else when (field) {
                        "Name" -> m(s.name)
                        "Composer" -> m(s.composer)
                        "Part" -> m(s.part)
                        "Genre" -> m(s.genre)
                        "Tags" -> m(s.tags)
                        else -> true
                    }
                }
            }
            if (tab == "favorites") r = r.filter { it.rating > 0 }
            r = when (tab) {
                "recent" -> r.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt ?: 0L }
                "favorites" -> r.sortedByDescending { it.rating }
                "composer" -> r.sortedBy { it.composer.lowercase() }
                "genre" -> r.sortedBy { it.genre.lowercase() }
                else -> sortSongs(r, sort)
            }
            r
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun sortSongs(list: List<Sheet>, sort: String): List<Sheet> = when (sort) {
        "composer" -> list.sortedWith(compareBy<Sheet> { it.composer.lowercase() }.thenBy { it.name.lowercase() })
        "date" -> list.sortedByDescending { it.updated }
        "rating" -> list.sortedWith(compareByDescending<Sheet> { it.rating }.thenBy { it.name.lowercase() })
        "difficulty" -> list.sortedWith(compareBy<Sheet> { difficultyRank(it.difficulty) }.thenBy { it.name.lowercase() })
        else -> list.sortedBy { it.name.lowercase() }
    }

    private fun difficultyRank(d: String) = when (d) {
        "beginner" -> 1; "intermediate" -> 2; "advanced" -> 3; "expert" -> 4; else -> 9
    }

    /** Diacritic- + case-insensitive normalize (ä→a, ř→r …) so "Dvorak" finds "Dvořák". */
    private fun normDia(s: String?): String =
        Normalizer.normalize(s ?: "", Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

    fun setTab(t: String) { tab.value = t }
    fun setSearch(q: String) { search.value = q }
    fun setSearchField(field: String) { searchField.value = field }
    fun setSortOrder(key: String) {
        sortOrder.value = key
        viewModelScope.launch { settings.set("Sortierung", key) }
    }

    fun importPdf(context: Context, uri: Uri) = viewModelScope.launch { repo.importFile(context, uri) }
    fun importFiles(context: Context, uris: List<Uri>) = viewModelScope.launch { repo.importFiles(context, uris) }
    fun delete(sheet: Sheet) = viewModelScope.launch { repo.delete(sheet) }
    fun rate(sheet: Sheet, stars: Int) = viewModelScope.launch { repo.update(sheet.copy(rating = stars)) }
    fun togglePractice(sheet: Sheet) = viewModelScope.launch { repo.setPractice(sheet, !sheet.inPractice) }

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")
    }
}
