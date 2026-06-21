package com.mushaf.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "reading")

/** Persists the last-read page (continue reading), display settings (dark theme,
 *  fill-screen), and the set of bookmarked verse keys, so the app reopens as it was left. */
class ReadingStore(private val context: Context) {

    private val keyLastPage = intPreferencesKey("last_page")
    private val keyBookmarks = stringSetPreferencesKey("bookmarks")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")
    private val keyFillScreen = booleanPreferencesKey("fill_screen")
    private val keyVisitedPages = stringSetPreferencesKey("visited_pages")
    private val keyReadPages = stringSetPreferencesKey("read_pages")
    private val keyHiddenButtons = stringSetPreferencesKey("hidden_header_buttons")
    private val keyBigButtons = booleanPreferencesKey("big_buttons")
    private val keyShowClock = booleanPreferencesKey("show_clock")
    private val keyShowSessionTimer = booleanPreferencesKey("show_session_timer")

    /** The display/position settings + per-page progress read together in one pass at startup.
     *  [visitedPages] = pages opened at all; [readPages] = pages dwelt on long enough to count
     *  as read (read ⊆ visited). Drives the khatma map. */
    data class Settings(
        val lastPage: Int,
        val darkTheme: Boolean,
        val fillScreen: Boolean,
        val visitedPages: Set<Int>,
        val readPages: Set<Int>,
        val hiddenButtons: Set<String>,
        val bigButtons: Boolean,
        val showClock: Boolean,
        val showSessionTimer: Boolean,
    )

    suspend fun settings(): Settings {
        val prefs = context.dataStore.data.first()
        return Settings(
            lastPage = prefs[keyLastPage] ?: 1,
            darkTheme = prefs[keyDarkTheme] ?: false,
            fillScreen = prefs[keyFillScreen] ?: false,
            visitedPages = prefs[keyVisitedPages].toIntSet(),
            readPages = prefs[keyReadPages].toIntSet(),
            hiddenButtons = prefs[keyHiddenButtons] ?: emptySet(),
            bigButtons = prefs[keyBigButtons] ?: false,
            showClock = prefs[keyShowClock] ?: false,
            showSessionTimer = prefs[keyShowSessionTimer] ?: false,
        )
    }

    private fun Set<String>?.toIntSet(): Set<Int> =
        this?.mapNotNullTo(HashSet()) { it.toIntOrNull() } ?: emptySet()

    suspend fun setVisitedPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyVisitedPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setReadPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyReadPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setLastPage(page: Int) {
        context.dataStore.edit { it[keyLastPage] = page }
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.dataStore.edit { it[keyDarkTheme] = value }
    }

    suspend fun setFillScreen(value: Boolean) {
        context.dataStore.edit { it[keyFillScreen] = value }
    }

    suspend fun setHiddenButtons(values: Set<String>) {
        context.dataStore.edit { it[keyHiddenButtons] = values }
    }

    suspend fun setBigButtons(value: Boolean) {
        context.dataStore.edit { it[keyBigButtons] = value }
    }

    suspend fun setShowClock(value: Boolean) {
        context.dataStore.edit { it[keyShowClock] = value }
    }

    suspend fun setShowSessionTimer(value: Boolean) {
        context.dataStore.edit { it[keyShowSessionTimer] = value }
    }

    suspend fun bookmarks(): Set<String> = context.dataStore.data.first()[keyBookmarks] ?: emptySet()

    suspend fun setBookmarks(values: Set<String>) {
        context.dataStore.edit { it[keyBookmarks] = values }
    }
}
