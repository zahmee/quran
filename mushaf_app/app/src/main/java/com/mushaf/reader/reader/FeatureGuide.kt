package com.mushaf.reader.reader

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * The feature guides on the app's site (docs/features/ in this repository): one page per feature,
 * each with a real recording of the app. The app has no copy of them; it opens them in the browser.
 */
internal object FeatureGuide {
    const val HUB = "https://zahmee.github.io/quran/features/"
    const val WHATS_NEW = HUB + "whats-new.html"

    /** One guide page, by the slug of its file in docs/features/, opened at [section] when given. */
    fun page(slug: String, section: String? = null) =
        "$HUB$slug.html" + (section?.let { "#$it" } ?: "")
}

/** Opens a guide page, and says so when the device has nothing that can open a web link. */
internal fun openFeatureGuide(context: Context, url: String) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.isSuccess
    if (!opened) {
        Toast.makeText(
            context,
            "تعذّر فتح الشرح: لا يوجد على الجهاز متصفح يفتح الروابط.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** One line of the "what's new" card, and the guide page that shows it. */
internal data class WhatsNewItem(
    val title: String,
    val body: String,
    val slug: String,
    /** The lesson on that page, for a page that holds more than one. */
    val section: String? = null,
)

/**
 * What the card shown once after an update tells the reader.
 *
 * [RELEASE] is the key the card was last seen at. Raise it, and replace [items], whenever a release
 * brings something worth showing; a release that does not touch it shows nothing.
 */
internal object WhatsNew {
    const val RELEASE = 67

    val items = listOf(
        WhatsNewItem(
            "بحثك لا يضيع",
            "افتح نتيجة ثم ارجع إلى البحث، تجد الكلمة والنتائج وموضعك كما تركتها، والنتيجة التي فتحتها مميّزة.",
            "search",
            "return",
        ),
        WhatsNewItem(
            "زر «نتائج البحث»",
            "بعد فتح نتيجة يظهر أسفل الصفحة زر يعيدك إلى نتائجك بلمسة.",
            "search",
            "return",
        ),
    )
}

/** The card shown once after an update: what the release brought, each with a way to its guide. */
@Composable
internal fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ما الجديد في هذا الإصدار") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WhatsNew.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    TextButton(
                        onClick = { openFeatureGuide(context, FeatureGuide.page(item.slug, item.section)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("شاهد الشرح") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("حسنًا") } },
        dismissButton = {
            TextButton(onClick = { openFeatureGuide(context, FeatureGuide.HUB) }) { Text("كل الشروحات") }
        },
    )
}
