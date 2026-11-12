package com.mushaf.reader.reader

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.share.ClipExporter
import com.mushaf.reader.share.ShareImageExport
import com.mushaf.reader.share.ShareImageRenderer
import com.mushaf.reader.share.ShareScope
import com.mushaf.reader.share.VideoFrame
import com.mushaf.reader.share.VideoPlan
import com.mushaf.reader.ui.theme.MushafPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share and save for the video tab: the share card over the recitation — with [highlight], one
 * picture per ayah, lit while it is recited, each page on its own ([VideoPlan]).
 *
 * The video is made when it is asked for, not ahead like the audio: drawing and encoding pictures
 * cost far more than cutting sound, and most people looking at the tab change something first.
 * Once made it is kept for as long as nothing it shows changes, so sharing it and then saving it
 * makes it once.
 */
@Composable
internal fun VideoActions(
    clip: AudioClipState,
    ayahs: List<AyahMarker>,
    renderer: ShareImageRenderer,
    markersFor: (Int) -> List<AyahMarker>,
    palette: MushafPalette,
    highlight: Boolean,
    reciter: Reciter?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutines = rememberCoroutineScope()
    val exporter = remember { ClipExporter(context) }
    val audio = clip.status as? AudioClipStatus.Ready
    // What the button says while the video is being made; null when idle.
    var making by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<Pair<Any, File>?>(null) }
    val ready = audio != null && making == null

    suspend fun video(): File {
        val sound = checkNotNull(audio)
        val lit = highlight && ayahs.size > 1
        val key = listOf(ayahs.map { it.verseKey }, palette.id, lit, sound.file.path, sound.file.lastModified())
        made?.takeIf { it.first == key && it.second.isFile }?.let { return it.second }

        val segments = VideoPlan.segments(ayahs, sound.timing, sound.range, lit)
        val byKey = ayahs.associateBy { it.verseKey }
        val dir = withContext(Dispatchers.IO) {
            File(context.cacheDir, "video").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
        }
        val stills = segments.mapIndexed { i, segment ->
            making = "جارٍ رسم الصور ${(i + 1).toArabicDigits()} من ${segments.size.toArabicDigits()}"
            val card = renderer.render(
                ShareScope.Ayahs(ayahs.filter { it.page == segment.page }),
                markersFor,
                palette,
                framed = true,
                highlight = segment.highlight?.let(byKey::get),
            )
            val file = File(dir, "frame-$i.png")
            withContext(Dispatchers.Default) {
                val frame = VideoFrame.compose(card, palette.paper.toArgb())
                file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                frame.recycle()
                card.recycle()
            }
            file to segment.durationMs
        }

        making = "جارٍ صنع الفيديو…"
        val out = withContext(Dispatchers.IO) { ShareImageExport.sharedFile(context, "${sound.name}.mp4") }
        exporter.exportVideo(stills, sound.file, out) {
            making = "جارٍ صنع الفيديو ${it.toArabicDigits()}٪"
        }
        made = key to out
        return out
    }

    fun failed(e: Exception) {
        Toast.makeText(context, failureMessage(e).replace("المقطع", "الفيديو"), Toast.LENGTH_LONG).show()
    }

    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = ready,
            onClick = {
                coroutines.launch {
                    try {
                        val file = video()
                        context.startActivity(
                            ShareImageExport.chooser(
                                ShareImageExport.uriFor(context, file),
                                caption(ayahs, reciter),
                                ShareImageExport.VIDEO_MIME,
                            )
                        )
                        onDismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed(e)
                    } finally {
                        making = null
                    }
                }
            },
        ) {
            val label = making
            if (label != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مشاركة")
            }
        }
        if (ShareImageExport.canSaveToGallery) {
            OutlinedButton(
                enabled = ready,
                onClick = {
                    coroutines.launch {
                        val saved = try {
                            val file = video()
                            ShareImageExport.saveVideo(context, file, file.name)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed(e)
                            null
                        } finally {
                            making = null
                        }
                        if (saved != null) {
                            Toast.makeText(
                                context,
                                if (saved) "حُفظ الفيديو في مجلد الأفلام" else "تعذّر حفظ الفيديو",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            ) {
                Icon(Icons.Outlined.Download, contentDescription = "حفظ في مجلد الأفلام", modifier = Modifier.size(18.dp))
            }
        }
    }
}
