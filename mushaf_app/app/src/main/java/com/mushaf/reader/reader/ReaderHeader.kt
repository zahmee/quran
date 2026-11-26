package com.mushaf.reader.reader

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.theme.BookmarkGoldColor
import com.mushaf.reader.ui.theme.MushafPalettes
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.ui.theme.StatusGoldColor
import com.mushaf.reader.ui.theme.StatusGreenColor
import com.mushaf.reader.ui.theme.StatusRedColor
import kotlinx.coroutines.delay
import java.util.Calendar

/** Widths (px) the header's fixed pieces have been laid out at; 0 until first measured. */
@Stable
private class HeaderPieceWidths {
    var page by mutableIntStateOf(0)
    var juz by mutableIntStateOf(0)
    var timer by mutableIntStateOf(0)
    /** Two-row layout only: the settings · clock corner. */
    var start by mutableIntStateOf(0)
}

/** Tells the reader screen which bar-placed actions this layout had to list in More instead. */
@Composable
private fun ReportBarOverflow(
    requested: List<String>,
    visible: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    val overflow = requested.filterNotTo(LinkedHashSet()) { it in visible }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(overflow) { latest(overflow) }
}

/** Extra room the single-row header gives each physical side to step past a camera hole. */
private data class CutoutClearance(val left: Dp, val right: Dp)

/**
 * Clearance for a punch-hole that falls inside the top [bandHeight] and sits in the outer third
 * of the window: that side is padded to just past the hole, minus whatever the horizontal cutout
 * inset already reserves there. A hole near the middle is left alone.
 */
@Composable
private fun cornerCutoutClearance(bandHeight: Dp): CutoutClearance {
    val density = LocalDensity.current
    // Reading the insets subscribes to cutout changes (fold, rotation) so the rects stay current.
    val cutout = WindowInsets.displayCutout
    val insetLeft = cutout.getLeft(density, LayoutDirection.Ltr)
    val insetRight = cutout.getRight(density, LayoutDirection.Ltr)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return CutoutClearance(0.dp, 0.dp)
    val rects = LocalView.current.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
    val width = LocalWindowInfo.current.containerSize.width
    val bandPx = with(density) { bandHeight.roundToPx() }
    var left = 0
    var right = 0
    for (rect in rects) {
        if (rect.top >= bandPx || rect.isEmpty) continue
        when {
            rect.centerX() < width / 3 -> left = maxOf(left, rect.right)
            rect.centerX() > width * 2 / 3 -> right = maxOf(right, width - rect.left)
        }
    }
    return with(density) {
        CutoutClearance(
            left = (left - insetLeft).coerceAtLeast(0).toDp(),
            right = (right - insetRight).coerceAtLeast(0).toDp(),
        )
    }
}

