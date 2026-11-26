package com.mushaf.reader.reader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Download
import com.mushaf.reader.data.packs.InstalledPack

/**
 * The source being read, heading its text. Its name opens every source the sheet can show: the app's
 * own, the downloaded books, and the way to download more, with a pin beside each. The pinned ones
 * are the pages the sheet swipes between; the marks under the name count them and the arrows step
 * through them. A source read without being pinned is one more page, the last.
 */
@Composable
internal fun SourceBar(
    title: String,
    index: Int,
    count: Int,
    onStep: (Int) -> Unit,
    current: String,
    pinned: List<String>,
    packs: List<InstalledPack>,
    onChoose: (String) -> Unit,
    onTogglePin: (String) -> Boolean,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val pin: (String) -> Unit = { source ->
        if (!onTogglePin(source)) {
            Toast.makeText(
                context,
                "يمكن تثبيت ${ExplanationSources.MAX_PINNED.toArabicDigits()} مصادر على الأكثر؛ ألغِ تثبيت أحدها أولًا.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 1) {
                SourceStepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = "المصدر السابق",
                    enabled = index > 0,
                    onClick = { onStep(-1) },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "اختيار المصدر", role = Role.Button) { open = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (count > 1) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            repeat(count) { page ->
                                val width by animateDpAsState(
                                    targetValue = if (page == index) 16.dp else 6.dp,
                                    label = "sourceMark",
                                )
                                Box(
                                    modifier = Modifier
                                        .size(width = width, height = 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (page == index) accent
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        ),
                                )
                            }
                        }
                    }
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Text(
                        text = "ثبّت بالدبوس ما تريد التنقّل بينه بالسحب",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    ExplanationSources.BUILT_IN.forEach { source ->
                        SourceMenuItem(
                            label = ExplanationTab.forSource(source).menuLabel,
                            selected = source == current,
                            pinned = source in pinned,
                            onClick = {
                                open = false
                                onChoose(source)
                            },
                            onPin = { pin(source) },
                        )
                    }
                    if (packs.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        packs.forEach { pack ->
                            val source = ExplanationSources.pack(pack.id)
                            SourceMenuItem(
                                label = pack.title,
                                selected = source == current,
                                pinned = source in pinned,
                                onClick = {
                                    open = false
                                    onChoose(source)
                                },
                                onPin = { pin(source) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("تنزيل تفاسير أخرى") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = accent) },
                        onClick = {
                            open = false
                            onOpenLibrary()
                        },
                    )
                }
            }
            if (count > 1) {
                SourceStepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = "المصدر التالي",
                    enabled = index < count - 1,
                    onClick = { onStep(+1) },
                )
            }
        }
    }
}

/** An arrow of the source bar: one source back or on, dimmed at either end. */
@Composable
private fun SourceStepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

@Composable
private fun SourceMenuItem(
    label: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (selected) accent else Color.Transparent,
            )
        },
        trailingIcon = {
            IconButton(onClick = onPin) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.OutlinedPushPin,
                    contentDescription = if (pinned) "إلغاء تثبيت $label" else "تثبيت $label",
                    modifier = Modifier.size(20.dp),
                    tint = if (pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        },
        onClick = onClick,
    )
}
