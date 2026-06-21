package com.mushaf.reader.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mushaf.reader.R

/** "About" screen: app identity, trust notes, and Mushaf source attribution. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
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
                        text = "حول التطبيق",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val openWeb: (String) -> Unit = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                val openEmail: () -> Unit = {
                    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@cdit.co")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "ملاحظات حول تطبيق قرآن القارئ")
                    }
                    runCatching { context.startActivity(mail) }
                }

                AppIdentityPanel(versionName)
                TrustPanel()
                MushafSourcePanel(
                    onVisitSite = { openWeb("https://qurancomplex.gov.sa") }
                )
                DeveloperPanel(
                    onVisitSite = { openWeb("https://cdit.co") }
                )
                ContactPanel(
                    onWhatsapp = { openWeb("https://wa.me/966502010911") },
                    onEmail = openEmail,
                    onWebsite = { openWeb("https://cdit.co/contact.html") }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppIdentityPanel(versionName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "قرآن القارئ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (versionName.isNotBlank()) "الإصدار $versionName" else "تطبيق مصحف للقراءة اليومية",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueChip("مجاني")
                ValueChip("بلا إعلانات")
                ValueChip("قراءة هادئة")
            }
        }
    }
}

@Composable
private fun ValueChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrustPanel() {
    Panel {
        SectionTitle("الثقة والشفافية")
        Spacer(Modifier.height(12.dp))
        InfoRow(
            icon = Icons.Outlined.CheckCircle,
            title = "مجاني بالكامل",
            body = "بلا رسوم، بلا اشتراكات، وبلا إعلانات."
        )
        SoftDivider()
        InfoRow(
            icon = Icons.Outlined.PrivacyTip,
            title = "خصوصيتك على جهازك",
            body = "لا يجمع التطبيق بيانات شخصية. تبقى إحصائيات القراءة وتقدّم الختمة محفوظة محلياً."
        )
        SoftDivider()
        InfoRow(
            icon = Icons.Outlined.FavoriteBorder,
            title = "مصمم للقراءة",
            body = "واجهة هادئة تركّز على صفحة المصحف وتقلّل المشتتات."
        )
    }
}

@Composable
private fun DeveloperPanel(onVisitSite: () -> Unit) {
    Panel {
        InfoHeader(
            icon = Icons.Outlined.Business,
            title = "الجهة المطوّرة",
            body = "تطبيق قرآن القارئ من تطوير مؤسسة إبداع التطوير والبرمجة لتقنية المعلومات (CDIT)، مؤسسة سعودية متخصصة في تطوير البرمجيات وحلول الأعمال الرقمية من خميس مشيط."
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "السجل التجاري: 5855353571",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        ActionButton(
            text = "زيارة موقع CDIT الرسمي",
            icon = Icons.Outlined.Business,
            onClick = onVisitSite
        )
    }
}

@Composable
private fun ContactPanel(
    onWhatsapp: () -> Unit,
    onEmail: () -> Unit,
    onWebsite: () -> Unit,
) {
    Panel {
        SectionTitle("تواصل معنا")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "نسعد باستقبال الملاحظات الفنية والاقتراحات التي تساعدنا على تحسين التطبيق.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        ActionButton(
            text = "تواصل عبر واتساب",
            icon = Icons.Outlined.ChatBubbleOutline,
            onClick = onWhatsapp
        )
        Spacer(Modifier.height(8.dp))
        ActionButton(
            text = "إرسال بريد إلكتروني",
            icon = Icons.Outlined.Email,
            onClick = onEmail
        )
        Spacer(Modifier.height(8.dp))
        ActionButton(
            text = "صفحة التواصل الرسمية",
            icon = Icons.Outlined.Language,
            onClick = onWebsite
        )
    }
}

@Composable
private fun MushafSourcePanel(onVisitSite: () -> Unit) {
    Panel {
        InfoHeader(
            icon = Icons.Outlined.Verified,
            title = "مصدر المصحف",
            body = "تعتمد صفحات المصحف على نسخة من مصحف المدينة المنورة، الصادر عن مجمع الملك فهد لطباعة المصحف الشريف."
        )

        Spacer(Modifier.height(14.dp))
        ActionButton(
            text = "زيارة الموقع الرسمي للمجمع",
            icon = Icons.Outlined.Verified,
            onClick = onVisitSite
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(icon)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoHeader(icon: ImageVector, title: String, body: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(icon)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun SoftDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

// --- small UI helpers (kept local to match the app's per-screen card style) ---

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
