package com.mushaf.reader.reader

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.share.ShareImageExport
import com.mushaf.reader.share.ShareImageRenderer
import com.mushaf.reader.share.ShareScope
import com.mushaf.reader.share.VideoFrame
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.theme.MushafPalettes
import com.mushaf.reader.ui.theme.paletteFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the share holds. */
internal enum class ShareKind { Ayah, Span, Page }

/**
 * What is sent: the printed page as an image, the recitation as audio, or the two together as a
 * video — the share card held still over the recitation.
 */
internal enum class ShareFormat(val label: String) { Image("صورة"), Audio("صوت"), Video("فيديو") }

/**
 * One share, from the moment it is opened until it is sent or dismissed.
 *
 * Held by the reader rather than by the sheet, because picking a run of ayahs takes the sheet off
 * the screen for [AyahRangePicker] and brings it back — and the choices made before must survive.
 *
 * [ayah] is the long-pressed ayah when opened from the ayah card, null from the header's "share
 * page", which then offers the page and runs of ayahs only.
 */
@Stable
internal class ShareSession(val page: Int, val ayah: AyahMarker?, readingPaletteId: String) {
    var kind by mutableStateOf(if (ayah != null) ShareKind.Ayah else ShareKind.Page)
    var paletteId by mutableStateOf(readingPaletteId)
    var framed by mutableStateOf(true)
    /** The chosen run once one has been picked — on this page or running over the next ones. */
    var run by mutableStateOf<List<AyahMarker>?>(null)
    /** True while [AyahRangePicker] is up in place of the sheet. */
    var picking by mutableStateOf(false)
    var format by mutableStateOf(ShareFormat.Image)
    /** Audio from ayah 1: take in the isti'adha and basmala the reciter opens the surah with. */
    var withOpening by mutableStateOf(true)
    /** Video of several ayahs: light each while it is recited. */
    var highlight by mutableStateOf(true)

    /**
     * The ayahs the audio holds. One recording is one surah, so "the page" here is the page's
     * ayahs of one surah: the long-pressed ayah's, else the first on the page.
     */
    fun audioAyahs(pageMarkers: List<AyahMarker>): List<AyahMarker> {
        val r = run
        return when {
            kind == ShareKind.Ayah && ayah != null -> listOf(ayah)
            kind == ShareKind.Span && !r.isNullOrEmpty() -> r
            else -> {
                val surah = ayah?.surahNumber ?: pageMarkers.firstOrNull()?.surahNumber
                pageMarkers.filter { it.surahNumber == surah }
            }
        }
    }

    /** Where the picker starts: the run picked before, else the long-pressed ayah, else the
     *  page's first. Empty for a page without ayahs, which offers no picker. */
    fun initialRun(pageMarkers: List<AyahMarker>): List<AyahMarker> =
        run ?: listOfNotNull(ayah).ifEmpty { pageMarkers.take(1) }

    fun scope(): ShareScope {
        val r = run
        return when {
            kind == ShareKind.Ayah && ayah != null -> ShareScope.Ayahs(listOf(ayah))
            kind == ShareKind.Span && !r.isNullOrEmpty() -> ShareScope.Ayahs(r)
            else -> ShareScope.Page(page)
        }
    }
}

