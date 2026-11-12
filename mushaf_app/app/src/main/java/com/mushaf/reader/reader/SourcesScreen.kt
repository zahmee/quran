package com.mushaf.reader.reader

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar

/**
 * The credit the Tafsir Center's texts carry. Its data is licensed CC BY 4.0, which asks for the
 * source, a link to the licence and a statement of the changes. The licence accepts a link to a
 * page holding them, so inside the app that page is this screen; a shared text leaves the app and
 * carries its own line.
 */
internal object CenterAttribution {
    const val CENTER_NAME = "مركز تفسير للدراسات القرآنية"
    const val CENTER_URL = "https://tafsir.net"
    const val LICENSE_NAME = "CC BY 4.0"
    const val LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"

    /** The `saadi_supplement_version` the bundled database records for its QuranEnc rows. */
    const val SUPPLEMENT_VERSION = "1.0.0"

    private const val LICENSED = "من قاعدة بيانات $CENTER_NAME ($CENTER_URL)، " +
        "برخصة المشاع الإبداعي $LICENSE_NAME ($LICENSE_URL)"

    const val SAADI_CREDIT = "المصدر: تفسير السعدي، $LICENSED، مع تعديلات في العرض وتصحيحات يسيرة."
    const val SUPPLEMENT_CREDIT =
        "وفيه تكملة من موسوعة القرآن الكريم (QuranEnc.com، الإصدار $SUPPLEMENT_VERSION)."
    const val MUKHTASAR_CREDIT =
        "المصدر: المختصر في تفسير القرآن الكريم، $LICENSED، مع إبراز الكلمات المفسَّرة وإصلاح علامات يسيرة."
    const val FAWAED_CREDIT = "المصدر: فوائد «المختصر في تفسير القرآن الكريم»، $LICENSED."
    const val SURAH_CARD_CREDIT = "المصدر: $LICENSED."
    const val TAJWEED_CREDIT = "المصدر: أحكام التجويد، $LICENSED، مع إصلاح يسير في التنسيق."
}

private const val COMPLEX_NAME = "مجمع الملك فهد لطباعة المصحف الشريف"

internal const val MUYASSAR_CREDIT = "المصدر: التفسير الميسر، الإصدار 3.0 — $COMPLEX_NAME."
internal const val GHARIB_CREDIT = "المصدر: الميسر في غريب القرآن الكريم، الطبعة الثانية — $COMPLEX_NAME."

/** A licence's name wrapped across two lines reads wrongly in a right-to-left paragraph. */
private fun String.unbroken(): String = replace(' ', '\u00A0')

private data class SourceLink(val label: String, val url: String)

private data class SourceEntry(
    val title: String,
    val body: String,
    val links: List<SourceLink> = emptyList(),
)

private data class SourceGroup(
    val title: String,
    val icon: ImageVector,
    val entries: List<SourceEntry>,
)

private val ComplexLink = SourceLink("موقع المجمع", "https://qurancomplex.gov.sa/quran-dev/")
private val CenterLink = SourceLink("موقع مركز تفسير", CenterAttribution.CENTER_URL)
private val CcByLink = SourceLink("نص رخصة ${CenterAttribution.LICENSE_NAME}", CenterAttribution.LICENSE_URL)
private val ApacheLink = SourceLink("نص رخصة Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0")

