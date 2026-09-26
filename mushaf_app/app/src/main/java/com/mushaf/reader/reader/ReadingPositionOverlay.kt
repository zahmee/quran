package com.mushaf.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mushaf.reader.data.QuranPagePosition
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.update.AppUpdateState
import com.mushaf.reader.update.AppUpdateUi
import kotlinx.coroutines.delay

@Composable
internal fun readingPositionAllowed(
    enabled: Boolean,
    readerVisible: Boolean,
    scrolling: Boolean,
    updates: AppUpdateUi?,
): Boolean {
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    // Never overlap a store-update action at the bottom of the reader.
    val updateState = updates?.state
    val updateVisible = updateState is AppUpdateState.Available ||
        updateState is AppUpdateState.Downloading ||
        updateState is AppUpdateState.ReadyToInstall ||
        updateState is AppUpdateState.UpdateFailed
    return enabled && resumed && readerVisible && !scrolling && !updateVisible
}

@Composable
internal fun rememberReadingPositionVisible(key: Any?, allowed: Boolean): Boolean {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, allowed) {
        visible = false
        if (allowed && key != null) {
            visible = true
            delay(3_000)
            visible = false
        }
    }
    return visible && allowed
}

/** Fallback for pages without a printed hizb star. Does not intercept reading gestures. */
@Composable
internal fun ReadingPositionOverlay(
    page: Int,
    position: QuranPagePosition?,
    allowed: Boolean,
    palette: MushafPalette,
    modifier: Modifier = Modifier,
) {
    val visible = rememberReadingPositionVisible(page to position, allowed && position != null)
    AnimatedVisibility(
        visible = visible && allowed && position != null,
        modifier = modifier,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(180)),
    ) {
        position?.let { info ->
            val shape = RoundedCornerShape(12.dp)
            // Keep the reader's own paper/ink hue in every palette, with enough opacity for
            // small Arabic text to remain legible above a printed line. No modal or touch layer.
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(lerp(palette.paper, palette.ink, 0.08f).copy(alpha = 0.97f), shape)
                    .border(0.5.dp, palette.ink.copy(alpha = 0.18f), shape)
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "الجزء ${info.start.juz.toArabicDigits()} · ${info.start.hizbLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.ink,
                    textAlign = TextAlign.Center,
                )
                info.boundary?.let { next ->
                    val label = if (next.juz != info.start.juz) {
                        "بداية الجزء ${next.juz.toArabicDigits()} داخل الصفحة"
                    } else {
                        "${next.hizbLabel()} يبدأ داخل الصفحة"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

internal fun QuranPagePosition.Division.hizbLabel(): String {
    val name = when (quarter) {
        2 -> "ربع الحزب"
        3 -> "نصف الحزب"
        4 -> "ثلاثة أرباع الحزب"
        else -> "الحزب"
    }
    return "$name ${hizb.toArabicDigits()}"
}
