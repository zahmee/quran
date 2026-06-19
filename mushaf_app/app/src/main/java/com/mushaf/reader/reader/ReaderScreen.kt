package com.mushaf.reader.reader

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val pageCount = viewModel.pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (viewModel.initialPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()
    var showGoTo by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }
    var showIndex by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var headerVisible by remember { mutableStateOf(true) }
    val selected = viewModel.selectedAyah

    val jumpToPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, pageCount - 1)) }
    }

    BackHandler(enabled = showStatsScreen) { showStatsScreen = false }
    BackHandler(enabled = showIndex) { showIndex = false }
    BackHandler(enabled = showSearch) { showSearch = false }
    // Registered after showIndex so Back from About returns to the index it was opened from.
    BackHandler(enabled = showAbout) { showAbout = false }
    // When the header is hidden, Back brings it back instead of leaving the app.
    BackHandler(enabled = !showStatsScreen && !showIndex && !showSearch && !showAbout && !headerVisible) {
        headerVisible = true
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.clearSelection()
        viewModel.saveLastPage(pagerState.currentPage + 1)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val currentPage = pagerState.currentPage + 1
                val pageInfo = viewModel.markersForPage(currentPage).firstOrNull()
                val juzInfo = viewModel.juzInfoForPage(currentPage)

                if (headerVisible) {
                    ReaderHeader(
                        page = currentPage,
                        surahNumber = pageInfo?.surahNumber,
                        surah = pageInfo?.surahNameAr,
                        ayahCount = pageInfo?.let { viewModel.surahAyahCount(it.surahNumber) },
                        juz = juzInfo.juz,
                        pageInJuz = juzInfo.pageInJuz,
                        pagesInJuz = juzInfo.pagesInJuz,
                        dark = viewModel.darkTheme,
                        hasBookmark = viewModel.bookmarks.isNotEmpty(),
                        fillScreen = viewModel.fillScreen,
                        onOpenIndex = { showIndex = true },
                        onOpenSearch = { showSearch = true },
                        onToggleFillScreen = { viewModel.toggleFillScreen() },
                        onHideHeader = { headerVisible = false },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onBookmarkJump = {
                            viewModel.bookmarkJumpPage()?.let { p ->
                                scope.launch { pagerState.scrollToPage((p - 1).coerceIn(0, pageCount - 1)) }
                            }
                        },
                        onStats = {
                            viewModel.openStats()
                            showStatsScreen = true
                        },
                        onPageClick = { showGoTo = true }
                    )
                }

                ReaderPager(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    viewModel = viewModel,
                    pagerState = pagerState,
                    selected = selected,
                    fillScreen = viewModel.fillScreen
                )
            }

            // When the header is hidden, a small floating button restores it.
            if (!headerVisible) {
                ShowHeaderButton(
                    onClick = { headerVisible = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        if (showGoTo) {
            GoToPageDialog(
                pageCount = pageCount,
                onDismiss = { showGoTo = false },
                onGo = { n ->
                    showGoTo = false
                    scope.launch { pagerState.scrollToPage((n - 1).coerceIn(0, pageCount - 1)) }
                }
            )
        }

        if (showStatsScreen) {
            StatsScreen(
                stats = viewModel.fullStats,
                sessions = viewModel.sessions,
                onBack = { showStatsScreen = false }
            )
        }

        if (showIndex) {
            val loaded = viewModel.ayahLoaded
            val surahs = remember(loaded) { viewModel.surahIndex() }
            val juzs = remember(loaded) { viewModel.juzIndex() }
            IndexScreen(
                surahs = surahs,
                juzs = juzs,
                onJump = { page ->
                    showIndex = false
                    jumpToPage(page)
                },
                onAbout = { showAbout = true },
                onBack = { showIndex = false }
            )
        }

        if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
        }

        if (showSearch) {
            SearchScreen(
                onSearch = { q -> viewModel.search(q) },
                onJump = { page ->
                    showSearch = false
                    jumpToPage(page)
                },
                onBack = { showSearch = false }
            )
        }
    }
}

@Composable
private fun ReaderPager(
    modifier: Modifier,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    selected: AyahMarker?,
    fillScreen: Boolean,
) {
    var anchor by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { index ->
            val pageNumber = index + 1
            ZoomablePage(
                model = viewModel.assetModel(pageNumber),
                markers = viewModel.markersForPage(pageNumber),
                imageWidth = viewModel.imageWidth,
                imageHeight = viewModel.imageHeight,
                selectedAyah = selected,
                bookmarkedKeys = viewModel.bookmarks,
                onSelectAyah = { m, off ->
                    viewModel.selectAyah(if (viewModel.selectedAyah == m) null else m)
                    anchor = off
                },
                onEmptyTap = { },
                fillScreen = fillScreen
            )
        }

        if (selected != null) {
            AyahFloatingMenu(
                ayah = selected,
                anchor = anchor,
                boxWidthPx = boxW,
                boxHeightPx = boxH,
                bookmarked = viewModel.isBookmarked(selected.verseKey),
                onBookmark = { viewModel.toggleBookmark(selected) },
                onClose = { viewModel.clearSelection() }
            )
        }
    }
}

