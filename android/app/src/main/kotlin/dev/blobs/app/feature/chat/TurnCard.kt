package dev.blobs.app.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.data.local.finishReasonKind
import dev.blobs.app.data.local.groundingKind
import dev.blobs.app.data.local.statusKind
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.OfflineAnswerNotice
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.theme.BlobsComponentShapes
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
internal fun TurnCard(
    turn: TurnEntity,
    answer: String,
    busy: Boolean,
    model: AppViewModel,
    researchStage: ResearchStage?,
    latest: Boolean
) {
    val appName = stringResource(R.string.app_name)
    val status = turn.statusKind
    val finishReason = turn.finishReasonKind
    val evidence = Evidence.decodeOrNull(turn.evidence)
    val offlineNotice = OfflineAnswerNotice.forQuestion(turn.question)
    val modelAnswer = answer.removePrefix(offlineNotice)
    val citedSourceIndices = AnswerText.citedSourceIndices(modelAnswer, evidence?.size ?: 0)
    val hasCompleteCitationCoverage = AnswerText.hasCompleteCitationCoverage(
        modelAnswer,
        evidence?.size ?: 0
    )
    val grounding = when (turn.groundingKind) {
        TurnGrounding.GROUNDED -> TurnGrounding.GROUNDED
        TurnGrounding.NOT_REQUESTED -> TurnGrounding.NOT_REQUESTED
        TurnGrounding.UNVERIFIED -> TurnGrounding.UNVERIFIED
        else -> if (hasCompleteCitationCoverage) {
            TurnGrounding.GROUNDED
        } else {
            TurnGrounding.UNVERIFIED
        }
    }
    val groups = remember(evidence) { groupTurnSources(evidence.orEmpty()) }
    val hasNoSources = status == TurnStatus.NO_SOURCES ||
        (
            status == TurnStatus.COMPLETE &&
                turn.scope.isNotBlank() &&
                evidence?.isEmpty() == true &&
                modelAnswer.isNotBlank()
            )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.widthIn(max = BlobsDimens.answerReadingWidth).fillMaxWidth()
                .padding(horizontal = BlobsDimens.page),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier.widthIn(max = BlobsDimens.userMessageWidth),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = BlobsComponentShapes.userMessage
                ) {
                    SelectionContainer {
                        Text(
                            turn.question,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                .semantics { heading() }
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painterResource(R.drawable.mascot_curious),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(appName, style = MaterialTheme.typography.labelMedium)
            }
            if (offlineNotice.isNotBlank() && modelAnswer.isNotBlank()) {
                Text(
                    offlineNotice.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (modelAnswer.isNotEmpty()) {
                AnswerContent(
                    answer = modelAnswer,
                    evidence = evidence.orEmpty(),
                    onRead = model::read
                )
            }
            if (status == TurnStatus.RUNNING) {
                ResearchActivity(researchStage, evidence?.size ?: 0)
            }
            TurnOutcome(
                turn = turn,
                status = status,
                finishReason = finishReason,
                grounding = grounding,
                answer = modelAnswer,
                hasNoSources = hasNoSources,
                latest = latest,
                busy = busy,
                onChooseLibraries = { model.navigate(Page.SOURCES) },
                onSearchAll = { model.retry(turn, widen = true) },
                onRetry = { model.retry(turn) }
            )
            if (evidence == null) Body(R.string.sources_unavailable)
            TurnSources(
                turnId = turn.id,
                groups = groups,
                citedSourceIndices = citedSourceIndices,
                onRead = model::read
            )
            if (status != TurnStatus.RUNNING) {
                TurnActions(
                    turnId = turn.id,
                    answer = modelAnswer,
                    modelName = turn.model.substringBeforeLast(" · ")
                )
            }
        }
    }
}
