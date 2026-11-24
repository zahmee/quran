package com.mushaf.reader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mushaf.reader.data.normalizeArabic
import com.mushaf.reader.data.notes.NoteEntity
import com.mushaf.reader.data.notes.cleanNoteText
import com.mushaf.reader.ui.components.MushafTopBar
import com.mushaf.reader.ui.theme.ReadingType

/**
 * The place a note is being written on: an ayah, or a whole surah when [ayah] is
 * [NoteEntity.SURAH_NOTE]. [quote] is the ayah's text, shown above the note as it is written.
 */
data class NoteTarget(
    val surah: Int,
    val ayah: Int,
    val title: String,
    val quote: String? = null,
)

/** "الآية ٢٥٥ من سورة البقرة" or "سورة البقرة", for the editor's title and the list. */
internal fun notePlaceTitle(surah: Int, ayah: Int, surahName: String): String =
    if (ayah == NoteEntity.SURAH_NOTE) "سورة $surahName"
    else "الآية ${ayah.toArabicDigits()} من سورة $surahName"

/**
 * Writing a note. It opens on what is already written there, so a note is added to rather than
 * replaced. Closing it any way but «إلغاء» keeps what was typed — a stray tap outside or Back
 * never loses a thought. Emptying the text and saving removes the note; «حذف» asks first.
 *
 * The corner button opens the same note over the whole screen for longer writing, and back
 * again; the text goes with it, since both layouts edit the one value.
 */