@Composable
internal fun ReaderHeader(
    page: Int,
    surahNumber: Int?,
    surah: String?,
    ayahCount: Int?,
    surahPercent: Int?,
    juz: Int,
    pageInJuz: Int,
    pagesInJuz: Int,
    juzPercent: Int,
    paletteId: String,
    hasBookmark: Boolean,
    bookmarkMenu: BookmarkMenu,
    fillScreen: Boolean,
    bigButtons: Boolean,
    showClock: Boolean,
    showSessionTimer: Boolean,
    showSurahNumber: Boolean,
    showSurahAyahCount: Boolean,
    showSurahProgress: Boolean,
    showJuzProgressPercent: Boolean,
    showJuzProgressPages: Boolean,
    clockColor: String,
    sessionTimerColor: String,
    sessionStartedAt: Long,
    sessionDurationMs: () -> Long,
    /** Extra pull-back from the screen's side edges, for curved corners. 0.dp by default. */
    edgeMargin: Dp,
    isVisible: (String) -> Boolean,
    isInBar: (String) -> Boolean,
    /** (action id, the tint it would have had) -> the color the user picked for it. */
    buttonTint: (String, Color) -> Color,
    /** Bar-placed actions this window has no room for (they are listed in More instead). */
    onBarOverflowChange: (Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
    onPaletteChange: (String) -> Unit,
    onStats: () -> Unit,
    onOpenNotes: () -> Unit,
    onPageClick: () -> Unit,
    onAbout: () -> Unit,
) {
    val headerColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val quietContentColor = contentColor.copy(alpha = 0.68f)
    val activeContentColor = MaterialTheme.colorScheme.primary
    val clockTextColor = headerStatusColor(clockColor, quietContentColor)
    val sessionTimerTextColor = headerStatusColor(sessionTimerColor, quietContentColor)

    // Height of the top camera/status-bar band (still reported in immersive mode). The strip
    // below fills it with corner buttons; the center is left clear for a punch-hole/notch.
    val topInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()

    // "Bigger buttons" setting bumps the header icons up a notch (and the bookmark in the info row).
    val btnSize = if (bigButtons) 40.dp else 32.dp
    val iconSize = if (bigButtons) 24.dp else 20.dp

    // Landscape, or an unfolded foldable's wide inner screen, has room to collapse the two header
    // rows into one. A portrait phone or a folded/cover screen (narrow) keeps the two-row layout.
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { windowSize.width.toDp() }
    val singleRow = windowSize.width > windowSize.height || windowWidth >= 600.dp

    // A placement preference means "keep this action on the bar when there is room". Each layout
    // below works out the room its fixed pieces really leave and fits the requested actions into it
    // with fitBarActions (fill/hide/bookmark keep their seats first); the rest are listed in More,
    // and that overflow is reported so the settings screen can say so rather than look unsaved.
    val requestedBarActions = HeaderActions.filter { isVisible(it.id) && isInBar(it.id) }.map { it.id }
    val showTimer = showSessionTimer && sessionStartedAt > 0L
    // Widest each fixed piece has been laid out at. Only growing keeps a ticking timer or a page
    // turn from bouncing a button between the bar and More; it starts over when the content changes.
    val pieces = remember(
        singleRow, bigButtons, showClock, showTimer, showJuzProgressPercent, showJuzProgressPages
    ) { HeaderPieceWidths() }
    val density = LocalDensity.current
    // A piece not laid out yet (the very first frame) counts at a conservative estimate.
    fun Int.orDp(estimate: Dp): Dp = if (this > 0) with(density) { this@orDp.toDp() } else estimate

    // Live ticker for the optional clock / current-session timer (only loops while one is shown).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showClock, showSessionTimer) {
        while (showClock || showSessionTimer) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val readingDurationMs = remember(nowMs, sessionStartedAt, sessionDurationMs) { sessionDurationMs() }

    Surface(
        color = headerColor,
        contentColor = contentColor,
        shadowElevation = 1.dp
    ) {
        if (singleRow) {
            // One-row header for wide screens. It sits in the camera band itself rather than below
            // it, so an unfolded foldable shows a single line with the punch-hole inside it. A hole
            // near either end pushes that end inward so no control lands under the lens; a side
            // notch in landscape is kept clear by the horizontal inset.
            val cornerHole = cornerCutoutClearance(bandHeight = btnSize + 4.dp)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .absolutePadding(left = cornerHole.left, right = cornerHole.right)
                    .padding(horizontal = 8.dp + edgeMargin, vertical = 2.dp)
                    .height(btnSize)
            ) {
                // The end group may grow toward the centred page badge but never under it. Its
                // fixed pieces (juz chip, timer, the always-present More) are measured, and every
                // bar action costs one button plus the row's spacing.
                val gap = 4.dp
                val endRoom = maxWidth / 2 - pieces.page.orDp(btnSize * 2) / 2 - gap * 2
                val actionRoom = endRoom - pieces.juz.orDp(112.dp) - gap -
                    (if (showTimer) pieces.timer.orDp(72.dp) + gap else 0.dp) - btnSize
                val visibleBarActionIds = fitBarActions(
                    requestedBarActions, barSlots(actionRoom.value, (btnSize + gap).value)
                )
                ReportBarOverflow(requestedBarActions, visibleBarActionIds, onBarOverflowChange)
                // Start group: settings · optional clock · surah identity.
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "إعدادات",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (showClock) {
                        Text(
                            text = formatClock(nowMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = clockTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    if (surah != null) {
                        SurahChip(
                            surah = surah,
                            surahNumber = surahNumber,
                            ayahCount = ayahCount,
                            surahPercent = surahPercent,
                            showSurahNumber = showSurahNumber,
                            showSurahAyahCount = showSurahAyahCount,
                            showSurahProgress = showSurahProgress,
                            color = quietContentColor,
                            modifier = Modifier.widthIn(max = 160.dp),
                            onClick = { onOpenIndex(0) }
                        )
                    }
                }
                // Center: page badge.
                PageChip(
                    page = page,
                    contentColor = contentColor,
                    quietContentColor = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .onSizeChanged { if (it.width > pieces.page) pieces.page = it.width },
                    onClick = onPageClick
                )
                // End group: juz position · optional session timer · hide · overflow menu.
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    JuzChip(
                        juz = juz,
                        juzPercent = juzPercent,
                        pageInJuz = pageInJuz,
                        pagesInJuz = pagesInJuz,
                        showJuzProgressPercent = showJuzProgressPercent,
                        showJuzProgressPages = showJuzProgressPages,
                        color = quietContentColor,
                        accentColor = activeContentColor,
                        big = bigButtons,
                        modifier = Modifier.onSizeChanged { if (it.width > pieces.juz) pieces.juz = it.width },
                        onClick = { onOpenIndex(1) }
                    )
                    if (showTimer) {
                        Text(
                            text = formatElapsed(readingDurationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier
                                .onSizeChanged { if (it.width > pieces.timer) pieces.timer = it.width }
                                .padding(horizontal = 6.dp)
                        )
                    }
                    HeaderBarActions(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        quietContentColor = quietContentColor,
                        activeContentColor = activeContentColor,
                        fillScreen = fillScreen,
                        hasBookmark = hasBookmark,
                        bookmarkMenu = bookmarkMenu,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        buttonTint = buttonTint,
                        onOpenSearch = onOpenSearch,
                        onStats = onStats,
                        onOpenNotes = onOpenNotes,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                    )
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        bookmarkMenu = bookmarkMenu,
                        fillScreen = fillScreen,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        onOpenSearch = onOpenSearch,
                        onStats = onStats,
                        onOpenNotes = onOpenNotes,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                        onAbout = onAbout
                    )
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Keep clear of a side notch in landscape; the top band is handled by the strip.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // …and clear of a curved corner, which no inset reports.
                .padding(horizontal = edgeMargin)
        ) {
            // Camera-safe top strip: every quick-access button lives here, split between the two
            // corners so the center stays clear for a punch-hole/notch. Trim a little of the inset
            // to tighten the top gap, but never below the icon height (32dp).
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(topInset - 6.dp, btnSize))
            ) {
                // Bar actions get whatever the start corner (settings · clock), the timer and the
                // always-present More leave; this row has no spacing between its buttons.
                val actionRoom = maxWidth -
                    pieces.start.orDp(btnSize + if (showClock) 64.dp else 0.dp) - 6.dp -
                    (if (showTimer) pieces.timer.orDp(72.dp) else 0.dp) - btnSize
                val visibleBarActionIds = fitBarActions(
                    requestedBarActions, barSlots(actionRoom.value, btnSize.value)
                )
                ReportBarOverflow(requestedBarActions, visibleBarActionIds, onBarOverflowChange)
                // Start corner (top-right in RTL): keep the always-needed controls close and quiet.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { if (it.width > pieces.start) pieces.start = it.width },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App settings — always shown so it can never hide itself away.
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "إعدادات",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    // Optional live wall-clock, shown beside the always-available settings button.
                    if (showClock) {
                        Text(
                            text = formatClock(nowMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = clockTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
                // End corner (top-left in RTL): reading-first actions plus a secondary menu.
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Optional current-session duration (green), shown before the stats button.
                    if (showTimer) {
                        Text(
                            text = formatElapsed(readingDurationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier
                                .onSizeChanged { if (it.width > pieces.timer) pieces.timer = it.width }
                                .padding(horizontal = 6.dp)
                        )
                    }
                    HeaderBarActions(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        quietContentColor = quietContentColor,
                        activeContentColor = activeContentColor,
                        fillScreen = fillScreen,
                        hasBookmark = hasBookmark,
                        bookmarkMenu = bookmarkMenu,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        buttonTint = buttonTint,
                        onOpenSearch = onOpenSearch,
                        onStats = onStats,
                        onOpenNotes = onOpenNotes,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                    )
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        bookmarkMenu = bookmarkMenu,
                        fillScreen = fillScreen,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        onOpenSearch = onOpenSearch,
                        onStats = onStats,
                        onOpenNotes = onOpenNotes,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                        onAbout = onAbout
                    )
                }
            }

            // Info row — surah identity (start) · page number (center) · juz position (end).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (surah != null) {
                    SurahChip(
                        surah = surah,
                        surahNumber = surahNumber,
                        ayahCount = ayahCount,
                        surahPercent = surahPercent,
                        showSurahNumber = showSurahNumber,
                        showSurahAyahCount = showSurahAyahCount,
                        showSurahProgress = showSurahProgress,
                        color = quietContentColor,
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = { onOpenIndex(0) }
                    )
                }

                PageChip(
                    page = page,
                    contentColor = contentColor,
                    quietContentColor = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier.align(Alignment.Center),
                    onClick = onPageClick
                )

                JuzChip(
                    juz = juz,
                    juzPercent = juzPercent,
                    pageInJuz = pageInJuz,
                    pagesInJuz = pagesInJuz,
                    showJuzProgressPercent = showJuzProgressPercent,
                    showJuzProgressPages = showJuzProgressPages,
                    color = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = { onOpenIndex(1) }
                )
            }
        }
        }
    }
}

