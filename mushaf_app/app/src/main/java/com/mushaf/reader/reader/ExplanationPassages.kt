package com.mushaf.reader.reader

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.theme.ReadingType
import com.mushaf.reader.data.ExplanationTextScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalConfiguration
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackContent
import com.mushaf.reader.data.content.TafsirPassage
import com.mushaf.reader.data.content.TafsirPassageRow
import com.mushaf.reader.data.content.TafsirRowKind
import kotlinx.coroutines.launch

/**
 * A downloaded book chosen from the sources menu: its text for the ayah, or — when the book was
 * deleted while the sheet was open — the way back to the library.
 */
@Composable
internal fun PackTabContent(
    current: InstalledPack?,
    state: PackContentUiState?,
    onOpenLibrary: () -> Unit,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    if (current == null) {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "هذا الكتاب لم يعد منزَّلًا على الجهاز.\nيمكنك تنزيله من جديد من مكتبة التفاسير، أو اختيار مصدر آخر من قائمة «المصادر».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenLibrary) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مكتبة التفاسير")
            }
        }
        return
    }
    Column(modifier = modifier) {
        val content = state?.content
        when {
            state == null || state.loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            content is PackContent.Passage -> PackPassageContent(
                passage = content.passage,
                author = current.author.ifBlank { current.title },
                onOpenSources = onOpenSources,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            content is PackContent.Text -> PackPassageContent(
                passage = TafsirPassage(listOf(content.row), content.row.fromAyah),
                author = current.author.ifBlank { current.title },
                onOpenSources = onOpenSources,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            else -> Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmptyExplanationMessage("لا يتوفر نص لهذه الآية في هذا الكتاب.")
                SourcesLink(onOpenSources)
            }
        }
    }
}

/** One paragraph of a row, laid out as its own list item: a pack's row can be book-length. */
private data class PackParagraph(val row: Int, val text: String, val lastOfRow: Boolean, val firstOfRow: Boolean)

/**
 * A downloaded book's text for the ayah. Its rows are long and usually just one, so each paragraph
 * is its own item, the notes follow the row's last paragraph, and a row explaining several ayahs
 * together says which.
 */
@Composable
private fun PackPassageContent(
    passage: TafsirPassage,
    author: String,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val reading = ReadingType.bodyLarge.scaled(scale)
    val paragraphs = remember(passage) {
        passage.rows.flatMapIndexed { index, row ->
            val parts = row.text.split('\n').filter { it.isNotBlank() }
            parts.mapIndexed { i, part -> PackParagraph(index, part, i == parts.lastIndex, i == 0) }
        }
    }
    val listState = rememberLazyListState()
    val lead = if (!passage.hasOwnTafsir) 1 else 0
    LaunchedEffect(passage) {
        if (passage.rows.size > 1 && passage.hasOwnTafsir) {
            val first = paragraphs.indexOfFirst { it.row == passage.focusIndex }
            if (first > 0) listState.scrollToItem(first + lead)
        }
    }
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!passage.hasOwnTafsir) {
            item {
                Text(
                    text = "لم يُفرد $author هذه الآية بتفسير مستقل، وهذا تفسير الموضع الذي تقع فيه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
        itemsIndexed(paragraphs) { _, paragraph ->
            val row = passage.rows[paragraph.row]
            Column {
                if (paragraph.firstOfRow && row.toAyah > row.fromAyah && row.fromAyah > 0) {
                    Text(
                        text = "تفسير الآيات (${row.fromAyah.toArabicDigits()}–${row.toAyah.toArabicDigits()})",
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge.scaled(scale),
                        color = muted,
                    )
                }
                Text(text = formatSaadiText(paragraph.text, accent), style = reading)
                if (paragraph.lastOfRow) SaadiNotes(notes = row.notes, scale = scale)
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

/** The occasion of the ayah's revelation, folded into one line above the tabs until it is opened. */
@Composable
internal fun NozoolBar(row: TafsirPassageRow, scale: Float) {
    var open by rememberSaveable(row.text) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "سبب النزول",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.scaled(scale),
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "طيّ سبب النزول" else "إظهار سبب النزول",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = if (open) 180f else 0f },
                )
            }
            if (open) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.text.split('\n').filter { it.isNotBlank() }.forEach { paragraph ->
                        Text(
                            text = formatSaadiText(paragraph, accent),
                            style = ReadingType.bodyMedium.scaled(scale),
                        )
                    }
                    SaadiNotes(notes = row.notes, scale = scale)
                }
            }
        }
    }
}

/**
 * Sa'di explains the Quran passage by passage, so the whole passage is shown — its lead-in, the
 * Quran text, and every ayah's explanation — with the asked-about ayah's own explanation outlined
 * and scrolled into view. The Quran text is the mushaf text the rest of the app shows.
 */