private val SourceGroups = listOf(
    SourceGroup(
        title = "المصحف",
        icon = Icons.Outlined.Verified,
        entries = listOf(
            SourceEntry(
                title = "صفحات المصحف",
                body = "نسخة من مصحف المدينة المنورة الصادر عن $COMPLEX_NAME.",
                links = listOf(ComplexLink),
            ),
            SourceEntry(
                title = "نص الآيات وتقسيمها",
                body = "نص الآيات بالرسم العثماني المستخدم في البحث والنسخ، وأسماء السور وتقسيم الآيات، " +
                    "من واجهة Quran.com البرمجية (الإصدار الرابع). أما مواضع الآيات على الصفحات فأُعدّت " +
                    "لهذا التطبيق على صوره.",
                links = listOf(SourceLink("موقع Quran.com", "https://quran.com")),
            ),
        ),
    ),
    SourceGroup(
        title = "التفسير ومعاني الكلمات",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        entries = listOf(
            SourceEntry(
                title = "تفسير السعدي",
                body = "«تيسير الكريم الرحمن في تفسير كلام المنان» لعبد الرحمن بن ناصر السعدي، من قاعدة " +
                    "بيانات ${CenterAttribution.CENTER_NAME}، برخصة المشاع الإبداعي " +
                    "${CenterAttribution.LICENSE_NAME.unbroken()}.\n\n" +
                    "وقد عُدّل: جُمعت الآيات في مقاطع، وحُذف المكرر وعلامات العمل، وصُححت أخطاء نسخ يسيرة، " +
                    "وعُرض نص الآيات من مصحف التطبيق، وأُظهر بعض حواشي المحقق فقط.\n\n" +
                    "وأُضيفت أربع تكملات من موسوعة القرآن الكريم، الإصدار " +
                    "${CenterAttribution.SUPPLEMENT_VERSION}، موسومة في مواضعها: البقرة ١٠١ و١٤٣ و١٨١، " +
                    "والنساء ١١٧. ومنها أيضاً تقسيم الآيات إلى مقاطع.",
                links = listOf(
                    CenterLink,
                    CcByLink,
                    SourceLink("موسوعة القرآن الكريم", "https://quranenc.com"),
                ),
            ),
            SourceEntry(
                title = "المختصر في تفسير القرآن الكريم",
                body = "من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، برخصة المشاع الإبداعي " +
                    "${CenterAttribution.LICENSE_NAME.unbroken()}.\n\n" +
                    "وقد عُدّل: أُبرزت الكلمات المفسَّرة بدل علاماتها، وأُصلحت علامات مختلة في مواضع يسيرة، " +
                    "ولم تُغيَّر كلماته.",
                links = listOf(CenterLink, CcByLink),
            ),
            SourceEntry(
                title = "التفسير الميسر",
                body = "الإصدار 3.0، من إصدارات $COMPLEX_NAME.",
                links = listOf(ComplexLink),
            ),
            SourceEntry(
                title = "معاني الكلمات",
                body = "«الميسر في غريب القرآن الكريم»، الطبعة الثانية، من إصدارات $COMPLEX_NAME.",
                links = listOf(ComplexLink),
            ),
            SourceEntry(
                title = "أسباب النزول",
                body = "لمئتي آية، من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، برخصة المشاع الإبداعي " +
                    "${CenterAttribution.LICENSE_NAME.unbroken()}. ولا تذكر القاعدة الكتاب الذي نُقلت منه.\n\n" +
                    "وقد عُدّل: حُذفت الوسوم وأكثر حواشي المحقق وأُبقي تخريج الأحاديث، وحُذف صفّان مكرران " +
                    "منسوبان إلى غير آيتهما.",
                links = listOf(CenterLink, CcByLink),
            ),
            SourceEntry(
                title = "البحث بالجذر",
                body = "جذور كلمات القرآن مأخوذة من صرف الكلمات في قاعدة بيانات ${CenterAttribution.CENTER_NAME}، " +
                    "برخصة المشاع الإبداعي ${CenterAttribution.LICENSE_NAME.unbroken()}.\n\n" +
                    "وقد عُدّل: عُدّ جذرًا ما سمّاه صرف الكلمة مادتها، فخرجت الأدوات والضمائر وفواتح السور، " +
                    "وقُسمت الكلمة التي يكتبها المصحف كلمة واحدة على جزأيها، وحُسبت أعداد المواضع من البيانات، " +
                    "وجُمعت صيغ الكلمة التي لا تختلف إلا بحرف عطف أو جر أو بأداة التعريف.",
                links = listOf(CenterLink, CcByLink),
            ),
            SourceEntry(
                title = "التجويد",
                body = "ملاحظة لكل آية في أحكام تلاوتها، من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، " +
                    "برخصة المشاع الإبداعي ${CenterAttribution.LICENSE_NAME.unbroken()}. ولا تذكر القاعدة " +
                    "الكتاب الذي نُقلت منه.\n\n" +
                    "وقد عُدّل: وُصلت أسطر الطبعة المقطوعة وسط الجمل، وحُذفت فاصلة أو نقطتان في أول بعض " +
                    "الملاحظات، وجُمعت الآيات التي كررت القاعدة ملاحظتها الواحدة تحت كل منها، وصارت أرقام " +
                    "القوائم التي سقطت نقاطًا، وحُذفت حواشي الطبعة وعلامات سقط ما فيها، وأُعيدت علامتا " +
                    "التنوين في موضع، وأُصلح قوس مختلف الطرفين حول بعض الكلمات القرآنية.",
                links = listOf(CenterLink, CcByLink),
            ),
            SourceEntry(
                title = "فوائد الصفحة",
                body = "الفوائد التي يستخلصها «المختصر في تفسير القرآن الكريم» من آيات كل صفحة من صفحات " +
                    "المصحف، من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، برخصة المشاع الإبداعي " +
                    "${CenterAttribution.LICENSE_NAME.unbroken()}. ولم يُغيَّر نصها.",
                links = listOf(CenterLink, CcByLink),
            ),
            SourceEntry(
                title = "عن السورة",
                body = "مكان نزول السورة وترتيبه، وطبقتها في الطول، وسجداتها، ومقصدها، وفصول في أسمائها " +
                    "ونزولها وفضائلها وعدد آياتها، وإحصاءات منها مطلعها وعدد كلماتها وحروفها، من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، برخصة " +
                    "المشاع الإبداعي ${CenterAttribution.LICENSE_NAME.unbroken()}.\n\n" +
                    "وقد عُدّل: حُذفت الوسوم وأكثر حواشي المحقق وأُبقي تخريج الأحاديث، وأُغلقت حواشٍ لم تُغلق " +
                    "في المصدر، وأُعيدت حاشيتان نقلتهما الطبعة إلى الصفحة التالية إلى موضعهما، ووُسمت سبعة " +
                    "فصول قُطع نصها في المصدر. وتُرك عدٌّ ثانٍ للآيات أدقّ من أن يفيد القارئ.",
                links = listOf(CenterLink, CcByLink),
            ),
        ),
    ),
    SourceGroup(
        title = "مكتبة التفاسير",
        icon = Icons.Outlined.Download,
        entries = listOf(
            SourceEntry(
                title = "تفسير ابن كثير، وتفسير الطبري، وتفسير البغوي، وإعراب القرآن",
                body = "«تفسير القرآن العظيم» لابن كثير، و«جامع البيان عن تأويل آي القرآن» للطبري، و«معالم التنزيل» " +
                    "للبغوي، و«إعراب القرآن الكريم»، من قاعدة بيانات ${CenterAttribution.CENTER_NAME}، برخصة المشاع " +
                    "الإبداعي ${CenterAttribution.LICENSE_NAME.unbroken()}. لا تُضمَّن في التطبيق، بل تُنزَّل حين " +
                    "تطلبها من مكتبة التفاسير، من مستودع التطبيق على GitHub.\n\n" +
                    "وقد عُدّل: حُذفت الوسوم وعلامات العمل وأكثر حواشي المحقق، وأُبقيت التصحيحات وتخريج الأحاديث، " +
                    "وجُمعت الآيات التي كررت القاعدة شرحها الواحد تحت كل منها، وأُغلقت حواشٍ لم تُغلق في المصدر، " +
                    "ووُسمت ثمانية مواضع قُطع نصها في المصدر. وقائمة التصحيحات كاملة منشورة مع الكتب.",
                links = listOf(
                    CenterLink,
                    CcByLink,
                    SourceLink("قائمة التصحيحات", "https://github.com/zahmee/natlu-data/blob/main/CORRECTIONS.md"),
                ),
            ),
            SourceEntry(
                title = "تفسير القرطبي، وفتح القدير، والتحرير والتنوير",
                body = "«الجامع لأحكام القرآن» للقرطبي، و«فتح القدير» للشوكاني، و«التحرير والتنوير» لمحمد الطاهر " +
                    "ابن عاشور، من مشروع OpenITI (الإصدار 2025.1.9) نقلًا عن موقع التفاسير altafsir.com، برخصة " +
                    "المشاع الإبداعي ${"CC BY-NC-SA 4.0".unbroken()}: يُنسب إلى مصدره، ولا يُستعمل استعمالًا تجاريًا، " +
                    "وما بُني عليه يُنشر بالرخصة نفسها. والتطبيق مجاني بلا إعلانات ولا مزايا مدفوعة. تُنزَّل حين " +
                    "تطلبها من مكتبة التفاسير.\n\n" +
                    "وقد عُدّل: وُصلت أسطر الطبعة المقطوعة، وحُذفت علامات التقسيم التي يضعها المشروع، ووُضعت الآيات " +
                    "المقتبسة بين قوسين مع موضعها. ولا يتضمن المصدر شرح الآية ٥٦ من الأحزاب في تفسير القرطبي، " +
                    "فوُضعت في موضعها عبارة تبيّن ذلك.",
                links = listOf(
                    SourceLink("مشروع OpenITI", "https://doi.org/10.5281/zenodo.3082463"),
                    SourceLink("نص رخصة CC BY-NC-SA 4.0", "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
                    SourceLink("موقع التفاسير", "https://www.altafsir.com"),
                ),
            ),
        ),
    ),
    SourceGroup(
        title = "التلاوة",
        icon = Icons.Outlined.Headphones,
        entries = listOf(
            SourceEntry(
                title = "التلاوات ومواضع الآيات فيها",
                body = "من موقع MP3Quran، تُجلب حين تشغّلها أو تنزّلها. وحقوق التلاوات لأصحابها.",
                links = listOf(SourceLink("موقع MP3Quran", "https://www.mp3quran.net")),
            ),
        ),
    ),
    SourceGroup(
        title = "الخطوط",
        icon = Icons.Outlined.TextFields,
        entries = listOf(
            SourceEntry(
                title = "IBM Plex Sans Arabic و Noto Naskh Arabic",
                body = "خط الواجهة وخط قراءة التفسير، وكلاهما برخصة الخطوط المفتوحة ${"SIL OFL 1.1".unbroken()}.",
                links = listOf(SourceLink("نص رخصة OFL", "https://openfontlicense.org")),
            ),
        ),
    ),
    SourceGroup(
        title = "المكتبات والخدمات",
        icon = Icons.Outlined.Code,
        entries = listOf(
            SourceEntry(
                title = "مكتبات مفتوحة المصدر",
                body = "Kotlin و Compose و Room و DataStore و WorkManager و Media3 " +
                    "و Material 3 وأيقوناته و Coil، وكلها برخصة ${"Apache 2.0".unbroken()}.",
                links = listOf(ApacheLink),
            ),
            SourceEntry(
                title = "خدمات Google",
                body = "Firebase لحساب النسخ السحابي وحفظ نسخه، ويعمل فقط إن أنشأت حساباً أو سجّلت الدخول. " +
                    "و Google Play لفحص التحديثات وتنزيلها.",
                links = listOf(SourceLink("موقع Firebase", "https://firebase.google.com")),
            ),
            SourceEntry(
                title = "التقويم الهجري",
                body = "تقويم أم القرى كما يوفّره نظام Android نفسه.",
            ),
        ),
    ),
)

/**
 * The one place the app names everything it uses. It is a dialog because it opens from the ayah
 * explanation, whose bottom sheet is a window of its own: anything drawn in the activity would sit
 * behind it, and closing the sheet instead would lose the reader's tab and scroll position.
 */
@Composable
internal fun SourcesDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        StatusBarIconsForBackground()
        SourcesScreen(onBack = onDismiss)
    }
}

