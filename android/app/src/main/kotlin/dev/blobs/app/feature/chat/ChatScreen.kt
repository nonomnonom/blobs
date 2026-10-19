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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.heading
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
import dev.blobs.app.ui.Mascot
import dev.blobs.app.ui.MascotMotion
import dev.blobs.app.ui.Page
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
    val packages by model.packages.collectAsStateWithLifecycle()
    val ready = model.selectedModel() != null && model.catalog.hasStarterLibrary(packages)
    val large = LocalDensity.current.fontScale >= 1.5f
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var observedTurns by rememberSaveable(currentThread) { mutableIntStateOf(0) }
    val showLatest by remember { derivedStateOf { list.canScrollForward && turns.isNotEmpty() } }
    var slow by rememberSaveable(work.turnId) { mutableStateOf(false) }
    LaunchedEffect(work.turnId, work.busy) {
        slow = false
        if (work.busy && work.turnId.isNotEmpty()) {
            delay((30_000L - (SystemClock.elapsedRealtime() - work.startedAt)).coerceAtLeast(0))
            slow = true
        }
    }
    LaunchedEffect(turns.size) {
        if (turns.size > observedTurns) {
            list.scrollToItem(turns.lastIndex)
            focus.clearFocus()
        }
        observedTurns = turns.size
    }
    if (turns.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HomeContent(state, work, ready, model)
        }
        return
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
                        model,
                        latest = turn.id == turns.lastOrNull()?.id
                    )
                }
                if (large) {
                    item(key = "follow-up-composer") {
                        Column(
                            Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.follow_up),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Composer(state, work, true, model)
                        }
                    }
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
            if (!large) {
                Composer(
                    state,
                    work,
                    true,
                    model,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TurnCard(
    turn: TurnEntity,
    answer: String,
    busy: Boolean,
    model: ProductViewModel,
    latest: Boolean
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val evidence = Evidence.decodeOrNull(turn.evidence)
    val large = LocalDensity.current.fontScale >= 1.5f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SelectionContainer {
            Text(
                turn.question,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (large) 21.sp else 26.sp,
                    lineHeight = if (large) 27.sp else 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.5).sp
                ),
                modifier = Modifier.semantics { heading() }
            )
        }
        Text(appName, style = MaterialTheme.typography.labelMedium)
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

            "UNVERIFIED" -> R.string.status_unverified

            "NO_SOURCES" -> R.string.status_no_sources

            else -> R.string.status_error
        }
        if (turn.status != "COMPLETE") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (turn.status ==
                    "RUNNING"
                ) {
                    Mascot(R.drawable.mascot_reading, Modifier.size(64.dp), MascotMotion.SEARCH)
                }
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            if (latest && turn.status != "COMPLETE") {
                if (turn.status == "NO_SOURCES") {
                    TextButton(onClick = { model.navigate(Page.SOURCES) }) {
                        Text(stringResource(R.string.choose_sources))
                    }
                }
                Button(onClick = {
                    model.retry(turn)
                }, enabled = !busy) { Text(stringResource(R.string.retry)) }
                if (turn.scope != "*" && turn.status == "NO_SOURCES") {
                    TextButton(onClick = {
                        model.retry(turn, widen = true)
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
