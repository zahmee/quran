package com.mushaf.reader.reader

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.mushaf.reader.ui.theme.NoteColor
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mushaf.reader.ui.theme.ReadingType
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.data.AyahMarker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The single ayah menu, opened by a long press: a card as wide as the page. It sits above the whole
 * ayah when there is room, else below it, so the selected ayah stays visible; only an ayah too tall
 * for either gets covered, and then on the roomier side.
 *
 * The ayah's text leads, centred, with where it is beneath it; one bar of named actions closes the
 * card. The text takes up to [AyahCardTextLines] lines, fewer when the card would otherwise cover
 * the ayah. Everything above the bar is a handle: dragging it moves the card up or down anywhere
 * over the page.
 *
 * The popup window covers the whole page area and never moves; only the card moves inside it.
 * Moving the window itself under the finger makes a drag jitter, because every touch is then
 * measured against a window that has just been repositioned.
 */
@Composable
internal fun AyahLongPressMenu(
    ayah: AyahMarker,
    ayahBounds: Rect,
    boxWidthPx: Int,
    boxHeightPx: Int,
    pageWidthPx: Int,
    bookmarkMenu: BookmarkMenu,
    onBookmark: (Int) -> Unit,
    showBookmark: Boolean,
    hasAyahNote: Boolean,
    hasSurahNote: Boolean,
    onNote: (onSurah: Boolean) -> Unit,
    onExplain: () -> Unit,
    onListenAyah: () -> Unit,
    onPlayFrom: () -> Unit,
    onRepeat: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val reference = "﴿${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}﴾"
    val shareText = if (ayah.textUthmani.isNotBlank()) "${ayah.textUthmani}\n$reference" else reference

    val density = LocalDensity.current
    val margin = with(density) { 10.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    // Where the card was last placed: [top, height]. Read by the outside-touch test and the drag.
    val placed = remember { FloatArray(2) }
    // Null until the header is dragged; until then the card sits beside the ayah.
    var draggedTop by remember { mutableStateOf<Float?>(null) }

    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val hasText = ayah.textUthmani.isNotBlank()

    val coverPageArea = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = anchorBounds.topLeft
        }
    }

    Popup(
        popupPositionProvider = coverPageArea,
        onDismissRequest = onClose,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // The card, its ayah text cut to [textLines]; the layout below picks how many fit.
        val cardWith: @Composable (Int) -> Unit = { textLines ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
                shadowElevation = 12.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    // The ayah itself comes first. Everything above the actions is the drag
                    // handle; the bar on top says so.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(boxHeightPx) {
                                detectVerticalDragGestures { change, dy ->
                                    change.consume()
                                    val maxTop = (boxHeightPx - placed[1]).coerceAtLeast(0f)
                                    draggedTop = ((draggedTop ?: placed[0]) + dy).coerceIn(0f, maxTop)
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                            )
                            if (hasText) {
                                Text(
                                    text = ayah.textUthmani,
                                    style = ReadingType.bodyLarge,
                                    color = ink,
                                    textAlign = TextAlign.Center,
                                    maxLines = textLines,
                                    overflow = TextOverflow.Ellipsis,
                                    // Starts below the close button in the corner.
                                    modifier = Modifier.padding(top = 18.dp),
                                )
                            }
                            // Where the ayah is: its surah and number under the text, the rest in chips.
                            Row(
                                modifier = Modifier.padding(top = if (hasText) 6.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${ayah.surahNameAr} • الآية ${ayah.ayahNumber.toArabicDigits()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (ayah.isSajdah) SajdahBadge()
                            }
                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AyahPlaceChip("صفحة ${ayah.page.toArabicDigits()}")
                                AyahPlaceChip("الجزء ${ayah.juz.toArabicDigits()}")
                                AyahPlaceChip("الحزب ${ayah.hizb.toArabicDigits()}")
                            }
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 4.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )

                    // One bar for every action, each named: reading the ayah (green) and listening
                    // to it (blue) first, then the bookmarks in their own colours, then the tools.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    ) {
                        val slot = Modifier.weight(1f)
                        AyahBarAction(
                            modifier = slot,
                            label = "التفسير",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            tint = accent,
                            onClickLabel = "التفسير والمعاني",
                            onClick = onExplain,
                        )
                        ListenBarAction(
                            modifier = slot,
                            onPlayFrom = onPlayFrom,
                            onListenAyah = onListenAyah,
                            onRepeat = onRepeat,
                        )
                        if (showBookmark) {
                            BookmarkBarAction(
                                modifier = slot,
                                verseKey = ayah.verseKey,
                                menu = bookmarkMenu,
                                onToggle = onBookmark,
                            )
                        }
                        NoteBarAction(
                            modifier = slot,
                            surahName = ayah.surahNameAr,
                            hasAyahNote = hasAyahNote,
                            hasSurahNote = hasSurahNote,
                            onNote = onNote,
                        )
                        AyahBarAction(
                            modifier = slot,
                            label = "نسخ",
                            icon = Icons.Filled.ContentCopy,
                            tint = null,
                            onClickLabel = "نسخ الآية",
                            onClick = {
                                clipboardScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("آية قرآنية", shareText)),
                                    )
                                    Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        // Opens the share sheet: the ayah as an image, its text one tap away.
                        AyahBarAction(
                            modifier = slot,
                            label = "مشاركة",
                            icon = Icons.Filled.Share,
                            tint = null,
                            onClickLabel = "مشاركة الآية",
                            onClick = onShare,
                        )
                    }
                }
            }
        }
        SubcomposeLayout(
            modifier = Modifier.pointerInput(Unit) {
                // Touching the page around the card closes it, as touching outside the old popup did.
                awaitEachGesture {
                    val y = awaitFirstDown(requireUnconsumed = false).position.y
                    if (y < placed[0] || y > placed[0] + placed[1]) currentOnClose()
                }
            },
        ) { _ ->
            val width = pageWidthPx.coerceIn(0, boxWidthPx)
            val cardConstraints = Constraints(minWidth = width, maxWidth = width, maxHeight = boxHeightPx)
            // As many lines of the ayah's text as still leave the ayah itself in view on the page;
            // when even one line cannot, the shortest card covers the least of it.
            val card = (AyahCardTextLines downTo 1).firstNotNullOf { lines ->
                subcompose(lines) { cardWith(lines) }.first().measure(cardConstraints).takeIf {
                    lines == 1 ||
                        fitsBesideAyah(ayahBounds, it.height.toFloat(), boxHeightPx.toFloat(), margin, gap)
                }
            }
            layout(boxWidthPx, boxHeightPx) {
                val h = card.height.toFloat()
                val maxTop = (boxHeightPx - h).coerceAtLeast(0f)
                val top = (draggedTop ?: menuTopBesideAyah(ayahBounds, h, boxHeightPx.toFloat(), margin, gap))
                    .coerceIn(0f, maxTop)
                placed[0] = top
                placed[1] = h
                // Physical pixels: the page is centred the same way in either layout direction.
                card.place((boxWidthPx - width) / 2, top.roundToInt())
            }
        }
    }
}

