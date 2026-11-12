package com.mushaf.reader.reader

import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.data.recitation.SurahTiming
import com.mushaf.reader.recitation.RecitationStorage
import com.mushaf.reader.share.ClipExporter
import com.mushaf.reader.share.AudioClips
import com.mushaf.reader.share.ClipRange
import com.mushaf.reader.share.ShareImageExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** What the share sheet needs of the recitation, so it does not hold the controller itself. */
internal class RecitationAccess(
    /** The reader's reciter; null until the list has loaded once. */
    val reciter: Reciter?,
    /** Settles on the saved (or default) reciter when none is loaded yet. */
    val prepare: () -> Unit,
    /** Opens the reciter list; the choice becomes the reader's reciter too. */
    val pick: () -> Unit,
    val timing: suspend (Reciter, Int) -> SurahTiming,
)

/** Where the audio clip stands. */
internal sealed interface AudioClipStatus {
    data object WaitingForReciter : AudioClipStatus
    data class Preparing(val percent: Int?) : AudioClipStatus
    /** [timing] and [range] say where each ayah falls in the clip — the video lights them by it. */
    data class Ready(
        val file: File,
        val name: String,
        val durationMs: Long,
        val timing: SurahTiming,
        val range: ClipRange,
    ) : AudioClipStatus
    data class Failed(val message: String) : AudioClipStatus
}

@Stable
internal class AudioClipState {
    var status by mutableStateOf<AudioClipStatus>(AudioClipStatus.WaitingForReciter)
    /** The run starts at ayah 1 and the reciter opens the surah before it: offer the opening. */
    var offersOpening by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)
}

/**
 * Cuts [ayahs] from [access]'s reciter as soon as the audio tab shows them, and again whenever the
 * ayahs, the reciter or the opening choice change — the clip on offer is always the one to send.
 */
@Composable
internal fun rememberAudioClip(
    ayahs: List<AyahMarker>,
    withOpening: Boolean,
    access: RecitationAccess,
): AudioClipState {
    val context = LocalContext.current
    val state = remember { AudioClipState() }
    val exporter = remember { ClipExporter(context) }
    val reciter = access.reciter

    LaunchedEffect(Unit) { if (reciter == null) access.prepare() }

    val first = ayahs.first()
    val last = ayahs.last()
    LaunchedEffect(reciter?.id, first.verseKey, last.verseKey, withOpening, state.attempt) {
        if (reciter == null) {
            state.status = AudioClipStatus.WaitingForReciter
            return@LaunchedEffect
        }
        state.status = AudioClipStatus.Preparing(null)
        try {
            val timing = access.timing(reciter, first.surahNumber)
            state.offersOpening = first.ayahNumber == 1 && AudioClips.hasOpening(timing)
            val range = AudioClips.range(
                timing, first.ayahNumber, last.ayahNumber, withOpening && state.offersOpening,
            )
            if (range == null) {
                state.status = AudioClipStatus.Failed("لا يتوفر توقيت هذه الآيات عند هذا القارئ.")
                return@LaunchedEffect
            }
            if (range.durationMs > AudioClips.MAX_DURATION_MS) {
                state.status = AudioClipStatus.Failed("المقطع أطول من عشرين دقيقة. اختر آيات أقل.")
                return@LaunchedEffect
            }
            val name = clipName(ayahs, reciter)
            val file = withContext(Dispatchers.IO) { ShareImageExport.sharedFile(context, "$name.m4a") }
            exporter.exportAudio(reciter, first.surahNumber, range, file) {
                state.status = AudioClipStatus.Preparing(it)
            }
            state.status = AudioClipStatus.Ready(file, name, range.durationMs, timing, range)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.status = AudioClipStatus.Failed(failureMessage(e))
        }
    }
    return state
}

/** "البقرة ٢٥٥–٢٥٧ - مشاري العفاسي": what the receiver sees as the file's name. */
internal fun clipName(ayahs: List<AyahMarker>, reciter: Reciter): String {
    val label = spanLabel(ayahs).replace(" • الآيات", "").replace(" • الآية", "")
    return "$label - ${reciter.name}".replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
}

internal fun failureMessage(e: Throwable): String {
    val causes = generateSequence(e) { it.cause }.toList()
    return when {
        causes.any { it is RecitationStorage.MobileDataBlockedException } ->
            "أنت على بيانات الجوال، والتلاوة لا تُجلب عليها إلا إن سمحت بذلك في إعدادات التلاوة. " +
                "السور المحمّلة تعمل بلا إنترنت."
        causes.any { it is IOException } ->
            "تعذّر جلب التلاوة. تحقّق من الاتصال، أو حمّل السورة لتعمل بلا إنترنت."
        else -> "تعذّر تجهيز المقطع."
    }
}

