package com.mushaf.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.sync.CloudFeature
import com.mushaf.reader.data.sync.CloudProblem
import com.mushaf.reader.data.sync.DevicePlace
import com.mushaf.reader.data.sync.ElsewherePlace
import com.mushaf.reader.data.sync.KhatmaRecord
import com.mushaf.reader.data.sync.MarkRecord
import com.mushaf.reader.data.sync.PlacePoint
import com.mushaf.reader.data.sync.ReadingSync
import com.mushaf.reader.data.sync.SyncUiState
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafSoftDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The reader's offer to continue from another device. It never moves the reader by itself and
 * going there sets no bookmark — it only turns the page and flashes the ayah.
 */
@Composable
fun SyncPlaceBanner(
    place: ElsewherePlace?,
    describeVerse: (String) -> String?,
    surahOfPage: (Int) -> String?,
    onGo: (PlacePoint) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept through the exit animation, after the state has already cleared it.
    val last = remember { mutableStateOf<ElsewherePlace?>(null) }
    if (place != null) last.value = place
    AnimatedVisibility(
        visible = place != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = last.value ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "قرأتَ على ${shown.deviceName} بعد آخر قراءة هنا",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                shown.bookmark?.let { point ->
                    BannerPlaceRow(
                        label = "العلامة: ${placeLabel(point, describeVerse, surahOfPage)}",
                        time = relativeTime(point.at),
                        onGo = { onGo(point) },
                    )
                }
                shown.lastPage?.let { point ->
                    BannerPlaceRow(
                        label = "آخر صفحة: ${placeLabel(point, describeVerse, surahOfPage)}",
                        time = relativeTime(point.at),
                        onGo = { onGo(point) },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("ليس الآن") }
                }
            }
        }
    }
}

@Composable
private fun BannerPlaceRow(label: String, time: String, onGo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onGo) { Text("انتقل") }
    }
}

/**
 * The history tab of the backup screen: where each device stands, the week's bookmarks and every
 * completed khatma, all from the reader's account.
 */
fun LazyListScope.readingHistoryItems(
    signedIn: Boolean,
    state: SyncUiState,
    describeVerse: (String) -> String?,
    surahOfPage: (Int) -> String?,
    onSyncNow: () -> Unit,
    onGo: (PlacePoint) -> Unit,
) {
    if (!signedIn) {
        item {
            MushafPanel {
                MushafSectionHeader(
                    title = "سجل القراءة بين أجهزتك",
                    subtitle = "سجّل الدخول من تبويب «النسخ» لتظهر هنا علاماتك وختماتك ومكان وقوفك على كل جهاز.",
                    icon = Icons.Outlined.Devices,
                )
            }
        }
        return
    }

    item { SyncStatusPanel(state = state, onSyncNow = onSyncNow) }

    val history = state.history
    if (history == null) {
        if (state.loading) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
        return
    }

    item {
        DevicesPanel(
            thisDeviceId = history.thisDeviceId,
            devices = history.devices,
            describeVerse = describeVerse,
            surahOfPage = surahOfPage,
            onGo = onGo,
        )
    }
    item { MarksPanel(marks = history.marks, thisDeviceId = history.thisDeviceId, describeVerse = describeVerse, onGo = onGo) }
    item { KhatmasPanel(khatmas = history.khatmas, thisDeviceId = history.thisDeviceId) }
}

