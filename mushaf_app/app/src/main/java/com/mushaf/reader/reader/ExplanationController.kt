package com.mushaf.reader.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.ExplanationTextScale
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.content.CenterExtrasRepository
import com.mushaf.reader.data.content.QuranContentRepository
import com.mushaf.reader.data.content.SurahCard
import com.mushaf.reader.data.content.TajweedNote
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackContentRepository
import com.mushaf.reader.data.packs.PackStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val PACK_ORDER = listOf("ibn_kathir", "tabari", "baghawi", "qurtubi", "shawkani", "ibn_ashur", "irab")

/**
 * The explanation sheet's state: which source it opens on, the sources pinned as its tabs, its
 * text size, and what it shows for one ayah — the bundled tafsirs and word meanings, the center's
 * extras (fawaed, tajweed, surah cards), and a downloaded pack's text. The choices are remembered
 * in [store]; the texts are read on demand and a newer request cancels an older one.
 */
class ExplanationController internal constructor(
    app: Application,
    private val scope: CoroutineScope,
    private val store: ReadingStore,
    private val centerExtras: CenterExtrasRepository,
) {
    private val contentRepo = QuranContentRepository(app)
    private val packRepo = PackContentRepository(app)

    /** The source the explanation sheet last showed, which it opens on next ([ExplanationSources]). */
    var explanationSource by mutableStateOf(ExplanationSources.SAADI)
        private set

    fun updateExplanationSource(source: String) {
        if (source == explanationSource) return
        explanationSource = source
        scope.launch { store.setExplanationSource(source) }
    }

    /** The sources pinned as the sheet's tabs, in order, including pins on packs deleted since. */
    var pinnedSources by mutableStateOf(ExplanationSources.DEFAULT_PINNED)
        private set

    /**
     * Pins or unpins [source]; false when every place is taken. Pins on deleted packs are dropped
     * here, so a place they held is free again — until then they come back with their pack.
     */
    fun togglePinnedSource(source: String): Boolean {
        val shown = ExplanationSources.visiblePinned(pinnedSources, installedPackIds)
        val next = ExplanationSources.togglePin(shown, source) ?: return false
        pinnedSources = next
        scope.launch { store.setPinnedSources(next) }
        return true
    }

    /** The installed packs in the order the sources menu lists them. */
    val installedPacks: List<InstalledPack>
        get() = PackStore.installed.values.sortedBy { PACK_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    private val installedPackIds: Set<String> get() = PackStore.installed.keys

    /** The source the sheet opens on: the last one read, unless its pack was deleted since. */
    fun openingSource(): String = ExplanationSources.opening(explanationSource, installedPackIds)

    /** Text size of the tafsir / word-meanings sheet, percent of the reading size. */
    var explanationTextScale by mutableIntStateOf(ExplanationTextScale.DEFAULT)
        private set

    /** One size up ([direction] > 0) or down; remembered for the next time the sheet opens. */
    fun stepExplanationTextScale(direction: Int) {
        val next = ExplanationTextScale.step(explanationTextScale, direction)
        if (next == explanationTextScale) return
        explanationTextScale = next
        scope.launch { store.setExplanationTextScale(next) }
    }

    var explanationState by mutableStateOf(AyahExplanationUiState())
        private set

    private var explanationJob: Job? = null

    fun loadExplanation(ayah: AyahMarker) {
        explanationJob?.cancel()
        explanationState = AyahExplanationUiState(
            verseKey = ayah.verseKey,
            loading = true,
        )
        explanationJob = scope.launch {
            try {
                val content = contentRepo.explanationFor(ayah.verseKey)
                explanationState = AyahExplanationUiState(
                    verseKey = ayah.verseKey,
                    tafsirHtml = content.tafsirHtml,
                    meanings = content.meanings,
                    saadi = content.saadi,
                    mukhtasar = content.mukhtasar,
                    nozool = content.nozool,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                explanationState = AyahExplanationUiState(
                    verseKey = ayah.verseKey,
                    errorMessage = "تعذر فتح المحتوى. حاول مرة أخرى.",
                )
            }
        }
    }

    /** The fawaed the Mukhtasar draws from [page]; empty when the page has none or cannot be read. */
    suspend fun pageFawaed(page: Int): List<String> =
        runCatching { centerExtras.fawaedForPage(page) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())

    /** The tajweed note on an ayah; null when it has none or the note cannot be read. */
    suspend fun tajweed(surah: Int, ayah: Int): TajweedNote? =
        runCatching { centerExtras.tajweed(surah, ayah) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    /** [surah]'s introduction, or null when it cannot be read. */
    suspend fun surahCard(surah: Int): SurahCard? =
        runCatching { centerExtras.surahCard(surah) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    fun clearExplanation() {
        explanationJob?.cancel()
        explanationJob = null
        explanationState = AyahExplanationUiState()
        packJob?.cancel()
        packJob = null
        packState = PackContentUiState()
    }

    var packState by mutableStateOf(PackContentUiState())
        private set

    private var packJob: Job? = null

    /** Loads [packId]'s text for [verseKey] into [packState], unless it is already there. */
    fun loadPackContent(verseKey: String, packId: String) {
        val current = packState
        if (current.verseKey == verseKey && current.packId == packId && (current.loading || current.content != null)) return
        val pack = PackStore.installed[packId]
        packJob?.cancel()
        if (pack == null) {
            packState = PackContentUiState(verseKey, packId)
            return
        }
        packState = PackContentUiState(verseKey, packId, loading = true)
        packJob = scope.launch {
            val content = try {
                packRepo.contentFor(packId, pack.kind, verseKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            packState = PackContentUiState(verseKey, packId, content = content)
        }
    }

    /** Reads the remembered source, pins and text size. */
    internal fun start() {
        scope.launch {
            explanationSource = store.explanationSource()
            pinnedSources = store.pinnedSources()
            explanationTextScale = store.explanationTextScale()
        }
    }
}