/** The most lines of its ayah's text the ayah card shows; it shows fewer to keep clear of the ayah. */
private const val AyahCardTextLines = 3

/** Whether a card [h] tall fits wholly above the ayah or wholly below it. */
private fun fitsBesideAyah(ayah: Rect, h: Float, boxH: Float, margin: Float, gap: Float): Boolean =
    ayah.top - h - gap >= margin || ayah.bottom + gap + h <= boxH - margin

/** The menu's top beside the ayah: above it if it fits, else below, else on the roomier side. */
private fun menuTopBesideAyah(ayah: Rect, h: Float, boxH: Float, margin: Float, gap: Float): Float {
    val above = ayah.top - h - gap
    val below = ayah.bottom + gap
    return when {
        above >= margin -> above
        below + h <= boxH - margin -> below
        // Neither fits: cover as little as possible, on the roomier side.
        ayah.top > boxH - ayah.bottom -> margin
        else -> boxH - h - margin
    }.coerceIn(margin, (boxH - h - margin).coerceAtLeast(margin))
}

/** Where the ayah falls — its page, juz or hizb — as a quiet chip under the reference. */
@Composable
private fun AyahPlaceChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * One action in the ayah card's bar: its icon in a tinted square, its name beneath. [tint] colours
 * the action by its group; null leaves it neutral, for the plain tools. [hasMenu] adds the arrow
 * that says a tap opens choices rather than acting at once.
 */
@Composable
private fun AyahBarAction(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    tint: Color?,
    onClickLabel: String,
    hasMenu: Boolean = false,
    onClick: () -> Unit,
) {
    val glyph = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    val container = tint?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = glyph)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // On a narrow screen six actions share the bar, and a name with its menu arrow can
            // outgrow its share; it steps down a size or two rather than losing letters to "…".
            val labelStyle = MaterialTheme.typography.labelMedium
            Text(
                text = label,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.sp,
                    maxFontSize = labelStyle.fontSize,
                    stepSize = 0.5.sp,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasMenu) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Listening, as one action in the bar: a tap opens the ways to listen — on from this ayah, this
 * ayah alone, or repeating it to memorise.
 */