@Composable
private fun SyncStatusPanel(state: SyncUiState, onSyncNow: () -> Unit) {
    MushafPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "المزامنة بين أجهزتك",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        state.loading -> "جارٍ المزامنة…"
                        state.lastSyncAt > 0L -> "آخر مزامنة: ${relativeTime(state.lastSyncAt)}"
                        else -> "لم تتم مزامنة بعد"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onSyncNow, enabled = !state.loading) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("مزامنة الآن")
            }
        }
        state.error?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "تُرسل العلامة فور وضعها، وإن لم يكن هناك اتصال تُرسل تلقائياً حين يعود. " +
                "وعند فتح التطبيق يُعرض عليك المكان الأحدث من جهازك الآخر دون أن يضع علامة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DevicesPanel(
    thisDeviceId: String,
    devices: List<DevicePlace>,
    describeVerse: (String) -> String?,
    surahOfPage: (Int) -> String?,
    onGo: (PlacePoint) -> Unit,
) {
    MushafPanel {
        MushafSectionHeader(title = "أين وقفت على أجهزتك", icon = Icons.Outlined.Devices)
        // This device first, then the others by their latest activity.
        val ordered = devices.sortedWith(
            compareByDescending<DevicePlace> { it.deviceId == thisDeviceId }
                .thenByDescending { maxOf(it.lastPage?.at ?: 0L, it.bookmark?.at ?: 0L) }
        )
        if (ordered.isEmpty()) {
            Spacer(Modifier.size(8.dp))
            EmptyLine("لم يُسجَّل أي جهاز بعد.")
        }
        ordered.forEachIndexed { index, device ->
            if (index > 0) MushafSoftDivider()
            val mine = device.deviceId == thisDeviceId
            Spacer(Modifier.size(if (index == 0) 10.dp else 0.dp))
            Text(
                text = if (mine) "${device.deviceName} (هذا الجهاز)" else device.deviceName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            device.lastPage?.let { point ->
                HistoryRow(
                    label = "آخر صفحة: ${placeLabel(point, describeVerse, surahOfPage)}",
                    detail = relativeTime(point.at),
                    onGo = if (mine) null else ({ onGo(point) }),
                )
            }
            device.bookmark?.let { point ->
                HistoryRow(
                    label = "العلامة: ${placeLabel(point, describeVerse, surahOfPage)}",
                    detail = relativeTime(point.at),
                    onGo = if (mine) null else ({ onGo(point) }),
                )
            }
            if (device.lastPage == null && device.bookmark == null) EmptyLine("لا شيء بعد.")
        }
    }
}

@Composable
private fun MarksPanel(
    marks: List<MarkRecord>,
    thisDeviceId: String,
    describeVerse: (String) -> String?,
    onGo: (PlacePoint) -> Unit,
) {
    MushafPanel {
        MushafSectionHeader(
            title = "العلامات المرجعية",
            subtitle = "آخر ${ReadingSync.KEEP_MARK_DAYS.toArabicDigits()} أيام، وتبقى أحدث علامة من كل جهاز.",
            icon = Icons.Outlined.BookmarkBorder,
        )
        Spacer(Modifier.size(6.dp))
        if (marks.isEmpty()) EmptyLine("لم تضع علامة منذ تسجيل الدخول.")
        marks.forEach { mark ->
            val where = describeVerse(mark.verseKey) ?: mark.verseKey
            HistoryRow(
                label = "$where · ص ${mark.page.toArabicDigits()}",
                detail = "${deviceLabel(mark.deviceName, mark.deviceId == thisDeviceId)} · ${syncDateTime(mark.at)}",
                onGo = { onGo(PlacePoint(mark.page, mark.verseKey, mark.at)) },
            )
        }
    }
}

@Composable
private fun KhatmasPanel(khatmas: List<KhatmaRecord>, thisDeviceId: String) {
    MushafPanel {
        MushafSectionHeader(title = "الختمات المكتملة", icon = Icons.Outlined.AutoStories)
        Spacer(Modifier.size(6.dp))
        if (khatmas.isEmpty()) EmptyLine("لا ختمات مكتملة في حسابك بعد.")
        khatmas.forEach { khatma ->
            HistoryRow(
                label = "ختمة ${syncDate(khatma.completedAt)}",
                detail = "في ${arabicCount(khatma.durationDays.coerceAtLeast(1), "يوم", "يومين", "أيام")} · " +
                    deviceLabel(khatma.deviceName, khatma.deviceId == thisDeviceId),
                onGo = null,
            )
        }
    }
}

@Composable
private fun HistoryRow(label: String, detail: String, onGo: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onGo != null) TextButton(onClick = onGo) { Text("انتقل") }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun deviceLabel(name: String, mine: Boolean) = if (mine) "هذا الجهاز" else name

/** "البقرة ٢٥٥ · ص ٤٢" for a bookmark, "ص ٥٠ · آل عمران" for a page. */
private fun placeLabel(point: PlacePoint, describeVerse: (String) -> String?, surahOfPage: (Int) -> String?): String {
    val page = "ص ${point.page.toArabicDigits()}"
    val verse = point.verseKey?.let(describeVerse)
    if (verse != null) return "$verse · $page"
    val surah = surahOfPage(point.page)
    return if (surah != null) "$page · $surah" else page
}

private fun syncDate(time: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")).format(Date(time))

private fun syncDateTime(time: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")).format(Date(time))

/** "قبل ساعتين", falling back to the date beyond a week. */
internal fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - time).coerceAtLeast(0L) / 60_000L).toInt()
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "قبل ${arabicCount(minutes, "دقيقة", "دقيقتين", "دقائق")}"
        hours < 24 -> "قبل ${arabicCount(hours, "ساعة", "ساعتين", "ساعات")}"
        days < 7 -> "قبل ${arabicCount(days, "يوم", "يومين", "أيام")}"
        else -> syncDateTime(time)
    }
}

