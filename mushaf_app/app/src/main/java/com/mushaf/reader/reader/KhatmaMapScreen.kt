package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReadColor = Color(0xFF2E7D5B)     // green: read (dwelt on)
private val VisitedColor = Color(0xFF9CCFB8)  // light green: opened but only briefly
private val StoppedColor = Color(0xFFD4A017)  // amber: the bookmark page

/**
 * Khatma map: one tiny square per page (604), colored by progress. Laid out right-to-left like a
 * mushaf (page 1 top-right). Tap a square to jump to that page.
 */
@Composable
fun KhatmaMapScreen(
    totalPages: Int,
    readPages: Set<Int>,
    visitedPages: Set<Int>,
    bookmarkPage: Int?,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val readCount = readPages.size
    val touched = visitedPages.size                 // read ⊆ visited
    val visitedOnly = (touched - readCount).coerceAtLeast(0)
    val remaining = (totalPages - touched).coerceAtLeast(0)
    val percent = if (totalPages > 0) readCount * 100 / totalPages else 0
    val noneColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Text(
                        text = "خريطة الختمة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "${readCount.toArabicDigits()} صفحة مقروءة من ${totalPages.toArabicDigits()}  •  ${percent.toArabicDigits()}٪",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "زُرت فقط: ${visitedOnly.toArabicDigits()}  •  متبقّي: ${remaining.toArabicDigits()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem(ReadColor, "مقروءة")
                LegendItem(VisitedColor, "زُرت")
                LegendItem(StoppedColor, "العلامة")
                LegendItem(noneColor, "لم تُقرأ")
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 34.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(totalPages) { index ->
                    val page = index + 1
                    val color = when {
                        page == bookmarkPage -> StoppedColor
                        readPages.contains(page) -> ReadColor
                        visitedPages.contains(page) -> VisitedColor
                        else -> noneColor
                    }
                    val textColor = when {
                        page == bookmarkPage -> Color(0xFF3A2D00)
                        readPages.contains(page) -> Color.White
                        visitedPages.contains(page) -> Color(0xFF1B3A2C)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    }
                    val isCurrent = page == currentPage
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .then(
                                if (isCurrent)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                else Modifier
                            )
                            .clickable { onJump(page) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = page.toArabicDigits(),
                            color = textColor,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            "  $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