/** The clip as it will be sent: played here, or the state of its making. */
@Composable
internal fun AudioClipCard(state: AudioClipState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state.status) {
            AudioClipStatus.WaitingForReciter -> Status(progress = true, text = "جارٍ تحميل القرّاء…")
            is AudioClipStatus.Preparing -> Status(
                progress = true,
                text = "جارٍ تجهيز المقطع" + (s.percent?.let { " ${it.toArabicDigits()}٪" } ?: "…"),
            )
            is AudioClipStatus.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { state.attempt++ }) { Text("إعادة المحاولة") }
            }
            is AudioClipStatus.Ready -> ClipPlayer(s.file, s.durationMs)
        }
    }
}

@Composable
private fun Status(progress: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Plays the finished file. Its own player, asking for audio focus as the recitation's does, so
 * starting it pauses a recitation that is playing.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ClipPlayer(file: File, durationMs: Long) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
    }
    var playing by remember(player) { mutableStateOf(false) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var dragging by remember(player) { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, playing) {
        while (true) {
            positionMs = player.currentPosition
            if (!playing) break
            delay(200)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledIconButton(onClick = { if (playing) player.pause() else player.play() }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "إيقاف مؤقت" else "استماع إلى المقطع",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Slider(
                value = dragging ?: fraction.coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo((it * durationMs).toLong()) }
                    dragging = null
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(clock(durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "٢:٠٥" */
private fun clock(ms: Long): String {
    val s = ms / 1000
    return "${(s / 60).toInt().toArabicDigits()}:${String.format(Locale.ROOT, "%02d", s % 60).toArabicDigits()}"
}

/** The reciter and, where there is one, the surah's opening. */
@Composable
internal fun AudioOptions(
    state: AudioClipState,
    access: RecitationAccess,
    surah: Int,
    withOpening: Boolean,
    onWithOpening: (Boolean) -> Unit,
) {
    Text(
        text = "القارئ",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(access.reciter?.name ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            access.reciter?.rewaya?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = access.pick) { Text("تغيير") }
    }
    if (state.offersOpening) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Al-Fatiha's first ayah is the basmala, and At-Tawbah has none: only the isti'adha.
                Text(
                    if (surah == 1 || surah == 9) "مع الاستعاذة" else "مع الاستعاذة والبسملة",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "ما يقرؤه القارئ قبل الآية الأولى.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = withOpening, onCheckedChange = onWithOpening)
        }
    }
}

/** Share and save, for the finished clip. */
@Composable
internal fun AudioActions(
    state: AudioClipState,
    ayahs: List<AyahMarker>,
    reciter: Reciter?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutines = rememberCoroutineScope()
    val ready = state.status as? AudioClipStatus.Ready
    var busy by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = ready != null && !busy,
            onClick = {
                val clip = ready ?: return@Button
                try {
                    val uri = ShareImageExport.uriFor(context, clip.file)
                    context.startActivity(
                        ShareImageExport.chooser(uri, caption(ayahs, reciter), ShareImageExport.AUDIO_MIME)
                    )
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "تعذّر مشاركة المقطع", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (ready != null) "مشاركة • ${size(ready.file)}" else "مشاركة")
        }
        if (ShareImageExport.canSaveToGallery) {
            OutlinedButton(
                enabled = ready != null && !busy,
                onClick = {
                    val clip = ready ?: return@OutlinedButton
                    busy = true
                    coroutines.launch {
                        val saved = try {
                            ShareImageExport.saveAudio(context, clip.file, "${clip.name}.m4a")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        } finally {
                            busy = false
                        }
                        Toast.makeText(
                            context,
                            if (saved) "حُفظ المقطع في مجلد الموسيقى" else "تعذّر حفظ المقطع",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Icon(Icons.Outlined.Download, contentDescription = "حفظ في مجلد الموسيقى", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * What goes with the file: the reference, the reciter, and where the recording comes from — the
 * recording is MP3Quran's, so it travels with its source named.
 */
internal fun caption(ayahs: List<AyahMarker>, reciter: Reciter?): String = buildString {
    append("﴿${spanLabel(ayahs)}﴾")
    if (reciter != null) {
        append("\nبصوت ${reciter.name}")
        if (reciter.rewaya.isNotBlank()) append(" — ${reciter.rewaya}")
    }
    append("\nالتلاوة من mp3quran.net")
}

/** "١٫٢ م.ب" */
private fun size(file: File): String {
    val mb = file.length() / (1024.0 * 1024.0)
    val text = if (mb < 10) String.format(Locale.ROOT, "%.1f", mb) else mb.toInt().toString()
    return "${text.replace('.', '٫').toArabicDigits()} م.ب"
}
