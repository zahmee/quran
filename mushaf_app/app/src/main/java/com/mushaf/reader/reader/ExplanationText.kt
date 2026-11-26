package com.mushaf.reader.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.mushaf.reader.data.content.MukhtasarText
import com.mushaf.reader.data.content.TafsirPassageRow

/** [this] with its size and line height multiplied by [factor], for the sheet's text-size steps. */
internal fun TextStyle.scaled(factor: Float): TextStyle {
    if (factor == 1f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
}

/**
 * Sa'di marks quoted Quran words — and each explanation's ayah number — with braces. A note's anchor
 * is what points the eye from the text to the numbered note below it, so it is drawn as a raised number.
 */
private val SaadiMarkup = Regex("""\{[^{}\n]*\}|${TafsirPassageRow.NoteAnchor.pattern}""")

internal fun formatSaadiText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    fun appendAnchored(part: String) {
        var at = 0
        TafsirPassageRow.NoteAnchor.findAll(part).forEach { anchor ->
            append(part.substring(at, anchor.range.first))
            withStyle(SpanStyle(color = accent, fontSize = 0.7.em, baselineShift = BaselineShift.Superscript)) {
                append("(${anchor.groupValues[1].toInt().toArabicDigits()})")
            }
            at = anchor.range.last + 1
        }
        append(part.substring(at))
    }
    var cursor = 0
    SaadiMarkup.findAll(raw).forEach { match ->
        appendAnchored(raw.substring(cursor, match.range.first))
        if (match.value.startsWith("{")) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                appendAnchored(match.value)
            }
        } else {
            appendAnchored(match.value)
        }
        cursor = match.range.last + 1
    }
    appendAnchored(raw.substring(cursor))
}

@Composable
internal fun EmptyExplanationMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The words the Mukhtasar explains stand out the way Sa'di's Quran quotes do. */
internal fun formatMukhtasarText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    MukhtasarText.Gloss.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}

/**
 * A fling that reaches the end of a text would pass its leftover speed up to the bottom sheet, and
 * the reader's next tap, say on the link below the text, would only stop that motion. A fling
 * downwards still goes up to the sheet, which is how it is swiped away.
 */
internal object KeepFlingInSheet : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available else Velocity.Zero
}

/** All that an explanation says about its source: the way to the screen that names every source. */
@Composable
internal fun SourcesLink(onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("المصادر والتراخيص", style = MaterialTheme.typography.labelMedium)
    }
}

private val TafsirAyahSpan = Regex(
    pattern = """<span\s+class=['\"]aya['\"]>(.*?)</span>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun formatTafsirText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    TafsirAyahSpan.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}