/**
 * Overflow ("more") menu shared by both header layouts: the secondary reading actions live behind a
 * single button so the strip stays uncluttered. Owns its own open/closed state.
 *
 * "About" sits at the bottom, below a divider, and is NOT one of [HeaderActions]: it is not a
 * reading tool you would ever want on the bar, and — being the answer to "what is this app, and
 * who made it" — it has to be somewhere a reader can always find it. Everything above the divider
 * can be moved or hidden from settings; this cannot, which is the point of it being here.
 *
 * Because of it the menu is never empty, so the overflow button no longer steps aside when every
 * reading action has been moved onto the bar.
 */
@Composable
private fun HeaderMoreMenu(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    quietContentColor: Color,
    hasBookmark: Boolean,
    bookmarkMenu: BookmarkMenu,
    fillScreen: Boolean,
    paletteId: String,
    isVisible: (String) -> Boolean,
    visibleBarActionIds: Set<String>,
    onOpenSearch: () -> Unit,
    onStats: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onPaletteChange: (String) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
    onAbout: () -> Unit,
) {
    // Actions assigned to the menu plus any preferred bar actions that did not fit this window.
    val menuActions = HeaderActions.filter { isVisible(it.id) && it.id !in visibleBarActionIds }

    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "\u0627\u0644\u0645\u0632\u064a\u062f",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            menuActions.forEach { action ->
                val enabled = when (action.id) {
                    "bookmark" -> hasBookmark
                    else -> true
                }
                DropdownMenuItem(
                    text = { Text(headerActionLabel(action, fillScreen)) },
                    leadingIcon = {
                        Icon(
                            imageVector = headerActionIcon(action, fillScreen),
                            contentDescription = null,
                            tint = headerActionDefaultTint(
                                action.id, hasBookmark, fillScreen,
                                quietContentColor, quietContentColor
                            )
                        )
                    },
                    enabled = enabled,
                    onClick = {
                        showMoreMenu = false
                        if (action.id == "theme") {
                            showThemeMenu = true
                        } else if (action.id == "bookmark") {
                            showBookmarkMenu = true
                        } else {
                            runHeaderAction(
                                action.id, onOpenSearch,
                                onStats, onOpenNotes, onOpenIndex, onToggleFillScreen, onHideHeader, onListen, onSharePage, onPageFawaed
                            )
                        }
                    }
                )
            }

            if (menuActions.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("دليل الميزات") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = quietContentColor
                    )
                },
                onClick = {
                    showMoreMenu = false
                    openFeatureGuide(context, FeatureGuide.HUB)
                }
            )
            DropdownMenuItem(
                text = { Text("حول التطبيق") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = quietContentColor
                    )
                },
                onClick = {
                    showMoreMenu = false
                    onAbout()
                }
            )
        }
        // Second menu on the same anchor: picking the theme without leaving the reader.
        PaletteMenu(
            expanded = showThemeMenu,
            paletteId = paletteId,
            onDismiss = { showThemeMenu = false },
            onPaletteChange = onPaletteChange
        )
        // And the four bookmarks, to pick which one to go back to.
        BookmarkJumpMenu(
            expanded = showBookmarkMenu,
            menu = bookmarkMenu,
            onDismiss = { showBookmarkMenu = false },
        )
    }
}

