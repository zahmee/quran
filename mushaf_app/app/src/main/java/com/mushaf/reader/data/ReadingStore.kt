package com.mushaf.reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "reading")

/** Persists the last-read page (continue reading) and the set of bookmarked verse keys. */
class ReadingStore(private val context: Context) {

    private val keyLastPage = intPreferencesKey("last_page")
    private val keyBookmarks = stringSetPreferencesKey("bookmarks")

    suspend fun lastPage(): Int = context.dataStore.data.first()[keyLastPage] ?: 1

    suspend fun setLastPage(page: Int) {
        context.dataStore.edit { it[keyLastPage] = page }
    }

    suspend fun bookmarks(): Set<String> = context.dataStore.data.first()[keyBookmarks] ?: emptySet()

    suspend fun setBookmarks(values: Set<String>) {
        context.dataStore.edit { it[keyBookmarks] = values }
    }
}