/**
 * A full-screen dialog is a window of its own and starts with light status-bar icons, which vanish
 * on the light paper themes. Call inside the dialog's content: dark icons on a light background.
 */
@Composable
internal fun StatusBarIconsForBackground() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = light
    }
}

@Composable
private fun SourcesScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(title = "المصادر والتراخيص", onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = "مصدر كل ما يستخدمه التطبيق. ولا تشمل رخصة شفرة التطبيق شيئاً من هذه المواد؛ " +
                            "فلكل منها شروط مصدره.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(SourceGroups, key = { it.title }) { group -> SourceGroupPanel(group) }
            }
        }
    }
}

@Composable
private fun SourceGroupPanel(group: SourceGroup) {
    MushafPanel {
        MushafSectionHeader(title = group.title, icon = group.icon)
        Spacer(Modifier.height(12.dp))
        group.entries.forEachIndexed { index, entry ->
            if (index > 0) MushafSoftDivider(verticalPadding = 12.dp)
            SourceEntryContent(entry)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceEntryContent(entry: SourceEntry) {
    val context = LocalContext.current
    Text(
        text = entry.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = entry.body,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (entry.links.isNotEmpty()) {
        FlowRow(modifier = Modifier.padding(top = 4.dp)) {
            entry.links.forEach { link ->
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(link.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