/** The actions the user placed on the bar itself, each drawn in the color chosen for it. Actions
 *  left in the More menu are skipped here — [HeaderMoreMenu] renders exactly the complement. */
@Composable
private fun HeaderBarActions(
    btnSize: Dp,
    iconSize: Dp,
    quietContentColor: Color,
    activeContentColor: Color,
    fillScreen: Boolean,
    hasBookmark: Boolean,
    bookmarkMenu: BookmarkMenu,
    paletteId: String,
    isVisible: (String) -> Boolean,
    visibleBarActionIds: Set<String>,
    buttonTint: (String, Color) -> Color,
    onOpenSearch: () -> Unit,
    onStats: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onPaletteChange: (String) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
) {
    HeaderActions.forEach { action ->
        if (!isVisible(action.id) || action.id !in visibleBarActionIds) return@forEach
        val tint = buttonTint(
            action.id,
            headerActionDefaultTint(
                action.id, hasBookmark, fillScreen,
                quietContentColor, activeContentColor
            )
        )
        if (action.id == "theme") {
            HeaderThemeButton(btnSize, iconSize, tint, paletteId, onPaletteChange)
            return@forEach
        }
        if (action.id == "bookmark") {
            HeaderBookmarkButton(
                btnSize, iconSize,
                tint = if (hasBookmark) tint else quietContentColor.copy(alpha = 0.38f),
                enabled = hasBookmark,
                label = action.title,
                icon = headerActionIcon(action, fillScreen),
                bookmarkMenu = bookmarkMenu,
            )
            return@forEach
        }
        val enabled = when (action.id) {
            "bookmark" -> hasBookmark
            else -> true
        }
        IconButton(
            onClick = {
                runHeaderAction(
                    action.id, onOpenSearch,
                    onStats, onOpenNotes, onOpenIndex, onToggleFillScreen, onHideHeader, onListen, onSharePage, onPageFawaed
                )
            },
            enabled = enabled,
            modifier = Modifier.size(btnSize)
        ) {
            Icon(
                imageVector = headerActionIcon(action, fillScreen),
                contentDescription = headerActionLabel(action, fillScreen),
                tint = if (enabled) tint else quietContentColor.copy(alpha = 0.38f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** The bookmark as a bar button: it opens the four bookmarks, each with where it stands. */
@Composable
private fun HeaderBookmarkButton(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    enabled: Boolean,
    label: String,
    icon: ImageVector,
    bookmarkMenu: BookmarkMenu,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        BookmarkJumpMenu(
            expanded = expanded,
            menu = bookmarkMenu,
            onDismiss = { expanded = false },
        )
    }
}

/** The palette picker as its own bar button; it opens the same list the More menu shows. */
@Composable
private fun HeaderThemeButton(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = "\u0627\u0644\u0645\u0638\u0647\u0631",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        PaletteMenu(
            expanded = expanded,
            paletteId = paletteId,
            onDismiss = { expanded = false },
            onPaletteChange = onPaletteChange
        )
    }
}

/** The six reading themes as menu items, each swatch painted in that theme's own paper and ink. */
@Composable
private fun PaletteMenu(
    expanded: Boolean,
    paletteId: String,
    onDismiss: () -> Unit,
    onPaletteChange: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MushafPalettes.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label) },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(16.dp),
                        shape = CircleShape,
                        color = choice.paper,
                        border = BorderStroke(1.dp, choice.ink.copy(alpha = 0.55f))
                    ) {}
                },
                trailingIcon = if (choice.id == paletteId) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                onClick = {
                    onDismiss()
                    onPaletteChange(choice.id)
                }
            )
        }
    }
}