/** Arabic counting: "دقيقة", "دقيقتين", "٥ دقائق", "١٥ دقيقة". */
internal fun arabicCount(n: Int, one: String, two: String, few: String): String = when {
    n == 1 -> one
    n == 2 -> two
    n in 3..10 -> "${n.toArabicDigits()} $few"
    else -> "${n.toArabicDigits()} $one"
}

// ── Cloud faults ─────────────────────────────────────────────────────────────

private fun CloudProblem.title(): String = when (feature to kind) {
    CloudFeature.SYNC to CloudProblem.Kind.Refused -> "تعذّرت مزامنة القراءة"
    CloudFeature.SYNC to CloudProblem.Kind.Stalled -> "علامات لم تصل إلى جهازك الآخر"
    CloudFeature.BACKUP to CloudProblem.Kind.Refused -> "تعذّر النسخ السحابي"
    else -> "لم يكتمل النسخ التلقائي"
}

private fun CloudProblem.body(): String = when (kind) {
    CloudProblem.Kind.Refused -> "${message ?: "رفض الخادم العملية."} بدأت المشكلة ${relativeTime(since)}."
    CloudProblem.Kind.Stalled -> if (feature == CloudFeature.SYNC) {
        "${markCount(waiting)} لم تُرسل بعد، أقدمها ${relativeTime(since)}. تحقّق من اتصالك بالإنترنت."
    } else {
        "آخر نسخة ناجحة ${relativeTime(since)}. تحقّق من اتصالك بالإنترنت، أو انسخ الآن."
    }
}

private fun markCount(n: Int): String = when {
    n == 1 -> "علامة واحدة"
    n == 2 -> "علامتان"
    n in 3..10 -> "${n.toArabicDigits()} علامات"
    else -> "${n.toArabicDigits()} علامة"
}

/**
 * The reader's alert for a broken or stuck cloud feature. Unlike the sync offer it is about
 * something wrong, so it stays until fixed; "Later" hides it only until the app is next opened.
 */
@Composable
fun CloudProblemBanner(
    problems: List<CloudProblem>,
    hidden: Boolean,
    onRetry: (CloudProblem) -> Unit,
    onDetails: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = remember { mutableStateOf<List<CloudProblem>>(emptyList()) }
    if (problems.isNotEmpty()) last.value = problems
    AnimatedVisibility(
        visible = problems.isNotEmpty() && !hidden,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = last.value
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                shown.forEachIndexed { index, problem ->
                    if (index > 0) MushafSoftDivider(verticalPadding = 8.dp)
                    ProblemLines(problem)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onLater) { Text("لاحقاً") }
                    TextButton(onClick = onDetails) { Text("التفاصيل") }
                    FilledTonalButton(onClick = { shown.forEach(onRetry) }) { Text("أعد المحاولة") }
                }
            }
        }
    }
}

@Composable
private fun ProblemLines(problem: CloudProblem) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (problem.kind == CloudProblem.Kind.Refused) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(problem.title(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                problem.body(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The same faults at the top of the backup screen, each with its own "try again". */
@Composable
fun CloudProblemsPanel(problems: List<CloudProblem>, busy: Boolean, onRetry: (CloudProblem) -> Unit) {
    MushafPanel(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
        problems.forEachIndexed { index, problem ->
            if (index > 0) MushafSoftDivider()
            ProblemLines(problem)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { onRetry(problem) }, enabled = !busy) { Text("أعد المحاولة") }
            }
        }
    }
}
