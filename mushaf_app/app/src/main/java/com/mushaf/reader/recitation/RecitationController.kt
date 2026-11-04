package com.mushaf.reader.recitation

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mushaf.reader.R
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.recitation.AyahClip
import com.mushaf.reader.data.recitation.AyahRef
import com.mushaf.reader.data.recitation.AyahSteps
import com.mushaf.reader.data.recitation.RecitationRepository
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.data.recitation.RepeatPlan
import com.mushaf.reader.data.recitation.RepeatPlanner
import com.mushaf.reader.data.recitation.SurahTiming
import com.mushaf.reader.reader.toArabicDigits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * The reader's side of recitation: talks to [RecitationService] through a media controller and
 * turns the player's position into "which ayah is being recited", which the page highlights and
 * follows. State is Compose state, so the bar and the page redraw on their own.
 *
 * A recitation is a playlist of whole surahs from the chosen one to An-Nas, so it runs on through
 * the mushaf in the background until stopped. The playlist lives in the service, so it outlasts
 * the activity; [onForeground] reconnects and picks up whatever is playing.
 */
class RecitationController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val repo: RecitationRepository,
    private val store: ReadingStore,
    private val surahName: (Int) -> String,
) {
    /** Complete recitations with ayah timing, Hafs first, then by name. Empty until requested. */
    var reciters by mutableStateOf<List<Reciter>>(emptyList())
        private set
    var reciter by mutableStateOf<Reciter?>(null)
        private set
    var listLoading by mutableStateOf(false)
        private set

    /** A recitation is loaded (playing, paused or failed): the player bar is shown. */
    var active by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** Waiting for the timing table or the audio. */
    var buffering by mutableStateOf(false)
        private set
    /** The ayah being recited; during a surah's opening (isti'adha, basmala) its first ayah. */
    var current by mutableStateOf<AyahRef?>(null)
        private set
    /** The clip playing when a memorisation run (or a single ayah) is on; null in continuous
     *  recitation. Carries which repetition of the ayah and of the passage this is. */
    var clip by mutableStateOf<AyahClip?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Playback speed; the pitch is kept. */
    var speed by mutableFloatStateOf(1f)
        private set
    /** Last repetition counts chosen, offered again the next time (ayah times, passage times). */
    var repeatDefaults by mutableStateOf(7 to 1)
        private set

    private var controller: MediaController? = null
    private var connecting: ListenableFuture<MediaController>? = null
    private val onConnected = mutableListOf<(MediaController) -> Unit>()
    private var released = false
    private var foreground = false
    private var ticker: Job? = null
    private var playJob: Job? = null
    private val timings = HashMap<Pair<Int, Int>, SurahTiming>()
    private val timingLoads = HashMap<Pair<Int, Int>, Job>()
    /** The plan being played, to rebuild it for another reciter. */
    private var plan: RepeatPlan? = null

    init {
        scope.launch {
            store.recitationSpeed()?.let { speed = it }
            store.recitationRepeat()?.let { repeatDefaults = it }
        }
    }

    /** Start reciting at [ref] and carry on through the following surahs. */
    fun playFrom(ref: AyahRef) {
        error = null
        active = true
        buffering = true
        current = ref
        clip = null
        plan = null
        playJob?.cancel()
        playJob = scope.launch {
            val chosen = ensureReciter() ?: run { buffering = false; return@launch }
            val timing = try {
                timingFor(chosen.id, ref.surah)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = NETWORK_ERROR
                buffering = false
                return@launch
            }
            val start = timing.startOf(ref.ayah) ?: 0L
            withController { c ->
                val items = (ref.surah..SurahAyahCounts.SURAH_COUNT).map { mediaItem(chosen, it) }
                c.setMediaItems(items, /* startIndex = */ 0, start)
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.setPlaybackSpeed(speed)
                c.prepare()
                c.play()
            }
        }
    }

    /** Hear [ref] alone, then stop. */
    fun listenTo(ref: AyahRef) = playPlan(RepeatPlan.single(ref))

    /**
     * Play a memorisation run: each ayah of the passage cut out of the surah file as its own clip,
     * repeated as [RepeatPlan] says. The whole run is the playlist, so it keeps its count in the
     * background even if the activity is gone; an endless run loops the playlist.
     */
    fun playPlan(passage: RepeatPlan) {
        error = null
        active = true
        buffering = true
        current = AyahRef(passage.surah, passage.fromAyah)
        clip = null
        plan = passage
        if (passage.ayahTimes > 1 || passage.rangeTimes != 1) {
            repeatDefaults = passage.ayahTimes to passage.rangeTimes
            scope.launch { store.setRecitationRepeat(passage.ayahTimes, passage.rangeTimes) }
        }
        playJob?.cancel()
        playJob = scope.launch {
            val chosen = ensureReciter() ?: run { buffering = false; return@launch }
            val timing = try {
                timingFor(chosen.id, passage.surah)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = NETWORK_ERROR
                buffering = false
                return@launch
            }
            if (RepeatPlanner.clipCount(passage) > RepeatPlanner.MAX_CLIPS) {
                error = TOO_LONG_ERROR
                buffering = false
                return@launch
            }
            val clips = RepeatPlanner.clips(passage, timing, chosen.id)
            if (clips.isEmpty()) {
                error = PLAYBACK_ERROR
                buffering = false
                return@launch
            }
            withController { c ->
                c.setMediaItems(clips.map { clipItem(chosen, it) }, /* startIndex = */ 0, 0L)
                c.repeatMode = if (passage.endless) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                c.setPlaybackSpeed(speed)
                c.prepare()
                c.play()
            }
        }
    }

    fun changeSpeed(value: Float) {
        speed = value
        controller?.setPlaybackSpeed(value)
        scope.launch { store.setRecitationSpeed(value) }
    }

    fun togglePlay() {
        val c = controller
        val at = current
        when {
            c == null || c.mediaItemCount == 0 || c.playerError != null ->
                if (at != null) playFrom(at)
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0, 0L)
                c.play()
            }
            else -> c.play()
        }
    }

    fun next() {
        if (stepClip(forward = true)) return
        val at = current ?: return
        val target = AyahSteps.next(at) ?: return
        seekOrRestart(at, target)
    }

    fun previous() {
        if (stepClip(forward = false)) return
        val at = current ?: return
        val target = AyahSteps.previous(at) ?: return
        seekOrRestart(at, target)
    }

    /** In a memorisation run, next/previous move by ayah, skipping the remaining repetitions.
     *  Returns false when the playlist is not a run. */
    private fun stepClip(forward: Boolean): Boolean {
        val c = controller ?: return false
        if (AyahClip.fromMediaId(c.currentMediaItem?.mediaId) == null) return false
        val clips = (0 until c.mediaItemCount).map { AyahClip.fromMediaId(c.getMediaItemAt(it).mediaId) }
        val index = c.currentMediaItemIndex
        val target = if (forward) RepeatPlanner.nextAyahStart(clips, index)
        else RepeatPlanner.previousAyahStart(clips, index)
        if (target != null) c.seekTo(target, 0L)
        return true
    }

    /** Stop and hide the bar. */
    fun close() {
        playJob?.cancel()
        controller?.run {
            stop()
            clearMediaItems()
        }
        active = false
        isPlaying = false
        buffering = false
        current = null
        clip = null
        plan = null
        error = null
        updateTicker()
    }

    fun requestReciters() {
        scope.launch { loadReciters() }
    }

    fun selectReciter(choice: Reciter) {
        if (choice.id == reciter?.id) return
        reciter = choice
        scope.launch { store.setRecitationReciterId(choice.id) }
        val at = current
        if (!active || at == null) return
        // A run carries on from the ayah it had reached, with the same counts.
        val passage = plan ?: controller?.let { planFromPlaylist(it) }
        if (passage != null && clip != null) {
            playPlan(passage.copy(fromAyah = at.ayah.coerceIn(passage.fromAyah, passage.toAyah)))
        } else {
            playFrom(at)
        }
    }

    /** Rebuilds the plan of a run this controller did not start (it outlived the activity). */
    private fun planFromPlaylist(c: MediaController): RepeatPlan? {
        if (c.mediaItemCount == 0) return null
        val first = AyahClip.fromMediaId(c.getMediaItemAt(0).mediaId) ?: return null
        val last = AyahClip.fromMediaId(c.getMediaItemAt(c.mediaItemCount - 1).mediaId) ?: return null
        return RepeatPlan(first.surah, first.ayah, last.ayah, first.ayahTimes, first.rangeTimes)
    }

    /** The reader is on screen: reconnect (picking up background playback) and follow along. */
    fun onForeground() {
        foreground = true
        withController { }
        updateTicker()
    }

    fun onBackground() {
        foreground = false
        updateTicker()
    }

    fun release() {
        released = true
        ticker?.cancel()
        playJob?.cancel()
        controller?.removeListener(listener)
        connecting?.let { MediaController.releaseFuture(it) }
        controller = null
        connecting = null
    }

    // ---------------------------------------------------------------------------------------

    private fun seekOrRestart(at: AyahRef, target: AyahRef) {
        val c = controller
        val id = reciter?.id
        val sameSurahStart = if (target.surah == at.surah && id != null) {
            timings[id to target.surah]?.startOf(target.ayah)
        } else null
        when {
            c != null && c.playerError == null && sameSurahStart != null -> {
                c.seekTo(sameSurahStart)
                current = target
            }
            // Forward into the next surah: it is already the next playlist item.
            c != null && c.playerError == null && target.surah == at.surah + 1 &&
                target.ayah == 1 && c.hasNextMediaItem() -> {
                c.seekTo(c.nextMediaItemIndex, 0L)
                current = target
            }
            else -> playFrom(target)
        }
    }

    private suspend fun ensureReciter(): Reciter? {
        reciter?.let { return it }
        val list = loadReciters() ?: return null
        val saved = store.recitationReciterId()
        val chosen = list.firstOrNull { it.id == saved }
            ?: list.firstOrNull { it.id == DEFAULT_RECITER_ID }
            ?: list.firstOrNull()
            ?: return null.also { error = LIST_ERROR }
        reciter = chosen
        return chosen
    }

    private suspend fun loadReciters(): List<Reciter>? {
        if (reciters.isNotEmpty()) return reciters
        listLoading = true
        return try {
            val collator = Collator.getInstance(Locale.forLanguageTag("ar"))
            repo.reciters()
                .filter { it.complete }
                .sortedWith(
                    compareBy<Reciter> { it.rewaya != HAFS }
                        .thenBy(collator) { it.name }
                )
                .also { reciters = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = LIST_ERROR
            null
        } finally {
            listLoading = false
        }
    }

    private suspend fun timingFor(reciterId: Int, surah: Int): SurahTiming {
        timings[reciterId to surah]?.let { return it }
        return repo.timing(reciterId, surah).also { timings[reciterId to surah] = it }
    }

    /** Fetches a table in the background, at most once at a time per reciter and surah. */
    private fun loadTimingLater(reciterId: Int, surah: Int) {
        val key = reciterId to surah
        if (timings.containsKey(key) || timingLoads[key]?.isActive == true) return
        timingLoads[key] = scope.launch {
            runCatching { timingFor(reciterId, surah) }
            timingLoads.remove(key)
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        if (released) return
        controller?.let { block(it); return }
        onConnected += block
        if (connecting != null) return
        val token = SessionToken(app, ComponentName(app, RecitationService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        connecting = future
        future.addListener({
            if (released) return@addListener
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                connecting = null
                onConnected.clear()
                error = SERVICE_ERROR
                buffering = false
                return@addListener
            }
            controller = c
            c.addListener(listener)
            adoptPlayerState(c)
            val waiting = onConnected.toList()
            onConnected.clear()
            waiting.forEach { it(c) }
        }, ContextCompat.getMainExecutor(app))
    }

    /** After (re)connecting: show the bar for a recitation that kept playing without us. */
    private fun adoptPlayerState(c: MediaController) {
        val id = c.currentMediaItem?.mediaId
        val playing = parseMediaId(id) ?: AyahClip.fromMediaId(id)?.let { it.reciterId to it.surah }
            ?: return
        active = true
        isPlaying = c.isPlaying
        buffering = c.playbackState == Player.STATE_BUFFERING
        speed = c.playbackParameters.speed
        if (reciter?.id != playing.first) {
            scope.launch {
                loadReciters()?.firstOrNull { it.id == playing.first }?.let { reciter = it }
            }
        }
        updatePosition()
        updateTicker()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) error = null
            updatePosition()
            updateTicker()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) isPlaying = false
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePosition()
        }

        override fun onPlayerError(e: PlaybackException) {
            buffering = false
            isPlaying = false
            error = if (e.errorCode in NETWORK_CODES) NETWORK_ERROR else PLAYBACK_ERROR
        }
    }

    private fun updateTicker() {
        val follow = foreground && controller != null && active && isPlaying
        if (follow && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    updatePosition()
                    delay(TICK_MS)
                }
            }
        } else if (!follow) {
            ticker?.cancel()
            ticker = null
        }
    }

    private fun updatePosition() {
        val c = controller ?: return
        // A clip names its ayah outright.
        AyahClip.fromMediaId(c.currentMediaItem?.mediaId)?.let { now ->
            if (now != clip) clip = now
            if (now.ref != current) current = now.ref
            return
        }
        clip = null
        val (reciterId, surah) = parseMediaId(c.currentMediaItem?.mediaId) ?: return
        val timing = timings[reciterId to surah]
        if (timing == null) {
            loadTimingLater(reciterId, surah)
            if (current?.surah != surah) current = AyahRef(surah, 1)
            return
        }
        val ref = AyahRef(surah, timing.ayahAt(c.currentPosition) ?: 1)
        if (ref != current) current = ref
        // Have the next surah's table ready before playback reaches it.
        if (ref.ayah >= SurahAyahCounts.of(surah) - 2 && surah < SurahAyahCounts.SURAH_COUNT) {
            loadTimingLater(reciterId, surah + 1)
        }
    }

    private fun mediaItem(reciter: Reciter, surah: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId("${reciter.id}:$surah")
            .setUri(reciter.surahUrl(surah))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("سورة ${surahName(surah)}")
                    .setArtist(reciter.name)
                    .setAlbumTitle(app.getString(R.string.app_name))
                    .setTrackNumber(surah)
                    .build()
            )
            .build()

    private fun clipItem(reciter: Reciter, clip: AyahClip): MediaItem =
        MediaItem.Builder()
            .setMediaId(clip.mediaId())
            .setUri(reciter.surahUrl(clip.surah))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("سورة ${surahName(clip.surah)} • الآية ${clip.ayah.toArabicDigits()}")
                    .setArtist(reciter.name)
                    .setAlbumTitle(app.getString(R.string.app_name))
                    .build()
            )
            .build()

    /** Continuous recitation's media id: "reciter:surah". */
    private fun parseMediaId(id: String?): Pair<Int, Int>? {
        val parts = id?.split(':') ?: return null
        if (parts.size != 2) return null
        val reciterId = parts[0].toIntOrNull() ?: return null
        val surah = parts[1].toIntOrNull()?.takeIf { it in 1..SurahAyahCounts.SURAH_COUNT } ?: return null
        return reciterId to surah
    }

    companion object {
        /** Mishary Alafasy, Hafs: its timing landed in the pause at every boundary we sampled. */
        const val DEFAULT_RECITER_ID = 123
        private const val HAFS = "حفص عن عاصم"
        private const val TICK_MS = 200L
        private const val NETWORK_ERROR = "تعذّر تحميل التلاوة. تحقّق من الاتصال بالإنترنت."
        private const val LIST_ERROR = "تعذّر تحميل قائمة القرّاء. تحقّق من الاتصال بالإنترنت."
        private const val PLAYBACK_ERROR = "تعذّر تشغيل التلاوة."
        private const val SERVICE_ERROR = "تعذّر تشغيل خدمة التلاوة."
        private const val TOO_LONG_ERROR = "المقطع طويل على هذا العدد من التكرار. قلّل الآيات أو مرات التكرار."
        private val NETWORK_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }
}
