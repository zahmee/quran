package com.mushaf.reader.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mushaf.reader.data.recitation.AyahRef

/**
 * Which of the reader's screens, sheets and dialogs are open. They are all overlays inside the
 * one activity: [ReaderScreen] draws the page and [ReaderOverlays] draws whichever of these is
 * set above it. Keeping them in one holder lets both read and change the same state.
 *
 * Plain UI state, gone with the activity, as it was when each lived in its own `remember`.
 */
@Stable
internal class ReaderScreenState {
    var showGoTo by mutableStateOf(false)
    var showStatsScreen by mutableStateOf(false)
    var showKhatmaMap by mutableStateOf(false)
    var showIndex by mutableStateOf(false)
    var indexTab by mutableStateOf(0)
    var showSearch by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showBackup by mutableStateOf(false)
    var showBookmarkManager by mutableStateOf(false)
    var showNotes by mutableStateOf(false)
    var showTafsirLibrary by mutableStateOf(false)
    var showReciterPicker by mutableStateOf(false)
    var headerVisible by mutableStateOf(true)

    /** The place a note is being written on, from the ayah card or the notes list. */
    var noteTarget by mutableStateOf<NoteTarget?>(null)

    /** The ayah the memorisation sheet starts from, while it is open. */
    var repeatFrom by mutableStateOf<AyahRef?>(null)

    /** The share-as-image sheet's choices, while it (or its ayah picker) is open. */
    var share by mutableStateOf<ShareSession?>(null)

    /** The page whose fawaed are open from the header. */
    var fawaedPage by mutableStateOf<Int?>(null)

    /** The surah whose card is open from the index. */
    var surahCardFor by mutableStateOf<SurahEntry?>(null)

    /** Bar-placed header actions the current window has no room for; settings flags them. */
    var barOverflow by mutableStateOf(emptySet<String>())
}

@Composable
internal fun rememberReaderScreenState(): ReaderScreenState = remember { ReaderScreenState() }
