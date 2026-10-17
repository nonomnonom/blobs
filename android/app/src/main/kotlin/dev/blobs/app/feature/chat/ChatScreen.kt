package dev.blobs.app.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.SectionLabel
import dev.blobs.app.ui.SourceRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: ProductState,
    turns: List<TurnEntity>,
    work: WorkState,
    model: ProductViewModel
) {
    val currentThread by model.conversationId.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val packages by model.packages.collectAsStateWithLifecycle()
    val ready = model.selectedModel() != null && packages.any { it.kind == "library" }
    val showLatest by remember(turns.isNotEmpty()) {
        derivedStateOf {
            list.canScrollForward &&
                turns.isNotEmpty()
        }
    }
    val large = LocalDensity.current.fontScale >= 1.5f
    var composing by rememberSaveable { mutableStateOf(true) }
    var slow by rememberSaveable(work.turnId) { mutableStateOf(false) }
    LaunchedEffect(work.turnId, work.busy) {
        slow = false
        if (work.busy && work.turnId.isNotEmpty()) {
            delay((30_000L - (SystemClock.elapsedRealtime() - work.startedAt)).coerceAtLeast(0))
            slow = true
        }
    }
    LaunchedEffect(turns.size, large) {
        if (turns.isNotEmpty()) {
            list.scrollToItem(turns.lastIndex)
            focus.clearFocus()
        }
        if (large && turns.isNotEmpty()) composing = false
    }
    Column(Modifier.fillMaxSize()) {
        if (work.busy &&
            work.threadId.isNotBlank() &&
            work.threadId != currentThread
        ) {
            Body(R.string.other_answer)
        }
        Column(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (turns.isEmpty()) {
                    item {
                        HomeContent(ready, work.busy, model)
                    }
                }
                items(turns, key = { it.id }) { turn ->
                    TurnCard(
                        if (work.turnId == turn.id) turn.copy(evidence = work.evidence) else turn,
                        if (work.turnId == turn.id &&
                            work.text.isNotBlank()
                        ) {
                            work.text
                        } else {
                            turn.answer
                        },
                        work.busy,
                        model
                    )
                }
            }
            if (showLatest) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        scope.launch {
                            list.scrollToItem(turns.lastIndex)
                            list.scrollBy(
                                list.layoutInfo.visibleItemsInfo.lastOrNull()?.size?.toFloat() ?: 0f
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text(stringResource(R.string.latest)) }
            }
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (slow) {
                Text(
                    stringResource(R.string.slow_answer),
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!composing && turns.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (work.busy && work.turnId.isNotEmpty()) {
                        PrimaryAction(R.string.stop_answer) { model.stop() }
                    } else {
                        PrimaryAction(R.string.follow_up, !work.busy) { composing = true }
                    }
                }
            } else {
                if (large &&
                    turns.isNotEmpty()
                ) {
                    TextButton(onClick = {
                        composing = false
                    }) { Text(stringResource(R.string.reading)) }
                }
                Composer(state, work, turns.isNotEmpty(), model)
            }
        }
    }
}

@Composable
private fun TurnCard(turn: TurnEntity, answer: String, busy: Boolean, model: ProductViewModel) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val evidence = Evidence.decodeOrNull(turn.evidence)
    val large = LocalDensity.current.fontScale >= 1.5f
    var showQuestion by rememberSaveable(turn.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (large) {
            TextButton(onClick = {
                showQuestion = !showQuestion
            }) { Text(stringResource(R.string.your_question)) }
        }
        if (!large || showQuestion) {
            SelectionContainer {
                Text(
                    turn.question,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        if (!large) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(painterResource(R.drawable.blob_wave), null, Modifier.size(36.dp))
                Text(appName, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (answer.isNotEmpty()) {
            val markerSize = if (large) 32.dp else 24.dp
            val markerSp = (markerSize.value / LocalDensity.current.fontScale).sp
            val citations = evidence.orEmpty().mapIndexed { index, source ->
                val description = stringResource(R.string.read_source, index + 1, source.title)
                "citation-${index + 1}" to InlineTextContent(
                    Placeholder(markerSp, markerSp, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Surface(
                        onClick = { model.read(source) },
                        modifier = Modifier.size(markerSize).semantics {
                            contentDescription =
                                description
                        },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                (index + 1).toString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }.toMap()
            val text = buildAnnotatedString {
                var previous = 0
                for (citation in AnswerText.citations(answer, evidence?.size ?: 0)) {
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
                Text(text, inlineContent = citations, style = MaterialTheme.typography.bodyLarge)
            }
        }
        val label = when (turn.status) {
            "RUNNING" -> R.string.working
            "COMPLETE" -> R.string.status_complete
            "STOPPED" -> if (answer.isBlank()) {
                R.string.status_stopped_empty
            } else {
                R.string.status_stopped
            }
            "INTERRUPTED" -> if (answer.isBlank()) {
                R.string.status_interrupted_empty
            } else {
                R.string.status_interrupted
            }
            "LIMITED" -> R.string.status_limited
            "NO_SOURCES" -> R.string.status_no_sources
            else -> R.string.status_error
        }
        if (turn.status != "COMPLETE") {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (turn.status == "RUNNING") LinearProgressIndicator(Modifier.fillMaxWidth())
        if (evidence == null) Body(R.string.sources_unavailable)
        if (!evidence.isNullOrEmpty()) {
            SectionLabel(R.string.sources_used)
            evidence.forEachIndexed { index, source ->
                SourceRow(
                    index + 1,
                    source.title,
                    stringResource(
                        R.string.read_source,
                        index + 1,
                        source.title
                    ),
                    onClick = {
                        model.read(source)
                    }
                )
            }
        }
        if (turn.status != "RUNNING") {
            if (answer.isNotBlank()) {
                TextButton(onClick = {
                    (
                        context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        ).setPrimaryClip(
                        ClipData.newPlainText(appName, answer)
                    )
                }) {
                    androidx.compose.material3.Icon(
                        painterResource(R.drawable.ic_content_copy),
                        null,
                        Modifier.padding(end = 8.dp).size(20.dp)
                    )
                    Text(stringResource(R.string.copy_answer))
                }
            }
            if (turn.status != "COMPLETE") {
                Button(onClick = {
                    model.send(turn.question)
                }, enabled = !busy) { Text(stringResource(R.string.retry)) }
                if (turn.scope != "*" && turn.status == "NO_SOURCES") {
                    TextButton(onClick = {
                        model.send(turn.question, widen = true)
                    }, enabled = !busy) { Text(stringResource(R.string.search_all)) }
                }
            }
            Text(
                turn.model.substringBeforeLast(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.answer_check),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AnnotatedString.Builder.appendEmphasis(value: String) {
    var start = 0
    val text = value.replace(Regex("(?m)^#{1,6} +"), "")
    for (match in Regex("\\*\\*([^*]+)\\*\\*").findAll(text)) {
        append(text.substring(start, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        start = match.range.last + 1
    }
    append(text.substring(start))
}
