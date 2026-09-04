package com.mushaf.reader.update

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The whole visible surface of the update feature: one quiet bar above the page.
 *
 * It is deliberately not a dialog. The reader is mid-page; an update is never urgent enough to
 * take the page away from them, so this can be ignored indefinitely and dismissed with one tap.
 *
 * It reads [AppUpdateUi.state] itself rather than taking it as a parameter, so that a download's
 * steady drip of progress invalidates this bar alone and not the whole reader around it.
 */
@Composable
fun UpdateBanner(updates: AppUpdateUi, modifier: Modifier = Modifier) {
    val state = updates.state
    val visible = state is AppUpdateState.Available ||
        state is AppUpdateState.Downloading ||
        state is AppUpdateState.ReadyToInstall ||
        state is AppUpdateState.UpdateFailed

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
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
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (state) {
                            is AppUpdateState.Downloading -> "جارٍ تنزيل التحديث…"
                            AppUpdateState.ReadyToInstall -> "التحديث جاهز للتثبيت"
                            AppUpdateState.UpdateFailed -> "تعذّر بدء التحديث"
                            else -> "يتوفّر إصدار جديد من التطبيق"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (state is AppUpdateState.Downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { updates.dismiss() }) { Text("لاحقاً") }
                        Spacer(Modifier.width(4.dp))
                        when (state) {
                            AppUpdateState.ReadyToInstall -> TextButton(
                                onClick = { updates.install() }
                            ) {
                                Text("إعادة التشغيل والتثبيت", fontWeight = FontWeight.SemiBold)
                            }
                            AppUpdateState.UpdateFailed -> TextButton(
                                onClick = { updates.update() }
                            ) {
                                Text("إعادة المحاولة", fontWeight = FontWeight.SemiBold)
                            }
                            else -> TextButton(onClick = { updates.update() }) {
                                Text("تنزيل التحديث", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
