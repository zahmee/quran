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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.share.ClipExporter
import com.mushaf.reader.share.ShareImageExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share and save for the video tab: the share card held still over the recitation.
 *
 * The video is made when it is asked for, not ahead like the audio: encoding pictures costs far
 * more than cutting sound, and most people looking at the tab will change something first. Once
 * made it is kept for as long as the picture and the clip stay the same, so sharing it and then
 * saving it encodes once.
 *
 * [frame] is the picture for the current options, or null while it is being drawn; [frameKey] says
 * what it shows.
 */
@Composable
internal fun VideoActions(
    clip: AudioClipState,
    frame: Bitmap?,
    frameKey: Any?,
    ayahs: List<AyahMarker>,
    reciter: Reciter?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutines = rememberCoroutineScope()
    val exporter = remember { ClipExporter(context) }
    val audio = clip.status as? AudioClipStatus.Ready
    // Null when idle; -1 while making with no percentage yet, else the percentage.
    var making by remember { mutableStateOf<Int?>(null) }
    var made by remember { mutableStateOf<Pair<Any, File>?>(null) }
    val ready = audio != null && frame != null && making == null

    suspend fun video(): File {
        val sound = checkNotNull(audio)
        val picture = checkNotNull(frame)
        val key = listOf(frameKey, sound.file.path, sound.file.lastModified())
        made?.takeIf { it.first == key && it.second.isFile }?.let { return it.second }
        making = -1
        val still = withContext(Dispatchers.IO) {
            File(context.cacheDir, "video/frame.png").also { f ->
                f.parentFile?.mkdirs()
                f.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val out = withContext(Dispatchers.IO) { ShareImageExport.sharedFile(context, "${sound.name}.mp4") }
        exporter.exportVideo(still, sound.file, sound.durationMs, out) { making = it }
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
            val progress = making
            if (progress != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (progress < 0) "جارٍ صنع الفيديو…" else "جارٍ صنع الفيديو ${progress.toArabicDigits()}٪")
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