@Composable
private fun ReaderHeader(
    page: Int,
    surahNumber: Int?,
    surah: String?,
    ayahCount: Int?,
    juz: Int,
    pageInJuz: Int,
    pagesInJuz: Int,
    dark: Boolean,
    hasBookmark: Boolean,
    fillScreen: Boolean,
    onOpenIndex: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onToggleTheme: () -> Unit,
    onBookmarkJump: () -> Unit,
    onStats: () -> Unit,
    onPageClick: () -> Unit,
) {
    val headerColor = if (dark) Color(0xFF202020) else MaterialTheme.colorScheme.surface
    val contentColor = if (dark) Color(0xFFEDEDED) else MaterialTheme.colorScheme.onSurface

    // Height of the top camera/status-bar band (still reported in immersive mode). The strip
    // below fills it with corner buttons; the center is left clear for a punch-hole/notch.
    val topInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()

    Surface(
        color = headerColor,
        contentColor = contentColor,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Keep clear of a side notch in landscape; the top band is handled by the strip.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
            // Camera-safe top strip: every quick-access button lives here, split between the two
            // corners so the center stays clear for a punch-hole/notch. Trim a little of the inset
            // to tighten the top gap, but never below the icon height (32dp).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(topInset - 6.dp, 32.dp))
            ) {
                // Start corner (top-right in RTL): index, theme, fill-screen.
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenIndex, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "فهرس السور والأجزاء",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onToggleTheme, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "تبديل الوضع",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onToggleFillScreen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (fillScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (fillScreen) "عرض الصفحة كاملة" else "ملء الشاشة",
                            tint = if (fillScreen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // End corner (top-left in RTL): stats, bookmark, hide, search (search at the corner).
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onStats, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "إحصائيات القراءة",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onBookmarkJump,
                        enabled = hasBookmark,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "الذهاب إلى الإشارة المرجعية",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onHideHeader, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "إخفاء الشريط العلوي وملء الشاشة",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onOpenSearch, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "بحث",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Info row — surah identity (start) · page number (center) · juz position (end).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (surah != null) {
                    val surahLabel = buildString {
                        if (surahNumber != null) append("${surahNumber.toArabicDigits()} ")
                        append(surah)
                        if (ayahCount != null && ayahCount > 0) append(" (${ayahCount.toArabicDigits()})")
                    }
                    Text(
                        text = surahLabel,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                    )
                }

                Text(
                    text = page.toArabicDigits(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onPageClick() }
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                )

                Text(
                    text = "الجزء ${juz.toArabicDigits()} (${pageInJuz.toArabicDigits()}/${pagesInJuz.toArabicDigits()})",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                )
            }
        }
    }
}

/** Small round floating button shown (top corner) while the header is hidden; restores it. */
@Composable
private fun ShowHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(8.dp)
            .size(40.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "إظهار الشريط العلوي",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Floating menu that appears over the page, anchored near the tapped position. */
@Composable
private fun AyahFloatingMenu(
    ayah: AyahMarker,
    anchor: Offset,
    boxWidthPx: Int,
    boxHeightPx: Int,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reference = "﴿${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}﴾"
    val shareText = if (ayah.textUthmani.isNotBlank()) "${ayah.textUthmani}\n$reference" else reference

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.toPx() }
    val gap = with(density) { 14.dp.toPx() }

    val x = (anchor.x - menuSize.width / 2f)
        .coerceIn(margin, (boxWidthPx - menuSize.width - margin).coerceAtLeast(margin))
    val above = anchor.y - menuSize.height - gap
    val y = (if (above >= margin) above else anchor.y + gap)
        .coerceIn(margin, (boxHeightPx - menuSize.height - margin).coerceAtLeast(margin))

    Surface(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { menuSize = it }
            .widthIn(max = 330.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "سورة ${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (ayah.isSajdah) SajdahBadge()
            }
            Text(
                text = "صفحة ${ayah.page.toArabicDigits()}  •  الجزء ${ayah.juz.toArabicDigits()}  •  الحزب ${ayah.hizb.toArabicDigits()}  •  الربع ${ayah.rub.toArabicDigits()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onBookmark, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "علامة",
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(shareText))
                        Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "نسخ")
                }
                IconButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "مشاركة")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "إغلاق")
                }
            }
        }
    }
}

@Composable
private fun GoToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val target = text.toIntOrNull()
    val valid = target != null && target in 1..pageCount
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الذهاب إلى صفحة") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (valid) target?.let(onGo) }),
                label = {
                    Text("رقم الصفحة (${1.toArabicDigits()} – ${pageCount.toArabicDigits()})")
                },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onGo) }, enabled = valid) { Text("اذهب") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
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

private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

fun Int.toArabicDigits(): String =
    toString().map { c -> if (c in '0'..'9') arabicDigits[c - '0'] else c }.joinToString("")