@Composable
private fun ListenBarAction(
    modifier: Modifier,
    onPlayFrom: () -> Unit,
    onListenAyah: () -> Unit,
    onRepeat: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = StatusBlueColor
    Box(modifier = modifier) {
        AyahBarAction(
            modifier = Modifier.fillMaxWidth(),
            label = "استماع",
            icon = Icons.Filled.PlayArrow,
            tint = tint,
            onClickLabel = "خيارات الاستماع",
            hasMenu = true,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("من هذه الآية فما بعدها") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onPlayFrom()
                },
            )
            DropdownMenuItem(
                text = { Text("الآية وحدها") },
                leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onListenAyah()
                },
            )
            DropdownMenuItem(
                text = { Text("التكرار والحفظ") },
                leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onRepeat()
                },
            )
        }
    }
}

/**
 * The bookmarks, as one action in the ayah card's bar: a tap opens the four, each with where it
 * stands now. Picking one puts it on this ayah (moving it from wherever it was); picking one this
 * ayah already holds takes it off. The button wears the colour of the bookmark on this ayah, or
 * gold when none is. A short message confirms each change, since the glyph alone is small.
 */
@Composable
private fun BookmarkBarAction(
    modifier: Modifier,
    verseKey: String,
    menu: BookmarkMenu,
    onToggle: (Int) -> Unit,
) {
    val place = menu.place
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val slots = menu.slots()
    val held = slots.firstOrNull { place(it)?.verseKey == verseKey }
    val toggle = { slot: Int ->
        val title = menu.title(slot)
        val here = place(slot)?.verseKey == verseKey
        onToggle(slot)
        // "العلامة الذهبية" reads as is; a name of the reader's own is quoted.
        val name = if (title.startsWith("العلامة ")) title else "علامة «$title»"
        val message = if (here) "أُزيلت $name" else "حُفظت $name على هذه الآية"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    // With a single bookmark showing there is nothing to choose: a tap puts it here or takes it off.
    val single = slots.singleOrNull()
    Box(modifier = modifier) {
        AyahBarAction(
            modifier = Modifier.fillMaxWidth(),
            label = "علامة",
            icon = if (held != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            tint = BookmarkSlotStyles[held ?: single ?: 0].color,
            onClickLabel = if (single != null) menu.title(single) else "العلامات المرجعية",
            hasMenu = single == null,
            onClick = { if (single != null) toggle(single) else open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            slots.forEach { slot ->
                val at = place(slot)
                BookmarkSlotItem(
                    slot = slot,
                    title = menu.title(slot),
                    place = at,
                    checked = at?.verseKey == verseKey,
                    enabled = true,
                    onClick = {
                        open = false
                        toggle(slot)
                    },
                )
            }
            ManageBookmarksItem {
                open = false
                menu.onManage()
            }
        }
    }
}

/**
 * Notes, as one action in the ayah card's bar: a tap offers a note on this ayah or on its whole
 * surah, each saying whether one is already written. The icon is filled when this ayah carries
 * a note.
 */
@Composable
private fun NoteBarAction(
    modifier: Modifier,
    surahName: String,
    hasAyahNote: Boolean,
    hasSurahNote: Boolean,
    onNote: (onSurah: Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AyahBarAction(
            modifier = Modifier.fillMaxWidth(),
            label = "ملاحظة",
            icon = if (hasAyahNote) Icons.AutoMirrored.Filled.StickyNote2 else Icons.AutoMirrored.Outlined.StickyNote2,
            tint = NoteColor,
            onClickLabel = "الملاحظات",
            hasMenu = true,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (hasAyahNote) "ملاحظتك على هذه الآية" else "ملاحظة على هذه الآية") },
                leadingIcon = {
                    Icon(
                        if (hasAyahNote) Icons.AutoMirrored.Filled.StickyNote2 else Icons.AutoMirrored.Outlined.StickyNote2,
                        contentDescription = null,
                        tint = NoteColor,
                    )
                },
                onClick = {
                    open = false
                    onNote(false)
                },
            )
            DropdownMenuItem(
                text = { Text(if (hasSurahNote) "ملاحظتك على سورة $surahName" else "ملاحظة على سورة $surahName") },
                leadingIcon = {
                    Icon(
                        if (hasSurahNote) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = NoteColor,
                    )
                },
                onClick = {
                    open = false
                    onNote(true)
                },
            )
        }
    }
}

@Composable
internal fun SajdahBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "۩ سجدة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
