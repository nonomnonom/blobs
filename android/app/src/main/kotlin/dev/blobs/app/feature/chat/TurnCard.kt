package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.data.TurnResponse
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.inference.OfflineAnswerNotice
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.chat.Bubble
import dev.blobs.app.ui.chat.BubbleContent
import dev.blobs.app.ui.chat.Message
import dev.blobs.app.ui.chat.MessageAlign
import dev.blobs.app.ui.chat.MessageContent
import dev.blobs.app.ui.chat.MessageFooter
import dev.blobs.app.ui.chat.MessageHeader
import dev.blobs.app.ui.theme.BlobsDimens

/**
 * One research turn, in the order the reader needs it: their question, the answer, the
 * state the answer is in, the evidence behind it, then what they can do next.
 */
@Composable
internal fun TurnCard(
    turn: TurnEntity,
    answer: String,
    busy: Boolean,
    model: AppViewModel,
    researchStage: ResearchStage?,
    latest: Boolean,
    modifier: Modifier = Modifier
) {
    val appName = stringResource(R.string.app_name)
    val response = remember(turn, answer) { TurnResponse.of(turn, answer) }
    val status = response.status
    val evidence = response.evidence
    val offlineNotice = OfflineAnswerNotice.forQuestion(turn.question)
    val modelAnswer = response.body
    val groups = remember(evidence) { groupTurnSources(evidence.orEmpty()) }
    val running = status == TurnStatus.RUNNING
    val stages = remember(turn.id) { mutableStateListOf<ResearchStage>() }
    LaunchedEffect(researchStage) {
        if (researchStage != null && researchStage !in stages) stages.add(researchStage)
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.widthIn(max = BlobsDimens.answerReadingWidth).fillMaxWidth()
                .padding(horizontal = BlobsDimens.page),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
        ) {
            Bubble(align = MessageAlign.End) {
                BubbleContent(turn.question)
            }
            Message(align = MessageAlign.Start) {
                MessageHeader(avatar = R.drawable.mascot_curious, label = appName)
                if (offlineNotice.isNotBlank() && modelAnswer.isNotBlank()) {
                    Text(
                        offlineNotice.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MessageContent {
                    if (running) {
                        ResearchTrace(stages, researchStage, evidence?.size ?: 0)
                    }
                    if (!running && response.evidence != null && modelAnswer.isNotBlank()) {
                        GroundingChain(response)
                    }
                    if (modelAnswer.isNotEmpty()) {
                        AnswerContent(
                            answer = modelAnswer,
                            evidence = evidence.orEmpty(),
                            onRead = model::read
                        )
                    }
                    TurnOutcome(
                        response = response,
                        latest = latest,
                        busy = busy,
                        onChooseLibraries = { model.navigate(Page.SOURCES) },
                        onSearchAll = { model.retry(turn, widen = true) },
                        onRetry = { model.retry(turn) }
                    )
                    if (evidence == null && !running) Body(R.string.sources_unavailable)
                    if (!running) {
                        TurnSources(
                            turnId = turn.id,
                            groups = groups,
                            citedSourceIndices = response.citedSourceIndices,
                            onRead = model::read
                        )
                    }
                    if (response.suggestions.isNotEmpty()) {
                        Suggestions(response.suggestions.map { it.text }, busy) { model.send(it) }
                    }
                }
                if (status != TurnStatus.RUNNING) {
                    MessageFooter {
                        TurnActions(
                            turnId = turn.id,
                            answer = modelAnswer,
                            modelName = turn.model.substringBeforeLast(" · ")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(offers: List<String>, busy: Boolean, onChoose: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xs),
        modifier = Modifier.padding(vertical = BlobsDimens.xxs)
    ) {
        Text(
            stringResource(R.string.suggested_follow_ups),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        offers.forEach { offer ->
            OutlinedButton(
                onClick = { if (!busy) onChoose(offer) },
                enabled = !busy,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Icon(
                    painterResource(R.drawable.ic_chat_bubble),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    offer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
