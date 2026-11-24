package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.ui.theme.BookmarkGoldColor
import com.mushaf.reader.ui.theme.BookmarkRedColor
import com.mushaf.reader.ui.theme.BookmarkTealColor
import com.mushaf.reader.ui.theme.BookmarkVioletColor

/** Where one bookmark stands: its ayah, that ayah's page, and "البقرة ٢٥٥" for the menus. */
data class BookmarkPlace(
    val verseKey: String,
    val page: Int,
    val verse: String,
)

/** How a bookmark slot is named and painted. The order is the slot order in storage. */
internal data class BookmarkSlotStyle(val name: String, val color: Color)

internal val BookmarkSlotStyles = listOf(
    BookmarkSlotStyle("الذهبية", BookmarkGoldColor),
    BookmarkSlotStyle("البنفسجية", BookmarkVioletColor),
    BookmarkSlotStyle("الحمراء", BookmarkRedColor),
    BookmarkSlotStyle("الفيروزية", BookmarkTealColor),
)

/** What bookmark [slot] is called: the reader's own [name] for it, or "العلامة الذهبية". */
internal fun bookmarkTitle(slot: Int, name: String): String =
    name.ifBlank { "العلامة ${BookmarkSlotStyles[slot].name}" }

/** "البقرة ٢٥٥ · ص ٤٢", or that the slot is free. */
internal fun bookmarkPlaceLine(place: BookmarkPlace?): String =
    if (place == null) "غير مستخدمة" else "${place.verse} · ص ${place.page.toArabicDigits()}"

/**
 * What every bookmark menu reads and does, handed down as one piece: where each slot stands,
 * what it is called, going to it, and opening the screen that names and clears them.
 */
internal class BookmarkMenu(
    val place: (Int) -> BookmarkPlace?,
    val title: (Int) -> String,
    val onJump: (Int) -> Unit,
    val onManage: () -> Unit,
)

/**
 * One bookmark as a menu row: its colour, its name, and where it stands on the line under it.
 * [checked] marks the slot that already holds the ayah in hand.
 */
@Composable
internal fun BookmarkSlotItem(
    slot: Int,
    title: String,
    place: BookmarkPlace?,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val style = BookmarkSlotStyles[slot]
    DropdownMenuItem(
        text = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    bookmarkPlaceLine(place),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = if (place != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = null,
                tint = if (enabled) style.color else style.color.copy(alpha = 0.38f),
            )
        },
        trailingIcon = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = "على هذه الآية", tint = style.color) }
        } else null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** The last row of every bookmark menu: the screen where the four are named and cleared. */
@Composable
internal fun ManageBookmarksItem(onClick: () -> Unit) {
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("إدارة العلامات") },
        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * The four bookmarks to go back to, each with where it stands. A slot not in use is shown but
 * cannot be picked, so the reader sees all four and which are free.
 */
@Composable
internal fun BookmarkJumpMenu(
    expanded: Boolean,
    menu: BookmarkMenu,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        BookmarkSlotStyles.indices.forEach { slot ->
            val at = menu.place(slot)
            BookmarkSlotItem(
                slot = slot,
                title = menu.title(slot),
                place = at,
                checked = false,
                enabled = at != null,
                onClick = {
                    onDismiss()
                    menu.onJump(slot)
                },
            )
        }
        ManageBookmarksItem {
            onDismiss()
            menu.onManage()
        }
    }
}

/**
 * Naming and clearing the four bookmarks, all on one screen. Each row holds the bookmark's
 * colour, a field for its name (blank keeps the colour's name), where it stands, and the ways to
 * go there or take it off. The names are saved together when the screen closes, however it
 * closes, so nothing typed is lost to a stray tap outside it.
 */
@Composable
internal fun BookmarkManageDialog(
    names: List<String>,
    place: (Int) -> BookmarkPlace?,
    onSaveNames: (List<String>) -> Unit,
    onJump: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val edited = remember { mutableStateListOf(*names.toTypedArray()) }
    val close = {
        onSaveNames(edited.toList())
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("العلامات المرجعية") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BookmarkSlotStyles.forEachIndexed { slot, style ->
                    if (slot > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val at = place(slot)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (at != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = style.color,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = edited[slot],
                            onValueChange = { edited[slot] = it.take(ReadingStore.BOOKMARK_NAME_MAX) },
                            singleLine = true,
                            placeholder = { Text("العلامة ${style.name}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            bookmarkPlaceLine(at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = at != null,
                            onClick = {
                                close()
                                onJump(slot)
                            },
                        ) { Text("انتقال") }
                        TextButton(enabled = at != null, onClick = { onClear(slot) }) { Text("إزالة") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("تم") } },
    )
}
