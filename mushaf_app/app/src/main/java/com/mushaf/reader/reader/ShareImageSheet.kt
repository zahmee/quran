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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.share.AyahSpan
import com.mushaf.reader.share.ShareImageExport
import com.mushaf.reader.share.ShareImageRenderer
import com.mushaf.reader.share.ShareScope
import com.mushaf.reader.ui.theme.MushafPalettes
import com.mushaf.reader.ui.theme.paletteFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the share image holds. */
internal enum class ShareKind { Ayah, Span, Page }

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
    /** The chosen run, once one has been picked. */
    var span by mutableStateOf<AyahSpan?>(null)
    /** True while [AyahRangePicker] is up in place of the sheet. */
    var picking by mutableStateOf(false)

    /** Where the picker starts: the run picked before, else the long-pressed ayah, else the
     *  page's first. */
    fun initialSpan(markers: List<AyahMarker>): AyahSpan =
        span ?: markers.indexOfFirst { it.verseKey == ayah?.verseKey }
            .takeIf { it >= 0 }?.let { AyahSpan(it, it) }
        ?: AyahSpan(0, 0)

    fun scope(markers: List<AyahMarker>): ShareScope {
        val s = span
        return when {
            kind == ShareKind.Ayah && ayah != null -> ShareScope.Ayahs(page, listOf(ayah))
            kind == ShareKind.Span && s != null && s.end < markers.size ->
                ShareScope.Ayahs(page, markers.subList(s.start, s.end + 1))
            else -> ShareScope.Page(page)
        }
    }
}

/**
 * Share as an image: an ayah, a run of ayahs, or the whole page, cut from the printed page.
 *
 * The preview is the very image that will be sent, re-rendered as the options change; the previous
 * one stays up meanwhile so the sheet does not flicker.
 *
 * The ayahs' text can still be sent as text from here — that was the card's share button before
 * this sheet existed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ShareImageSheet(
    session: ShareSession,
    pageMarkers: List<AyahMarker>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember { ShareImageRenderer(context) }
    val coroutines = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val page = session.page

    var image by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val scope = session.scope(pageMarkers)
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

    LaunchedEffect(scope, session.paletteId, session.framed) {
        rendering = true
        try {
            image = renderer.render(scope, pageMarkers, paletteFor(session.paletteId), session.framed)
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
                    text = "مشاركة صورة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (ayahs != null) "سورة ${spanLabel(ayahs)}" else pageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Preview(image = image, rendering = rendering, failed = failed)

                if (pageMarkers.isNotEmpty()) {
                    ShareLabel("ما في الصورة")
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
                            label = { Text("الصفحة كاملة") },
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

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = image != null && !busy,
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
                        enabled = image != null && !busy,
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
