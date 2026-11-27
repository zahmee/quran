package com.mushaf.reader.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mushaf.reader.data.RootSearch

/** Results together with the query they answer, so the labels describe that query and not one the
 *  reader has typed since. */
internal class Answer(val query: String, val results: SearchResults)

/**
 * What the search screen shows, kept while the screen is closed so a reader who opens a result
 * and comes back finds the same word, the same results and the same place in the list.
 *
 * Lives in [ReaderScreenState]: gone with the activity, not saved across launches.
 */
@Stable
internal class SearchSession {
    var query by mutableStateOf("")
    var answer by mutableStateOf(Answer("", SearchResults()))

    /** The root whose words are listed, over the results it was chosen from; Back returns to them. */
    var openRoot by mutableStateOf<RootSearch.Root?>(null)

    /** The form chip chosen in [openRoot]'s list, null for «الكل». */
    var rootForm by mutableStateOf<Int?>(null)

    /** The last root list loaded, so coming back does not reload it and lose the scroll. */
    var rootResults by mutableStateOf<RootResults?>(null)

    val listState = LazyListState()
    var rootListState = LazyListState()
        private set

    /** The row the reader last opened, marked when they come back to the list. */
    var openedKey by mutableStateOf<String?>(null)

    /** The page a result was opened on; the reader offers a way back to the results near it. */
    var returnPage by mutableStateOf<Int?>(null)

    fun showRoot(root: RootSearch.Root) {
        openRoot = root
        rootForm = null
        rootResults = null
        rootListState = LazyListState()
    }

    /** Remember that the reader left for [page] from the row [key]. */
    fun opened(key: String, page: Int) {
        openedKey = key
        returnPage = page
    }

    /** Start over: empty box, no results, no way back. */
    fun clear() {
        query = ""
        answer = Answer("", SearchResults())
        openRoot = null
        rootResults = null
        openedKey = null
        returnPage = null
    }
}

/** How far the reader may page away from an opened result before the way back to the results goes. */
internal const val SearchReturnReach = 2
