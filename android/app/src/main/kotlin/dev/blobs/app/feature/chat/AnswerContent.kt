package dev.blobs.app.feature.chat

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
internal fun AnswerContent(
    answer: String,
    evidence: List<Evidence>,
    modifier: Modifier = Modifier,
    onRead: (Evidence) -> Unit
) {
    if (answer.isEmpty()) return
    val displayNumbers = remember(evidence) {
        groupTurnSources(evidence).flatMap { group ->
            group.sourceIndices.map { sourceIndex -> sourceIndex to group.displayNumber }
        }.toMap()
    }
    val markerSize = BlobsDimens.inlineCitationTarget
    val markerSp = (markerSize.value / LocalDensity.current.fontScale).sp
    val inlineContent = evidence.mapIndexed { index, source ->
        val displayNumber = displayNumbers[index] ?: index + 1
        val description = stringResource(
            R.string.read_source,
            displayNumber,
            source.title
        )
        "citation-${index + 1}" to InlineTextContent(
            Placeholder(markerSp, markerSp, PlaceholderVerticalAlign.TextCenter)
        ) {
            InlineCitation(displayNumber, description) { onRead(source) }
        }
    }.toMap()
    val text = buildAnnotatedString {
        var previous = 0
        for (citation in AnswerText.citations(answer, evidence.size)) {
            appendEmphasis(answer.substring(previous, citation.range.first))
            appendInlineContent(
                "citation-${citation.sourceIndex + 1}",
                answer.substring(citation.range)
            )
            previous = citation.range.last + 1
        }
        appendEmphasis(answer.substring(previous))
    }
    SelectionContainer {
        Text(
            text,
            modifier = modifier,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun AnnotatedString.Builder.appendEmphasis(value: String) {
    var start = 0
    val text = AnswerText.readable(value).replace(Regex("(?m)^#{1,6} +"), "")
    for (match in Regex("\\*\\*([^*]+)\\*\\*").findAll(text)) {
        append(text.substring(start, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        start = match.range.last + 1
    }
    append(text.substring(start))
}
