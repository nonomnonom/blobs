package dev.blobs.app.feature.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.theme.BlobsDimens
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: AppUiState,
    turns: List<TurnEntity>,
    work: WorkState,
    model: AppViewModel,
    modifier: Modifier = Modifier
) {
    val currentThread by model.conversationId.collectAsStateWithLifecycle()
    val ready = model.selectedModel() != null
    val large = LocalDensity.current.fontScale >= 1.5f
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var observedTurns by rememberSaveable(currentThread) { mutableIntStateOf(0) }
    val showLatest by remember { derivedStateOf { list.canScrollForward && turns.isNotEmpty() } }
    LaunchedEffect(turns.size) {
        if (turns.size > observedTurns) {
            list.scrollToItem(turns.lastIndex)
            focus.clearFocus()
        }
        observedTurns = turns.size
    }
    if (turns.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = BlobsDimens.homeReadingWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                HomeContent(state, work, ready, model)
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
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
                    top = BlobsDimens.sm,
                    bottom = BlobsDimens.lg
                ),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
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
                        researchStage = work.researchStage.takeIf { work.turnId == turn.id },
                        latest = turn.id == turns.lastOrNull()?.id
                    )
                }
                if (large) {
                    item(key = "follow-up-composer") {
                        Column(
                            Modifier.padding(horizontal = BlobsDimens.page),
                            verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
                        ) {
                            Text(
                                stringResource(R.string.follow_up),
                                style = MaterialTheme.typography.titleMedium
                            )
                            AnswerDisclaimer()
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
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(bottom = BlobsDimens.xs)
                        .heightIn(min = BlobsDimens.touchTarget),
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
            if (!large) {
                AnswerDisclaimer(Modifier.padding(horizontal = BlobsDimens.page))
                Composer(
                    state,
                    work,
                    true,
                    model,
                    Modifier.padding(
                        horizontal = BlobsDimens.md,
                        vertical = BlobsDimens.xs
                    )
                )
            }
        }
    }
}