@Composable
internal fun NoteEditorDialog(
    target: NoteTarget,
    existing: NoteEntity?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val original = existing?.text.orEmpty()
    // The cursor starts after what is already written: a note is added to, not typed over.
    var field by rememberSaveable(target.surah, target.ayah, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(original, TextRange(original.length)))
    }
    var fullScreen by rememberSaveable(target.surah, target.ayah) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val keepAndClose = {
        if (cleanNoteText(field.text) != cleanNoteText(original)) onSave(field.text)
        onDismiss()
    }
    // A new note opens ready to type in. One already written opens to be read, with the keyboard
    // down: raising it uninvited would hide the note and the buttons under it. Changing size
    // means the reader is writing, so the field takes focus again in the window it moved to.
    val focus = remember { FocusRequester() }
    var resized by rememberSaveable(target.surah, target.ayah) { mutableStateOf(false) }
    LaunchedEffect(fullScreen) {
        if (!resized && original.isNotEmpty()) return@LaunchedEffect
        delay(150)
        runCatching { focus.requestFocus() }
    }

    val onFieldChange: (TextFieldValue) -> Unit = { next ->
        field = if (next.text.length <= NoteEntity.MAX_TEXT) next
        else next.copy(text = next.text.take(NoteEntity.MAX_TEXT))
    }
    val context = LocalContext.current
    // Speaking a note: Android's own speech service (Google's, on most phones) listens in its
    // own window and hands back the words, which go in where the cursor stands. The app never
    // touches the microphone itself, so it asks for no permission to do this.
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        when {
            result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty() -> {
                field = insertAtCursor(field, spoken)
                runCatching { focus.requestFocus() }
            }
            result.resultCode == Activity.RESULT_CANCELED -> Unit
            else -> Toast.makeText(context, dictationProblem(result.resultCode), Toast.LENGTH_LONG).show()
        }
    }
    val dictate = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, DictationLanguage)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, DictationLanguage)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم بملاحظتك")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            dictation.launch(intent)
        } catch (missing: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "الإملاء الصوتي غير متاح على هذا الجهاز. يمكنك استعمال ميكروفون لوحة المفاتيح.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                target.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(top = 2.dp),
            )
            // Small and tucked into the corner, out of the title's way.
            Row(modifier = Modifier.offset(x = 14.dp, y = (-12).dp)) {
                NoteCornerButton(
                    icon = Icons.Filled.Mic,
                    description = "إملاء الملاحظة بالصوت",
                    onClick = dictate,
                )
                NoteCornerButton(
                    icon = if (fullScreen) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
                    description = if (fullScreen) "تصغير خانة الملاحظة" else "تكبير خانة الملاحظة",
                    onClick = {
                        resized = true
                        fullScreen = !fullScreen
                    },
                )
            }
        }
    }
    val lastEdit: @Composable () -> Unit = {
        existing?.let {
            Text(
                "آخر تعديل ${relativeTime(it.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    val saveButton: @Composable () -> Unit = { TextButton(onClick = keepAndClose) { Text("حفظ") } }
    val otherButtons: @Composable () -> Unit = {
        Row {
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = keepAndClose,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        // The keyboard takes the bottom of the screen; the field gives way to it
                        // rather than hiding what is being typed underneath.
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    title()
                    NoteQuote(target.quote, maxLines = 2)
                    OutlinedTextField(
                        value = field,
                        onValueChange = onFieldChange,
                        placeholder = { Text("اكتب ملاحظتك أو تأملك هنا") },
                        modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus),
                    )
                    lastEdit()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        otherButtons()
                        saveButton()
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = keepAndClose,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = title,
            text = {
                Column {
                    NoteQuote(target.quote, maxLines = 3)
                    OutlinedTextField(
                        value = field,
                        onValueChange = onFieldChange,
                        placeholder = { Text("اكتب ملاحظتك أو تأملك هنا") },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    lastEdit()
                }
            },
            confirmButton = saveButton,
            dismissButton = otherButtons,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الملاحظة؟") },
            text = { Text("ستُحذف الملاحظة على ${target.title.removePrefix("ملاحظة على ")} ولا يمكن استرجاعها إلا من نسخة احتياطية.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إبقاء") } },
        )
    }
}

/** The speech service is asked for Arabic as written in Saudi Arabia; it understands the dialects
 *  close to it and writes Modern Standard spelling. */
private const val DictationLanguage = "ar-SA"

/** [spoken] goes in at the cursor, with a space before it when it would touch a word. */
internal fun insertAtCursor(field: TextFieldValue, spoken: String): TextFieldValue {
    val start = field.selection.min
    val end = field.selection.max
    val before = field.text.substring(0, start)
    val after = field.text.substring(end)
    val gap = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val text = (before + gap + spoken + after).take(NoteEntity.MAX_TEXT)
    val cursor = (before.length + gap.length + spoken.length).coerceAtMost(text.length)
    return TextFieldValue(text, TextRange(cursor))
}

/** What to tell the reader when the speech service came back without words. */
private fun dictationProblem(resultCode: Int): String = when (resultCode) {
    RecognizerIntent.RESULT_NETWORK_ERROR -> "تعذر الإملاء الصوتي: يحتاج اتصالًا بالإنترنت."
    RecognizerIntent.RESULT_NO_MATCH -> "لم يُلتقط كلام واضح. حاول مرة أخرى."
    RecognizerIntent.RESULT_AUDIO_ERROR -> "تعذر استعمال الميكروفون."
    else -> "تعذر الإملاء الصوتي. حاول مرة أخرى."
}

/** A small button in the editor's corner. */
@Composable
private fun NoteCornerButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The ayah being written on, above its note, in the reading face. */
@Composable
private fun NoteQuote(quote: String?, maxLines: Int) {
    if (quote.isNullOrBlank()) return
    Text(
        quote,
        style = ReadingType.bodyLarge,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

/** How the notes list is ordered. */
private enum class NotesOrder { Mushaf, Newest }

/**
 * Every note the reader wrote, in one place: in mushaf order under each surah's name, or newest
 * first. A search narrows it by the note's words or the surah's name. Tapping a note goes to its
 * place in the mushaf; the pencil opens it for writing.
 */
@Composable
internal fun NotesScreen(
    notes: List<NoteEntity>,
    surahName: (Int) -> String,
    onOpen: (NoteEntity) -> Unit,
    onEdit: (NoteEntity) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(NotesOrder.Mushaf) }

    val shown = remember(notes, query, order) {
        // Folded the way the mushaf search folds, so «الملائكة» finds «الملايكة» and hamza
        // and tashkeel never stand between the reader and their own words.
        val needle = normalizeArabic(query.trim()).lowercase()
        val matching = if (needle.isEmpty()) notes else notes.filter { note ->
            normalizeArabic(note.text).lowercase().contains(needle) ||
                normalizeArabic(surahName(note.surah)).contains(needle)
        }
        when (order) {
            NotesOrder.Mushaf -> matching
            NotesOrder.Newest -> matching.sortedByDescending { it.updatedAt }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "ملاحظاتي",
                subtitle = if (notes.isEmpty()) null else "${notes.size.toArabicDigits()} ملاحظة",
                onBack = onBack,
            )
            if (notes.isEmpty()) {
                NotesHint(
                    "لم تكتب ملاحظة بعد.\nاضغط مطولًا على آية، ثم «ملاحظة» لتكتب على الآية أو على سورتها.",
                )
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث في ملاحظاتك") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = order == NotesOrder.Mushaf,
                    onClick = { order = NotesOrder.Mushaf },
                    label = { Text("ترتيب المصحف") },
                )
                FilterChip(
                    selected = order == NotesOrder.Newest,
                    onClick = { order = NotesOrder.Newest },
                    label = { Text("الأحدث") },
                )
            }
            if (shown.isEmpty()) {
                NotesHint("لا توجد ملاحظة تطابق البحث.")
                return@Column
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                var previousSurah = -1
                shown.forEach { note ->
                    // In mushaf order the notes sit under their surah's name; newest-first mixes
                    // surahs, so each row names its own instead.
                    if (order == NotesOrder.Mushaf && note.surah != previousSurah) {
                        previousSurah = note.surah
                        item(key = "surah-${note.surah}") {
                            Text(
                                "سورة ${surahName(note.surah)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                            )
                        }
                    }
                    item(key = "note-${note.surah}-${note.ayah}") {
                        val place = when {
                            order == NotesOrder.Newest -> notePlaceTitle(note.surah, note.ayah, surahName(note.surah))
                            note.isSurahNote -> "على السورة كاملة"
                            else -> "الآية ${note.ayah.toArabicDigits()}"
                        }
                        NoteRow(
                            place = place,
                            note = note,
                            onOpen = { onOpen(note) },
                            onEdit = { onEdit(note) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(place: String, note: NoteEntity, onOpen: () -> Unit, onEdit: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "الانتقال إليها", onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(place, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        relativeTime(note.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    note.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "تعديل الملاحظة")
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun NotesHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