/**
 * Share an ayah, a run of ayahs, or the whole page — as an image cut from the printed page, or as
 * audio cut from the reciter's recording.
 *
 * The preview is the very file that will be sent, made again as the options change; a previous
 * image stays up meanwhile so the sheet does not flicker.
 *
 * The ayahs' text can still be sent as text from here — that was the card's share button before
 * this sheet existed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ShareImageSheet(
    session: ShareSession,
    markersFor: (Int) -> List<AyahMarker>,
    recitation: RecitationAccess,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember { ShareImageRenderer(context) }
    val coroutines = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val page = session.page
    val pageMarkers = remember(page) { markersFor(page) }

    // The image to send, and a smaller copy to show: a run over three pages is several thousand
    // pixels tall, more than a screen needs and more than every GPU will take as one texture.
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val scope = session.scope()
    val ayahs = (scope as? ShareScope.Ayahs)?.ayahs
    val pageLabel = "صفحة ${page.toArabicDigits()}"
    val reference = if (ayahs != null) {
        "﴿${spanLabel(ayahs)}﴾"
    } else {
        val surahs = pageMarkers.map { it.surahNameAr }.distinct().joinToString("، ")
        if (surahs.isBlank()) "﴿$pageLabel﴾" else "﴿$pageLabel • $surahs﴾"
    }
    val fileName = if (ayahs != null) {
        val a = ayahs.first()
        val z = ayahs.last()
        if (a == z) "natlu-${a.surahNumber}-${a.ayahNumber}"
        else "natlu-${a.surahNumber}-${a.ayahNumber}-${z.ayahNumber}"
    } else {
        "natlu-page-$page"
    }

    val format = session.format
    val audioAyahs = session.audioAyahs(pageMarkers)
    // The recitation is cut on the audio and the video tab alike, and a switch between the two
    // keeps the clip already made.
    val clip = if (format != ShareFormat.Image && audioAyahs.isNotEmpty()) {
        rememberAudioClip(audioAyahs, session.withOpening, recitation)
    } else null

    // What is drawn: the image tab's choice; for the video, its first picture — the first page of
    // the audio's ayahs, the first of them lit — always framed: the frame carries the reference,
    // which a video has nowhere else to show.
    val videoHighlight = session.highlight && audioAyahs.size > 1
    val firstPage = audioAyahs.firstOrNull()?.page
    val drawScope: ShareScope? = when (format) {
        ShareFormat.Image -> scope
        ShareFormat.Video -> audioAyahs.filter { it.page == firstPage }.takeIf { it.isNotEmpty() }
            ?.let { ShareScope.Ayahs(it) }
        ShareFormat.Audio -> null
    }
    val drawHighlight = if (format == ShareFormat.Video && videoHighlight) audioAyahs.firstOrNull() else null
    val drawFramed = format == ShareFormat.Video || session.framed
    val drawKey = listOf(drawScope, session.paletteId, drawFramed, format == ShareFormat.Video, drawHighlight?.verseKey)
    // The picture on show may still be the one before a change; only this one may be sent.
    var imageKey by remember { mutableStateOf<Any?>(null) }
    val imageReady = image != null && imageKey == drawKey && !rendering

    LaunchedEffect(drawKey) {
        val target = drawScope ?: return@LaunchedEffect
        rendering = true
        try {
            val palette = paletteFor(session.paletteId)
            val card = renderer.render(target, markersFor, palette, drawFramed, drawHighlight)
            val shown = if (format == ShareFormat.Video) {
                withContext(Dispatchers.Default) { VideoFrame.compose(card, palette.paper.toArgb()) }
            } else card
            val small = withContext(Dispatchers.Default) { shown.forPreview() }
            image = shown
            preview = small
            imageKey = drawKey
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            rendering = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // The options scroll; the buttons stay pinned below them, in reach on any screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "مشاركة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        format != ShareFormat.Image && audioAyahs.isNotEmpty() -> "سورة ${spanLabel(audioAyahs)}"
                        ayahs != null -> "سورة ${spanLabel(ayahs)}"
                        else -> pageLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (pageMarkers.isNotEmpty()) {
                    MushafSegmentedTabs(
                        labels = ShareFormat.entries.map { it.label },
                        selectedIndex = format.ordinal,
                        onSelected = { session.format = ShareFormat.entries[it] },
                    )
                }

                if (format != ShareFormat.Audio) {
                    Preview(image = preview, rendering = rendering, failed = failed)
                }
                if (format == ShareFormat.Video && audioAyahs.map { it.page }.distinct().size > 1) {
                    Text(
                        text = "في الفيديو تظهر كل صفحة وحدها حين تصل التلاوة إليها، وهذه أولاها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (clip != null) AudioClipCard(clip)

                if (pageMarkers.isNotEmpty()) {
                    ShareLabel(if (format == ShareFormat.Image) "ما في الصورة" else "ما في المقطع")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.ayah != null) {
                            FilterChip(
                                selected = session.kind == ShareKind.Ayah,
                                onClick = { session.kind = ShareKind.Ayah },
                                label = { Text("الآية") },
                            )
                        }
                        // Picking again from here is the way back into the picker, too.
                        FilterChip(
                            selected = session.kind == ShareKind.Span,
                            onClick = { session.picking = true },
                            label = { Text("عدة آيات") },
                        )
                        FilterChip(
                            selected = session.kind == ShareKind.Page,
                            onClick = { session.kind = ShareKind.Page },
                            // A recitation cannot hold a page of two surahs; it holds the page's ayahs.
                            label = { Text(if (format == ShareFormat.Image) "الصفحة كاملة" else "آيات الصفحة") },
                        )
                    }
                    if (session.kind == ShareKind.Span) {
                        TextButton(onClick = { session.picking = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تعديل الآيات")
                        }
                    }
                }

                if (format != ShareFormat.Audio) {
                    ShareLabel("المظهر")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MushafPalettes.forEach { p ->
                            FilterChip(
                                selected = session.paletteId == p.id,
                                onClick = { session.paletteId = p.id },
                                label = { Text(p.label) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(p.paper)
                                            .border(BorderStroke(1.5.dp, p.ink.copy(alpha = 0.6f)), CircleShape),
                                    )
                                },
                            )
                        }
                    }

                }
                if (format == ShareFormat.Image) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الإطار والمرجع", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "اسم السورة، ورقم الآية والصفحة، واسم التطبيق.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = session.framed, onCheckedChange = { session.framed = it })
                    }
                }
                if (format == ShareFormat.Video && audioAyahs.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تظليل الآية الجارية", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "تتظلّل كل آية في الفيديو وهي تُتلى.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = session.highlight, onCheckedChange = { session.highlight = it })
                    }
                }
                if (clip != null) {
                    AudioOptions(
                        state = clip,
                        access = recitation,
                        surah = audioAyahs.first().surahNumber,
                        withOpening = session.withOpening,
                        onWithOpening = { session.withOpening = it },
                    )
                }
            }

            if (format == ShareFormat.Audio && clip != null) {
                AudioActions(state = clip, ayahs = audioAyahs, reciter = recitation.reciter, onDismiss = onDismiss)
            } else if (format == ShareFormat.Video && clip != null) {
                VideoActions(
                    clip = clip,
                    ayahs = audioAyahs,
                    renderer = renderer,
                    markersFor = markersFor,
                    palette = paletteFor(session.paletteId),
                    highlight = session.highlight,
                    reciter = recitation.reciter,
                    onDismiss = onDismiss,
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = imageReady && !busy,
                        onClick = {
                            val bmp = image ?: return@Button
                            busy = true
                            coroutines.launch {
                                try {
                                    val uri = ShareImageExport.toCache(context, bmp, fileName)
                                    context.startActivity(ShareImageExport.chooser(uri, reference))
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تعذّر تجهيز الصورة", Toast.LENGTH_SHORT).show()
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("مشاركة")
                    }
                    if (ShareImageExport.canSaveToGallery) {
                        OutlinedButton(
                            enabled = imageReady && !busy,
                            onClick = {
                                val bmp = image ?: return@OutlinedButton
                                busy = true
                                coroutines.launch {
                                    val saved = try {
                                        ShareImageExport.saveToGallery(context, bmp, fileName)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        false
                                    } finally {
                                        busy = false
                                    }
                                    Toast.makeText(
                                        context,
                                        if (saved) "حُفظت الصورة في معرض الصور" else "تعذّر حفظ الصورة",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = "حفظ في معرض الصور", modifier = Modifier.size(18.dp))
                        }
                    }
                    val text = ayahs?.let { shareText(it, reference) }
                    if (text != null) {
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                                onDismiss()
                            },
                        ) {
                            Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("نص")
                        }
                    }
                }
            }
        }
    }
}

/**
 * The ayahs as text, closed by [reference]. One ayah reads as before this sheet existed; several
 * carry each one's number after it, as the mushaf does.
 */
private fun shareText(ayahs: List<AyahMarker>, reference: String): String? {
    if (ayahs.any { it.textUthmani.isBlank() }) return null
    val body = if (ayahs.size == 1) ayahs.single().textUthmani
    else ayahs.joinToString(" ") { "${it.textUthmani} ﴿${it.ayahNumber.toArabicDigits()}﴾" }
    return "$body\n$reference"
}

/** This image, or a copy scaled to fit [PREVIEW_MAX] px each way when it is larger. */
private fun Bitmap.forPreview(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= PREVIEW_MAX) return this
    val f = PREVIEW_MAX.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * f).toInt().coerceAtLeast(1), (height * f).toInt().coerceAtLeast(1), true)
}

private const val PREVIEW_MAX = 2048

@Composable
private fun Preview(image: Bitmap?, rendering: Boolean, failed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            val bitmap = remember(image) { image.asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = "معاينة الصورة",
                modifier = Modifier
                    .padding(10.dp)
                    .heightIn(max = 280.dp)
                    .aspectRatio(image.width.toFloat() / image.height)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
            )
        }
        when {
            rendering -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            failed && image == null -> Text(
                text = "تعذّر تجهيز الصورة",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ShareLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
