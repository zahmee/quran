package com.mushaf.reader.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.normalizeArabic
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.ui.theme.StatusBlueColor

/**
 * The recitation bar under the page: what is being recited and by whom (tap to change the
 * reciter), previous/next ayah, play/pause and close. In this right-to-left screen the reciter
 * sits on the right, "previous" points right (back toward the start of the line) and "next" left.
 */
@Composable
internal fun RecitationBar(
    surahName: String,
    ayah: Int?,
    reciterName: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onPickReciter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPickReciter)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = error ?: buildString {
                        append("سورة ").append(surahName)
                        if (ayah != null) append(" • الآية ").append(ayah.toArabicDigits())
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = reciterName ?: "اختيار القارئ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "تغيير القارئ",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "الآية السابقة")
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                when {
                    buffering && error == null -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    error != null -> Icon(Icons.Filled.Refresh, contentDescription = "إعادة المحاولة")
                    isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "إيقاف مؤقت")
                    else -> Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل")
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "الآية التالية")
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "إغلاق التلاوة",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Choosing the reciter: the recitations that have ayah timing, searchable by name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReciterPickerSheet(
    reciters: List<Reciter>,
    selectedId: Int?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPick: (Reciter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val shown = remember(reciters, query) {
        val q = normalizeArabic(query.trim())
        if (q.isEmpty()) reciters else reciters.filter { normalizeArabic(it.name).contains(q) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "اختيار القارئ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "القرّاء الذين تتوفر لتلاواتهم مواضع الآيات، فتتبع الصفحة التلاوة آيةً آية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("ابحث باسم القارئ") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            reciters.isEmpty() && loading -> Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            reciters.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: "لا توجد قائمة قرّاء بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(shown, key = { it.id }) { reciter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(reciter) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reciter.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (reciter.id == selectedId) FontWeight.Bold
                                else FontWeight.Normal,
                            )
                            if (reciter.rewaya.isNotBlank()) {
                                Text(
                                    text = reciter.rewaya,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (reciter.id == selectedId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "القارئ المختار",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/** The ayah being recited, drawn over the page in its own color (selection is green, the
 *  bookmarks gold and violet). Coordinates are page-image pixels times [baseScale]. */
@Composable
internal fun RecitationHighlight(marker: AyahMarker, baseScale: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = StatusBlueColor.copy(alpha = 0.16f)
        val corner = CornerRadius(8f, 8f)
        marker.rects.forEach { r ->
            drawRoundRect(
                color = color,
                topLeft = Offset(r.x * baseScale, r.y * baseScale),
                size = Size(r.w * baseScale, r.h * baseScale),
                cornerRadius = corner,
            )
        }
    }
}
