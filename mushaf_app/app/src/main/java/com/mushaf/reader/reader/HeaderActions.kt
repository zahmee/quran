package com.mushaf.reader.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A reader action the header can offer three ways: hidden, sitting on the top bar in a color the
 * user picks, or tucked inside the More menu.
 *
 * This list is the single source of order: the bar draws the on-bar actions in it, the More menu
 * lists the rest in it, and the settings screen presents them in it too.
 */
internal data class HeaderAction(
    val id: String,
    val title: String,
    /** One line explaining the action in the settings list. */
    val body: String,
    val settingsIcon: ImageVector,
    val barIcon: ImageVector,
)

internal val HeaderActions = listOf(
    HeaderAction(
        "search", "البحث", "بحث في نص المصحف وأسماء السور.",
        Icons.Outlined.Search, Icons.Filled.Search,
    ),
    HeaderAction(
        "listen", "الاستماع للتلاوة", "تشغيل التلاوة من أول آية في الصفحة.",
        Icons.Outlined.Headphones, Icons.Filled.Headphones,
    ),
    HeaderAction(
        "fawaed", "فوائد الصفحة", "ما يُستفاد من آيات الصفحة الحالية، من المختصر في التفسير.",
        Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb,
    ),
    HeaderAction(
        "share", "مشاركة الصفحة", "إرسال الصفحة الحالية صورةً كما في المصحف.",
        Icons.Outlined.Share, Icons.Filled.Share,
    ),
    HeaderAction(
        "bookmark", "العلامة المرجعية", "الانتقال إلى الفاصل الذهبي المحفوظ.",
        Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark,
    ),
    HeaderAction(
        "bookmark2", "العلامة المرجعية الثانية", "الانتقال إلى الفاصل البنفسجي المحفوظ.",
        Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark,
    ),
    HeaderAction(
        "stats", "إحصائيات القراءة", "متابعة القراءة والختمة والجلسات.",
        Icons.Outlined.QueryStats, Icons.Filled.BarChart,
    ),
    HeaderAction(
        "index", "الفهرس", "فتح السور والأجزاء.",
        Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook,
    ),
    HeaderAction(
        "theme", "المظهر", "اختيار مظهر المصحف من الأوضاع الستة.",
        Icons.Outlined.Palette, Icons.Filled.Palette,
    ),
    HeaderAction(
        "fill", "عرض الصفحة كاملة", "التبديل بين عرض الصفحة كاملة وملء الشاشة بها.",
        Icons.Outlined.WidthFull, Icons.Filled.Fullscreen,
    ),
    HeaderAction(
        "hide", "توسيع مساحة القراءة", "إخفاء رأس الصفحة للقراءة بلا مشتتات.",
        Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowUp,
    ),
)