@Composable
internal fun SaadiPassageContent(
    passage: TafsirPassage?,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val leadItems = if (passage != null && !passage.hasOwnTafsir) 1 else 0
    LaunchedEffect(passage) {
        if (passage != null && passage.hasOwnTafsir) {
            listState.scrollToItem(passage.focusIndex + leadItems)
        }
    }
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (passage == null || passage.isEmpty) {
            item { EmptyExplanationMessage("لا يتوفر تفسير لهذه الآية.") }
        } else {
            if (!passage.hasOwnTafsir) {
                item {
                    Text(
                        text = "لم يُفرد السعدي هذه الآية بتفسير مستقل، وهذا تفسير المقطع الذي تقع فيه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(passage.rows) { index, row ->
                SaadiRow(row = row, focused = index in passage.focusRows, scale = scale)
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

@Composable
private fun SaadiRow(row: TafsirPassageRow, focused: Boolean, scale: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val centered = Modifier.fillMaxWidth()
    val reading = ReadingType.bodyLarge.scaled(scale)
    when (row.kind) {
        TafsirRowKind.SurahTitle -> Text(
            text = row.text,
            modifier = centered,
            style = MaterialTheme.typography.titleMedium.scaled(scale),
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            textAlign = TextAlign.Center,
        )
        // Surah headings can carry a note: Maryam's says it is Madinan, and the editor corrects it.
        TafsirRowKind.RevelationPlace -> Column(modifier = centered) {
            Text(
                text = formatSaadiText(row.text, accent),
                modifier = centered,
                style = MaterialTheme.typography.labelLarge.scaled(scale),
                color = muted,
                textAlign = TextAlign.Center,
            )
            SaadiNotes(notes = row.notes, scale = scale)
        }
        TafsirRowKind.Basmala -> Text(
            text = row.text,
            modifier = centered,
            style = reading,
            color = accent,
            textAlign = TextAlign.Center,
        )
        TafsirRowKind.Divider -> Text(
            text = row.text,
            modifier = centered,
            color = muted,
            textAlign = TextAlign.Center,
        )
        TafsirRowKind.QuranText -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Text(
                text = row.text,
                modifier = Modifier.padding(16.dp),
                style = reading,
                color = accent,
            )
        }
        TafsirRowKind.AyahTafsir, TafsirRowKind.Prose, TafsirRowKind.Supplement -> {
            val text = formatSaadiText(row.text, accent)
            val body: @Composable (Modifier) -> Unit = { bodyModifier ->
                Column(modifier = bodyModifier) {
                    if (row.kind == TafsirRowKind.Supplement) {
                        Text(
                            text = "تكملة من موسوعة القرآن الكريم، غير موجودة في نسخة مركز تفسير:",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.labelMedium.scaled(scale),
                            color = muted,
                        )
                    }
                    Text(text = text, style = reading)
                    SaadiNotes(notes = row.notes, scale = scale)
                }
            }
            if (focused) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                ) {
                    body(Modifier.padding(14.dp))
                }
            } else {
                body(Modifier)
            }
        }
    }
}

/** The editor's notes on a row, folded away under a small button until the reader asks for them. */
@Composable
internal fun SaadiNotes(notes: List<String>, scale: Float) {
    if (notes.isEmpty()) return
    var open by rememberSaveable(notes) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelLarge.scaled(scale)
    TextButton(
        onClick = { open = !open },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = "حواشي المحقق (${notes.size.toArabicDigits()})", style = labelStyle)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "طيّ الحواشي" else "إظهار الحواشي",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = if (open) 180f else 0f },
        )
    }
    if (open) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            notes.forEachIndexed { index, note ->
                Text(
                    text = "(${(index + 1).toArabicDigits()}) $note",
                    style = MaterialTheme.typography.bodySmall.scaled(scale),
                    color = muted,
                )
            }
        }
    }
}

/**
 * The sheet's tools for the text it shows: its size, then copying and sharing it. The size opens its
 * two steps, down and up (fixed steps in [ExplanationTextScale], each end disabling its button). In
 * the RTL sheet the smaller size sits on the right and the larger on the left, following the reading
 * direction.
 */
@Composable
internal fun SheetTextTools(text: String?, title: String, scale: Int, onStep: (Int) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var sizing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            SmallToolButton(Icons.Filled.FormatSize, "حجم الخط") { sizing = true }
            DropdownMenu(expanded = sizing, onDismissRequest = { sizing = false }) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallToolButton(
                        Icons.Filled.TextDecrease,
                        "تصغير الخط",
                        enabled = ExplanationTextScale.canShrink(scale),
                    ) { onStep(-1) }
                    Text(
                        text = "${ExplanationTextScale.normalize(scale).toArabicDigits()}٪",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 52.dp),
                    )
                    SmallToolButton(
                        Icons.Filled.TextIncrease,
                        "تكبير الخط",
                        enabled = ExplanationTextScale.canGrow(scale),
                    ) { onStep(+1) }
                }
            }
        }
        SmallToolButton(Icons.Filled.ContentCopy, "نسخ $title", enabled = text != null) {
            text?.let { content ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, content)))
                    // Android 13 and newer already show a clipboard confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "تم نسخ $title", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        SmallToolButton(Icons.Filled.Share, "مشاركة $title", enabled = text != null) {
            text?.let { content ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
        }
    }
}

/** A compact icon button for a tools group: small glyph, the group's muted colour. */
@Composable
private fun SmallToolButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}