private fun runHeaderAction(
    id: String,
    onOpenSearch: () -> Unit,
    onStats: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
) {
    when (id) {
        "search" -> onOpenSearch()
        "share" -> onSharePage()
        "fawaed" -> onPageFawaed()
        "stats" -> onStats()
        "notes" -> onOpenNotes()
        "index" -> onOpenIndex(0)
        "fill" -> onToggleFillScreen()
        "hide" -> onHideHeader()
        "listen" -> onListen()
    }
}

/** Fill-screen is the one action that reads as a state, so its icon and wording follow it. */
private fun headerActionIcon(action: HeaderAction, fillScreen: Boolean) =
    if (action.id == "fill" && fillScreen) Icons.Filled.FullscreenExit else action.barIcon

private fun headerActionLabel(action: HeaderAction, fillScreen: Boolean) =
    if (action.id == "fill" && fillScreen) "\u0639\u0631\u0636 \u0627\u0644\u0635\u0641\u062d\u0629 \u0643\u0627\u0645\u0644\u0629" else action.title

/** What an action is tinted when its color is left on "\u0647\u0627\u062f\u0626" — the look it had before the setting
 *  existed: the bookmarks flag whether they point anywhere, fill-screen flags that it is on. */
private fun headerActionDefaultTint(
    id: String,
    hasBookmark: Boolean,
    fillScreen: Boolean,
    quiet: Color,
    active: Color,
): Color = when (id) {
    "bookmark" -> if (hasBookmark) BookmarkGoldColor else quiet
    "fill" -> if (fillScreen) active else quiet
    else -> quiet
}

/** Current wall-clock time as h:mm (12-hour) with a ص/م suffix, in Arabic digits. */
private fun formatClock(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = cal.get(Calendar.MINUTE)
    val suffix = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
    return "${"$h:${"%02d".format(m)}".toArabicDigits()} $suffix"
}

/** Elapsed session time as m:ss (or h:mm:ss past an hour) in Arabic digits. */
private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val str = if (h > 0) "$h:${"%02d".format(m)}:${"%02d".format(s)}" else "$m:${"%02d".format(s)}"
    return str.toArabicDigits()
}

internal fun headerStatusColor(id: String, muted: Color): Color =
    when (id) {
        "red" -> StatusRedColor
        "green" -> StatusGreenColor
        "gold" -> StatusGoldColor
        "blue" -> StatusBlueColor
        else -> muted
    }
